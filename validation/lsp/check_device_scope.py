"""Check the built module's diagnostic gate on ART without loading it into any system service."""
import os
import subprocess
import sys
import tempfile
import uuid
import zipfile
from pathlib import Path

ROOT=Path(__file__).resolve().parents[2]
sys.path.insert(0,str(ROOT/'tools'))
import device as d


def check():
    sdk=Path(os.environ.get('ANDROID_SDK_ROOT') or ROOT/'.toolchains/android-sdk')
    platform=sdk/'platforms/android-35/android.jar'
    app=ROOT/'app/build'
    classes=app/'intermediates/javac/release/compileReleaseJavaWithJavac/classes'
    apk=app/'outputs/apk/release/app-release.apk'
    name='dev.edgeflow.lsp.DiagnosticScopeCheck'
    prefix='/data/local/tmp/cho-scope-'+uuid.uuid4().hex
    with tempfile.TemporaryDirectory() as temporary:
        folder=Path(temporary)
        subprocess.run(['javac','--release','17','-encoding','utf8','-cp',os.pathsep.join(map(str,[platform,classes])),
                        '-d',str(folder),str(Path(__file__).with_name('DiagnosticScopeCheck.java'))],check=True)
        subprocess.run([str(sdk/'build-tools/36.0.0/d8.bat'),'--min-api','33','--lib',str(platform),
                        '--classpath',str(classes),'--output',str(folder),str(folder/(name.replace('.','/')+'.class'))],check=True)
        jar=folder/'probe.jar'
        with zipfile.ZipFile(jar,'w') as archive:
            archive.write(folder/'classes.dex','classes.dex')
        try:
            for source,ext in [(apk,'.apk'),(jar,'.jar')]:
                subprocess.run(d.A+['push',str(source),prefix+ext],check=True,capture_output=True)
                d.sh('chmod','444',prefix+ext)
            report=d.sh('env','CLASSPATH='+prefix+'.jar:'+prefix+'.apk','app_process','/system/bin',name)
            if not report.startswith('PASS 44 '):
                raise AssertionError(report)
            return report.strip()
        finally:
            d.sh('rm','-f',prefix+'.jar',prefix+'.apk')


if __name__=='__main__':
    print(check())
