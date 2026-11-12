"""Record picker transitions and exercise keyboard geometry without changing widget bindings."""
from pathlib import Path
import json
import subprocess
import time
import uuid
import xml.etree.ElementTree as ET

import device as d
import verify_widget_edit as widget

OUT=d.B/'widget-clip-ime'


def capture(name):
    tree=d.ui()
    (OUT/(name+'.xml')).write_bytes(ET.tostring(tree,encoding='utf-8'))
    (OUT/(name+'.png')).write_bytes(subprocess.check_output(d.A+['exec-out','screencap','-p']))
    (OUT/(name+'-windows.txt')).write_text(d.sh('dumpsys','window','windows'),encoding='utf-8')
    return tree


def record(name,action):
    width,height=d.current_display_size()
    remote='/data/local/tmp/cho-picker-'+uuid.uuid4().hex+'.mp4'
    path=OUT/(name+'.mp4')
    recorder=subprocess.Popen(d.A+['shell','screenrecord','--size',f'632x{round(height*632/width/2)*2}','--bit-rate','9000000','--time-limit','4',remote],stdout=subprocess.DEVNULL)
    try:
        time.sleep(.7);action()
        assert recorder.wait(timeout=7)==0
        subprocess.run(d.A+['pull',remote,str(path)],check=True,capture_output=True)
    finally:
        if recorder.poll() is None:recorder.terminate();recorder.wait()
        d.sh('rm','-f',remote)
    return path


def geometry():
    tree=d.ui()
    search=next(node for node in tree.iter('node') if node.get('content-desc')=='搜索应用或插件')
    footer=next(node for node in tree.iter('node') if node.get('text')=='取消' and node.get('window-id')==search.get('window-id'))
    navigation=[d.bounds(node) for node in tree.iter('node') if node.get('package')=='com.android.systemui' and node.get('content-desc') in ('返回','主屏幕','最近任务') and d.visible(node)]
    left,top,right,bottom=d.bounds(footer)
    for nav_left,nav_top,nav_right,nav_bottom in navigation:
        assert right<=nav_left or nav_right<=left or bottom<=nav_top or nav_bottom<=top,'Footer overlaps navigation'
    assert d.visible(search) and d.visible(footer),'Search or footer is clipped'
    return {'search':d.bounds(search),'footer':d.bounds(footer)}


def main():
    OUT.mkdir(parents=True,exist_ok=True)
    before=widget.state();prefs=d.preferences()
    widget.open_panel();widget.page_menu();add=d.wait_node('添加插件')
    record('picker-enter',lambda:d.tap(add));d.wait_node('搜索应用或插件')
    closed=geometry();capture('picker-shown')
    field=d.wait_node('搜索应用或插件')
    record('picker-ime-open',lambda:d.tap(field));opened=geometry();capture('picker-ime-shown')
    assert opened['footer'][3]<closed['footer'][3]-100,'Keyboard did not move footer'
    d.set_text('搜索应用或插件','CHO-no-such-provider');d.wait_node('没有找到匹配的插件')
    assert geometry()==opened,'Empty results changed geometry'
    record('picker-ime-close',lambda:d.sh('input','keyevent','4'))
    assert geometry()==closed,'Closing keyboard did not restore geometry'
    assert d.wait_node('搜索应用或插件').get('text')=='CHO-no-such-provider','Search text lost'
    d.set_text('搜索应用或插件',' ')
    for _ in range(3):
        d.tap(d.wait_node('搜索应用或插件'));time.sleep(.12);d.sh('input','keyevent','4');time.sleep(.5)
        assert geometry()==closed,'Interrupted IME transition left stale geometry'
    d.tap(d.wait_node('搜索应用或插件'));time.sleep(.5)
    button=d.wait_node('取消');record('picker-exit-ime',lambda:d.tap(button))
    d.wait_widget_panel();capture('picker-closed')
    assert not any(node.get('content-desc')=='搜索应用或插件' and d.visible(node) for node in d.ui().iter('node'))
    assert widget.state()==before and d.preferences()==prefs,'Picker changed saved preferences'
    service=d.sh('settings','get','secure','enabled_accessibility_services')
    assert 'dev.edgeflow/' in service,'Overlay service was disabled'
    (OUT/'picker-geometry.json').write_text(json.dumps({'closed':closed,'opened':opened},indent=2),encoding='utf-8')
    print('PASS picker enter/exit recordings, IME toggle/reversal, empty search, retained query, footer and original preferences',flush=True)


if __name__=='__main__':main()
