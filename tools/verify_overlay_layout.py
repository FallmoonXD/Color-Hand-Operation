"""Check touchpad and task-switcher layout on the connected device at its current rotation."""
import time

import device as d


def item(root, label):
    return next(n for n in root.iter('node') if label in (n.get('text'), n.get('content-desc')))


def panel(label):
    return next(root for root in d.ui() if any(label in (n.get('text'), n.get('content-desc'))
                                              for n in root.iter('node')))


def touchpad():
    d.main_link('虚拟触控板')
    d.wait_node('光标居中')
    time.sleep(.7)
    root = panel('光标居中')
    left, top, right, bottom = d.bounds(root)
    title = d.bounds(item(root, '虚拟触控板'))
    close = d.bounds(item(root, '关闭'))
    assert abs(title[0] + title[2] - left - right) <= 2, (title, (left, right))
    assert title[2] <= close[0] or close[2] <= title[0], (title, close)
    pad = d.bounds(item(root, '触控板：滑动移动光标，轻点点击，双指滑动滚动'))
    keys = [d.bounds(item(root, s)) for s in ('单击', '双击', '长按', '返回')]
    assert top < pad[1] < pad[3] < keys[0][1]
    assert all(left <= x < r <= right and top <= y < b <= bottom for x, y, r, b in keys)
    row = sorted(keys[:3])
    assert row[0][2] < row[1][0] and row[1][2] < row[2][0]
    d.tap(item(root, '关闭'))
    time.sleep(.6)


def task_gaps():
    root = panel('系统最近任务')
    title = d.bounds(item(root, '任务切换器'))
    footer = d.bounds(item(root, '系统最近任务'))
    scroll = next(n for n in root.iter('node') if n.get('class') == 'android.widget.ScrollView')
    x, top, right, bottom = d.bounds(scroll)
    above, below = top - title[3], footer[1] - bottom
    # The title is 46dp high. Both fixed gaps should be 8dp at any density.
    expected = (title[3] - title[1]) * 8 / 46
    assert abs(above - expected) <= 1 and abs(below - expected) <= 1, (above, below, expected)
    return x, top, right, bottom


def tasks():
    d.main_link('任务切换器')
    d.wait_node('系统最近任务')
    time.sleep(.8)
    x, top, right, bottom = task_gaps()
    center = str((x + right) // 2)
    d.sh('input', 'swipe', center, str(bottom - 8), center, str(top + 8), '280')
    time.sleep(.4)
    task_gaps()
    d.tap(item(panel('系统最近任务'), '刷新'))
    time.sleep(.6)
    task_gaps()
    left, y, _, _ = d.bounds(panel('系统最近任务'))
    d.sh('input', 'tap', str(max(0, left - 2)), str(max(0, y - 2)))
    time.sleep(.6)


if __name__ == '__main__':
    before = d.preferences()
    try:
        touchpad()
        tasks()
    finally:
        d.main_home()
        assert d.preferences() == before, 'Panel previews changed gesture settings'
    print('PASS touchpad title, control bounds, task viewport and fixed gaps')
