"""Inspect the final theme-initialized shortcut page and its first expansion without changing configuration."""
import json,time
import device as d

before=d.preferences();d.open_action_chooser('right');d.click_item('设置应用快捷方式',True);d.wait_node('应用快捷方式')
end=time.monotonic()+40
while time.monotonic()<end:
 tree=d.ui();headers=[n for n in tree.iter('node') if n.get('content-desc','').startswith('展开') and n.get('class')=='android.widget.Button' and d.visible(n)]
 if headers:break
 time.sleep(.3)
assert headers,'No visible shortcut publishers'
first=headers[0];name=first.get('content-desc')[2:]
d.shot('action-menu-shortcuts-final');d.tap(first);time.sleep(.35);d.wait_node('收起'+name)
d.shot('action-menu-shortcuts-expanded-final');assert d.preferences()==before
d.click_item('收起'+name,True);time.sleep(.3);d.wait_node('展开'+name);assert d.preferences()==before
d.sh('input','keyevent','4');time.sleep(.4);d.wait_node('选择操作')
d.sh('input','keyevent','4');time.sleep(.4);d.wait_node('短距离滑动')
pid=d.sh('pidof',d.P).strip();log=d.sh('logcat','-d','--pid='+pid,'-s','ReplicaIcons:I')
assert 'system=com.android.settings:coui_line_arrow_collapsed' in log
result={'final_shortcut_page_and_expansion_captured':True,'native_settings_expand_arrow_loaded':True,'return_via_action_directory':True,'preferences_unchanged':d.preferences()==before}
(d.B/'action-menu-first-frame-validation.json').write_text(json.dumps(result,ensure_ascii=False,indent=2),encoding='utf-8');print(json.dumps(result,ensure_ascii=False,indent=2))
