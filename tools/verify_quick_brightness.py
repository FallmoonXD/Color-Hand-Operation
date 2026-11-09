"""Compare the saved brightness, DisplayPowerState and panel driver on a PKR110."""
import re
import time

import device as d


KEYS = ('screen_brightness', 'screen_auto_brightness_adj', 'screen_brightness_mode')


def setting(key):
    return d.sh('settings', 'get', 'system', key).strip()


def backlight():
    # actual_brightness is always 0 on this driver; brightness is the driver's requested backlight.
    state = d.sh('dumpsys', 'display')
    applied = float(re.search(r'\bmActualBacklight=([\d.]+)', state)[1])
    driver = int(d.sh('cat', '/sys/class/backlight/panel0-backlight/brightness').strip())
    return applied, driver


def exercise(mode, low, high):
    d.sh('settings', 'put', 'system', 'screen_brightness_mode', str(mode))
    d.panel_settings('快速工具')
    d.click_item('预览快速工具', True)
    slider = next(n for n in d.ui().iter('node') if d.visible(n) and n.get('content-desc', '').startswith('屏幕亮度 '))
    left, top, right, bottom = d.bounds(slider)
    y = str((top + bottom) // 2)
    results = []

    def touch(action, fraction):
        x = str(round(left + (right - left) * fraction))
        d.sh('input', 'motionevent', action, x, y)

    def settled(fraction):
        expected = low + (high - low) * fraction
        deadline = time.monotonic() + 1.4
        while True:
            applied, driver = backlight()
            if abs(applied - expected) < (high - low) * .014:
                return applied, driver
            assert time.monotonic() < deadline, (mode, fraction, expected, applied, driver)
            time.sleep(.025)

    try:
        touch('DOWN', .20)
        results.append(settled(.20))
        touch('MOVE', .34)
        results.append(settled(.34))
        touch('UP', .34)
        time.sleep(.6)
        applied, driver = backlight()
        saved = int(setting('screen_brightness'))
        assert abs(saved - results[-1][0]) < 25 and abs(saved - applied) < 25, (results, saved, applied)
        assert results[1][1] > results[0][1] + 100 and setting('screen_brightness_mode') == str(mode), results
        adjustment = setting('screen_auto_brightness_adj')
        touch('DOWN', .23)
        settled(.23)
        touch('CANCEL', .23)
        time.sleep(.5)
        dump = d.sh('dumpsys', 'display')
        assert 'mTemporaryScreenBrightness:NaN' in dump and 'mTemporaryAutoBrightnessAdjustment=NaN' in dump
        assert setting('screen_auto_brightness_adj') == adjustment, 'Canceled drag changed the saved adjustment'
        print('PASS', 'auto' if mode else 'manual', 'drag', results, 'saved', saved, 'driver', driver, 'cancel released preview', flush=True)
    finally:
        touch('CANCEL', .34)
        d.sh('input', 'keyevent', '4')
        d.main_home()


if __name__ == '__main__':
    initial = {key: setting(key) for key in KEYS}
    low = int(d.sh('getprop', 'sys.oplus.multibrightness.min'))
    high = int(d.sh('getprop', 'sys.oplus.multibrightness'))
    assert 0 <= low < high <= 65535
    try:
        for mode in (0, 1):
            exercise(mode, low, high)
    finally:
        for key, value in initial.items():
            if value == 'null':
                d.sh('settings', 'delete', 'system', key)
            else:
                d.sh('settings', 'put', 'system', key, value)
        assert all(setting(key) == initial[key] for key in KEYS[1:])
