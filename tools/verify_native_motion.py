"""Compare CHO with the installed PKR110 Settings/SystemUI resources and runtime springs."""
import hashlib
import os
import subprocess
import tempfile
import zipfile
from pathlib import Path

import device as d


def run(out):
    root = Path(__file__).resolve().parents[1]
    sdk = Path(os.environ.get('ANDROID_SDK_ROOT', root / '.toolchains/android-sdk'))
    android = sdk / 'platforms/android-35/android.jar'
    package = 'dev.edgeflow.motiontest'
    assert not d.sh('pm','list','packages',package).strip(), 'Motion test package is already installed'
    classes, dex = out / 'classes', out / 'dex'
    classes.mkdir(parents=True, exist_ok=True)
    dex.mkdir(exist_ok=True)
    compiled = root / 'app/build/intermediates/javac/release/compileReleaseJavaWithJavac/classes'
    subprocess.run(['javac', '-encoding', 'UTF-8', '--release', '17', '-cp',
                    os.pathsep.join(map(str, (android, compiled))), '-d', str(classes),
                    *map(str, (root / 'tools/android/motion').glob('*.java'))], check=True)
    subprocess.run([str(sdk / 'build-tools/36.0.0/d8.bat'), '--min-api', '33', '--lib',
                    str(android), '--classpath', str(compiled), '--output', str(dex),
                    *map(str, classes.rglob('*.class'))], check=True)
    bt = sdk / 'build-tools/36.0.0'
    raw, aligned, apk = (out / name for name in ('raw.apk', 'aligned.apk', 'motion-check.apk'))
    subprocess.run([str(bt / 'aapt2.exe'), 'link', '-I', str(android), '--manifest',
                    str(root / 'tools/android/motion/AndroidManifest.xml'), '-o', str(raw)], check=True)
    with zipfile.ZipFile(raw, 'a') as z:
        z.write(dex / 'classes.dex', 'classes.dex')
    subprocess.run([str(bt / 'zipalign.exe'), '-f', '4', str(raw), str(aligned)], check=True)
    subprocess.run([str(bt / 'apksigner.bat'), 'sign', '--ks', str(Path.home() / '.android/debug.keystore'),
                    '--ks-pass', 'pass:android', '--out', str(apk), str(aligned)], check=True)
    subprocess.run(d.A + ['install','--no-incremental',str(apk)],check=True)
    try:
        result=d.sh('am','instrument','-w',package+'/replica.MotionInstrumentation')
        print(result,flush=True)
        assert ('live ColorOS motion comparisons' in result and 'PASS attached controls' in result and 'upper-track GPU comparisons' in result
                and 'INSTRUMENTATION_CODE: -1' in result)
    finally:
        installed=d.sh('pm','path',package).strip().removeprefix('package:')
        assert d.sh('sha256sum',installed).split()[0]==hashlib.sha256(apk.read_bytes()).hexdigest()
        subprocess.run(d.A+['uninstall',package],check=True)


if __name__ == '__main__':
    with tempfile.TemporaryDirectory(prefix='cho-motion-check-') as directory:
        run(Path(directory))
