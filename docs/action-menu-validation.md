# 0.3.9 操作选择与应用二级页面

2026-11-06，当前一加 PKR110 / ColorOS 17。版本 `0.3.9-action-menu`，versionCode 12。

## 界面与导航

- 操作标题由 16sp / 400 改为 16sp / 500，对应本机 Settings 的 `couiTextAppearanceHeadline6` 与 `sans-serif-medium`；普通摘要保持 13sp / 400。列表有效左右内边距为 24dp，56dp 最小行高保留，并允许大字号自然增高。
- `ActionCatalog` 仅定义展示分组：无、导航、屏幕、音量与媒体、系统开关与设置、需要设置。60 个原有动作 ID 与执行器注册数组保持不变，七个可配置动作及两种外部动作统一位于列表末尾，使用系统强调色。
- 操作选择和参数设置是两个独立点击区域。标题区域保留单选语义，右侧设置按钮可单独获得无障碍焦点。已配置的应用、快捷方式摘要加入朗读内容。
- 删除 `chooserReturnKey` / `chooserReturnScreen` 与应用配置完成后的自动重弹逻辑。进入快速工具、小组件等设置页后返回对应触钮页，不再次打开操作弹层。
- 启动指定应用、应用快捷方式改为完整二级设置页，使用已有 `ColorSettingsScaffold` 与 350ms `ColorPageMotion`。配置目标和选中操作保持分离：仅修改未选中的目标不会改变当前手势；已选同类型的目标会更新。退出不提交新目标。
- 系统配色、字体和深色模式继续通过 `ColorOsTheme` 获取。没有引入 AOSP AlertDialog 作为新的应用选择页面，也没有修改已验收的手势浮层材质。
- 真机首帧检查发现并修复初始化遗漏：`ColorOsTheme.watch` 只订阅变化，因此新控件必须先执行样式回调，再订阅。操作行点击态以及快捷方式文字、卡片、箭头均已补齐首次初始化。

## 快捷方式

通过现有 Root 桥接，一次查询当前 Android 用户的系统快捷方式，按实际发布应用分组。只显示有快捷方式记录的应用，使用真实应用图标；箭头展开具体入口，展开或收起不修改手势。停用的快捷方式保留停用状态，不能选择。只有选择具体入口才提交目标。

异步结果带请求代次；退出、后台或重建关闭旧会话，迟到结果不能回填新页。嵌入会话关闭时保留旧视图供退场动画绘制，避免先清空页面再退出。

## 验证

- `validation/action-catalog/run.ps1`：37 项，覆盖实际执行器注册表完整性、展示分组、配置目的地及真实 `ActionTarget` 的配置/选择/取消语义。
- `validation/shortcut-catalog/run.ps1`：44 项，覆盖完整系统响应、真实发布应用分组、停用/已选条目、用户与标识校验。详见 [快捷方式分组页](shortcut-grouped-page.md)。
- `tools/verify_action_menu.py`：操作分组与实际边距、七个配置页面返回、应用二级页配置与选择分离；测试修改限于指定动作及目标字段，退出后恢复。
- `tools/verify_shortcut_subpage.py`：真实快捷方式查询、展开/收起不提交、配置与选择分离、取消及已选应用自动展开。
- 真机已确认 62 个入口及底部九项顺序、24dp 左侧实际内边距；七个配置页返回均不重弹，快速工具再进入按钮编辑页后返回也通过。应用目标配置不改当前动作，随后可独立选择；取消保留目标，测试改动已恢复。证据：`verification/action-menu-ui-validation.json`。
- 系统实际返回 64 个快捷方式发布应用。展开/收起不修改配置，设置目标与选择手势分离，已选应用自动展开，返回保留目标；测试配置已还原。证据：`verification/action-menu-shortcut-validation.json`。
- 1.6 倍字号下选择页标题、底部按钮和末组入口均可用；应用二级页经历深色模式重建后仍保持正确页面与返回路径。字体和夜间模式已恢复。证据：`verification/action-menu-environment-validation.json`。
- 修正初始化后的最终 APK 已重新安装并核对首屏/首次展开：卡片、字重、颜色和箭头正常，日志确认展开箭头来自已安装的 Settings 资源。证据：`verification/action-menu-first-frame-validation.json` 以及 `action-menu-shortcuts-final` / `action-menu-shortcuts-expanded-final` 截图。
- 其他快捷方式、环境与最终安装结果分别记录在本地 `verification/action-menu-*`。这些运行记录以实际执行结果为准，不以源码检查代替真机结果。

快捷方式范围由应用向 Android 发布的数据决定；应用内部未发布的页面不会凭空生成入口。现阶段实机验证对象仍为当前已连接的一加。
