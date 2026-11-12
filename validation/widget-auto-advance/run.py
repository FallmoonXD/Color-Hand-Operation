from pathlib import Path
import subprocess

root=Path(__file__).resolve().parents[2]
output=root/'.checks/widget-auto-advance'
output.mkdir(parents=True,exist_ok=True)
source=(root/'app/src/main/java/replica/WidgetAutoAdvance.java').read_text(encoding='utf8')
source='\n'.join(line for line in source.splitlines() if not line.startswith(('package ','import ')))
(output/'WidgetAutoAdvance.java').write_text(source,encoding='utf8')
subprocess.run(['javac','-encoding','UTF-8','--release','17','-d',str(output),str(output/'WidgetAutoAdvance.java'),str(Path(__file__).with_name('WidgetAutoAdvanceCheck.java'))],check=True)
subprocess.run(['java','-cp',str(output),'WidgetAutoAdvanceCheck'],check=True)
