# 更新记录

## 0.3.16 · 2026-11-07

- 换上新的橙紫配色图标，保留原来的 MD3 主题图标和通知小图标。
- 整理 README、功能说明、兼容说明和开发笔记。

这版没有改手势、面板材质或配置格式。图标的分层文件和导出方式见[图标记录](docs/user-layered-icon-validation.md)。

## 0.3.15 · 2026-11-06

第一个公开预览版。

- 图标改为白灰前景和柔和亮边，MD3 的手与手臂合成连续色块。
- 部署脚本自动选择唯一已授权的 ADB 设备，保留其他无障碍服务。
- 读不到亮度范围时停用调整，避免写入不适合当前手机的数值。
- 原生材质缺失、Root 被拒绝或绑定失败时使用普通材质。
- Athena 后台脚本只在匹配的机型和固件上运行。

## 早期开发记录

这些版本记录了公开预览版之前的修改，文档中可能保留当时的内部版本名和旧图标。

| 版本 | 改动 | 记录 |
| --- | --- | --- |
| 0.3.14 | 简化图标光影，调整主题图标的填充和遮挡 | [图标记录](docs/frosted-icon-validation.md) |
| 0.3.13 | 恢复原手机/手部轮廓，把三个圆点换成渐隐箭头 | [图标记录](docs/oho-glass-icon-validation.md) |
| 0.3.12 | 统一设置菜单、选择面板和表单；修复键盘遮挡、刷新及窗口边界问题 | [菜单测试](docs/coloros-menu-validation.md) |
| 0.3.11 | 操作列表嵌入所属页面，返回配置页时保留滚动和输入状态 | [页面连续性](docs/continuous-pages-validation.md) |
| 0.3.10 | 配置入口改为齿轮，返回时恢复原操作列表 | [列表状态](docs/retained-menu-validation.md) |
| 0.3.9 | 动作按功能分组，应用和快捷方式使用二级页面 | [操作菜单](docs/action-menu-validation.md) |
| 0.3.8 | 增加可拖动的角度编辑器，触控区域改按系统栏之间的空间计算 | [角度与触控区域](docs/safe-angle-validation.md) |
| 0.3.7 | 调整振动、长短滑角度和应用动作设置，暂时恢复 OHO+ 原图标 | [设置记录](docs/oho-controls-validation.md) |
| 0.3.6 | 加入触控区域实景预览和调色板，试验无手部图标 | [预览与调色板](docs/palette-preview-validation.md) |
| 0.3.5 | 调整操作选择面板、说明文字和图标 | [界面记录](docs/design-refinement-validation.md) |
| 0.3.4 | 首页开关和触钮改为局部刷新，改进菜单定位、大字号和主题恢复 | [界面修正](docs/ui-polish-validation.md) |
| 0.3.3 | 图标改为手握手机的构图 | [图标记录](docs/oho-icon-validation.md) |
| 0.3.2 | 应用更名为 Color Hand Operation，加入自适应图标和独立通知图标 | [名称与图标](docs/branding-validation.md) |
| 0.3.1 | 修复触控区域提交闪页、滑条与滚动冲突、长滑时间判断 | [交互修正](docs/interaction-fix-validation.md) |
| 0.3.0 | 设置页改用 ColorOS 风格，加入主题、控件和页面过渡 | [设置页记录](docs/coloros-ui-validation.md) |
