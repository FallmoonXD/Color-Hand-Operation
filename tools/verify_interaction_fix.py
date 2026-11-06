"""Targeted 0.3.1 device regression; retain current user settings and volume."""
import json, subprocess, time
import device as d

before=d.preferences();volume=d.vol();result={}
width_progress=round((before['left_handle_width_dp']-2)/28*1000)
assert abs((2+28*width_progress/1000)-before['left_handle_width_dp'])<.0001

def bar():return d.find_item('触摸宽度',True)
def at(n,progress):
    a,b,c,e=d.bounds(n);return str(round(a+49+(c-a-98)*progress/1000)),str((b+e)//2)
def tap_progress(progress):
    n=bar();d.sh('input','tap',*at(n,progress));time.sleep(.35);return n,d.node('触摸宽度')
def no_preview():
    assert not any('Color Hand Operation 触控区域预览' in line and 'Window #' in line for line in d.sh('dumpsys','window','windows').splitlines())
def save():
    (d.B/'interaction-fix-validation.json').write_text(json.dumps(result,ensure_ascii=False,indent=2),encoding='utf-8')

try:
    d.main_home();n=bar();old=d.bounds(n)
    x,y=at(n,width_progress);d.sh('input','swipe',x,y,str(int(x)+45),str(int(y)-430),'450');time.sleep(.45)
    assert d.preferences()==before,'Vertical swipe on slider changed configuration'
    current=d.node('触摸宽度');assert d.bounds(current)[1]<old[1]-100,'Vertical slider swipe did not scroll page'
    no_preview();result['vertical_slider_scroll']=True
    # A stationary tap and horizontal edit retain the exact same actual Views/window.
    n,current=tap_progress(width_progress);assert n.get('node-hash')==current.get('node-hash');assert d.bounds(n)==d.bounds(current);assert d.preferences()==before
    result['stationary_tap_keeps_page']=True
    n,current=tap_progress(400);assert n.get('node-hash')==current.get('node-hash');assert d.bounds(n)==d.bounds(current)
    changed=d.preferences();assert abs(changed['left_handle_width_dp']-13.2)<.04 and changed['left_handle_width_dp']==changed['right_handle_width_dp']
    tap_progress(width_progress);assert d.preferences()==before
    result['tap_commit_in_place_and_linked']=True
    n=bar();x,y=at(n,width_progress);end,_=at(n,800)
    p=subprocess.Popen(d.A+['shell',f'CLASSPATH=/data/local/tmp/edgeflow-probe.jar app_process /system/bin probe.Pointer {x} {y} {end} {y} 1800 CANCEL'],stdout=subprocess.PIPE,stderr=subprocess.PIPE,text=True)
    assert p.stdout.readline().strip()=='HOLDING';assert d.preferences()==before
    previews=[line for line in d.sh('dumpsys','window','windows').splitlines() if 'Color Hand Operation 触控区域预览' in line and 'Window #' in line]
    assert len(previews)==2;d.shot('interaction-horizontal-draft');p.communicate(timeout=5);assert p.returncode==0
    assert d.preferences()==before;no_preview();result['horizontal_drag_and_cancel']=True
    d.shot('interaction-geometry-after')
    # Far but fast UP must remain short; old code incorrectly opened widgets.
    d.sh('input','keyevent','3');time.sleep(.4)
    for side,x,end in [('left',5,455),('right',1259,809)]:
        current_volume=d.vol();d.sh('input','swipe',str(x),'1900',str(end),'1450','80');time.sleep(.25)
        assert d.vol()==current_volume+1,(side,d.vol(),current_volume)
        assert 'replica.WidgetActivity' not in d.resumed(),'Fast release bypassed long duration'
        d.sh('input','tap','630','2550');time.sleep(.65)
        d.sh('cmd','media_session','volume','--stream','3','--set',str(current_volume))
    result['fast_far_release_is_short_both_sides']=True
    d.sh('input','swipe','5','1900','455','1450','650');d.wait_node('第 1 页，共 1 页；页面菜单')
    d.sh('input','tap','630','2550');time.sleep(.4);result['qualified_long_still_opens_widgets']=True
finally:
    if d.preferences().get('left_handle_width_dp')!=before['left_handle_width_dp']:
        d.main_home();tap_progress(width_progress)
    d.sh('cmd','media_session','volume','--stream','3','--set',str(volume))
    result['preferences_preserved']=d.preferences()==before;save()
assert result['preferences_preserved']
print(json.dumps(result,ensure_ascii=False,indent=2))
