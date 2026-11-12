"""Verify outside taps against the real Back button underneath each tool preview."""
import time

import device as d


def marker(name):
    return next((n for n in d.ui().iter('node') if n.get('package') == d.P and d.visible(n)
                 and name in (n.get('text'), n.get('content-desc'))), None)


def check(title, opened, close=None):
    d.panel_settings(title)
    tree = d.ui()
    back = next(n for n in tree.iter('node') if n.get('package') == d.P
                and n.get('content-desc') == '返回' and d.visible(n))
    preview = d.PREVIEWS[title]
    d.click_item(preview, True)
    time.sleep(.85)
    assert marker(opened) is not None, title
    try:
        # Use coordinates measured before the overlay opened. The OS may hide the
        # underlying Activity from the accessibility tree while a modal is focused.
        d.tap(back)
        time.sleep(.75)
        if close is None:
            assert marker(opened) is None and marker(preview) is not None, title
            d.tap(back)
            time.sleep(.55)
        else:
            assert marker(opened) is not None, title
        assert marker(preview) is None and marker('面板与工具') is not None, title
    finally:
        if close is not None and marker(opened) is not None:
            d.click(close)
            time.sleep(.6)


if __name__ == '__main__':
    prefs = d.preferences()
    try:
        check('快速工具', '媒体音量')
        check('小组件面板', '插件页面')
        check('任务切换器', '系统最近任务')
        check('虚拟导航键', '关闭虚拟导航键', '关闭虚拟导航键')
        check('虚拟触控板', '光标居中', '关闭')
    finally:
        d.main_home()
        assert d.preferences() == prefs
    print('PASS outside tap ownership for three temporary panels and two persistent tools')
