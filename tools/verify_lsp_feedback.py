"""Shared CHO feedback in passive SystemUI windows; local-only evidence and temporary profiles."""
import sys,time,json,subprocess,shlex,re
from pathlib import Path
import verify_lsp_configured as c

c.OUT=c.ROOT/'verification/lsp-feedback'
OUT=c.OUT;d=c.d;request=c.request;fixture=c.fixture
results=[]

def record(name,**data):
    results[:]=[r for r in results if r['case']!=name];results.append({'case':name,**data});c.save('results.json',results);print('PASS',name,flush=True)

def save_frame(name):
    (OUT/name).write_bytes(subprocess.check_output(d.A+['exec-out','screencap','-p']))

def hold(side='right',distance=200,kind='hold'):
    path=d.sh('pm','path',c.PACKAGE).strip().removeprefix('package:');w,h=d.current_display_size();x=10 if side=='left' else w-10
    command='CLASSPATH='+shlex.quote(path)+' app_process /system/bin dev.edgeflow.lspfixture.Events '+shlex.join([kind,str(x),str(h//2),str(distance if side=='left' else -distance)])
    process=subprocess.Popen(d.A+['shell',shlex.join(['su','-c',command])],stdout=subprocess.PIPE,stderr=subprocess.PIPE,text=True)
    if kind=='hold':assert process.stdout.readline().strip()=='HOLD'
    return process

def start(profile):
    value=request('configured',profile=profile)
    assert value.get('active') and value.get('feedback_attached') and not value.get('feedback_error'),value
    time.sleep(.2);return value

def stop():
    value=request('stop');assert not value.get('active') and not value.get('feedback_attached') and not value.get('feedback_error'),value
    assert 'CHO LSP feedback' not in d.sh('dumpsys','window','windows')
    return value

def smoke():
    fixture();start(c.profile(handle_opacity=65,handle_color=-38400,custom_vibration=True,vibration_duration_ms=0))
    try:
        save_frame('host-idle.png');w,h=d.current_display_size();d.sh('input','tap',str(w-10),str(h//2));time.sleep(.15)
        value=request('status');assert value['claims']==0 and value['haptic_requests']==0,value
        driver=hold();value=request('status');assert value['wave_visible'] and value['wave_depth']>0 and value['claims']==1,value
        save_frame('host-hold.png');assert driver.wait(timeout=6)==0
        foreground=d.resumed();final=stop()
        assert final['actions']==1 and final['haptic_requests']==0 and c.PACKAGE+'/.ProbeActivity' in foreground,(final,foreground)
        record('host-feedback-smoke',held=value,final=final)
    finally:request('stop')

def overlay_rows():
    text=d.sh('dumpsys','window','windows');rows=[]
    for block in re.split(r'(?m)(?=  Window #\d+ Window\{)',text):
        if not block.startswith('  Window #') or 'CHO LSP feedback' not in block.splitlines()[0]:continue
        visible=re.search(r'mViewVisibility=(0x[0-9a-f]+)',block)
        rows.append({'title':block.splitlines()[0].strip(),'visible':visible is not None and visible[1]=='0x0','passive':'NOT_FOCUSABLE NOT_TOUCHABLE' in block,'trusted':'TRUSTED_OVERLAY' in block})
    return rows

def wait_visible(visible):
    until=time.monotonic()+4;rows=[]
    while time.monotonic()<until:
        rows=overlay_rows()
        if len(rows)==2 and all(r['visible']==visible and r['passive'] and r['trusted'] for r in rows):return rows
        time.sleep(.15)
    raise AssertionError(rows)

def input_matrix():
    for side in ['left','right']:
        for kind in ['tap','vertical','retreat','multi-after','cancel']:
            fixture();start(c.profile(handle=side,handle_opacity=70,custom_vibration=True,vibration_duration_ms=0))
            marker='FEEDBACK_'+str(time.time_ns());d.sh('log','-t','CHOLspFixture',marker);w,h=d.current_display_size();x=10 if side=='left' else w-10
            try:
                if kind=='tap':d.sh('input','tap',str(x),str(h//2))
                elif kind=='vertical':d.sh('input','swipe',str(x),str(h//2),str(x),str(h//2-230),'250')
                else:assert hold(side=side,kind=kind).wait(timeout=5)==0
                time.sleep(.2);value=request('status');events=d.sh('logcat','-d','-v','brief','-s','CHOLspFixture:I').split(marker)[-1]
                assert value['actions']==0 and value['haptic_requests']==0 and not value['wave_visible'],value
                if kind=='tap':assert 'CLICK '+side in events and 'ACTION_CANCEL' not in events,events
                if kind=='vertical':assert value['claims']==0 and 'ACTION_CANCEL' not in events,events
                record(side+'-'+kind,final=stop(),events=events)
            finally:request('stop')

def scenes():
    fixture();start(c.profile(hide_home=True,handle_opacity=70,custom_vibration=True,vibration_duration_ms=0));wait_visible(True)
    try:
        d.sh('input','keyevent','3');hidden=wait_visible(False);fixture();visible=wait_visible(True)
        record('home-hides-idle-indicators',hidden=hidden,restored=visible)
    finally:stop()
    fixture();start(c.profile(hide_keyboard=True,handle_opacity=70,custom_vibration=True,vibration_duration_ms=0))
    try:
        d.click('pilot text input');hidden=wait_visible(False);d.sh('input','keyevent','4');visible=wait_visible(True)
        record('ime-hides-idle-indicators',hidden=hidden,restored=visible)
    finally:stop()
    fixture();start(c.profile(hide_notifications=True,handle_opacity=70,custom_vibration=True,vibration_duration_ms=0))
    try:
        d.sh('cmd','statusbar','expand-notifications');hidden=wait_visible(False);d.sh('cmd','statusbar','collapse');visible=wait_visible(True)
        record('shade-hides-idle-indicators',hidden=hidden,restored=visible)
    finally:d.sh('cmd','statusbar','collapse');stop()
    fixture();start(c.profile(disabled_apps=['com.android.settings'],handle_opacity=70,custom_vibration=True,vibration_duration_ms=0))
    try:
        d.sh('am','start','-W','-a','android.settings.SETTINGS');hidden=wait_visible(False);fixture();visible=wait_visible(True)
        record('excluded-task-hides-idle-indicators',hidden=hidden,restored=visible)
    finally:stop()

def vibration_rows():
    return {l.strip() for l in d.sh('dumpsys','vibrator_manager').splitlines() if 'com.android.systemui' in l and 'uid=' in l and '| original:' in l}

def system_haptic(enabled):
    setting=d.sh('settings','get','system','haptic_feedback_enabled').strip()
    try:
        d.sh('settings','put','system','haptic_feedback_enabled',enabled);fixture();start(c.profile(custom_vibration=False,quick_vibration=False,vibration=True));before=vibration_rows()
        c.swipe();final=stop();time.sleep(.25);after=vibration_rows()-before
        assert final['haptic_requests']==1 and final['last_haptic']=='system',final
        # ColorOS can report the View call as handled even when vibration is disabled.
        # The vibrator service history is the delivery check, not that boolean.
        if enabled=='0':assert not any('|             finished |' in line for line in after),(final,after)
        else:assert final['haptic_accepted']==1 and after,(final,after)
        record('system-haptics-'+enabled,final=final,vibration_history=sorted(after))
    finally:
        request('stop')
        if setting=='null':d.sh('settings','delete','system','haptic_feedback_enabled')
        else:d.sh('settings','put','system','haptic_feedback_enabled',setting)

def haptics():
    setting=d.sh('settings','get','system','haptic_feedback_enabled').strip();c.save('haptic-setting-before.json',{'haptic_feedback_enabled':setting})
    try:
        cases=[('quick-custom',True,8,False,1),('release-custom',False,8,False,1),('long-custom',True,8,True,2),('custom-zero',True,0,False,0),('legacy-muted',True,8,False,0)]
        for name,quick,duration,longer,expected in cases:
            fixture();profile=c.profile(quick_vibration=quick,custom_vibration=True,vibration_duration_ms=duration,vibration=name!='legacy-muted',right_long_enabled=longer,action_right_straight_long='none')
            start(profile);before=vibration_rows()
            try:
                driver=hold(distance=390 if longer else 200);mid=request('status')
                if not quick:assert mid['haptic_requests']==0,mid
                assert driver.wait(timeout=6)==0;time.sleep(.2);final=stop();after=vibration_rows()-before
                assert final['haptic_requests']==expected and final['haptic_accepted']==expected,final
                if expected:assert after,(final,'No matching vibration service history')
                record(name,held=mid,final=final,vibration_history=sorted(after))
            finally:request('stop')
        for enabled in ['1','0']:system_haptic(enabled)
    finally:
        if setting=='null':d.sh('settings','delete','system','haptic_feedback_enabled')
        else:d.sh('settings','put','system','haptic_feedback_enabled',setting)
        assert d.sh('settings','get','system','haptic_feedback_enabled').strip()==setting

def capture_curve(name):
    remote='/data/local/tmp/cho-lsp-'+name+'.mp4';path=OUT/(name+'.mp4')
    recorder=subprocess.Popen(d.A+['shell','screenrecord','--size','632x1390','--bit-rate','8000000','--time-limit','6',remote],stdout=subprocess.DEVNULL,stderr=subprocess.PIPE)
    driver=None
    try:
        time.sleep(.5);driver=hold();time.sleep(.6);save_frame(name+'.png')
        assert driver.wait(timeout=6)==0;driver=None
        assert recorder.wait(timeout=8)==0
        subprocess.run(d.A+['pull',remote,str(path)],check=True,stdout=subprocess.DEVNULL)
    finally:
        if driver is not None:driver.wait(timeout=6)
        if recorder.poll() is None:recorder.wait(timeout=8)
        d.sh('rm','-f',remote)
    return path

def curve_motion(path):
    raw=json.loads(subprocess.check_output(['ffprobe','-v','error','-select_streams','v:0','-show_entries','frame=best_effort_timestamp_time','-of','json',str(path)]))
    times=[float(f['best_effort_timestamp_time']) for f in raw['frames']]
    pixels=subprocess.check_output(['ffmpeg','-v','error','-i',str(path),'-vf','crop=84:160:548:500','-pix_fmt','gray','-fps_mode','passthrough','-enc_time_base','demux','-f','rawvideo','-'])
    size=84*160;assert len(pixels)==len(times)*size
    depths=[]
    for i in range(len(times)):
        frame=pixels[i*size:(i+1)*size];columns=[x for x in range(84) if sum(frame[y*84+x]<40 for y in range(160))>=4]
        depths.append(84-min(columns) if columns else 0)
    maximum=max(depths);assert maximum>=60,(path,maximum)
    plateau=max(i for i,value in enumerate(depths) if value>=maximum-1)
    end=next(i for i in range(plateau+1,len(depths)) if depths[i]==0)
    moving=[i for i in range(plateau+1,end) if 0<depths[i]<maximum-1]
    assert moving,(path,depths)
    result={'intermediate_frames':len(moving),'distinct_depths':len({depths[i] for i in moving}),
        'moving_to_clear_ms':round((times[end]-times[moving[0]])*1000),
        'frames':[(round(times[i],4),depths[i]) for i in range(plateau,end+1)]}
    assert result['distinct_depths']>=2 and result['moving_to_clear_ms']<200,result
    return result

def wait_main_surfaces(present):
    until=time.monotonic()+6
    while time.monotonic()<until:
        found=any('Window #' in l and '手势绘制' in l for l in d.sh('dumpsys','window','windows').splitlines())
        if found==present:return
        time.sleep(.2)
    raise AssertionError('Original CHO surface state did not settle')

def parity():
    from PIL import Image,ImageChops
    original=d.sh('settings','get','secure','enabled_accessibility_services').strip();assert 'dev.edgeflow/' not in original
    request('stop');original_video=None
    try:
        subprocess.run(d.A+['install','-r','--no-incremental',str(OUT/'original-cho.apk')],check=True)
        fixture();d.sh('settings','put','secure','enabled_accessibility_services',original+':dev.edgeflow/dev.edgeflow.GestureService');wait_main_surfaces(True)
        original_video=capture_curve('original-wave')
    finally:
        d.sh('settings','put','secure','enabled_accessibility_services',original);wait_main_surfaces(False)
        subprocess.run(d.A+['install','-r','--no-incremental',str(c.ROOT/'app/build/outputs/apk/release/app-release.apk')],check=True)
    fixture();start(c.profile(handle_opacity=0,custom_vibration=True,vibration_duration_ms=0))
    try:new_video=capture_curve('lsp-wave');final=stop();assert final['wave_releases']==1 and final['actions']==1,final
    finally:request('stop')
    old=Image.open(OUT/'original-wave.png').convert('RGB');new=Image.open(OUT/'lsp-wave.png').convert('RGB');w,h=old.size;roi=(w-200,h//2-700,w,h//2+200)
    difference=ImageChops.difference(old.crop(roi),new.crop(roi)).tobytes();count=sum(any(difference[i:i+3]) for i in range(0,len(difference),3))
    record('original-wave-pixel-comparison',region=roi,different_pixels=count,total_pixels=200*900,exact=count==0)
    assert count==0,('Wave differs from original',count)
    before=curve_motion(original_video);after=curve_motion(new_video)
    assert abs(before['moving_to_clear_ms']-after['moving_to_clear_ms'])<=55,(before,after)
    record('original-wave-exit-comparison',original=before,lsp=after)
    assert c.status()==json.loads((OUT/'before.json').read_text(encoding='utf8'))

def geometry():
    from PIL import Image
    fixture();profile=c.profile(handle_opacity=50,left_enabled=True,left_color=-65536,right_color=-16776961,custom_vibration=True,vibration_duration_ms=0)
    start(profile)
    try:
        rows=wait_visible(True);save_frame('handle-colors.png');frame=Image.open(OUT/'handle-colors.png').convert('RGB');w,h=frame.size
        samples={'left':frame.getpixel((10,800)),'right':frame.getpixel((w-10,800)),'background':frame.getpixel((w//2,800))}
        for side,color in [('left',(255,0,0)),('right',(0,0,255))]:
            expected=tuple(round((c*128+b*127)/255) for c,b in zip(color,samples['background']))
            assert all(abs(a-b)<=1 for a,b in zip(samples[side],expected)),(samples,expected)
        changed=json.loads(json.dumps(profile));changed['values']['handle_opacity']=0
        request('update',profile=changed);time.sleep(.25);after=wait_visible(True)
        assert [r['title'] for r in rows]==[r['title'] for r in after],(rows,after)
        save_frame('handle-colors-hidden.png');hidden=Image.open(OUT/'handle-colors-hidden.png').convert('RGB')
        assert hidden.getpixel((10,800))==samples['background'] and hidden.getpixel((w-10,800))==samples['background']
        record('independent-colors-opacity-in-place',samples=samples,windows=rows)
    finally:stop()
    rotation=d.sh('wm','user-rotation').strip().split()
    try:
        d.sh('wm','user-rotation','lock','1');time.sleep(.5);fixture();w,h=d.current_display_size();assert w>h
        start(c.profile(handle_opacity=60,custom_vibration=True,vibration_duration_ms=0,right_landscape_handle_width_dp=20))
        driver=hold();time.sleep(.3);value=request('status');assert value['wave_visible'] and value['claims']==1,value
        save_frame('landscape-wave.png');assert driver.wait(timeout=6)==0;foreground=d.resumed();value=stop()
        assert value['actions']==1 and c.PACKAGE+'/.ProbeActivity' in foreground,(value,foreground)
        record('landscape-feedback-at-physical-edge',final=value)
    finally:request('stop');d.sh('wm','user-rotation',*rotation);time.sleep(.4)
    setting=d.sh('settings','get','global','animator_duration_scale').strip()
    try:
        d.sh('settings','put','global','animator_duration_scale','0');fixture();start(c.profile(custom_vibration=True,vibration_duration_ms=0))
        c.swipe();value=request('status');assert not value['wave_visible'] and value['wave_depth']==0 and value['wave_releases']==1,value
        record('disabled-animation-clears-wave',final=stop())
    finally:
        request('stop')
        if setting=='null':d.sh('settings','delete','global','animator_duration_scale')
        else:d.sh('settings','put','global','animator_duration_scale',setting)
    fixture();start(c.profile(custom_vibration=True,vibration_duration_ms=0));driver=hold()
    assert request('status')['wave_visible'];value=stop();assert driver.wait(timeout=6)==0
    assert value['actions']==0 and c.PACKAGE+'/.SecondActivity' in d.resumed(),value
    record('stop-during-contact-removes-surfaces',final=value)

def wait_host(predicate):
    until=time.monotonic()+8;last={}
    while time.monotonic()<until:
        last=request('status')
        if predicate(last):return last
        time.sleep(.2)
    raise AssertionError(last)

def enable_persistent():
    d.sh('am','start','-W','-n',c.PILOT+'/dev.edgeflow.lsp.PilotActivity');time.sleep(.4);d.click_item('开启常驻输入')
    until=time.monotonic()+8
    while time.monotonic()<until:
        text='\n'.join(n.get('text','') for n in d.ui().iter('node'))
        if 'persistent_requested: true' in text and 'active: true' in text:
            fixture();return wait_host(lambda b:b.get('active') and b.get('feedback_attached') and not b.get('feedback_error'))
        if 'Exception:' in text:raise AssertionError(text[-1600:])
        time.sleep(.2)
    raise AssertionError('Persistent input did not start')

def disable_persistent():
    # Use the real foreground control after deliberately killing its process.
    d.sh('am','start','-W','-n',c.PILOT+'/dev.edgeflow.lsp.PilotActivity');time.sleep(.4);d.click_item('关闭常驻输入');time.sleep(.3)
    fixture();return wait_host(lambda b:not b.get('active') and not b.get('persistent_requested'))

def finish_checks():
    fixture();start(c.profile(action='screen_magnifier',right_long_enabled=True,action_right_straight_long='none',quick_vibration=True,custom_vibration=True,vibration_duration_ms=8))
    try:
        driver=hold(distance=390);assert driver.wait(timeout=6)==0;value=stop()
        assert value['haptic_requests']==1 and value['noops']==1 and value['last_long'],value
        record('unsupported-short-does-not-pulse-before-long',final=value)
    finally:request('stop')
    fixture();start(c.profile(handle='left',custom_vibration=True,vibration_duration_ms=0))
    try:
        driver=hold(side='left');time.sleep(.4);save_frame('left-wave.png');assert driver.wait(timeout=6)==0
        value=stop();assert value['actions']==1 and value['last_handle']=='left',value
        record('left-arrow-and-return',final=value)
    finally:request('stop')
    original=d.preferences()['quick_action_long'];initial=enable_persistent()
    try:
        d.main_home();d.click_item('手势设置');d.find_item('长距离滑动持续时间');time.sleep(.3)
        ids=lambda rows:sorted(re.search(r'Window\{([^ ]+)',r['title'])[1] for r in rows)
        before=ids(wait_visible(True))
        node=next(n for n in d.ui().iter('node') if n.get('content-desc')=='快捷操作' and n.get('checked')==str(original).lower() and d.visible(n))
        d.tap(node);time.sleep(.5);assert d.preferences()['quick_action_long']!=original
        after=ids(wait_visible(True));assert before==after,(before,after)
        node=next(n for n in d.ui().iter('node') if n.get('content-desc')=='快捷操作' and n.get('checked')==str(not original).lower() and d.visible(n))
        # The short-action switch may have the same state; target the lower (long) row.
        matches=[n for n in d.ui().iter('node') if n.get('content-desc')=='快捷操作' and d.visible(n)]
        d.tap(max(matches,key=lambda n:d.bounds(n)[1]));time.sleep(.5);assert d.preferences()['quick_action_long']==original
        fixture();value=wait_host(lambda b:b.get('active') and b.get('feedback_attached'))
        assert value['session_starts']==initial['session_starts'],(initial,value)
        c.swipe();value=request('status');assert value['actions']==1 and c.PACKAGE+'/.ProbeActivity' in d.resumed(),value
        record('persistent-feedback-update-keeps-windows',before=before,after=after,final=value)
        # Both application processes can end while SystemUI owns the feedback surfaces.
        for package in ['dev.edgeflow',c.PILOT]:
            pid=d.sh('pidof',package).strip();assert pid.isdecimal();d.sh('su','-c','kill -9 '+pid)
        fixture();driver=hold();time.sleep(.3);value=request('status');assert value['wave_visible'] and value['feedback_attached'],value
        save_frame('apps-killed-wave.png');assert driver.wait(timeout=6)==0
        record('feedback-after-both-apps-killed',final=request('status'))
    finally:
        try:
            if d.preferences()['quick_action_long']!=original:
                d.main_home();d.click_item('手势设置');d.find_item('长距离滑动持续时间')
                matches=[n for n in d.ui().iter('node') if n.get('content-desc')=='快捷操作' and d.visible(n)]
                d.tap(max(matches,key=lambda n:d.bounds(n)[1]));time.sleep(.4)
                assert d.preferences()['quick_action_long']==original
        finally:
            disable_persistent()
    assert c.status()==json.loads((OUT/'before.json').read_text(encoding='utf8'))

def controller_recovery():
    import xml.etree.ElementTree as E
    fixture();actual=json.loads(request('snapshot')['profile']);enable_persistent()
    def identities():return sorted(re.search(r'Window\{([^ ]+)',r['title'])[1] for r in overlay_rows())
    try:
        before=identities();pid=d.sh('pidof',c.PILOT).strip();assert pid.isdecimal();d.sh('su','-c','kill -9 '+pid)
        until=time.monotonic()+8;new=''
        while time.monotonic()<until:
            new=d.sh('sh','-c','pidof '+c.PILOT+' || true').strip()
            if new and new!=pid:break
            time.sleep(.2)
        assert new and new!=pid
        value=request('status');assert value['active'] and value['feedback_attached'] and identities()==before,value
        record('controller-reconnect-keeps-feedback',host=value,controller_restarted=True)
        # Send the newest authenticated snapshot immediately after killing the writer.
        # This avoids relying on a background ContentProvider becoming reachable first.
        modified=json.loads(json.dumps(actual));modified['values']['enabled']=False
        pid=d.sh('pidof',c.PILOT).strip();d.sh('su','-c','kill -9 '+pid);request('update',profile=modified)
        value=wait_host(lambda b:not b.get('active') and b.get('persistent_state')=='MASTER_OFF')
        raw=d.sh('su','-c','cat /data/user_de/0/'+c.PILOT+'/shared_prefs/persistent_controller.xml')
        cache={n.get('name'):n.text for n in E.fromstring(raw) if n.tag=='string'}
        assert json.loads(cache['profile'])['values']['enabled'] is False
        record('controller-death-pending-update-persists',host=value,cached_master_enabled=False)
    finally:
        request('update',profile=actual);wait_host(lambda b:b.get('active') and b.get('feedback_attached'));disable_persistent()
    assert c.status()==json.loads((OUT/'before.json').read_text(encoding='utf8'))

if __name__=='__main__':
    if (OUT/'results.json').exists():results.extend(json.loads((OUT/'results.json').read_text(encoding='utf8')))
    if sys.argv[1]=='prepare':c.prepare()
    elif sys.argv[1]=='smoke':smoke()
    elif sys.argv[1]=='matrix':input_matrix();scenes();haptics()
    elif sys.argv[1]=='system-off':system_haptic('0')
    elif sys.argv[1]=='parity':parity()
    elif sys.argv[1]=='geometry':geometry()
    elif sys.argv[1]=='finish':finish_checks()
    elif sys.argv[1]=='controller':controller_recovery()
    else:raise SystemExit('prepare | smoke | matrix | system-off | parity | geometry | finish | controller')
