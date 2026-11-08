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


def run():
    prefs=d.preferences()
    out={}
    try:
        d.sh('am','start','-W','-a','android.settings.DISPLAY_SETTINGS')
        d.wait_node('显示与亮度');tree=d.ui()
        back=next(n for n in tree.iter('node') if n.get('resource-id')=='com.android.settings:id/coui_toolbar_back_view')
        time.sleep(.5);native=patch(shot(),back,.37)
        d.panel_settings('导航键面板')
        back=next(n for n in d.ui().iter('node') if n.get('package')==d.P and n.get('content-desc')=='返回' and d.visible(n))
        icon=next(n for n in back.iter('node') if n.get('class')=='android.widget.ImageView')
        time.sleep(.5);actual=patch(shot(),icon,.37)
        out['toolbar']=match(outline(native,128),outline(actual,128),0)
        assert out['toolbar']>.98,out
        for side in ('左','右'):
            d.click_item(side+'侧边缘',True);d.wait_node('关闭导航键面板');time.sleep(.7)
            tree=d.ui();screen=shot()
            left=d.bounds(next(n for n in tree.iter('node') if n.get('content-desc')=='关闭导航键面板'))[0]
            for name,resource in [('返回','back'),('主屏幕','home'),('最近任务','recent_apps')]:
                system=next(n for n in tree.iter('node') if n.get('resource-id')=='com.android.systemui:id/'+resource)
                tile=next(n for n in tree.iter('node') if n.get('content-desc')==name and d.bounds(n)[0]==left and d.visible(n))
                # Match at display pixels, without resizing masks. SystemUI may round its
                # origin differently by one pixel when centering an odd-sized vector.
                ref=patch(screen,system,.22)
                x,y,r,b=d.bounds(tile);cx,cy=(x+r)//2,(y+b)//2;half=ref.width//2
                actual=screen.crop((cx-half,cy-half,cx+half,cy+half))
                masks=[outline(pic,max(ImageStat.Stat(pic).median)*.735) for pic in (ref,actual)]
                score=match(*masks,2);out[side+resource]=round(score,4);assert score>.94,out
            d.click('关闭导航键面板');time.sleep(.5)
        print(json.dumps(out,ensure_ascii=False))
    finally:
        grip=next((n for n in d.ui().iter('node') if n.get('content-desc')=='关闭导航键面板' and d.visible(n)),None)
        if grip is not None:d.tap(grip);time.sleep(.5)
        d.main_home()
        assert d.preferences()==prefs,'Preview changed user settings'


if __name__=='__main__':
    run()
