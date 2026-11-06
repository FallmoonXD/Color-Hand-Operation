"""Check the actual compact sliders, feedback controls and independent angle boundaries."""
import json,shlex,time
import device as d
from preference_trial import PreferenceTrial

keys={'vibration','quick_vibration','custom_vibration','vibration_duration_ms','short_angle_up_inner_degrees'}
result={}
def set_progress(description,value):
    # Exercise the real touch path. The shell UiAutomation process twice exited
    # with status 137 on ACTION_SET_PROGRESS; the app itself remained healthy.
    node=d.find_item(description,True);a,b,c,e=d.bounds(node)
    steps=30 if description=='振动级别' else 65
    x=round(a+49+(c-a-98)*value/steps)
    d.sh('input','tap',str(x),str((b+e)//2));time.sleep(.35)
def check_center(title):
    number=d.find_item(title+'当前值',True);bar=d.find_item(title,True)
    a,b,c,e=d.bounds(number);x,y,z,w=d.bounds(bar)
    assert abs((a+c)-(x+z))<8,(title,d.bounds(number),d.bounds(bar))
    assert e<=y+4,(title,e,y)
    assert number.get('text')==bar.get('state-description')
    return number.get('text')
with PreferenceTrial({'edgeflow':keys}) as trial:
    d.main_home();check_center('触摸宽度');check_center('大小');check_center('位置');d.shot('oho-controls-home')
    d.main_link('手势设置')
    assert not any('GestureGuide' in n.get('class','') for n in d.ui().iter('node'))
    quick=d.find_item('快速振动',True);initial=quick.get('checked')
    d.tap(quick);time.sleep(.3);assert d.preferences()['quick_vibration']==(initial!='true')
    d.tap(d.find_item('快速振动',True));time.sleep(.3)
    result['quick_vibration_is_independent_switch']=True
    custom=d.find_item('自定义振动强度',True)
    if custom.get('checked')=='true':d.tap(custom);time.sleep(.3)
    assert not any(n.get('content-desc')=='振动级别' for n in d.ui().iter('node'))
    d.tap(d.find_item('自定义振动强度',True));time.sleep(.5)
    assert d.preferences()['custom_vibration'] is True
    set_progress('振动级别',15)
    assert d.preferences()['vibration_duration_ms']==15
    assert check_center('振动级别')=='50%'
    d.shot('oho-controls-vibration')
    d.tap(d.find_item('自定义振动强度',True));time.sleep(.4)
    assert not any(n.get('content-desc')=='振动级别' for n in d.ui().iter('node'))
    assert d.preferences()['vibration_duration_ms']==15
    result['custom_level_hides_without_forgetting_value']=True
    d.click_item('手势角度',True)
    names=['上方限位','向上斜滑边界','向下斜滑边界','下方限位']
    for prefix in ('短滑','长滑'):
        for name in names:check_center(prefix+name)
    assert not any('GestureGuide' in n.get('class','') for n in d.ui().iter('node'))
    d.find_item('短滑上方限位',True);d.shot('oho-controls-angles')
    before=d.preferences();set_progress('短滑向上斜滑边界',40)
    after=d.preferences();assert after['short_angle_up_inner_degrees']==45
    assert {k:v for k,v in after.items() if k!='short_angle_up_inner_degrees'}=={k:v for k,v in before.items() if k!='short_angle_up_inner_degrees'}
    assert check_center('短滑向上斜滑边界')=='45°'
    result['eight_independent_angle_boundaries_and_center_values']=True
result['preferences_restored_exactly']=True
(d.B/'oho-controls-ui-validation.json').write_text(json.dumps(result,ensure_ascii=False,indent=2),encoding='utf-8')
print(json.dumps(result,ensure_ascii=False,indent=2))
