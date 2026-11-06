# Color Hand Operation · 0.3.2

2026-11-05，OnePlus PKR110 / ColorOS 17 实机验证。此记录对应 0.3.2 的箭头图标；当前设计文件随后在 0.3.3 改为 OHO+ 构图，旧设计仍可在 0.3.2 源码归档中查看。

## 名称与升级

显示名称统一为 **Color Hand Operation**，包含启动器、首页、关于、菜单、无障碍服务、前台通知及浮层窗口标题。导出配置默认文件名为 `color-hand-operation-settings.json`。

安装包版本 `0.3.2-color-hand-operation` / versionCode 5。技术包名仍为 `dev.edgeflow`，保留签名、SharedPreferences、Provider authority、配置 schema、Root 常驻脚本路径和小组件宿主身份，因此可以直接覆盖旧版升级。

首页长标题保持原来的连续折叠轨迹和字重变化；标题按实际字体与可用宽度自适应，收起位置预留更多按钮空间。

## 图标

原创图形为半透明侧边触控条、圆润的白色内滑箭头和指尖圆点，底色使用蓝青渐变。系统图标资源没有被当作新品牌标识使用。

- 原生 Android VectorDrawable，自适应图标采用独立前景、背景和 monochrome 层。
- 108dp 画布，识别图形位于中央安全范围；圆形及圆角由系统图标遮罩处理，前景没有固化方形底板。
- `roundIcon` 同步提供自适应层；通知使用透明背景的独立单色小图标，替换旧系统快退图标。
- [可编辑 SVG](../design/color-hand-operation.svg)、[遮罩与缩小预览](../design/icon-mask-preview.png)、[资源生成脚本](../tools/build_brand_icon.py)。脚本中的预览只需 Pillow；构建 APK 不需要执行脚本或安装 Pillow。
- 预览中的主题配色用于展示 monochrome 的形态；未更改用户的系统图标配色设置，也没有将预览配色冒充当前系统效果。

## 验证结果

- 离线 Gradle debug 构建成功；APK label 与 launcher/roundIcon 资源指向正确，已编译的 adaptive XML 包含三层。
- 一加原地覆盖安装成功。桌面已有的“搞机”文件夹中图标与名称更新，点击仍打开同一个应用；桌面长名称由启动器按格宽省略。
- 实机查看首页展开/收起及关于页面：名称完整，无更多按钮遮挡。
- 无障碍 Bound services 中显示 `Color Hand Operation`；GKD 保持连接。通知标题已更新。
- 升级前后逐项比较 `edgeflow`、`edgeflow_launcher`、`edgeflow_shortcuts`、`replica_widgets` 四份偏好，完全一致。
- 手机上安装的 APK SHA-256 与构建产物一致。结构化结果保存在本地 `verification/branding-validation.json`，实机截图为 `verification/brand-*.png`。

本次主要验证品牌资源、长标题布局及升级保留数据；没有把此前完整功能回归声称为本轮重新执行。
