"""Retained chooser across configuration recreation, nested Activity and background/resume."""
import json,re,time
import device as d

before=d.preferences();font=d.sh('settings','get','system','font_scale').strip();mode=d.sh('cmd','uimode','night').strip();night=re.search(r'Night mode: (\w+)',mode).group(1);out={}
def back():d.sh('input','keyevent','4');time.sleep(.55)
def sheet():return d.wait_node('选择操作')
def lower_group_still_visible():
 tree=d.ui();assert any(n.get('content-desc')=='设置虚拟导航键' and d.visible(n) for n in tree.iter('node'))
 assert not any(n.get('content-desc')=='无' and d.visible(n) for n in tree.iter('node'))
try:
 d.open_action_chooser('right');d.find_item('设置应用快捷方式',True);d.click_item('设置快速工具',True);d.wait_node('快速工具')
 d.sh('settings','put','system','font_scale','1.15');time.sleep(.9);d.wait_node('编辑按钮')
 # The recreated Activity has metadata but no dialog until we return to its owner.
 d.click_item('编辑按钮',True);d.wait_node('快速工具');back();d.wait_node('编辑按钮');back();sheet();lower_group_still_visible()
 out['metadata_only_chooser_survives_nested_activity_after_recreation']=True
 d.sh('cmd','uimode','night','yes');time.sleep(.9);sheet();lower_group_still_visible();d.shot('retained-menu-dark-restored')
 out['visible_chooser_restores_after_theme_recreation']=True
 d.sh('cmd','uimode','night',night);d.sh('settings','put','system','font_scale',font);time.sleep(.9);sheet();lower_group_still_visible()
 identity=sheet().get('node-hash');snapshot=[(n.get('content-desc'),d.bounds(n)) for n in d.ui().iter('node') if n.get('class')=='android.widget.RadioButton' and n.get('content-desc') and d.visible(n)]
 d.sh('input','keyevent','3');time.sleep(.5);d.sh('am','start','-W','-n',d.P+'/.MainActivity');time.sleep(.6)
 assert sheet().get('node-hash')==identity;lower_group_still_visible()
 current=[(n.get('content-desc'),d.bounds(n)) for n in d.ui().iter('node') if n.get('class')=='android.widget.RadioButton' and n.get('content-desc') and d.visible(n)]
 assert current==snapshot,(snapshot,current)
 out['background_resume_retains_same_nodes_and_scroll']=True
 back();d.wait_node('短距离滑动');back();d.wait_node('更多选项');assert not any(n.get('text')=='选择操作' and d.visible(n) for n in d.ui().iter('node'))
 out['explicit_close_does_not_resurrect']=True
 assert d.preferences()==before
finally:
 d.sh('cmd','uimode','night',night)
 if font=='null':d.sh('settings','delete','system','font_scale')
 else:d.sh('settings','put','system','font_scale',font)
 time.sleep(.6)
 out['font_restored']=d.sh('settings','get','system','font_scale').strip()==font
 out['night_mode_restored']=d.sh('cmd','uimode','night').strip()==mode
 out['preferences_unchanged']=d.preferences()==before
 (d.B/'retained-menu-environment-validation.json').write_text(json.dumps(out,ensure_ascii=False,indent=2),encoding='utf-8')
print(json.dumps(out,ensure_ascii=False,indent=2))
