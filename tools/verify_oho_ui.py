"""OHO settings hierarchy, non-mutating navigation, and restored arrow verification.

Uses the independent accessibility probe. No force-stop, lock, or reboot. The
right long-swipe switch is tested off and restored to its original effective
state. Original preference keys and all twelve mappings must remain unchanged.
"""
import json,time,xml.etree.ElementTree as E
import device as d

def prefs():
    return {n.get('name'):{'type':n.tag,'value':n.text if n.tag=='string' else n.get('value')}
            for n in E.fromstring(d.sh('run-as',d.P,'cat','shared_prefs/edgeflow.xml'))}

def find(label,tree=None):
    return next((n for n in (tree if tree is not None else d.ui()).iter('node')
                 if (n.get('text')==label or n.get('content-desc')==label) and d.visible(n)),None)

def back():
    d.sh('input','keyevent','4');time.sleep(.3)

def scroll_click(label,up=False):
    for _ in range(35):
        tree=d.ui();n=find(label,tree)
        if n is not None:d.tap(n);time.sleep(.3);return
        sc=next((n for n in tree.iter('node') if n.get('class') in ('android.widget.ScrollView','android.widget.ListView') and d.visible(n)),None)
        a,b,c,e=d.bounds(sc) if sc is not None else (100,420,1164,2500)
        target=next((n for n in tree.iter('node') if n.get('text')==label or n.get('content-desc')==label),None)
        reverse=up or (target is not None and d.bounds(target)[3]<=b)
        x=(a+c)//2;y1=int(b+(e-b)*.70);y2=int(b+(e-b)*.30)
        if reverse:y1,y2=y2,y1
        d.sh('input','swipe',str(x),str(y1),str(x),str(y2),'450');time.sleep(.3)
    raise AssertionError('Missing visible setting: '+label)

def home():
    d.sh('am','start','-W','-f','0x14000000','-n',d.P+'/.MainActivity');time.sleep(.4)
    for _ in range(8):
        if find('更多选项') is not None:break
        back()
    else:raise AssertionError('Could not return to home')
    for _ in range(4):
        if find('启用手势') is not None:return
        d.sh('input','swipe','630','900','630','2300','250');time.sleep(.2)
    raise AssertionError('Home master switch is missing')

def screenshot(name):
    d.shot('oho-ui-'+name)

def run():
    r={};before=prefs()
    old=json.loads((d.B/'oho-ui-prefs-before.json').read_text(encoding='utf-8'))
    assert all(before.get(k)==v for k,v in old.items()),'An original preference changed during deployment'
    r['original_preferences_preserved_at_launch']=len(old)
    home();tree=d.ui()
    sequence=['启用手势','左侧触钮','右侧触钮','添加触钮','高级设置','手势设置']
    ys=[d.bounds(find(s,tree))[1] for s in sequence]
    assert ys==sorted(ys),ys
    assert find('移除触钮',tree).get('enabled')=='false'
    assert find('启用左侧触钮',tree).get('checked')=='true'
    assert find('启用右侧触钮',tree).get('checked')=='true'
    screenshot('home');r['home_order_and_separate_switches']=True
    d.click('左侧触钮');tree=d.ui()
    for label in ['短滑直线向右','短滑对角线向上','短滑对角线向下',
                  '长滑直线向右','长滑对角线向上','长滑对角线向下','对齐触钮']:
        assert find(label,tree) is not None,label
    screenshot('left');r['short_long_direction_rows']=True
    configs=[('快速工具','快速工具','编辑按钮'),('小组件面板','小组件面板','循环翻页'),
             ('快速启动','快速启动','列数'),('任务切换器','任务切换器','显示任务数量'),
             ('虚拟触控板','虚拟触控板','移动速度'),('虚拟导航键','虚拟导航键','按键触控范围'),
             ('键盘快捷方式','键盘快捷方式','预览键盘快捷方式')]
    r['action_gear_routes']=[]
    for label,title,control in configs:
        d.click('短滑直线向右');d.wait_node('选择操作')
        scroll_click('设置'+label);d.wait_node(title);d.wait_node(control)
        if label=='快速工具':screenshot('tools-settings')
        if label=='快速启动':
            d.click('列数');d.wait_node('2 列');d.wait_node('3 列');d.wait_node('4 列');back()
        if label=='任务切换器':
            for n in range(8,14):d.wait_node(str(n)+' 个任务')
        back();d.wait_node('选择操作');back();d.wait_node('左侧触钮')
        r['action_gear_routes'].append(label)
    assert prefs()==before,'Navigating settings must not mutate configuration'
    r['navigation_has_no_preference_writes']=True
    home();d.click('高级设置');d.wait_node('应用例外');d.wait_node('隐藏触钮');screenshot('advanced');back()
    d.click('手势设置');d.wait_node('短距离滑动');d.wait_node('长距离滑动');screenshot('gestures');back()
    d.click('更多选项');d.wait_node('关于');d.wait_node('恢复默认设置');d.click('权限与后台运行');d.wait_node('系统自启动');back()
    d.click('更多选项');d.click('备份与恢复');d.wait_node('导出配置');d.wait_node('导入配置');back()
    assert prefs()==before
    r['advanced_gesture_permission_backup_routes']=True
    original_long=before.get('right_long_enabled',{'value':'true'})['value']=='true'
    assert original_long,'Test expects default right long-swipe behavior'
    try:
        home();d.click('右侧触钮');d.click('长距离滑动');time.sleep(.4)
        assert prefs()['right_long_enabled']['value']=='false'
        assert find('长滑直线向左') is None and find('短滑直线向左') is not None
        assert prefs()['action_right_straight_long']==before['action_right_straight_long']
        back();d.click('高级设置')
        d.sh('input','swipe','1259','1600','800','1600','700');time.sleep(.7)
        assert find('更多选项') is not None,'Disabled long swipe should still execute short Back'
        r['disabled_long_swipe_executes_short_and_keeps_mapping']=True
    finally:
        if prefs().get('right_long_enabled',{}).get('value')!='true':
            home();d.click('右侧触钮');d.click('长距离滑动');back()
    final=prefs();assert all(final.get(k)==v for k,v in old.items())
    r['original_preferences_preserved_after_tests']=len(old)
    r['original_twelve_actions_unchanged']=sum(k.startswith(('action_left_','action_right_')) for k in old)==12
    home();screenshot('home-final')
    (d.B/'oho-ui-validation.json').write_text(json.dumps(r,ensure_ascii=False,indent=2),encoding='utf-8')
    print(json.dumps(r,ensure_ascii=False),flush=True)

if __name__=='__main__':run()
