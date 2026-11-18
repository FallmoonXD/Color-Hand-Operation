"""LSP tool actions and coexistence. Only the disposable fixture may be force-stopped."""
import json,time
import verify_lsp_windows as w
c=w.c;c.OUT=c.ROOT/'verification/lsp-tools-controls';w.OUT=c.OUT
results=[]

def record(name,**values):
    results.append({'case':name,**values});c.save('control-results.json',results);print('PASS',name,flush=True)

def own(label,timeout=6):
    end=time.monotonic()+timeout
    while time.monotonic()<end:
        for n in c.d.ui().iter('node'):
            if n.get('package')=='dev.edgeflow' and c.d.visible(n) and label in [n.get('text'),n.get('content-desc')]:return n
        time.sleep(.15)
    raise AssertionError('No CHO control: '+label)

def click(label):c.d.tap(own(label));time.sleep(.3)

def start(action,profile=None,fixture=True):
    if fixture:c.fixture()
    c.request('stop')
    s=c.start(profile or c.profile(action));assert s.get('tool_windows_available'),s
    c.swipe();w.await_tools(True)

def stop():
    if c.PACKAGE+'/' not in c.d.resumed():c.fixture()
    c.request('stop');w.await_tools(False);w.count(0)

def run():
    start('navigation_panel');click('返回')
    assert c.PACKAGE+'/.ProbeActivity' in c.d.resumed();own('关闭虚拟导航键');record('navigation-back-to-underlying-activity')
    click('主屏幕');assert 'com.android.launcher/' in c.d.resumed();own('关闭虚拟导航键');record('navigation-home-keeps-rail')
    click('最近任务');tree=c.d.ui();assert any(n.get('package')=='com.android.launcher' and n.get('text')=='清除' and c.d.visible(n) for n in tree.iter('node'))
    own('关闭虚拟导航键');record('navigation-recents-keeps-rail');click('关闭虚拟导航键');w.await_tools(False);stop()
    p=c.profile('navigation_panel',left_enabled=True,action_left_straight_short='quick_tools')
    start('navigation_panel',p);c.swipe('left');own('媒体音量');own('关闭虚拟导航键')
    assert len(w.tool_windows())==2;record('quick-tools-and-navigation-share-lease',lease=w.count(1))
    c.d.sh('input','tap','10',str(c.d.current_display_size()[1]//2));time.sleep(.7);own('关闭虚拟导航键')
    assert len(w.tool_windows())==1;record('outside-closes-transient-tool-keeps-navigation');stop()
    start('keyboard_shortcuts');click('添加');own('快捷方式名称');click('取消');own('全选，CTRL+A');stop();record('keyboard-editor-cancel-preserves-data')
    c.fixture();c.d.click('pilot text input');c.d.set_text('pilot text input','CHO keyboard');c.d.sh('input','keyevent','BACK')
    start('keyboard_shortcuts',fixture=False);click('全选，CTRL+A');w.await_tools(False);time.sleep(.5)
    c.d.sh('input','text','Z');time.sleep(.2)
    text=c.d.wait_node('pilot text input').get('text');assert text=='Z',text
    stop();c.d.sh('input','keyevent','BACK');record('keyboard-ctrl-a-reaches-underlying-editor')
    remaining()

def remaining():
    c.fixture();c.d.sh('am','start','-W','-a','android.settings.SETTINGS')
    start('task_switcher',fixture=False);node=own('切换到 CHO LSP input fixture',timeout=14);c.d.tap(node)
    end=time.monotonic()+5
    while c.PACKAGE+'/.SecondActivity' not in c.d.resumed() and time.monotonic()<end:time.sleep(.2)
    assert c.PACKAGE+'/.SecondActivity' in c.d.resumed();stop();record('task-list-loaded-and-fixture-task-launched')
    start('quick_launcher');click('编辑');own('完成');click('完成');stop();record('launcher-editor-without-layout-mutation')
    start('quick_tools');click('关闭当前应用');end=time.monotonic()+5
    while c.PACKAGE+'/' in c.d.resumed() and time.monotonic()<end:time.sleep(.2)
    assert c.PACKAGE+'/' not in c.d.resumed(),'Disposable fixture was not closed'
    assert not c.d.sh('sh','-c','pidof '+c.PACKAGE+' || true').strip(),'Fixture process remained'
    c.fixture();stop();record('close-current-app-stops-only-fixture')
    assert c.status()==json.loads((c.OUT/'before.json').read_text(encoding='utf8'))
    record('original-preferences-and-device-settings-preserved')

if __name__=='__main__':
    import sys
    if len(sys.argv)>1 and sys.argv[1]=='remaining':
        results.extend(json.loads((c.OUT/'control-results.json').read_text(encoding='utf8')));remaining()
    else:run()
