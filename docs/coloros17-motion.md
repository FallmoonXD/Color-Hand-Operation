# ColorOS 17 动效对照

参照设备是一加 PKR110。时长按系统动画倍率 1× 记录。组件的位置、材质和触摸归属由 CHO 管理。核对时分别检查透明度、缩放、位移和窗口移除。完整时长和肉眼可见的主要变化分别计量。

| CHO 组件 | ColorOS 实现或资源 | 使用的参数 |
| --- | --- | --- |
| 快速工具、小组件、快速启动、任务切换器、虚拟触控板、放大镜、居中确认框 | Settings 的 `coui_center_dialog_enter/exit` | 进入 250 ms，透明度 0→1、缩放 0.8→1，曲线 (0.3,0,0.1,1)。退出 150 ms，只淡出，曲线 (0.3,0,1,1)。入场被打断时从当前状态退出 |
| 键盘快捷方式、颜色和选择底部弹层 | `coui_bottom_dialog_enter/exit` | 上下平移各 250 ms，进入和退出分别使用上面的两条曲线。二级窗口先关，移除后再执行选项回调 |
| 首页更多、浮窗里的锚定菜单 | Settings 的 `DefaultScreenAnimationController`、`PopupMotionAnimateKitController`、`PopupGravityMotionParamsExtKt` | 透明度进入和退出的 response 分别为 0.27 / 0.23 秒，bounce 都为 0。缩放分别为 0.40/0.20 和 0.21/0（response/bounce）。两条弹簧独立结束。沿用 CHO 的锚点和避让规则 |
| 设置页面、工具编辑 Activity、预测返回 | `coui_{open,close}_slide_*`、框架 `BackProgressAnimator.onBackCancelled` | 四组页面转场均为 350 ms，透明度和位移各走对应插值。系统已经把取消的返回进度送回零，最后一帧直接归位 |
| 设置列表展开、应用快捷方式分组 | `ExpandableRecyclerConnector`、`COUIMoveEaseInterpolator`、AndroidX `RecyclerView.ItemAnimator` | 高度变化 400 ms，曲线 (0.3,0,0.1,1)。分组反向展开按剩余高度缩短时间。条目添加和移除的淡变为 120 ms。禁用触钮组直接显示禁用态 |
| 开关、滑条、单选、多选 | `COUISwitchParamsExtKt`、`StandardAnimationHandler`、`coui_btn_radio_*`、`coui_checkbox_*` 动画矢量 | 开关切换 0.4/0.3、回位 0.4/0.2。滑条轨道和滑块按压用各自弹簧。单选取消时填充 200 ms、圆点缩放 267 ms。多选填充 150 ms，勾线 300 ms，取消时填充延后 150 ms。[控件对照](coloros-controls.md) |
| 顶部渐变、短页及列表回弹 | `COUIPhysicalAnimationUtil`、`COUINestedScrollView`、`SpringOverScroller` | 标题和顶部遮罩随滚动位置更新。边缘按距离和视口计算阻尼，回弹按刷新间隔积分并合并不足半个像素的步进。范围内的滚动、惯性和焦点继续使用设备框架的 ScrollView/ListView |
| 音量按钮、边缘虚拟导航键 | SystemUI `VolumeAnimUtil`、`ScrimUtil` | 本机动画等级 1，进入 700 ms、退出 500 ms，弹簧为 0.7/0.2、0.7/0。等级大于 3 时使用 300 / 250 ms 和 0.9/0。等级读取不到时采用普通档 |
| 手势箭头 | SystemUI `BaseBezierCalculator.onGestureFinished` 的横向回位 | response 0.1 秒、bounce 0，从实际像素位移回到边缘。跟手和识别仍用触摸事件，箭头形状和抬高位置沿用 OHO+ |
| 屏幕自由移动 | SystemUI 单手模式的 `OneHandedDisplayAreaOrganizer` 和 `OneHandedInterpolator` | 平移复位读取系统资源，本机为 600 ms。没有位移时直接结束。映射点击前仍检查原前台窗口 |
| 原生材质首帧晚到 | SystemUI `CrossFadeHelper.fadeIn` 与 `Interpolators.ALPHA_IN` | 用 210 ms、曲线 (0.4,0,1,1) 从兜底画面过渡到原生材质。长按阈值、音量条空闲超时和模糊服务的首帧等待各自独立 |

第三方小组件内部、输入法、系统授权页和外部应用的转场由对应应用或系统绘制。对照覆盖 CHO 控制的动画属性，正常运行无需加载厂商 APK 的代码。

## 复测

使用 JDK 21 构建并覆盖安装同一份 `:app:assembleRelease`。在已解锁的一加上开启 CHO 无障碍服务，使用 1× 系统动画倍率。

~~~powershell
$env:CHO_TEST_READ_RELEASE_PREFS='1'
python tools/verify_native_motion.py
python tools/verify_panel_exit.py --back-handle right --output verification/coloros-native-motion
python tools/verify_motion_modes.py --handle right --notification-tile --output verification/coloros-native-motion
~~~

`--back-handle` 用已经设为短直滑返回的主触钮。第三项的 `--handle` 会暂时改动一个可恢复的手势，结束时核对原配置。环境变量供已 Root 的手机运行测试时只读核对配置。

第一项安装独立测试包，在自己的进程中同时加载本机 Settings、SystemUI 和已安装的 CHO APK。它逐点比较实际动画资源的矩阵、透明度、原生弹簧和动画矢量，并挂载 CHO 控件检查反向切换、取消和关闭回调。结束后核对并卸载测试包。第二项分析录屏中间帧和窗口移除时刻。第三项覆盖动画关闭、0.5×、2×、横屏常驻、主题、大字体、关闭模糊、试划、屏幕自由移动和放大镜。

窗外点击是否落到下层、系统音量条、快速工具媒体按钮、菜单首帧、短页顶部渐变和应用快捷方式展开另有实机回归。记录保存在本地 `verification/coloros-native-motion/`。这些数据对应本机固件，其他厂商的资源、动画等级和刷新率需要分别验证。

发布构建也运行完整 Android Lint。旧代码中的方向、系统栏和快捷方式标志改用数值相同的 SDK 常量。包可见性检查在清单里单独注明用途：恢复的手势、快捷方式和小组件可能来自没有桌面入口的包。安装权限与之前相同。
