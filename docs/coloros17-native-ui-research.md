# ColorOS 17 原生 UI 调查笔记

2026-11-06，在一加 PKR110、ColorOS 17 / Android 17 上整理了一份本地 UI 资料库。主要目的是改界面时能找到对应的页面、控件和绘制代码，少靠截图猜尺寸。

## 收集了什么

- 当前安装的全部 483 个系统包，以及 27 个厂商相关包；`pm path` 返回的 543 条 APK 路径均已收齐。
- 544 个 APK 资源包解码成功，得到约 45.7 万份 XML，其中布局变体约 9.6 万份。
- 约 1.05 万个 Activity / activity-alias 声明，1,515 条 XML 页面跳转关系。
- 32 个应用或框架的重点反编译结果，约 11.8 万个 Java 文件，部分复用同哈希的旧样本。
- shader 字符串、字体与默认主题、overlay 状态、native 库符号，以及 5 个核心包的运行时资源值。

这些数目含共享库、资源变体和不可达入口，不是实际打开过的页面数。视觉检查只覆盖了“显示与亮度”和“声音与振动”两页；亮暗资源配置读取也不是完整的暗色界面测试。

## 找一个页面的实现

先查 manifest，再跟 Fragment、XML 和控件类。以显示设置为例：

```text
android.settings.DISPLAY_SETTINGS
  → Settings$DisplaySettingsActivity
  → manifest: com.android.settings.FRAGMENT_CLASS
  → com.oplus.settings.feature.display.DisplaySettingsFragment
  → R.xml.display_settings_oplus
  → Oplus Preference / COUI 控件
```

Settings 包里同时有 `display_settings.xml`，但当前页面用的是 `display_settings_oplus.xml`。不能只按文件名相似就选一个。声音页对应 `SoundSettingsFragment` 和 `sound_vibrate_settings`。

接下来查 Preference 绑定的布局、style 和绘制类。行距、按压、取消、弹簧、禁用态并不都在 XML 里，通常还需要看 `com.coui.appcompat`。

## 控件、主题和动画

设置包里的 COUI 实现包含开关、滑条、单选/复选、菜单、对话框、底部面板、搜索和滚动等组件。滑条纹理还分渐变、缓存阴影和覆盖层，不能只替换默认 SeekBar 的背景。

当前 560 dpi 配置下，开关轨道解析为 154×84 px，即 44×24 dp；圆径 18 dp，内边距 3 dp。数值来自资源读取，实际形状仍需对照绘制代码。

颜色需要一起看 AppBaseTheme、COUIThemeOverlay、RRO 和主题商店配置。`material_color_value` 不只是一个颜色值，`font_variation_settings=550` 也不等于所有文字都应固定为 550 字重。

动画方面，`COUISpringForce` 用 `(2π / response)²` 换算 stiffness。响应时间、阻尼、初始速度、反向接续和收敛条件都影响结果，只有一条近似贝塞尔曲线通常不够。

## 两条材质路径

页面内的背景效果大致沿着这条链路：

```text
COUIMaterialStateManager
  → COUIMaterialEffect / Blur / Stroke / Spotlight / Caustic
  → OplusViewBackgroundRenderEffect / OplusMaterialUtil
  → 扩展 RenderNode
  → HWUI 与 Oplus native 扩展
```

其中背景模糊会组合 `RenderEffect.createBlurEffect` 和两层混色，再交给 Oplus 的背景渲染接口。它与直接对 View 自身内容调用 `setRenderEffect` 不同。

跨窗口模糊另外使用 `com.oplus.blur` 服务，通过 SurfaceControl 捕获、HardwareBuffer 和 shader 处理背景。该服务有 `oplus.permission.OPLUS_COMPONENT_SAFE` 权限要求，不能把 Binder 编号当成跨固件通用 API。

本机 Oplus SDK 为 40.23，相关类和方法存在。材质效果还受系统开关、主题和省电影响；接口存在不代表第三方应用在所有窗口都能使用。原生库已保留二进制和符号，没有恢复为厂商 C++ 源码。

## 资料位置与使用范围

完整资料留在本地 `analysis/coloros17-native-ui-20261106/`，不随仓库发布。主要入口是：

- `ui-catalog.sqlite3`、`query.py`：按类、资源、页面或跳转目标搜索。
- `activity-catalog.json`、`page-links.jsonl`、`widget-usage.json`：页面与控件索引。
- `acquisition.json`：原始文件位置和 SHA-256。
- `source-coverage.json`：反编译范围与诊断日志。
- `runtime/resolved-resources.json`：运行时资源和 API 读取结果。
- `RESEARCH_REPORT.md`：完整本地报告。

采样 OTA 为 `PKR110_11.F.11_2110_202610310143`。8 个旧 APK/JAR 与当前设备同哈希，但整机指纹已经变化；复用这些文件的分析时，不要顺带沿用旧固件的后台策略或兼容结论。

JADX 对复杂控制流和 Kotlin 协程仍有还原错误；有歧义时回到 DEX 和设备行为。云端模板、未安装模块、其他地区/机型以及工作资料都还需要单独调查。这份资料库用于定位实现，不参与应用构建，也不改变现有面板材质。
