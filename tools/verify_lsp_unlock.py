"""Read SystemUI's own CHO dump; the app and test fixture may be frozen or absent."""
import argparse,json,time
from pathlib import Path
import device as d

def status():
    text=d.sh('dumpsys','activity','service','com.android.systemui/.SystemUIService','--cho-status')
    line=next((line for line in text.splitlines() if 'CHO_STATUS: ' in line),None)
    if line is None:raise RuntimeError('SystemUI CHO diagnostics unavailable: '+text[-500:])
    return json.loads(line.split('CHO_STATUS: ',1)[1])

def sample(path,seconds):
    assert not path.exists(),'Do not overwrite an earlier recording'
    path.parent.mkdir(parents=True,exist_ok=True);deadline=time.monotonic()+seconds;previous=None
    with path.open('w',encoding='utf8') as file:
        while time.monotonic()<deadline:
            begin=time.monotonic();row=status();row['query_ms']=round((time.monotonic()-begin)*1000)
            file.write(json.dumps(row,ensure_ascii=False)+'\n');file.flush()
            brief={k:row.get(k) for k in ['locked','interactive','display_state','active','persistent_state','persistent_error','error','session_starts','actions','claims','reason']}
            if brief!=previous:print(row['uptime'],brief,flush=True);previous=brief
            time.sleep(.05)

if __name__=='__main__':
    parser=argparse.ArgumentParser();parser.add_argument('--output',type=Path);parser.add_argument('--seconds',type=int,default=180);args=parser.parse_args()
    if args.output:sample(args.output,args.seconds)
    else:print(json.dumps(status(),ensure_ascii=False,indent=2))
