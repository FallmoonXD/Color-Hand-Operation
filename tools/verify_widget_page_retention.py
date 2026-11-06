"""Verify a widget gesture/Back keeps Main's page nodes and all owned preferences.

Uses only already-enabled handles and an existing widgets mapping. It never
reassigns gestures, enables accessibility, writes preferences, or edits widgets.
Run while Main is on the page under investigation, or select --page explicitly.
The node check detects page reconstruction; it does not prove pixel-perfect motion.
"""
import argparse
import json
import math
import re
import time
import xml.etree.ElementTree as ET

import device as d


HANDLES = ('left', 'right', 'extra_left', 'extra_right', 'extra2_left', 'extra2_right')


def label(handle):
    prefix = '额外第二组' if handle.startswith('extra2_') else '额外' if handle.startswith('extra_') else ''
    return prefix + ('左侧手势区域' if handle.endswith('left') else '右侧手势区域')


def original_action(handle, direction, longer):
    if handle.startswith('extra'):
        return 'none'
    if longer:
        return {'up': 'widgets', 'down': 'quick_tools', 'straight': 'one_hand' if handle == 'left' else 'sidebar'}[direction]
    return {'up': 'volume_up', 'down': 'volume_down', 'straight': 'recents' if handle == 'left' else 'back'}[direction]


def gesture_plan(state, tree, width, height, density):
    """Read the same configured boundaries; refuse to invent or modify a mapping."""
    assert state.get('enabled', True), 'Gesture master is disabled; this test will not enable it'
    nodes = {n.get('content-desc'): n for n in tree.iter('node') if n.get('visible-to-user', 'true') == 'true'}
    windows = {h: d.bounds(nodes[label(h)]) for h in HANDLES if label(h) in nodes}
    for handle in reversed(HANDLES):
        if handle not in windows or not state.get(handle + '_enabled', not handle.startswith('extra')):
            continue
        for longer in (True, False):
            if longer and (not state.get(handle + '_long_enabled', True) or state.get('quick_action_short', False)):
                continue
            for direction in ('up', 'down', 'straight'):
                key = d.action_key(handle, direction, longer)
                if state.get(key, original_action(handle, direction, longer)) != 'widgets':
                    continue
                prefix = 'long' if longer else 'short'
                old = float(state.get('direction_angle_degrees', 30))
                if direction == 'straight':
                    angle = 0
                else:
                    inner = float(state.get(f'{prefix}_angle_{direction}_inner_degrees', old))
                    outer = float(state.get(f'{prefix}_angle_{direction}_outer_degrees', 80))
                    angle = (inner + outer) / 2 * (-1 if direction == 'up' else 1)
                short = float(state.get('trigger_dp', 39.43)) * density
                long_distance = float(state.get('long_trigger_dp', 87.43)) * density
                if longer:
                    distance = max(short + 24, long_distance + 32)
                else:
                    distance = short + max(2, min(24, (long_distance - short) / 3))
                left, top, right, bottom = windows[handle]
                x = (left + right) / 2
                dx = math.cos(math.radians(angle)) * distance * (1 if handle.endswith('left') else -1)
                dy = math.sin(math.radians(angle)) * distance
                if not 10 <= x + dx <= width - 10:
                    continue
                ranges = [(max(top + 4, 10 - dy), min(bottom - 4, height - 10 - dy))]
                # A later enabled segment on the same side has input-window priority.
                for peer in HANDLES[HANDLES.index(handle) + 1:]:
                    if peer not in windows or peer.endswith('left') != handle.endswith('left'):
                        continue
                    _, a, _, b = windows[peer]
                    ranges = [(lo, hi) for start, end in ranges for lo, hi in ((start, min(end, a - 4)), (max(start, b + 4), end)) if hi > lo]
                ranges = [(a, b) for a, b in ranges if b - a >= 4]
                if not ranges:
                    continue
                start, end = max(ranges, key=lambda pair: pair[1] - pair[0])
                y = (start + end) / 2
                duration = max(450, int(state.get('long_delay_ms', 300)) + 250) if longer else 160
                return {'mapping': key, 'handle': handle, 'direction': direction, 'long': longer,
                        'from': [round(x), round(y)], 'to': [round(x + dx), round(y + dy)],
                        'duration_ms': duration, 'angle_degrees': angle}
    raise AssertionError('No safe, already-enabled widgets gesture is available; no mapping was changed')


def all_preferences():
    result = {}
    names = d.sh('run-as', d.P, 'ls', 'shared_prefs').splitlines()
    for name in sorted(n for n in names if n.endswith('.xml')):
        tree = ET.fromstring(d.sh('run-as', d.P, 'cat', 'shared_prefs/' + name))
        entries = {}
        for n in tree:
            value = sorted(child.text or '' for child in n) if n.tag == 'set' else n.text or '' if n.tag == 'string' else n.get('value')
            entries[n.get('name')] = {'type': n.tag, 'value': value}
        result[name] = entries
    return result


def preference_diff(before, after):
    changes = {}
    for store in set(before) | set(after):
        a, b = before.get(store, {}), after.get(store, {})
        keys = [key for key in set(a) | set(b) if a.get(key) != b.get(key)]
        if keys:
            changes[store] = sorted(keys)
    return changes


def page_snapshot(tree):
    roots = [n for n in tree if n.get('package') == d.P and n.get('visible-to-user', 'true') == 'true']
    assert roots, 'No application page root'
    root = max(roots, key=lambda n: max(0, d.bounds(n)[2] - d.bounds(n)[0]) * max(0, d.bounds(n)[3] - d.bounds(n)[1]))
    records, counts, scrolls = {}, {}, []
    for node in root.iter('node'):
        if node.get('package') != d.P or node.get('visible-to-user', 'true') != 'true':
            continue
        bounds = d.bounds(node)
        if bounds[2] <= bounds[0] or bounds[3] <= bounds[1]:
            continue
        if node.get('scrollable') == 'true':
            scrolls.append({'class': node.get('class'), 'node': node.get('node-hash'), 'bounds': bounds})
        text, desc = node.get('text', ''), node.get('content-desc', '')
        if not text and not desc:
            continue
        signature = (node.get('class'), text, desc)
        occurrence = counts.get(signature, 0)
        counts[signature] = occurrence + 1
        key = json.dumps([*signature, occurrence], ensure_ascii=False)
        records[key] = {'node': node.get('node-hash'), 'bounds': bounds, 'checked': node.get('checked'), 'state': node.get('state-description')}
    assert len(records) >= 2 and all(n['node'] is not None for n in records.values()), 'Need the current OverlayDump probe with node-hash evidence'
    assert not any('正在读取' in key for key in records), 'Wait for the application page to finish loading before retention verification'
    return {'window': root.get('window-id'), 'root': root.get('node-hash'), 'nodes': records, 'scrolls': scrolls}


def stable_page(timeout=10):
    deadline = time.monotonic() + timeout
    previous = None
    while time.monotonic() < deadline:
        try:
            snapshot = page_snapshot(d.ui())
        except AssertionError:
            time.sleep(.2)
            continue
        if snapshot == previous:
            return snapshot
        previous = snapshot
        time.sleep(.2)
    raise AssertionError('Main page did not settle before widget gesture')


def await_activity(fragment, timeout=8):
    deadline = time.monotonic() + timeout
    while time.monotonic() < deadline:
        resumed = d.resumed()
        if fragment in resumed:
            return resumed
        time.sleep(.15)
    raise AssertionError('Activity did not resume: ' + fragment)


def self_test():
    tree = ET.fromstring('<hierarchy><node content-desc="右侧手势区域" visible-to-user="true" bounds="[1220,360][1264,2700]" /></hierarchy>')
    state = {'enabled': True, 'right_enabled': True, 'action_right_up_long': 'widgets'}
    original = dict(state)
    plan = gesture_plan(state, tree, 1264, 2780, 3.5)
    assert plan['mapping'] == 'action_right_up_long' and plan['to'][0] < plan['from'][0] and plan['to'][1] < plan['from'][1]
    assert state == original
    state['quick_action_short'] = True
    try:
        gesture_plan(state, tree, 1264, 2780, 3.5)
    except AssertionError:
        pass
    else:
        raise AssertionError('Must not overwrite configuration to manufacture a long gesture')
    assert preference_diff({'edgeflow.xml': {'x': 1}}, {'edgeflow.xml': {'x': 1}}) == {}
    assert preference_diff({'edgeflow.xml': {'x': 1}}, {'edgeflow.xml': {'x': 2}}) == {'edgeflow.xml': ['x']}
    print('PASS 4 offline widget-retention helper checks; no device accessed')


def run(args):
    report = {'scope': 'widget gesture / Back, Main node identity and owned preferences', 'page': args.page}
    d.B.mkdir(parents=True, exist_ok=True)
    baseline = all_preferences()
    assert 'layout_v3' in baseline.get('replica_widgets.xml', {}), 'Widget layout must already be initialized; this test will not migrate it'
    try:
        if args.page != 'current':
            d.main_link({'gestures': '手势设置', 'widgets': '小组件设置', 'exceptions': '应用例外', 'launcher': '快速启动设置'}[args.page])
        await_activity(d.P + '/.MainActivity')
        before = stable_page()
        metrics = d.sh('wm', 'size');width, height = map(int, re.findall(r'(\d+)x(\d+)', metrics)[-1])
        density = int(re.findall(r'density:\s*(\d+)', d.sh('wm', 'density'))[-1]) / 160
        plan = gesture_plan(d.preferences(), d.ui(), width, height, density)
        report['gesture'] = plan
        d.sh('input', 'swipe', *(str(n) for n in plan['from'] + plan['to']), str(plan['duration_ms']))
        report['widget_resumed'] = await_activity('replica.WidgetActivity')
        time.sleep(.45)
        d.sh('input', 'keyevent', '4')
        report['main_resumed'] = await_activity(d.P + '/.MainActivity')
        after = stable_page()
        report['before'] = before;report['after'] = after
        report['main_window_preserved'] = before['window'] == after['window']
        report['page_nodes_preserved'] = before['nodes'] == after['nodes'] and before['scrolls'] == after['scrolls']
        assert report['main_window_preserved'], 'Main window was recreated during widget gesture/Back'
        assert report['page_nodes_preserved'], 'Main page nodes, viewport or control state changed during widget gesture/Back'
        report['passed'] = True
    except Exception as error:
        report['passed'] = False;report['error'] = str(error)
        raise
    finally:
        # Close only our still-open widget panel; do not navigate or reset arbitrary UI.
        if 'replica.WidgetActivity' in d.resumed():
            d.sh('input', 'keyevent', '4');time.sleep(.4)
        changes = preference_diff(baseline, all_preferences())
        report['preferences_unchanged'] = not changes;report['preference_changes'] = changes
        if changes:
            report['passed'] = False
        (d.B / 'widget-page-retention-validation.json').write_text(json.dumps(report, ensure_ascii=False, indent=2), encoding='utf-8')
        assert not changes, 'Unexpected preference changes: ' + json.dumps(changes, ensure_ascii=False)
    print(json.dumps({k: v for k, v in report.items() if k not in ('before', 'after')}, ensure_ascii=False))


if __name__ == '__main__':
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--page', choices=('current', 'gestures', 'widgets', 'exceptions', 'launcher'), default='current')
    parser.add_argument('--self-test', action='store_true', help='Run only pure helper checks; never access ADB')
    options = parser.parse_args()
    self_test() if options.self_test else run(options)
