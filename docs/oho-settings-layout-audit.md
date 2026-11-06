# OHO+ 设置信息架构审计

审计对象为本地 OHO+ 9.3.13.0 解包资源与 JADX 源码。以下是原版层级和交互证据，不是当前 EdgeFlow 已经完成的声明。目标是保留 OHO+ 的页面组织、位置和导航，ColorOS 17 负责材质与控件外观。

现有 `analysis/oho-9.3.13/reference/` 主要采集手势、工具、音量和小组件；没有找到可确认原版首页/主菜单的截图。不能把旧 visual-test 的设置截图当作三星 OHO 首页。原版不是通过 `res/xml` PreferenceScreen 构造首页，而是 `res/layout` 与 Activity 共同构造；本次结构证据来自这两者。

## 首页：真实自上而下顺序

`resources/res/layout/activity_settings_main.xml` 定义下列顺序，`SGPSettingsActivity.o()` 初始化标题、摘要和可见性：

1. 系统 ActionBar，标题 `One Hand Operation +`，右上更多菜单。`SGPSettingsActivity.java:399`。
2. 全局开关卡：64dp 高，左侧“开/关”状态文字，右侧 Switch。不是单独一行“服务状态”加多个权限按钮。
3. 两段说明：在屏幕左侧或右侧添加手势触钮；可自定义从各触钮向不同方向滑动的操作。
4. 第一对触钮：一个圆角分组，先“左侧触钮”，再“右侧触钮”，上下排列而非左右两张大卡。
5. 第二、第三对触钮：按数量条件显示，同样每对一个分组、左行在上右行在下。
6. 并排“添加触钮”“删除触钮”按钮。添加/删除的是一对，最多六个触钮；不足/达到上下限时禁用相应按钮。
7. 一个分组，两行顺序是“高级设置”在前、“手势设置”在后。
8. 触钮控制分组：**触摸宽度 → 大小 → 位置 → 触钮颜色**。

首页没有“快速工具、音量、小组件、触控板、导航、启动器”等平铺工具目录。

每个触钮行由 `settings_layout_list_item_switch_color_button.xml` 定义：左侧标题、下方动作摘要，中间靠右25dp颜色标记，右侧70dp宽开关区，开关前有竖分隔线。颜色标记在代码中只设置指示外观，不是独立颜色选择按钮；颜色选择入口在控制组。

`SGPSettingsActivity.s()` 按短横、短上、短下顺序拼接摘要；该触钮开启长滑时再接长横、长上、长下。跳过“无”，逗号连接。`u()` 的整行点击行为是：关闭的触钮先启用并返回；已启用的触钮才进入手柄配置。Switch 可以直接独立开关。

“高级设置”摘要：应用程序例外 • 动画 • 隐藏触钮（支持 S Pen 时第三项改为 S Pen 相关项）。“手势设置”摘要：快捷操作 • 滑动距离 • 振动。来源 `SGPSettingsActivity.java:192` 附近。

底部控制组并非始终显示：`controller_container` 仅在**只有一对且对齐开启**时显示。否则相关尺寸/位置移至单触钮配置。关闭全局开关会让主控制区变淡且禁用对应控制。来源 `SGPSettingsActivity.o()/m()`。

## 更多菜单：不能只照抄 menu.xml

`res/menu/menu.xml` 虽声明六项，但 `SGPSettingsActivity.onPrepareOptionsMenu():535` 修改可见性：

| 顺序 | 正常菜单项 | 条件 |
| --- | --- | --- |
| 1 | 关于 | 常规可见 |
| 2 | 恢复默认设置 | 常规可见；点击后确认对话框 |
| 3/4 | 添加触钮／删除触钮 | 被明确隐藏，使用首页按钮 |
| 5 | 通过 GTS 分享 | 仅系统 GTS 可用 |
| 6 | 权限 | `y.s0()` 中国地区判断为真时显示 |

`n6/y.java:2767` 的 `s0()` 检查 CSC 国家码或 locale CN。不能把 GTS/权限当成所有机型均显示，也不能把添加/删除重复放入更多菜单。

## 点击一侧触钮后的配置页

来源：`activity_settings_gesture_chooser.xml`，`SGPGestureChooserActivity.a()/d()/onResume()`，`a6/o.java`。

1. 返回箭头 + “左侧触钮”或“右侧触钮”标题；多对时有对应颜色标记。
2. 短距离滑动组：组标题，其下三行。
   - 左手“直线向右”，右手“直线向左”。
   - 对角线向上。
   - 对角线向下。
3. 长距离滑动组：标题行含开关和说明；开关关闭时三个长滑行 `GONE`，不是置灰占位。三行方向顺序与短滑一致。
4. 对齐触钮开关，副标题“左侧和右侧保持相同的尺寸和位置”。
5. 按条件显示的触摸宽度、大小、位置、触钮颜色。

动作行是左侧方向图标、中间方向文字与所选动作副标题；不是把动作标题当方向标题。长滑图标背景与短滑不同。`settings_layout_gesture_action_type.xml` 给出这一行结构。

尺寸位置条件：只有一对且对齐时，下层尺寸项隐藏并使用首页控制；多对或关闭对齐后，单触钮页显示尺寸/位置/颜色。触摸宽度仅 primary 的 index0/1 显示；额外触钮不是每段都有独立宽度条。源码 `SGPGestureChooserActivity.d():153`。

## 动作选择与功能配置入口

点击某方向弹出 `a6/n0` DialogFragment；动作列表来自 `gesture_action_type_arr_values` 的原顺序，经当前系统能力过滤。行结构 `settings_function_chooser_list_item.xml`：左 Radio、动作名称、可选右侧齿轮。普通行不是功能启动按钮。

`a6/k0.java:49` 的齿轮路由：

| 动作 | 配置页 |
| --- | --- |
| 快速工具 `group_control_panel` | `SGPQuickToolsConfigActivity` |
| 快速启动 `fast_action` | `SGPFastActionSettingActivity` |
| 任务切换器 `task_switcher` | `SGPTaskSwicherConfigActivity` |
| 虚拟触控板 `touch_assist` | `SGPTouchAssistConfigActivity` |
| 导航键 `show_softkey` | `SGPSoftkeyConfigActivity` |
| 小组件 `widget_popup` | `SGPWidgetPopupSettingActivity` |
| 快捷菜单 `context_menu` | `SGPContextMenuConfigActivity` |
| 隐藏触钮 `handle_hide` | `SGPHideHandleConfigActivity` |
| 向左/右旋转 | `SGPRotationConfigActivity` |

这说明这些功能的配置是从**已分配方向的动作选择器**进入，不能用首页“所有工具”列表替代原版导航路径。

## 两个全局设置页

**手势设置**，来源 `activity_settings_gesture_detail.xml`、`SGPGestureDetailActivity.onResume():115`：

- 短距离滑动：快捷操作开关 → 手势角度入口 → 滑动距离条。
- 长距离滑动：快捷操作开关 → 滑动距离条 → 长距离滑动持续时间条。原版至少一侧 primary 开启长滑才显示整组；持续时间仅长快捷操作开启时显示。长手势角度行在 XML 默认 `GONE`，不能无依据当成常显项。
- 振动：快速振动开关 → 自定义振动强度开关 → 振动级别条，具体显示受硬件/设置条件影响。

**高级设置**，来源 `activity_settings_more_settings.xml`、`SGPMoreSettingActivity.onCreate()/onResume()`：

- 应用程序例外（导航入口，无 Switch）。
- 动画（原版存在动画设置入口/开关）。
- S Pen 手势（仅支持硬件，部分屏幕模式隐藏）。
- 适配键盘（开关；原意是把触钮移至键盘上方，不等于“键盘显示时隐藏”）。
- 系统手势适配（XML默认隐藏，仅代码判定支持时显示）。
- “隐藏触钮”分组：横屏 → 快捷面板 → 外屏（仅支持外屏机型）→ 锁屏 → 主屏幕。

会话中的明确例外：用户此前要求删除手势动画调整，只保留 OHO 原版外观。不能仅为了复制原版菜单而无条件恢复动画调参；这属于用户选择，不应写成“原版没有动画设置”。

## 功能配置页的实际内容

以下来自对应 `activity_settings_*` XML，既有实现取值不同也必须明确区分：

| 页 | 原版自上而下内容 |
| --- | --- |
| 快速工具 | “点击滑块”开关（允许点击调整音量/亮度）；可排序操作列表 fragment |
| 快速启动 | “在不点击的情况下打开应用程序”开关（悬停抬手）；显示最近应用页开关；页面列数2/3/4单选 |
| 任务切换器 | 最大项目数8/9/10/11/12/13单选 |
| 虚拟触控板 | 透明度条；速度条 |
| 导航键 | 大小条；操作列表 fragment |
| 小组件弹出窗口 | 循环页面滚动开关 |
| 快捷菜单 | 页说明；应用列表；底部添加按钮；进入各应用详情配置 |
| 隐藏触钮 | 设置超时：3秒、5秒、15秒、1分钟、10分钟 |

## gulugulu 依赖结论

**不需要安装旧 gulugulu 才能构建或运行 EdgeFlow。** 本次只读检查，没有卸载或删除任何内容。

| 范围 | 当前证据与边界 |
| --- | --- |
| 构建 | `settings.gradle.kts` 仅 include `:app`；`app/build.gradle.kts` 仅 AndroidSDK 与本地 `libs/hiddenapibypass.jar`。没有旧 APK、Smali、旧工程模块或分析目录依赖。 |
| 运行 | Manifest 组件均属 `dev.edgeflow`/内置 `replica`；生产源码未发现旧包名、DexClassLoader 或旧进程调用。Root helper 全部从本应用 `ApplicationInfo.sourceDir` 启动。 |
| 当前构建产物 | 对 `app/build/outputs/apk/debug/app-debug.apk` 的5个DEX做类描述符前缀检查：包含 `Ldev/edgeflow/GestureService;` 和 `Lreplica/ToolsOverlay;`；未发现 `Lgulu/gulugulu/` 或 `Lcom/samsung/android/sidegesturepad/`。这是当前本地构建产物检查，不是仅凭 README 判断。 |
| `replica` 命名 | 是之前共同开发后迁入本 APK 的 Java 浮层/材质模块，不是运行时加载 gulugulu 的外部框架。 |
| 系统资源 | `SystemIcons` 读 `com.android.systemui` 与 `com.coloros.floatassistant` 的资源，`createPackageContext(...,0)` 不带加载代码标志；有本 APK 资源回退。依赖的是目标固件资源，不是 gulugulu。 |
| 开源库来源 | HiddenApiBypass 6.2 是此前已下载的开源库，文件已经放入本工程 `app/libs`，构建不会回读 test26 或旧 APK。 |
| 部署辅助 | `tools/device.py:51` 唯一旧包名用途是切换无障碍服务时过滤 `gulu.gulugulu.visualtest/`；旧包不存在也不影响此过滤。 |
| 一次性数据迁移 | MainActivity 只读取自身 `filesDir/migration-widgets.json`；WidgetMigration 用 provider component 分配本应用的新 widget IDs，不读取旧应用进程或继续借用旧 IDs。provider 应用（例如支付宝）仍需安装，这是正常 AppWidget 依赖。 |
| 配置导入 | ConfigArchive 与 LauncherLayout 的 legacy 字段是兼容已导出数据格式，不是旧应用运行依赖。 |

已经验证的新小组件宿主和当前配置不需要旧 gulugulu 常驻。是否保留旧版本用于回看对比，是用户选择；“是否还需要”不等于授权卸载。
