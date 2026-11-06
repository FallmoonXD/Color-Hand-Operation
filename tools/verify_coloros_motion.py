"""Real pointer/volume/page/font checks, restoring all temporary system values."""
import json, subprocess, time
import device as d
r={};volume=d.vol()
try:
    d.sh('input','keyevent','3');time.sleep(.3)
    for label,x,end in [('right',1259,1059),('left',5,205)]:
        d.sh('input','swipe',str(x),'1300',str(end),'1100','250');time.sleep(.2);assert d.vol()==volume+1,(label,d.vol(),volume)
        if label=='right':d.shot('coloros-final-volume')
        d.sh('input','tap','630','2550');time.sleep(.7) # Existing ColorOS exit duration is 500ms.
        assert not any('Color Hand Operation 音量' in line and 'Window #' in line for line in d.sh('dumpsys','window','windows').splitlines())
        d.sh('input','swipe',str(x),'1300',str(end),'1500','250');time.sleep(.2);assert d.vol()==volume
        d.sh('input','tap','630','2550');time.sleep(.7)
    r['volume_gestures_and_outside_dismiss']=True
    d.main_home();n=d.find_item('左侧触钮',True)
    p=subprocess.Popen(d.A+['shell','screenrecord','--size','632x1390','--bit-rate','4000000','--time-limit','5','/data/local/tmp/edgeflow-page-motion.mp4'],stdout=subprocess.PIPE,stderr=subprocess.PIPE)
    time.sleep(.5);d.tap(n);time.sleep(.55);d.sh('input','keyevent','4');time.sleep(.55)
    d.click_item('右侧触钮',True);d.sh('input','keyevent','4');time.sleep(.4)
    p.communicate(timeout=8);assert p.returncode==0
    subprocess.run(d.A+['pull','/data/local/tmp/edgeflow-page-motion.mp4',str(d.B/'coloros-page-motion.mp4')],check=True,stdout=subprocess.DEVNULL)
    d.sh('rm','/data/local/tmp/edgeflow-page-motion.mp4');r['page_motion_recorded']=True
    original=d.sh('settings','get','global','animator_duration_scale').strip()
    try:
        d.sh('settings','put','global','animator_duration_scale','0');time.sleep(.3)
        d.click_item('左侧触钮',True);d.wait_node('短距离滑动');d.sh('input','keyevent','4');time.sleep(.25);d.wait_node('更多选项');r['animation_disabled_navigation']=True
    finally:d.sh('settings','put','global','animator_duration_scale',original)
    original_font=d.sh('settings','get','system','font_scale').strip()
    try:
        d.sh('settings','put','system','font_scale','1.15');time.sleep(.8);d.wait_node('更多选项');d.shot('coloros-final-font-115');r['font_scale']=True
    finally:d.sh('settings','put','system','font_scale',original_font);time.sleep(.8)
    d.main_home();d.shot('coloros-final-home')
finally:
    d.sh('cmd','media_session','volume','--stream','3','--set',str(volume));(d.B/'coloros-motion-validation.json').write_text(json.dumps(r,indent=2),encoding='utf-8')
print(r)
