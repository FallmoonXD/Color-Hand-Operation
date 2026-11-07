"""Check recreated-Activity ownership, large text and keyboard bounds; restore system font."""
import json,time,shlex
import device as d

before=d.preferences();font=d.sh('settings','get','system','font_scale').strip();out={}
def previews():return [s.strip() for s in d.sh('dumpsys','window','windows').splitlines() if 'Window #' in s and '触控区域预览' in s]
def open_palette():d.click_item('触钮颜色',True);return d.find_item('颜色代码',True)
try:
    d.main_home()
    for _ in range(3):
        d.sh('input','keyevent','3');time.sleep(.4)
        d.sh('am','start','-W','-f','0x14000000','-n',d.P+'/.MainActivity');time.sleep(.5)
        assert len(previews())==2,'Old Activity cleared successor preview'
    out['three_clear_top_recreations_preserve_successor_previews']=True
    d.sh('settings','put','system','font_scale','1.6');time.sleep(1)
    d.range_settings();field=open_palette();d.shot('palette-large-font')
    confirm=d.find_item('确定');assert d.visible(confirm)
    d.tap(field);time.sleep(.8);d.shot('palette-keyboard')
    assert d.visible(d.wait_node('确定')) and d.visible(d.wait_node('取消'))
    d.sh('input','keyevent','4');time.sleep(.4)
    d.click('取消');time.sleep(.4);assert d.preferences()==before
    out['large_font_and_keyboard_keep_actions_accessible']=True
finally:
    if font=='null':d.sh('settings','delete','system','font_scale')
    else:d.sh('settings','put','system','font_scale',font)
    time.sleep(.7)
    out['font_restored']=d.sh('settings','get','system','font_scale').strip()==font
    out['preferences_unchanged']=d.preferences()==before
    (d.B/'palette-bounds-validation.json').write_text(json.dumps(out,ensure_ascii=False,indent=2),encoding='utf-8')
print(json.dumps(out,ensure_ascii=False,indent=2))
