# 小组件与桌面卡片数据链路审计

更新时间：2026-11-12。本文只比较已经安装、无需下载推荐内容的本地组件和卡片。

## 结论

CHO 当前读取的是 Android `AppWidget` 提供方。桌面卡片使用 Launcher 内的 Pantanal／Seedling 卡片引擎。两者可以显示同一个应用的相关内容，但不是同一个实例，也不保证读取同一份状态。

因此，“内容不一致”需要分成两类：

- **数据不一致**：两个入口读取的业务数据、账号状态或卡片实例不同。
- **渲染不一致**：数据相近，但桌面使用卡片引擎和专用布局，CHO 使用提供方返回的 `RemoteViews`。

本轮没有把两者强行合并，也没有伪造桌面卡片 ID。

## 两条链路

### CHO 普通小组件

1. `WidgetProviderPicker` 枚举 `AppWidgetManager.getInstalledProvidersForProfile()`。
2. `WidgetPanel` 分配并尝试绑定 `AppWidgetHost` 的 ID，需授权或配置时由 `WidgetActivity` 接收系统和提供方的结果。
3. `SizedWidgetHostView` 接收提供方的 `RemoteViews`，按提供方声明和实际内容适配格位。
4. 数据刷新、账号状态、配置页和点击动作由提供方应用负责。

CHO 保存的是提供方组件名、AppWidget ID、资料和网格位置。它没有保存桌面卡片的 `cardId`、`cardType` 或 `hostId`。

### Launcher 普通小组件

桌面同样可以通过 `AppWidgetHost` 绑定普通小组件，但额外使用 `LauncherAppWidgetInfo`、桌面宿主的 padding、卡片对齐和 `CustomLauncherAppWidgetHostView`。同一个提供方在两个宿主中可能因为尺寸选项、内边距和宿主渲染策略不同而出现视觉差异。

这类差异可以通过匹配提供方、资料、尺寸选项和刷新时机逐项缩小，但不能据此推出它与桌面卡片是同一来源。

### Launcher 卡片

桌面卡片由 `LauncherCardInfo` 和卡片引擎管理，关键实例字段包括：

- `mCardType`
- `mCardId`
- `mHostId`

`CommonCardView` 通过 `CardManager` 和 `CardModelWrapper` 创建卡片模型，再交给 Seedling／Pantanal 引擎渲染。卡片更新使用卡片配置、服务 ID、类别和卡片模型；拖动预览甚至可以直接取 Seedling 视图截图。

已检查的卡片引擎入口不能直接交给 Android `RemoteViews` 宿主。CHO 目前没有对应的卡片实例记录或卡片引擎宿主。

## 便签是最清楚的例子

便签同时存在两种入口：

- 旧 `NoteWidgetProvider`：受 `oppo.permission.OPPO_COMPONENT_SAFE` 保护，并配合桌面读写权限；它使用便签自己的 widgetId 与笔记映射。
- 新卡片选择页：`NoteSelectActivity` 接收 `cardId`、`hostId`、`cardType`，再通过内部键生成笔记本或笔记映射。其 Activity 和卡片 Provider 受 `com.oplus.permission.safe.ASSISTANT` 保护。

旧小组件的 AppWidget ID 不能替代新卡片的 `cardId`／`hostId`。即使 CHO 被挂载为系统应用，当前设备仍没有授予这些签名权限，因此不能安全地从卡片入口读取或修改便签卡片内容。

## 日历和时钟的差异

日历月视图和世界时钟的普通入口能作为 `AppWidget` 被 CHO 托管，已经实测显示和滚动正常。日历应用本身还使用 `CardModel`、`CardConfigInfo` 和订阅服务管理卡片内容；这些卡片配置与月视图 `RemoteViews` 不是同一个对象。

需要额外下载的推荐卡片不纳入本审计。已有本地卡片所用的联网数据和订阅状态仍需逐项核对，本轮没有完成这部分内容对照。

## 能做什么

### 可以直接改进

- 为同一应用建立“普通 AppWidget 对照组”，核对提供方、资料、尺寸选项、配置状态和刷新时间。
- 把差异分成“数据不同”和“布局／渲染不同”，不要只比较截图。
- 对普通 AppWidget 继续复用桌面的尺寸、padding、滚动和固定内容适配逻辑。
- 在 CHO 中显示“普通小组件”与“桌面卡片”是不同入口，避免用户把两者当成同一实例。

### 需要系统桥接才能做

- 读取 Launcher 的 `LauncherCardInfo` 和卡片实例列表。
- 调用 `CardManager`／Pantanal／Seedling 创建、刷新或销毁卡片。
- 接入便签卡片的 `cardId`、`hostId`、`cardType` 映射。

这些工作需要当前固件的签名权限、桌面进程协作或受控系统模块。不能通过复制 ID、伪造 Intent 或把 AppWidget 绑定记录改名来完成。

## 后续方案

下一次若继续处理，建议按以下顺序：

1. 先为日历、时钟、便签和音乐建立普通 AppWidget 的内容快照。
2. 在桌面只记录同一应用卡片的实例字段和显示内容，不修改任何卡片数据。
3. 先判断是否只是渲染差异，再判断是否存在稳定、可授权的数据接口。
4. 只有确认有稳定桥接能力后，才设计“桌面卡片”入口；否则保留普通小组件并明确能力边界。

当前结论：CHO 的普通小组件接入已经覆盖常规 `AppWidget`；桌面专用卡片仍属于独立能力，暂不接入。
