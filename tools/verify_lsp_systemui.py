"""Run the installed LSP pilot through signed app IPC, with no Root input backend."""
import json,re,shlex,time
from pathlib import Path
from verify_lsp_input import d,ROOT,PACKAGE,fixture,status
from verify_lsp_boundaries import choose_navigation
from lsp_control import request

OUT=ROOT/'verification/lsp-systemui'
PILOT='dev.edgeflow.lsp.pilot'
results=[]

def record(name,**values):
    row={'case':name,**values};results[:]=[r for r in results if r['case']!=name];results.append(row)
    (OUT/'results.json').write_text(json.dumps(results,ensure_ascii=False,indent=2),encoding='utf8')
    print('PASS',name,flush=True)

def assert_host(value):
    pid=int(d.sh('pidof','com.android.systemui').strip())
    uid=int(re.search(r'(?m)^package:com\.android\.systemui\s+uid:(\d+)',d.sh('pm','list','packages','-U','com.android.systemui'))[1])
    assert value.get('connected') and value.get('client_permission'),value
    assert value.get('uid')==uid and value.get('pid')==pid and value['uid']!=value['client_uid'],value
    assert not value.get('error'),value

def start(mode='capture'):
    result=request(mode);assert_host(result);assert result.get('active'),result;return result

def stop():
    result=request('stop');assert_host(result);assert not result.get('active'),result
    assert 'CHO LSP pilot' not in d.sh('dumpsys','input'),'Monitor leaked'
    return result

def marker():
    token='LSP_HOST_'+str(time.time_ns());d.sh('log','-t','CHOLspFixture',token);return token

def logs(token):return d.sh('logcat','-d','-v','brief','-s','CHOLspFixture:I').split(token)[-1].strip()

def inward(side='right'):
    w,h=d.current_display_size();x=70 if side=='left' else w-70;y=h//2
    d.sh('input','swipe',str(x),str(y),str(x+(260 if side=='left' else -260)),str(y),'350');time.sleep(.4)

def single_return():
    inward();foreground=d.resumed();final=stop()
    assert PACKAGE+'/.ProbeActivity' in foreground and final['actions']==1 and final['claims']==1,(foreground,final)
    return {'foreground':foreground,'final':final}

def main():
    before=json.loads((OUT/'before.json').read_text(encoding='utf8'))
    rotation=d.sh('wm','user-rotation').strip().split();nav_changed=False
    try:
        for mode in ['observe','capture']:
            for side in ['left','right']:
                for kind in ['tap','vertical','inward']:
                    fixture();start(mode);w,h=d.current_display_size();x=70 if side=='left' else w-70;y=h//2;t=marker()
                    if kind=='tap':d.sh('input','tap',str(x),str(y))
                    elif kind=='vertical':d.sh('input','swipe',str(x),str(y),str(x),str(y-260),'350')
                    else:inward(side)
                    time.sleep(.2);foreground=d.resumed();events=logs(t);final=stop()
                    expected=1 if mode=='capture' and kind=='inward' else 0
                    assert final['actions']==expected and final['claims']==expected and not final['rejected_actions'],final
                    assert PACKAGE+('/.ProbeActivity' if expected else '/.SecondActivity') in foreground,foreground
                    if expected:assert 'ACTION_CANCEL' in events and 'CLICK' not in events,events
                    else:
                        assert 'ACTION_UP' in events and 'ACTION_CANCEL' not in events,events
                        if kind=='tap':assert 'CLICK '+side in events,events
                    record(mode+'-'+side+'-'+kind,final=final,foreground=foreground,events=events)
        driver=d.sh('pm','path',PACKAGE).strip().removeprefix('package:')
        for side in ['left','right']:
            for kind in ['cancel','retreat','multi-before','multi-after','mouse']:
                fixture();start();w,h=d.current_display_size();x=70 if side=='left' else w-70;t=marker()
                command='CLASSPATH='+shlex.quote(driver)+' app_process /system/bin dev.edgeflow.lspfixture.Events '+shlex.join([kind,str(x),str(h//2),str(260 if side=='left' else -260)])
                d.sh('su','-c',command);time.sleep(.2);final=stop();foreground=d.resumed()
                assert final['actions']==0 and final['claims']==(0 if kind in ['mouse','multi-before'] else 1),final
                assert PACKAGE+'/.SecondActivity' in foreground,foreground
                record(side+'-'+kind,final=final,foreground=foreground,events=logs(t))
                if kind=='mouse':d.sh('input','tap',str(w//2),str(h//2))
        fixture();start();d.sh('wm','user-rotation','lock','1');time.sleep(.7)
        result=request('status');assert_host(result);assert not result['active'] and result['reason']=='display-changed',result
        stop();record('rotation',final=result);d.sh('wm','user-rotation',*rotation);time.sleep(.5)
        nav_changed=True;choose_navigation('全面屏手势')
        for side in ['left','right']:
            fixture();start();inward(side);time.sleep(.4);final=stop();foreground=d.resumed()
            assert PACKAGE+'/.ProbeActivity' in foreground and final['actions']<=1,(foreground,final)
            record('native-back-'+side,final=final,foreground=foreground)
        choose_navigation('虚拟按键');nav_changed=False
        # Verify the real controller UI can arm the host before its process ends.
        d.sh('am','start','-W','-n',PILOT+'/dev.edgeflow.lsp.PilotActivity');time.sleep(.4)
        d.click('返回测试 30 秒');pilot_pid=d.sh('pidof',PILOT).strip();assert pilot_pid.isdecimal()
        fixture();r=request('status');assert_host(r);assert r['active'],r
        d.sh('su','-c','kill -9 '+pilot_pid);time.sleep(.4)
        record('pilot-process-killed',old_pid=pilot_pid,**single_return())
        d.sh('am','start','-W','-n','dev.edgeflow/.MainActivity');fixture();start()
        pid=d.sh('pidof','dev.edgeflow').strip();assert pid.isdecimal();d.sh('su','-c','kill -9 '+pid);time.sleep(.4)
        record('cho-process-killed',old_pid=pid,**single_return())
        fixture();start();d.sh('am','start','-W','-n','dev.edgeflow/.MainActivity');time.sleep(.6);d.sh('input','keyevent','187');time.sleep(1)
        card=next(n for n in d.ui().iter('node') if n.get('text')=='Color Hand Operation' and n.get('class')=='android.widget.FrameLayout' and d.visible(n))
        l,t,r,b=d.bounds(card);x=(l+r)//2;d.sh('input','swipe',str(x),str(t+int((b-t)*.65)),str(x),'200','330');time.sleep(2)
        pid=d.sh('sh','-c','pidof dev.edgeflow || true').strip();assert not pid,'CHO task swipe did not end its process'
        fixture();record('cho-recents-swiped',cho_pid_after_swipe=pid,**single_return())
        fixture();start();time.sleep(30.5);result=request('status');assert_host(result)
        assert not result['active'] and result['reason']=='expired',result
        stop();record('expiry-30s',final=result)
    finally:
        try:fixture();request('stop')
        finally:
            d.sh('wm','user-rotation',*rotation)
            if nav_changed:choose_navigation('虚拟按键')
            after=status();(OUT/'after-tests.json').write_text(json.dumps(after,ensure_ascii=False,indent=2),encoding='utf8')
            assert after==before,'CHO data or phone settings changed'
    print('PASS',len(results),'SystemUI cases and original data retained',flush=True)

if __name__=='__main__':main()
