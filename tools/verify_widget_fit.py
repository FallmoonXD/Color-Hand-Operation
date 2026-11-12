"""Check scaled RemoteViews edges and clicks, then restore the original panel."""
from pathlib import Path
import json
import subprocess
import tempfile
import time

import device as d
import verify_widget_edit as widget

OUT=d.B/'widget-clip-ime'


def corners(wid,count):
    cell=d.bounds(widget.plugin(wid));boxes=[]
    for label in ('左上','右上','左下','右下'):
        node=d.wait_node(label);box=d.bounds(node);boxes.append(box)
        assert cell[0]<=box[0]<box[2]<=cell[2] and cell[1]<=box[1]<box[3]<=cell[3],(cell,box)
        assert abs((box[2]-box[0])/(box[3]-box[1])-1.2)<.03,'Fixed content aspect changed'
        d.tap(node);count+=1;d.wait_node('验证插件 · '+str(count))
    assert boxes[0][1]==boxes[1][1] and boxes[2][3]==boxes[3][3]
    assert boxes[0][0]==boxes[2][0] and boxes[1][2]==boxes[3][2]
    return boxes,count


def main():
    OUT.mkdir(parents=True,exist_ok=True)
    original=widget.state();prefs=d.preferences();original_ids={item['id'] for item in widget.data()}
    assert not d.sh('pm','list','packages',widget.PACKAGE).strip()
    with tempfile.TemporaryDirectory() as folder:
        apk=widget.fixture(Path(folder));subprocess.run(d.A+['install','--no-incremental',str(apk)],check=True)
        try:
            widget.open_panel();widget.add_page();fixed=widget.add_widget('CHO 固定尺寸插件')
            normal,count=corners(fixed,0)
            (OUT/'fixed-4x2.png').write_bytes(subprocess.check_output(d.A+['exec-out','screencap','-p']))
            left,top,right,bottom=d.bounds(widget.plugin(fixed));x,y=widget.pause_on(fixed);widget.event('UP',x,y)
            widget.event('DOWN',right-20,bottom-20);widget.event('MOVE',left+(right-left)/2-20,bottom-20)
            time.sleep(.2);widget.event('UP',left+(right-left)/2-20,bottom-20);time.sleep(.6);widget.back()
            assert widget.geometry(fixed)['w']==2
            small,count=corners(fixed,count)
            assert small[0][2]-small[0][0]<normal[0][2]-normal[0][0],'Smaller cell did not refit content'
            (OUT/'fixed-2x2.png').write_bytes(subprocess.check_output(d.A+['exec-out','screencap','-p']))
            adaptive=widget.add_widget();host=widget.plugin(adaptive);cell=d.bounds(host)
            content=d.bounds(next(node for node in host.iter('node') if node.get('text')=='验证插件 · '+str(count)))
            assert abs((cell[2]-cell[0])-(content[2]-content[0])-56)<=4,'Adaptive widget unexpectedly scaled'
            d.tap(widget.plugin(adaptive));count+=1;d.wait_node('验证插件 · '+str(count))
            rotating=widget.add_widget('CHO 轮播插件')
            deadline=time.monotonic()+8
            while time.monotonic()<deadline:
                texts=[node.get('text') for node in d.ui().iter('node') if node.get('text','').startswith('轮播内容 ') and d.visible(node)]
                if texts:break
            assert texts,'Collection widget failed to render'
            (OUT/'fit-results.json').write_text(json.dumps({'fixed_4x2':normal,'fixed_2x2':small,'adaptive':content,'collection':texts},ensure_ascii=False,indent=2),encoding='utf-8')
            print('PASS fixed widget four corners, eight transformed clicks, resize refit, adaptive click/size and collection viewport',flush=True)
        finally:
            d.main_home();widget.open_panel()
            while widget.state()['pages']>original['pages']:
                widget.go(widget.state()['pages']-1)
                assert not any(item['id'] in original_ids and item['page']==widget.state()['page'] for item in widget.data())
                widget.confirm_remove_page()
            widget.go(original['page']);d.main_home()
            subprocess.run(d.A+['uninstall',widget.PACKAGE],check=True)
            assert widget.state()==original and d.preferences()==prefs,'Original state not restored'
            print('PASS original widget IDs and settings restored',flush=True)


if __name__=='__main__':main()
