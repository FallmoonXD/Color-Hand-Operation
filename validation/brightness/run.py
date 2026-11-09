"""Pure production range/command checks. Does not run adb, su or write settings."""
from pathlib import Path
import shutil
import subprocess

ROOT=Path(__file__).resolve().parents[2]
OUT=ROOT/'.checks/brightness'
OUT.mkdir(parents=True,exist_ok=True)
compiler=shutil.which('javac')
runtime=str(Path(compiler).with_name('java.exe' if Path(compiler).suffix=='.exe' else 'java'))
source=(ROOT/'app/src/main/java/replica/BrightnessControl.java').read_text(encoding='utf-8')
def method(signature):
 start=source.index(signature);at=source.index('{',start)+1;depth=1
 while depth:
  depth+=(source[at]=='{')-(source[at]=='}');at+=1
 return source[start:at]
methods=['private synchronized void updateRange(','public void sync(','private void unavailable(','private void describe(','private void write(','private synchronized boolean valid(','private void drainWrites(','public void close(']
code='\n'.join(method(name) for name in methods).replace('PendingRootCommand','FakeRootCommand')
template=Path(__file__).with_name('BrightnessControlCheck.template.java').read_text(encoding='utf-8')
(OUT/'BrightnessControlCheck.java').write_text(template.replace('/* PRODUCTION */',code),encoding='utf-8')
subprocess.run([compiler,'-encoding','UTF-8','--release','17','-d',str(OUT),
               str(ROOT/'app/src/main/java/replica/BrightnessRangePolicy.java'),
               str(Path(__file__).with_name('BrightnessRangeCheck.java')),str(OUT/'BrightnessControlCheck.java')],check=True)
results=[]
for name in ['BrightnessRangeCheck','BrightnessControlCheck']:
 result=subprocess.run([runtime,'-cp',str(OUT),'replica.'+name],check=True,capture_output=True,text=True)
 print(result.stdout,end='');results.append(result.stdout)
(OUT/'results.txt').write_text(''.join(results),encoding='utf-8')
