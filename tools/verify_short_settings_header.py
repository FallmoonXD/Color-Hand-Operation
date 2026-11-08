"""Verify the installed CHO toolbar and a short page using real touch contacts on a portrait phone."""
import io
import json
import time
import subprocess
from PIL import Image, ImageStat
import device as d

HELP = ('在屏幕左侧或右侧添加手势触钮，以便单手轻松控制手机。 '
        '您可以自定义从各个触钮向不同方向滑动时要执行的操作。')
ROW = '显示任务数量'


def visible(tree, label, attr):
    return next(n for n in tree.iter('node')
                if n.get('package') == d.P and n.get(attr) == label and d.visible(n))


def button_bounds(tree, label):
    target = visible(tree, label, 'content-desc')
    icon = next(n for n in target.iter('node') if n.get('class') == 'android.widget.ImageView')
    return d.bounds(target), d.bounds(icon)


def screenshot(name):
    payload = subprocess.check_output(d.A + ['exec-out', 'screencap', '-p'])
    (d.B / 'short-settings-header' / (name + '.png')).write_bytes(payload)
    return Image.open(io.BytesIO(payload)).convert('RGB')


def pixel(image, x, y):
    return ImageStat.Stat(image.crop((x-2, y-2, x+3, y+3))).median


def pull(label, x, y, travel, anchor=ROW, attr='content-desc'):
    before = d.bounds(visible(d.ui(), anchor, attr))
    try:
        d.sh('input', 'motionevent', 'DOWN', str(x), str(y))
        for step in range(1, 6):
            d.sh('input', 'motionevent', 'MOVE', str(x), str(y - travel*step//5))
        during = d.bounds(visible(d.ui(), anchor, attr))
        image = screenshot(label + '-held')
    finally:
        d.sh('input', 'motionevent', 'UP', str(x), str(y-travel))
    time.sleep(1)
    tree = d.ui()
    returned = d.bounds(visible(tree, anchor, attr))
    assert before[1]-during[1] > travel//8 and returned == before, (label, before, during, returned)
    assert not any(n.get('package')==d.P and n.get('text')=='取消' and d.visible(n) for n in tree.iter('node')), 'Drag opened an option'
    return before[1]-during[1], image


def run():
    (d.B / 'short-settings-header').mkdir(parents=True, exist_ok=True)
    initial = d.preferences()
    try:
        d.main_home()
        width, height = d.current_display_size()
        assert width < height, 'Run this check in portrait with system animations enabled'
        d.sh('input','swipe',str(width//2),str(height//4),str(width//2),str(3*height//4),'350')
        time.sleep(.5)
        tree = d.ui()
        title = d.bounds(visible(tree, 'Color Hand Operation', 'content-desc'))
        more, more_icon = button_bounds(tree, '更多选项')
        help_bounds = d.bounds(visible(tree, HELP, 'text'))
        status = d.bounds(visible(tree, '已开启' if initial.get('enabled', True) else '已关闭', 'text'))
        assert title[2] <= more[0] and help_bounds[1] >= status[3]
        home = screenshot('home')
        d.click('更多选项'); d.wait_node('关于'); d.sh('input','keyevent','4')

        d.panel_settings('任务切换器')
        tree = d.ui()
        secondary = d.bounds(visible(tree, '任务切换器', 'text'))
        back, back_icon = button_bounds(tree, '返回')
        assert title[1::2] == secondary[1::2] and more[1::2] == back[1::2]
        assert more_icon[1::2] == back_icon[1::2] and more_icon[2]-more_icon[0] == back_icon[2]-back_icon[0]
        row = d.bounds(visible(tree, ROW, 'content-desc'))
        idle = screenshot('short-rest')
        x = (row[0]+row[2])//2
        y = (row[1]+row[3])//2
        # On a light page, sample the spot shadow below the 52 dp bar as well as below
        # the icon. A clipped shadow stops at the bar even though the circle itself looks fine.
        if min(pixel(idle,x,3*height//4)) > 175 and d.sh('settings','get','system','system_material_stroke_enable').strip()=='1':
            for shot,icon,side in ((home,more_icon,-1),(idle,back_icon,1)):
                cx=(icon[0]+icon[2])//2
                sy=secondary[3]+max(1,(icon[3]-icon[1])//20)
                background=pixel(shot,cx+side*(icon[2]-icon[0]),sy)
                shadow=pixel(shot,cx,sy)
                assert min(b-s for b,s in zip(background,shadow))>=2, ('Toolbar shadow clipped',background,shadow)
        band_y = secondary[3] + (row[1]-secondary[3])//2
        base = pixel(idle, x, 3*height//4)
        assert max(abs(a-b) for a,b in zip(pixel(idle,x,secondary[1]+2),base)) <= 2, 'Status end is tinted'
        assert pixel(idle,x,band_y) != base, 'Resting gradient vanished on a short page'
        travel = row[3]-row[1]
        item_distance, item = pull('option', x, y, travel)
        blank_distance, blank = pull('blank', x, 3*height//4, travel)
        assert abs(item_distance-blank_distance) <= 4, (item_distance, blank_distance)
        assert max(abs(a-b) for a,b in zip(pixel(item,x,band_y),pixel(blank,x,band_y))) <= 2
        assert pixel(item,x,band_y) != pixel(idle,x,band_y), 'The fade did not respond to the pull'
        returned = screenshot('short-return')
        assert pixel(returned,x,band_y) == pixel(idle,x,band_y), 'Resting gradient missing after release'
        d.click_item(ROW, True); d.wait_node('取消'); d.click('取消')
        d.panel_settings('虚拟触控板')
        slider = next(n for n in d.ui().iter('node') if n.get('class')=='android.widget.SeekBar' and d.visible(n))
        left,top,right,bottom = d.bounds(slider)
        slider_distance,_ = pull('slider', (left+right)//2, (top+bottom)//2, travel, '预览触控板')
        slider_name = slider.get('content-desc')
        initial_progress = slider.get('range-current')
        sy = (top+bottom)//2
        sx,ex = left+(right-left)//3, right-(right-left)//5
        try:
            d.sh('input','motionevent','DOWN',str(sx),str(sy))
            d.sh('input','motionevent','MOVE',str((sx+ex)//2),str(sy))
            d.sh('input','motionevent','MOVE',str(ex),str(sy))
            dragged = visible(d.ui(),slider_name,'content-desc')
            assert dragged.get('range-current') != initial_progress and d.bounds(dragged)==(left,top,right,bottom)
        finally:
            d.sh('input','motionevent','CANCEL',str(ex),str(sy))
        assert visible(d.ui(),slider_name,'content-desc').get('range-current') == initial_progress
        report = dict(title=title, secondary=secondary, more_icon=more_icon, back_icon=back_icon,
                      option_pull_px=item_distance, blank_pull_px=blank_distance, slider_pull_px=slider_distance,
                      resting_pixel=pixel(idle,x,band_y), held_pixel=pixel(item,x,band_y))
        (d.B / 'short-settings-header' / 'results.json').write_text(json.dumps(report,indent=2),encoding='utf8')
        print(report)
    finally:
        d.main_home()
        assert d.preferences() == initial, 'Dragging a row changed its setting'


if __name__ == '__main__':
    run()
