"""Embedded chooser identity/scroll and configuration callbacks on the connected phone."""
import json,time
import device as d
from preference_trial import PreferenceTrial

out={};key='action_right_straight_short'
def back():d.sh('input','keyevent','4');time.sleep(.55)
def sheet():return d.wait_node('选择操作')
def visible_rows():
 return [(n.get('content-desc'),d.bounds(n),n.get('checked')) for n in d.ui().iter('node') if n.get('class')=='android.widget.RadioButton' and n.get('content-desc') and d.visible(n)]
def no_sheet():assert not any(n.get('text')=='选择操作' and d.visible(n) for n in d.ui().iter('node'))
def equal_snapshot(before):
 now=visible_rows();assert now==before,(before,now)

d.open_action_chooser('right');tree=d.ui();assert any(n.get('text')=='需要设置' for n in tree.iter('node'));assert not any(n.get('content-desc')=='应用启动器' for n in tree.iter('node'))
d.shot('continuous-pages-compact')
with PreferenceTrial({'edgeflow':{key,'target_app_'+key,'target_shortcut_'+key},'edgeflow_launcher':{'shortcut_labels'}}):
 before=d.preferences();d.find_item('设置应用快捷方式',True);identity=sheet().get('node-hash');window=sheet().get('window-id');snapshot=visible_rows();d.shot('continuous-pages-bottom')
 for title in ['快速工具','小组件','快速启动','任务切换器','虚拟触控板','虚拟导航键','键盘快捷方式']:
  print('Checking retained settings:',title,flush=True)
  d.click_item('设置'+title,True);d.wait_node(title);no_sheet()
  if title=='快速工具':d.click_item('编辑按钮',True);d.wait_node('快速工具');back();d.wait_node('编辑按钮');no_sheet()
  back();assert sheet().get('node-hash')==identity;assert sheet().get('window-id')==window;equal_snapshot(snapshot);assert d.preferences()==before
 out['seven_settings_keep_same_sheet_nodes_window_and_scroll']=True
 d.click_item('设置启动指定应用',True);d.wait_node('启动指定应用');app=d.find_item('设置',True);assert d.bounds(app)[3]-d.bounds(app)[1]>=252;d.shot('continuous-pages-app-rows');d.tap(app);time.sleep(.6)
 assert sheet().get('node-hash')==identity
 configured=d.preferences();assert configured[key]==before[key];target=configured['target_app_'+key];assert target.startswith('app:com.android.settings/')
 d.find_item('启动指定应用，设置',True);d.shot('continuous-pages-updated-target')
 out['configure_target_updates_retained_row_without_selecting']=True
 # The original row's click closure must read the newly configured target.
 d.click_item('启动指定应用，设置',True);d.wait_node('短距离滑动');no_sheet();assert d.preferences()[key]==target
 out['retained_row_selects_latest_target_and_closes_once']=True
 d.open_action_chooser('right');d.find_item('应用快捷方式',True);saved=d.preferences();identity=sheet().get('node-hash')
 assert not saved.get('target_shortcut_'+key),'This first-use branch expects no saved shortcut target'
 d.click_item('应用快捷方式',True);d.wait_node('应用快捷方式');no_sheet();back();assert sheet().get('node-hash')==identity;assert d.preferences()==saved
 out['first_target_selection_cancel_returns_to_same_chooser']=True
 d.click_item('应用快捷方式',True);d.wait_node('应用快捷方式')
 end=time.monotonic()+40
 while time.monotonic()<end:
  tree=d.ui()
  if any(n.get('content-desc')=='展开设置' for n in tree.iter('node')):break
  time.sleep(.3)
 d.click_item('展开设置',True);d.click_item('WLAN',True);d.wait_node('短距离滑动');no_sheet();assert d.preferences()[key].startswith('launcher_shortcut:')
 out['first_target_selection_success_selects_and_closes_chooser']=True
out['test_preferences_restored']=True
(d.B/'continuous-pages-ui-validation.json').write_text(json.dumps(out,ensure_ascii=False,indent=2),encoding='utf-8');print(json.dumps(out,ensure_ascii=False,indent=2))
