"""Real pad input and rotation checks against a disposable gesture-detecting Activity."""
import json,time,subprocess,re,shlex
import verify_lsp_configured as c
import verify_lsp_windows as w
c.OUT=c.ROOT/'verification/lsp-touchpad';w.OUT=c.OUT
results=[]
hardware_plan=None
PAD='触控板：滑动移动光标，轻点点击，双指滑动滚动'

def record(name,**data):
    saved=c.OUT/'touch-results.json'
    if saved.exists():results[:]=json.loads(saved.read_text(encoding='utf8'))
    results[:]=[row for row in results if row['case']!=name]
    result={'case':name,**data};results.append(result);c.save('touch-results.json',results)
    cases=c.OUT/'touch-cases';cases.mkdir(exist_ok=True);(cases/(name+'.json')).write_text(json.dumps(result,ensure_ascii=False,indent=2),encoding='utf8')
    print('PASS',name,flush=True)
def own(label):
    return next(n for n in c.d.ui().iter('node') if n.get('package')=='dev.edgeflow' and c.d.visible(n) and label in [n.get('text'),n.get('content-desc')])
def tap(label,delay=.3):c.d.tap(own(label));time.sleep(delay)
def state():return json.loads(c.d.sh('su','-c','cat /data/user/0/'+c.PACKAGE+'/files/touch_state.json'))
def fixture():
    c.d.sh('am','start','-S','-W','-n',c.PACKAGE+'/.ProbeActivity');c.d.sh('am','start','-W','-n',c.PACKAGE+'/.TouchActivity')
def start():
    fixture();c.request('stop');c.start(c.profile('virtual_touchpad'));c.swipe();w.await_tools(True);time.sleep(.65)
def stop():c.request('stop');w.await_tools(False);w.count(0)
def delta(a,b):return {k:b[k]-a[k] for k in ['taps','doubles','longs','moves','cancels','downs','ups','scroll']}
def injected(a,b):return [e for e in b['events'][len(a['events']):] if e['tool']==0]
def hardware_tap():
    global hardware_plan
    # A second `input tap` uses the same virtual device as the pad's injection.
    # Use the actual touchscreen input path for physical-interruption coverage.
    if hardware_plan is None:
        blocks=re.split(r'(?=add device \d+:)',c.d.sh('su','-c','getevent -lp'))
        matches=[b for b in blocks if 'INPUT_PROP_DIRECT' in b and 'ABS_MT_SLOT' in b and 'ABS_MT_POSITION_X' in b]
        assert len(matches)==1,'Cannot identify one touchscreen safely'
        block=matches[0];device=re.search(r'add device \d+: (/dev/input/event\d+)',block)[1]
        xmax=int(re.search(r'ABS_MT_POSITION_X[^\n]*max (\d+)',block)[1]);ymax=int(re.search(r'ABS_MT_POSITION_Y[^\n]*max (\d+)',block)[1])
        width,height=c.d.current_display_size();assert width<height,'Hardware interruption is a portrait test'
        hardware_plan=(device,round(200*xmax/width),round(400*ymax/height))
    device,x,y=hardware_plan
    assert c.PACKAGE+'/.TouchActivity' in c.d.resumed(),'Hardware test target changed'
    down=[(3,47,0),(3,57,34568),(3,53,x),(3,54,y),(3,0,x),(3,1,y),(3,48,100),(3,49,100),(1,330,1),(1,325,1),(0,0,0)]
    up=[(3,47,0),(3,57,-1),(1,330,0),(1,325,0),(0,0,0)]
    def send(events):c.d.sh('su','-c','\n'.join(f'sendevent {device} {ty} {code} {value}' for ty,code,value in events))
    # On this phone these test packets only reached InputReader with a read-side
    # evdev client open. Close that probe afterward; no configuration is changed.
    command='echo $$; exec getevent -lt '+shlex.quote(device)
    reader=subprocess.Popen(c.d.A+['shell',shlex.join(['su','-c',command])],stdout=subprocess.PIPE,stderr=subprocess.PIPE,text=True,encoding='utf8')
    pid=reader.stdout.readline().strip();assert pid.isdecimal() and int(pid)>100
    try:
        time.sleep(.05);send(down);time.sleep(.06)
    finally:
        send(up);c.d.sh('su','-c','kill -TERM '+pid);reader.communicate(timeout=3)

def basic():
    start();a=state();tap('双击',.4);b=state();d=delta(a,b);events=injected(a,b)
    assert d['doubles']==1 and d['downs']==2 and d['ups']==2 and [e['action'] for e in events]==[0,1,0,1],d
    record('double-tap',delta=d,events=events)
    time.sleep(.4);a=state();tap('长按',.8);b=state();d=delta(a,b);events=injected(a,b)
    assert d['longs']==1 and d['downs']==1 and d['ups']==1 and events[-1]['time']-events[0]['time']>=600,d
    record('long-press',delta=d,events=events);stop()
    start();tap('滚动：关');a=state();l,t,r,bounds=c.d.bounds(own(PAD));x=(l+r)//2;y=(t+bounds)//2
    c.d.sh('input','swipe',str(x),str(y+100),str(x),str(y-100),'220');time.sleep(.5);b=state();d=delta(a,b)
    assert d['moves']>=10 and d['scroll']>100 and d['downs']==1 and d['ups']==1,d
    record('scroll',delta=d,events=injected(a,b));tap('滚动：开')
    c.d.sh('input','swipe',str(x),str(y),str(x+120),str(y),'200');time.sleep(.15);a=state();tap('单击');b=state();events=injected(a,b)
    width,height=c.d.current_display_size();assert events[0]['x']>width*.5+100,events
    record('relative-cursor-move',events=events);tap('光标居中');a=state();tap('单击');b=state();events=injected(a,b)
    assert abs(events[0]['x']-width*.5)<=2 and abs(events[0]['y']-height*.35)<=2,events
    record('cursor-center',events=events)
    before=state();c.d.sh('input','tap','200','400');time.sleep(.2);after=state();own('单击');assert after['taps']==before['taps']+1
    record('outside-touch-passes-through-pad-stays');tap('返回');assert c.PACKAGE+'/.ProbeActivity' in c.d.resumed();own('单击');record('pad-back-keeps-pad');stop()
    assert c.status()==json.loads((c.OUT/'test-baseline.json').read_text(encoding='utf8'))

def interruptions():
    start();a=state();tap('长按',.16);c.request('stop');time.sleep(.15);b=state();events=injected(a,b)
    assert events and events[0]['action']==0 and events[-1]['action']==3 and b['longs']==a['longs'],events
    w.await_tools(False);w.count(0);record('stop-balances-pending-touch',events=events)
    fixture();c.request('stop');a=state();hardware_tap();time.sleep(.15);b=state();assert b['taps']==a['taps']+1 and abs(b['events'][-1]['x']-200)<5 and abs(b['events'][-1]['y']-400)<5
    start();a=state();tap('长按',.14);hardware_tap();time.sleep(.35);b=state();d=delta(a,b);events=injected(a,b)
    assert events and events[0]['action']==0 and events[-1]['action']==3 and d['longs']==0,d
    assert d['taps']==1,('New physical tap was lost',d,b['events'][len(a['events']):])
    record('new-touch-cancels-injection-and-still-clicks',delta=d,events=b['events'][len(a['events']):]);stop()
    start();a=state();tap('长按',.1);c.d.sh('am','start','-W','-a','android.settings.SETTINGS');time.sleep(.4);b=state();events=injected(a,b)
    assert events and events[-1]['action']==3 and b['longs']==a['longs'],events
    record('foreground-change-cancels-touch',events=events);fixture();stop()

def rotation():
    import verify_lsp_feedback as f
    c.OUT=c.ROOT/'verification/lsp-touchpad';w.OUT=c.OUT
    original={k:c.d.sh('settings','get','system',k).strip() for k in ['accelerometer_rotation','user_rotation']}
    c.save('rotation-before.json',original);fixture();c.request('stop');f.enable_persistent()
    try:
        c.request('update',profile=c.profile('virtual_touchpad',left_enabled=True,action_left_straight_short='navigation_panel'))
        end=time.monotonic()+8
        while time.monotonic()<end:
            r=c.request('status')
            if r.get('active') and not r.get('unsupported_actions'):break
            time.sleep(.15)
        assert r.get('active') and r.get('persistent_requested'),r
        fixture();c.swipe();c.swipe('left');own('单击');own('关闭虚拟导航键');w.count(1)
        pid=c.d.sh('pidof','dev.edgeflow').strip()
        c.d.sh('settings','put','system','accelerometer_rotation','0')
        for value in ['1','0']:
            c.d.sh('settings','put','system','user_rotation',value)
            end=time.monotonic()+8
            while time.monotonic()<end:
                width,height=c.d.current_display_size();r=c.request('status')
                if (width>height)==(value=='1') and r.get('active') and r.get('width')==width:break
                time.sleep(.2)
            assert r.get('active') and r.get('width')==width,r
            own('单击');own('关闭虚拟导航键');assert c.d.sh('pidof','dev.edgeflow').strip()==pid
            assert len(w.tool_windows())==3;w.count(1)
            a=state();tap('光标居中');tap('单击');b=state();events=injected(a,b)
            assert b['taps']==a['taps']+1 and abs(events[0]['x']-width*.5)<2 and abs(events[0]['y']-height*.35)<2,events
            (c.OUT/('rotation-'+value+'.png')).write_bytes(subprocess.check_output(c.d.A+['exec-out','screencap','-p']))
            record('persistent-rotation-'+value,events=events,status=r)
    finally:
        for k,value in original.items():
            c.d.sh('settings','delete','system',k) if value=='null' else c.d.sh('settings','put','system',k,value)
        f.disable_persistent();w.await_tools(False);w.count(0)
        c.d.sh('am','start','-W','-n',c.PILOT+'/dev.edgeflow.lsp.PilotActivity');time.sleep(.3);fixture()
        reset=c.request('persistent_reset');assert not reset.get('provider_error'),reset;c.save('controller-reset.json',reset)
    assert c.status()==json.loads((c.OUT/'test-baseline.json').read_text(encoding='utf8'))

def death():
    start();pid=c.d.sh('pidof','dev.edgeflow').strip();assert pid.isdecimal();a=state();tap('长按',.12);c.d.sh('su','-c','kill -9 '+pid);time.sleep(.35);b=state();events=injected(a,b)
    assert events and events[-1]['action']==3 and b['longs']==a['longs'],events
    w.await_tools(False);w.count(0);record('host-death-cancels-active-pointer',events=events);stop()
    start();pid=c.d.sh('pidof','com.android.systemui').strip();server=c.d.sh('pidof','system_server').strip();a=state();tap('长按',.12);c.d.sh('su','-c','kill -TERM '+pid);time.sleep(.35);b=state();events=injected(a,b)
    assert events and events[-1]['action']==3 and b['longs']==a['longs'],events
    assert c.d.sh('pidof','system_server').strip()==server
    w.await_tools(False);record('systemui-death-cancels-active-pointer',events=events)
    # This final case may lock the phone. Remaining cleanup is read-only or package restoration.

if __name__=='__main__':
    import sys
    if (c.OUT/'touch-results.json').exists():results.extend(json.loads((c.OUT/'touch-results.json').read_text(encoding='utf8')))
    {'basic':basic,'interruptions':interruptions,'rotation':rotation,'death':death}[sys.argv[1] if len(sys.argv)>1 else 'basic']()
