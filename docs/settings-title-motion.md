# 标题与上滑遮罩

首页的 `Color Hand Operation` 随列表向上移动，进入工具栏后停住。整个过程沿用展开时的字号和字重，`Color` 仍使用 ColorOS 官网字标。标题和更多按钮分别占位。窗口较窄或系统字体较大时，按两个位置中较窄的可用宽度适配整行，同一次滚动中字号固定。

## ColorOS 17 对照

在 PKR110 的系统设置「显示与亮度」页上滑，列表进入标题后方，顶部逐渐模糊，靠近下一行的内容仍然清晰。标题和返回按钮画在遮罩上方。

本机 Settings APK 中，`settings_homepage_container_oplus.xml` 和 `coui_preference_list_with_appbar_fragment.xml` 都使用 `AppBarBlurOverlayView`。`AppBarBlurOverlayBehavior` 按实际滚动距离设置透明度，从页首的 0 到 30dp 时的 1。普通设置页向栏下方多覆盖 24dp。首页的 `HomepageImpl` 在工具栏完全收起时另留 14dp，带搜索栏的系统布局使用 8dp 下沿。

`AppBarBlurHelper` 将页面底色的纵向渐变叠在同窗口背景模糊上。默认模糊半径由上到下从 30px 降至 0，速度参数为 3。浅色渐变从下往上的位置为 0、0.4、1，对应透明度为 0、0.7、1。深色使用两个端点，透明度为 0、0.4。

## CHO 的实现

`ColorSettingsScaffold` 在滚动内容和固定标题之间绘制一层 `ColorAppBarMask`。应用选择页读取真实列表的首行位置，动作目录读取滚动容器的偏移。固定搜索框、返回、保存和更多按钮都在遮罩上层。列表回到顶部或搜索结果清空，遮罩随即退去。内容不足一屏的页面保持静止。

ColorOS 上使用 `OplusRenderEffect.createGradientBlurEffect` 和 `OplusViewBackgroundRenderEffect.setBackgroundRenderEffect`。其他系统缺少相关接口时使用页面底色渐变。系统关闭材质模糊或进入省电模式时，标题栏使用实色背景。

`validation/settings-title/run.ps1` 检查标题位置、窄窗口适配和滚动透明度。实机对照图在本地 `verification/title-mask/`。
