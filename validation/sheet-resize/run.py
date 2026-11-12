from pathlib import Path
import subprocess

root=Path(__file__).resolve().parents[2]
output=root/'.checks/sheet-resize'
output.mkdir(parents=True,exist_ok=True)
source=(root/'app/src/main/java/replica/ColorSheetResizeMotion.java').read_text(encoding='utf8')
source='\n'.join(line for line in source.splitlines() if not line.startswith(('package ','import android.')))
(output/'ColorSheetResizeMotion.java').write_text('import replica.ColorControlPhysics;\n'+source,encoding='utf8')
subprocess.run(['javac','-encoding','UTF-8','--release','17','-d',str(output),str(output/'ColorSheetResizeMotion.java'),str(root/'app/src/main/java/replica/ColorControlPhysics.java'),str(Path(__file__).with_name('SheetResizeCheck.java'))],check=True)
subprocess.run(['java','-cp',str(output),'SheetResizeCheck'],check=True)
