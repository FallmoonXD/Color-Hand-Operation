"""Configured SystemUI pilot checks. Evidence stays under ignored verification/.

Use prepare before enabling the module in LSPosed; run after the owner's unlock.
Synthetic profiles travel through the signed fixture and never overwrite CHO.
The live-master check toggles CHO through its UI and restores the original value.
"""
import copy,hashlib,json,re,shlex,sqlite3,subprocess,sys,time,os,tempfile
from pathlib import Path
from verify_lsp_input import ROOT,d,PACKAGE,fixture,status
from lsp_control import request

OUT=ROOT/'verification/lsp-configured'
PILOT='dev.edgeflow.lsp.pilot'
results=[]
os.environ['CHO_TEST_READ_RELEASE_PREFS']='1'

def save(name,value):
    (OUT/name).write_text(json.dumps(value,ensure_ascii=False,indent=2),encoding='utf8')

def module_state(label):
    directory=OUT/label;directory.mkdir(parents=True,exist_ok=True)
    for suffix in ['','-wal','-shm']:
        name='modules_config.db'+suffix
        command='cat /data/adb/lspd/config/'+name
        raw=subprocess.run(d.A+['exec-out','su','-c',command],capture_output=True,check=True).stdout
        (directory/name).write_bytes(raw)
    with sqlite3.connect(directory/'modules_config.db') as db:
        return {name:list(db.execute('select * from '+name)) for name in ['modules','modules_state','scope']}

def prepare():
    OUT.mkdir(parents=True,exist_ok=True)
    assert not (OUT/'before.json').exists(),'Existing baseline must not be overwritten'
    before=status();save('before.json',before);save('modules-before.json',module_state('db-before'))
    path=d.sh('pm','path','dev.edgeflow').strip().removeprefix('package:')
    assert path.startswith('/data/app/') and '\n' not in path,path
    subprocess.run(d.A+['pull',path,str(OUT/'original-cho.apk')],check=True)
    save('installed-before.json',{'sha256':hashlib.sha256((OUT/'original-cho.apk').read_bytes()).hexdigest()})
    for package in [PILOT,PACKAGE]:assert not d.sh('pm','list','packages',package).strip(),package+' already installed'
    for apk in [ROOT/'app/build/outputs/apk/release/app-release.apk',ROOT/'lsp/build/outputs/apk/debug/lsp-debug.apk',ROOT/'.checks/lsp-fixture/fixture.apk']:
        subprocess.run(d.A+['install','-r','--no-incremental',str(apk)],check=True)
    fixture();snapshot=request('snapshot');assert not snapshot.get('provider_error'),snapshot
    profile=json.loads(snapshot['profile']);save('source-profile.json',profile)
    prefs=before['prefs']['edgeflow.xml']
    assert profile['values']['enabled']==prefs.get('enabled',True)
    for key,value in profile['values'].items():assert value==prefs[key],key
    assert not profile['accessibility_input'],profile
    save('provider-read.json',{'fields':len(profile['values']),'all_match_original':True,'client_uid':snapshot['client_uid']})
    assert status()==before,'Preparation changed user state'
    print('PASS same-signature provider export equals original preferences',flush=True)

def record(name,**value):
    results.append({'case':name,**value});save('results.json',results);print('PASS',name,flush=True)

def profile(action='back',handle='right',**changes):
    value=copy.deepcopy(json.loads((OUT/'source-profile.json').read_text(encoding='utf8')))
    p=value['values'];value['accessibility_input']=False
    for key in list(p):
        if key.startswith('app_override/'):del p[key]
    p.update(enabled=True,hide_home=False,hide_notifications=False,hide_keyboard=False,hide_when_keyboard=False,hide_landscape=False,landscape_enabled=True,disabled_apps=[],excluded_packages='',trigger_dp=40,long_trigger_dp=100,long_delay_ms=500,quick_action_short=False,quick_action_long=True,long_immediate=False)
    for item in ['left','right','extra_left','extra_right','extra2_left','extra2_right']:
        p[item+'_enabled']=item==handle;p[item+'_long_enabled']=False
        p[item+'_handle_width_dp']=20;p[item+'_handle_start_fraction']=.2;p[item+'_handle_end_fraction']=.8
        for direction in ['up','straight','down']:
            p[f'action_{item}_{direction}_short']=action;p[f'action_{item}_{direction}_long']='none'
    p.update(changes);return value

def start(value=None):
    result=request('configured',profile=value)
    assert result.get('connected') and result.get('active') and result.get('configured') and not result.get('error'),result
    assert result['pid']==int(d.sh('pidof','com.android.systemui').strip()) and result['uid']!=result['client_uid'],result
    return result

def read_after():
    foreground=d.resumed()
    d.sh('am','start','-W','-n',PACKAGE+'/.SecondActivity')
    result=request('stop');assert not result.get('active') and not result.get('error'),result
    assert 'CHO LSP pilot' not in d.sh('dumpsys','input')
    return foreground,result

def swipe(side='right',dy=0,distance=200,duration=220):
    w,h=d.current_display_size();x=10 if side=='left' else w-10
    d.sh('input','swipe',str(x),str(h//2),str(x+(distance if side=='left' else -distance)),str(h//2+dy),str(duration));time.sleep(.15)

def run():
    for handle in ['left','right','extra_left','extra_right','extra2_left','extra2_right']:
        fixture();start(profile(handle=handle));swipe('left' if handle.endswith('left') else 'right');foreground,end=read_after()
        assert PACKAGE+'/.ProbeActivity' in foreground and end['actions']==1 and end['last_handle']==handle,(foreground,end)
        record('configured-'+handle,final=end)
    for name,changes,expected in [
        ('app-override-none',{'app_override/'+PACKAGE+'/action_right_straight_short':'none'},0),
        ('app-override-home',{'app_override/'+PACKAGE+'/action_right_straight_short':'home'},1),
        ('app-excluded',{'disabled_apps':[PACKAGE]},0),
        ('unsupported-tools',{'action_right_straight_short':'screen_magnifier'},0)]:
        fixture();start(profile(**changes));swipe();foreground,end=read_after();assert end['actions']==expected,end
        if name=='app-override-none':assert end['noops']==1 and PACKAGE+'/.SecondActivity' in foreground,end
        if name=='app-override-home':assert 'com.android.launcher/' in foreground and end['last_action']=='home',(foreground,end)
        if name in ['app-excluded','unsupported-tools']:assert end['claims']==0 and PACKAGE+'/.SecondActivity' in foreground,end
        record(name,final=end)
    for name,changes,duration,expected in [
        ('long-held',{'right_long_enabled':True,'action_right_straight_long':'home'},800,'home'),
        ('long-too-short',{'right_long_enabled':True,'action_right_straight_long':'home'},220,'back'),
        ('long-on-release',{'right_long_enabled':True,'action_right_straight_long':'home','quick_action_long':False},800,'home'),
        ('long-immediate',{'right_long_enabled':True,'action_right_straight_long':'home','long_immediate':True},220,'home')]:
        fixture();start(profile(**changes));swipe(distance=390,duration=duration);foreground,end=read_after()
        assert end['actions']==1 and end['last_action']==expected and end['last_long']==(expected=='home'),end
        record(name,final=end,foreground=foreground)
    fixture();first=start(profile());update=request('update',profile=profile(action='home'))
    assert update['expires_at']==first['expires_at'] and update['revision']!=first['revision'],(first,update)
    swipe();foreground,end=read_after();assert end['last_action']=='home' and 'com.android.launcher/' in foreground,end
    record('live-profile-replacement-no-extension',initial=first,updated=update,final=end)
    fixture();start(profile());request('stop');disabled=request('update',profile=profile())
    assert not disabled['active'];record('update-never-arms',final=disabled)
    fixture();bad=profile();bad['accessibility_input']=True;result=request('configured',profile=bad)
    assert not result.get('active') and result.get('error'),result
    record('duplicate-backend-snapshot-rejected',final=result)
    fixture();start(profile());pid=d.sh('pidof','dev.edgeflow').strip();assert pid.isdecimal()
    d.sh('su','-c','kill -9 '+pid);swipe();foreground,end=read_after()
    assert end['actions']==1 and PACKAGE+'/.ProbeActivity' in foreground,end
    record('cho-process-killed-config-retained',final=end)
    live_master()

def live_master():
    # Use the installed CHO switch, so the provider itself must publish the change.
    fixture();start(profile());d.sh('am','start','-W','-n','dev.edgeflow/.MainActivity');time.sleep(.5)
    original=d.preferences()['enabled'];assert original,'Live-master test expects the owner\'s enabled baseline'
    try:
        d.click('启用手势');assert not d.preferences()['enabled'];fixture();result=request('status')
        assert not result['active'] and result['reason']=='configuration-disabled',result
        record('cho-ui-master-disables-host',final=result)
    finally:
        d.sh('am','start','-W','-n','dev.edgeflow/.MainActivity');time.sleep(.4)
        if d.preferences()['enabled']!=original:d.click('启用手势')
    fixture();result=request('status');assert not result['active'];record('cho-ui-reenable-does-not-auto-arm',final=result)
    assert status()==json.loads((OUT/'before.json').read_text(encoding='utf8')),'User state changed'
    print('PASS',len(results),'configured host cases',flush=True)

def extras():
    fixture();start();swipe();foreground,end=read_after()
    assert end['actions']==1 and end['last_action']=='back' and PACKAGE+'/.ProbeActivity' in foreground,end
    record('unmodified-owner-profile',final=end)
    for direction,dy in [('up',-200),('down',200)]:
        fixture();start(profile());swipe(dy=dy);foreground,end=read_after()
        assert end['actions']==1 and end['last_direction']==direction,end
        record('diagonal-'+direction,final=end)
    for kind in ['tap','vertical']:
        fixture();start(profile());w,h=d.current_display_size()
        if kind=='tap':d.sh('input','tap',str(w-10),str(h//2))
        else:d.sh('input','swipe',str(w-10),str(h//2),str(w-10),str(h//2-200),'220')
        foreground,end=read_after();assert end['claims']==0 and end['actions']==0 and PACKAGE+'/.SecondActivity' in foreground,end
        record('configured-'+kind+'-pass-through',final=end)
    fixture();start(profile(hide_home=True));d.sh('input','keyevent','3');time.sleep(.4);swipe();foreground,end=read_after()
    assert end['claims']==0 and 'com.android.launcher/' in foreground,end
    record('hide-on-home',final=end)
    fixture();d.click('pilot text input');time.sleep(.4);first=start(profile(hide_keyboard=True))
    assert 'ime_visible' in first.get('scene',''),first
    swipe();foreground,end=read_after();assert end['claims']==0 and end['actions']==0,end
    d.sh('input','keyevent','4');record('hide-with-ime',final=end)
    notifications_hidden()
    actions_and_lifecycle()

def notifications_hidden():
    fixture();start(profile(hide_notifications=True));d.sh('cmd','statusbar','expand-notifications');time.sleep(.4)
    opened=request('status');assert any(k in opened.get('scene','') for k in ['notif_expanded','notif_visible']),opened
    swipe();d.sh('cmd','statusbar','collapse');foreground,end=read_after();assert end['claims']==0 and end['actions']==0,end
    record('hide-in-notifications',opened=opened,final=end)

def actions_and_lifecycle():
    for action in ['settings','app:com.android.settings/.Settings','notifications','quick_settings']:
        fixture();start(profile(action=action));swipe();time.sleep(.3)
        if action in ['notifications','quick_settings']:
            opened=request('status');assert any(k in opened.get('scene','') for k in (['notif_expanded','notif_visible'] if action=='notifications' else ['qs_visible'])),opened
            d.sh('cmd','statusbar','collapse')
        foreground,end=read_after();assert end['actions']==1 and end['last_action']==action,end
        if action.startswith(('settings','app:')):assert 'com.android.settings/' in foreground,foreground
        record('action-'+action,final=end,foreground=foreground)
    # Verify a new profile and a new task both cancel an already claimed contact.
    driver=d.sh('pm','path',PACKAGE).strip().removeprefix('package:')
    for kind in ['replace','focus']:
        fixture();start(profile());w,h=d.current_display_size()
        command='CLASSPATH='+shlex.quote(driver)+' app_process /system/bin dev.edgeflow.lspfixture.Events hold '+shlex.join([str(w-10),str(h//2),'-200'])
        child=subprocess.Popen(d.A+['shell',shlex.join(['su','-c',command])],stdout=subprocess.PIPE,stderr=subprocess.PIPE,text=True)
        assert child.stdout.readline().strip()=='HOLD'
        if kind=='replace':request('update',profile=profile(action='home'))
        else:d.sh('am','start','-W','-a','android.settings.SETTINGS')
        assert child.wait(timeout=8)==0
        foreground,end=read_after();assert end['actions']==0,end
        record('mid-contact-'+kind+'-cancels',final=end)
    # Media uses a real session and looping silence; no personal player is controlled.
    from verify_quick_media import fixture as player_fixture,PACKAGE as PLAYER,muted,set_volume
    assert not d.sh('pm','list','packages',PLAYER).strip()
    volume=d.vol();was_muted=muted()
    assert not was_muted,'Volume fixture expects an unmuted baseline; leave owner state unchanged otherwise'
    with tempfile.TemporaryDirectory() as folder:
        apk=player_fixture(Path(folder));subprocess.run(d.A+['install','--no-incremental',str(apk)],check=True)
        try:
            d.sh('am','start','-W','-n',PLAYER+'/.Player');time.sleep(.4)
            fixture();set_volume(4)
            for action,expected in [('volume_up',5),('volume_down',4),('volume_mute',0),('volume_mute',4)]:
                start(profile(action=action));swipe();foreground,end=read_after();assert end['actions']==1 and d.vol()==expected,(end,d.vol())
                record('action-'+action+'-'+str(expected),final=end)
                d.sh('cmd','statusbar','collapse');time.sleep(.2)
            for action in ['media_play_pause','media_play_pause','media_next','media_previous']:
                marker='LSP_MEDIA_'+str(time.time_ns());d.sh('log','-t','ChoMediaTest',marker)
                start(profile(action=action));swipe();foreground,end=read_after();events=d.sh('logcat','-d','-v','brief','-s','ChoMediaTest:I').split(marker)[-1]
                key={'media_play_pause':85,'media_next':87,'media_previous':88}[action]
                assert end['actions']==1 and f'KEY {key} 0' in events and f'KEY {key} 1' in events,(end,events)
                record('action-'+action,final=end,events=events)
        finally:
            request('stop');subprocess.run(d.A+['uninstall',PLAYER],check=True);set_volume(volume)
            assert d.vol()==volume and muted()==was_muted
    fixture();first=start(profile());time.sleep(30.2);result=request('status')
    assert not result['active'] and result['reason']=='expired' and 'CHO LSP pilot' not in d.sh('dumpsys','input'),result
    record('configured-expiry',final=result)
    assert status()==json.loads((OUT/'before.json').read_text(encoding='utf8'))
    print('PASS',len(results),'configured host cases',flush=True)

def final_checks():
    notifications_hidden()
    fixture();start();swipe();foreground,end=read_after()
    assert end['actions']==1 and end['last_action']=='back' and PACKAGE+'/.ProbeActivity' in foreground,end
    record('final-build-owner-profile',final=end)
    fixture();value=request('capture');assert value['active'] and not value['configured'],value
    w,h=d.current_display_size();d.sh('input','swipe',str(w-70),str(h//2),str(w-330),str(h//2),'350')
    time.sleep(.2);foreground,end=read_after();assert end['actions']==1 and PACKAGE+'/.ProbeActivity' in foreground,end
    record('fixed-pilot-regression',final=end)
    final_boundaries()

def final_boundaries():
    fixture();start(profile(hide_keyboard=True));d.click('pilot text input');time.sleep(.4)
    opened=request('status');assert 'ime_visible' in opened.get('scene',''),opened
    swipe();foreground,end=read_after()
    assert end['actions']==0 and end['claims']==0,end
    d.sh('input','keyevent','4');record('final-build-ime-exclusion',opened=opened,final=end)
    original=d.sh('settings','get','secure','enabled_accessibility_services').strip()
    assert 'dev.edgeflow/' not in original
    added=original+':dev.edgeflow/dev.edgeflow.GestureService'
    try:
        fixture();start(profile());d.sh('settings','put','secure','enabled_accessibility_services',added);time.sleep(.6)
        assert d.sh('settings','get','secure','enabled_accessibility_services').strip()==added
        w,h=d.current_display_size();d.sh('input','tap',str(w//2),str(h//2));time.sleep(.2)
        value=request('status');assert not value['active'] and value['reason']=='accessibility-input-active' and value['claims']==0,value
        assert 'CHO LSP pilot' not in d.sh('dumpsys','input')
        record('live-a11y-activation-stops-monitor',final=value)
        denied=request('configured',profile=profile());assert not denied.get('active') and denied.get('error'),denied
        record('live-a11y-activation-refuses-new-monitor',final=denied)
    finally:
        d.sh('settings','put','secure','enabled_accessibility_services',original);time.sleep(.6)
        assert d.sh('settings','get','secure','enabled_accessibility_services').strip()==original
    rotation=d.sh('wm','user-rotation').strip().split()
    try:
        d.sh('wm','user-rotation','lock','1');time.sleep(.5);fixture();w,h=d.current_display_size();assert w>h
        start(profile(right_landscape_handle_width_dp=20));swipe();foreground,end=read_after()
        assert end['actions']==1 and PACKAGE+'/.ProbeActivity' in foreground,end
        record('landscape-physical-edge',final=end)
        fixture();start(profile(hide_landscape=True));swipe();foreground,end=read_after();assert end['claims']==0 and end['actions']==0,end
        record('hide-in-landscape',final=end)
    finally:d.sh('wm','user-rotation',*rotation);time.sleep(.4)
    fixture();start(profile());d.sh('wm','user-rotation','lock','1');time.sleep(.4);value=request('status')
    try:
        assert not value['active'] and value['reason']=='display-changed',value
        record('configured-rotation-stops-monitor',final=value)
    finally:d.sh('wm','user-rotation',*rotation);time.sleep(.4)
    assert status()==json.loads((OUT/'before.json').read_text(encoding='utf8'))
    save('after-tests.json',status());print('PASS',len(results),'configured host cases',flush=True)

if __name__=='__main__':
    if sys.argv[1]=='prepare':prepare()
    elif sys.argv[1]=='run':run()
    elif sys.argv[1]=='remaining':
        results.extend(json.loads((OUT/'results.json').read_text(encoding='utf8')));live_master();extras()
    elif sys.argv[1]=='actions':
        results.extend(json.loads((OUT/'results.json').read_text(encoding='utf8')));actions_and_lifecycle()
    elif sys.argv[1]=='final':
        results.extend(json.loads((OUT/'results.json').read_text(encoding='utf8')));final_checks()
    elif sys.argv[1]=='boundaries':
        results.extend(json.loads((OUT/'results.json').read_text(encoding='utf8')));final_boundaries()
    else:raise SystemExit('prepare | run | remaining | actions | final | boundaries')
