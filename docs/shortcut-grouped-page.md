# 应用快捷方式分组页

`ShortcutPicker` 现在提供可嵌入 Main 设置二级页的内容，而不是先列出所有普通应用、逐个进入弹窗查询。只有系统实际返回快捷方式的发布应用会出现在列表中。包含动态、声明式、已固定及被其他启动器固定的快捷方式；停用条目也保留，展开后显示原因并禁止选择。

## 主页面接入

```java
ShortcutPicker.Session session = ShortcutPicker.embedded(activity, selectedAction, callback);
content.addView(session.view());
session.start();
```

`view()` 返回裸 `LinearLayout`，没有内部 `ScrollView`。Main 负责唯一的页面滚动、返回、页面切换和在 `render`、`onPause`、`onDestroy` 中关闭 Session。`start()` 幂等；仅用户点“重新读取”才再次加载。

应用组行显示实际应用图标、应用名、快捷方式数量或当前已选名称，以及 Settings 原生展开箭头。展开和收起只改变子项可见性，既不修改手势配置，也不选择应用。已选项所在组首次自动展开。点击具体可用快捷方式才调用 `onSelected(actionId)`；Main 决定配置对应动作并返回父页。已保存但未被当前系统查询返回的选择会显示暂不可用提示，不伪造条目或把它降级为普通应用启动。

组行无障碍名称为“展开{应用名}”或“收起{应用名}”，状态描述给出数量/已选；支持 EXPAND/COLLAPSE。子项保留具体快捷方式名称、单选状态和停用原因。

## 查询与协议

Session 只启动一次 `su -c` 辅助进程：自身 APK 中的 `ShortcutBridge list-all <当前Android用户>`。`LauncherApps.ShortcutQuery` 不设 package，因此用一次 `getShortcuts` Binder 查询获得当前用户的完整发布集合。图标查询仍针对实际返回条目进行，但不会为每个应用再启动 Root 进程。

旧 `list <canonical shortcut identity>` 与 `start <identity>` 保留兼容。启动仍验证实际快捷方式存在、启用状态及系统 `startShortcut` 的成功返回，不以普通 Intent 替代快捷方式。

`ShortcutCatalog` 单独校验协议标记、完整 JSON、唯一规范标识、当前用户、字段类型与体积，随后按标识中的真实 publisher package 分组。普通 launcher 应用、声明了 metadata 但没有系统快捷方式的应用不会被加入。原版兼容的 package-scoped 解析仍可要求来源 package 精确匹配。

保护性上限是 4096 条、16 MiB 输出；超限明确报错，不静默截断。位图图标总传输预算 2 MiB，资源图标直接通过 publisher 资源读取。没有独立图标或位图预算耗尽时显示实际应用图标，条目本身不会被丢弃。所有外部包仅提供数据/资源，没有加载执行其代码。

## 风格、动画与关闭

Settings `coui_line_arrow_collapsed.xml` 是当前箭头来源，优先通过 `SystemIcons.raw(...,"com.android.settings:coui_line_arrow_collapsed")` 读取；`cos_coui_line_arrow_collapsed.xml` 保存完全相同的路径作为其他 ROM 的回退，颜色由实时主题设置。箭头随分组展开旋转 180°。文字、卡片、点击态、单选标记均使用现有 ColorOS 主题组件。

嵌入页使用 220 ms 的高度/透明度展开与箭头旋转；首次 GONE 子列表通过已测量的父卡片宽度取得目标高度。系统禁用动画时立即到目标状态，非零系统时长倍数由 Android animator 统一处理，避免重复乘倍数。展开不会触发 Main 整页重建。

`close()` 幂等：递增查询代次、取消 Future、终止进程、取消分组动画，并让迟到的结果/点击回调失效，`onClosed()` 只发一次。嵌入页保留现有 View、图标和当前像素，供 Main 的 350 ms 页面退场使用；不在关闭时清空旧页面。视图移出树后按普通 Android 生命周期释放。窗口兼容 `show(...)` 复用同一分组列表；Activity 仍可用现有 ActionSheet 包装，服务浮层保留 Glass 和 `FLAG_NOT_FOCUSABLE`，没有为了选择器抢媒体焦点。

## 本地验证

全部生产 Java 编译成功。`validation/shortcut-catalog/run.ps1` 的 **44 项断言**覆盖：空集合不产生应用、多个 publisher 分组、停用项/已选摘要、同 ID 跨 publisher、300 应用完整保留、跨用户拒绝、旧 package-scoped 兼容、错误词标签、重复/残缺协议、字段/图标/资源类型及 helper user 参数校验。

这里记录实现和 JVM 测试。Android 17 的跨用户 Binder 查询、展开动画及选择后的导航另做设备测试。
