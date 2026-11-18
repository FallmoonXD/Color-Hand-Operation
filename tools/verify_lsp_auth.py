"""Check the SystemUI control permission with a fixture that did not request it."""
import json,subprocess,zipfile
from verify_lsp_input import ROOT,d
from lsp_control import request

PACKAGE='dev.edgeflow.lspuntrusted'
def build():
    p=ROOT/'.checks/lsp-untrusted';p.mkdir(exist_ok=True)
    source=(ROOT/'validation/lsp/fixture/AndroidManifest.xml').read_text(encoding='utf8')
    source=source.replace('package="dev.edgeflow.lspfixture"','package="'+PACKAGE+'"').replace('<uses-permission android:name="dev.edgeflow.lsp.pilot.CONTROL"/>','')
    source=source.replace('<uses-permission android:name="dev.edgeflow.permission.READ_GESTURES"/>','')
    (p/'AndroidManifest.xml').write_text(source,encoding='utf8')
    sdk=ROOT/'.toolchains/android-sdk';bt=sdk/'build-tools/36.0.0'
    def run(*args):subprocess.run(list(map(str,args)),check=True)
    run(bt/'aapt2.exe','link','-I',sdk/'platforms/android-35/android.jar','--manifest',p/'AndroidManifest.xml','-o',p/'raw.apk')
    with zipfile.ZipFile(p/'raw.apk','a') as z:z.write(ROOT/'.checks/lsp-fixture/dex/classes.dex','classes.dex')
    run(bt/'zipalign.exe','-f','4',p/'raw.apk',p/'aligned.apk')
    from pathlib import Path
    run(bt/'apksigner.bat','sign','--ks',Path.home()/'.android/debug.keystore','--ks-pass','pass:android','--out',p/'fixture.apk',p/'aligned.apk')
    return p/'fixture.apk'

def main():
    assert not d.sh('pm','list','packages',PACKAGE).strip()
    apk=build();subprocess.run(d.A+['install','--no-incremental',str(apk)],check=True)
    try:
        request('stop')
        d.sh('am','start','-W','-n',PACKAGE+'/dev.edgeflow.lspfixture.SecondActivity')
        rejected=request('capture',package=PACKAGE)
        d.sh('am','start','-W','-n','dev.edgeflow.lspfixture/.SecondActivity')
        after=request('status')
        assert rejected.get('timeout') and not rejected.get('client_permission'),rejected
        assert after.get('connected') and not after.get('active'),after
        assert 'CHO LSP pilot' not in d.sh('dumpsys','input')
        invalid=request('not-an-operation');assert invalid.get('connected') and invalid.get('error') and not invalid.get('active'),invalid
        request('observe');clean=request('stop');assert not clean.get('error') and not clean.get('active'),clean
        (ROOT/'verification/lsp-systemui/control-permission.json').write_text(json.dumps({'without_permission':rejected,'host_after_rejection':after,'invalid_operation':invalid,'recovered':clean},indent=2))
        print('PASS caller without CONTROL denied, invalid operation contained, host still responds')
    finally:subprocess.run(d.A+['uninstall',PACKAGE],check=True)

if __name__=='__main__':main()
