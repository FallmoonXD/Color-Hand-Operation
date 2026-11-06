"""Large-font chooser and recreated ColorOS application subpage, restoring system settings."""
import json,re,time
import device as d

before=d.preferences();font=d.sh('settings','get','system','font_scale').strip();mode=d.sh('cmd','uimode','night').strip();night=re.search(r'Night mode: (\w+)',mode).group(1);out={}
def back():d.sh('input','keyevent','4');time.sleep(.5)
def no_sheet():assert not any(n.get('text')=='选择操作' and d.visible(n) for n in d.ui().iter('node'))
try:
 d.sh('settings','put','system','font_scale','1.6');time.sleep(.8)
 d.open_action_chooser('right');close=d.wait_node('关闭选择操作');cancel=d.wait_node('取消')
 assert d.bounds(close)[2]<=1264 and d.bounds(cancel)[3]<=2626
 d.find_item('设置启动指定应用',True);d.shot('action-menu-large-font')
 out['large_font_header_footer_and_config_actions_fit']=True
 d.click_item('设置启动指定应用',True);d.wait_node('启动指定应用');d.find_item('设置',True);no_sheet()
 d.sh('cmd','uimode','night','yes');time.sleep(1)
 d.wait_node('启动指定应用');d.find_item('设置',True);d.shot('action-menu-app-dark-large-font');no_sheet()
 assert d.preferences()==before
 back();d.wait_node('短距离滑动');no_sheet()
 out['subpage_survives_theme_recreation_and_returns_without_chooser']=True
finally:
 d.sh('cmd','uimode','night',night)
 if font=='null':d.sh('settings','delete','system','font_scale')
 else:d.sh('settings','put','system','font_scale',font)
 time.sleep(.8)
 out['font_restored']=d.sh('settings','get','system','font_scale').strip()==font
 out['night_mode_restored']=d.sh('cmd','uimode','night').strip()==mode
 out['preferences_unchanged']=d.preferences()==before
 (d.B/'action-menu-environment-validation.json').write_text(json.dumps(out,ensure_ascii=False,indent=2),encoding='utf-8')
print(json.dumps(out,ensure_ascii=False,indent=2))
