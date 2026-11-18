"""Continue host lifecycle checks after the core input matrix has passed."""
from verify_lsp_systemui import *

def main():
    results.extend(json.loads((OUT/'results.json').read_text(encoding='utf8')))
    before=json.loads((OUT/'before.json').read_text(encoding='utf8'))
    try:
        d.sh('am','start','-W','-n',PILOT+'/dev.edgeflow.lsp.PilotActivity');time.sleep(.4)
        d.click('返回测试 30 秒');pid=d.sh('pidof',PILOT).strip();assert pid.isdecimal()
        # Keep the IPC fixture foreground while reading its reply on ColorOS.
        fixture();value=request('status');assert_host(value);assert value['active'],value
        d.sh('su','-c','kill -9 '+pid);time.sleep(.3)
        record('pilot-process-killed',old_pid=pid,**single_return())
        d.sh('am','start','-W','-n','dev.edgeflow/.MainActivity');fixture();start()
        pid=d.sh('pidof','dev.edgeflow').strip();assert pid.isdecimal()
        d.sh('su','-c','kill -9 '+pid);time.sleep(.3)
        record('cho-process-killed',old_pid=pid,**single_return())
        fixture();start();d.sh('am','start','-W','-n','dev.edgeflow/.MainActivity');time.sleep(.6)
        d.sh('input','keyevent','187');time.sleep(1)
        card=next(n for n in d.ui().iter('node') if n.get('text')=='Color Hand Operation' and n.get('class')=='android.widget.FrameLayout' and d.visible(n))
        l,t,r,b=d.bounds(card);x=(l+r)//2
        d.sh('input','swipe',str(x),str(t+int((b-t)*.65)),str(x),'200','330');time.sleep(2)
        pid=d.sh('sh','-c','pidof dev.edgeflow || true').strip();assert not pid,pid
        fixture();record('cho-recents-swiped',cho_pid_after_swipe=pid,**single_return())
        fixture();start();time.sleep(30.5);value=request('status');assert_host(value)
        assert not value['active'] and value['reason']=='expired',value
        stop();record('expiry-30s',final=value)
    finally:
        fixture();request('stop');after=status()
        (OUT/'after-tests.json').write_text(json.dumps(after,ensure_ascii=False,indent=2),encoding='utf8')
        assert after==before,'CHO data or phone settings changed'
    print('PASS',len(results),'host cases including lifecycle',flush=True)

if __name__=='__main__':main()
