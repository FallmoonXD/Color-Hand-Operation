"""Focused on-device UI regression checks; preserves all saved user configuration."""
import json, re, subprocess, time, sys
import device as d

results=json.loads((d.B/'coloros-ui-validation.json').read_text(encoding='utf-8')) if '--panels' in sys.argv and (d.B/'coloros-ui-validation.json').exists() else {}
baseline={name:d.preferences(name) for name in ('edgeflow','edgeflow_launcher','replica_widgets')}
tools_before=d.sh('run-as',d.P,'cat','files/tools-layout-v1.json')
settings={key:d.sh('settings','get','system',key).strip() for key in ('system_material_blur_enable','system_material_stroke_enable')}
night=d.sh('settings','get','secure','ui_night_mode').strip()
assert night in ('1','2'), 'Run this check with an explicit light/dark mode to restore'

def theme():
    value=d.sh('su','-c','content call --uri content://dev.edgeflow.material --method ui_theme')
    return json.loads(value[value.index('{"source"'):value.rindex('}')])

def setting(key,value):
    d.sh('settings','put','system',key,value);time.sleep(.35)

def preview_frames():
    windows=d.sh('dumpsys','window','windows').replace('\r','')
    result={}
    for block in re.split(r'(?=^\s*Window #\d+ )',windows,flags=re.M):
        header=re.match(r'\s*Window #\d+ Window\{[^\n]+Color Hand Operation 触控区域预览 (\w+)\}',block)
        if header:
            frame=re.search(r'\bframe=(\[-?\d+,-?\d+\]\[-?\d+,-?\d+\])',block)
            assert frame,'Missing preview window geometry'
            result[header[1]]=frame[1]
    return result

def pointer(n,change_theme=False):
    before=d.preferences();a,b,c,e=d.bounds(n);y=(b+e)//2
    resting=preview_frames()
    assert resting,'Home owns the configured handle previews'
    p=subprocess.Popen(d.A+['shell',f'CLASSPATH=/data/local/tmp/edgeflow-probe.jar app_process /system/bin probe.Pointer {a+int((c-a)*.368)} {y} {a+int((c-a)*.72)} {y} 2200 CANCEL'],stdout=subprocess.PIPE,stderr=subprocess.PIPE,text=True)
    try:
        assert p.stdout.readline().strip()=='HOLDING'
        assert d.preferences()==before,'Draft written while dragging'
        if change_theme:setting('system_material_stroke_enable','0')
        else:
            draft=preview_frames()
            assert draft.keys()==resting.keys() and draft!=resting,'Expected draft handle geometry'
        out,err=p.communicate(timeout=6);assert p.returncode==0,(out,err)
        time.sleep(.3);assert d.preferences()==before,'CANCEL or theme recreation wrote draft'
        assert preview_frames()==resting,'Cancelled drag must restore the page-owned preview bounds'
    finally:
        if p.poll() is None:p.communicate(timeout=6)
        if change_theme:setting('system_material_stroke_enable',settings['system_material_stroke_enable'])

try:
    if '--panels' not in sys.argv:
        d.main_home()
        tree=d.ui()
        titles=[n for n in tree.iter('node') if n.get('package')==d.P and n.get('content-desc')=='Color Hand Operation' and d.visible(n)]
        assert len(titles)==1,'The typeset title must expose one complete label'
        title=d.bounds(titles[0]);more=d.bounds(next(n for n in tree.iter('node') if n.get('content-desc')=='更多选项' and d.visible(n)))
        assert title[2]<=more[0] or more[2]<=title[0],'Title overlaps the more button'
        results['home_title_accessibility']=True
        d.click_item('左侧触钮',True)
        title=next(n for n in d.ui().iter('node') if n.get('text')=='左侧触钮')
        assert d.bounds(title)[1]>=140,'Secondary toolbar overlaps status bar'
        d.shot('coloros-final-left');results['secondary_insets']=True;print('PASS insets',flush=True)
        d.range_settings();pointer(d.find_item('触摸宽度',True));results['geometry_cancel']=True
        pointer(d.find_item('触摸宽度',True),True);results['geometry_theme_interruption']=True;print('PASS geometry cancellation/theme interruption',flush=True)
        d.open_action_chooser('right');d.wait_node('搜索操作');d.shot('coloros-final-action-chooser');d.sh('input','keyevent','4');d.wait_node('短距离滑动')
        results['action_chooser_open_close']=True
        d.action_settings('快速工具');d.click_item('编辑按钮',True)
        d.wait_node('已添加 12 / 12');d.shot('coloros-final-tools-editor')
        d.click_item('已添加 Wi-Fi，位置 1',True);d.wait_node('已添加 11 / 12')
        d.sh('cmd','uimode','night','yes');time.sleep(1)
        d.wait_node('已添加 11 / 12');assert theme()['dark'];d.shot('coloros-final-tools-editor-dark')
        d.sh('cmd','uimode','night','no');time.sleep(1);d.wait_node('已添加 11 / 12')
        d.click_item('添加应用快捷入口',True)
        edit=next(n for n in d.ui().iter('node') if n.get('class')=='android.widget.EditText')
        d.tap(edit);d.sh('input','text','Chrome');time.sleep(.5)
        d.shot('coloros-final-app-search')
        # The picker has its own page and IME. Its toolbar returns to the draft.
        back=next(n for n in d.ui().iter('node') if n.get('package')==d.P and n.get('content-desc')=='返回' and d.visible(n))
        d.tap(back);d.wait_node('保存')
        back=next(n for n in d.ui().iter('node') if n.get('package')==d.P and n.get('content-desc')=='返回' and d.visible(n))
        d.tap(back);d.wait_node('放弃');d.shot('coloros-final-discard-dialog');d.click('放弃')
        assert d.sh('run-as',d.P,'cat','files/tools-layout-v1.json')==tools_before
        results['tools_draft_night_search_discard']=True;print('PASS editor draft/night/search/discard',flush=True)
    d.main_home();d.sh('input','keyevent','3');time.sleep(.4)
    d.sh('input','swipe','1259','1300','859','1900','1100');d.wait_node('通知栏')
    d.shot('coloros-final-tools-material');results['native_before']=d.sh('su','-c','content call --uri content://dev.edgeflow.material --method stats').strip()
    setting('system_material_stroke_enable','0');assert not theme()['stroke'];d.shot('coloros-final-tools-no-stroke')
    setting('system_material_blur_enable','0');assert not theme()['blur'];d.shot('coloros-final-tools-opaque')
    stats=d.sh('su','-c','content call --uri content://dev.edgeflow.material --method stats').strip();results['native_disabled']=stats
    assert 'active=0' in stats,stats
    setting('system_material_stroke_enable',settings['system_material_stroke_enable']);setting('system_material_blur_enable',settings['system_material_blur_enable']);time.sleep(.7)
    assert theme()['blur'];d.shot('coloros-final-tools-material-restored')
    results['material_live_toggle']=True
    d.sh('input','tap','630','2550');time.sleep(.4)
    d.sh('input','swipe','5','1900','455','1450','1100');d.wait_widget_panel()
    d.shot('coloros-final-widgets');d.open_widget_page_menu();d.shot('coloros-final-widget-menu')
    d.sh('input','keyevent','4');time.sleep(.3);d.sh('input','tap','630','2550');results['widgets_and_menu']=True
finally:
    for key,value in settings.items():setting(key,value)
    d.sh('cmd','uimode','night','yes' if night=='2' else 'no');time.sleep(.7)
    results['saved_preferences_equal']={name:d.preferences(name)==values for name,values in baseline.items()}
    results['tools_layout_equal']=d.sh('run-as',d.P,'cat','files/tools-layout-v1.json')==tools_before
    (d.B/'coloros-ui-validation.json').write_text(json.dumps(results,ensure_ascii=False,indent=2),encoding='utf-8')
assert all(results['saved_preferences_equal'].values()),results['saved_preferences_equal']
assert results['tools_layout_equal']
print(json.dumps(results,ensure_ascii=False,indent=2))
