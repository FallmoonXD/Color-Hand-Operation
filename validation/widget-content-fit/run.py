from pathlib import Path
import subprocess

root=Path(__file__).resolve().parents[2]
output=root/'.checks/widget-content-fit'
output.mkdir(parents=True,exist_ok=True)
source=(root/'app/src/main/java/replica/WidgetContentFit.java').read_text(encoding='utf8')
source='\n'.join(line for line in source.splitlines() if not line.startswith(('package ','import ')))
(output/'WidgetContentFit.java').write_text(source,encoding='utf8')
subprocess.run(['javac','-encoding','UTF-8','--release','17','-d',str(output),str(output/'WidgetContentFit.java'),str(Path(__file__).with_name('WidgetContentFitCheck.java'))],check=True)
subprocess.run(['java','-cp',str(output),'WidgetContentFitCheck'],check=True)
