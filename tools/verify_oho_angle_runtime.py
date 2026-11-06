"""Real gesture rejection/acceptance at an edited outer limit, and mirrored short/long actions."""
import json,time
import device as d
from preference_trial import PreferenceTrial

result={};volume=d.vol();key='short_angle_down_outer_degrees'
def outer(progress):
    d.main_link('手势设置');d.click_item('手势角度',True)
    n=d.find_item('短滑下方限位',True);a,b,c,e=d.bounds(n)
    d.sh('input','tap',str(round(a+49+(c-a-98)*progress/10)),str((b+e)//2));time.sleep(.3)
    assert d.preferences()[key]==75+progress
    d.sh('input','keyevent','3');time.sleep(.6)
def set_volume():d.sh('cmd','media_session','volume','--stream','3','--set','8')
try:
    with PreferenceTrial({'edgeflow':{key}}):
        outer(0);set_volume();d.sh('input','swipe','1259','1400','1114','2082','80');time.sleep(.4)
        assert d.vol()==8,'78-degree swipe escaped the 75-degree outer limit'
        result['edited_75_degree_limit_rejects_78_degree_swipe']=True
        outer(10);set_volume();d.sh('input','swipe','1259','1400','1114','2082','80');time.sleep(.4)
        assert d.vol()==7,'78-degree swipe failed within the 85-degree outer limit'
        d.sh('input','tap','630','220');time.sleep(.3)
        result['edited_85_degree_limit_accepts_same_swipe']=True
        for side,start,end in [('left',5,455),('right',1259,809)]:
            set_volume();d.sh('input','swipe',str(start),'1450',str(end),'1900','80');time.sleep(.4)
            assert d.vol()==7,side+' short gesture failed'
            d.sh('input','tap','630','220');time.sleep(.3)
            d.sh('input','swipe',str(start),'1450',str(end),'1900','450');time.sleep(.7)
            assert any('Window #' in line and 'Color Hand Operation 快速工具' in line for line in d.sh('dumpsys','window','windows').splitlines()),side+' long gesture failed'
            d.sh('input','tap','630','220');time.sleep(.5)
            result[side+'_short_volume_and_long_tools']=True
finally:
    d.sh('cmd','media_session','volume','--stream','3','--set',str(volume))
    result['original_volume_restored']=d.vol()==volume
    (d.B/'oho-controls-runtime-validation.json').write_text(json.dumps(result,ensure_ascii=False,indent=2),encoding='utf-8')
print(json.dumps(result,ensure_ascii=False,indent=2))
