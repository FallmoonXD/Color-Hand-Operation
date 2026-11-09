"""Measure widget page translation, and monitor the live blur session while swiping (PKR110)."""
import json
from pathlib import Path
import re
import subprocess
import tempfile
import time
import uuid

import device as d
import verify_widget_edit as w


def blur():
    raw=d.sh('su','-c','content call --uri content://dev.edgeflow.material --method stats')
    return {k:int(v) for k,v in re.findall(r'(active|starts|stops)=(\d+)',raw)}


def record(out,name,viewport,action,duration=5):
    l,t,r,b=viewport;width,height=d.current_display_size()
    rw=632;rh=round(height*rw/width/2)*2
    remote='/data/local/tmp/cho-widget-'+uuid.uuid4().hex+'.mp4'
    raw=out/(name+'-raw.mp4')
    recorder=subprocess.Popen(d.A+['shell','screenrecord','--size',f'{rw}x{rh}','--bit-rate','9000000','--time-limit',str(duration),remote],stdout=subprocess.DEVNULL)
    try:
        time.sleep(.7);action();assert recorder.wait(timeout=duration+3)==0
        subprocess.run(d.A+['pull',remote,str(raw)],check=True,capture_output=True)
    finally:
        if recorder.poll() is None:recorder.terminate()
        d.sh('rm','-f',remote)
    s=rw/width
    x,y=round(l*s),round((t+.08*(b-t))*s)
    line_width=round((r-l)*s)
    def line(ycoord):
        return subprocess.check_output(['ffmpeg','-loglevel','fatal','-i',str(raw),'-vf',f'format=rgb24,crop={line_width}:1:{x}:{ycoord}',
            '-pix_fmt','rgb24','-fps_mode','passthrough','-enc_time_base','1:90000','-f','rawvideo','-'])
    pixels=line(y);edges=[]
    for i in range(0,len(pixels),3*line_width):
        row=pixels[i:i+3*line_width]
        blue=[j for j,(a,g,c) in enumerate(zip(row[::3],row[1::3],row[2::3])) if abs(a-72)<12 and abs(g-84)<12 and abs(c-101)<12]
        edges.append(max(blue,default=-1))
    partial={e for e in edges if 2<e<line_width*.46}
    assert len(partial)>=7,(name,len(partial),edges)
    report={'intermediate_positions':len(partial)}
    if name=='swipe':
        # A fixed patch of glass in the empty lower portion of both pages.
        values=line(round((b-(b-t)*.12)*s));stride=3*line_width
        spread=max(max(values[j::stride])-min(values[j::stride]) for j in range(stride//8,7*stride//8))
        assert spread<=5,('Glass flashed in a stationary patch',spread)
        report['stationary_glass_channel_range']=spread
    cropw=round((r-l)*s/2)*2;croph=round((b-t)*s/2)*2
    subprocess.run(['ffmpeg','-y','-loglevel','error','-i',str(raw),'-vf',f'crop={cropw}:{croph}:{x}:{round(t*s)}',
                    '-fps_mode','vfr','-enc_time_base','1:90000','-an','-c:v','libx264','-pix_fmt','yuv420p','-movflags','+faststart',str(out/(name+'.mp4'))],check=True)
    return report


def main():
    out=d.B/'widget-drag';out.mkdir(exist_ok=True);before=w.state();prefs=d.preferences()
    assert not d.sh('pm','list','packages',w.PACKAGE).strip()
    with tempfile.TemporaryDirectory() as folder:
        apk=w.fixture(Path(folder));subprocess.run(d.A+['install','--no-incremental',str(apk)],check=True)
        try:
            w.open_panel();w.add_page();wid=w.add_widget();node=d.wait_widget_panel();identity=node.get('node-hash')
            viewport=d.bounds(d.wait_node('插件页面'));l,t,r,b=d.bounds(node)
            w.page_menu();button=d.wait_node('添加页面')
            report={'add':record(out,'add',viewport,lambda:d.tap(button))}
            assert d.wait_widget_panel().get('node-hash')==identity
            original_blur=blur()
            def swipe():
                y=(t+b)//2;start=l+(r-l)*.1
                w.event('DOWN',start,y)
                for f in (.17,.43,.68,.78,.62,.78):
                    w.event('MOVE',start+(r-l)*f,y);time.sleep(.12)
                w.event('UP',start+(r-l)*.78,y)
            report['swipe']=record(out,'swipe',viewport,swipe)
            assert w.state()['page']==before['pages']
            assert blur()==original_blur and d.wait_widget_panel().get('node-hash')==identity
            w.go(before['pages']+1);time.sleep(.8)
            w.page_menu();d.click_item('移除当前页面',True);button=d.wait_node('移除')
            report['remove']=record(out,'remove',viewport,lambda:d.tap(button))
            assert w.state()['page']==before['pages'] and any(o['id']==wid for o in w.data())
            assert d.wait_widget_panel().get('node-hash')==identity
            report['swipe_blur']=original_blur;report['footer_retained']=True
            print('PASS',json.dumps(report),flush=True)
        finally:
            d.main_home();w.open_panel()
            while w.state()['pages']>before['pages']:
                w.go(w.state()['pages']-1)
                assert not any(o['id'] in {a['id'] for a in json.loads(before['layout_v3'])} and o['page']==w.state()['page'] for o in w.data())
                w.confirm_remove_page()
            w.go(before['page']);d.main_home()
            subprocess.run(d.A+['uninstall',w.PACKAGE],check=True)
            assert w.state()==before and d.preferences()==prefs

if __name__=='__main__':main()
