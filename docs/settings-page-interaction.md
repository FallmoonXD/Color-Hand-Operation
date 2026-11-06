# ColorOS 设置页、参数预览与页面动画

2026-11-05。本记录对应 MainActivity 及共享页框架，不替代根代理的实机验收报告。

## 结构与主题

OHO 首页、触钮页、动作选择及高级/手势设置层级保持。MainActivity 统一使用 ColorOsTheme 的动态颜色/字体接口，以及 ColorSwitch、ColorSlider、ColorRadioButton、ColorCheckBox 和 ColorDialogBuilder。更多菜单也使用同一弹窗控件，不保留默认 PopupMenu。

ColorSettingsScaffold 负责52dp栏、首页32sp大标题、二级18sp中等字重标题、圆形返回按钮、边缘到边缘系统insets和系统ScrollView越界滚动。Main订阅动态主题，只在可见时重建当前页并恢复滚动位置；销毁时注销主题和服务状态订阅。服务连接提示由包内SERVICE_STATE广播刷新，没有状态轮询。

尺寸依据本机Settings资源：toolbar_min_height=52dp，TextAppearance.COUI.Toolbar.SecondTitle=18dp/medium，homepage_header_title_text_size=32dp。卡片保留20dp，因为drawable/card_list_item_full_bg.xml明确在各state使用dimen_20；不能误以旧coui_preference_card_radius=12dp代表当前整卡圆角。

## 圆点用途纠正

OHO原版SGPSettingsActivity.t()/r()按第1、2、3对触钮使用固定组色；启用时显示半透明圆点，关闭时透明。原版日间颜色为#4456c0e5、#33e555e5、#4455e555，夜间提高alpha；n6/y.v()仅绘制圆形。它不是用户选择的实际触钮颜色，也不是独立颜色编辑按钮。

本次以系统主题色为基础派生不同组标识颜色，保留透明和启停语义。圆点带说明性无障碍描述、提示和长按解释；参数预览使用同组颜色建立对应。真正触钮颜色仍在“触钮颜色”页设置，未改用户保存颜色或可见度。

## 参数预览

触摸宽度、大小、位置均带简短用途说明和模拟手机预览。拖动时只构造draft几何，通过GestureService.beginHandlePreview更新真实屏幕边缘的临时不可触摸高亮；对齐时显示两侧。模拟图被滚动遮挡时显示临时小预览卡。

抬手仅提交一次几何；ACTION_CANCEL不提交并恢复保存值。onPause、onStop、onDestroy及离页清理预览。高亮不写handle_opacity，不移动实际输入窗口；服务不可用时仍保留模拟预览。系统无障碍/键盘直接调整滑条会写入对应值。

手势设置和角度页增加阈值示意，显示短滑/长滑距离及直线/对角线分界。修改距离或角度时示意跟随更新；它不是手势动画样式编辑器。

## 页面动画

ColorPageMotion读取并复现本机Settings的coui_open_slide/close_slide格式：350ms，新页从100%进入，旧页退到-30%并降至0.5 alpha；返回反向。四个平移曲线分别来自原XML的(.3,.1,.3,1)、(.3,.15,.3,1)、(.3,.26,.4,1)、(.25,.1,.3,1)。关闭系统动画时跳过转场。

Main使用系统OnBackAnimationCallback接收手势进度，不拦截屏幕边缘触摸伪造返回；取消平滑复位，完成时延续当前位移/缩放。退出中的旧页停止响应控件并从无障碍树隐藏，动画结束或取消后移除。

## 已做验证与边界

所有当前生产Java已编译通过。未运行本轮ADB、截图或设备触摸测试；实机拖动取消、主题变化、动画观感和服务状态验证由根代理进行。页面采用独立实现的共享控件，没有加载Settings.apk中的Java类，也不声称整个Settings App完全相同。边界滚动后续实现范围见下文。

## 字号与字重继承链复核

行标题由COUIPreferenceTitleStyle继承TextAppearance.COUI.List.Title，再继承couiTextAppearanceHeadline6：16sp、sans-serif-medium（500）、headline_xs行距倍率1.05。摘要由TextAppearance.COUI.Preference.Summary继承couiTextAppearanceArticleBody和couiTextAppearance：13sp、sans-serif（400）、body_s行距倍率1.1。Main.row已由17sp/400改为该实际组合，摘要保持13sp/400并补行距。

二级栏TextAppearance.COUI.Toolbar.SecondTitle继承couiTextHeadlineS；后者显式textFontWeight=550。因此二级标题保留18并使用550。已验收的首页32大标题未改，浮层文字不在本次改动范围。依据为本机Settings styles.xml与dimens.xml的继承链，不来自截图猜测。

## 有限范围的设置页边界回弹

共享scaffold现在使用ColorSettingsScrollView，仅影响Main和ToolsSettings内部页面。范围内保留ScrollView的滚动、惯性、焦点和无障碍；到上下边缘后移动内容，不使用AOSP EdgeEffect拉伸。动作选择器的自建滚动区域随后也接入同一边界回弹；其余弹窗ListView仍为无glow普通滚动。动作选择器只调整窗口高度，保留ColorDialogBuilder的392dp/屏幕减40dp宽度限制。

阻尼参考本机已解包COUIScrollView和COUIPhysicalAnimationUtil：delta × 0.4 × (1 − min(abs(over)/limit,1))。SpringOverScroller摩擦12.19经配置转换为37.57；ScrollView设置tension multiplier2.15，对应弹簧刚度209.928。独立实现求解相同阻尼弹簧的解析解，按实际帧间隔推进，并遵守系统动画倍率/禁用。

未移植原类的快甩加速、OEM帧率提升、私有嵌套滚动策略、速度过滤、最小每帧0.5px额外积分及设备动画等级调整；不能把这称为整个COUIScrollView逐行等同。当前Settings APK的同名类尚未逐条反编译复核，模型依据来自同机SystemUI和安全中心的COUI实现。

触摸起点命中SeekBar时不被父页拦截；横向意图锁定排除，多指关闭本轮边界效果。抬手物理回弹，CANCEL归位，Detach/失去窗口焦点/隐藏/禁用动画立即清除位移与帧回调。惯性进入边界时只产生一次回弹，不改真实滚动范围或保存配置。

validation/settings-scroll/run.ps1运行生产纯Java物理与事件方向门控：51项通过，覆盖60/90/120Hz等时位移和速度、掉帧一致性、能量衰减、两端对称、反向拖动回到原生滚动、有限归零、滑条/横向/多指/取消门控。全生产Java编译通过。测试不模拟Android真实事件分发，触摸竞争与上下边界需根代理实机检查。

## 页面重建与滑条取消修正

新建scaffold在首次测量前同步读取窗口Insets，附着时再次读取并申请分发，避免页面宿主已经收到Insets后，新二级页标题首帧仍贴到状态栏。该路径同时覆盖前进、返回和主题重建。

Main的三类滑条均检查ColorSlider.isTouchCancelled，涵盖真实CANCEL以及禁用、Detach产生的内部取消；后者不经过Main的OnTouchListener。取消仅恢复最近保存的值，不提交草稿；几何滑条取消也不再render，避免拆除旧页时重入。持续时间滑条在每次成功保存后更新其恢复点。正常UP仍只保存一次。图标通过附着范围内的主题监听保留主文字色或强调色角色；普通按钮使用sp，两个Canvas预览的文字使用scaledDensity。

上述修正后全生产Java编译通过。本代理未执行这些修正的设备触摸或截图验证；ColorSlider事件路径专项验证由控件代理负责，最终真机检查由根代理负责。

## 触控区域提交保持页面实例

geometrySlider的正常提交此前会整页render，再异步恢复ScrollView位置，造成参数调整时闪现。现以局部GeometryControls同步当前组的三个滑条和模拟预览，不替换页面或滚动容器。拖动大小影响位置夹限时，关联位置条实时更新；UP只写一次并读取规范值，CANCEL只恢复原组。对齐仍由HandleSettings的一次编辑同步左右两侧，临时预览不会改变触钮可见度。

HandleSettings的生产进度换算新增6项实际验证，覆盖尺寸扩大至底边、连续位置调整保留高度、宽度独立、全高位置归中、草稿/展示不写配置，以及左右一次写入同步。配置验证总计117项通过，并完成全生产Java编译；这些不是Android触摸分发或录屏无闪测试。本轮未构建APK或操作设备。

按最新用户要求，长滑的持续时间在两种快捷操作模式均显示，范围仍为0..1000ms并从DOWN计时。快捷操作只区分满足距离和时间后立即执行或等UP执行。此处有意不同于原版关闭快捷操作时隐藏持续时间的行为；旧long_immediate仍代表有效时间为0，用户调整时间后清除该兼容标志。切换此开关不再重建页面。
