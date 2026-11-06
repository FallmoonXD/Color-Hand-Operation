"""Current OnePlus control/overlay regression, restoring changed system settings."""
import device as d,time,json,subprocess
r={};volume=d.vol();mode=d.sh('settings','get','system','screen_brightness_mode').strip();raw=d.sh('settings','get','system','screen_brightness').strip();adj=d.sh('settings','get','system','screen_auto_brightness_adj').strip()
def outside():d.sh('input','tap','630','2400');time.sleep(.4)
def tools():
 d.sh('input','keyevent','3');time.sleep(.3);d.sh('input','swipe','1259','1300','859','1900','1100');time.sleep(.45);d.wait_node('自动亮度')
def center(n):a,b,c,e=d.bounds(n);return ((a+c)//2,(b+e)//2)
try:
 # This script starts with a tool panel already open or opens one itself.
 if not any(n.get('content-desc')=='自动亮度' for n in d.ui().iter('node')):tools()
 current=d.sh('settings','get','system','screen_brightness_mode').strip()
 d.click('自动亮度');time.sleep(.6);assert d.sh('settings','get','system','screen_brightness_mode').strip()!=current
 d.click('自动亮度');time.sleep(.6);assert d.sh('settings','get','system','screen_brightness_mode').strip()==current;r['auto_brightness_roundtrip']=True
 if current=='1':d.click('自动亮度');time.sleep(.4)
 n=next(n for n in d.ui().iter('node') if n.get('content-desc','').startswith('屏幕亮度 '));a,b,c,e=d.bounds(n)
 d.sh('input','swipe',str(a+(c-a)//3),str((b+e)//2),str(a+2*(c-a)//3),str((b+e)//2),'420');time.sleep(.6)
 assert d.sh('settings','get','system','screen_brightness_mode').strip()=='0'
 brightness=int(d.sh('settings','get','system','screen_brightness').strip());assert brightness>0;r['brightness_slider_manual_raw']=brightness
 old=d.vol();x,y=center(d.node('音量增加'));d.sh('input','swipe',str(x),str(y),str(x),str(y),'850');time.sleep(.2);now=d.vol();assert now>=old+3;r['tool_volume_hold_delta']=now-old
 assert 'Launcher' in d.resumed();r['tools_preserve_resumed_activity']=True
 outside();d.sh('cmd','media_session','volume','--stream','3','--set',str(volume))
 d.sh('input','swipe','1259','1300','1059','1100','220');time.sleep(.4)
 x,y=center(d.wait_node('音量增加'));old=d.vol();d.sh('input','swipe',str(x),str(y),str(x),str(y),'850');time.sleep(.2);now=d.vol();assert now>=old+3;r['floating_volume_hold_delta']=now-old
 assert d.node('音量增加') is not None
 outside();assert not any(n.get('content-desc')=='音量增加' and n.get('package')==d.P for n in d.ui().iter('node'));r['volume_outside_dismissal']=True
 # Process death is distinct from a recent-task card swipe. Do not force-stop the app.
 pid=d.sh('pidof',d.P).strip();assert pid.isdigit();d.sh('su','-c','kill -9 '+pid)
 new='';deadline=time.monotonic()+20
 while time.monotonic()<deadline:
  try:new=d.sh('pidof',d.P).strip()
  except subprocess.CalledProcessError:new=''
  if new and new!=pid:break
  time.sleep(.4)
 assert new and new!=pid;d.wait_node('右侧手势区域',timeout=12);r['process_rebound']={'before':pid,'after':new}
 tools();r['tools_after_process_rebound']=True;outside()
finally:
 d.sh('cmd','media_session','volume','--stream','3','--set',str(volume))
 d.sh('settings','put','system','screen_auto_brightness_adj',adj);d.sh('settings','put','system','screen_brightness',raw);d.sh('settings','put','system','screen_brightness_mode',mode)
 d.sh('input','keyevent','3');(d.B/'controls-validation.json').write_text(json.dumps(r,ensure_ascii=False,indent=2),encoding='utf-8')
print(json.dumps(r,ensure_ascii=False))
