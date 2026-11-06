"""Check OHO advanced-context switches, restoring each user's initial value."""
import device as d,time,json
r={};before=d.preferences()
assert before.get('enabled',True) and before.get('right_enabled',True),'Context test requires an already-enabled primary right handle'

def handles():return any(n.get('content-desc')=='右侧手势区域' for n in d.ui().iter('node'))
def setting(title,key,on):
 d.main_link('高级设置');d.set_toggle(title,on);assert d.preferences().get(key,False)==on

try:
 setting('主屏幕','hide_home',True);d.sh('input','keyevent','3');time.sleep(.6);assert not handles();r['hide_on_home']=True
 d.sh('am','start','-W','-a','android.settings.SETTINGS');time.sleep(.6);assert handles();r['restore_off_home']=True
 setting('主屏幕','hide_home',False);setting('快捷面板','hide_notifications',True)
 d.sh('cmd','statusbar','expand-notifications');time.sleep(.6);assert not handles();r['hide_in_notification_shade']=True
 d.sh('cmd','statusbar','collapse');time.sleep(.6);assert handles();r['restore_after_shade']=True
finally:
 try:
  d.sh('cmd','statusbar','collapse')
  setting('主屏幕','hide_home',before.get('hide_home',False))
  setting('快捷面板','hide_notifications',before.get('hide_notifications',False))
 finally:(d.B/'context-validation.json').write_text(json.dumps(r,indent=2),encoding='utf-8')
print(json.dumps(r))
