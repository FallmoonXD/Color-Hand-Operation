# 标题与上滑遮罩

首页的 `Color Hand Operation` 随列表向上移动，进入工具栏后停住。整个过程沿用展开时的字号和字重，`Color` 仍使用 ColorOS 官网字标。标题和更多按钮分别占位。窗口较窄或系统字体较大时，按两个位置中较窄的可用宽度适配整行，同一次滚动中字号固定。

## ColorOS 17 对照

在 PKR110 的系统设置「显示与亮度」页上滑，列表进入标题后方，顶部逐渐模糊，靠近下一行的内容仍然清晰。标题和返回按钮画在遮罩上方。

本机 Settings APK 中，`settings_homepage_container_oplus.xml` 和 `coui_preference_list_with_appbar_fragment.xml` 都使用 `AppBarBlurOverlayView`。`AppBarBlurOverlayBehavior` 按实际滚动距离设置透明度，从页首的 0 到 30dp 时的 1。普通设置页向栏下方多覆盖 24dp。首页的 `HomepageImpl` 在工具栏完全收起时另留 14dp，带搜索栏的系统布局使用 8dp 下沿。

`AppBarBlurHelper` 将页面底色的纵向渐变叠在同窗口背景模糊上。默认模糊半径由上到下从 30px 降至 0，速度参数为 3。浅色渐变从下往上的位置为 0、0.4、1，对应透明度为 0、0.7、1。深色使用两个端点，透明度为 0、0.4。

## CHO 的实现

`ColorSettingsScaffold` 在滚动内容和固定标题之间绘制 `ColorAppBarMask`。首页、二级设置、搜索结果和短页面打开时都有一层淡渐变。CHO 将页首遮罩透明度设为 55%，上滑 30dp 后达到 100%，回到页首恢复到 55%。应用选择页读取列表首行位置，动作目录读取滚动容器偏移。标题、固定搜索框、返回和保存按钮画在遮罩上方。

ColorOS 上使用 `OplusRenderEffect.createGradientBlurEffect` 和 `OplusViewBackgroundRenderEffect.setBackgroundRenderEffect`。关闭模糊、开启省电或设备缺少接口时，标题仍有底色渐变。

更多菜单、确认框、底部选择窗和手势面板共用 `TopEdgeGradient`。浅色主题从顶部略微压暗，深色主题从顶部略微提亮。浮层渐变最多向下延伸 88dp，按圆角裁切；快速启动和任务切换器的标题卡也沿用相同处理。渐变跟随窗口淡入，玻璃面板继续共用原有的取景。系统设置、文档选择器和小组件提供方的设置界面由对应应用绘制。

`validation/settings-title/run.ps1` 检查标题位置、窄窗口适配和渐变透明度。实机对照、菜单入场测量及浅色和深色的像素检查记录在本地 `verification/unified-gradient/`。
