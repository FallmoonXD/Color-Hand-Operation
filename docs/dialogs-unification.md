# 0.3.12 共享选择面板与表单

记录 `ColorDialogBuilder`、`GlassDialogBuilder` 和服务菜单的实现与离线测试。设备上的键盘位置、焦点和视频播放结果见 [0.3.12 实机记录](coloros-menu-validation.md)。

## 兼容接口与呈现方式

Builder 继续返回真实 `AlertDialog` 子类。现有 `create()` / `show()`、`getButton()`、`getListView()`、`setOnShowListener()` 和 `setOnDismissListener()` 调用保持可用。

- `setItems`、单选、多选、adapter 列表以及 `setView` 表单改为底部 Settings 选择面板。
- 仅标题/消息的确认提示保留居中 Settings 对话框。
- 短表单按实际内容高度展开，不使用固定屏高百分比。面板宽高上限沿用 `ColorSheetGeometry`，列表行按实际测量高度计算。
- 标题、可滚动内容、分隔线和底部操作区相互独立。标准列表文字为 16sp、中等字重，左右 24dp；单选和多选使用共享 ColorOS 控件并保留真实 checked 无障碍状态。
- 底部按钮按实际文字宽度选择横排或纵排，按钮高度随内容增长，避免三个长按钮或大字号被强制裁成 48dp。
- 纯表单使用共享设置滚动容器。直属含 ListView/ScrollView 的纵向表单保留搜索/状态区域，列表改为弹性高度；原高度用于计算期望高度。异步加载、错误文字和列表可见性变化会触发重测。

配色、字体、输入框、按钮和分隔线跟随 `ColorOsTheme`。首屏显式初始化颜色，订阅仅负责后续变化。玻璃面板继续使用原 `ColorOsSurface`，没有调整已确认的运行时浮层材质参数，也没有修改 retained `ColorActionSheet` / `ColorPageMotion` 流程。

## 关闭顺序和验证表单

普通 `setItems` 以及 Builder 提供的正/负/中性按钮，默认先完成退出动画、移除旧窗口并派发原 `onDismiss`，随后才调用选择/按钮回调。因此回调中新建下一层表单，不会先显示新窗口再关闭旧窗口。

单选和多选保持原 API 的不自动关闭语义，便于实时选择与多项勾选。需要导航或显式提交的自定义行、验证按钮可使用：

```java
ColorDialogBuilder.dismissThen(dialog, () -> openNextPage());
```

`getButton(...).setOnClickListener(...)` 仍可在 `show()` 后覆盖默认操作以执行输入验证。验证失败只显示行内错误；验证成功后再调用 `dismissThen`。

`dismissImmediately` 取消退出中的动作，以及已经排队但尚未执行的后续回调。Activity 在退出中暂停、销毁或在移窗后尚未派发回调时暂停，也会取消后续动作。重复关闭不会重复移窗或重复提交；服务 context 不需要伪造 Activity。

危险确认可使用：

```java
builder.setPositiveDestructive("删除", (dialog, which) -> deleteSelection());
builder.setDestructiveButton(DialogInterface.BUTTON_NEGATIVE, "放弃", listener);
```

它只声明按钮的错误/危险色，不改变动作权限或提交语义。

## 输入法、浮层和短菜单

面板保留系统 `SOFT_INPUT_ADJUST_RESIZE`。系统负责把浮动窗口放到键盘上方；共享几何仅限制内容高度，底部外边距固定 8dp，不把 IME 高度再次加到窗口 y 上。

设备检查曾发现：键盘顶为 y=1606 时，WidgetProviderPicker 的 Dialog 仍请求 2123px 高，380dp ListView 及 footer 没有收缩（`verification/ui-unification-widget-ime-window.txt`）。源码修正不再只依赖浮窗 root 收到的 IME inset：`ColorWindowSpace` 以 WindowMetrics 的完整 task bounds 和可见 system bars / cutout / IME 计算空间，再以 `getWindowVisibleDisplayFrame` 限幅。坐标保留 task origin，支持分屏和旋转；不会与浮窗自身较小的 root bounds 相交。修正后的键盘位置需要设备测试；下面的离线断言只检查几何计算。

`bodyHeight` 不再对正在显示的弹性 ListView 或其父容器执行天然高度 measure / forceLayout：它按保存的期望列表高度、普通搜索字段的实际高度、padding 和 margins 计算期望值。窗口更新尺寸后由框架的正常 traversal 完成最终受限测量与 layout。早期修复曾在全局布局后手工 forceLayout + root.measure；缺少对应框架 layout 时，父容器会保持布局请求标志，后续 adapter 更新或行内错误的 requestLayout 可能无法继续上送。该路径已移除，未用无条件 requestLayout 或轮询替代。底部按钮显式居中，行内错误采用动态主题 error 色。按钮的横排/竖排判定也不再对活 Button 做 UNSPECIFIED 测量：使用 `Layout.getDesiredWidth` 对当前 TextPaint 和转换后的实际文字求期望宽度，再计入 compound padding / minimum width。这避免全局布局后按钮保留狭窄的内部文字 layout，导致外框宽度正常而取消文字偏左；实际行高和居中由框架布局完成。

共享 Builder 不清除调用方的 `FLAG_NOT_FOCUSABLE` 或窗口类型。`GlassDialogBuilder` 可识别经过包装的 AccessibilityService / ToolsPanel context；非文本选择菜单默认使用 accessibility overlay 和不可聚焦策略。实际文本编辑器仅清除 `FLAG_ALT_FOCUSABLE_IM`；调用方仍负责决定何时允许编辑窗口取得输入焦点。

可取消的非焦点窗口额外请求 `FLAG_WATCH_OUTSIDE_TOUCH`，收到 `ACTION_OUTSIDE` 时仅取消当前弹窗。关闭按钮也只取消当前弹窗，没有注入返回键。不能从这些 flags 推导所有媒体应用一定不会暂停，仍需实机播放检查。

真实按钮锚定的短菜单可使用：

```java
new ColorPopupMenu(anchor)
    .setFocusable(false)
    .setWindowLayoutType(WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY);
```

两个方法都是显示前的链式配置。Activity 默认菜单仍可聚焦，服务菜单不主动请求首项焦点。锚定菜单同样使用 `ColorWindowSpace`：保留真实 anchor 的屏幕坐标，但不再把可用区域强行裁到 Widget/Launcher 小浮窗之内，也不对该小窗口重复减系统栏 inset。任务窗口边界仍保留。退出后执行顺序不变。

服务菜单的 token 另有实测问题：`verification/ui-unification-popup-diagnostic.txt` 显示 `PopupWindow.showAtLocation(View,...)` 将父窗口的 IWindow binder 交给 TYPE_ACCESSIBILITY_OVERLAY，系统拒绝 BadToken。修正只作用于该类型：读取真实 anchor 所在 root 的 `WindowManager.LayoutParams.token`（AccessibilityService 注册 token），使用项目已有 HiddenApiBypass 调用框架的 IBinder 重载。Activity 菜单继续使用原 View API。窗口类型、服务 WindowManager 与 NOT_FOCUSABLE 策略保持，不降级成应用窗口；缺少已注册 token 则明确失败并记录 warning。临时诊断日志已移除。

## 离线验证

执行：

```powershell
python -X utf8 standalone/validation/dialog/run.py
```

- **15 项生产回调方法断言通过**：退出期间不派发、移窗后 `onDismiss` 先于导航、重复结束、退出中暂停、移窗后暂停、立即销毁、owner 不可用、服务 context、回调异常时释放订阅，以及原 `onDismiss` 主动取消后续动作。
- **14 项生产 footer 方法断言通过**：短按钮横排、长字/大字号纵排、自然行高、纵横切换清除旧边距、重复测量不触发布局循环、空操作区和窄窗口，以及 compound padding、显示文字转换、minimum width、null 文本；测试中任何活 Button.measure 调用都会直接失败。
- **12 项生产 body 高度方法断言通过**：期望列表/搜索高度、margins、布局参数不变、不测量活列表及其父、不遗留人工布局标志、连续数据请求可传播、隐藏行和无限高视口、叶子测量异常不破坏列表、短表单自然高度。
- **13 项生产 task/IME 空间方法断言通过**：完整任务边界、浮窗不裁小 root、键盘区域、短表单不拉高、长列表限高、OEM metrics IME 缺失的 visible-frame 补充、键盘关闭恢复、分屏原点、旋转横向 inset、未 attach/空 frame/metrics 失败。
- **7 项生产 Popup token 路由断言通过**：Activity 仍用 View API、服务使用 root 注册 token 而非 IWindow binder、保留 overlay 类型和屏幕坐标、缺 token/错误根类型时不降级。测试替换隐藏框架调用边界，真实 Binder 验证仍交由设备。
- 以上 **61 项**通过；四个相关生产类（含新增 `ColorWindowSpace`）对 Android API 35 独立编译通过。

脚本直接抽取生产方法，替换 Dialog 移窗、主线程消息队列和文字测量边界；不复制一套独立业务实现。它没有模拟系统 IME、窗口焦点、真实字形尺寸、NativeMaterial 合成或第三方媒体播放，也没有执行整包构建或安装。
