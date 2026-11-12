"""Exercise the normal widget host on temporary pages with an isolated RemoteViews provider."""
import hashlib
import json
import os
import re
from pathlib import Path
import subprocess
import tempfile
import time
import uuid
import zipfile
from io import BytesIO

from PIL import Image
import device as d

PACKAGE='dev.edgeflow.widgettest'
OUT=d.B/'widget-drag'

def fixture(folder):
    root=Path(__file__).resolve().parents[1]
    sdk=Path(os.environ.get('ANDROID_SDK_ROOT',root/'.toolchains/android-sdk'))
    bt=sdk/'build-tools/36.0.0';android=sdk/'platforms/android-35/android.jar'
    source=root/'validation/widget-ui'
    def run(*args):subprocess.run(list(map(str,args)),check=True)
    classes=folder/'classes';dex=folder/'dex';gen=folder/'gen'
    for path in (classes,dex,gen):path.mkdir()
    resources=folder/'resources.zip';raw=folder/'raw.apk';aligned=folder/'aligned.apk';apk=folder/'fixture.apk'
    run(bt/'aapt2.exe','compile','--dir',source/'res','-o',resources)
    run(bt/'aapt2.exe','link','-I',android,'--manifest',source/'AndroidManifest.xml','--java',gen,'-o',raw,resources)
    run('javac','-encoding','UTF-8','--release','17','-cp',android,'-d',classes,source/'Provider.java',*gen.rglob('*.java'))
    run(bt/'d8.bat','--min-api','33','--lib',android,'--output',dex,*classes.rglob('*.class'))
    with zipfile.ZipFile(raw,'a') as archive:archive.write(dex/'classes.dex','classes.dex')
    run(bt/'zipalign.exe','-f','4',raw,aligned)
    run(bt/'apksigner.bat','sign','--ks',Path.home()/'.android/debug.keystore','--ks-pass','pass:android','--out',apk,aligned)
    return apk

def state():return d.preferences('replica_widgets')
def data():return json.loads(state()['layout_v3'])
def center(node):
    l,t,r,b=d.bounds(node);return (l+r)//2,(t+b)//2
def hold(x,y):d.sh('input','swipe',str(x),str(y),str(x),str(y),'750');time.sleep(.35)
def back():d.sh('input','keyevent','4');time.sleep(.4)
def shot(name,bounds):
    im=Image.open(BytesIO(subprocess.check_output(d.A+['exec-out','screencap','-p'])))
    im.crop(bounds).save(OUT/(name+'.png'))
def action_card(name):
    n=d.wait_node(name);tree=d.ui()
    rows=[r for r in tree.iter('node') if r.get('clickable')=='true' and r.get('content-desc') and
          any(x.get('resource-id')=='com.android.launcher:id/popup_list_window_item_icon' for x in r.iter('node'))]
    assert rows and any(r.get('content-desc')==name for r in rows),'Launcher row resources did not load'
    first,last=d.bounds(rows[0]),d.bounds(rows[-1])
    pad=round((last[3]-last[1])/5)
    return rows,(first[0],first[1]-pad,last[2],last[3]+pad)
def open_panel():
    d.panel_settings('小组件面板');d.click_item('打开小组件面板',True);d.wait_widget_panel()
def page_menu():
    d.open_widget_page_menu();rows,_=action_card('添加插件')
    assert [n.get('content-desc') for n in rows]==['添加插件','添加页面','移除当前页面']
def add_page():
    old=state()['pages'];page_menu();d.click_item('添加页面',True)
    d.wait_node(f'第 {old+1} 页，共 {old+1} 页')
def go(index):
    for _ in range(state()['pages']+1):
        s=state()
        if s['page']==index:return
        l,t,r,b=d.bounds(d.wait_widget_panel());cx=(l+r)//2;y=(t+b)//2
        dist=round((r-l)*.4)*(1 if index<s['page'] else -1)
        d.sh('input','swipe',str(cx),str(y),str(cx+dist),str(y),'230');time.sleep(.45)
    raise AssertionError('Could not reach temporary page')
def confirm_remove_page():
    page_menu();d.click_item('移除当前页面',True);d.wait_node('移除当前页面？');d.click('移除');time.sleep(.5)

def widget_confirmation(wid):
    before=state()
    def open_confirmation():
        x,y=pause_on(wid);event('UP',x,y);d.click_item('移除插件',True)
        d.wait_node('要移除此插件吗？');d.wait_node('移除插件不会删除应用内数据')
    for cancel in ('button','back','outside'):
        open_confirmation()
        tree=d.ui();confirm=d.node('移除');window=confirm.get('window-id')
        assert not any(n.get('window-id')==window and n.get('content-desc','').startswith('关闭') for n in tree.iter('node'))
        navigation=[d.bounds(n)[1] for n in tree.iter('node') if n.get('package')=='com.android.systemui' and n.get('content-desc') in ('返回','主屏幕','最近任务') and d.visible(n)]
        if navigation:assert d.bounds(confirm)[3]<=min(navigation),'Confirmation overlaps navigation buttons'
        if cancel=='button':
            button=next(node for node in tree.iter('node') if node.get('window-id')==window and node.get('text')=='取消')
            (OUT/'widget-confirmation.png').write_bytes(subprocess.check_output(d.A+['exec-out','screencap','-p']))
            remote='/data/local/tmp/cho-confirmation-'+uuid.uuid4().hex+'.mp4'
            video=OUT/'confirmation-exit.mp4'
            width,height=d.current_display_size()
            recorder=subprocess.Popen(d.A+['shell','screenrecord','--size',f'632x{round(height*632/width/2)*2}','--time-limit','3',remote],stdout=subprocess.DEVNULL)
            try:
                time.sleep(.5);d.tap(button);assert recorder.wait(timeout=7)==0
                subprocess.run(d.A+['pull',remote,str(video)],check=True,capture_output=True)
                from verify_panel_exit import measure
                report=measure(video);assert report['intermediate_frames']>=3,report
                print('PASS confirmation exit',report['intermediate_frames'],'intermediate frames',flush=True)
            finally:
                if recorder.poll() is None:recorder.terminate();recorder.wait()
                d.sh('rm','-f',remote)
        elif cancel=='back':back()
        else:d.sh('input','tap',str(d.current_display_size()[0]//2),str(d.bounds(d.node('要移除此插件吗？'))[1]-150));time.sleep(.5)
        assert state()==before,'Cancel changed widget layout: '+cancel
    open_confirmation();d.click('移除');time.sleep(.5)
    assert not any(item['id']==wid for item in data())
    bindings=d.sh('dumpsys','appwidget').split('Widgets:')[1].split('Hosts:')[0]
    assert not re.search(r'\bid='+str(wid)+r'\b',bindings),'Removed widget binding retained'
    assert all(item in data() for item in json.loads(before['layout_v3']) if item['id']!=wid)
    print('PASS launcher confirmation cancel, Back, outside touch and selected binding removal',flush=True)
def add_widget(label='CHO 验证插件'):
    before={o['id'] for o in data()}
    page_menu();d.click_item('添加插件',True)
    d.set_text('搜索应用或插件','CHO')
    d.click_item(label+'，CHO 验证插件',True)
    deadline=time.monotonic()+10
    while time.monotonic()<deadline:
        found=[o for o in data() if o['id'] not in before]
        if found and d.widget_indicator() is not None:return found[0]['id']
        tree=d.ui()
        approve=next((n for n in tree.iter('node') if n.get('package') not in (None,d.P) and
                     n.get('text') in ('创建','允许','添加','Create','Allow') and n.get('clickable')=='true' and d.visible(n)),None)
        if approve is not None:d.tap(approve)
    raise AssertionError('Fixture binding not completed')
def geometry(wid):
    return next(o for o in data() if o['id']==wid)
def plugin(wid):
    return d.wait_node('插件 '+str(wid))
def event(kind,x,y):
    d.sh('input','motionevent',kind,str(round(x)),str(round(y)))
def await_page(index,count):
    return d.wait_node(f'第 {index+1} 页，共 {count} 页')
def pause_on(wid):
    x,y=center(plugin(wid));event('DOWN',x,y);time.sleep(.7);return x,y

def canceled_drag_outline(wid):
    time.sleep(.4)
    left,top,right,bottom=d.bounds(plugin(wid))
    edge=(left+(right-left)//3,top+2,right-(right-left)//3,top+40)
    before=Image.open(BytesIO(subprocess.check_output(d.A+['exec-out','screencap','-p']))).convert('RGB').crop(edge)
    x,y=pause_on(wid);event('MOVE',x+45,y);time.sleep(.2);event('CANCEL',x+45,y);time.sleep(.6)
    after=Image.open(BytesIO(subprocess.check_output(d.A+['exec-out','screencap','-p']))).convert('RGB').crop(edge)
    assert max(abs(a-b) for a,b in zip(before.tobytes(),after.tobytes()))<=3,'Detached selection outline remained after drag'
    print('PASS canceled drag clears the detached selection outline',flush=True)

def menu_exit(wid):
    from verify_panel_exit import measure
    remote='/data/local/tmp/cho-menu-'+uuid.uuid4().hex+'.mp4'
    video=OUT/'menu-touch-exit.mp4'
    recorder=subprocess.Popen(d.A+['shell','screenrecord','--size','632x1390','--time-limit','3',remote],stdout=subprocess.DEVNULL)
    try:
        time.sleep(.5);d.tap(plugin(wid))
        assert recorder.wait(timeout=7)==0
        subprocess.run(d.A+['pull',remote,str(video)],check=True,capture_output=True)
        report=measure(video)
        assert report['intermediate_frames']>=3,report
        print('PASS menu touch dismissal',report['visible_ms'],'ms',report['intermediate_frames'],'intermediate frames',flush=True)
    finally:
        if recorder.poll() is None:recorder.terminate()
        d.sh('rm','-f',remote)
    x,y=pause_on(wid);event('UP',x,y);action_card('设置')
def record_motion(name,action,crop,horizontal):
    remote='/data/local/tmp/cho-widget-motion-'+uuid.uuid4().hex+'.mp4'
    video=OUT/(name+'.mp4')
    recorder=subprocess.Popen(d.A+['shell','screenrecord','--size','632x1390','--time-limit','4',remote],stdout=subprocess.DEVNULL)
    try:
        time.sleep(.6);action()
        assert recorder.wait(timeout=7)==0
        subprocess.run(d.A+['pull',remote,str(video)],check=True,capture_output=True)
    finally:
        if recorder.poll() is None:recorder.terminate()
        d.sh('rm','-f',remote)
    width,height,x,y=crop
    raw=subprocess.check_output(['ffmpeg','-v','fatal','-i',str(video),'-vf',f'format=rgb24,crop={width}:{height}:{x}:{y}',
                                 '-pix_fmt','rgb24','-fps_mode','passthrough','-f','rawvideo','-'])
    length=width*height
    positions=[]
    for offset in range(0,len(raw),length*3):
        pixels=raw[offset:offset+length*3]
        blue=[i for i,(r,g,b) in enumerate(zip(pixels[::3],pixels[1::3],pixels[2::3]))
              if abs(r-72)<12 and abs(g-84)<12 and abs(b-101)<12]
        assert blue,'Provider pixels missing from motion recording'
        positions.append(max(blue)-min(blue)+1 if horizontal else max(blue))
    return positions

def press_motion(wid):
    l,t,r,b=d.bounds(plugin(wid));x,y=(l+r)//2,(t+b)//2
    def action():
        event('DOWN',x,y);time.sleep(.85);event('UP',x,y)
    values=record_motion('widget-press',action,(round((r-l)/2),1,round(l/2),round((t+.22*(b-t))/2)),True)
    assert len(set(values))>=4 and .92<=min(values)/values[0]<=.97 and abs(values[-1]-values[0])<=2,values
    print('PASS press shrinks then restores',len(set(values)),'widths; minimum ratio',round(min(values)/values[0],3),flush=True)
    back()

def check_hidden_picker():
    page_menu();d.click_item('添加插件',True)
    d.set_text('搜索应用或插件','CHO 隐藏插件');d.wait_node('没有找到匹配的插件')
    assert not any(n.get('content-desc')=='CHO 隐藏插件，CHO 验证插件' for n in d.ui().iter('node'))
    d.click('取消')
    print('PASS provider hide_from_picker flag respected',flush=True)

def exercise(before):
    n=before['pages'];original_page=before['page']
    open_panel();add_page();check_hidden_picker()
    dots=d.wait_widget_panel();cl,ct,cr,cb=d.bounds(d.wait_node('插件页面'))
    cw=(cr-cl)/4;ch=(cb-ct)/4;dl,dt,dr,db=d.bounds(dots)
    assert dots.get('clickable')!='true'
    for x,y in [((cl+cr)//2,ct-5),(cl-5,(ct+cb)//2),((dl+dr)//2,db+5)]:
        hold(x,y);rows,b=action_card('添加插件');assert [r.get('content-desc') for r in rows]==['添加插件','添加页面','移除当前页面']
        shot('page-menu',b)
        im=Image.open(OUT/'page-menu.png').convert('L');sizes=[]
        for row in rows[1:]:
            icon=next(v for v in row.iter('node') if v.get('resource-id')=='com.android.launcher:id/popup_list_window_item_icon')
            x1,y1,x2,y2=d.bounds(icon);box=im.crop((x1-b[0],y1-b[1],x2-b[0],y2-b[1])).point(lambda v:255 if v<175 else 0).getbbox()
            sizes.append((box[2]-box[0],box[3]-box[1]))
        assert max(abs(a-b) for a,b in zip(*sizes))<=1,sizes
        back()
    first=add_widget();second=add_widget()
    assert geometry(first)['x']==0 and geometry(second)['x']==2
    canceled_drag_outline(first)
    press_motion(first)
    d.tap(d.wait_node('验证插件 · 0'));d.wait_node('验证插件 · 1')
    # Hold without lifting: both frame and the provider-specific menu must already be visible.
    ax,ay=pause_on(first)
    rows,menu_bounds=action_card('移除插件')
    assert [r.get('content-desc') for r in rows]==['移除插件']
    bounds=d.bounds(plugin(first));shot('long-press',(cl,ct,cr,max(bounds[3],menu_bounds[3])))
    def corner_pixels(bounds):
        l,t,r,b=bounds;im=Image.open(BytesIO(subprocess.check_output(d.A+['exec-out','screencap','-p']))).convert('RGB')
        return sum(1 for rgb in im.crop((r-100,b-100,r-12,b-12)).get_flattened_data() if min(rgb)>248)
    assert corner_pixels(bounds)>100,'Resize arc missing while native menu is visible'
    event('MOVE',ax+50,ay+4);event('MOVE',ax+2*cw,ay);time.sleep(.25);event('UP',ax+2*cw,ay);time.sleep(.45)
    assert geometry(first)['x']==2 and geometry(second)['x']==0,'Continuous long drag did not swap the two slots'
    d.wait_node('验证插件 · 1')
    print('PASS same-pointer long press to swap and provider click ownership',flush=True)
    # After releasing the original press, grab the trailing lower arc with the popup still present.
    x,y=pause_on(first);event('UP',x,y);action_card('移除插件')
    l,t,r,b=d.bounds(plugin(first));gx,gy=r-20,b-20
    def resize():
        event('DOWN',gx,gy);event('MOVE',gx,gy+ch);time.sleep(.15);event('UP',gx,gy+ch)
    values=record_motion('widget-resize',resize,(1,round((cb-t)/2),round((l+.18*(r-l))/2),round(t/2)),False)
    assert len(set(values))>=6 and values[-1]>values[0]+40,values
    print('PASS resize has',len(set(values)),'intermediate heights',flush=True)
    assert geometry(first)['h']==2,'Corner resize required a second menu selection'
    old=geometry(first).copy();l,t,r,b=d.bounds(plugin(first));gx,gy=r-20,b-20
    event('DOWN',gx,gy);event('MOVE',gx-cw,gy-ch);event('CANCEL',gx-cw,gy-ch);time.sleep(.25)
    assert geometry(first)==old;back()
    print('PASS direct corner resize and cancel',flush=True)
    add_page();third=add_widget();go(n);time.sleep(.8)
    def cross(cancel):
        x,y=pause_on(first);event('MOVE',x+45,y);event('MOVE',cr-10,y)
        # Move away before a second edge dwell. An accessibility dump can take longer than a dwell.
        time.sleep(.57)
        tx,ty=cl+cw,ct+.5*ch;event('MOVE',tx,ty);await_page(n+1,n+2)
        event('CANCEL' if cancel else 'UP',tx,ty);time.sleep(.85)
    snapshot=state();cross(True);assert state()==snapshot
    wpx,hpx=d.current_display_size();scale=632/wpx
    remote='/data/local/tmp/cho-widget-exchange.mp4';raw=OUT/'exchange-raw.mp4'
    rec=subprocess.Popen(d.A+['shell','screenrecord','--size',f'632x{round(hpx*scale/2)*2}','--bit-rate','8000000','--time-limit','7',remote])
    try:
        time.sleep(.5);cross(False);assert rec.wait(timeout=10)==0
        subprocess.run(d.A+['pull',remote,str(raw)],check=True,capture_output=True)
    finally:
        if rec.poll() is None:rec.terminate()
        d.sh('rm','-f',remote)
    subprocess.run(['ffmpeg','-y','-loglevel','error','-i',str(raw),'-vf',
                    f'crop={round((cr-cl)*scale/2)*2}:{round((db-ct)*scale/2)*2}:{round(cl*scale)}:{round(ct*scale)}',
                    '-fps_mode','vfr','-enc_time_base','1:90000','-an','-c:v','libx264','-pix_fmt','yuv420p','-movflags','+faststart',str(OUT/'exchange.mp4')],check=True)
    assert (geometry(first)['page'],geometry(first)['x'])==(n+1,0),(state()['page'],geometry(first),geometry(third))
    assert (geometry(third)['page'],geometry(third)['x'])==(n,2),(geometry(first),geometry(third))
    print('PASS cross-page swap of unlike sizes, cancel restores the original page and bindings',flush=True)
    # Hold over the trailing empty workspace and cancel: a speculative page is never saved.
    snapshot=state();x,y=pause_on(first);event('MOVE',x+45,y);event('MOVE',cr-10,y)
    await_page(n+2,n+3);event('CANCEL',cr-10,y);time.sleep(.9);assert state()==snapshot
    configurable=add_widget('CHO 可设置插件')
    time.sleep(.4)
    left,top,right,bottom=d.bounds(plugin(configurable))
    # Sample the straight top edge, away from the provider's centered label and the menu.
    edge=(left+(right-left)//3,top+2,right-(right-left)//3,top+40)
    before_selection=Image.open(BytesIO(subprocess.check_output(d.A+['exec-out','screencap','-p']))).convert('RGB').crop(edge)
    x,y=pause_on(configurable);event('UP',x,y);rows,bounds=action_card('设置')
    assert [r.get('content-desc') for r in rows]==['设置','编辑插件','移除插件']
    assert corner_pixels(d.bounds(plugin(configurable)))<100,'Fixed widget received a resize handle'
    after_selection=Image.open(BytesIO(subprocess.check_output(d.A+['exec-out','screencap','-p']))).convert('RGB').crop(edge)
    shade=0 if d.sh('settings','get','secure','launcher_wallpaper_bright').strip()=='true' else 255
    for a,b in zip(before_selection.tobytes(),after_selection.tobytes()):
        blended=(a*215+shade*40)/255
        assert min(a,blended)-3<=b<=max(a,blended)+3,'Fixed widget received more than the launcher backdrop'
    print('PASS fixed widget has no resize handle or selection outline',flush=True)
    menu_exit(configurable)
    shot('provider-menu',bounds);d.click_item('设置',True);d.click('关闭提供方设置');time.sleep(.5)
    if d.widget_indicator() is None:open_panel()
    x,y=pause_on(configurable);event('UP',x,y);d.click_item('编辑插件',True);d.click('完成配置 '+str(configurable))
    d.wait_widget_panel();assert geometry(configurable)['page']==n+1
    print('PASS real provider settings shortcut and reconfiguration of the existing ID',flush=True)
    for edge,target,count in [(cr-10,n+2,n+3),(cl+10,n+1,n+3)]:
        x,y=pause_on(first);event('MOVE',x+42,y);event('MOVE',edge,y);time.sleep(.58)
        tx,ty=cl+cw,ct+ch;event('MOVE',tx,ty);await_page(target,count);event('UP',tx,ty);time.sleep(.85)
        assert geometry(first)['page']==target and state()['pages']==count,(target,geometry(first))
    go(n+2);confirm_remove_page()
    print('PASS extending the workspace by dropping, dragging back and removing the empty page',flush=True)
    go(n);widget_confirmation(second);confirm_remove_page()
    assert state()['pages']==n+1 and geometry(first)['page']==n and geometry(configurable)['page']==n
    confirm_remove_page();go(original_page);assert state()==before
    print('PASS page removal/renumbering and original layout restored',flush=True)

def main():
    OUT.mkdir(parents=True,exist_ok=True);before=state();prefs=d.preferences()
    assert not d.sh('pm','list','packages',PACKAGE).strip()
    with tempfile.TemporaryDirectory() as tmp:
        apk=fixture(Path(tmp));subprocess.run(d.A+['install','--no-incremental',str(apk)],check=True)
        try:exercise(before)
        finally:
            if state()!=before:
                d.main_home();open_panel()
                while state()['pages']>before['pages']:
                    last=state()['pages']-1;go(last)
                    original_ids={i['id'] for i in json.loads(before['layout_v3'])}
                    assert not any(i['id'] in original_ids and i['page']==last for i in data())
                    confirm_remove_page()
                go(before['page'])
            d.main_home()
            installed=d.sh('pm','path',PACKAGE).strip().removeprefix('package:')
            assert d.sh('sha256sum',installed).split()[0]==hashlib.sha256(apk.read_bytes()).hexdigest()
            subprocess.run(d.A+['uninstall',PACKAGE],check=True)
            assert d.preferences()==prefs and state()==before

if __name__=='__main__':main()
