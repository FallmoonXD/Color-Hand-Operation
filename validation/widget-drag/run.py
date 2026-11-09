from pathlib import Path
import subprocess
root=Path(__file__).resolve().parents[2]
out=root/'.checks/widget-drop';out.mkdir(parents=True,exist_ok=True)
src=root/'app/src/main/java/replica'
subprocess.run(['javac','-encoding','UTF-8','--release','17','-d',str(out),str(src/'WidgetGrid.java'),str(src/'WidgetDrop.java'),str(Path(__file__).with_name('WidgetDropCheck.java'))],check=True)
subprocess.run(['java','-cp',str(out),'replica.WidgetDropCheck'],check=True)
