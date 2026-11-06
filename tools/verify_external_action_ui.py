"""Configure external targets separately from choosing a gesture action; restore test keys."""
import json,time
import device as d
from preference_trial import PreferenceTrial

key='action_right_up_short'
allowed={key,'target_app_'+key,'target_shortcut_'+key}
result={}

def chooser():d.open_action_chooser('right','up',False)
def gear(shortcut=False):d.click_item('设置应用快捷方式' if shortcut else '设置启动指定应用',True)
def package_windows():
    return [n for n in d.ui().iter('node') if n.get('package')==d.P]
def select_shortcut():
    d.click_item('设置')
    # Actual Settings publisher shortcut is harmless to configure; this does not launch it.
    found=None
    for _ in range(20):
        candidates=[n for n in package_windows() if 'WLAN' in n.get('text','') or 'WLAN' in n.get('content-desc','') or 'Wi-Fi' in n.get('text','')]
        found=next((n for n in candidates if n.get('clickable')=='true' and d.visible(n)),None)
        if found is not None:break
        time.sleep(.3)
    assert found is not None,'No actual Settings Wi-Fi shortcut'
    d.shot('oho-controls-shortcuts')
    d.tap(found);time.sleep(.6)

with PreferenceTrial({'edgeflow':allowed,'edgeflow_launcher':{'shortcut_labels'}}) as trial:
    before=trial.before['edgeflow'];active=before[key]
    chooser();gear();d.wait_node('启动指定应用');assert d.preferences()==before
    d.shot('oho-controls-app-picker');d.click_item('设置');time.sleep(.5)
    state=d.preferences();assert state[key]==active
    app=state['target_app_'+key];assert app.startswith('app:com.android.settings/')
    result['app_gear_configures_without_selecting']=True
    d.click_item('启动指定应用',True);time.sleep(.4);assert d.preferences()[key]==app
    result['app_left_row_selects_saved_target']=True
    chooser();d.click_item('音量减小',True);time.sleep(.4);assert d.preferences()[key]=='volume_down'
    chooser();d.click_item('启动指定应用',True);time.sleep(.4);assert d.preferences()[key]==app
    result['target_survives_other_action_selection']=True
    chooser();gear(True);assert d.preferences()[key]==app
    d.shot('oho-controls-shortcut-apps');select_shortcut()
    state=d.preferences();assert state[key]==app
    shortcut=state['target_shortcut_'+key];assert shortcut.startswith('launcher_shortcut:')
    result['shortcut_gear_configures_without_selecting']=True
    d.click_item('应用快捷方式',True);time.sleep(.4);assert d.preferences()[key]==shortcut
    result['shortcut_left_row_selects_saved_target']=True
    chooser();d.shot('oho-controls-action-rows');gear(True);state=d.preferences()
    d.sh('input','keyevent','4');time.sleep(.5);assert d.preferences()==state
    result['shortcut_picker_cancel_preserves_configuration']=True
result['preferences_restored_exactly']=True
(d.B/'oho-controls-external-validation.json').write_text(json.dumps(result,ensure_ascii=False,indent=2),encoding='utf-8')
print(json.dumps(result,ensure_ascii=False,indent=2))
