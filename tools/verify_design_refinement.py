"""Focused on-device checks for 0.3.5; keep all user configuration unchanged."""
import json, time, hashlib
import device as d

before=d.preferences();result={}
baseline=json.loads((d.B/'design-refinement-baseline.json').read_text(encoding='utf-8'))

def save():
    (d.B/'design-refinement-validation.json').write_text(json.dumps(result,ensure_ascii=False,indent=2),encoding='utf-8')

def back():
    d.sh('input','keyevent','4');time.sleep(.4)

def home_top():
    d.main_home()
    for _ in range(8):
        if any(n.get('content-desc')=='启用手势' and d.visible(n) for n in d.ui().iter('node')):return
        d.sh('input','swipe','620','800','620','2200','450');time.sleep(.2)
    raise AssertionError('Home did not reach top')

def sheet_node(text,title='选择操作'):
    tree=d.ui();heading=next(n for n in tree.iter('node') if n.get('text')==title and d.visible(n));window=heading.get('window-id')
    return next(n for n in tree.iter('node') if n.get('window-id')==window and (n.get('text')==text or n.get('content-desc')==text) and d.visible(n))

def vibration(amplitude):
    n=d.find_item('振动强度',True);a,b,c,e=d.bounds(n);pad=49
    x=round(a+pad+(c-a-2*pad)*amplitude/255)
    d.sh('input','tap',str(x),str((b+e)//2));time.sleep(.35)
    return n,d.node('振动强度')

try:
    home_top();d.shot('refinement-home')
    tree=d.ui()
    assert any('从屏幕边缘滑动' in n.get('text','') for n in tree.iter('node'))
    result['home_rows']={s:d.bounds(d.node(s)) for s in ['左侧触钮','右侧触钮','高级设置','手势设置']}

    d.open_handle('right');page_window=d.wait_node('短距离滑动').get('window-id')
    d.click_item('短滑直线向左',True);d.wait_node('选择操作');d.shot('refinement-action-sheet')
    heading=d.wait_node('选择操作');sheet_window=heading.get('window-id')
    assert sheet_window!=page_window
    d.tap(sheet_node('关闭选择操作'));time.sleep(.4);assert d.preferences()==before
    d.click_item('短滑直线向左',True);d.wait_node('选择操作');back();d.wait_node('短距离滑动')
    d.click_item('短滑直线向左',True);heading=d.wait_node('选择操作');a,b,c,e=d.bounds(heading)
    d.sh('input','tap','630',str(max(140,b-180)));time.sleep(.4)
    assert not any(n.get('text')=='选择操作' and d.visible(n) for n in d.ui().iter('node'))
    result['sheet_close_back_and_outside_preserve_mapping']=True

    d.click_item('短滑直线向左',True);d.wait_node('选择操作')
    for title in ['快速工具','小组件','快速启动','任务切换器','虚拟触控板','导航键面板','键盘快捷方式']:
        d.click_item('设置'+title,True);d.wait_node(title);assert d.preferences()==before
        back();d.wait_node('选择操作')
    result['all_seven_action_settings_and_return']=True
    d.click_item('启动指定应用…');d.wait_node('启动应用');d.shot('refinement-app-sheet');d.click('关闭启动应用');time.sleep(.4)
    assert d.preferences()==before
    result['application_picker_uses_sheet_and_cancel_preserves_mapping']=True

    d.open_action_chooser('right');d.tap(sheet_node('返回'));time.sleep(.4)
    d.wait_node('短距离滑动');assert d.preferences()==before
    result['select_current_action_preserves_mapping']=True

    d.main_link('应用例外');d.wait_node('在这些应用中停用手势')
    end=time.monotonic()+15
    while time.monotonic()<end:
        tree=d.ui()
        checks=[n for n in tree.iter('node') if n.get('content-desc','').startswith('在“') and n.get('content-desc','').endswith('”中停用手势')]
        if checks:break
        time.sleep(.3)
    assert checks,'Exceptions did not load'
    d.shot('refinement-exceptions');result['exception_app_count']=len(checks)
    assert d.preferences()==before

    d.main_link('手势设置');bar=d.find_item('振动强度',True)
    assert bar.get('state-description')=='0%',bar.attrib
    assert d.wait_node('0：使用系统触觉反馈') is not None
    old,new=vibration(128)
    assert old.get('node-hash')==new.get('node-hash'),'Strength rebuilt page'
    amplitude=d.preferences()['vibration_strength'];assert 126<=amplitude<=129,amplitude
    assert new.get('state-description') in ['49%','50%','51%'],new.attrib
    d.shot('refinement-vibration');vibration(before['vibration_strength'])
    assert d.preferences()==before
    result['vibration_percentage_native_precision_and_same_view']=True

    d.main_home();d.find_item('触摸宽度',True);d.shot('refinement-touch-region')
    current={name:d.preferences(name) for name in baseline}
    normalized=json.loads(json.dumps(current,default=lambda value:sorted(value)))
    assert normalized==baseline,'Stored configuration differs from baseline'
    assert hashlib.sha256(d.sh('run-as',d.P,'cat','files/tools-layout-v1.json').encode()).hexdigest()==(d.B/'design-refinement-tools-hash.txt').read_text().strip()
    result['all_four_stores_and_tools_layout_preserved']=True
finally:
    if d.preferences().get('vibration_strength')!=before.get('vibration_strength'):
        d.main_link('手势设置');vibration(before['vibration_strength'])
    result['final_preferences_equal']=d.preferences()==before;save()

print(json.dumps(result,ensure_ascii=False,indent=2))
