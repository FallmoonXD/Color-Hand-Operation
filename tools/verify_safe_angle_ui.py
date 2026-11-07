"""Real UI geometry, safe-area sizing and reversible direct manipulation of angle boundaries."""
import json,math,re,subprocess,time
import device as d
from preference_trial import PreferenceTrial

result={}
allowed={'short_angle_up_inner_degrees'}
for side in ('left','right'):
    allowed.update(side+'_handle_'+part for part in ('width_dp','start_fraction','end_fraction'))

def windows():
    found={}
    for block in re.split(r'(?m)^  Window #',d.sh('dumpsys','window','windows')):
        title=block.splitlines()[0] if block else ''
        if 'Color Hand Operation' not in title:continue
        match=re.search(r' frame=\[(-?\d+),(-?\d+)\]\[(-?\d+),(-?\d+)\]',block)
        if match:found[title]=list(map(int,match.groups()))
    return found
def node(description):return d.find_item(description,True)
def tap_progress(description,progress,max_progress=1000):
    n=node(description);a,b,c,e=d.bounds(n)
    d.sh('input','tap',str(round(a+49+(c-a-98)*progress/max_progress)),str((b+e)//2));time.sleep(.3)
def current(description):return node(description+'当前值').get('text')
def graph_points():
    first=node('短滑向上斜滑边界');second=node('短滑向下斜滑边界')
    # Re-read both after scrolling so all bounds share one viewport coordinate space.
    tree=d.ui();first=next(n for n in tree.iter('node') if n.get('content-desc')=='短滑向上斜滑边界');second=next(n for n in tree.iter('node') if n.get('content-desc')=='短滑向下斜滑边界')
    def center(n):a,b,c,e=d.bounds(n);return ((a+c)/2,(b+e)/2)
    x1,y1=center(first);x2,y2=center(second)
    up=float(first.get('range-current'));down=float(second.get('range-current'))
    radius=(y2-y1)/(math.sin(math.radians(up))+math.sin(math.radians(down)))
    cx=x1-radius*math.cos(math.radians(up));cy=y1+radius*math.sin(math.radians(up))
    return (x1,y1),cx,cy,radius,up
def pointer(start,end,hold,finish):
    command='CLASSPATH=/data/local/tmp/edgeflow-probe.jar app_process /system/bin probe.Pointer '+' '.join(map(str,[*map(round,start),*map(round,end),hold,finish]))
    return subprocess.Popen(d.A+['shell',command],stdout=subprocess.PIPE,stderr=subprocess.PIPE,text=True)

with PreferenceTrial({'edgeflow':allowed}) as trial:
    before=trial.before['edgeflow'];assert before['handle_geometry_version']==2
    d.range_settings()
    for title in ('触摸宽度','大小','位置'):assert '.' not in current(title),title
    bar=node('触摸宽度');tree=d.ui();number=next(n for n in tree.iter('node') if n.get('content-desc')=='触摸宽度当前值')
    _,bar_y,_,bar_bottom=d.bounds(bar);_,_,_,number_bottom=d.bounds(number)
    if bar_bottom-bar_y==154:
        gap=bar_y+77-35-number_bottom
        assert 10<=gap<=18,gap
        result['label_to_track_gap_px']=gap
    d.shot('safe-angle-compact-sliders')
    no_detail=node('触钮颜色');plain_height=d.bounds(no_detail)[3]-d.bounds(no_detail)[1]
    if plain_height<196:
        d._scroll(d.ui(),True);no_detail=node('触钮颜色');plain_height=d.bounds(no_detail)[3]-d.bounds(no_detail)[1]
    d.main_home();with_detail=node('手势设置');detail_height=d.bounds(with_detail)[3]-d.bounds(with_detail)[1]
    d.range_settings()
    assert plain_height==196 and detail_height>plain_height,(plain_height,detail_height)
    result['plain_and_detail_row_heights_px']=[plain_height,detail_height]
    tap_progress('大小',600);assert current('大小')=='62%'
    tap_progress('大小',1000);assert current('大小')=='100%'
    frames=windows();previews=[v for k,v in frames.items() if '触控区域预览' in k]
    handles=[v for k,v in frames.items() if ' u0 Color Hand Operation 左手势区域}' in k or ' u0 Color Hand Operation 右手势区域}' in k]
    assert len(previews)==2 and len(handles)==2,(previews,handles)
    assert all(frame[1]==140 and frame[3]==2626 for frame in previews+handles),(previews,handles)
    result['full_size_matches_safe_area_and_real_input']=True
    d.shot('safe-angle-full-area')
    # The relocated slider retains ordinary vertical scrolling without changing geometry.
    width=node('触摸宽度');a,b,c,e=d.bounds(width);saved=d.preferences()
    d.sh('input','swipe',str((a+c)//2),str((b+e)//2),str((a+c)//2+20),str((b+e)//2-280),'450');time.sleep(.3)
    assert d.preferences()==saved
    result['slider_vertical_scroll_preserves_geometry']=True
    d.main_link('手势设置');d.click_item('手势角度',True)
    tree=d.ui();assert any(n.get('content-desc')=='短滑手势角度编辑器' for n in tree.iter('node'))
    d.shot('safe-angle-graph')
    start,cx,cy,radius,original=graph_points();target=original-12 if original>=55 else original+12
    end=(cx+radius*math.cos(math.radians(target)),cy-radius*math.sin(math.radians(target)))
    saved=d.preferences();proc=pointer(start,end,1400,'CANCEL')
    assert proc.stdout.readline().strip()=='HOLDING';assert d.preferences()==saved
    d.shot('safe-angle-drag-draft');proc.communicate(timeout=5);assert proc.returncode==0
    time.sleep(.2);assert d.preferences()==saved
    result['graph_draft_cancel_does_not_write']=True
    proc=pointer(start,end,0,'UP');proc.communicate(timeout=5);assert proc.returncode==0;time.sleep(.3)
    after=d.preferences();assert abs(after['short_angle_up_inner_degrees']-target)<1.2,(after['short_angle_up_inner_degrees'],target)
    assert {k:v for k,v in after.items() if k!='short_angle_up_inner_degrees'}=={k:v for k,v in saved.items() if k!='short_angle_up_inner_degrees'}
    result['graph_release_updates_only_dragged_boundary']=True
    d.find_item('从右侧查看');saved=d.preferences();d.click_item('从右侧查看');time.sleep(.3)
    assert d.preferences()==saved;d.shot('safe-angle-mirrored')
    assert d.find_item('从左侧查看') is not None
    d.click_item('从左侧查看')
    host=node('短滑手势角度编辑器');a,b,c,e=d.bounds(host)
    saved=d.preferences();d.sh('input','swipe',str(c-40),str(b+100),str(c-40),str(b-200),'450');time.sleep(.3)
    assert d.preferences()==saved
    result['mirror_and_empty_area_scroll_do_not_edit_angles']=True
result['test_preferences_restored']=True
(d.B/'safe-angle-ui-validation.json').write_text(json.dumps(result,ensure_ascii=False,indent=2),encoding='utf-8')
print(json.dumps(result,ensure_ascii=False,indent=2))
