"""Exercise the normal widget host on temporary pages with an isolated RemoteViews provider."""
import hashlib
import json
import os
from pathlib import Path
import subprocess
import tempfile
import time
import zipfile
from io import BytesIO

from PIL import Image
import device as d

PACKAGE='dev.edgeflow.widgettest'
OUT=d.B/'launcher-widgets'

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
    d.panel_settings('插件');d.click_item('打开插件面板',True);d.wait_widget_panel()
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
def add_widget():
    page_menu();d.click_item('添加插件',True)
    d.click('搜索应用或插件');d.sh('input','text','CHO')
    d.click_item('CHO 验证插件，CHO 验证插件',True)
    deadline=time.monotonic()+10
    while time.monotonic()<deadline:
        found=[o for o in data() if o['page']==state()['page']]
        if found and d.widget_indicator() is not None:return found[0]['id']
        tree=d.ui()
        approve=next((n for n in tree.iter('node') if n.get('package') not in (None,d.P) and
                     n.get('text') in ('创建','允许','添加','Create','Allow') and n.get('clickable')=='true' and d.visible(n)),None)
        if approve is not None:d.tap(approve)
    raise AssertionError('Fixture binding not completed')
def exercise(before):
    pages=before['pages'];page=before['page']
    open_panel()
    dot=d.wait_widget_panel();assert dot.get('clickable')!='true';d.tap(dot);time.sleep(.25)
    assert not any(n.get('content-desc')=='添加插件' for n in d.ui().iter('node'))
    add_page();assert state()['pages']==pages+1 and state()['page']==pages
    dots=d.wait_widget_panel();l,t,r,b=d.bounds(d.wait_node('插件页面'));dl,dt,dr,db=d.bounds(dots)
    # Center, top/side outer padding and the empty footer all invoke the same menu.
    for x,y in [((l+r)//2,(t+b)//2),((l+r)//2,t-5),(l-6,(t+b)//2),((dl+dr)//2,db+5)]:
        hold(x,y);rows,bounds=action_card('添加插件')
        assert [n.get('content-desc') for n in rows]==['添加插件','添加页面','移除当前页面']
        shot('blank-menu',bounds);back()
    wid=add_widget()
    cell='插件 '+str(wid);node=d.wait_node(cell);label=d.wait_node('验证插件 · 0')
    d.tap(label);d.wait_node('验证插件 · 1')
    cl,ct,cr,cb=d.bounds(d.wait_node('插件页面'));footer=d.bounds(d.wait_widget_panel())
    hold((cl+cr)//2,cb-15);action_card('添加插件');back();d.wait_node('验证插件 · 1')
    hold(*center(node));rows,bounds=action_card('调整大小');shot('plugin-menu',bounds)
    d.click_item('调整大小',True);time.sleep(.45)
    # The frame owns touch while resizing.
    def geometry():return next(o for o in data() if o['id']==wid)
    def frame():
        tree=d.ui()
        assert any(n.get('resource-id')=='com.android.launcher:id/widget_resize_frame' for n in tree.iter('node'))
        return d.bounds(d.wait_node(cell))
    def drag(edge,dx,dy,cancel=False):
        l,t,r,b=frame();x=l+28 if edge=='left' else (l+r)//2;y=b-28 if edge=='bottom' else (t+b)//2
        if cancel:
            for ev,px,py in [('DOWN',x,y),('MOVE',x+dx,y+dy),('CANCEL',x+dx,y+dy)]:d.sh('input','motionevent',ev,str(px),str(py))
        else:d.sh('input','swipe',str(x),str(y),str(x+dx),str(y+dy),'420')
        time.sleep(.4)
    cl,ct,cr,cb=d.bounds(d.wait_node('插件页面'));cw=(cr-cl)//4;ch=(cb-ct)//4
    shot('resize',frame());shot('panel',(cl,ct,cr,footer[3]));drag('left',cw,0);assert (geometry()['x'],geometry()['w'])==(1,3)
    drag('bottom',0,-ch);assert geometry()['h']==1
    saved=geometry();drag('left',-cw,0,True);assert geometry()==saved
    back();d.wait_node('验证插件 · 1')
    # The long press, including its release, never launches the RemoteViews click.
    hold(*center(d.wait_node(cell)));d.click_item('移除插件',True);d.click('取消');d.wait_node('验证插件 · 1')
    add_page();second=add_widget();go(pages)
    page_menu();d.click_item('移除当前页面',True);d.click('取消');assert geometry()==saved
    confirm_remove_page();assert state()['pages']==pages+1 and all(o['id']!=wid for o in data())
    assert state()['page']==pages and any(o['id']==second and o['page']==pages for o in data())
    hold(*center(d.wait_node('插件 '+str(second))));d.click_item('移除插件',True);d.click('移除');time.sleep(.55)
    assert all(o['id']!=second for o in data())
    confirm_remove_page();go(page);assert state()==before
    print('PASS native row and frame IDs, empty areas, footer swipes, provider click ownership, white resize, cancellation, page removal and initial layout')

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
