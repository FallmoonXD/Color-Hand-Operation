"""Measure fading, centered panels on the phone at normal animation speed.

Run from the project root with the device UI probe installed, FFmpeg on PATH,
and CHO's accessibility service enabled. No gesture mappings are changed.
"""
import argparse
import json
import re
import subprocess
import time
import uuid
from pathlib import Path

import device as d


def measure(video):
    metadata = subprocess.check_output([
        'ffprobe', '-v', 'error', '-select_streams', 'v:0', '-show_entries',
        'frame=best_effort_timestamp_time', '-of', 'json', str(video)])
    times = [float(frame['best_effort_timestamp_time']) for frame in json.loads(metadata)['frames']]
    pixels = subprocess.check_output([
        'ffmpeg', '-v', 'fatal', '-i', str(video), '-vf',
        'scale=96:212:flags=area,crop=96:180:0:16', '-pix_fmt', 'gray',
        '-fps_mode', 'passthrough', '-f', 'rawvideo', '-'])
    size = 96 * 180
    assert len(times) >= 3 and len(pixels) == len(times) * size, 'Incomplete recording'
    shown, hidden = pixels[:size], pixels[-size:]
    # Project each frame onto the difference between the settled panel and the
    # page beneath it. Ignore low-contrast pixels and both system bars.
    changed = [(i, a - b) for i, (a, b) in enumerate(zip(shown, hidden)) if abs(a - b) > 18]
    assert len(changed) > 100, 'No measurable change between the open and closed panel'
    norm = sum(delta * delta for _, delta in changed)
    values = [sum((pixels[j * size + i] - hidden[i]) * delta for i, delta in changed) / norm
              for j in range(len(times))]

    def crossing(threshold):
        for j in range(1, len(values)):
            if values[j - 1] > threshold >= values[j]:
                t = (values[j - 1] - threshold) / (values[j - 1] - values[j])
                return times[j - 1] + t * (times[j] - times[j - 1])
        raise AssertionError(f'No crossing at {threshold}')

    # An encoder may skip a long interval of stationary frames before the first
    # change. Do not interpolate across that gap to invent animation frames.
    first = next(j for j, value in enumerate(values) if value < .90)
    start = times[first] if times[first] - times[first - 1] > .06 else crossing(.90)
    end = crossing(.10)
    return {'visible_ms': round(1000 * (end - start)),
            'intermediate_frames': sum(.10 < v < .90 for v in values),
            'frames': list(zip(times, (round(x, 3) for x in values)))}


def tools_window_exit_ms(lines):
    events = re.findall(r'^\s*([\d.]+)\s+.*?\bI\s+ReplicaTools\s*:\s*(close requested|removed)\s*$', lines, re.M)
    assert [kind for _, kind in events[-2:]] == ['close requested', 'removed'], 'Expected an animated close'
    return round(1000 * (float(events[-1][0]) - float(events[-2][0])))


def record(title, action, path):
    d.panel_settings(title)
    # Android's three-button navigation also exposes a node named "返回".
    # Capture the underlying CHO toolbar button before the modal window opens.
    back = next(n for n in d.ui().iter('node') if n.get('package') == d.P
                and n.get('content-desc') == '返回' and d.visible(n))
    if action in ('left', 'right'):
        label = '左侧手势区域' if action == 'left' else '右侧手势区域'
        handle = d.bounds(d.wait_node(label))
    preview = d.PREVIEWS[title]
    d.click_item(preview, True)
    time.sleep(1.1)
    marker = {'快速工具':'媒体音量', '小组件':'页面菜单', '快速启动':'完成',
              '任务切换器':'系统最近任务', '虚拟触控板':'光标居中'}[title]
    def panel_visible():
        return any(n.get('package') == d.P and d.visible(n)
                   and (n.get('text') == marker or n.get('content-desc','').endswith(marker))
                   for n in d.ui().iter('node'))
    assert panel_visible(), 'Preview did not open: ' + title
    close = d.wait_node('关闭') if action == 'close' else None
    width, height = d.current_display_size()
    remote = '/data/local/tmp/cho-exit-' + uuid.uuid4().hex + '.mp4'
    recorder = subprocess.Popen(d.A + ['shell', 'screenrecord', '--size',
        f'632x{round(height * 632 / width / 2) * 2}', '--bit-rate', '10000000',
        '--time-limit', '3', remote], stdout=subprocess.DEVNULL)
    monitor = None
    try:
        if title == '快速工具':
            pid = d.sh('pidof', '-s', d.P).strip()
            monitor = subprocess.Popen(d.A + ['logcat', '-T', '1', '--pid', pid, '-v', 'epoch',
                '-s', 'ReplicaTools:I'], stdout=subprocess.PIPE, encoding='utf-8', errors='replace')
        time.sleep(1.25)
        if action == 'back':
            d.sh('input', 'keyevent', '4')
        elif action in ('left', 'right'):
            x = (handle[0] + handle[2]) // 2
            y = handle[1] + (handle[3] - handle[1]) // 3
            travel = round(width * .18) * (1 if action == 'left' else -1)
            d.sh('input', 'swipe', str(x), str(y), str(x + travel), str(y), '160')
        elif action == 'close':
            d.tap(close)
        else:
            d.tap(back)
        assert recorder.wait(timeout=8) == 0, 'Screen recording failed'
        subprocess.run(d.A + ['pull', remote, str(path)], check=True, capture_output=True)
    finally:
        if monitor is not None:
            monitor.terminate()
            log, _ = monitor.communicate()
        if recorder.poll() is None:
            recorder.terminate()
            recorder.wait()
        d.sh('rm', '-f', remote)
    assert not panel_visible(), 'Panel still open after dismissal: ' + title
    d.wait_node(preview)  # The same settings page must still be underneath.
    return tools_window_exit_ms(log) if monitor is not None else None


if __name__ == '__main__':
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--back-handle', choices=('left', 'right'),
                        help='Also test the specified primary handle already mapped to short straight Back.')
    parser.add_argument('--output', type=Path, default=d.B / 'panel-exit',
                        help='Directory for the recordings and measurements.')
    args = parser.parse_args()
    scale = d.sh('settings', 'get', 'global', 'animator_duration_scale').strip()
    assert scale in ('null', '1', '1.0'), 'Run at normal animation speed'
    out = args.output
    out.mkdir(parents=True, exist_ok=True)
    try:
        cases = [('快速工具', 'outside'), ('快速工具', 'back'), ('小组件', 'outside'),
                 ('任务切换器', 'outside'), ('快速启动', 'outside'), ('虚拟触控板', 'close')]
        if args.back_handle:
            cases += [(title, args.back_handle) for title in ('快速工具', '快速启动', '任务切换器')]
        for title, action in cases:
            path = out / f'{title}-{action}.mp4'
            window_exit_ms = record(title, action, path)
            result = measure(path)
            if window_exit_ms is not None:
                result['window_exit_ms'] = window_exit_ms
            path.with_suffix('.json').write_text(json.dumps(result, indent=2), encoding='utf-8')
            print(title, action, result['visible_ms'], 'ms,', result['intermediate_frames'], 'frames')
            # Native Settings coui_center_dialog_exit: alpha 1→0, 150 ms,
            # cubic (.3,0,1,1). The 90%→10% interval is 107 ms; allow two
            # display/encoder frames of quantization at 60 Hz.
            assert 75 <= result['visible_ms'] <= 141, 'Visible exit differs from the COUI center fade'
            assert result['intermediate_frames'] >= 4, 'Panel snapped shut'
            if 'window_exit_ms' in result:
                assert 135 <= result['window_exit_ms'] <= 195, 'Window did not complete the native 150 ms exit'
    finally:
        d.main_home()
