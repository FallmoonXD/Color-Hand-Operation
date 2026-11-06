"""Device checks for persistent controls, anchored menus and incremental windows.
Uses the non-suppressing accessibility probe; restores every setting it touches.
"""
import json,re,time,subprocess
import device as d

before=d.preferences();results={}
assert before.get('handle_pair_count',1)==1
assert before.get('enabled',True) and before.get('left_enabled',True) and before.get('right_enabled',True)
assert all(not before.get(k,False) for k in ('extra_left_enabled','extra_right_enabled','extra2_left_enabled','extra2_right_enabled'))

def same_node(a,b):return (a.get('node-hash'),a.get('window-id'))==(b.get('node-hash'),b.get('window-id'))
def window_ids():
    rows=d.sh('dumpsys','window','windows').splitlines();out={}
    for line in rows:
        if 'Window #' not in line or not any(x in line for x in ('手势区域','手势绘制')):continue
        match=re.search(r'Window\{(\w+) u0 (Color Hand Operation [^}]+)\}',line)
        if match:out[match[2]]=match[1]
    return out
def home_top():
    d.main_home()
    for _ in range(6):
        if any(n.get('content-desc')=='启用手势' and d.visible(n) for n in d.ui().iter('node')):return
        d.sh('input','swipe','620','800','620','2200','450');time.sleep(.2)
    raise AssertionError('No home master switch')
def remove_pair():
    count=d.preferences()['handle_pair_count'];d.click_item('移除触钮');d.click('移除第 '+str(count)+' 对触钮');time.sleep(.5)

try:
    home_top();master=d.node('启用手势');left=d.node('启用左侧触钮');right=d.node('启用右侧触钮');windows=window_ids();assert len(windows)==8,windows
    d.shot('polish-home');d.sh('dumpsys','gfxinfo',d.P,'reset')
    record=subprocess.Popen(d.A+['shell','screenrecord','--bit-rate','6000000','--time-limit','8','/sdcard/cho-switch-test.mp4'],stdout=subprocess.DEVNULL,stderr=subprocess.PIPE)
    time.sleep(.4)
    for label,key,original in [('启用手势','enabled',master),('启用左侧触钮','left_enabled',left),('启用右侧触钮','right_enabled',right)]:
        d.tap(original);time.sleep(.4)
        assert d.preferences()[key] is False,key
        after=d.node(label);assert same_node(original,after),(label,'node replaced')
        assert window_ids()==windows,(label,'gesture windows recreated')
        d.tap(after);time.sleep(.4)
        assert d.preferences()[key] is True
        assert same_node(original,d.node(label))
        assert window_ids()==windows
    record.communicate(timeout=12);assert record.returncode==0
    subprocess.run(d.A+['pull','/sdcard/cho-switch-test.mp4',str(d.B/'polish-switch-motion.mp4')],check=True,capture_output=True)
    d.sh('rm','/sdcard/cho-switch-test.mp4')
    (d.B/'polish-switch-framestats.txt').write_text(d.sh('dumpsys','gfxinfo',d.P,'framestats'),encoding='utf-8')
    results['master_and_both_switches_retain_views_and_eight_windows']=True

    # The toolbar action follows the title, and its popup starts below that row.
    heading=next(n for n in d.ui().iter('node') if n.get('content-desc')=='Color Hand Operation')
    more=d.node('更多选项');ha,hb,hc,he=d.bounds(heading);ma,mb,mc,me=d.bounds(more)
    assert abs((hb+he)/2-(mb+me)/2)<20,(d.bounds(heading),d.bounds(more))
    assert hc<=ma
    d.click('更多选项');about=d.wait_node('关于');assert d.bounds(about)[0]>400;d.shot('polish-more')
    d.sh('input','keyevent','4');time.sleep(.35);assert same_node(master,d.node('启用手势'))
    d.click('更多选项');d.click('关于');d.wait_node('0.3.4-ui-polish');d.sh('input','keyevent','4');time.sleep(.5)
    results['more_anchor_back_and_about_navigation']=True

    home_top();master=d.node('启用手势');left=d.node('启用左侧触钮')
    d.click_item('添加触钮');time.sleep(.5);assert d.preferences()['handle_pair_count']==2
    assert same_node(left,d.node('启用左侧触钮'));assert same_node(master,d.node('启用手势'))
    d.click_item('添加触钮');time.sleep(.5);assert d.preferences()['handle_pair_count']==3
    assert d.find_item('添加触钮').get('enabled')=='false';assert window_ids()==windows
    d.click_item('移除触钮');d.wait_node('移除第 3 对触钮');d.shot('polish-remove');d.click('取消');time.sleep(.35);assert d.preferences()['handle_pair_count']==3
    remove_pair();assert d.preferences()['handle_pair_count']==2
    remove_pair();assert d.preferences()['handle_pair_count']==1
    assert same_node(left,d.node('启用左侧触钮'));assert same_node(master,d.node('启用手势'));assert window_ids()==windows
    results['add_remove_cancel_keeps_page_and_other_controls']=True

    home_top();d.click('右侧触钮');longer=d.find_item('长距离滑动');long_before=before.get('right_long_enabled',True)
    d.tap(longer);time.sleep(.4);assert d.preferences()['right_long_enabled']!=long_before;assert same_node(longer,d.node('长距离滑动'))
    d.tap(d.node('长距离滑动'));time.sleep(.4);assert d.preferences()['right_long_enabled']==long_before;assert same_node(longer,d.node('长距离滑动'))
    d.sh('input','keyevent','4');time.sleep(.4)
    results['secondary_long_switch_keeps_its_view']=True
    d.find_item('触摸宽度',True);d.shot('polish-geometry')
    assert d.preferences()==before
    results['all_preferences_restored_exactly']=True
finally:
    # Restore only the specific settings exercised above via their real controls.
    if d.preferences().get('enabled',True)!=before.get('enabled',True):home_top();d.click('启用手势')
    for side in ('left','right'):
        if d.preferences().get(side+'_enabled',True)!=before.get(side+'_enabled',True):home_top();d.click('启用'+('左' if side=='left' else '右')+'侧触钮')
    if d.preferences().get('handle_pair_count',1)>1:
        home_top()
        while d.preferences().get('handle_pair_count',1)>1:remove_pair()
    if d.preferences().get('right_long_enabled',True)!=before.get('right_long_enabled',True):home_top();d.click('右侧触钮');d.click_item('长距离滑动')
    results['final_preferences_equal']=d.preferences()==before
    (d.B/'ui-polish-validation.json').write_text(json.dumps(results,ensure_ascii=False,indent=2),encoding='utf-8')
print(json.dumps(results,ensure_ascii=False,indent=2))
