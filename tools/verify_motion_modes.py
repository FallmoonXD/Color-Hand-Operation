"""Exercise window timing, input and cancellation on the connected phone.

Use an unlocked PKR110 in portrait with the device probe installed. Restores
animator scale and rotation in finally blocks. The two optional gesture tests
temporarily use a primary short straight slot and restore it through the UI.
"""
import argparse
import datetime
import json
import re
import subprocess
import time
from pathlib import Path

import device as d
from verify_panel_exit import record, measure


def setting(namespace, key):
    return d.sh('settings', 'get', namespace, key).strip()


def restore(namespace, key, value):
    if value == 'null':
        d.sh('settings', 'delete', namespace, key)
    else:
        d.sh('settings', 'put', namespace, key, value)
    assert setting(namespace, key) == value


def scale_checks(out):
    key = 'animator_duration_scale'
    original = setting('global', key)
    try:
        for scale in (0, .5, 2):
            d.sh('settings', 'put', 'global', key, str(scale))
            time.sleep(.6)
            video = out / f'quick-tools-scale-{scale}.mp4'
            ms = record('快速工具', 'outside', video)
            if scale:
                result = measure(video)
                expected = 107 * scale
                assert abs(result['visible_ms'] - expected) < 38, result
                assert 150 * scale - 20 <= ms <= 150 * scale + 48, ms
            else:
                assert ms < 55, ms
                result = {}
            result['window_exit_ms'] = ms
            video.with_suffix('.json').write_text(json.dumps(result, indent=2), encoding='utf-8')
            print('PASS animator scale', scale, 'window exit', ms, 'ms', flush=True)
            d.main_home()
            d.click('更多选项')
            d.wait_node('关于')
            d.sh('input', 'keyevent', '4')
            d.wait_node('更多选项')
            assert not any(n.get('text') == '关于' and d.visible(n) for n in d.ui().iter('node'))
    finally:
        restore('global', key, original)


def rotation_check():
    auto, angle = (setting('system', k) for k in ('accelerometer_rotation', 'user_rotation'))
    initial = d.current_display_size()
    assert initial[0] < initial[1]
    try:
        d.panel_settings('虚拟导航键')
        d.click_item(d.PREVIEWS['虚拟导航键'], True)
        d.wait_node('关闭虚拟导航键')
        d.sh('wm', 'user-rotation', 'lock', '1')
        deadline = time.monotonic() + 6
        while d.current_display_size()[0] < d.current_display_size()[1]:
            assert time.monotonic() < deadline, 'Phone did not rotate'
            time.sleep(.1)
        d.wait_node('关闭虚拟导航键')
        d.click('关闭虚拟导航键')
        time.sleep(.7)
        assert not any(n.get('content-desc')=='关闭虚拟导航键' and d.visible(n) for n in d.ui().iter('node'))
        d.find_item(d.PREVIEWS['虚拟导航键'],True)
        d.panel_settings('快速工具')
        d.click_item(d.PREVIEWS['快速工具'], True)
        d.wait_node('媒体音量')
        d.sh('input', 'keyevent', '4')
        d.wait_node(d.PREVIEWS['快速工具'])
    finally:
        restore('system', 'user_rotation', angle)
        restore('system', 'accelerometer_rotation', auto)
        time.sleep(.7)
    assert d.current_display_size() == initial
    print('PASS rail rotation, close, and landscape tools', flush=True)


def appearance_check():
    font=setting('system','font_scale')
    blur=setting('system','system_material_blur_enable')
    night=re.search(r'Night mode:\s*(\w+)',d.sh('cmd','uimode','night'),re.I).group(1)
    try:
        d.sh('cmd','uimode','night','no' if night=='yes' else 'yes')
        d.sh('settings','put','system','font_scale','1.4')
        d.sh('settings','put','system','system_material_blur_enable','0')
        time.sleep(.7)
        d.main_home()
        d.click('更多选项')
        d.click('恢复默认设置')
        d.wait_node('恢复默认设置？')
        d.click('取消')
        d.wait_node('更多选项')
        d.panel_settings('键盘快捷方式')
        d.click_item(d.PREVIEWS['键盘快捷方式'],True)
        d.wait_node('关闭键盘快捷方式')
        d.click('关闭键盘快捷方式')
        time.sleep(.4)
        d.wait_node(d.PREVIEWS['键盘快捷方式'])
        d.panel_settings('快速工具')
        d.click_item(d.PREVIEWS['快速工具'],True)
        d.wait_node('媒体音量')
        d.sh('input','keyevent','4')
        time.sleep(.3)
        d.wait_node(d.PREVIEWS['快速工具'])
    finally:
        restore('system','font_scale',font)
        restore('system','system_material_blur_enable',blur)
        d.sh('cmd','uimode','night',night)
        assert re.search(r'Night mode:\s*'+night+r'\b',d.sh('cmd','uimode','night'),re.I)
        d.main_home()
    print('PASS theme, large font, unblurred popup, center dialog, bottom sheet and tools',flush=True)


def notification_order():
    d.panel_settings('快速工具')
    d.click_item(d.PREVIEWS['快速工具'],True)
    tile=d.wait_node('通知栏')
    pid=d.sh('pidof','-s',d.P).strip()
    monitor=subprocess.Popen(d.A+['logcat','-T','1','--pid',pid,'-v','threadtime','-s','ReplicaTools:I'],
                             stdout=subprocess.PIPE,encoding='utf-8',errors='replace')
    try:
        d.tap(tile)
        time.sleep(1)
        shade=d.sh('dumpsys','activity','service','com.android.systemui')
    finally:
        monitor.terminate()
        log,_=monitor.communicate()
        d.sh('cmd','statusbar','collapse')
    stamp=r'(\d\d-\d\d \d\d:\d\d:\d\d\.\d+)'
    event_time=lambda s:datetime.datetime.strptime('2000-'+s,'%Y-%m-%d %H:%M:%S.%f')
    click=event_time(re.findall(stamp+r'.*ReplicaTools: tool=4\s*$',log,re.M)[-1])
    removal=min(t for t in map(event_time,re.findall(stamp+r'.*ReplicaTools: removed',log)) if t>=click)
    opened=min(t for t in map(event_time,re.findall(stamp+r' D systemui\.shadewindow: Setting isExpanded to true: .*?panelVisible true',shade)) if t>=click)
    assert removal<=opened and (opened-removal).total_seconds()<1
    d.wait_node(d.PREVIEWS['快速工具'])
    print('PASS notification shade opened after quick tools retired',flush=True)


def gesture_checks(side):
    initial = d.preferences()
    label = '左侧手势区域' if side == 'left' else '右侧手势区域'

    def gesture():
        l,t,r,b = d.bounds(d.wait_node(label))
        w,_ = d.current_display_size()
        x,y = (l+r)//2,t+(b-t)//3
        distance = round(.18*w) * (1 if side == 'left' else -1)
        d.sh('input', 'swipe', str(x), str(y), str(x+distance), str(y), '160')

    def about():
        d.main_home()
        d.click('更多选项')
        d.click('关于')
        d.wait_node('GitHub 主页')

    d.main_home()
    d.click_item('手势设置',True)
    d.click_item('手势试划',True)
    gesture()
    d.wait_node('判定数据')
    assert any('短滑 · ' in n.get('text','') for n in d.ui().iter('node') if n.get('package')==d.P)
    print('PASS real edge input in trial', flush=True)
    with d.preserved_action(side):
        d.choose_action('屏幕自由移动',side)
        about()
        w,h = d.current_display_size()
        for move in (True, False):
            gesture()
            d.wait_node('屏幕自由移动：拖动平移画面，抬手点击映射位置')
            x,y = w//2,round(h*.84)
            if move:
                d.sh('input','swipe',str(x),str(y),str(x+w//10),str(y),'180')
            else:
                d.sh('input','tap',str(x),str(y))
            time.sleep(1)
            assert not any('屏幕自由移动：' in n.get('content-desc','') for n in d.ui().iter('node'))
            d.wait_node('GitHub 主页')
        print('PASS screen translation return, stationary tap and foreground retention', flush=True)
        d.choose_action('屏幕放大镜',side)
        about()
        gesture()
        d.wait_node('放大镜控制区：滑动移动放大视野')
        d.click('关闭')
        time.sleep(.4)
        assert not any('放大镜控制区' in n.get('content-desc','') for n in d.ui().iter('node'))
        d.wait_node('GitHub 主页')
    assert d.preferences()==initial, 'Gesture tests changed saved configuration'
    print('PASS magnifier close and configuration restoration', flush=True)


if __name__ == '__main__':
    parser=argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--handle',choices=('left','right'))
    parser.add_argument('--notification-tile',action='store_true',help='Verify the existing notification tile using PKR110 SystemUI.')
    parser.add_argument('--output',type=Path,default=d.B/'motion-modes')
    args=parser.parse_args()
    args.output.mkdir(parents=True,exist_ok=True)
    try:
        scale_checks(args.output)
        rotation_check()
        appearance_check()
        if args.notification_tile:
            notification_order()
        if args.handle:
            gesture_checks(args.handle)
    finally:
        d.main_home()
