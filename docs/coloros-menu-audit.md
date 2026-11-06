# ColorOS 设置、弹窗与子菜单源码审计

2026-11-06。根据 `app/src/main/java`、manifest 和资源整理页面、菜单及旧配置入口。这里列代码路径，设备结果见[0.3.12 测试记录](coloros-menu-validation.md)。

## 判定口径

- **已统一**：入口使用本项目的 ColorOS 字体、颜色、控件、页面或菜单组件，而非直接展示 AOSP 默认菜单。
- **玻璃浮层保留**：使用已接受的 `PanelStyle` / `ColorOsSurface` / `ColorMotion` 管线。白色图标、半透明背景不按设置页的实色背景重置。
- **残留**：确有代码差异或遗漏，下面给出具体位置与影响，不能据此宣称设备上必然出现某种像素问题。
- **外部界面**：由系统、输入法、目标应用或 AppWidget provider 绘制，本项目只负责发起入口。

源码位置以方法名为主，行号对应记录时的版本。

## MainActivity 全部页面路由

共同骨架：`MainActivity.render()` → `ColorSettingsScaffold` + `ColorSettingsScrollView` + `ColorPageMotion`；普通行使用 16sp / 500 主文字与 13sp / 400 说明，卡片、分隔和返回/进入图标由统一主题与系统资源提供。路由定义见 `MainActivity.java:64–75`。

| 页面 ID / 入口 | 内容与控件 | 源码结论 |
|---|---|---|
| `home` | 总开关、服务状态、1–3 对触钮、添加/移除触钮、全局入口、联动几何设置 | `HomeControls` 局部刷新；`ColorSwitch`；已统一 |
| `left` / `right` / `extra_left` / `extra_right` / `extra2_left` / `extra2_right` | 短/长滑方向动作、长滑开关、触钮尺寸/位置/颜色 | `side()` / `directionRows()` / `geometry()`；统一行、系统方向图标、自有滑条 |
| `advanced` | 应用例外、横屏/通知面板/桌面/键盘条件隐藏 | `advanced()`；统一卡片、`ColorSwitch` |
| `gestures` | 快捷操作、短/长距离、持续时间、快速振动、自定义强度 | `gestureSettings()` / `vibrationSettings()`；`ColorSlider` / `ColorSwitch`，数值与说明层级统一 |
| `angle` | 短/长滑两个可拖动角度图，左右镜像按钮 | `angleSettings()` / `AngleEditorView`；原生 Canvas 与可调无障碍节点，非图片模拟 |
| `exceptions` | 真实应用图标、排除项复选框 | `excludedApps()`；后台加载、`ColorCheckBox`；已统一 |
| `tools` | 编辑快速工具、预览 | `toolsSettings()`；统一设置入口，编辑进入独立 Activity |
| `widgets` | 循环翻页、打开小组件面板 | `widgetSettings()`；统一设置页，面板保留玻璃材质 |
| `launcher` | 最近应用页、列数、编辑快速启动 | `launcherSettings()`；统一设置行 |
| `launcher_columns` | 2/3/4 列；仅现存 5 列时显示兼容选项 | `launcherColumns()`；独立二级页，复用动作单选行 |
| `tasks` | 显示任务数量、预览任务切换器 | `taskSettings()`；卡片内 `ColorRadioButton`，非旧居中列表 |
| `touchpad` | 光标速度、预览 | `touchpadSettings()`；统一滑条与入口 |
| `navigation` | 横竖排列、大小、按键顺序、添加、预览 | `navigationSettings()`；统一控件；每项编辑是实际齿轮锚点菜单 |
| `navigation_action` | 分组的可选导航动作、当前选中态 | `navigationActionSettings()`；独立二级页，过滤重复与不适用动作 |
| `keyboard` | 按应用保存组合键的说明、预览 | `keyboardSettings()`；统一设置页；具体编辑在玻璃浮层 |
| `action_app` | 为当前手势选择真实应用 Activity | `applicationActionSettings()`；二级页、40dp 图标、单选状态、异步加载 |
| `action_shortcut` | 有真实快捷方式的应用分组、展开箭头、具体入口 | `shortcutActionSettings()` → `ShortcutPicker.embedded()`；无嵌套滚动容器，展开不保存 |
| `permissions` | 无障碍、后台、自启动、运行时权限、勿扰 | 页内行已统一；点击后为外部系统界面 |
| `backup` | 导入、导出、迁移报告入口 | 页内行已统一；文件选择为系统 SAF |
| `widget_migration` | 迁移状态、数量、需要配置的 provider | `widgetMigrationReport()`；统一卡片和说明；不弹原始 JSON |
| `about` | 显示名、版本、项目说明 | `about()`；统一卡片 |

旧 `screen="color"` 在恢复状态时回退，不再是可达颜色设置页。当前颜色入口直接打开调色板。

### Main 内部非页面入口

| 入口 | 当前实现 | 关闭与导航 |
|---|---|---|
| 首页更多 | `moreMenu(View)` / `ColorPopupMenu` | 实际更多按钮为锚点；选项退出后进入权限/备份/恢复/关于 |
| 移除触钮对 | `removePair()` / `ColorPopupMenu` | 以移除按钮为锚点；说明配置保留；破坏性文字与取消分离 |
| 恢复默认 | `resetSettings()` / `ColorDialogBuilder` | 简短确认，`setPositiveDestructive`；不伪装成长选择列表 |
| 手势动作选择 | `chooseAction()` / `ColorActionSheet` | 分组、自有单选、可选齿轮；嵌入当前页面，选择退出后提交，齿轮子页返回可恢复 chooser |
| 触钮颜色 | `showColorPalette()` / `ColorPaletteSheet` | 色板/色环/透明度/HEX；取消不写入，确认提交；输入框与底部操作区使用统一组件 |
| 导航按键编辑 | `editNavigation(View,int)` / `ColorPopupMenu` | 更换、前移、后移、移除按实际位置筛选；更换进入 `navigation_action` |

## Activity 与浮层子菜单清单

Manifest 的自有 Activity 为 MainActivity、ToolsSettingsActivity、ToolsActivity、WidgetActivity。后两者采用透明浮动主题作为窗口容器，不使用该主题的默认菜单内容。

| 所属入口 | 完整子入口 | 当前组件 / 状态 |
|---|---|---|
| 快速工具编辑 `ToolsSettingsActivity` | 3×4 草稿格、可用功能、恢复默认、保存、长按排序、移除 | ColorOS scaffold / 主题文字 / 真实系统与应用图标 / 自有触控态；草稿与已保存布局分离 |
| 快速工具编辑 → 添加应用 | 搜索、真实图标应用列表、返回 | `ColorAppPickerPage` 独立二级页；固定搜索、后台读取、无居中应用列表 |
| 快速工具编辑 → 未保存返回 | 放弃/继续编辑 | `ColorDialogBuilder` 确认；破坏性按钮；Activity 销毁时关闭 |
| 快速工具浮层 / ToolsActivity | 亮度、音量、媒体、三排按钮 | 保留 `ToolsPanel` 的原有玻璃基底和 `GlassSeekBar`；系统图标，不是默认 SeekBar 外观 |
| 快速工具 → 屏幕超时 | 15 秒至 10 分钟单选 | `GlassDialogBuilder` 统一底部选择面板；非输入子面板不抢焦点 |
| 小组件 → 页面菜单 | 添加组件、添加页面、高度、循环、编辑/完成、删除页面 | `WidgetActivity.pageMenu(View)` → 真实锚点 `ColorPopupMenu` |
| 小组件 → 高度 | 紧凑 / 标准 / 宽敞 | 沿用原三档值；同锚点短菜单 |
| 小组件 → 添加 | 搜索、provider 图标、组件名、应用名、加载/空/错误态 | `WidgetProviderPicker` + `GlassDialogBuilder`；后台读取、76dp 行、自有样式 ListView；列表区域常显，状态使用等高不可点占位 |
| 小组件 → 编辑工具条 | 添加 / 完成 / 页面 | 系统图标 + 主题文字 + 统一点击态；原 36dp 工具条与主面板几何保留 |
| 小组件 → 组件菜单 | provider 设置、前/后页、底色、移除 | `menu(JSONObject,View)` / 锚点菜单；无设置页的 provider 不显示“组件设置”；首页不显示前一页 |
| 小组件 → 底色 | 浅色 / 深色 / 透明 | 同锚点短菜单；用户明确设置的是组件卡片底色，不自动改成主题卡片色 |
| 小组件 → 删除组件/页面 | 明确删除确认 / 取消 | `GlassDialogBuilder` + 破坏性按钮；退出完成后执行；没有因审计删除组件 |
| 小组件 → 拖动/尺寸 | 编辑态移动、边缘缩放 | `WidgetGrid` 既有交互；不存在遗漏的默认尺寸选择器 |
| 快速启动 → 添加 | 应用 / 系统动作 / 应用快捷方式 / 文件夹 | 实际按钮锚点 `ColorPopupMenu` |
| 快速启动 → 应用 / 系统动作 | 真实图标多选 / 分组动作多选、保存、取消 | Glass 底部面板；16sp/500 标题、13sp/400 说明、自有 `ColorCheckBox`；多选仅保存时应用 |
| 快速启动 → 应用快捷方式 | 真实应用分组、展开、禁用条目状态 | 复用 `ShortcutPicker.show()`；Accessibility Overlay 使用 Glass，非 AOSP 列表 |
| 快速启动 → 文件夹 | 新建、重命名、移入已有/新文件夹、删除确认 | 统一输入框与 Glass 表单；移动/新建串联使用 `dismissThen` |
| 快速启动 → 入口长按 / 编辑 | 前后移动、首末页、文件夹、移除；最近项添加常用或切任务 | 实际入口锚点 `ColorPopupMenu`；父层触摸有 gate，防子菜单关闭穿透 |
| 快速启动 → 选项 | 列数、最近应用页、新文件夹 | 锚点菜单；列数为 Glass 单选；退出后刷新父层 |
| 任务切换器 → 数量 | 8–13 与已保存的旧值 | Glass 单选；已改 `dismissThen` 后更新数量与任务列表 |
| 键盘快捷方式主面板 | 当前应用、快捷方式名/组合键、添加/关闭 | Glass 自有列表，72dp 行；主面板保留作为编辑的父层 |
| 键盘快捷方式 → 每项管理 | 修改、前移、后移、删除 | 编辑图标或长按的锚点菜单；不适用移动项被隐藏 |
| 键盘快捷方式 → 添加/修改 | 名称、组合键、校验错误、保存/取消 | 统一输入框/说明/Glass 表单；需要输入时允许 IME，其余浮层保持不抢焦点 |
| 动作 `screen_timeout` | 直接手势触发的屏幕超时 | `ActionExecutor.timeout()` 同样使用 Glass 单选，并非只改快速工具内入口 |
| 旧动作 `app_launcher` | 已保存旧配置仍可打开的应用启动器 | `ActionExecutor.apps()` 后台加载真实图标、72dp 自有适配器 + Glass；新动作菜单不再新增此旧入口 |

`WidgetActivity` 为菜单、Dialog、provider worker 统一维护生命周期与 generation。render / onPause / onNewIntent 引起的 render / finish / onDestroy 会清理旧编辑器；绑定、删除的延迟回调还检查 generation。Provider 选中前不分配 host ID。

## 共享组件与 AOSP 残留扫描

| 扫描项 | 结果与解释 |
|---|---|
| `new AlertDialog.Builder` / `new PopupMenu` | 当前生产源码未找到直接实例化。`ActionExecutor.dialog()` 的返回类型虽是 `AlertDialog.Builder`，实际对象为 `GlassDialogBuilder` |
| `new Switch/SeekBar/RadioButton/CheckBox/EditText/Spinner/NumberPicker/DatePicker/TimePicker` | 未找到直接展示这些默认控件的构造入口。监听器的 `SeekBar` 类型不等于默认外观 |
| `new Button` / `ImageButton` | Button 仅共享 `ColorDialogBuilder` 操作区，显式清默认 elevation/state animator/minWidth；放大镜 ImageButton 显式使用系统向量、玻璃按钮背景与 padding；均不是默认外观遗漏 |
| `ColorSwitch` / `ColorSlider` | 自绘轨道、滑块与运动；去掉默认 background/thumb/progress drawable |
| `ColorRadioButton` / `ColorCheckBox` | 保留标准 API / 无障碍语义，使用 `ColorChoiceDrawable` 替代默认按钮图形 |
| `GlassSeekBar` | 快速工具专用自绘条；默认 thumb/background 被清除，保留已接受的半透明轨道与白色填充 |
| `ListView` | 仅统一 Builder 与 WidgetProviderPicker 等显式样式列表；无默认 divider、默认 selector 或 `android.R.layout` 行模板 |
| 原始 `Dialog` | Tools/Volume/Launcher/Tasks/Touchpad/Navigation 等玻璃窗口容器，均自建 content；不能仅因使用 Dialog 类判为 AOSP 外观 |
| `ColorDialogBuilder` | 长列表、自定义表单为底部 sheet；短确认仍为圆角确认框。自有标题、关闭、固定操作区、单/多选行、主题刷新、退出后回调；窗口可用区域交由 `ColorWindowSpace` 统一处理，包含 IME 限高 |
| `GlassDialogBuilder` | 复用上述内容与生命周期，保留原生玻璃材质和 overlay 窗口 flags |
| `ColorPopupMenu` | 真实锚点、屏幕/窗口边界约束、系统字体/主题色、圆角点击态、退出后回调；非默认 PopupMenu |
| `ColorActionSheet` | 用于动作与调色板；独立底部容器，支持 suspend/resume、嵌入主页面、关闭后派发 |
| `Theme.Material*` 父主题 | 仍用于 Activity/Dialog 的 Android 窗口基础属性。实际可见控件已自绘/自建，不能把父主题名当成视觉残留证据 |

## 图标与字体来源

- 主设置返回/进入、齿轮、展开箭头、更多菜单及工具按钮通过 `SystemIcons` 或导出的 `cos_*` vector 获取系统资源；应用、AppWidget、快捷方式图标来自各自真实 provider。
- `SystemIcons.raw()` 优先读安装包资源，再用本地同名 `cos_*` vector；两者均缺失时目前使用本地 ColorOS 设置图标兜底。**这不是各动作独立图标的等价替代**，其他固件缺资源时仍可能出现泛化齿轮。当前源码扫描已无 `android.R.drawable` / `android.R.layout` 直接引用。
- `GestureWaveView` 仍使用 `oho_gesture_arrow`：这是明确保留的 OHO+ 手势反馈图形，不是遗漏的设置菜单图标。
- launcher adaptive / monochrome 图标是品牌资产；前台通知使用独立 `ic_stat_hand_operation`。不把品牌资产改为系统设置图标。
- 触控板、导航、任务切换、放大镜、自由移动等已接受的玻璃浮层保留白色前景；这一点与浅色设置页动态文字角色不同，不能据此直接认定未统一。

## 发现、源码修复与剩余边界

### 发现后已在源码确认修复

1. `MainActivity.angleSettings()` 镜像按钮最初只有 `ColorOsTheme.watch`，首帧未设置背景。当前已有 `mirrorStyle.run()` 后订阅。
2. `TaskSwitcherOverlay.limit()` 最初在 sheet 退出期间立即清任务列表。当前已使用 `ColorDialogBuilder.dismissThen` 后写入和 `load()`。
3. `MagnificationOverlay.text()` 最初遗漏系统字体设置。当前已有 `ColorOsTheme.typeface` 首次设置及 watch。
4. `ColorDialogBuilder.ErrorLabel` 原为固定红色；当前使用 `ColorOsTheme.error()`，TextStyle 有独立 error 角色以支持主题刷新。
5. `ToolsSettingsActivity` 的空位、加/减角标与 `MagnificationOverlay` 的加减按钮原为文字图形；当前使用系统 `ic_add_24dp` / `ic_remove_24dp`。移除角标也已使用主题 error 色。
6. `QuickLauncherOverlay.text()` 主玻璃页当前已为字体添加首帧设置与 live watch。
7. `WidgetProviderPicker` 原来用 ListView 的 GONE/VISIBLE 切换 loading/empty，改变面板自然高度。当前列表保持可见，状态用等高、不可点占位行；列表数据在按压期间改变会取消旧按压，点击还核对条目身份。

对应设备结果见下方测试链接。

### 保留的固件适配边界

| 位置 | 确认差异 | 建议 / 边界 |
|---|---|---|
| `SystemIcons.raw()` 最后一级 fallback | 未收录或固件不存在的名称统一回退为设置图标 | 未收录的动作仍需补齐图标 |

本轮静态检查未再定位到可达的默认 AOSP 菜单或选择控件入口。此结论不替代大字体、IME、窗口切换和不同固件上的实际检查。

`ToolsPanel.label()` 中仍有旧固定蓝色辅助方法，但当前全文件只有定义、没有调用；当前不影响界面。

### 外部界面，不属于自有菜单遗漏

- 系统无障碍、电池优化、自启动管理、通知/蓝牙/相机运行时授权、勿扰权限。
- SAF 导入/导出文件选择器、系统最近任务、通知/快捷面板、系统录屏、ColorOS 侧边栏及其他被启动的系统功能。
- AppWidget 的系统绑定授权、`startAppWidgetConfigureActivityForResult()` 打开的 provider 配置页，以及 provider RemoteViews 的内部文字、按钮和布局。
- 系统输入法、文本选择/复制粘贴浮动工具条、系统 Toast、通知栏的最终展示、Root 授权管理器的授权页。
- 真实应用图标、快捷方式自带图标和任务快照不应统一染色成单色系统功能图标。

## 设备验证栏

实际执行路径、发现并修正的问题、键盘和深色/放大字体检查见 [实机测试](coloros-menu-validation.md)。此表仍按源码入口清单使用，不把未触发的每个外部应用或每种窗口尺寸都算作实测通过。
