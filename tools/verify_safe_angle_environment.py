"""Large text layout plus portrait/landscape insets; preserve system and app settings."""
import json,re,time
import device as d

before=d.preferences();font=d.sh('settings','get','system','font_scale').strip()
rotation=d.sh('wm','user-rotation').strip();preferred=d.sh('settings','get','system','user_rotation').strip();result={}
def area():
    # Independent WindowManager evidence, without creating an app_process context.
    raw=d.sh('dumpsys','window','displays')
    frame=re.search(r'mDisplayFrame=Rect\(0, 0 - (\d+), (\d+)\)',raw)
    assert frame,raw
    width,height=map(int,frame.groups())
    cutout=re.search(r'mDisplayCutout=DisplayCutout\{insets=Rect\((\d+), (\d+) - (\d+), (\d+)\)',raw)
    edges=list(map(int,cutout.groups())) if cutout else [0,0,0,0]
    for line in raw.splitlines():
        if not re.search(r'type=(statusBars|navigationBars) ',line):continue
        found=re.search(r'frame=\[(\d+),(\d+)\]\[(\d+),(\d+)\].*sideHint=(LEFT|TOP|RIGHT|BOTTOM)',line)
        assert found,line
        a,b,c,e=map(int,found.groups()[:4]);side=found[5]
        index=('LEFT','TOP','RIGHT','BOTTOM').index(side)
        edges[index]=max(edges[index],(c,e,width-a,height-b)[index])
    return dict(width=width,height=height,left=edges[0],top=edges[1],right=width-edges[2],bottom=height-edges[3])
def inspect_frames():
    found={}
    for block in re.split(r'(?m)^  Window #',d.sh('dumpsys','window','windows')):
        heading=block.splitlines()[0] if block else ''
        for name in ('左手势区域','右手势区域','触控区域预览 left','触控区域预览 right'):
            if ' u0 Color Hand Operation '+name+'}' not in heading:continue
            frame=re.search(r' frame=\[(-?\d+),(-?\d+)\]\[(-?\d+),(-?\d+)\]',block)
            if frame:found[name]=list(map(int,frame.groups()))
    return found
def verify_frame_area(label):
    metrics=area()
    frames={}
    for _ in range(8):
        frames=inspect_frames()
        if len(frames)==4 and all(v[1]==metrics['top'] and v[3]==metrics['bottom'] for v in frames.values()):break
        time.sleep(.25)
    assert len(frames)==4,(label,metrics,frames)
    for name,frame in frames.items():
        assert frame[1]==metrics['top'] and frame[3]==metrics['bottom'],(label,metrics,frames)
        if 'left' in name or name=='左手势区域':assert frame[0]==metrics['left']
        else:assert frame[2]==metrics['right']
    result[label]={'area':metrics,'frames':frames}
try:
    d.main_link('手势设置');d.click_item('手势角度',True)
    d.sh('settings','put','system','font_scale','1.6');time.sleep(.8)
    d.find_item('短滑手势角度编辑器',True);d.shot('safe-angle-large-font')
    tree=d.ui();host=next(n for n in tree.iter('node') if n.get('content-desc')=='短滑手势角度编辑器');button=next(n for n in tree.iter('node') if n.get('content-desc') in ('短滑从右侧查看','短滑从左侧查看'))
    h=d.bounds(host);b=d.bounds(button);assert h[2]<=b[0] or b[2]<=h[0],(h,b)
    result['large_font_graph_and_mirror_do_not_overlap']=True
    d.sh('settings','put','system','font_scale',font);time.sleep(.7)
    d.main_home();verify_frame_area('portrait')
    d.sh('wm','user-rotation','lock','1');time.sleep(1.2)
    verify_frame_area('landscape');d.shot('safe-angle-landscape')
    assert d.preferences()==before
    result['rotation_does_not_rewrite_geometry']=True
finally:
    if font=='null':d.sh('settings','delete','system','font_scale')
    else:d.sh('settings','put','system','font_scale',font)
    d.sh('wm','user-rotation','lock',preferred if preferred!='null' else '0')
    if rotation=='free':d.sh('wm','user-rotation','free')
    time.sleep(.8)
    result['font_restored']=d.sh('settings','get','system','font_scale').strip()==font
    result['rotation_mode_restored']=d.sh('wm','user-rotation').strip()==rotation
    result['preferences_unchanged']=d.preferences()==before
    (d.B/'safe-angle-environment-validation.json').write_text(json.dumps(result,ensure_ascii=False,indent=2),encoding='utf-8')
print(json.dumps(result,ensure_ascii=False,indent=2))
