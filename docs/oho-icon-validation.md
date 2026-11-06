# OHO+ 构图的 ColorOS 风格图标 · 0.3.3

应用名称仍为 Color Hand Operation，包名 `dev.edgeflow`。此为 0.3.3 历史记录，仅替换品牌图标与版本信息；当前设计文件在 0.3.4 又调整为重绘轮廓、单条轨迹和蓝紫配色，0.3.3 原设计保留在该版本源码归档中。

## 设计与来源

几何基础取自用户提供的 Samsung One Hand Operation+ 9.3.13.0 APK 中 `drawable/oho__fg.xml` 和 `drawable/oho__mono.xml`：保留手握手机、拇指和三颗滑动圆点。视口由 432 单位归一化到 108 单位；不使用旧版 PNG 放大。

颜色与光影调整参考本机 ColorOS 17 的系统应用资源：Settings 的分层自适应图标、薄边缘高光及轻接触阴影，安全中心白盾的柔和明暗层级。采用青色的小幅斜向渐变底、浅色机身、蓝色屏幕和柔和肤色。袖口延伸到系统遮罩外，避免自适应缩放后出现人为横向切断。

这是对 OHO+ 图标的改编，并非三星或 OPPO 官方发布的图标；来源没有被标注为原创。本地参考在 `analysis/oho-9.3.13` 与 `analysis/coloros17-icon-audit`，构建/运行不依赖这些分析目录。

[图标规范 JSON](../design/color-hand-operation-icon.json) 包含全部路径、颜色、渐变和出处；[生成器](../tools/build_brand_icon.py) 输出 Android VectorDrawable、SVG 和 PNG 预览。只有重新生成预览需要 Pillow，APK 构建直接使用已生成资源。

普通图标、自适应 roundIcon、monochrome 及前台通知单色图标同时更新。单色预览的配色仅用于展示层次，实际颜色交给系统主题决定。

## 验证

- 检查圆角、圆形和单色预览，以及 72px / 44px 缩小效果。
- 离线构建、覆盖安装，核对现有桌面文件夹中的新图标和启动行为。
- 比较升级前后四份 SharedPreferences，检查配置、小组件绑定不变。
- 核对已安装 APK 哈希、无障碍连接及通知资源；结构化结果记录于本地 `verification/oho-icon-validation.json`。

安装包版本 `0.3.3-oho-icon` / versionCode 6。本次不改手势识别、UI 材质或页面动画。
