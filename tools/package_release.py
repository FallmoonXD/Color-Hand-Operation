"""Package the tested APK and verify that its application payload can be built from the source archive."""
from contextlib import contextmanager
from hashlib import sha256
from pathlib import Path
import os
import re
import shutil
import subprocess
import tempfile
import zipfile


# Public certificate of the GitHub v0.4.1 release. Upgrades must retain this identity.
SIGNER_SHA256 = '7c3bb3a646981f2abbb4d5e32866119721adc16aca85d351e0299ba82fea8838'
VCS_INFO = 'META-INF/version-control-info.textproto'
APK_PATH = Path('app/build/outputs/apk/release/app-release.apk')


def clean_revision(root):
    revision = subprocess.check_output(['git', 'rev-parse', 'HEAD'], cwd=root, text=True).strip()
    changes = subprocess.check_output(
        ['git', 'status', '--porcelain=v1', '-z', '--untracked-files=all', '--ignore-submodules=none'], cwd=root)
    if changes:
        raise ValueError('Release sources contain tracked changes or untracked files. Inspect git status before packaging.')
    return revision


def verify_apk(apk, sdk, revision, version, code):
    tools = sdk/'build-tools/36.0.0'
    signer = tools/('apksigner.bat' if os.name == 'nt' else 'apksigner')
    certs = subprocess.check_output([str(signer), 'verify', '--print-certs', str(apk)], encoding='utf-8')
    digests = re.findall(r'^Signer #\d+ certificate SHA-256 digest: ([\da-fA-F]{64})\s*$', certs, re.MULTILINE)
    if [digest.lower() for digest in digests] != [SIGNER_SHA256]:
        raise ValueError('The APK signing certificate does not match the public CHO release.')
    with zipfile.ZipFile(apk) as signed:
        info = signed.read(VCS_INFO).decode('utf-8')
        if f'revision: "{revision}"' not in info:
            raise ValueError('Rebuild assembleRelease from the intended release commit.')
    aapt = tools/('aapt.exe' if os.name == 'nt' else 'aapt')
    badging = subprocess.check_output([str(aapt), 'dump', 'badging', str(apk)]).decode('utf-8', errors='replace')
    if not badging.startswith(f"package: name='dev.edgeflow' versionCode='{code}' versionName='{version}'") or 'application-debuggable' in badging:
        raise ValueError('APK does not match the release version, package or build type.')


def apk_payload(path):
    # AGP writes different VCS metadata when building the exported tree without .git.
    # The distributed APK's own commit is checked separately before this comparison.
    entries = {}
    with zipfile.ZipFile(path) as apk:
        members = apk.infolist()
        if len({entry.filename for entry in members}) != len(members):
            raise ValueError('Duplicate paths in APK')
        for member in members:
            if member.filename != VCS_INFO:
                entries[member.filename] = (member.compress_type, sha256(apk.read(member)).digest())
    return entries


def verify_source_archive(archive, apk, sdk):
    with tempfile.TemporaryDirectory(prefix='cho-release-source-') as work:
        with zipfile.ZipFile(archive) as source:
            source.getinfo('ColorHandOperation/gradle/wrapper/gradle-wrapper.jar')
            source.extractall(work)
        checkout = Path(work)/'ColorHandOperation'
        wrapper = [str(checkout/'gradlew.bat')] if os.name == 'nt' else ['sh', str(checkout/'gradlew')]
        env = dict(os.environ, ANDROID_HOME=str(sdk), ANDROID_SDK_ROOT=str(sdk))
        subprocess.run([*wrapper, '--no-build-cache', ':app:assembleRelease'], cwd=checkout, env=env, check=True)
        tested, rebuilt = apk_payload(apk), apk_payload(checkout/APK_PATH)
        if tested != rebuilt:
            different = sorted(name for name in tested.keys() | rebuilt.keys() if tested.get(name) != rebuilt.get(name))
            raise ValueError('The tested APK differs from the archived sources: '+', '.join(different[:8]))


@contextmanager
def output_lock(root):
    folder = root/'.checks'
    folder.mkdir(exist_ok=True)
    with (folder/'release-pack.lock').open('a+b') as handle:
        if os.name == 'nt':
            import msvcrt
            handle.seek(0)
            msvcrt.locking(handle.fileno(), msvcrt.LK_LOCK, 1)
        else:
            import fcntl
            fcntl.flock(handle.fileno(), fcntl.LOCK_EX)
        # Closing the handle also releases the lock when the process exits.
        yield


def install_outputs(files, dist):
    # Keep previous directory entries available until every replacement succeeds.
    # All swaps and their rollback copies live on the output volume.
    replaced = []
    try:
        for source in files:
            dest = dist/source.name
            previous = None
            if dest.exists():
                previous = source.with_name(source.name+'.previous')
                shutil.copy2(dest, previous)
            source.replace(dest)
            replaced.append((dest, previous))
    except OSError:
        for dest, previous in reversed(replaced):
            if previous is None:
                dest.unlink()
            else:
                previous.replace(dest)
        raise


def package(root):
    root = root.resolve()
    revision = clean_revision(root)
    gradle = (root/'app/build.gradle.kts').read_text(encoding='utf-8')
    version = re.search(r'versionName\s*=\s*"([^"]+)"', gradle)[1]
    if not re.fullmatch(r'\d+\.\d+\.\d+', version):
        raise ValueError('Public versionName must use X.Y.Z without internal suffixes.')
    code = re.search(r'versionCode\s*=\s*(\d+)', gradle)[1]
    sdk = Path(os.environ.get('ANDROID_SDK_ROOT') or os.environ.get('ANDROID_HOME') or root/'.toolchains/android-sdk').resolve()
    dist = root/'dist'
    dist.mkdir(exist_ok=True)
    with tempfile.TemporaryDirectory(prefix='.cho-pack-', dir=dist) as directory:
        stage = Path(directory)
        name = 'ColorHandOperation-'+version
        apk, archive = stage/(name+'.apk'), stage/(name+'-source.zip')
        shutil.copy2(root/APK_PATH, apk)
        verify_apk(apk, sdk, revision, version, code)
        subprocess.run(['git', 'archive', '--format=zip', '-9', '--prefix=ColorHandOperation/',
                        '-o', str(archive), revision], cwd=root, check=True)
        verify_source_archive(archive, apk, sdk)
        sums = [sha256(path.read_bytes()).hexdigest()+'  '+path.name for path in (apk, archive)]
        version_sums = stage/f'SHA256SUMS-{version}.txt'
        version_sums.write_text('\n'.join(sums)+'\n', encoding='utf-8')
        with output_lock(root):
            if clean_revision(root) != revision:
                raise ValueError('Release sources changed during verification.')
            cumulative = dist/'SHA256SUMS.txt'
            old = cumulative.read_text(encoding='utf-8').splitlines() if cumulative.exists() else []
            names = {apk.name, archive.name}
            retained = [line for line in old if line.partition('  ')[2] not in names]
            (stage/cumulative.name).write_text('\n'.join(retained+sums)+'\n', encoding='utf-8')
            install_outputs((apk, archive, version_sums, stage/cumulative.name), dist)
        print('\n'.join(sums))


if __name__ == '__main__':
    package(Path(__file__).resolve().parents[1])
