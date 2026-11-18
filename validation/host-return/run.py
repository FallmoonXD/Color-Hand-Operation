"""Exercise widget configuration returns on an unlocked development phone using two disposable UIDs.

The test host uses an Activity window. Its two subprocess substitutions model su denial and
a later grant without modifying the phone's Root manager. The installed CHO is never replaced.
"""
import argparse
import json
import re
import shutil
import subprocess
import sys
import tempfile
import time
import zipfile
from pathlib import Path

HERE = Path(__file__).resolve().parent
ROOT = HERE.parents[1]
sys.path.insert(0, str(ROOT/'tools'))
import device as d
from verify_lsp_unlock import status
from verify_widget_edit import fixture

PKG = 'dev.edgeflow.hostreturncheck'
PROVIDER = 'dev.edgeflow.widgettest'
COMPONENT = PKG+'/replica.HostReturnCheck'


def substitute(path, old, new):
    source = path.read_text(encoding='utf8')
    if source.count(old) != 1:
        raise ValueError('Validation substitution no longer matches '+str(path))
    path.write_text(source.replace(old, new), encoding='utf8')


def build_provider_and_host(folder, log):
    build = ROOT/'.checks/host-return-validation'
    # Only current production sources enter the test build.
    src = folder/'java'
    shutil.copytree(ROOT/'app/src/main/java',src)
    shutil.copyfile(HERE/'HostReturnCheck.java',src/'replica/HostReturnCheck.java')
    substitute(src/'replica/PanelOverlayDialog.java',
               'window.setType(WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY);',
               'window.setType(WindowManager.LayoutParams.TYPE_APPLICATION);')
    substitute(src/'replica/NativeBlur.java',
               'new ProcessBuilder("su","-c",command).redirectErrorStream(true).start()', 'HostReturnCheck.startBlur()')
    substitute(src/'dev/edgeflow/RootAccess.java',
               'new ProcessBuilder("su","-c","id -u").redirectErrorStream(true).start()', 'replica.HostReturnCheck.checkRoot()')
    with log.open('w',encoding='utf8') as output:
        subprocess.run([str(ROOT/'gradlew.bat'),'--offline','-I',str(HERE/'init.gradle'),'-PchoValidationSources='+str(src),':app:assembleDebug'],
                       cwd=ROOT,stdout=output,stderr=subprocess.STDOUT,check=True)
    apk = build/'build/outputs/apk/debug/app-debug.apk'
    with zipfile.ZipFile(apk) as z:
        assert not any('META-INF/xposed/' in n for n in z.namelist())
    return fixture(folder), apk


def start(op, existing=None):
    args = ['am','start','-W','-n',COMPONENT,'--es','op',op]
    if existing is not None:
        args += ['--ez','existing','true','--ei','id',str(existing)]
    d.sh(*args)


def read_report(name):
    raw = d.sh('run-as',PKG,'sh','-c','if test -f files/'+name+'.json; then cat files/'+name+'.json; fi')
    return json.loads(raw) if raw.strip() else None


def await_report(name):
    end = time.monotonic()+12
    while time.monotonic()<end:
        error = read_report('error')
        if error:
            raise AssertionError(error)
        result = read_report(name)
        if result is not None:
            return result
        time.sleep(.1)
    raise AssertionError('No validation result for '+name)


def wait_node(pkg, match):
    end = time.monotonic()+8
    while time.monotonic()<end:
        found = next((n for n in d.ui().iter('node') if n.get('package')==pkg and d.visible(n) and match(n)),None)
        if found is not None:
            return found
        time.sleep(.12)
    raise AssertionError((pkg,d.resumed()))


def bind(allow):
    wait_node('com.android.settings',lambda n:'CHO host validation' in n.get('text','') and 'CHO 必须配置插件' in n.get('text',''))
    d.tap(wait_node('com.android.settings',lambda n:n.get('text')==('创建' if allow else '取消')))


def provider_button():
    return wait_node(PROVIDER,lambda n:n.get('text','').startswith('完成配置 '))


def check(output):
    rows=[]
    def record(name,panel,ids):
        if panel:
            wait_node(PKG,lambda n:'第 1 页' in n.get('content-desc',''))
        # Wait for the actual Activity result and its window handoff, even if no panel is expected.
        deadline=time.monotonic()+6
        while time.monotonic()<deadline:
            if COMPONENT in d.resumed():
                break
            time.sleep(.1)
        assert COMPONENT in d.resumed(),name
        d.sh('run-as',PKG,'rm','-f','files/inspect.json')
        start('inspect')
        state=await_report('inspect')
        assert state['panel']==panel and not state['configuring'] and sorted(state['ids'])==sorted(ids), (name,state)
        assert [c['id'] for c in state['layout']]==ids, (name,state)
        rows.append(dict(case=name,state=state))
        (output/'widgets.json').write_text(json.dumps(rows,indent=2,ensure_ascii=False),encoding='utf8')
        print('PASS',name,flush=True)

    start('material')
    material=await_report('material')
    assert material['blocked'] and material['first']=='DENIED' and material['second']=='GRANTED',material
    assert material['launches_before']==1 and material['launches_after']==2 and material['online'],material
    assert material['active']==0 and material['frames']==0,material
    (output/'material.json').write_text(json.dumps(material,indent=2),encoding='utf8')
    start('new');bind(False);record('cancel-system-bind',True,[])
    start('new');bind(True);provider_button();d.sh('input','keyevent','4');record('cancel-new-configuration',True,[])
    start('new');bind(True);button=provider_button()
    widget=int(re.search(r'\d+',button.get('text'))[0])
    d.tap(button);record('save-new-configuration',True,[widget])
    for op,panel in [('new',True),('again',True),('cancelReturn',False),('revoke',False)]:
        start('new' if op=='again' else op,widget)
        assert provider_button().get('text')=='完成配置 '+str(widget)
        if op=='again':
            start('again');d.tap(provider_button())
        else:
            d.sh('input','keyevent','4')
        record({'new':'cancel-existing-configuration','again':'resume-provider-and-save',
                'cancelReturn':'dismissed-host','revoke':'expired-window-token'}[op],panel,[widget])


def main():
    parser=argparse.ArgumentParser(description=__doc__)
    parser.add_argument('output',type=Path)
    output=parser.parse_args().output
    phone=status()
    if not phone.get('interactive') or phone.get('locked'):
        raise RuntimeError('Widget UI checks require an already unlocked phone')
    for pkg in (PKG,PROVIDER):
        if ('package:'+pkg) in d.sh('pm','list','packages',pkg).splitlines():
            raise RuntimeError('Test package is already installed: '+pkg)
    output.mkdir(parents=True,exist_ok=False)
    installed=[]
    try:
        with tempfile.TemporaryDirectory() as folder:
            provider,host=build_provider_and_host(Path(folder),output/'build.log')
            for pkg,apk in [(PROVIDER,provider),(PKG,host)]:
                subprocess.run(d.A+['install','--no-incremental',str(apk)],check=True)
                installed.append(pkg)
        check(output)
    finally:
        for pkg in reversed(installed):
            subprocess.run(d.A+['uninstall',pkg],check=True)


if __name__=='__main__':
    main()
