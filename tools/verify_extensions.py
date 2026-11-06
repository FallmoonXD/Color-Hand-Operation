"""Device checks for translated screenshot input and the relative magnification pad."""
import device as d,time,json,re
r={}
def choose(action):d.choose_action(action,'right')

def trigger():time.sleep(1.5);d.sh('input','swipe','1259','1400','1059','1400','220');time.sleep(.85)
def main_top():d.main_home();d.find_item('左侧触钮',True)

assert d.preferences().get('left_enabled',True),'Translated tap test requires an already-enabled left handle'
with d.preserved_action('right'):
 try:
  choose('屏幕放大镜');main_top();trigger();time.sleep(1)
  # UiAutomation connect/disconnect resets full-display magnification even with
  # DONT_SUPPRESS_ACCESSIBILITY_SERVICES. Do not inspect its UI tree while zoomed.
  before=d.sh('dumpsys','accessibility');assert 'activated: true, scale: 2.0' in before
  d.sh('input','swipe','756','1390','896','1310','350');time.sleep(.25)
  after=d.sh('dumpsys','accessibility');assert 'activated: true, scale: 2.0' in after;assert re.search(r'MagnificationConfig\[[^\n]+',before)[0]!=re.search(r'MagnificationConfig\[[^\n]+',after)[0]
  d.sh('input','tap','1042','1608');time.sleep(1);assert 'scale: 2.5' in d.sh('dumpsys','accessibility');d.shot('magnifier-pad')
  d.sh('input','tap','1095','1182');time.sleep(.6);assert 'activated: false, scale: 1.0' in d.sh('dumpsys','accessibility');r['magnifier_pad_pan_zoom_close']=True
  choose('屏幕自由移动');main_top();target=d.find_item('左侧触钮',True);a,b,c,e=d.bounds(target);ty=(b+e)//2
  trigger();d.wait_node('关闭屏幕自由移动');d.shot('screen-move-before')
  # Drag up 80px; the screenshot moves down 160px. The mapped release hits the original row.
  d.sh('input','swipe','500',str(ty+240),'500',str(ty+160),'500');time.sleep(.8)
  d.wait_node('短距离滑动');assert any(n.get('text')=='左侧触钮' and d.visible(n) for n in d.ui().iter('node'))
  assert 'Color Hand Operation 屏幕自由移动' not in d.sh('dumpsys','window','windows');r['screen_move_translated_tap']=True
  d.shot('screen-move-result')
 except Exception:
  d.shot('extension-failure');raise
 finally:
  try:
   close=next((n for n in d.ui().iter('node') if n.get('content-desc')=='关闭屏幕自由移动' and d.visible(n)),None)
   if close is not None:d.tap(close);time.sleep(.5)
   d.sh('input','tap','630','240');time.sleep(.4);d.sh('input','keyevent','3')
  finally:(d.B/'extension-validation.json').write_text(json.dumps(r,ensure_ascii=False,indent=2),encoding='utf-8')
print(json.dumps(r,ensure_ascii=False))
