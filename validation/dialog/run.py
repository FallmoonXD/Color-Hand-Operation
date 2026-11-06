"""Run real production dismissal/measurement methods with deterministic UI boundaries."""
from pathlib import Path
import shutil,subprocess
here=Path(__file__).resolve().parent
root=here.parent.parent
source=(root/'app/src/main/java/replica/ColorDialogBuilder.java').read_text(encoding='utf-8')
out=root/'.checks/dialog-contract';out.mkdir(parents=True,exist_ok=True)
def method(signature):
 start=source.index(signature);at=source.index('{',start)+1;depth=1
 while depth:
  depth+=(source[at]=='{')-(source[at]=='}');at+=1
 return source[start:at]
flow=['private void dismissThen(Runnable','public void dismiss()','private void finishDismiss()','private void finishDismiss(boolean','private void releaseOwner(','private boolean ownerAvailable(','private void observeOwner(']
checks=[('DialogFlowCheck',flow),('DialogFooterCheck',['private void arrangeButtons(','private int preferredButtonWidth(']),('DialogBodyCheck',['private int bodyHeight('])]
for name,signatures in checks:
 code='\n'.join(method(x) for x in signatures)
 code=code.replace('android.app.Application.ActivityLifecycleCallbacks','Callbacks').replace('android.text.Layout','Layout')
 template=(here/(name+'.template.java')).read_text(encoding='utf-8')
 (out/(name+'.java')).write_text(template.replace('/* PRODUCTION */',code),encoding='utf-8')
space=(root/'app/src/main/java/replica/ColorWindowSpace.java').read_text(encoding='utf-8')
space=space[space.index('/** Screen-coordinate'):].replace('final class ColorWindowSpace','class ColorWindowSpace')
space_template=(here/'DialogSpaceCheck.template.java').read_text(encoding='utf-8')
(out/'DialogSpaceCheck.java').write_text(space_template.replace('/* PRODUCTION */',space),encoding='utf-8')
checks.append(('DialogSpaceCheck',[]))
source=(root/'app/src/main/java/replica/ColorPopupMenu.java').read_text(encoding='utf-8')
token=method('private void showWindow(').replace('android.os.IBinder','Binder').replace('org.lsposed.hiddenapibypass.HiddenApiBypass','HiddenApiBypass')
(out/'PopupTokenCheck.java').write_text((here/'PopupTokenCheck.template.java').read_text(encoding='utf-8').replace('/* PRODUCTION */',token),encoding='utf-8')
checks.append(('PopupTokenCheck',[]))
compiler=shutil.which('javac');runtime=str(Path(compiler).with_name('java.exe' if Path(compiler).suffix=='.exe' else 'java'))
subprocess.run([compiler,'-encoding','UTF-8','--release','17','-d',str(out),*[str(out/(name+'.java')) for name,_ in checks],str(root/'app/src/main/java/replica/ColorSheetGeometry.java')],check=True)
results=[]
for name,_ in checks:
 result=subprocess.run([runtime,'-cp',str(out),name],check=True,capture_output=True,text=True,encoding='utf-8');print(result.stdout,end='');results.append(result.stdout)
(out/'results.txt').write_text(''.join(results),encoding='utf-8')
