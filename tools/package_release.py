"""Package the verified local APK and a source-only archive; keep earlier releases."""
from pathlib import Path
import hashlib, os, re, shutil, subprocess, zipfile

root=Path(__file__).resolve().parents[1]
dist=root/'dist';dist.mkdir(exist_ok=True)
version=re.search(r'versionName\s*=\s*"([^"]+)"',(root/'app/build.gradle.kts').read_text(encoding='utf-8')).group(1)
name='ColorHandOperation-'+version
apk=dist/(name+'.apk');shutil.copy2(root/'app/build/outputs/apk/debug/app-debug.apk',apk)
sdk=Path(os.environ.get('ANDROID_SDK_ROOT',str(root/'.toolchains/android-sdk')))
subprocess.run([str(sdk/'build-tools/36.0.0/apksigner.bat'),'verify','--verbose',str(apk)],check=True)
archive=dist/('ColorHandOperation-'+version.split('-')[0]+'-source.zip')
excluded={'.git','.gradle','.checks','.toolchains','build','dist','verification','__pycache__','.idea','.cxx'}
with zipfile.ZipFile(archive,'w',zipfile.ZIP_DEFLATED,compresslevel=9) as z:
    for directory,children,files in os.walk(root):
        children[:]=sorted(child for child in children if child not in excluded)
        for file in sorted(files):
            path=Path(directory)/file
            if file=='local.properties' or path.suffix in ('.log','.pyc'):continue
            z.write(path,Path('ColorHandOperation')/path.relative_to(root))
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
