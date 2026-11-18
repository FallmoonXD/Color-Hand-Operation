# 开发笔记

使用和安装先看[仓库首页](../README.md)、[功能说明](feature-status.md)和[兼容说明](compatibility.md)。这里放实现细节和测试记录，方便查问题、接着改代码。

修改界面前先查[逐页、逐组件 COUI 选型](coui-ui-audit.md)，其中列出了每个入口的目标结构、COUI 参照和渲染方式。查现有绘制路径与实机证据，可看[ColorOS 17 渲染方案](coloros17-rendering-plan.md)。

页面、浮窗、手势和控件的动画参数见 [ColorOS 17 动效对照](coloros17-motion.md)。

## 从哪里看代码

| 模块 | 入口 | 相关笔记 |
| --- | --- | --- |
| 设置与页面导航 | `MainActivity`、`ColorSettingsScaffold` | [页面清单](coloros-menu-audit.md)、[页面连续性](continuous-pages-validation.md) |
| 手势输入与动作 | `PersistentRuntime`、`NativeInputSession`、`GestureRecognizer`、`ActionExecutor` | [手势测试](gesture-validation.md)、[触控区域](handle-safe-area-validation.md) |
| LSP 模块与独立调试原型 | `lsp/` 的 `ModuleEntry`、`NativeInputSession`、`EdgeInputSession` | [原型与验证边界](lsp-input-prototype.md) |
| 主题与控件 | `ColorOsTheme`、`ColorSwitch`、`ColorSlider` | [主题](coloros-theme-audit.md)、[控件](coloros-controls.md) |
| 弹窗与菜单 | `ColorPopupMenu`、`ColorActionSheet`、`ColorDialogBuilder` | [弹出菜单](settings-popup.md)、[动作选择](settings-action-sheet.md)、[表单](dialogs-unification.md) |
| 浮层材质 | `NativeBlur`、`NativeMaterial`、`PanelStyle` | [材质开关与生命周期](material-policy.md)、[原生 UI 调查](coloros17-native-ui-research.md) |
| 配置和小组件 | `AppPrefs`、`ConfigArchive`、`WidgetMigration` | [配置测试](config-validation.md)、[接入范围](widget-provider-compatibility.md)、[桌面卡片差异](widget-card-data-audit.md) |
| 图标 | `design/`、`tools/build_brand_icon.py` | [当前彩色图标](user-layered-icon-validation.md)、[素材来源](../THIRD_PARTY_NOTICES.md) |

包名和配置仍使用 `dev.edgeflow` / `edgeflow`。`replica/` 是本项目的 UI 模块名，构建时不需要原始参考 APK。

## 构建与调试

JDK 21 + 仓库自带 Gradle wrapper；SDK 配置和普通构建命令见[首页](../README.md#自己编译)。

`tools/deploy.py` 用于开发设备：覆盖安装调试包并配置相机、蓝牙、小组件绑定等开发权限。模块加载状态在「权限与运行」查看。只有一台已授权设备时自动选择；多台设备时先设置 `EDGEFLOW_SERIAL`。旧视觉测试版迁移需要显式传入 `--migrate-visualtest`。

验证脚本放在 `validation/`，各目录有运行入口。手机 UI 树用独立 probe 读取，避免普通 `uiautomator dump` 干扰正在运行的无障碍服务。涉及实机 UI 时先临时保持亮屏，结束后恢复超时并息屏。

发布打包使用 `tools/package_release.py`。先从预定发布提交构建并验收 release APK，在干净工作树运行脚本。它核对公开证书和 APK 提交号，从即将分发的源码 ZIP 临时重建，再比较 APK 内各文件的内容与压缩方式。源码 ZIP 缺少 Git 元数据，因此提交号从待分发 APK 单独读取。校验通过后，`dist/` 收录实机验收过的原 APK、源码 ZIP 和校验和。可用 `ANDROID_HOME` 或 `ANDROID_SDK_ROOT` 指定 SDK。

## 测试记录怎么读

文件名中的版本对应当时的代码。旧记录里的截图、测试次数和结果只属于那次测试；当前图标或页面可能已经改过。离线检查和实机步骤分别记录。

`verification/` 保存本机截图、日志和设备状态，不提交到 Git。旧文档中这些路径是本地记录位置，GitHub 上没有对应下载。APK 解包、反编译和厂商原始文件同样留在工程外的 `analysis/`，公开文档只保留实现说明和来源索引。

- [更新记录](../CHANGELOG.md)：按版本找修改。
- [当前开发状态](PROJECT_STATE.md)：接续开发前需要知道的约定和待测项。
- [一加早期实机记录](device-validation.md)：手势、工具、小组件和后台恢复。
- [0.3.15 测试记录](neutral-glass-release-validation.md)：能力探测和白灰图标阶段。
- [OHO+ 功能对照](oho-feature-matrix.json)：已实现、未实现和差异项。
- [OHO++ 实机对照](ohoplus-device-comparison.md)：普通安装的后台退出、输入接管、工具和小组件差异。
