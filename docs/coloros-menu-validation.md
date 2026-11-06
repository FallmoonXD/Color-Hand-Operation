# 0.3.12 自有菜单统一与测试

日期：2026-11-06。包名 `dev.edgeflow`，版本 `0.3.12-coloros-menus` / versionCode 15；OnePlus PKR110，ColorOS 17 / Android 17，1264×2780，density 3.5。

## 覆盖范围

[完整入口审计](coloros-menu-audit.md) 枚举全部 MainActivity 路由、Activity、自有浮层及兼容旧配置入口。字体、颜色、行距、点击态、选择控件与系统图标统一；非输入的服务菜单保留不可聚焦策略。用户已接受的快速工具、音量与小组件主体材质参数未重新调整。

主要新增或迁移：快速工具添加应用二级页、启动器列数页、小组件迁移结果页、Widget provider 搜索与组件/页面菜单、快速启动添加/多选/文件夹菜单、键盘快捷方式管理与表单，以及共享选择/确认容器。系统授权、文件选择、输入法、第三方小组件配置与 RemoteViews 由原所属组件绘制。

## 实测发现并修正

- 浮动 Activity 下 IME 不能只看 root 局部 inset：改以任务 WindowMetrics 与可见 display frame 限制高度，系统 ADJUST_RESIZE 负责位置，避免重复偏移。
- 活 ListView 在 globalLayout 后被 forceLayout/measure，会阻断下一次 adapter 或错误标签的布局请求。测量改为计算列表期望高度，由框架完成最终布局。
- Widget provider 加载/无结果切换原来改变整个面板高度：固定列表区域，空态为不可点击占位；数据更新时取消旧按压并核对点击条目身份。
- Popup 不再与小浮窗 root 边界相交；依据任务安全区域排布。服务 Popup 的 View 重载使用了 IWindow binder，真机抛出 BadToken；2032 窗口改用原无障碍服务注册 token，应用窗口保持原 API。
- 补齐屏幕超时服务子窗口类型、子窗口资源清理、父层点击保护及先退出再执行回调；统一 footer 居中、错误文字色、首帧角度按钮背景与系统加减图标。

## 离线检查

- Gradle `assembleDebug --offline` 通过。
- 共享 Dialog：61 项生产方法断言（回调15、footer14、body12、任务/IME13、Popup token7）。
- Popup 几何125项、浮层编辑器22项、页面运动29项、配置兼容217项通过，合计454项。
- 新增系统向量的路径、尺寸、变换、alpha 与来源逐项比对，AAPT2 编译通过；来源见 [图标记录](overlay-submenu-icon-sources.md)。
- 配置 JVM 验证使用本机 mockable android.jar；普通 SDK stub jar 只能用于编译，直接运行会在 AppWidgetProviderInfo 构造抛 Stub。未把此测试环境错误当作生产故障。

这些检查不模拟实际 WindowManager、IME、图形合成或第三方媒体播放。

## 一加实机路径

- 快速工具编辑 → 移除草稿中的一个按钮 → 添加应用二级页/搜索/IME → 返回保留编辑草稿与滚动 → 放弃修改；已保存布局 SHA-256 保持一致。
- Widget 页面菜单、弹窗高度、组件底色、移除组件/删除页确认取消；组件菜单无需被原小浮窗高度裁切。
- 添加 Widget → 搜索无结果 → 支付宝 → 清空：列表即时刷新，搜索框位置不变；IME 展开时取消按钮完整可见，位置为 `[84,1354][1180,1522]`，键盘顶部约1606px。
- 快速启动 → 添加菜单、文件夹命名/IME取消、真实应用多选后取消、系统动作多选、应用快捷方式取消；选项 → 列数取消。
- 任务切换器数量取消、快速工具屏幕超时外点取消：父面板保持可用。屏幕超时菜单显示期间 WindowManager 的当前焦点仍为 MainActivity，不能据此推导所有第三方视频都不会暂停；本轮未把焦点检查写成 B站播放测试。
- 启动器列数二级页、小组件迁移结果页、恢复默认确认取消。

关键截图与结构化证据位于本地 `verification/ui-unification-*`。验证前四个配置命名空间和快速工具文件摘要保存在 baseline；测试中因旧版加载位置变化产生的临时百度地图组件 ID23 已明确删除，原支付宝 ID21 保留。最终偏好与绑定核对见本轮 final-verification.json。

- 键盘快捷方式：锚定编辑菜单、修改取消、添加表单、重复组合键行内错误、IME 下的固定操作区均已实测；取消后父列表保持同一 node-hash。
- ColorOS 深色模式与 font_scale=1.15：键盘表单、输入法及更多菜单完成显示检查，操作按钮可见。夜间模式与字体比例已恢复为原 `no` / `1.0`；本轮未验证极端字号、横屏及其他固件。
- 导航键编辑 → 更换动作二级页 → 返回，以及快速工具添加应用搜索 → 放弃草稿 → 返回，保留原选择操作页同一 node-hash。
- 实机视觉复核发现 footer 自然测量污染按钮文字宽度，造成取消文字偏左。已改为用文本 Paint 计算期望宽度，不再测量活动 Button；最终安装包中已重新打开带行内错误与 IME 的表单，截图确认两个按钮文字均居中。

最终 APK、安装版本/摘要、偏好、绑定与息屏状态保存在本地 `verification/ui-unification-final-verification.json`。

最终安装 APK 与本地构建 SHA-256 相同：`b5528af246bb45fa72b1c217cd79b4063db7010c80f8d23149dc5880d96aa806`。四个偏好命名空间及工具布局与测试前一致，AppWidget host 仅保留原支付宝 ID21。已回到桌面、关闭 USB 保持唤醒并息屏（Dozing/AOD），桌面保持唤醒辅助进程已结束。
