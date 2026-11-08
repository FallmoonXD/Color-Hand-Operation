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
    landscape=metrics['width']>metrics['height']
    left,right=(0,metrics['width']) if landscape else (metrics['left'],metrics['right'])
    density=int(re.findall(r'density: (\d+)',d.sh('wm','density'))[-1])/160
    expected={}
    for side,name in [('left','左手势区域'),('right','右手势区域')]:
        width=before.get(side+'_handle_width_dp',before.get('handle_width_dp',12.3))
        if landscape:width=before.get(side+'_landscape_handle_width_dp',width)
        width=min(max(2,int(width*density+.5)),max(2,(right-left)//8))
        height=metrics['bottom']-metrics['top']
        start=before.get(side+'_handle_start_fraction',before.get('handle_start_fraction',.13084841))
        end=before.get(side+'_handle_end_fraction',before.get('handle_end_fraction',1))
        y=metrics['top']+min(height-1,int(start*height+.5))
        bottom=min(metrics['bottom'],max(y+1,metrics['top']+int(end*height+.5)))
        x=left if side=='left' else right-width
        expected[name]=[x,y,x+width,bottom]
        if before.get('enabled',True) and before.get(side+'_enabled',True):
            expected['触控区域预览 '+side]=expected[name]
    for _ in range(8):
        frames=inspect_frames()
        if frames==expected:break
        time.sleep(.25)
    assert frames==expected,(label,metrics,frames,expected)
    result[label]={'area':metrics,'frames':frames}
try:
    d.main_link('手势设置');d.click_item('手势角度',True)
    d.sh('settings','put','system','font_scale','1.6');time.sleep(.8)
    d.find_item('短滑手势角度编辑器',True);d.shot('safe-angle-large-font')
    tree=d.ui();host=next(n for n in tree.iter('node') if n.get('content-desc')=='短滑手势角度编辑器')
    buttons=[n for n in tree.iter('node') if d.visible(n) and '从左侧查看' in (n.get('content-desc'),n.get('text'))]
    assert buttons,'Missing angle direction control'
    h=d.bounds(host);assert all(d.bounds(n)[3]<=h[1] for n in buttons),(h,[d.bounds(n) for n in buttons])
    result['large_font_graph_and_mirror_do_not_overlap']=True
    d.sh('settings','put','system','font_scale',font);time.sleep(.7)
    d.main_home();verify_frame_area('portrait')
    for value in ('1','3'):
        d.sh('wm','user-rotation','lock',value);time.sleep(1.2)
        verify_frame_area('landscape-'+value)
    d.shot('safe-angle-landscape')
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
