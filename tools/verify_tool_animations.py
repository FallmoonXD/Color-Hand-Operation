"""Test the animation editor through its UI and put the original tool selections back."""
import json
import time
from pathlib import Path

import device as d
from verify_panel_exit import record, measure


TOOLS = {'quick_tools':'快速工具','widgets':'小组件','quick_launcher':'快速启动','task_switcher':'任务切换器',
         'virtual_touchpad':'虚拟触控板','navigation_panel':'虚拟导航键','keyboard_shortcuts':'键盘快捷方式',
         'volume_buttons':'音量按钮','screen_magnifier':'屏幕放大镜','screen_free_move':'屏幕自由移动',
         'screen_timeout':'屏幕超时','app_launcher':'应用启动器（兼容）'}
STYLES = {'default':'工具默认','volume':'系统音量条','expanded_volume':'展开音量面板','sidebar':'智能侧边栏',
          'anchor':'锚点菜单','slide':'侧向平移','center':'原地浮现','none':'无动画'}
BULK = '将这组动画应用到所有工具'


def enter(tool):
    d.main_home()
    d.click_item('面板与工具',True)
    d.click_item('动画设置',True)
    d.click_item(TOOLS[tool],True)
    d.wait_node(TOOLS[tool]+'动画')


def choose(phase,style):
    d.click('入场' if phase=='enter' else '退场')
    row=d.find_item(STYLES[style],True)
    d.tap(row)
    time.sleep(.2)


def selections():
    return {k:v for k,v in d.preferences().items() if k.startswith('tool_motion/')}


def bulk():
    d.click_item(BULK,True)


def restore(before):
    if before==selections():
        return
    enter('quick_tools')
    choose('enter','default');choose('exit','default');bulk()
    for tool in TOOLS:
        own=[(phase,before[f'tool_motion/{tool}/{phase}']) for phase in ('enter','exit')
             if f'tool_motion/{tool}/{phase}' in before]
        if own:
            enter(tool)
            for phase,style in own:
                choose(phase,style)
    assert selections()==before


def main():
    base=d.B/'tool-animations';base.mkdir(parents=True,exist_ok=True)
    initial=d.preferences();before=selections()
    try:
        enter('quick_tools')
        assert not any('点左图预览' in n.get('text','') for n in d.ui().iter('node'))
        d.click_item('预览智能侧边栏入场',True)
        assert selections()==before, 'Replaying the previews changed preferences'
        choose('enter','sidebar');choose('exit','slide')
        assert selections()==dict(before,**{'tool_motion/quick_tools/enter':'sidebar','tool_motion/quick_tools/exit':'slide'})
        assert not any(n.get('text')=='展开音量面板' and d.visible(n) for n in d.ui().iter('node'))
        bulk()
        all_keys={f'tool_motion/{tool}/{phase}':value for tool in TOOLS for phase,value in [('enter','sidebar'),('exit','slide')]}
        assert selections()==all_keys,'Global apply did not copy exactly the pair'
        enter('widgets');choose('exit','none')
        all_keys['tool_motion/widgets/exit']='none'
        assert selections()==all_keys,'Editing one tool changed a peer'
        # Reopen both panels from their ordinary settings entries.
        video=base/'quick-sidebar-in-slide-out.mp4'
        duration=record('快速工具','outside',video);report=measure(video)
        assert 330<=duration<=410 and report['intermediate_frames']>=3, (duration,report)
        record('小组件','outside',base/'widget-no-exit.mp4')
        print('PASS preview, independent phases, one-tap apply to 12 tools, isolated edit, actual slide and immediate dismiss')
        (base/'result.json').write_text(json.dumps({'window_exit_ms':duration,'mid_frames':report['intermediate_frames']}),encoding='utf8')
    finally:
        restore(before);d.main_home()
        assert d.preferences()==initial,'User preferences were not restored'


if __name__=='__main__':
    main()
