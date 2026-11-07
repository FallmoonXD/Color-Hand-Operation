"""Record the expanded home menu and check its background reveal on the OnePlus.

Requires the device probe, screen recording, FFmpeg, Pillow and enabled animations.
Leaves the recording and measurements in the local verification directory.
"""
import io
import json
import re
import subprocess
import time
import uuid

from PIL import Image
import device as d


def measure(video, row, screen):
    metadata = json.loads(subprocess.check_output([
        'ffprobe', '-v', 'error', '-select_streams', 'v:0', '-show_entries',
        'stream=width,height:frame=best_effort_timestamp_time', '-of', 'json', str(video)]))
    times = [float(f['best_effort_timestamp_time']) for f in metadata['frames']]
    w, h = (metadata['streams'][0][k] for k in ('width', 'height'))
    x, y, right, bottom = row
    # The trailing quarter covers the underlying switches, clear of the menu labels.
    left = round((x + .75 * (right - x)) * w / screen[0])
    width = round(right * w / screen[0]) - left
    top = round(y * h / screen[1])
    height = min(h - top, round(3 * (bottom - y) * h / screen[1]))
    raw = subprocess.check_output([
        'ffmpeg', '-v', 'fatal', '-i', str(video), '-vf', f'crop={width}:{height}:{left}:{top}:exact=1',
        '-fps_mode', 'passthrough', '-pix_fmt', 'rgb24', '-f', 'rawvideo', '-'])
    size = width * height * 3
    assert len(raw) == size * len(times) and len(times) > 3, 'Incomplete recording'
    first, last = raw[:size], raw[-size:]
    samples = []
    for y in range(0, height, 3):
        for x in range(0, width, 3):
            i = 3 * (y * width + x)
            delta = [last[i+k] - first[i+k] for k in range(3)]
            if sum(n*n for n in delta) > 85**2:
                samples.extend((i+k, n) for k, n in enumerate(delta))
    assert len(samples) >= 30, 'Not enough contrast behind this menu to measure a reveal'
    norm = sum(delta*delta for _, delta in samples)
    values = [sum((raw[j*size+i] - first[i]) * delta for i, delta in samples)/norm for j in range(len(times))]
    start = next(i for i, value in enumerate(values) if value > .04)
    opening = values[start:next((i for i in range(start,len(times)) if times[i]-times[start] > .40),len(times))]
    peak, drop = opening[0], 0
    for value in opening:
        drop = max(drop, peak - value)
        peak = max(peak, value)
    last_jump = max((b-a for a,b in zip(opening,opening[1:]) if a > .55 and b > .90), default=0)
    return dict(first_visible=opening[0], max_drop=drop, late_jump=last_jump, peak=peak,
                samples=len(samples)//3, frames=list(zip(times,values)))


def run():
    d.main_home()
    screen = Image.open(io.BytesIO(subprocess.check_output(d.A+['exec-out','screencap','-p']))).size
    assert screen[0] < screen[1], 'Run the home-menu recording in portrait'
    x = str(screen[0]//2)
    d.sh('input','swipe',x,str(screen[1]//4),x,str(5*screen[1]//6),'250')
    time.sleep(.6)
    setting = d.sh('settings','get','global','animator_duration_scale').strip()
    scale = float(setting) if setting != 'null' else 1
    assert scale == 1, 'Use normal animation speed for this timing check'
    anchor = d.wait_node('更多选项')
    d.B.mkdir(parents=True,exist_ok=True)
    video = d.B/'popup-reveal.mp4'
    remote = '/data/local/tmp/cho-popup-'+uuid.uuid4().hex+'.mp4'
    width = min(896,screen[0])//2*2
    height = round(width * screen[1]/screen[0]/2)*2
    recorder = subprocess.Popen(d.A+['shell','screenrecord','--size',f'{width}x{height}',
        '--bit-rate','16000000','--time-limit','4',remote],stdout=subprocess.DEVNULL,stderr=subprocess.PIPE)
    opened = False
    try:
        time.sleep(1)
        d.tap(anchor)
        row = d.bounds(d.wait_node('权限与后台运行'))
        opened = True
        error = recorder.communicate(timeout=10)[1]
        assert recorder.returncode == 0, error
        subprocess.run(d.A+['pull',remote,str(video)],check=True,capture_output=True)
    finally:
        if recorder.poll() is None:
            recorder.terminate()
            recorder.wait()
        d.sh('rm','-f',remote)
        if opened:
            d.sh('input','keyevent','4')
    report = measure(video,row,screen)
    (d.B/'popup-reveal.json').write_text(json.dumps(report,indent=2),encoding='utf-8')
    print({k:round(v,3) for k,v in report.items() if k!='frames'})
    assert report['peak'] > .95 and report['first_visible'] < .35, 'Menu appeared without fading in'
    assert report['max_drop'] < .12, 'Background flashed, then faded backwards'
    assert report['late_jump'] < .20, 'Blur snapped on at the end of the animation'


if __name__ == '__main__':
    run()
