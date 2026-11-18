"""Integrated APK preparation and device evidence; never overwrite an earlier baseline."""
import hashlib,json,sqlite3,subprocess,time,os
from pathlib import Path
import verify_lsp_input as base
import lsp_control
d=base.d
ROOT=base.ROOT
OUT=ROOT/'verification/lsp-integrated'
os.environ['CHO_TEST_READ_RELEASE_PREFS']='1'

def save(name,value):
    OUT.mkdir(parents=True,exist_ok=True)
    (OUT/name).write_text(json.dumps(value,ensure_ascii=False,indent=2,default=lambda v:{'hex':v.hex()} if isinstance(v,bytes) else list(v)),encoding='utf8')

def modules(label):
    directory=OUT/label;directory.mkdir(parents=True,exist_ok=True)
    for suffix in ['', '-wal','-shm']:
        name='modules_config.db'+suffix
        raw=subprocess.run(d.A+['exec-out','su','-c','cat /data/adb/lspd/config/'+name],capture_output=True,check=True).stdout
        (directory/name).write_bytes(raw)
    with sqlite3.connect(directory/'modules_config.db') as db:
        tables=[r[0] for r in db.execute("select name from sqlite_master where type='table'") if not r[0].startswith('sqlite_')]
        return {name:list(db.execute('select * from "'+name+'"')) for name in tables}

def request(operation,**kwargs):return lsp_control.request(operation,module='dev.edgeflow',**kwargs)

def prepare():
    OUT.mkdir(parents=True,exist_ok=True)
    assert not (OUT/'before.json').exists(),'Existing baseline must not be overwritten'
    before=base.status();save('before.json',before);save('modules-before.json',modules('db-before'))
    dp=d.sh('su','-c','ls /data/user_de/0/dev.edgeflow/shared_prefs 2>/dev/null || true').split()
    save('dp-before.json',{name:d.sh('su','-c','cat /data/user_de/0/dev.edgeflow/shared_prefs/'+name) for name in dp if name.endswith('.xml')})
    path=d.sh('pm','path','dev.edgeflow').strip().removeprefix('package:')
    assert path.startswith('/data/app/') and '\n' not in path
    subprocess.run(d.A+['pull',path,str(OUT/'original-cho.apk')],check=True)
    save('installed-before.json',{'sha256':hashlib.sha256((OUT/'original-cho.apk').read_bytes()).hexdigest()})
    assert not d.sh('pm','list','packages','dev.edgeflow.lsp.pilot').strip(),'Isolated pilot still installed'
    assert not d.sh('pm','list','packages',base.PACKAGE).strip(),'Fixture already installed'
    fixture=base.build_fixture()
    for apk in [ROOT/'app/build/outputs/apk/release/app-release.apk',fixture]:subprocess.run(d.A+['install','-r','--no-incremental',str(apk)],check=True)
    d.sh('am','start','-W','-n','dev.edgeflow/.MainActivity');time.sleep(.5)
    assert base.status()==before,'Installing integrated APK changed user configuration'
    save('snapshot.json',request('snapshot'))
    print('PASS integrated package preserves prior preferences and permissions',flush=True)

def record(case,**data):
    path=OUT/'results.json';rows=json.loads(path.read_text(encoding='utf8')) if path.exists() else []
    rows=[r for r in rows if r['case']!=case];rows.append({'case':case,**data});save('results.json',rows);print('PASS',case,flush=True)

if __name__=='__main__':
    import sys
    if len(sys.argv)>1 and sys.argv[1]=='prepare':prepare()
    else:print(json.dumps(request('status'),ensure_ascii=False,indent=2))
