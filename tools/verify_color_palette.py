"""Gallery-style draft/confirm behavior, with exact restoration of test-only preference edits."""
import json,time,shlex,subprocess,xml.etree.ElementTree as E
import device as d

before=d.preferences();out={};tested={'left_color','right_color','handle_opacity','handles_linked'}
original=E.fromstring(d.sh('run-as',d.P,'cat','shared_prefs/edgeflow.xml'))

def edit(value):
    command=shlex.join(['app_process','/system/bin','probe.OverlayDump','set-text','颜色代码',value])
    d.sh('sh','-c','CLASSPATH=/data/local/tmp/edgeflow-probe.jar '+command);time.sleep(.25)
def set_progress(description,value):
    command=shlex.join(['app_process','/system/bin','probe.OverlayDump','set-progress',description,str(value)])
    d.sh('sh','-c','CLASSPATH=/data/local/tmp/edgeflow-probe.jar '+command);time.sleep(.25)
def palette(home=True):
    if home:d.main_home()
    d.click_item('触钮颜色',True);d.wait_node('颜色代码')
def close():
    d.click('取消');time.sleep(.4)
def code():return d.find_item('颜色代码',True)
def restore_test_values():
    current=d.preferences()
    if current==before:return
    assert {k:v for k,v in current.items() if k not in tested}=={k:v for k,v in before.items() if k not in tested},'Unexpected keys changed; do not overwrite them'
    tree=E.fromstring(d.sh('run-as',d.P,'cat','shared_prefs/edgeflow.xml'))
    for n in list(tree):
        if n.get('name') in tested:tree.remove(n)
    for n in original:
        if n.get('name') in tested:tree.append(n)
    # Stop the test app before touching its persisted XML, so its in-memory preferences
    # cannot overwrite the restoration. No widget, shortcut or other app data is touched.
    d.sh('am','force-stop',d.P)
    assert 'edgeflow.xml.bak' not in d.sh('run-as',d.P,'ls','shared_prefs').split()
    payload=E.tostring(tree,encoding='utf-8',xml_declaration=True)
    command=shlex.join(['run-as',d.P,'sh','-c','cat > shared_prefs/edgeflow.xml.palette-test && mv shared_prefs/edgeflow.xml.palette-test shared_prefs/edgeflow.xml'])
    subprocess.run(d.A+['shell','-T',command],input=payload,check=True,capture_output=True)
    d.sh('am','start','-W','-n',d.P+'/.MainActivity');d.activate();time.sleep(.6)
    # ColorOS may ask whether to retain the user's already-enabled accessibility
    # service after the test process restart. Keep the authorized existing state.
    keep=next((n for n in d.ui().iter('node') if n.get('text')=='保持开启' and n.get('package')=='com.oplus.safecenter'),None)
    if keep is not None:d.tap(keep);time.sleep(.5)
    assert d.preferences()==before,'Preference restoration failed'

try:
    palette();assert code().get('text')=='#FF188DFF'
    d.shot('palette-ring');assert d.preferences()==before
    edit('#GG0000');assert d.node('确定').get('enabled')=='false';assert d.preferences()==before
    edit('#80123456');assert d.node('确定').get('enabled')=='true'
    hue=next(n.get('content-desc') for n in d.ui().iter('node') if n.get('content-desc','').startswith('色相 '))
    set_progress(hue,120);assert code().get('text').startswith('#80')
    set_progress('透明度级别',41);assert d.preferences()==before
    d.click('网格');d.shot('palette-grid')
    cells=[n for n in d.ui().iter('node') if n.get('content-desc','').startswith('第 ') and ' 行，第 ' in n.get('content-desc','')]
    assert len(cells)==120,len(cells)
    d.tap(cells[17]);time.sleep(.2);assert code().get('text').startswith('#80');assert d.preferences()==before
    close();assert d.preferences()==before
    out['invalid_hex_blocks_confirm']=True;out['hue_and_grid_preserve_argb_alpha']=True;out['draft_color_and_opacity_cancel_without_writes']=True

    palette();edit('#80123456');set_progress('透明度级别',41);d.click('确定');time.sleep(.4)
    after=d.preferences();signed=0x80123456-2**32
    assert after['left_color']==signed and after['right_color']==signed and after['handle_opacity']==41,after
    out['confirm_commits_argb_and_independent_opacity_for_linked_pair']=True

    # Single-side entry must not overwrite its linked peer's color.
    d.open_handle('right');link=d.find_item('对齐触钮',True)
    if link.get('checked')=='true':d.tap(link);time.sleep(.4)
    palette(False);edit('#FF3399CC');d.click('确定');time.sleep(.4)
    after=d.preferences();assert after['left_color']==signed and after['right_color']==0xFF3399CC-2**32
    assert after['handle_opacity']==41;out['single_side_color_changes_only_that_side']=True
finally:
    restore_test_values();out['preferences_restored_exactly']=d.preferences()==before
    (d.B/'palette-color-validation.json').write_text(json.dumps(out,ensure_ascii=False,indent=2),encoding='utf-8')
print(json.dumps(out,ensure_ascii=False,indent=2))
