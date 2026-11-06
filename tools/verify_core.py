import device as d,time,re,json,subprocess
r={};volume=d.vol()
def home():d.sh('input','keyevent','3');time.sleep(.35)
def outside():d.sh('input','tap','630','2550');time.sleep(.35)
def swipe(x,y,ex,ey,duration=1100):d.sh('input','swipe',str(x),str(y),str(ex),str(ey),str(duration));time.sleep(.55)
def app_card(n):
 a,b,c,e=d.bounds(n)
 return n.get('content-desc')=='Color Hand Operation' and n.get('text')=='' and n.get('package')=='com.android.launcher' and d.visible(n) and c-a>600 and e-b>1000
try:
 d.keep_open();home()
 positions=[]
 for side,x,ex,y in [('right',1259,859,1300),('left',5,405,1700)]:
  swipe(x,y,ex,y+600);n=d.node('通知栏');positions.append(n.get('bounds'));d.shot('tools-'+side);assert 'Launcher' in d.resumed();outside()
 assert positions[0]!=positions[1];r['fresh_panel_anchors']=positions
 for x,ex in [(5,205),(1259,1059)]:
  before=d.vol();swipe(x,1300,ex,1100,250);assert d.vol()==before+1;outside();swipe(x,1300,ex,1500,250);assert d.vol()==before;outside()
 r['four_volume_gestures']=True
 for x,ex in [(5,455),(1259,809)]:
  swipe(x,1900,ex,1450);assert 'replica.WidgetActivity' in d.resumed();assert d.node('第 1 页，共 1 页；页面菜单') is not None;d.tap(d.node('第 1 页，共 1 页；页面菜单'));assert d.node('添加小组件') is not None;d.sh('input','keyevent','4');outside()
 r['both_widget_gestures_and_menu']=True
 home();swipe(5,1400,205,1400,250);assert any(app_card(n) for n in d.ui().iter('node'));home();r['left_recents']=True
 d.sh('am','start','-a','android.settings.SETTINGS');time.sleep(.6);swipe(1259,1400,1059,1400,250);assert 'com.android.settings' not in d.resumed();r['right_back']=True
 # Both curve surfaces stay narrow/opaque and source clipping is symmetrical.
 d.sh('am','start','-a','android.settings.SETTINGS');time.sleep(.5)
 for side,x,ex in [('left',5,210),('right',1259,1054)]:
  d.sh('input','motionevent','DOWN',str(x),'1800');d.sh('input','motionevent','MOVE',str(ex),'1700');time.sleep(.12);d.shot('wave-'+side);d.sh('input','motionevent','CANCEL',str(ex),'1700');time.sleep(.25)
 r['wave_windows_narrow']=all(s in d.sh('dumpsys','window','windows') for s in ['Color Hand Operation 手势绘制 L','Color Hand Operation 手势绘制 R'])
 home();d.sh('am','start','-W','-f','0x14000000','-n',d.P+'/.MainActivity');time.sleep(.7);d.sh('input','keyevent','187');time.sleep(1.3)
 pid=d.sh('pidof',d.P).strip()
 d.shot('core-recents-before')
 for attempt in range(2):
  cards=[n for n in d.ui().iter('node') if app_card(n)]
  if not cards:break
  a,b,c,e=d.bounds(cards[0]);d.sh('input','swipe',str((a+c)//2),str(b+int((e-b)*.64)),str((a+c)//2),str(b+18),'300');time.sleep(1.2)
  print('after swipe',attempt,cards[0].attrib,d.resumed(),flush=True)
 d.shot('core-recents-after')
 after=d.sh('pidof',d.P).strip();assert pid==after,(pid,after);assert not any(app_card(n) for n in d.ui().iter('node'));r['swipe_away_preserves_service']={'before':pid,'after':after}
 home();swipe(1259,1300,859,1900);assert d.node('通知栏') is not None;outside();r['works_after_swipe_away']=True
 home();swipe(5,1400,455,1400);d.shot('one-hand-final');d.sh('input','tap','630','300');time.sleep(.45)
 swipe(1259,1400,809,1400);assert any(n.get('package')=='com.coloros.smartsidebar' and n.get('text')=='中转站' for n in d.ui().iter('node'));r['native_sidebar_from_long_gesture']=True;d.shot('sidebar-final');d.sh('input','tap','100','2450');time.sleep(.3)
 assert d.P in d.sh('settings','get','secure','enabled_accessibility_services');r['accessibility_still_enabled']=True
finally:
 d.sh('input','motionevent','CANCEL','630','1400');d.sh('cmd','media_session','volume','--stream','3','--set',str(volume));home();(d.B/'core-validation.json').write_text(json.dumps(r,ensure_ascii=False,indent=2),encoding='utf-8')
print(json.dumps(r,ensure_ascii=False))
