"""Modal lifecycle, header drag, and large-font bounds on the connected device."""
import json,time
import device as d

before=d.preferences();font=d.sh('settings','get','system','font_scale').strip();out={}
def has_sheet():return any(n.get('text')=='选择操作' and d.visible(n) for n in d.ui().iter('node'))
try:
    d.open_action_chooser('right');n=d.wait_node('选择操作');a,b,c,e=d.bounds(n)
    d.sh('input','swipe',str(a+80),str((b+e)//2),str(a+80),str(min(2500,(b+e)//2+750)),'420');time.sleep(.5)
    assert not has_sheet(),'Header drag did not dismiss'
    assert d.preferences()==before;out['header_drag_dismiss_without_selection']=True
    d.click_item('短滑直线向左',True);d.wait_node('选择操作')
    d.sh('am','start','-W','-a','android.settings.SETTINGS')
    deadline=time.monotonic()+8
    while d.P+'/.MainActivity' in d.resumed() and time.monotonic()<deadline:time.sleep(.25)
    assert d.P+'/.MainActivity' not in d.resumed(),'Device has not completed activity navigation'
    time.sleep(1.2)
    d.sh('am','start','-W','-n',d.P+'/.MainActivity');time.sleep(.4)
    assert not has_sheet();assert d.preferences()==before;out['background_dismiss_without_stale_selection']=True

    d.sh('settings','put','system','font_scale','1.6');time.sleep(.8)
    d.main_home()
    for _ in range(8):
        if any(n.get('content-desc')=='启用手势' and d.visible(n) for n in d.ui().iter('node')):break
        d.sh('input','swipe','620','850','620','2200','450')
    d.shot('refinement-home-large-font')
    d.open_action_chooser('right');d.wait_node('选择操作');close=d.wait_node('关闭选择操作');cancel=d.wait_node('取消')
    assert d.bounds(close)[2]<=1264 and d.bounds(cancel)[3]<=2626
    d.shot('refinement-action-sheet-large-font');out['large_font_fixed_header_and_footer_fit']=True
    d.click('关闭选择操作');d.main_link('应用例外');d.wait_node('在这些应用中停用手势');time.sleep(1)
    d.shot('refinement-exceptions-large-font');assert d.preferences()==before
finally:
    if font=='null':d.sh('settings','delete','system','font_scale')
    else:d.sh('settings','put','system','font_scale',font)
    time.sleep(.8)
    out['font_setting_restored']=d.sh('settings','get','system','font_scale').strip()==font
    out['preferences_unchanged']=d.preferences()==before
    (d.B/'design-refinement-bounds.json').write_text(json.dumps(out,ensure_ascii=False,indent=2),encoding='utf-8')
print(json.dumps(out,ensure_ascii=False,indent=2))
