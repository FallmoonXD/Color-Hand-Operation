"""Focused integration checks; only temporary changes to new Color Hand Operation settings."""
import device as d,time,json
r={}
def outside():d.sh('input','tap','630','300');time.sleep(.4)
def right_short():d.sh('input','swipe','1259','1400','1059','1400','220');time.sleep(.5)
def choose_right_short(action):d.choose_action(action,'right')

with d.preserved_action('right'):
 choose_right_short('系统屏幕放大')
 d.sh('input','keyevent','3');time.sleep(.4);right_short()
 data=d.sh('dumpsys','accessibility')
 (d.B/'magnification-active.txt').write_text(data,encoding='utf-8')
 assert 'activated: true, scale: 2.0' in data
 r['magnification_2x']=True
 d.shot('magnified')
 right_short()
 reset=d.sh('dumpsys','accessibility')
 assert 'activated: false, scale: 1.0' in reset
 r['magnification_reset']=True
 (d.B/'magnification-reset.txt').write_text(reset,encoding='utf-8')
 d.shot('magnification-reset')
d.sh('input','keyevent','3')
(d.B/'extra-validation.json').write_text(json.dumps(r,ensure_ascii=False,indent=2),encoding='utf-8')
print(json.dumps(r,ensure_ascii=False))
