"""Persistent pilot lifecycle checks. Never publishes or changes CHO gesture mappings."""
import sys,time,json,subprocess,hashlib
from pathlib import Path
import verify_lsp_configured as c
from verify_lsp_input import build_fixture

c.OUT=c.ROOT/'verification/lsp-persistent'
OUT=c.OUT;d=c.d;request=c.request;fixture=c.fixture
results=[]

def record(name,**data):
    results[:]=[r for r in results if r['case']!=name]
    results.append({'case':name,**data});c.save('results.json',results);print('PASS',name,flush=True)

def control(action):
    if action=='enable':
        d.sh('am','start','-W','-n',c.PILOT+'/dev.edgeflow.lsp.PilotActivity');time.sleep(.4)
        d.click_item('开启常驻输入')
        until=time.monotonic()+8;last=''
        while time.monotonic()<until:
            last='\n'.join(n.get('text','') for n in d.ui().iter('node'))
            if 'persistent_requested: true' in last and 'active: true' in last:
                fixture();return {'requested':True}
            if 'Exception:' in last:raise AssertionError(last[-2000:])
            time.sleep(.15)
        raise AssertionError('Controller UI did not report active persistent input: '+last[-2000:])
    value=request('persistent_'+action);assert not value.get('provider_error') and not value.get('source_error'),value
    return json.loads(value['state'])

def wait_state(state,timeout=8):
    deadline=time.monotonic()+timeout;last={}
    while time.monotonic()<deadline:
        last=request('status')
        if last.get('persistent_state')==state and (state!='RUNNING' or last.get('active')):return last
        time.sleep(.2)
    raise AssertionError((state,last))

def return_once():
    fixture();before=wait_state('RUNNING');c.swipe();foreground=d.resumed();after=request('status')
    assert c.PACKAGE+'/.ProbeActivity' in foreground and after['actions']==before.get('actions',0)+1,(foreground,before,after)
    return after

def prepare():
    c.prepare()
    d.sh('am','start','-W','-n',c.PILOT+'/dev.edgeflow.lsp.PilotActivity')
    # A newly installed, never-launched package cannot serve this provider on ColorOS.
    # The previous experimental index can remain disabled after uninstall.

def first():
    fixture();baseline=wait_state('DISABLED');assert baseline['persistent_available'] and not baseline.get('active'),baseline
    record('fresh-controller-disabled',status=baseline)
    result=control('enable');assert result['requested'];active=wait_state('RUNNING');assert active['expires_at']==0,active
    record('enable-real-owner-profile',status=return_once())
    time.sleep(31);active=wait_state('RUNNING');assert active['expires_at']==0
    record('past-30-seconds-still-running',status=return_once())
    for pkg in ['dev.edgeflow',c.PILOT]:
        pid=d.sh('pidof',pkg).strip();assert pid.isdecimal(),(pkg,pid);d.sh('su','-c','kill -9 '+pid)
    time.sleep(.3);assert not d.sh('sh','-c','pidof dev.edgeflow || true').strip()
    record('both-app-processes-killed',status=return_once())
    d.sh('am','start','-W','-n','dev.edgeflow/.MainActivity');time.sleep(.4)
    original=d.preferences()['enabled'];assert original
    try:
        d.click('启用手势');fixture();paused=wait_state('MASTER_OFF');assert not paused.get('active') and paused['persistent_requested']
        record('master-off-pauses-saved-request',status=paused)
    finally:
        d.sh('am','start','-W','-n','dev.edgeflow/.MainActivity');time.sleep(.3)
        if d.preferences()['enabled']!=original:d.click('启用手势')
    record('master-on-resumes',status=return_once())
    enabled=d.sh('settings','get','secure','enabled_accessibility_services').strip();assert 'dev.edgeflow/' not in enabled
    try:
        d.sh('settings','put','secure','enabled_accessibility_services',enabled+':dev.edgeflow/dev.edgeflow.GestureService')
        value=wait_state('ACCESSIBILITY');assert not value.get('active') and 'CHO LSP pilot' not in d.sh('dumpsys','input')
        record('a11y-pauses-without-touch',status=value)
    finally:d.sh('settings','put','secure','enabled_accessibility_services',enabled)
    record('a11y-removed-resumes',status=return_once())
    rotation=d.sh('wm','user-rotation').strip().split()
    try:
        before=wait_state('RUNNING');d.sh('wm','user-rotation','lock','1');time.sleep(.5);value=wait_state('RUNNING')
        assert value['width']>value['height'] and value['session_starts']>before['session_starts'],value
        record('rotation-rebuilds-input',status=value)
    finally:d.sh('wm','user-rotation',*rotation);time.sleep(.4)
    record('portrait-resumes',status=return_once())
    control('disable');value=wait_state('DISABLED');assert not value.get('active') and 'CHO LSP pilot' not in d.sh('dumpsys','input'),value
    record('explicit-disable-removes-monitor',status=value)
    d.sh('am','start','-W','-n','dev.edgeflow/.MainActivity');time.sleep(.3)
    try:
        d.click('启用手势');time.sleep(.3);d.click('启用手势')
    finally:
        if d.preferences()['enabled']!=original:d.click('启用手势')
    fixture();value=wait_state('DISABLED');assert not value.get('active');record('source-updates-do-not-undo-disable',status=value)
    legacy=request('capture');assert legacy['active'] and not legacy['persistent_requested'],legacy
    control('enable');record('persistent-replaces-bounded-monitor',status=return_once())
    assert c.status()==json.loads((OUT/'before.json').read_text(encoding='utf8'))
    c.save('before-reload-status.json',wait_state('RUNNING'))
    print('READY for SystemUI reload; saved request remains enabled',flush=True)

def restored():
    fixture();value=wait_state('RUNNING');before=json.loads((OUT/'before-reload-status.json').read_text(encoding='utf8'))
    assert value['pid']!=before['pid'] and value['source_checked'],value
    record('new-systemui-restores-after-unlock',status=return_once())
    control('disable');value=wait_state('DISABLED');record('disable-before-final-reload',status=value)
    assert c.status()==json.loads((OUT/'before.json').read_text(encoding='utf8'))

if __name__=='__main__':
    if sys.argv[1]=='prepare':prepare()
    else:
        if (OUT/'results.json').exists():results.extend(json.loads((OUT/'results.json').read_text(encoding='utf8')))
        if sys.argv[1]=='first':first()
        elif sys.argv[1]=='restored':restored()
        else:raise SystemExit('prepare | first | restored')
