"""Select and execute a published Settings shortcut without changing its setting."""
import device as d,time,json,xml.etree.ElementTree as E
def find_text(value,prefix=False):
 for _ in range(15):
  tree=d.ui()
  n=next((n for n in tree.iter('node') if ((n.get('text') or '').startswith(value) if prefix else n.get('text')==value) and d.visible(n)),None)
  if n is not None:d.tap(n);time.sleep(.4);return
  lists=[n for n in tree.iter('node') if n.get('class')=='android.widget.ListView' and d.visible(n)]
  if lists:
   a,b,c,e=d.bounds(lists[0]);d.sh('input','swipe',str((a+c)//2),str(e-45),str((a+c)//2),str(b+45),'280')
  else:time.sleep(.4)
 raise AssertionError('Missing '+value)
def choose(value):d.choose_action(value,'right')
wifi_before=d.sh('settings','get','global','wifi_on').strip()
with d.preserved_action('right'):
 try:
  choose('应用快捷方式…');find_text('设置');find_text('WLAN ·',True)
  raw=d.sh('run-as',d.P,'cat','shared_prefs/edgeflow.xml');key=next(n.text for n in E.fromstring(raw) if n.get('name')=='action_right_straight_short');assert key.startswith('launcher_shortcut:1:0:')
  d.sh('input','keyevent','3');time.sleep(.7);d.sh('am','start','-W','-f','0x14000000','-n',d.P+'/.MainActivity');time.sleep(.7)
  d.sh('input','swipe','1259','1400','1059','1400','220');time.sleep(1.2)
  resumed=d.resumed();assert 'com.oplus.wirelesssettings' in resumed,resumed
  d.wait_node('WLAN');d.shot('shortcut-settings-result')
  assert d.sh('settings','get','global','wifi_on').strip()==wifi_before
  (d.B/'shortcut-start-validation.json').write_text(json.dumps({'picker_selected_real_shortcut':key,'resumed':resumed,'wifi_setting_unchanged':True},ensure_ascii=False,indent=2),encoding='utf-8')
  print(resumed,flush=True)
 finally:d.sh('input','keyevent','3')
