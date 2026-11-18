"""Real input cancellation, expiry, rotation and native-back arbitration checks."""
import json,shlex,subprocess,time
from verify_lsp_input import d,ROOT,OUT,PACKAGE,REMOTE,Probe,fixture,build_fixture,status

def save(results):
    (OUT/'boundaries.json').write_text(json.dumps(results,ensure_ascii=False,indent=2),encoding='utf8')

def choose_navigation(label):
    d.sh('am','start','-W','-a','oplus.intent.action.NAVIGATION_BAR_SETTINGS');time.sleep(.4)
    d.click(label);time.sleep(.6)

def main():
    OUT.mkdir(parents=True,exist_ok=True);before=status();results=[];installed=False;navigation_changed=False
    original_rotation=d.sh('wm','user-rotation').strip().split()
    assert before['navigation']=='0','Start with the recorded three-button navigation baseline'
    assert not d.sh('pm','list','packages',PACKAGE).strip()
    apk=build_fixture()
    try:
        subprocess.run(d.A+['push',str(ROOT/'lsp/build/outputs/apk/debug/lsp-debug.apk'),REMOTE],check=True)
        subprocess.run(d.A+['install','--no-incremental',str(apk)],check=True);installed=True
        driver=d.sh('pm','path',PACKAGE).strip().removeprefix('package:')
        for side in ['left','right']:
            for kind in ['cancel','retreat','multi-before','multi-after','mouse']:
                fixture();w,h=d.current_display_size();x=70 if side=='left' else w-70;y=h//2;delta=260 if side=='left' else -260
                probe=Probe('capture');marker='LSP_BOUNDARY_'+str(time.time_ns());d.sh('log','-t','CHOLspFixture',marker)
                try:
                    command='CLASSPATH='+shlex.quote(driver)+' app_process /system/bin dev.edgeflow.lspfixture.Events '+shlex.join([kind,str(x),str(y),str(delta)])
                    d.sh('su','-c',command);time.sleep(.3);foreground=d.resumed()
                    events=d.sh('logcat','-d','-v','brief','-s','CHOLspFixture:I').split(marker)[-1].strip()
                finally:final=probe.stop()
                row={'case':kind,'side':side,'final':final,'foreground':foreground,'fixture_events':events};results.append(row);save(results)
                assert final and not final['error'] and final['actions']==0 and final['rejected_actions']==0,row
                assert final['claims']==(0 if kind in ('multi-before','mouse') else 1),row
                assert PACKAGE+'/.SecondActivity' in foreground,row
                if kind not in ('multi-before','mouse'):assert 'ACTION_CANCEL' in events and 'CLICK' not in events,row
                if kind=='mouse':d.sh('input','tap',str(w//2),str(h//2))
                assert 'CHO LSP pilot' not in d.sh('dumpsys','input')
                print('PASS',side,kind,flush=True)
        fixture();probe=Probe('capture',1);time.sleep(1.3);final=probe.stop();results.append({'case':'expiry','final':final});save(results)
        assert final and final['reason']=='expired' and not final['active'] and final['actions']==0,final
        assert 'CHO LSP pilot' not in d.sh('dumpsys','input');print('PASS automatic expiry',flush=True)
        fixture();probe=Probe('capture')
        try:d.sh('wm','user-rotation','lock','1');time.sleep(1)
        finally:final=probe.stop();d.sh('wm','user-rotation',*original_rotation);time.sleep(.6)
        results.append({'case':'rotation','final':final});save(results)
        assert final and final['reason']=='display-changed' and not final['active'] and final['actions']==0,final
        assert 'CHO LSP pilot' not in d.sh('dumpsys','input');print('PASS rotation disposes monitor',flush=True)
        navigation_changed=True;choose_navigation('全面屏手势')
        assert d.sh('settings','get','secure','navigation_mode').strip()=='2'
        for side in ['left','right']:
            fixture();w,h=d.current_display_size();x=70 if side=='left' else w-70;y=h//2
            probe=Probe('capture')
            try:
                d.sh('input','swipe',str(x),str(y),str(x+(260 if side=='left' else -260)),str(y),'350');time.sleep(.8);foreground=d.resumed()
            finally:final=probe.stop()
            row={'case':'native-back-arbitration','side':side,'final':final,'foreground':foreground};results.append(row);save(results)
            assert final and not final['error'] and final['actions']<=1,row
            assert PACKAGE+'/.ProbeActivity' in foreground,'Expected one return, not two: '+str(row)
            assert 'CHO LSP pilot' not in d.sh('dumpsys','input')
            print('PASS native-back arbitration',side,'pilot actions',final['actions'],flush=True)
    finally:
        d.sh('wm','user-rotation',*original_rotation)
        if navigation_changed:choose_navigation('虚拟按键')
        if installed:subprocess.run(d.A+['uninstall',PACKAGE],check=True)
        d.sh('rm','-f',REMOTE)
        after=status();(OUT/'after-boundaries.json').write_text(json.dumps(after,ensure_ascii=False,indent=2),encoding='utf8')
        assert after==before,'Settings or CHO data changed'
        print('PASS boundary test state restored',flush=True)

if __name__=='__main__':main()
