"""Real edge previews, pointer cancellation, and settings lifecycle on the OnePlus."""
import json,re,time,subprocess
import device as d

before=d.preferences();out={};progress=round((before['left_handle_width_dp']-2)/28*1000)
assert abs(2+28*progress/1000-before['left_handle_width_dp'])<.0001

def preview_windows():
    result={}
    for block in re.split(r'(?m)^  Window #',d.sh('dumpsys','window','windows')):
        heading=block.splitlines()[0] if block else ''
        match=re.search(r'Window\{(\w+) u0 Color Hand Operation 触控区域预览 (\w+)\}',heading)
        if not match:continue
        frame=re.search(r' frame=\[(-?\d+),(-?\d+)\]\[(-?\d+),(-?\d+)\]',block)
        result[match[2]]={'id':match[1],'frame':list(map(int,frame.groups())) if frame else None,'not_touchable':'NOT_TOUCHABLE' in block,'visible':'isOnScreen=true' in block}
    return result

def bar():return d.find_item('触摸宽度',True)
def point(n,p):
    a,b,c,e=d.bounds(n);return str(round(a+49+(c-a-98)*p/1000)),str((b+e)//2)
def tap(p):
    n=bar();d.sh('input','tap',*point(n,p));time.sleep(.35);return n,bar()
def same_frames(a,b):return {k:v['frame'] for k,v in a.items()}=={k:v['frame'] for k,v in b.items()}

try:
    d.main_home();initial=preview_windows();assert set(initial)=={'left','right'},initial
    assert all(v['not_touchable'] and v['visible'] for v in initial.values()),initial
    tree=d.ui();assert not any(n.get('content-desc','').startswith('触控区域预览，') for n in tree.iter('node'))
    d.shot('palette-preview-home');start=time.monotonic()
    n=bar();d.shot('palette-preview-geometry');a,b,c,e=d.bounds(n);x,y=point(n,progress)
    d.sh('input','swipe',x,y,str(int(x)+40),str(int(y)-400),'450');time.sleep(.4)
    assert d.preferences()==before,'Vertical scroll edited geometry'
    assert d.bounds(bar())[1]<b-90
    old,new=tap(400);assert old.get('node-hash')==new.get('node-hash')
    assert abs(d.preferences()['left_handle_width_dp']-13.2)<.04
    edited=preview_windows();assert edited['left']['frame'][2]<initial['left']['frame'][2]
    tap(progress);assert d.preferences()==before;assert same_frames(initial,preview_windows())
    out['tap_updates_real_edges_without_replacing_control']=True
    out['vertical_scroll_does_not_adjust_slider']=True

    n=bar();x,y=point(n,progress);end,_=point(n,900)
    pointer=subprocess.Popen(d.A+['shell',f'CLASSPATH=/data/local/tmp/edgeflow-probe.jar app_process /system/bin probe.Pointer {x} {y} {end} {y} 1800 CANCEL'],stdout=subprocess.PIPE,stderr=subprocess.PIPE,text=True)
    assert pointer.stdout.readline().strip()=='HOLDING';assert d.preferences()==before
    draft=preview_windows();assert draft['left']['frame'][2]>initial['left']['frame'][2]
    d.shot('palette-preview-draft');pointer.communicate(timeout=6);assert pointer.returncode==0;time.sleep(.3)
    assert d.preferences()==before;assert same_frames(initial,preview_windows())
    out['draft_cancel_restores_saved_edges_without_writing']=True
    remaining=11-(time.monotonic()-start)
    if remaining>0:time.sleep(remaining)
    assert set(preview_windows())=={'left','right'};out['settings_preview_survives_old_ten_second_timeout']=True

    d.main_link('手势设置');assert not preview_windows();d.sh('input','keyevent','4');time.sleep(.4)
    assert set(preview_windows())=={'left','right'}
    d.sh('am','start','-W','-a','android.settings.SETTINGS');time.sleep(1.2);assert not preview_windows()
    d.main_home();assert set(preview_windows())=={'left','right'}
    out['navigation_background_and_return_follow_preview_lifecycle']=True
finally:
    if d.preferences().get('left_handle_width_dp')!=before.get('left_handle_width_dp'):d.main_home();tap(progress)
    out['preferences_unchanged']=d.preferences()==before
    (d.B/'palette-preview-geometry-validation.json').write_text(json.dumps(out,ensure_ascii=False,indent=2),encoding='utf-8')
assert out['preferences_unchanged'];print(json.dumps(out,ensure_ascii=False,indent=2))
