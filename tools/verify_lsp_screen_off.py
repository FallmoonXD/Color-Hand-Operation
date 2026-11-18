"""Final interactive test: screen-off must dispose the pilot input monitor.

Run after other device tests. This leaves the phone asleep and may require the
owner to unlock it before further interactive work.
"""
import json,subprocess,time
from verify_lsp_input import d,ROOT,OUT,REMOTE,Probe,status

def main():
    OUT.mkdir(parents=True,exist_ok=True);before=status();probe=None
    try:
        subprocess.run(d.A+['push',str(ROOT/'lsp/build/outputs/apk/debug/lsp-debug.apk'),REMOTE],check=True)
        probe=Probe('observe',5)
        d.sh('input','keyevent','223');time.sleep(.8);final=probe.stop();probe=None
        power=[l.strip() for l in d.sh('dumpsys','power').splitlines() if 'mWakefulness=' in l]
        result={'final':final,'power':power,'monitor_absent':'CHO LSP pilot' not in d.sh('dumpsys','input')}
        (OUT/'screen-off.json').write_text(json.dumps(result,ensure_ascii=False,indent=2),encoding='utf8')
        assert final and final['reason']=='display-off' and not final['active'] and final['actions']==0,result
        assert result['monitor_absent'],result
        assert any(l in ['mWakefulness=Dozing','mWakefulness=Asleep'] for l in power),result
        print('PASS screen-off disposes monitor; phone is asleep',flush=True)
    finally:
        if probe is not None:probe.stop()
        d.sh('rm','-f',REMOTE)
        after=status();(OUT/'after-screen-off.json').write_text(json.dumps(after,ensure_ascii=False,indent=2),encoding='utf8')
        assert after==before,'Screen-off test changed settings'

if __name__=='__main__':main()
