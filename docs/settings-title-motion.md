# 标题与上滑遮罩

首页和二级页使用同一行工具栏。标题默认 18sp，`Color` 在文字基线上绘制官网字标。更多和返回各占一个 48dp 的点击区域，中央的圆形按钮为 40dp。窄窗口或大字号下，两个页面都按各自的可用宽度适配标题。

## ColorOS 17 对照

在 PKR110 的系统设置「显示与亮度」页上滑，列表进入标题后方，顶部逐渐模糊，靠近下一行的内容仍然清晰。标题和返回按钮画在遮罩上方。

本机 Settings APK 中，`settings_homepage_container_oplus.xml` 和 `coui_preference_list_with_appbar_fragment.xml` 都使用 `AppBarBlurOverlayView`。`AppBarBlurOverlayBehavior` 按实际滚动距离设置透明度，从页首的 0 到 30dp 时的 1。普通设置页向栏下方多覆盖 24dp。首页的 `HomepageImpl` 在工具栏完全收起时另留 14dp，带搜索栏的系统布局使用 8dp 下沿。

`AppBarBlurHelper` 将页面底色的纵向渐变叠在同窗口背景模糊上。默认模糊半径由上到下从 30px 降至 0，速度参数为 3。浅色渐变从下往上的位置为 0、0.4、1，对应透明度为 0、0.7、1。深色使用两个端点，透明度为 0、0.4。

## CHO 的实现

`ColorSettingsScaffold` 将首页字标、更多按钮和二级页的标题、返回按钮放在同一个 52dp 工具栏中。列表滚动或回弹时，工具栏保持固定。「已开启／已关闭」下方恢复原来的两句触钮说明，字号为 14sp，行高为 20sp。系统字体需要更多空间时随文字增高。

`ColorAppBarMask` 把静止时的渐变和滚动时的模糊分别绘制。静止渐变从页面底色过渡到下沿的一点卡片色，状态栏一端保留页面原色。上滑 30dp 时，原生背景模糊完全显现。回弹结束，静止渐变重新完整显示。短页从选项、滑条或空白处纵向起手都使用同一回弹，遮罩读取内容的实际位移。长页仍由原有列表处理滚动，水平滑条和点击由各自控件处理。

短页在按住上滑时可以一直保持 `scrollY == 0`。只看松手后的截图，会漏掉可点击选项是否交出了触摸事件，也无法证明静止渐变存在。实机测试需要在按住时读取行坐标，并在松手后单独检查渐变下沿。

ColorOS 上使用 `OplusRenderEffect.createGradientBlurEffect` 和 `OplusViewBackgroundRenderEffect.setBackgroundRenderEffect`。关闭模糊、开启省电或设备缺少接口时，标题仍有底色渐变。

更多菜单、确认框、底部选择窗使用弹窗底色做局部渐变；快速启动和任务切换器的标题卡取各自的卡片底色。玻璃面板的明暗由取景和边缘光照决定，见[材质记录](material-policy.md)。系统设置、文档选择器和小组件提供方的设置界面由对应应用绘制。

`validation/settings-title/run.ps1` 检查静止、拖动、释放三种状态的图层透明度。`tools/verify_short_settings_header.py` 在实机比较首页和二级页标题栏，从短页选项和空白处起手，读取行的位置及渐变像素，再检查回弹和点击。设备记录在 `verification/short-settings-header/`。
