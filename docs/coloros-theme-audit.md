# ColorOS 设置主题与编辑器核对

本轮只读对照已安装版本的 Settings APK 解包资源、定向反编译类和本地 Oplus framework 源码；未加载外部 APK 的 Java 类。设备主题切换与恢复由主验证流程单独记录。

## 颜色、字体与材质

- Settings 的 `AppBaseTheme` 和 `AppBaseDarkTheme` 均存在，分别继承 `Theme.COUI.Main` / `.Dark`（`analysis/coloros17-settings/resources/res/values/styles.xml:458,480`）。读取 Settings 自身资源与主题属性，可保留系统资源覆盖的配色。MaterialYou JSON 的 seed 不直接等于 COUI 强调色，应用只观察该键并重新读取系统资源。
- `COUIThemeOverlay.resolveThemeStyle` 按 `mMaterialColor` 的低 16 位选择颜色、16–23 位选择模式（`single/COUIThemeOverlay.java:238-285`）。高 32 位是框架配置刷新代数，不能用来判断“蓝色模式”或“壁纸模式”；依据为本地 `OplusActivityTaskManagerService.getMaterialForUser`。当前单色数组仍使用 OnePlus 的 `coui_theme_arrays_single_patch_p`，对应本项目目标设备；其他品牌和旧主题兼容版本的 patch/repatch 分支未宣称等价。
- 新增与 `COUIThemeOverlay.isRejectTheme` 相同的优先级条件：非夜间、`mThemeChangedFlags & 1`，且主题确实包含 `com.android.settings` 单包资源时，跳过普通颜色 overlay，使主题商店 skin 的资源保留优先级。`flags & 256` 使用 `/my_company/media/theme` 和 `custom_theme_path_setting` 分支；其他 skin 使用 `/data/theme[/userId]/com.android.settings`（原类 `hasCustomThemePkg`、`hasDataThemePkg`、`isRejectTheme`）。应用只读文件属性，不获取主题文件内容或 Root 权限。
- 部分系统限制第三方读取主题路径或 extra configuration。此时继续使用可取得的 Settings 资源与普通配色，**不假定 skin 已存在**。`ColorOsTheme.describe().skin` 明确区分 `settings-package-skin`、`inactive`、`night-mode`、`no-settings-package-skin` 与 `unverified-flags` / `unverified-package-access`。受限设备上的主题商店 skin 精确匹配仍需验证，不能只凭主题商店已启用推断。
- 正文字体继续来自 `Typeface.DEFAULT` 派生字体；不把 `font_variation_settings=550` 误写成所有文字固定 `'wght' 550`。Settings `COUIChangeTextUtil:39-47` 使用 DEFAULT/medium；framework `OplusFontUtils.replaceTypefaceWithVariation:383-390` 会为未手动固定 axis 的系统字体应用当前用户字体变化。该 setting 还包含状态位（`FontVariationUtils:113-122`），不是单独的字重数字。
- 材质许可同时受 Settings 的 `coui_blur_enable && !is_immersive_theme`、系统模糊/描边开关和省电影响；原 `COUIMaterialStateManager` 的开关读取默认值为 0。硬件/供应商私有渲染管线完整等价性不在这次独立控件实现的声明范围内。

## 本轮发现及修复

- 动作选择器原先在统一弹窗显示后强制全宽，覆盖最大宽限制；Main 由页面代理修复为只调整高度，并使用共享滚动容器。
- 自定义弹窗内容原先被递归强制成 400 字重和主文本色。现在保留调用方原有主/次/强调色角色与字重，主题改变时按相同角色更新；复选/单选控件仍保留其自身状态颜色。自定义标题亦加入刷新，输入内容、选择范围和错误文字不会因为换色被重建。
- 快速工具编辑器跨 Activity 的进入/退出不再依赖默认窗口动画。独立资源复现 Settings `coui_open/close_slide_*` 的 350ms、100% 前页移动、30% 后页移动、后页 1↔0.5 透明度和四条源曲线；API34+ 使用 `overrideActivityTransition`，API33 使用 `overridePendingTransition`。系统负责 transition scale，应用不再次乘倍。
- Java 扫描未发现生产设置页仍直接创建默认 Switch、SeekBar、RadioButton、CheckBox、PopupMenu 或默认列表行布局。AlertDialog 仅保留 API/lifecycle 容器，实际内容由 ColorDialogBuilder 绘制；系统权限页、文件选择器和系统文本编辑服务仍归系统管理。

此处静态核对和编译不能替代设备对字体、主题商店 skin、弹窗换色和 Activity 过渡的视觉验证。
