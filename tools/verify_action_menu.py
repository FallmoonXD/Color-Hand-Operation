"""Action chooser layout, settings return and independent application target configuration."""
import json,time
import device as d
from preference_trial import PreferenceTrial

out={};key='action_right_straight_short'
def back():d.sh('input','keyevent','4');time.sleep(.45)
def sheet_visible():return any(n.get('text')=='选择操作' and d.visible(n) for n in d.ui().iter('node'))
def opener():d.open_action_chooser('right')
def save():
 (d.B/'action-menu-ui-validation.json').write_text(json.dumps(out,ensure_ascii=False,indent=2),encoding='utf-8')

with PreferenceTrial({'edgeflow':{key,'target_app_'+key,'target_shortcut_'+key},'edgeflow_launcher':{'shortcut_labels'}}) as trial:
 before=d.preferences()
 opener();tree=d.ui();choices=[n.get('content-desc').split('，')[0] for n in tree.iter('node') if n.get('class')=='android.widget.RadioButton' and n.get('content-desc')]
 assert len(choices)==62,(len(choices),choices)
 assert choices[0]=='无'
 assert choices[-9:]==['快速工具','小组件','快速启动','任务切换器','虚拟触控板','虚拟导航键','键盘快捷方式','启动指定应用','应用快捷方式'],choices[-9:]
 first=d.wait_node('无');assert d.bounds(first)[0]==112,d.bounds(first)
 out['action_count_and_grouped_order']=len(choices);out['leading_content_inset_dp']=24
 d.shot('action-menu-overview');d.click_item('设置应用快捷方式',True)
 d.wait_node('应用快捷方式');assert not sheet_visible();back();d.wait_node('短距离滑动');assert not sheet_visible();assert d.preferences()==before
 out['shortcut_subpage_back_does_not_reopen_sheet']=True
 for title in ['快速工具','小组件','快速启动','任务切换器','虚拟触控板','虚拟导航键','键盘快捷方式']:
  print('Checking settings return:',title,flush=True)
  opener();d.click_item('设置'+title,True);d.wait_node(title);assert not sheet_visible()
  if title=='快速工具':d.click_item('编辑按钮',True);d.wait_node('快速工具');back();d.wait_node('编辑按钮')
  back();d.wait_node('短距离滑动');assert not sheet_visible(),title;assert d.preferences()==before,title
 out['all_seven_settings_return_without_reopening']=True
 opener();d.find_item('设置应用快捷方式',True);d.shot('action-menu-configurable');back()
 opener();d.click_item('设置启动指定应用',True);d.wait_node('启动指定应用');assert not sheet_visible()
 d.find_item('设置',True);d.shot('action-menu-app-subpage');d.click_item('设置',True);d.wait_node('短距离滑动')
 configured=d.preferences();assert configured[key]==before[key];assert configured['target_app_'+key].startswith('app:com.android.settings/')
 assert {k:v for k,v in configured.items() if k!='target_app_'+key}=={k:v for k,v in before.items() if k!='target_app_'+key}
 assert not sheet_visible();out['configure_application_without_selecting']=True
 opener();n=d.find_item('启动指定应用，设置',True);d.tap(n);time.sleep(.5);d.wait_node('短距离滑动')
 assert d.preferences()[key]==configured['target_app_'+key];assert not sheet_visible();out['select_preconfigured_application']=True
 opener();d.click_item('设置启动指定应用',True);d.wait_node('启动指定应用');saved=d.preferences();back();d.wait_node('短距离滑动');assert d.preferences()==saved
 out['application_subpage_cancel_preserves_target']=True
out['test_preferences_restored']=True;save();print(json.dumps(out,ensure_ascii=False,indent=2))
