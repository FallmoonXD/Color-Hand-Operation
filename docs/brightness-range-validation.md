# 快速工具亮度

ColorOS 的多位亮度使用面板原始档位。CHO 先读取系统的 `screen_brightness_range`，再尝试 `OplusPowerManager` 和多位亮度属性。只有拿到有效上下限才启用滑条；每个来源都单独校验，无法读取当前亮度时会在滑条上说明原因。

在明确支持 `oplus.software.display.multibits_dimming_support` 的设备上，拖动沿用 SystemUI 的 `OplusBrightnessControllerExImpl.setTemporaryBrightness`。每次把有效档位传给显示服务的 `setTemporaryBrightness` 和 `setTemporaryAutoBrightnessAdjustment`。松手时才保存 `screen_brightness` 和 `screen_auto_brightness_adj`，自动亮度模式由用户的设置决定。其他固件只在已知范围内写普通亮度设置。

同一面板的拖动共用一个 Root 进程。还未发送的中间位置可以合并，已经松手的请求保留顺序。取消拖动或关闭窗口会清理临时调节。亮度范围发生变化时丢弃按旧范围计算的请求。Root 不可用时会提示失败，同一次拖动不会反复请求授权。

在一加 PKR110 上，旧包的自动亮度滑条从约 20% 拖到 34% 时，系统设置变了，背光驱动读数仍是 1224。改用显示服务后，在松手前就能看到驱动读数和 DisplayPowerState 随档位变化。这里测的是显示服务与驱动的读数，光照和固件自身的渐变也会影响最终亮度。

实机测试会暂时切换手动和自动模式，核对拖动、松手和取消，并恢复原来的模式和调整值。

~~~powershell
python -X utf8 validation/brightness/run.py
python -X utf8 tools/verify_quick_brightness.py
~~~
