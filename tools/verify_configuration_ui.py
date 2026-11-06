"""Reversible OHO-route checks: explicit added pair, custom navigation, task previews."""
import device as d,time,json
r={};before=d.preferences();pairs_before=d.pair_count(before)
primary={k:v for k,v in before.items() if k.startswith('action_left_') or k.startswith('action_right_')}
navigation_before=json.loads(before.get('navigation_actions','["back","home","recents"]'))
assert before.get('enabled',True),'Enable the master explicitly before this test'
assert len(navigation_before)<5 and 'notifications' not in navigation_before,'This test requires one free navigation slot and no existing notification button'
extra_flags={key:before.get(key,False) for key in ('extra_left_enabled','extra_right_enabled','extra2_left_enabled','extra2_right_enabled')}

def navigation():return json.loads(d.preferences().get('navigation_actions','["back","home","recents"]'))
def remove_test_navigation():
 current=navigation()
 if current==navigation_before:return
 assert current==navigation_before+['notifications'],'Unexpected navigation mutation; will not remove user buttons'
 d.main_link('导航键设置');d.click_item(str(len(current))+'. 通知栏',True);d.click('移除')
 assert navigation()==navigation_before

try:
 # Pair addition does not enable it. Toggle the test handle explicitly and restore it below.
 d.set_pair_count(3);d.set_handle_enabled('extra2_right',True)
 with d.preserved_action('extra2_right'):
  d.choose_action('主屏幕','extra2_right')
  state=d.preferences();start=state.get('extra2_right_handle_start_fraction',.34);end=state.get('extra2_right_handle_end_fraction',.62);y=round((start+end)*2780/2)
  d.sh('input','swipe','1259',str(y),'1059',str(y),'250');time.sleep(.6);assert 'Launcher' in d.resumed()
  assert {k:v for k,v in d.preferences().items() if k in primary}==primary
  r['extra2_right_independent_action']=True
 d.main_link('导航键设置');d.click_item('添加按键',True);d.click_item('通知栏')
 assert navigation()==navigation_before+['notifications']
 d.click_item('预览导航键',True);d.wait_node('通知栏');d.shot('navigation-custom')
 d.click('通知栏');time.sleep(1);assert any(n.get('resource-id')=='com.android.systemui:id/notification_panel' for n in d.ui().iter('node'))
 d.sh('cmd','statusbar','collapse');time.sleep(.4);d.sh('input','tap','630','240');time.sleep(.3)
 remove_test_navigation();r['navigation_custom_action_and_restore']=True
 d.main_link('任务切换器');d.wait_node('系统任务预览',10);d.shot('tasks-preview');r['task_preview_visible']=True
 d.sh('input','tap','630','240');time.sleep(.5);assert 'Color Hand Operation 任务切换器' not in d.sh('dumpsys','window','windows');r['task_preview_window_closed']=True
finally:
 try:
  cleanup_errors=[]
  def restore(label,operation):
   try:operation()
   except Exception as error:cleanup_errors.append(label+': '+str(error))
  restore('collapse shade',lambda:d.sh('cmd','statusbar','collapse'))
  restore('navigation buttons',remove_test_navigation)
  # Restore flags while all pairs still exist, then restore the original effective pair count.
  restore('expose handle pairs',lambda:d.set_pair_count(3))
  for key,value in extra_flags.items():restore(key,lambda key=key,value=value:d.set_handle_enabled(key.removesuffix('_enabled'),value))
  restore('original pair count',lambda:d.set_pair_count(pairs_before))
  if cleanup_errors:r['cleanup_errors']=cleanup_errors
  assert not cleanup_errors,'; '.join(cleanup_errors)
  assert d.pair_count()==pairs_before
  state=d.preferences();assert all(state.get(k,False)==v for k,v in extra_flags.items())
  assert {k:v for k,v in state.items() if k in primary}==primary
 finally:(d.B/'configuration-ui-validation.json').write_text(json.dumps(r,ensure_ascii=False,indent=2),encoding='utf-8')
print(json.dumps(r,ensure_ascii=False))
