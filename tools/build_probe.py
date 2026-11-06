"""Build a shell UI-tree probe without suppressing enabled accessibility services."""
from pathlib import Path
import os, shutil, subprocess, zipfile
import device as d

root=Path(__file__).resolve().parents[1]
sdk=Path(os.environ.get('ANDROID_SDK_ROOT',str(root/'.toolchains/android-sdk')))
build=root/'.checks/probe'
classes=build/'classes'; dex=build/'dex'
classes.mkdir(parents=True,exist_ok=True);dex.mkdir(exist_ok=True)
jar=sdk/'platforms/android-35/android.jar'
javac=shutil.which('javac') or str(Path(os.environ['JAVA_HOME'])/'bin/javac.exe')
subprocess.run([javac,'-source','17','-target','17','-cp',str(jar),'-d',str(classes),*map(str,(root/'tools/android/probe').glob('*.java'))],check=True)
d8=sdk/'build-tools/36.0.0/d8.bat'
subprocess.run([str(d8),'--min-api','33','--lib',str(jar),'--output',str(dex),*map(str,classes.rglob('*.class'))],check=True)
output=build/'edgeflow-probe.jar'
with zipfile.ZipFile(output,'w') as z:z.write(dex/'classes.dex','classes.dex')
subprocess.run(d.A+['push',str(output),'/data/local/tmp/edgeflow-probe.jar'],check=True)
print('Built independent UI probe:',output)
