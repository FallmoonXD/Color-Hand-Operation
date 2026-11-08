"""Navigation editor actions, ColorOS page return and source-config preservation."""
import json,time
import device as d
from preference_trial import PreferenceTrial

out={}
def back():d.sh('input','keyevent','4');time.sleep(.5)
def current():return json.loads(d.preferences().get('navigation_actions','["back","home","recents"]'))
def edit_first():
 tree=d.ui();node=next(n for n in tree.iter('node') if n.get('content-desc','').startswith('编辑1. ') and d.visible(n));d.tap(node);time.sleep(.3)

with PreferenceTrial({'edgeflow':{'navigation_actions'}}):
 d.action_settings('虚拟导航键');initial=current();d.shot('continuous-pages-navigation')
 for side in ('左','右'):
  d.click_item(side+'侧边缘',True);d.wait_node('关闭虚拟导航键');time.sleep(.8)
  tree=d.ui();grip=next(n for n in tree.iter('node') if n.get('content-desc')=='关闭虚拟导航键')
  gx=d.bounds(grip)[0];titles=set(d.action_titles().values())
  keys=[n for n in tree.iter('node') if d.visible(n) and n.get('package')==d.P and n.get('clickable')=='true' and d.bounds(n)[0]==gx and n.get('content-desc') in titles]
  assert len(keys)==len(initial) and all(d.bounds(a)[3]<=d.bounds(b)[1] for a,b in zip(keys,keys[1:]))
  d.tap(grip);time.sleep(.4)
 out['both_edges_stack_configured_actions']=True
 edit_first();tree=d.ui();assert not any(n.get('text')=='前移' and d.visible(n) for n in tree.iter('node'));d.shot('continuous-pages-navigation-menu')
 if len(initial)>1:
  d.click_item('后移');expected=initial.copy();expected[0],expected[1]=expected[1],expected[0];assert current()==expected;out['reorder_saved']=True;edit_first()
 d.click_item('更换动作');d.wait_node('选择导航动作');d.shot('continuous-pages-navigation-actions')
 assert not any(n.get('content-desc')=='应用启动器' for n in d.ui().iter('node'))
 d.click_item('更多动作',True);d.click_item('截屏',True);d.wait_node('虚拟导航键');assert current()[0]=='screenshot';out['replace_saved_and_returns_to_editor']=True
 if len(current())<5:
  d.click_item('添加按键',True);d.wait_node('选择导航动作');d.click_item('更多动作',True);d.click_item('通知栏',True);d.wait_node('虚拟导航键');assert current()[-1]=='notifications';out['add_saved_and_returns_to_editor']=True
 edit_first();d.click_item('移除');d.wait_node('虚拟导航键');assert 'screenshot' not in current();out['remove_saved']=True
 back();d.wait_node('选择操作');out['nested_navigation_returns_to_original_chooser']=True
out['preferences_restored']=True
(d.B/'continuous-pages-navigation-validation.json').write_text(json.dumps(out,ensure_ascii=False,indent=2),encoding='utf-8')
print(json.dumps(out,ensure_ascii=False))
