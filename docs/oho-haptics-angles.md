# OHO+ 9.3.13 振动与角度核对

依据 OHO+ 9.3.13 的资源和反编译代码整理原版行为及兼容方案。JVM 测试检查时间和参数；实际振动手感需要在手机上测试。

## 原版文案与界面条件

`resources/res/values-zh-rCN/strings.xml`：

| 资源 | 原文 |
| --- | --- |
| `settings_use_quick_vibration` / `_detail`，412–413 行 | 快速振动；识别到手势时立即振动，而不是等到手指从屏幕上松开。 |
| `setting_touch_custom_vibration` / `_desc`，378–379 行 | 自定义振动强度；为%s 设置自定义振动强度等级。 |
| `sub_title_use_haptic_vibration`，433 行 | 振动级别 |
| `handler_low` / `handler_high`，99 / 95 行 | 低 / 高 |
| `gesture_setting_angle`，91 行 | 手势角度 |

`SGPGestureDetailActivity.java:164–174,221–240` 将“快速振动”和“自定义振动强度”分成两个开关。强度控制区只在自定义开启时显示。设备没有振动器则整个振动分组隐藏。`n6/y.java:266–275` 的系统触觉能力检查是三星 `semGetSupportedVibrationType()>1`；支持时可选系统/自定义，不支持时隐藏自定义开关并强制自定义（`n6/c.java:465–466`）。

新安装默认必须看初始化，而不只看界面 `getInt` 的 fallback：`n6/c.java:128–135` 初始化快速振动为开；系统能力可用时初始化自定义为关。自定义级别默认手机 5、平板 0（`y.c1()` 读取 `ro.build.characteristics` 的 tablet 标记）。界面级别范围为 0–30（`SGPGestureDetailActivity.java:171–172`）。

原版其他条件：长滑持续时间只在长滑“快捷操作”开启时显示，`long_swipe_time` 默认 6、范围 0–20；运行乘 50 ms，实际默认 300 ms、范围 0–1000 ms（`SGPGestureDetailActivity.java:150–161`、`b6/a.java:710`）。这不是振动时长。

## 真实触发时机与系统优先

`b6/a.java:745–768` 首次跨过短滑距离时设置 `f8260n=true`，快速振动开启且方向可操作才提交一次反馈；撤回再跨阈值不会把该标志复位。该标志在新的 DOWN 初始化。`r5/f.java:361–366` 对长滑动作另给一次反馈。方向改变、撤回和抬手不能让已执行动作再重复反馈。

快速振动关闭时，`r5/f.java:3082–3087` 的 `Y()` 在动作执行路径提交反馈。因此通常是抬手反馈；若另行开启“快捷操作”，动作可提前执行，相应反馈也提前。资源说明把常规抬手情况作了简化，不应据此把所有反馈强制延迟到 UP。

`n6/m.java:116` 读取三星 `Settings.System.navigation_gestures_vibrate`；`n6/y.java:2117–2147` 的系统模式只有该设置开启才调用三星 TYPE_TOUCH 波形。自定义模式的 0 表示不振动；能力支持时使用 `createOneShot(level, DEFAULT_AMPLITUDE)`，即 1–30 ms，而非 1–30 幅度。旧式振动器备用分支使用 `level*2+20` ms 的波形。

独立应用使用 ColorOS 的公开 `View.performHapticFeedback(GESTURE_END)` 作为系统模式，交由系统选择效果与强度，不使用忽略系统全局触觉设置的标记，不在系统拒绝反馈后偷偷调用 Vibrator。自定义模式采用 0–30 ms、系统默认振幅。它与三星的硬件波形不同，不声明跨设备振感完全相同。

## 原版角度是四条实际识别边界

`SGPAngleSettingView.java:130–169` 为短滑和长滑各保存四个斜率；`w5/b.java:359–366` 直接读取这些值用于识别。不是修改演示箭头角度。

| 边界（相对向内水平线，表中取绝对角度） | 斜率限制，`b6/a.java:1079–1091` | 约合可调范围 | 原版短滑默认 | 原版长滑默认 |
| --- | --- | --- | --- | --- |
| 上方纵向限位 | 3.732–11.43 | 75–85° | 75° | 75° |
| 向上斜滑/水平分界 | 0.087–2.747 | 5–70° | 25°（0.466） | 20°（0.364） |
| 向下斜滑/水平分界 | -2.747 至 -0.087 | 5–70° | 45°（-1） | 30°（-0.577） |
| 下方纵向限位 | -11.43 至 -3.732 | 75–85° | 75° | 75° |

原版保存的是浮点斜率；图上扇区角度才 `Math.round`（`SGPAngleSettingView.java:324,340,347,356,363`）。最新图形编辑器同样保留浮点拖动精度，仅把图上读数显示为整数度。外界的最小值 75° 总大于内界最大值 70°，四条边界独立调节不会相互穿越。

独立识别器把短滑和长滑的有效区域分开计算：长滑可以在短滑角度范围外成立；长滑未达到持续时间时只能回退到仍有效的短滑；最终动作读取对应短/长方向。长滑关闭时不会因独立长角度而重新启用。接近纵向的范围仍排除，不新增纵向滚动动作。

## 配置兼容

| 新字段 | 类型/范围 | 缺省与旧配置兼容 |
| --- | --- | --- |
| `quick_vibration` | boolean | true，只决定反馈时机 |
| `custom_vibration` | boolean | false 系统；旧非零 `vibration_strength` 仍显示自定义 |
| `vibration_duration_ms` | int，0–30 | 5；0 为自定义静音，界面显示 level/30 百分比 |
| `(short\|long)_angle_(up\|down)_(outer\|inner)_degrees` | float；outer 75–85、inner 5–70 | 未设的新边界继续使用旧 `direction_angle_degrees`（默认 30°）作为内界，80° 作为外界 |

不批量改写既有数据。旧 `vibration_strength` 1–255 在没有新 duration 时继续使用原振幅及原短/长 8/18 ms；新界面将其映射为可显示级别，非零幅度最低显示 1 级，不误显示为静音。用户调整级别后才使用新的 duration 模式，旧字段保留供旧档案理解。

旧 `vibration=false` 不能变成不可见的永久总禁用：初始仍不振动，界面呈现“自定义开、0 级”；用户明确操作新振动控件时把这一状态固化成 `custom_vibration=true,duration=0`，解除旧 master 限制，再应用本次选择。关闭“自定义振动强度”即可恢复系统模式。单纯进入页面、读取或导出不迁移/改写。

`GestureAngles`、`GestureHaptics` 是无 Android 依赖的读配置辅助；`EdgeHandleView` 负责反馈时机和系统调用，`AppPrefs` 验证新字段与持久化类型。

## 验证

`validation/gesture/run.ps1` 当前通过：68 状态机、51 触钮配置、136 长滑持续时间、45 独立角度/振动兼容、39 实际生产输入方法断言。新增覆盖短区外长区内及相反、上下与左右镜像、不同短长分界、旧 80° 边界、长滑禁用、快速振动开/关、方向变化和撤回不重复反馈、取消不触发延迟反馈。输入测试提取真实 `EdgeHandleView` 输入/调度/派发方法，触觉出口记数；并未模拟真实硬件振幅。

配置测试另覆盖 8 角度的浮点往返、范围拒绝、duration 的整数类型、两开关类型、旧字段保留和独立外部动作目标。最终 `validation/config/run.ps1` 使用现有 Gradle mockable Android jar 完成全部生产 Java 编译与 **184 项配置断言**（原 141 项加本轮 43 项）。SDK 原始 `android.jar` 仅适合编译，不可拿来执行这个需要 Android 对象构造器的 JVM harness。设备 UI、系统反馈设置和实际振感由单独的真机回归记录确定。

## 可拖动图形编辑器

`angle_setting_view.xml` 原版使用半圆及四个拖柄；`values/dimens.xml:899–902` 定义圆直径 400 dp、拖柄 40 dp、径线 5 dp、圆外线 2 dp。图的圆心贴屏幕左边或右边，四条径线随斜率改变（`SGPAngleSettingView.onDraw`），两个方向切换按钮仅改变查看侧，不改变参数。按钮是 40 dp 圆底箭头，分布于屏幕两侧中部、横 margin 20 dp；XML 没有给它们文本说明。原版文本位于动态扇区中点，不是固定的动作图标。

原版每次拖动结束暂存斜率；点击“确定”更新保留快照，取消或窗口卸载会把进入时的四值写回。该保存时机由 `SGPAngleSettingView.onClick/onDetachedFromWindow` 与 `b6/a.java:1046–1105` 共同实现。

独立 `AngleEditorView` 是原生 Canvas 控件，不依赖生成图片、WebView 或 OEM 私有类。它直接编辑同一组四边界，短/长各一张图；三方向引导箭头及文字中心固定于 -45°/0°/+45°，实际识别扇区和四拖柄才随值变化。这是本轮明确要求，相比原版移动标签的设计差异。扇区夹角及四边界的整数读数实时更新，真实提交值保留小数。

拖柄命中半径为 24 dp，边界线仅外半径 48%–100% 的 9 dp 邻域可启动编辑。普通扇区空白、原点附近、反侧区域返回未处理，交给父滚动容器；不把任意竖滑解释成调角。命中后先禁止父拦截，DOWN 只选中，移动超过系统 touch slop 才更新草稿。起始抓取偏移保留，避免摸到拖柄边缘时角度跳变。UP 只对发生变化的那个字段回调一次；CANCEL、多指、离开窗口、失焦、后台隐藏、尺寸变化及禁用都恢复原始浮点值，不提交。

控件读取 `ColorOsTheme` 的实时颜色、字体和字体大小；半径、标签列宽和底部读数行数随可用宽度调整，不要求 Main 写死高度。`setMirrored` 仅镜像编辑视角。无障碍提供 4 个虚拟 SeekBar 节点，固定边界名称、动态 stateDescription、真实浮点 RangeInfo、SET_PROGRESS、增减 1°及 SHOW_ON_SCREEN；屏外节点可见性取实际父容器裁剪交集。触觉或系统手势运行语义未因此变化。

公开接入：`new AngleEditorView(context, longer, values, listener)`，数组顺序为上外、上内、下内、下外。唯一必需回调 `onCommit(index,degrees)` 由 Main 映射到 `GestureAngles.key(longer,index)`；可选 `onPreview`/`onCancel` 不落盘。`getAngles()` 返回副本，`setAngles()` 可读回刷新，`cancelDrag()` 可主动撤销。这里没有增加新的角度配置字段。

`validation/angle-editor/run.ps1` 的 **161 项断言**检查真实几何辅助：左右镜像、多密度坐标正反变换、相邻 5° 拖柄的命中优先、普通区域不截获、抓取偏移、限位、精确回滚、一次提交、非有限数拒绝及数组隔离。新增控件与全部生产 Java 编译通过。Android 手势分发、页面滚动和绘制结果仍需设备测试。
