"""Targeted final-build checks after the direction-icon background correction."""
import json,hashlib,time,subprocess
from pathlib import Path
from PIL import Image
import device as d
import verify_oho_ui as u

r=json.loads((d.B/'oho-ui-validation.json').read_text(encoding='utf-8'))
u.home();d.click('左侧触钮');d.wait_node('长滑对角线向上');d.shot('oho-ui-left');u.back()
u.scroll_click('触钮颜色');d.wait_node('自定义颜色');d.wait_node('触钮可见度');d.shot('oho-ui-colors');u.back();u.home()
crops=[]
for side,x0,x1 in [('left',5,210),('right',1259,1054)]:
    for direction,dy in [('up',-180),('straight',0),('down',180)]:
        try:
            d.sh('input','motionevent','DOWN',str(x0),'1800')
            d.sh('input','motionevent','MOVE',str(x1),str(1800+dy));time.sleep(.12)
            name='oho-ui-wave-'+side+'-'+direction;d.shot(name)
            with Image.open(d.B/(name+'.png')) as im:
                crops.append(im.crop((0 if side=='left' else 1084,1400,180 if side=='left' else 1264,2200)).convert('RGB'))
        finally:
            d.sh('input','motionevent','CANCEL',str(x1),str(1800+dy));time.sleep(.35)
canvas=Image.new('RGB',(180*6,800),'#eeeeee')
for i,im in enumerate(crops):canvas.paste(im,(180*i,0))
canvas.save(d.B/'oho-ui-six-arrows.png')
r['six_arrow_capture_order']=['left_up','left_straight','left_down','right_up','right_straight','right_down']
d.sh('input','swipe','1259','1600','850','1900','500');time.sleep(.7)
assert 'Color Hand Operation 快速工具' in d.sh('dumpsys','window','windows') or any(n.get('content-desc')=='自动亮度' for n in d.ui().iter('node'))
d.wait_node('自动亮度');d.shot('oho-ui-tools-overlay');d.sh('input','tap','630','300');time.sleep(.6)
d.sh('input','swipe','1259','1800','850','1500','500');time.sleep(.9)
assert any('第 1 页' in (n.get('content-desc') or '') for n in d.ui().iter('node'))
d.shot('oho-ui-widgets-overlay');d.sh('input','tap','630','300');time.sleep(.5)
r['quick_tools_and_widgets_default_gestures']=True
u.home();d.shot('oho-ui-home-final')
old=json.loads((d.B/'oho-ui-prefs-before.json').read_text(encoding='utf-8'));after=u.prefs()
assert all(after.get(k)==v for k,v in old.items())
r['original_preferences_preserved_final_build']=len(old)
apk=Path(__file__).resolve().parents[1]/'app/build/outputs/apk/debug/app-debug.apk'
digest=hashlib.sha256(apk.read_bytes()).hexdigest()
installed=d.sh('pm','path',d.P).strip().removeprefix('package:')
assert d.sh('sha256sum',installed).split()[0]==digest
package=d.sh('dumpsys','package',d.P)
assert 'versionName=0.2.0-oho-ui' in package and 'versionCode=2 ' in package
r['installed_version']='0.2.0-oho-ui';r['installed_apk_sha256']=digest
r['services']=d.sh('settings','get','secure','enabled_accessibility_services').strip()
assert d.P in r['services'] and 'gulu.gulugulu' not in r['services']
r['gulugulu_process_absent']=subprocess.run(d.A+['shell','pidof','gulu.gulugulu.visualtest'],capture_output=True).returncode!=0
assert r['gulugulu_process_absent']
power=d.sh('dumpsys','power');assert 'mWakefulness=Awake' in power and 'mStayOn=true' in power
r['device_awake_and_stays_on']=True
(d.B/'oho-ui-validation.json').write_text(json.dumps(r,ensure_ascii=False,indent=2),encoding='utf-8')
print(json.dumps(r,ensure_ascii=False),flush=True)
