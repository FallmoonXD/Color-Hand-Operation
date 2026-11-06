"""Navigation editor actions, ColorOS page return and source-config preservation."""
import json,time
import device as d
from preference_trial import PreferenceTrial

out={}
def back():d.sh('input','keyevent','4');time.sleep(.5)
def current():return json.loads(d.preferences().get('navigation_actions','["back","home","recents"]'))
def switch():return next(n for n in d.ui().iter('node') if n.get('class')=='android.widget.Switch' and n.get('content-desc')=='纵向排列' and d.visible(n))
def edit_first():
 tree=d.ui();node=next(n for n in tree.iter('node') if n.get('content-desc','').startswith('编辑1. ') and d.visible(n));d.tap(node);time.sleep(.3)

with PreferenceTrial({'edgeflow':{'navigation_actions','navigation_vertical'}}):
 d.action_settings('导航键面板');initial=current();d.shot('continuous-pages-navigation')
 sw=switch();identity=sw.get('node-hash');before=sw.get('checked');d.tap(sw);time.sleep(.45)
 assert switch().get('node-hash')==identity
 assert switch().get('checked')!=before
 d.tap(switch());time.sleep(.4);assert switch().get('checked')==before
 out['switch_animates_without_rebuilding_page']=True
 edit_first();tree=d.ui();assert not any(n.get('text')=='前移' and d.visible(n) for n in tree.iter('node'));d.shot('continuous-pages-navigation-menu')
 if len(initial)>1:
  d.click_item('后移');expected=initial.copy();expected[0],expected[1]=expected[1],expected[0];assert current()==expected;out['reorder_saved']=True;edit_first()
 d.click_item('更换动作');d.wait_node('选择导航动作');d.shot('continuous-pages-navigation-actions')
 assert not any(n.get('content-desc')=='应用启动器' for n in d.ui().iter('node'))
 d.click_item('截屏',True);d.wait_node('导航键面板');assert current()[0]=='screenshot';out['replace_saved_and_returns_to_editor']=True
 if len(current())<5:
  d.click_item('添加按键',True);d.wait_node('选择导航动作');d.click_item('通知栏',True);d.wait_node('导航键面板');assert current()[-1]=='notifications';out['add_saved_and_returns_to_editor']=True
 edit_first();d.click_item('移除');d.wait_node('导航键面板');assert 'screenshot' not in current();out['remove_saved']=True
 back();d.wait_node('选择操作');out['nested_navigation_returns_to_original_chooser']=True
out['preferences_restored']=True
(d.B/'continuous-pages-navigation-validation.json').write_text(json.dumps(out,ensure_ascii=False,indent=2),encoding='utf-8')
print(json.dumps(out,ensure_ascii=False))
