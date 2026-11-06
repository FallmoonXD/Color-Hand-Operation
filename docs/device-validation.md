# 一加实机验证记录

2026-11-05；设备 PKR110，Android 17 / API 37，ColorOS 17，USB ADB + Root。

最终安装的是 `dev.edgeflow` / `0.1.0-independent`。APK 大小 **3,564,646 字节**，v2 签名验证通过。测试 APK 与手机实际安装的 base.apk SHA-256 一致：

```
6f7ac51b91ee08aa8fef70564415faf1ec35b563a9c3a65af7704f430801bb54
```

工程通过 Gradle 独立构建。生产源代码没有旧包名、分析目录或 `/data/local/tmp` 桥接包依赖；APK DEX 含新引擎类，未发现 gulugulu 原包和 Samsung OHO 原包的类描述符。Root 材质/任务桥接从自身安装 APK 加载。设备验证探针另由本工程源码生成，仅用于测试。

| 范围 | 已取得的证据 |
| --- | --- |
| 默认 12 个动作位置 | 左右返回/最近任务、四条音量手势、双侧小组件/工具、ColorOS 单手模式和原生侧边栏实际触发。单手模式有截图核对；其余动作有状态或节点检查。 |
| 跟手与面板位置 | 左右固定窄绘制窗口，黑色实体曲线；左右不同触发点生成不同工具位置。取消后没有残留曲线。 |
| 原生材质 | 最终 APK 展开工具时 provider 返回 `connected=true, active=1, frames=576, starts=28, stops=27`；关闭后 `active=0, starts=28, stops=28`。证明连接、取帧和当前捕获释放，长期泄漏情况未测。 |
| 亮度/音量 | 自动亮度往返切换；手动滑条实际写入 raw 2728；工具内长按音量增加 5 级，独立音量按钮长按增加 6 级；外部点击关闭。测试结束恢复媒体音量 0 和自动亮度。 |
| B 站 | 实际视频播放中展开工具、音量按钮时，媒体会话保持 PLAYING(3)，B 站 Activity 保持 resumed；横屏全屏下再次通过。没有点击关注、点赞、发送弹幕等账户动作。 |
| 横屏 | 通过 B 站全屏进入真实 2780×1264 横屏；手柄重建为对应边缘坐标，工具三排共 12 个按钮完整位于屏幕内，播放继续。退出后回到竖屏。 |
| 小组件 | 支付宝提供方重新绑定给新 UID，宿主 ID 21，旧版 ID 17 保留；完整显示组件。添加第二页、删除该空页后回到原页面。 |
| 快速启动 | 创建文件夹，加入“返回”动作并移入、打开文件夹；最终删除测试文件夹，恢复空的初始启动器。 |
| 任务切换器 | 查询真实 taskId，点击设置条目恢复对应设置任务。 |
| 触控板/导航 | 触控板单击实际触发指针下的小组件入口；修正后初始光标窗口为 `(579,920),105×105`，与预期中心相符。外部点击后光标窗口消失。导航 Home 和横竖排列持久化通过。 |
| 组合键 | 设置搜索框里执行 CTRL+A 后，后续输入 READY 替换了整段旧文本。 |
| 系统放大 | 系统 dump 显示 `activated=true, scale=2.0`；再次触发后为 `activated=false, scale=1.0`。测试动作恢复为右短滑返回。 |
| 场景隐藏 | 主屏幕隐藏/离开恢复、通知面板隐藏/收起恢复通过；测试开关最终恢复为关闭。 |
| 配置文件 | 通过 ColorOS 系统文件选择器导出含 preferences/tools/launcher/shortcuts/widgets 的 v2 JSON，再通过系统选择器成功导入；测试文件已移除，导出证据留在本机 verification。 |
| 后台划卡 | 初次删除真实 EdgeFlow 任务卡后 PID 31788 保持；最终 APK 再测 PID 32315 保持，手势和工具仍可用。测试曾误选最近任务后方桌面的同名图标，收紧节点定位后已排除该测试问题。 |
| 进程恢复 | 先发现 ColorOS 自启动未允许。使用核实的系统 provider 只将 `dev.edgeflow` 自启动 false→true，并读回；配合显式 started lifecycle + START_STICKY，kill -9 后 PID 21973→24369 自动恢复，手柄和工具重新可用。 |
| 自启动入口 | 最终设置页入口实测打开 `com.oplus.startupapp.view.StartupAppListActivity`，只打开界面。应用不会自动重新打开用户关闭的无障碍权限。 |

本机原始证据在 `verification/`：`core-validation.json`、`controls-validation.json`、`media-validation.json`、`context-validation.json`、`widget-page-validation.json`、`artifact-validation.json` 和对应截图/系统 dump。目录含个人设备信息，不作为源码分发内容。

后续扩展实测记录：

| 扩展 | 实际结果 |
| --- | --- |
| 六段手柄 | 额外右侧第二段绑定 Home，在该段触发成功；原左右12个映射逐键比对未变。测试段已恢复关闭和无动作。全部六段的几何/键隔离另有51项主机断言。 |
| 自定义导航 | 增加第四个“通知栏”按钮，实际展开系统 notification_panel；删除测试键后恢复返回/主页/最近任务三键。 |
| 任务预览 | 接口返回5个真实任务145×320预览，面板实际显示“系统任务预览”节点，外部点击后窗口消失。预览仅存内存，关闭清理；没有做长期内存压力测试。 |
| 真实应用快捷方式 | 选择器读到支付宝4个公开动态入口和真实图标。随后在设置页选择系统设置发布的 WLAN 入口，手势直接启动 `com.oplus.wirelesssettings/com.android.settings.SettingsActivity`；WLAN 状态未修改，测试手势恢复“返回”。支付宝“更多设置”虽被系统接受启动，却没有持久前台页面，因此不计作该入口成功测试。 |
| 放大控制区 | 真实系统2倍放大，滑动改变视野中心，调到2.5倍，再关闭回到1倍。控制区用 NOT_MAGNIFIABLE 和 EXCLUDE_FROM_SCREEN_MAGNIFICATION 两个窗口标志保持原尺寸。UI 自动化探针连接/断开会重置系统放大，已通过前后状态对照确认，因此缩放验证期间使用系统 dump 和坐标操作。 |
| 屏幕自由移动 | 平移截图后抬手，映射点击打开原坐标上的左侧手势设置；前台窗口ID和包名均校验，退出释放截图。窗口透明度0.94适配当前ColorOS整屏无障碍窗口检测，没有修改系统检测策略。 |
| 小组件四边编辑 | 支付宝组件左边缩进一格、恢复，再从底边缩小一格、恢复；取消一次缩放后持久布局不变。最终仍为原ID21、x0/y0/w4/h2，单页。 |

对应结果文件：`extension-validation.json`、`configuration-ui-validation.json`、`shortcut-start-validation.json`、`task-preview-validation.json`、`widget-edit-validation.json`。放大窗口标志语义另核对了 [AOSP WindowState](https://android.googlesource.com/platform/frameworks/base/+/refs/heads/main/services/core/java/com/android/server/wm/WindowState.java) 与 [WindowManager](https://android.googlesource.com/platform/frameworks/base/+/refs/heads/main/core/java/android/view/WindowManager.java)，实际常量取自本机固件。

主机验证为 **52 项手势状态断言、51 项手柄配置断言、78 项配置/迁移/几何断言、14 项快捷方式身份断言、9 项命令结果断言**。这些是实际运行的 JVM 检查，不能替代上表的 Android 验证。

未做实际设备重启、锁屏后解锁、长期耗电/压力运行和所有 60 个选项的逐项测试。强行停止应用不属于自动恢复保证。Samsung 专属接口和部分高级交互仍有差异，详见 [功能状态](feature-status.md)。

测试结束时旧视觉测试版及其数据保留，旧无障碍服务停用，GKD 保持原状态；EdgeFlow 服务已连接。手机保持充电常亮，未进行锁屏或重启。
