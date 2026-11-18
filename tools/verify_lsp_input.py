"""Exercise the pilot backend via Root without installing/reloading an LSP module.

Only the disposable fixture is installed. CHO preferences and accessibility
authorization must remain unchanged. Run from the checkout with an unlocked phone.
"""
from pathlib import Path
import json,os,queue,shlex,shutil,subprocess,sys,threading,time,zipfile
import device as d

ROOT=Path(__file__).resolve().parents[1]
OUT=ROOT/'verification/lsp-input-prototype'
PACKAGE='dev.edgeflow.lspfixture'
REMOTE='/data/local/tmp/cho-lsp-pilot.apk'

def build_fixture():
    build=ROOT/'.checks/lsp-fixture';build.mkdir(parents=True,exist_ok=True)
    for name in ['classes','dex']:(build/name).mkdir(exist_ok=True)
    sdk=Path(os.environ.get('ANDROID_SDK_ROOT',ROOT/'.toolchains/android-sdk'))
    jar=sdk/'platforms/android-35/android.jar';bt=sdk/'build-tools/36.0.0'
    def run(*args):subprocess.run(list(map(str,args)),check=True)
    run('javac','--release','17','-encoding','utf8','-cp',jar,'-d',build/'classes',*list((ROOT/'validation/lsp/fixture').glob('*.java')))
    run(bt/'d8.bat','--min-api','33','--lib',jar,'--output',build/'dex',*list((build/'classes').rglob('*.class')))
    run(bt/'aapt2.exe','link','-I',jar,'--manifest',ROOT/'validation/lsp/fixture/AndroidManifest.xml','-o',build/'raw.apk')
    with zipfile.ZipFile(build/'raw.apk','a') as archive:archive.write(build/'dex/classes.dex','classes.dex')
    run(bt/'zipalign.exe','-f','4',build/'raw.apk',build/'aligned.apk')
    run(bt/'apksigner.bat','sign','--ks',Path.home()/'.android/debug.keystore','--ks-pass','pass:android','--out',build/'fixture.apk',build/'aligned.apk')
    return build/'fixture.apk'

def preferences():
    os.environ['CHO_TEST_READ_RELEASE_PREFS']='1'
    names=d.sh('su','-c','ls /data/user/0/dev.edgeflow/shared_prefs').split()
    return {n:json.loads(json.dumps(d.preferences(n[:-4]),default=lambda x:sorted(x))) for n in names if n.endswith('.xml')}

def status():
    return {'prefs':preferences(),'accessibility':d.sh('settings','get','secure','enabled_accessibility_services').strip(),
            'navigation':d.sh('settings','get','secure','navigation_mode').strip(),
            'display':{key:d.sh('settings','get','system',key).strip() for key in ['screen_brightness','screen_brightness_mode','screen_off_timeout']},
            'stay_awake':d.sh('settings','get','global','stay_on_while_plugged_in').strip()}

def fixture():
    d.sh('am','start','-S','-W','-n',PACKAGE+'/.ProbeActivity')
    d.sh('am','start','-W','-n',PACKAGE+'/.SecondActivity')
    time.sleep(.25)
    assert PACKAGE+'/.SecondActivity' in d.resumed(),'Fixture did not reach second page'

class Probe:
    def __init__(self,mode,duration=20):
        self.lines=[];self.messages=queue.Queue()
        command=f'CLASSPATH={REMOTE} app_process /system/bin dev.edgeflow.lsp.RootProbe {mode} {duration}'
        self.process=subprocess.Popen(d.A+['shell',shlex.join(['su','-c',command])],stdin=subprocess.PIPE,stdout=subprocess.PIPE,stderr=subprocess.STDOUT,text=True,encoding='utf8',errors='replace',bufsize=1)
        def read():
            for line in self.process.stdout:
                self.lines.append(line)
                try:self.messages.put(json.loads(line))
                except ValueError:pass
        self.reader=threading.Thread(target=read,daemon=True);self.reader.start()
        try:self.ready=self.wait('ready',8)
        except Exception:
            self.stop();raise
    def wait(self,event,timeout):
        end=time.monotonic()+timeout
        while time.monotonic()<end:
            try:value=self.messages.get(timeout=min(.3,end-time.monotonic()))
            except queue.Empty:
                if self.process.poll() is not None:break
                continue
            if value.get('event')==event:return value
        raise AssertionError('No '+event+' from probe: '+''.join(self.lines)[-1600:])
    def stop(self):
        if self.process.poll() is None:
            try:self.process.stdin.write('stop\n');self.process.stdin.flush()
            except OSError:pass
        try:self.process.wait(timeout=5)
        except subprocess.TimeoutExpired:
            # The device-side watchdog remains bounded at 30 seconds if ADB is interrupted.
            self.process.wait(timeout=32)
        self.reader.join(timeout=1)
        results=[]
        while not self.messages.empty():results.append(self.messages.get_nowait())
        final=next((x for x in reversed(results) if x.get('event')=='finished'),None)
        return final

def main():
    OUT.mkdir(parents=True,exist_ok=True)
    before=status();(OUT/'before.json').write_text(json.dumps(before,ensure_ascii=False,indent=2),encoding='utf8')
    assert not d.sh('pm','list','packages',PACKAGE).strip(),'Fixture package already exists'
    assert not any(n.get('content-desc') in ('左侧手势区域','右侧手势区域') for n in d.ui().iter('node')),'Disable CHO input before comparing independent monitors'
    apk=ROOT/'lsp/build/outputs/apk/debug/lsp-debug.apk'
    assert apk.is_file(),'Build :lsp:assembleDebug first'
    fixture_apk=build_fixture();results=[];installed=False
    try:
        subprocess.run(d.A+['push',str(apk),REMOTE],check=True)
        subprocess.run(d.A+['install','--no-incremental',str(fixture_apk)],check=True);installed=True
        for mode in ['observe','capture']:
            for side in ['left','right']:
                for kind in ['tap','vertical','inward']:
                    fixture();width,height=d.current_display_size();x=70 if side=='left' else width-70;y=height//2
                    probe=Probe(mode)
                    marker='LSP_'+str(time.time_ns());d.sh('log','-t','CHOLspFixture',marker)
                    try:
                        if kind=='tap':d.sh('input','tap',str(x),str(y))
                        else:
                            ex=x+(260 if side=='left' else -260) if kind=='inward' else x
                            ey=y if kind=='inward' else y-260
                            d.sh('input','swipe',str(x),str(y),str(ex),str(ey),'350')
                        time.sleep(.35);resumed=d.resumed()
                        events=d.sh('logcat','-d','-v','brief','-s','CHOLspFixture:I').split(marker)[-1].strip()
                    finally:final=probe.stop()
                    row={'mode':mode,'side':side,'kind':kind,'ready':probe.ready,'final':final,'foreground':resumed,'fixture_events':events}
                    results.append(row);(OUT/'results.json').write_text(json.dumps(results,ensure_ascii=False,indent=2),encoding='utf8')
                    (OUT/(mode+'-'+side+'-'+kind+'.log')).write_text(''.join(probe.lines),encoding='utf8')
                    assert final is not None,'Missing clean shutdown'
                    if mode=='capture' and kind=='inward':
                        assert final['claims']==1 and final['actions']==1 and final['rejected_actions']==0,row
                        assert PACKAGE+'/.ProbeActivity' in resumed,row
                        assert 'ACTION_CANCEL' in events and 'CLICK' not in events,row
                    else:
                        assert final['claims']==0 and final['actions']==0,row
                        assert PACKAGE+'/.SecondActivity' in resumed,row
                        assert 'ACTION_UP' in events and 'ACTION_CANCEL' not in events,row
                        if kind=='tap':assert 'CLICK '+side in events,row
                    assert not final['active'] and not final['error'],row
                    monitors=d.sh('dumpsys','input');assert 'CHO LSP pilot' not in monitors,'Input monitor leaked after stop'
                    print('PASS',mode,side,kind,flush=True)
    finally:
        if installed:subprocess.run(d.A+['uninstall',PACKAGE],check=True)
        d.sh('rm','-f',REMOTE)
        after=status();(OUT/'after.json').write_text(json.dumps(after,ensure_ascii=False,indent=2),encoding='utf8')
        assert after==before,'CHO configuration or device settings changed'
        print('PASS original CHO data, authorization and display settings retained',flush=True)

if __name__=='__main__':main()
