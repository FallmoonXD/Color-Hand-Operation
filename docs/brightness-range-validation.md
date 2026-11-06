# 亮度范围探测与失败路径

公开给其他固件前，删除了 `BrightnessControl` 的设备专属默认范围 2–4095。现在按顺序读取 `Settings.System.screen_brightness_range`、`OplusPowerManager` 的最小/最大值、Oplus 多位亮度属性。每个来源独立校验和处理异常，失败不会跳过后续来源；全部失败则没有可写范围。

校验要求为明确的两个整数、非负下限、上限大于下限且不超过 65535。异常、哨兵、溢出、反序和缺失均不产生默认值。未取得范围时滑条禁用，并提供状态描述与一次“无法确认亮度范围，暂不可调整”提示。当前值无法读取时同样禁用，不虚构位置。

`screen_auto_brightness_adj` 的原始整数语义只在 `OplusFeatureConfigManager.hasFeature("oplus.software.display.multibits_dimming_support")` 明确返回 true 时启用。未知或 false 不推断厂商、机型或上限，只写已确认范围内的 `screen_brightness`。参考为本地已核对的 `OplusBrightnessControllerExImpl.init()` 中的 capability 检查和 `setBrightness()` 的 raw 调整行为；构建、运行不需要反编译目录。

范围变更会清理待写请求，旧范围下开始的拖动不再用新范围解释；关闭也清理队列。已经进入系统执行的单次命令无法追溯取消，后续排队命令不继续执行。双设置命令改用 `&&`，首个设置失败不再被后一个的成功退出码掩盖。没有修改自动亮度按钮、滑条外观或材质。

验证命令：

```powershell
python -X utf8 validation/brightness/run.py
```

- 29 项实际 `BrightnessRangePolicy` 断言：合法/非法范围、独立探测失败、设置范围优先级、整数边界、当前 2–4095 映射与 raw 2728 往返、能力未确认时禁止 raw adjustment、无范围拒绝命令。
- 20 项提取生产控制器方法的断言：不可用 UI/提示去重、正确百分比、拖动合并、旧范围请求丢弃、关闭取消、失效拖动、执行失败提示、关闭后不回调、executor 拒绝、当前值读取失败及恢复。
- 两个生产 Java 类独立编译通过。

测试替换 Android 控件、主线程队列、executor 和进程边界，没有执行 `adb`、`su` 或写入任何系统设置。它不证明某一未测试固件的 feature API / 属性可访问，也不是手机亮度写入或重启实测。
