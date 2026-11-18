"""Run gesture recovery checks on an unlocked phone with the signed LSP fixture installed."""
import copy
import json
import shlex
import sys
import time
from concurrent.futures import ThreadPoolExecutor
from pathlib import Path

import device as d
from lsp_control import request
from verify_lsp_configured import swipe
from verify_lsp_input import PACKAGE, fixture
from verify_lsp_unlock import status


def wait_for(predicate, seconds=10):
    deadline = time.monotonic() + seconds
    while time.monotonic() < deadline:
        state = status()
        if predicate(state):
            return state
        time.sleep(.05)
    raise AssertionError(state)


def sync(profile):
    # The foreground trial must stay resumed. ColorOS can freeze our background fixture.
    args = ['content', 'call', '--uri', 'content://dev.edgeflow.lsp.persistent',
            '--method', 'sync', '--arg', json.dumps(profile, separators=(',', ':'))]
    result = d.sh('su', '-c', shlex.join(args))
    if 'state=' not in result:
        raise AssertionError('The controller did not acknowledge the temporary profile')


def test_profile(original):
    profile = copy.deepcopy(original)
    values = profile['values']
    values.update(enabled=True, trigger_dp=35, disabled_apps=[], excluded_packages='',
                  hide_home=False, hide_keyboard=False, hide_notifications=False)
    for side in ('left', 'right'):
        values.update({side+'_enabled': True, side+'_long_enabled': False,
                       side+'_handle_width_dp': 16, side+'_handle_start_fraction': .2,
                       side+'_handle_end_fraction': .8})
        for direction in ('up', 'straight', 'down'):
            slot = f'action_{side}_{direction}_short'
            values[slot] = 'back'
            values.pop(f'app_override/dev.edgeflow/{slot}', None)
            values[f'app_override/{PACKAGE}/{slot}'] = (
                f'app:{PACKAGE}/{PACKAGE}.RemovedActivity' if side == 'right' else 'back')
    return profile


def check(output):
    assert status().get('active'), 'Start with the owner unlocked and gestures already running'
    fixture()
    original = json.loads(request('snapshot', module=d.P)['profile'])
    profile = test_profile(original)
    off = copy.deepcopy(profile)
    off['values']['enabled'] = False
    width, height = d.current_display_size()
    records = {}
    try:
        sync(profile)
        time.sleep(.5)
        before = wait_for(lambda s: s.get('active'))
        swipe('right', distance=280)
        rejected = wait_for(lambda s: s.get('rejected_actions', 0) > before['rejected_actions'])
        assert rejected['active'] and rejected['last_action_error'] and not rejected['error']
        swipe('left', distance=280)
        after = status()
        assert PACKAGE+'/.ProbeActivity' in d.resumed()
        assert after['actions'] == before['actions']+1 and after['session_starts'] == before['session_starts']
        records['missing_component'] = dict(before=before, rejected=rejected, after=after)

        sync(off)
        wait_for(lambda s: s.get('persistent_state') == 'MASTER_OFF')
        d.main_home()
        d.click_item('手势设置')
        d.click_item('手势试划')
        trial = d.P+'/.GestureTrialActivity'
        assert trial in d.resumed() and not status().get('active')
        contacts = []
        records['trial_contacts'] = contacts
        with ThreadPoolExecutor(max_workers=1) as worker:
            for delay in (.25, .42, .15, .72, .91, .31):
                sync(off)
                wait_for(lambda s: s.get('persistent_state') == 'MASTER_OFF' and not s.get('trial_generation'))
                time.sleep(delay)
                update = worker.submit(sync, profile)
                before = wait_for(lambda s: s.get('active'))
                d.sh('input', 'swipe', str(width-10), str(height//2), str(width-290), str(height//2), '80')
                after = status()
                update.result()
                assert trial in d.resumed() and after['actions'] == before['actions']
                assert after['claims'] == before['claims']+1
                wait_for(lambda s: s.get('trial_generation', 0)>0)
                swipe('right', distance=280)
                texts = [node.get('text', '') for node in d.ui().iter('node') if node.get('package') == d.P]
                result = next((text for text in texts if '短滑' in text and '返回' in text), None)
                assert result and status()['actions'] == before['actions'], texts
                contacts.append(dict(before=before, after=after, result=result))
                if len(contacts)>=2 and any(not row['before']['trial_generation'] and
                        not row['after']['trial_generation'] for row in contacts):
                    break
            else:
                raise AssertionError('Did not sample a full pre-registration contact')
        d.sh('input', 'keyevent', '4')
        wait_for(lambda s: s.get('active') and not s.get('trial_generation'))
        assert d.P+'/.MainActivity' in d.resumed()
        before = status()
        swipe('right', distance=280)
        after = status()
        assert after['actions'] == before['actions']+1
        records['after_trial'] = after
    finally:
        sync(original)
        wait_for(lambda s: s.get('active'))
        output.write_text(json.dumps(records, indent=2, ensure_ascii=False), encoding='utf8')


if __name__ == '__main__':
    output = Path(sys.argv[1])
    if output.exists():
        raise FileExistsError(output)
    check(output)
    print('PASS missing component, trial recovery, pre-registration contact, normal action after exit')
