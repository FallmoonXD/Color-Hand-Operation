"""Check trial navigation on an unlocked phone, using only existing settings."""
import json
import shlex
import time

import device as d


def back():
    d.sh('input', 'keyevent', '4')
    time.sleep(.4)


def set_query(field, value):
    command = shlex.join(['app_process', '/system/bin', 'probe.OverlayDump', 'set-text', field, value])
    d.sh('sh', '-c', 'CLASSPATH=/data/local/tmp/edgeflow-probe.jar ' + command)


def run():
    stores = ('edgeflow', 'edgeflow_launcher', 'replica_widgets', 'edgeflow_shortcuts')
    before = {name: d.preferences(name) for name in stores}
    d.main_home()
    d.click_item('面板与工具', True)
    tools = ('快速工具', '小组件面板', '快速启动', '任务切换器', '虚拟触控板', '虚拟导航键', '键盘快捷方式')
    for title in tools:
        d.click_item(title, True)
        d.wait_node(title)
        back()
        d.wait_node('面板与工具')

    handle = next(h for h in ('right', 'left') if before['edgeflow'].get(h + '_enabled', True))
    d.open_action_chooser(handle)
    set_query('搜索操作', '小组件面板')
    gear = d.wait_node('设置插件')
    d.tap(gear)
    d.wait_node('循环翻页')
    back()
    assert d.wait_node('设置插件').get('node-hash') == gear.get('node-hash')
    search = d.wait_node('搜索操作')
    assert search.get('text') == '小组件面板', 'Returning from the gear lost the filter'
    back()
    d.wait_node('短距离滑动')
    d.range_settings(handle)
    d.wait_node('触钮颜色')
    d.main_home()
    assert all(d.preferences(name) == values for name, values in before.items())
    result = dict(panel_routes=len(tools), filter_retained=True, gear_retained=True,
                  range_route=True, preferences_preserved=True)
    d.B.mkdir(parents=True, exist_ok=True)
    (d.B/'coui-navigation.json').write_text(json.dumps(result, indent=2), encoding='utf-8')
    print(result)


if __name__ == '__main__':
    run()
