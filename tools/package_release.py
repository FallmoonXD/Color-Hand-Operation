"""Package the signed release APK and the committed sources for a GitHub release."""
from pathlib import Path
import hashlib, os, re, shutil, subprocess, zipfile

root=Path(__file__).resolve().parents[1]
dist=root/'dist';dist.mkdir(exist_ok=True)
version=re.search(r'versionName\s*=\s*"([^"]+)"',(root/'app/build.gradle.kts').read_text(encoding='utf-8')).group(1)
if not re.fullmatch(r'\d+\.\d+\.\d+',version):
    raise ValueError('Public versionName must use X.Y.Z without internal suffixes.')
name='ColorHandOperation-'+version
# The archive must correspond to the commit that will be tagged. Ignored local
# files (including signing keys) never enter the source distribution.
subprocess.run(['git','diff','--quiet','HEAD','--'],cwd=root,check=True)
sdk=Path(os.environ.get('ANDROID_SDK_ROOT',str(root/'.toolchains/android-sdk')))
build_tools=sdk/'build-tools/36.0.0'
built=root/'app/build/outputs/apk/release/app-release.apk'
subprocess.run([str(build_tools/'apksigner.bat'),'verify','--verbose',str(built)],check=True)
revision=subprocess.check_output(['git','rev-parse','HEAD'],cwd=root,text=True).strip()
with zipfile.ZipFile(built) as signed:
    info=signed.read('META-INF/version-control-info.textproto').decode('utf-8')
    if f'revision: "{revision}"' not in info:
        raise ValueError('Rebuild assembleRelease from the committed release sources before packaging.')
badging=subprocess.check_output([str(build_tools/'aapt.exe'),'dump','badging',str(built)]).decode('utf-8',errors='replace')
code=re.search(r'versionCode\s*=\s*(\d+)',(root/'app/build.gradle.kts').read_text(encoding='utf-8'))[1]
if not badging.startswith(f"package: name='dev.edgeflow' versionCode='{code}' versionName='{version}'") or 'application-debuggable' in badging:
    raise ValueError('APK does not match the release version, package or build type.')
apk=dist/(name+'.apk');shutil.copy2(built,apk)
archive=dist/(name+'-source.zip')
subprocess.run(['git','archive','--format=zip','-9','--prefix=ColorHandOperation/','-o',str(archive),'HEAD'],cwd=root,check=True)
with zipfile.ZipFile(archive) as z:
    assert z.testzip() is None
    assert 'ColorHandOperation/gradle/wrapper/gradle-wrapper.jar' in z.namelist()
hashfile=dist/'SHA256SUMS.txt'
lines=hashfile.read_text(encoding='utf-8').splitlines() if hashfile.exists() else []
names={apk.name,archive.name};lines=[line for line in lines if not any(line.endswith('  '+n) for n in names)]
for path in (apk,archive):
    checksum=hashlib.sha256(path.read_bytes()).hexdigest()
    lines.append(checksum+'  '+path.name);print(checksum,path.name,path.stat().st_size)
hashfile.write_text('\n'.join(lines)+'\n',encoding='utf-8')
(dist/('SHA256SUMS-'+version+'.txt')).write_text('\n'.join(lines[-2:])+'\n',encoding='utf-8')
