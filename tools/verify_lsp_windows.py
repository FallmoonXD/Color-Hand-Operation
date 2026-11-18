"""Real delegated-window lifecycle checks; no CHO settings or widgets are edited."""
import json,subprocess,time
from pathlib import Path
import verify_lsp_configured as c

c.OUT=c.ROOT/'verification/lsp-tools'
OUT=c.OUT
TITLE='CHO delegated token probe'
results=[]

def record(name,**values):
    results.append({'case':name,**values})
    (OUT/'window-results.json').write_text(json.dumps(results,ensure_ascii=False,indent=2),encoding='utf8')
    print('PASS',name,flush=True)

def windows():return c.d.sh('dumpsys','window','windows')

def no_window():
    end=time.monotonic()+3
    while time.monotonic()<end:
        value=windows()
        if TITLE not in value:return value
        time.sleep(.1)
    raise AssertionError('Delegated window remains after revocation')

def count(expected):
    result=c.request('window_probe_status')
    assert result.get('window_leases')==expected,result
    return result

def mount():
    result=c.request('window_probe');assert result.get('window_added'),result
    value=windows();assert TITLE in value,value[-1000:]
    return result,value

def run():
    before=json.loads((OUT/'before.json').read_text(encoding='utf8'))
    c.fixture();count(0)
    mounted,value=mount();count(1)
    (OUT/'framework-window-mounted.txt').write_text(value,encoding='utf8')
    (OUT/'framework-window-mounted.png').write_bytes(subprocess.check_output(c.d.A+['exec-out','screencap','-p']))
    record('ordinary-uid-window',result=mounted)
    c.request('window_probe_stop');no_window();count(0)
    reused=c.request('window_probe_reuse');assert not reused.get('window_added') and reused.get('window_error'),reused
    no_window();record('revocation-removes-window-and-rejects-old-token',reuse=reused)
    for index in range(3):
        mount();count(1);c.request('window_probe_stop');no_window();count(0)
    record('repeated-grant-revoke')
    mount();pid=c.d.sh('pidof',c.PACKAGE).strip();assert pid.isdecimal()
    c.d.sh('su','-c','kill -9 '+pid);no_window()
    c.fixture();count(0);record('host-death-releases-framework-token')
    # Freeze the app: revocation must work without asking its UI thread to run.
    mount();pid=c.d.sh('pidof',c.PACKAGE).strip();assert pid.isdecimal()
    try:
        c.d.sh('su','-c','kill -STOP '+pid)
        c.d.sh('am','broadcast','-a','dev.edgeflow.lsp.pilot.CONTROL_REQUEST','-p','com.android.systemui','--es','operation','window_probe_stop')
        # Revocation runs in SystemUI and must not need the frozen UI thread.
        end=time.monotonic()+17
        while TITLE in windows() and time.monotonic()<end:time.sleep(.3)
        assert TITLE not in windows(),'Expired grant kept a frozen window'
    finally:c.d.sh('su','-c','kill -CONT '+pid)
    count(0);record('controller-revokes-frozen-host-window')
    denial=c.d.sh('service','call','activity',str(0x43484f),'i32','0')
    (OUT/'framework-window-shell-denial.txt').write_text(denial,encoding='utf8')
    assert 'SecurityException' in denial or '00530020' in denial or 'Only SystemUI' in denial,denial
    record('shell-cannot-use-framework-interface')
    state=c.status();assert state==before,'User preferences or device settings changed'
    record('original-settings-preserved')
    print('PASS',len(results),'window lifecycle cases',flush=True)

def tool_windows():
    import re
    return [block for block in re.split(r'(?=  Window #\d+)',windows()) if re.search(r'^  Window #',block) and 'package=dev.edgeflow appop=' in block and 'ty=ACCESSIBILITY_OVERLAY' in block]

def await_tools(shown):
    end=time.monotonic()+4
    while time.monotonic()<end:
        value=tool_windows()
        if bool(value)==shown:return value
        time.sleep(.1)
    raise AssertionError('Tool window visibility mismatch: '+str(shown))

def tools(remaining=False):
    global results
    results=json.loads((OUT/'tool-results.json').read_text(encoding='utf8')) if remaining else []
    before=json.loads((OUT/'before.json').read_text(encoding='utf8'))
    def save_case(name,**values):
        results.append({'case':name,**values});(OUT/'tool-results.json').write_text(json.dumps(results,ensure_ascii=False,indent=2),encoding='utf8');print('PASS',name,flush=True)
    def start(action,side='right'):
        c.request('stop');c.request('tool_probe_stop');c.fixture()
        initial=c.start(c.profile(action,side));assert initial.get('tool_windows_available'),initial
        c.swipe(side);await_tools(True)
        end=time.monotonic()+4
        while time.monotonic()<end:
            status=c.request('status')
            if status.get('actions')==1:break
            time.sleep(.1)
        assert status.get('actions')==1 and status.get('rejected_actions')==0,status
        return status
    def underlying():
        assert c.PACKAGE+'/.SecondActivity' in c.d.resumed()
        assert any(n.get('text')=='SecondActivity clicks=0' for n in c.d.ui().iter('node'))
    if not remaining:
        for action in ['quick_tools','widgets']:
            for side in ['left','right']:
                start(action,side);c.d.sh('input','keyevent','BACK');await_tools(False);underlying()
                c.request('stop');count(0);save_case(action+'-'+side+'-gesture-and-back')
            start(action);c.d.sh('input','tap','10',str(c.d.current_display_size()[1]//2));await_tools(False);underlying()
            c.request('stop');count(0);save_case(action+'-outside-tap-consumed')
        start('widgets');c.d.open_widget_page_menu();c.d.click('添加插件');c.d.click('搜索应用或插件');time.sleep(.4)
        assert 'mInputShown=true' in c.d.sh('dumpsys','input_method')
        c.d.sh('input','keyevent','BACK');time.sleep(.4);c.d.wait_node('搜索应用或插件')
        c.d.click('关闭添加插件');c.d.wait_widget_panel();c.request('stop');await_tools(False);count(0)
        save_case('widget-menu-picker-ime-and-layered-close')
    start('widgets');c.d.sh('input','swipe','350','1040','350','1040','850');c.d.click('移除插件');c.d.wait_node('取消');c.d.click('取消');c.d.wait_widget_panel()
    c.request('stop');await_tools(False);save_case('widget-remove-confirmation-cancel')
    start('widgets');c.d.open_widget_page_menu();c.d.click('添加插件');c.d.wait_node('搜索应用或插件')
    c.request('stop');await_tools(False);count(0);save_case('stop-removes-parent-and-child-windows')
    start('quick_tools')
    for _ in range(3):c.swipe();await_tools(True)
    time.sleep(.3);assert len(tool_windows())==1,'Reopening leaves duplicate tools'
    c.request('stop');await_tools(False);count(0);save_case('rapid-reopen-keeps-latest-window')
    start('widgets');pid=c.d.sh('pidof','dev.edgeflow').strip();assert pid.isdecimal();c.d.sh('su','-c','kill -9 '+pid)
    await_tools(False);count(0);c.swipe();await_tools(True);c.request('stop');await_tools(False);count(0)
    save_case('host-death-and-next-gesture-cold-start')
    assert c.status()==before,'Original CHO settings changed'
    save_case('original-preferences-and-widget-layout-retained')

if __name__=='__main__':
    import sys
    tools(len(sys.argv)>2 and sys.argv[2]=='remaining') if len(sys.argv)>1 and sys.argv[1]=='tools' else run()
