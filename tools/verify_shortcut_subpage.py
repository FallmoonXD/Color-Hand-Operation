"""Grouped shortcut discovery, expansion/collapse and independent target selection on the phone."""
import json,time
import device as d
from preference_trial import PreferenceTrial

out={};key='action_right_straight_short'
def back():d.sh('input','keyevent','4');time.sleep(.5)
def has_sheet():return any(n.get('text')=='选择操作' and d.visible(n) for n in d.ui().iter('node'))
def open_shortcuts():
 d.open_action_chooser('right');d.click_item('设置应用快捷方式',True);d.wait_node('应用快捷方式')
 end=time.monotonic()+40
 while time.monotonic()<end:
  tree=d.ui();groups=[n for n in tree.iter('node') if n.get('content-desc','').startswith(('展开','收起')) and n.get('class')=='android.widget.Button']
  if groups:return groups
  time.sleep(.35)
 raise AssertionError('Shortcut groups did not load: '+str([(n.get('text'),n.get('content-desc')) for n in tree.iter('node') if n.get('text') or n.get('content-desc')]))

with PreferenceTrial({'edgeflow':{key,'target_shortcut_'+key},'edgeflow_launcher':{'shortcut_labels'}}):
 before=d.preferences();groups=open_shortcuts();out['publisher_group_count']=len(groups)
 assert len(groups)>0 and not has_sheet()
 d.shot('action-menu-shortcut-publishers')
 d.click_item('展开设置',True);d.find_item('WLAN',True);time.sleep(.3);d.shot('action-menu-shortcut-expanded')
 assert d.preferences()==before
 d.click_item('收起设置',True);time.sleep(.35);assert d.preferences()==before
 assert not any(n.get('content-desc')=='WLAN' and d.visible(n) for n in d.ui().iter('node'))
 out['expansion_and_collapse_do_not_select_or_write']=True
 d.click_item('展开设置',True);d.click_item('WLAN',True);d.wait_node('短距离滑动');assert not has_sheet()
 configured=d.preferences();assert configured[key]==before[key];target=configured['target_shortcut_'+key];assert target.startswith('launcher_shortcut:')
 assert {k:v for k,v in configured.items() if k!='target_shortcut_'+key}=={k:v for k,v in before.items() if k!='target_shortcut_'+key}
 out['shortcut_configuration_keeps_current_action']=True
 d.open_action_chooser('right');d.click_item('应用快捷方式，WLAN',True);d.wait_node('短距离滑动');assert d.preferences()[key]==target
 out['configured_shortcut_can_be_selected_independently']=True
 saved=d.preferences();open_shortcuts();d.find_item('收起设置',True);out['selected_publisher_auto_expands']=True
 back();d.wait_node('短距离滑动');assert d.preferences()==saved and not has_sheet()
 out['cancel_preserves_selected_shortcut']=True
out['test_preferences_restored']=True
(d.B/'action-menu-shortcut-validation.json').write_text(json.dumps(out,ensure_ascii=False,indent=2),encoding='utf-8')
print(json.dumps(out,ensure_ascii=False,indent=2))
