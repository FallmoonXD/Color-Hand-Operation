"""Compare CHO with the stock toolbar and three-button SystemUI on a light ColorOS device.

Run with back, home and recents already assigned to the rail. Never change the user's assignments.
"""
from io import BytesIO
import json
import subprocess
import time

from PIL import Image, ImageStat
import device as d


def shot():
    return Image.open(BytesIO(subprocess.check_output(d.A+['exec-out','screencap','-p']))).convert('RGB')


def patch(image,node,fraction):
    left,top,right,bottom=d.bounds(node)
    cx,cy=(left+right)//2,(top+bottom)//2
    side=round((bottom-top)*fraction)
    return image.crop((cx-side,cy-side,cx+side,cy+side))


def outline(image,cut):
    return {(x,y) for y in range(image.height) for x in range(image.width) if max(image.getpixel((x,y)))<cut}


def match(ref,actual,radius):
    assert ref and actual,'Missing glyph'
    best=0
    for dx in range(-radius,radius+1):
        for dy in range(-radius,radius+1):
            moved={(x+dx,y+dy) for x,y in actual}
            best=max(best,len(ref & moved)/len(ref | moved))
    return best


def centered_ink(image):
    background=max(ImageStat.Stat(image).median)
    cut=(background+255)/2
    points={(x,y) for y in range(image.height) for x in range(image.width)
            if min(image.getpixel((x,y)))>cut}
    assert points and max(min(image.getpixel(p)) for p in points)>=250,'Glyph is not white'
    return points


def centroid(points):
    return tuple(sum(p[i]+.5 for p in points)/len(points) for i in (0,1))


def check_rail_backdrop(page,opened,root,grip):
    # Check the straight side margins inside the rail, between the window edge and
    # the glass buttons. Sharp Settings text or gears here exposed the old gaps.
    left,top,right,bottom=d.bounds(root)
    dp=(d.bounds(grip)[3]-d.bounds(grip)[1])/32
    page=page.convert('L');opened=opened.convert('L')
    edges=leaks=0
    for offset in range(round(2*dp),round(5*dp)):
        for x in (left+offset,right-offset-1):
            for y in range(top+round(26*dp),bottom-round(26*dp)):
                if abs(page.getpixel((x,y))-page.getpixel((x,y+1)))<48:continue
                edges+=1
                if abs(opened.getpixel((x,y))-opened.getpixel((x,y+1)))>24:leaks+=1
    if edges>=12:assert leaks<=max(2,edges//10),(edges,leaks)
    return edges


def run():
    prefs=d.preferences()
    out={}
    try:
        d.sh('am','start','-W','-a','android.settings.DISPLAY_SETTINGS')
        d.wait_node('显示与亮度');tree=d.ui()
        back=next(n for n in tree.iter('node') if n.get('resource-id')=='com.android.settings:id/coui_toolbar_back_view')
        time.sleep(.5);native=patch(shot(),back,.37)
        d.panel_settings('虚拟导航键')
        back=next(n for n in d.ui().iter('node') if n.get('package')==d.P and n.get('content-desc')=='返回' and d.visible(n))
        icon=next(n for n in back.iter('node') if n.get('class')=='android.widget.ImageView')
        time.sleep(.5);actual=patch(shot(),icon,.37)
        out['toolbar']=match(outline(native,128),outline(actual,128),0)
        assert out['toolbar']>.98,out
        for side in ('左','右'):
            page=shot()
            d.click_item(side+'侧边缘',True);d.wait_node('关闭虚拟导航键');time.sleep(.7)
            tree=d.ui();screen=shot()
            grip=next(n for n in tree.iter('node') if n.get('content-desc')=='关闭虚拟导航键')
            rail=next(root for root in tree if grip in root.iter('node'))
            out[side+'背景边缘样点']=check_rail_backdrop(page,screen,rail,grip)
            left=d.bounds(grip)[0]
            close=patch(screen,grip,.28)
            assert max(abs(v-close.width/2) for v in centroid(centered_ink(close)))<=1.5
            for name,resource in [('返回','back'),('主屏幕','home'),('最近任务','recent_apps')]:
                system=next(n for n in tree.iter('node') if n.get('resource-id')=='com.android.systemui:id/'+resource)
                tile=next(n for n in tree.iter('node') if n.get('content-desc')==name and d.bounds(n)[0]==left and d.visible(n))
                # Match at native size; SystemUI centers the viewport, CHO centers visible ink.
                ref=patch(screen,system,.22)
                x,y,r,b=d.bounds(tile);cx,cy=(x+r)//2,(y+b)//2;half=ref.width//2
                actual=screen.crop((cx-half,cy-half,cx+half,cy+half))
                # SystemUI can dim its own icons while the overlay is open.
                gray=ref.convert('L')
                midpoint=(ImageStat.Stat(gray).median[0]+gray.getextrema()[0])/2
                system_ink=outline(ref,midpoint)
                ours=centered_ink(actual)
                cx,cy=centroid(ours)
                assert max(abs(cx-half),abs(cy-half))<=1.5,(side,resource,cx,cy,half)
                sx,sy=centroid(system_ink)
                shifted={(x+round(cx-sx),y+round(cy-sy)) for x,y in system_ink}
                score=match(shifted,ours,1);out[side+resource]=round(score,4);assert score>.94,out
            d.click('关闭虚拟导航键');time.sleep(.5)
        print(json.dumps(out,ensure_ascii=False))
    finally:
        grip=next((n for n in d.ui().iter('node') if n.get('content-desc')=='关闭虚拟导航键' and d.visible(n)),None)
        if grip is not None:d.tap(grip);time.sleep(.5)
        d.main_home()
        assert d.preferences()==prefs,'Preview changed user settings'


if __name__=='__main__':
    run()
