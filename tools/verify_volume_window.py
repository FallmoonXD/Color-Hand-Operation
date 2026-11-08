"""Verify the floating buttons against ColorOS SystemUI on a phone with a short volume gesture."""
import time

import device as d


def volume_gesture():
    prefs = d.preferences()
    width, _ = d.current_display_size()
    for action in ('volume_down', 'volume_up'):
        for side, label in [('right', '右侧手势区域'), ('left', '左侧手势区域')]:
            for direction in ('down', 'up', 'straight'):
                if prefs.get(d.action_key(side, direction)) != action:
                    continue
                handle = next((n for n in d.ui().iter('node') if n.get('content-desc') == label and d.visible(n)), None)
                if handle is None:
                    continue
                l, t, r, b = d.bounds(handle)
                x, y = (l + r) // 2, (t + b) // 2
                distance = round(width * .16)
                end_y = y + {'up': -1, 'down': 1, 'straight': 0}[direction] * round(.75 * distance)
                end_x = x + (distance if side == 'left' else -distance)
                return lambda: d.sh('input','swipe',str(x),str(y),str(end_x),str(end_y),'150')
    raise AssertionError('Assign a short volume gesture to an enabled primary handle')


def windows():
    result = {}
    for block in d.sh('dumpsys','window','windows').split('  Window #')[1:]:
        if 'isVisible=true' not in block:
            continue
        header = block.splitlines()[0]
        if 'ty=VOLUME_OVERLAY' in block:
            result['system'] = header.split('Window{',1)[1].split()[0]
        elif 'Color Hand Operation 音量控制}' in header:
            result['buttons'] = header.split('Window{',1)[1].split()[0]
    return result


def pair():
    deadline = time.monotonic() + 2
    while time.monotonic() < deadline:
        state = windows()
        if 'system' in state and 'buttons' in state:
            return state
    raise AssertionError('System volume panel and CHO buttons did not both open')


def until_hidden():
    # WindowManager can retain the native surface after its final visible frame.
    # Compare accessible windows during dismissal, then check both surfaces detach.
    setting = d.sh('settings','get','secure','accessibility_interactive_ui_timeout_ms').strip()
    deadline = time.monotonic() + max(25, int(setting) / 1000 + 5 if setting.isdecimal() else 0)
    first_missing = {}
    while time.monotonic() < deadline:
        info = [s.strip() for s in d.sh('dumpsys','accessibility').splitlines() if s.strip().startswith('A11yWindow[')]
        system = any('type=TYPE_SYSTEM,' in s and ('音量控件,' in s or ' volume controls,' in s) for s in info)
        buttons = any('type=TYPE_ACCESSIBILITY_OVERLAY,' in s and ('title=弹出式窗口,' in s or 'title=Popup window,' in s) for s in info)
        now = time.monotonic()
        for label, visible in [('system',system),('buttons',buttons)]:
            if not visible:
                first_missing.setdefault(label,now)
            else:
                first_missing.pop(label,None)
        if not system and not buttons:
            delta = first_missing['buttons'] - first_missing['system']
            assert abs(delta) < .35, ('accessible panels closed separately', delta)
            if not windows():
                return
        time.sleep(.04)
    raise AssertionError('Volume windows did not close')


def run():
    before = d.vol()
    try:
        d.panel_settings('虚拟触控板')
        tree = d.ui()
        back = next(n for n in tree.iter('node') if n.get('package') == d.P
                    and n.get('content-desc') == '返回' and d.visible(n))
        trigger = volume_gesture()
        trigger()
        pair()
        d.tap(back)
        until_hidden()
        d.wait_node('面板与工具')
        assert not any(n.get('content-desc') == '预览触控板' and d.visible(n) for n in d.ui().iter('node'))

        trigger()
        first = pair()['buttons']
        time.sleep(1)
        trigger()
        assert pair()['buttons'] == first, 'Repeated gesture replaced the volume buttons'
        time.sleep(1.8)
        d.sh('input','keyevent','25')
        time.sleep(1.8)
        state = windows()
        assert state.get('buttons') == first and 'system' in state, state
        until_hidden()
        print('PASS outside tap reaches the Activity, repeated gestures reuse the window, buttons follow SystemUI dismissal')
    finally:
        if d.vol() != before:
            d.sh('cmd','media_session','volume','--stream','3','--set',str(before))
        d.main_home()


if __name__ == '__main__':
    run()
