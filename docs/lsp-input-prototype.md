# LSPosed 输入后端原型

本文记录独立原型和双后端试用阶段。0.5.0 使用 Root + LSPosed，首页通过「权限与运行」查看状态。安装与升级步骤见[仓库首页](../README.md)，最近的整合验收见[项目状态](PROJECT_STATE.md)。

2026-11-17 已整合到 CHO 本体，在首页新增“运行方式”，本机试用包已安装并选择 LSP。主应用直接编译 `lsp/src/main/java` 和模块元数据，调试页与 RootProbe 仅存在于独立 debug 应用。共享识别器仍以主应用源文件为准。主应用 compileSdk 升到 37，以满足 libxposed Service 的依赖要求；minSdk、targetSdk、公开版本和签名不变。最新验收见 [项目状态](PROJECT_STATE.md)，以下早期记录描述独立原型阶段。

整合后，`BackendChoice` 保存本机运行方式；`RuntimeBackend` 检查当前安装路径、框架服务及系统侧接管结果，确认成功后才让自己的无障碍服务停用。选择失败保留原运行方式。`TrialChannel` 通过 Activity 持有的 Binder 回传识别数据，失去焦点或进程退出时结束，试划不执行动作。`NativeVolumeState` 观察本机音量视图的 `setSliderState` 与 `expandPanel`，经工具授权对应的 Binder 回传给原音量按钮；没有改写系统音量状态或动画参数。七个尚未接入的动作在选择页标注不支持，实际手势不接管其短滑。

独立原型位于 `lsp/`，包名 `dev.edgeflow.lsp.pilot`，版本 0.1.0。固定返回测试使用两侧各 40dp、中间 70% 屏幕高度、24dp 确认距离；配置测试读取 CHO 的真实触钮和动作。限时测试均为 30 秒，另有独立的常驻开关，不改变 CHO 默认设置。

## 已经得到的结论

这台一加的 SystemUI 拥有 `MONITOR_INPUT` 和 `INJECT_EVENTS` 权限，系统提供 `InputMonitor`。原型通过 `Context.getSystemService(InputManager.class)` 取得真实实例，再探测监听、注入和回收方法；没有依赖已失效的 `InputManager.getInstance()`，也没有修改厂商侧滑检测类。

实机证明了下面这条输入链路可以工作：

1. 按下时仅观察。点击抬起直接留给原应用。
2. 达到横向确认条件后，请求 `pilferPointers()`。只有该请求成功，状态机才记录自己拥有这条手势。
3. 底层应用收到取消事件，后续抬起才提交一次返回。
4. 多指、系统取消、回划和显示几何变化取消动作。超时或息屏释放监听器。

原型沿用 CHO 的 `GestureRecognizer` / `GestureAngles`，额外增加输入归属状态，而不是再维护一套手势算法。已明确开始的竖滑不会在后续转向时突然被接管；鼠标事件不进入触屏手势识别。

2026-11-17 的桌面复测补充：配置后端现在按系统 touch slop 接管触摸，按用户配置的完整距离／时长执行动作。这两个阈值不能合并；此前等待完整触发距离会让桌面先打开全局搜索，随后前台变化又取消 CHO。解锁恢复另用 `LifecycleDispatch` 合并事件，不用可被连续通知推迟的尾端延迟；Keyguard 交接中的暂不可用不会锁死为故障。实时状态可通过 `dumpsys activity service com.android.systemui/.SystemUIService --cho-status` 读取，采样入口为 `tools/verify_lsp_unlock.py`。

## 代码分工

| 入口 | 职责 |
| --- | --- |
| `ModuleEntry` | SystemUI 在 Application 创建后注册输入控制；系统框架通过 `onSystemServerStarting` 安装窗口接口。现代 API 作用域为 `com.android.systemui` 和 `system`，加载不自动启动输入 |
| `PilotServer` | 接受同签名实验应用的检查、旁听、返回测试和停止请求。一次最多 30 秒，不提供任意命令或任意按键接口 |
| `NativeInputApi` | 检查权限与方法签名，取得输入监听器、接管触摸及注入配对返回按键 |
| `NativeInputSession` | 管理接收器、显示变化、锁屏/交互状态、结束回调及清理。每个输入事件都在 `finally` 中完成回执 |
| `EdgeInputSession` | 平台无关的观察、申请接管、确认归属和取消状态，调用原有识别器 |
| `GestureConfigProvider`（主应用） | 导出受签名权限保护的只读配置；偏好变化后通知已启动的实验会话 |
| `GestureProfile` / `MappedGestureSession` | 保存不可变配置，使用共享的触钮几何、动作解析与识别器，处理六个触钮、长短滑和按应用覆盖 |
| `SystemScene` / `MappedActions` | 核对前台任务、场景排除和无障碍冲突，分发已接入的基础动作 |
| `PersistentBridge` / `PersistentState` | 模块应用保存本地开关、确认配置和框架远程状态；保护开启、关闭、同步及清理入口 |
| `PersistentRuntime` / `PersistentPolicy` | SystemUI 内的常驻生命周期，处理暂停、恢复、安装身份核验、输入错误与监听互斥 |
| `ControllerService` / `ControllerConnection` | 受平台签名权限保护的绑定通道，控制进程结束后恢复连接，保存系统侧收到的源配置 |
| `GestureFeedbackWindows` | 两侧不接收触摸的反馈窗口，复用原绘制、颜色与触觉实现，监听前台任务和场景变化 |
| `FrameworkWindowBroker` / `WindowLeaseStore` | 系统框架仅授予 CHO 工具窗口令牌，撤销时同时移除所属窗口；监听 SystemUI 与宿主两端 Binder 的死亡 |
| `ToolHostConnection` / `LspToolHostService` | SystemUI 按手势位置请求显示，CHO 自己的 UID 托管真实工具和原 AppWidget；显示结果异步确认 |
| `RootProbe` | 通过 Root `app_process` 运行同一份输入后端，便于不重载 SystemUI 的实机验证。标准输入关闭、明确停止或超时都会结束 |
| `PilotActivity` | 独立开发控制页，明确显示连接状态。不是正式版 UI |

应用端控制广播要求 `dev.edgeflow.lsp.pilot.CONTROL` 签名权限。模块没有修改系统权限判定、手机管家、启动管理或其他应用的授权。配置不写入 `Settings.Global`；常驻状态由模块自己的设备加密存储和框架远程偏好保存。

输入接收器需要两个隐藏类型的编译声明。`src/stubs/` 通过 `compileOnly` 使用，运行时由 Android 提供。打包检查确认占位类和 libxposed API 定义没有进入 APK。识别器源码由 Gradle Variant API 在构建时选取原文件，检查其内容一致，避免复制后分叉。

## 验证范围

设备仍为 PKR110 / `PKR110_17.0.0.101(CN01)`。输入测试时 CHO 无障碍关闭，其他服务保留。以下是实际设备上的 Root 通道验证，不等于已经完成 LSPosed 在 SystemUI 内的注入验证。

| 检查 | 结果 |
| --- | --- |
| 输入归属状态机 | 172 项断言通过，覆盖未确认归属不执行、一次手势一次动作、回划、多指、错误指针、乱序时间及失效坐标 |
| 左右两侧基础输入 | 12 组通过：旁听/接管两种模式下的点击、竖滑和内滑；70px 起手点位于原型的 40dp 宽区域内 |
| 取消与多指 | 8 组通过：左右侧的取消、回划、接管前多指和接管后多指，均不误返回 |
| 鼠标输入 | 2 组通过，不触发触屏手势接管 |
| 到期与旋转 | 2 组通过，自动结束后及旋转后均没有残留监听器 |
| 全面屏手势仲裁 | 左右侧各一组通过，原型各提交一次返回，前台由第二页退回第一页，没有再退一次 |
| 息屏 | 监听器以 `display-off` 结束，系统输入服务中没有残留，电源服务读回休眠 |
| 实验控制页 | 未激活模块时正确显示未连接；点击测试按钮不会凭空创建监听器；横屏可滚动到停止按钮 |
| 打包与构建 | 原型及主应用的 debug 构建、lint 通过；原型 0 错误、7 警告，主应用 0 错误、248 条既有警告 |

27 组实机输入/生命周期用例的判断同时包含接收器统计、测试应用触摸日志和前台 Activity。返回测试验证的是第二页只退回第一页，不仅检查注入 API 是否返回成功。每组停止后检查系统中不再存在原型监听器。

Root 测试入口最初在读取锁屏状态时遇到 Android 17 的 `ApplicationSharedMemory not initialized`。现已在这个短生命周期测试进程中初始化私有共享内存；没有修改系统共享内存。SystemUI 正常启动时由框架完成对应初始化，不执行该测试引导代码。失败日志也保留，不能把早期启动失败算成输入测试通过。

第一阶段只做了实验页未连接验证，当时没有在 LSPosed 中启用模块；实际类加载、签名控制入口往返和 SystemUI 生命周期留到了第二阶段。

上述为第一阶段 Root 通道的验证边界。第二阶段的实际模块验证如下。

## SystemUI 模块验证（2026-11-16）

已实际安装并启用实验模块，作用域仅为 `com.android.systemui`。重载后确认初始化 Hook 和受签名权限保护的控制入口均已注册。测试请求由同签名的临时应用 UID 发出，返回的执行 UID、PID 与当前 SystemUI 一致；输入后端没有通过 Root `app_process` 代替模块运行。

29 组输入和生命周期用例通过：12 组左右侧基础输入、10 组取消/回划/多指/鼠标边界、旋转、左右侧全面屏返回、原型应用进程结束、CHO 进程结束、最近任务划掉 CHO，以及 30 秒自动到期。结束应用后的测试仍能取消底层触摸并只退回测试应用的上一页。这里验证的是独立原型的固定区域返回，手机上的正式 CHO 尚未切换后端。

另外验证了控制权限：没有声明 `CONTROL` 权限的测试应用无法启动接管；无效操作返回错误且不会创建监听器，后续正常请求仍能执行。重载 SystemUI 后能重新查询状态，PID 已变化，旧监听器消失，新进程默认不启动输入。锁屏时启动请求被拒绝，没有创建触区。

首次联调发现回调对象携带了发送端匿名类名，导致 SystemUI 反序列化失败。发送端现通过 `ResultReceiver.CREATOR` 转成系统基础类型，保留原 Binder 回调，不要求接收进程加载发送端类。原型控制页和测试发送端均已修复，实际跨进程往返通过。

两处测试工具问题也单独记录：包管理器的模糊搜索包含 SystemUI 资源覆盖包，执行 UID 校验改为精确匹配包名；最近任务等待不足和测试应用退到后台后的调度延迟，不再计作模块输入失败。已通过的核心用例保留，补测剩余生命周期部分，没有将查询超时当作 SystemUI 已退出。

复测入口为 `tools/verify_lsp_systemui.py`，中断后可用 `tools/verify_lsp_lifecycle.py` 继续生命周期部分，权限检查用 `tools/verify_lsp_auth.py`。这些入口要求已安装并激活原型和同签名测试应用，属于需要机主方便解锁时执行的模块阶段。`tools/lsp_control.py` 只通过测试应用发送控制请求；测试组件本身由 `android.permission.DUMP` 限制调用。

证据在本地 `verification/lsp-systemui/`：`results.json`、`control-permission.json`、`reconnect.json` 和 `final-state.json`。实验模块与临时应用已经卸载，并重载 SystemUI 清除驻留代码；新进程没有加载原型，系统输入服务没有遗留监听器。CHO 配置、导航、显示和其他无障碍服务与基线一致，原有模块的开关和作用域保持。模块索引中还清除了上轮未安装原型的旧记录，其他模块记录未变。最后读回 `Dozing`。

## 复测

使用项目现有 JDK 21 和 Gradle wrapper：

主应用仍使用 API 35 编译平台。`lsp/` 的 Service 102.0.0 依赖要求 API 37，因此构建该实验模块还需安装 Android SDK Platform 37.0；两者的 targetSdk 均保持 35。

```powershell
.\gradlew.bat :lsp:assembleDebug :lsp:lintDebug
python validation/lsp/run.py
python validation/lsp/apk_check.py
python tools/verify_lsp_input.py
python tools/verify_lsp_boundaries.py
```

输入测试要求手机已解锁、Root 可用，并关闭 CHO 的实际触钮，避免双重接管。边界测试从三键导航开始，临时测试全面屏手势后恢复。它们只安装临时测试应用，保留 CHO 数据和无障碍列表。

最后再运行 `python tools/verify_lsp_screen_off.py`；这一步会息屏，需要继续交互时可能要机主重新解锁。用户已要求将这类操作集中到最后或待其回来，不能为了循环测试反复重载系统。

APK 在 `lsp/build/outputs/apk/debug/lsp-debug.apk`。本机证据在不提交的 `verification/lsp-input-prototype/`：`results.json`、`boundaries.json`、`screen-off.json`、`pilot-ui.json`、`apk-check.json` 以及各轮前后状态。早期错误日志与最终回归分开保留。

## 真实配置与基础动作（2026-11-16）

主应用增加 `content://dev.edgeflow.gesture_config`，仅接受声明了同签名 `READ_GESTURES` 权限的调用方。接口只读，不执行默认值迁移，不返回小组件布局、笔记或其他工具数据。无读取权限的测试应用被拒绝；没有控制权限的调用方也不能启动系统侧监听。实验控制页读取这个快照，再通过原有签名通道发送给 SystemUI。

六个触钮的宽度、高度、横屏独立宽度、重叠优先级、旧坐标别名、长滑开关、距离、时长、独立角度和即时触发规则均沿用 CHO。动作的默认值、逐应用继承和显式“无”由同一份解析代码处理。配置更新取消正在进行的手势，不延长 30 秒会话；更新消息不能自行开启输入。总开关关闭会停止监听，重新开启仍需手动开始实验。

前台任务在按下、接管、等待长滑和执行前核对。已有无障碍输入时拒绝配置模式，防止两套后端同时触发。桌面、横屏、键盘、通知栏的隐藏设置继续生效；键盘和通知栏状态来自 SystemUI 自己的 `SysUiStateImpl.commitUpdate()` 与 `QuickStepContract.getSystemUiStateString()`。这里只读状态，不改系统导航处理。该接口不可用时，请求了对应隐藏规则的配置不会接管输入。

实机通知栏检查发现本固件使用 `notif_visible`，并非只有 `notif_expanded`。已把它与 `qs_visible` 一并纳入隐藏判定，保留失败记录并单独复测修正。不能只根据 AOSP 标志名称假定厂商行为一致。

基础动作接入返回、主屏幕、最近任务、音量、媒体键、通知栏、控制中心、设置和应用启动。搜索、菜单、浏览器前进保留按键映射，但本轮未逐一验证目标应用响应。音量目前只调用系统音量条，尚未接入 CHO 音量按钮。快速工具、小组件、导航键等工具窗口及其他未接入动作列在 `unsupported_actions`，不会替换成返回或短滑；已经接管后改选到未接入长滑，也不会误执行短滑。

51 组实机用例通过：六个触钮、长短滑、应用覆盖与排除、配置热更新、前台变化取消、结束 CHO 进程、首页总开关、实际保存的配置、点击和竖滑穿透、桌面与键盘排除，以及上述基础动作。媒体键使用循环静音音轨的独立播放器，逐个检查按下与抬起事件；音量、静音和播放器均已恢复或清理。最终构建又验证了通知栏隐藏、原配置、固定返回、输入法、启用无障碍后停止监听并拒绝重复启动、横屏物理边缘与隐藏、旋转清理。配置模式的锁屏启动拒绝另外通过。

离线新增 186 项配置模式断言，原输入归属 172 项通过。原手势回归 343 项、配置与备份 364 项通过。主应用和实验模块构建、lint、打包检查通过。一次配置回归误用了普通 SDK stub jar，换回项目规定的 mockable jar 后通过；实机脚本一次忘记开启正式包的 Root 只读配置入口，已修正，未将这两项工具问题记作应用故障。

复测入口 `tools/verify_lsp_configured.py`：`prepare` 保存原安装包和设置后安装测试包，`run` 测基本映射，`remaining` 接续场景和动作，`actions` 可单独接续动作，`final` 测修正项与剩余边界，`boundaries` 可从键盘与无障碍冲突继续。需要机主解锁时才重载 SystemUI。每次实验结束需停止监听、恢复原安装包、卸载临时模块与测试应用、核对原设置和模块作用域，并在最后息屏。本地证据为 `verification/lsp-configured/`。

本轮已完成收尾：原安装包的设备读回哈希一致，偏好、无障碍、显示与其他模块设置均保留，实验应用已卸载，新 SystemUI 没有原型加载记录或监听器。LSPosed 仍保留原型的停用索引，作用域为空；没有直接改写运行中的配置数据库。最终电源状态为 `Dozing`。键盘补测的一次误报来自测试脚本在重开 Activity 后才读 IME 状态，修正采样顺序后通过，没有因此修改应用行为。

## 常驻开关与恢复（2026-11-16）

常驻模式和限时测试分开。首次开启在原型控制页前台读取 CHO 配置，保存整份版本化状态；关闭先保存本地停用意图，再同步框架，避免只收掉当前监听而在进程重载后重新开启。配置变化由 CHO 主动推送，后台恢复读取最后确认的副本，并检查源应用的 UID、首次安装时间和配置接口。框架侧是一条完整 JSON 状态，避免分别更新开关和动作时出现半份配置。

这条通道使用 [libxposed 的远程偏好](https://libxposed.github.io/api/io/github/libxposed/api/XposedInterface.html#getRemotePreferences(java.lang.String))；模块应用通过 [Service 102.0.0](https://github.com/libxposed/service/tree/102.0.0) 写入，SystemUI 只读。Provider 先检查调用权限，再使用 ContentProvider 的身份切换接口处理嵌套调用。SystemUI 只能核对状态或停用，普通调用方必须持有控制签名权限。没有通用命令执行或权限绕过入口。

输入只在机主用户、设备已解锁、源应用有效、CHO 总开关打开且没有 CHO 无障碍输入时启动。息屏释放监听，解锁后重新创建；旋转先结束旧会话，再按新几何创建。失败会停住，不在后台无限重试。常驻启动先收掉限时监听，两个模式不会同时接管。数据清除和卸载事件会撤销保存的请求；这条事件分支经过离线检查，未通过清除机主数据实测。

本机实测修正了三处问题：

- 原型在前台能读取 CHO Provider，退到后台后同一调用返回 Unknown authority。恢复改用确认过的缓存，源应用修改设置时主动同步；没有关闭 ColorOS 的后台限制。
- 覆盖安装后，旧进程没有及时收到远程偏好变更。开关写入后补发签名通知，让接收端重新核对持久化状态，不能把写入成功当成监听已经切换。
- 新进程恢复曾卡在 CHECKING。解锁信号到达时锁屏状态可能还没更新，原逻辑只检查一次；现在后续检查满足条件时会再次发起核验。修正后用新 PID 实际执行返回通过。

17 组实机用例通过：默认关闭、真实配置开启、超过 30 秒持续运行、结束 CHO 和原型应用进程、总开关暂停/恢复、无障碍接管/退出、旋转重建与回到竖屏、主动关闭、设置更新不推翻停用、限时转常驻、息屏解锁、新 SystemUI 恢复，以及停用状态跨重载保持。另测五个未授权 Provider 操作，均被拒绝。新增 35 项生命周期和序列化断言通过，原 172 项输入归属、186 项映射与 364 项配置/备份回归通过。源码构建、lint 和打包检查通过。

复测工具是 `tools/verify_lsp_persistent.py`：`prepare` 保存基线并安装测试应用，`first` 运行启停和进程用例，`restored` 在实际重载并解锁后验证恢复和关闭。息屏检查需机主能解锁。测试控制请求始终由签名测试应用发送；其后台广播曾延迟，因此首次开启改为操作原型前台按钮并等页面报告运行，再切回测试应用，未将请求超时算作模块故障。

本轮收尾已先关闭常驻，再以 `persistent_reset` 清除框架实验配置。原 CHO APK 的设备读回哈希与基线一致；临时应用、原型索引/作用域/远程配置已清理，新 SystemUI 没有原型代码或监听器。其他模块的配置表、用户偏好、无障碍、导航和显示设置保持，手机最后为 `Dozing`。本地证据在 `verification/lsp-persistent/`，失败日志与修复后结果均保留。

## 手势反馈与控制通道补测（2026-11-16）

触钮提示、箭头和振动现由两条后端共用代码。`GestureWaveView` 是应用资源适配层，实际绘制在 `GestureWaveRenderer`；LSP 构建从原文件生成源码与资源输入，未维护另一份曲线或弹簧。透明度、六个触钮的独立颜色、系统反馈、自定义时长和旧振幅设置也使用同一套解释。旧浮点透明度在配置导出时规范为百分比，防止序列化丢失小数类型。

本机 SystemUI 的 `DisplayBackGestureHandlerImpl` 使用导航提示窗口和 trusted overlay。LSP 使用同类窗口，实际 WMS 读回为 `NAVIGATION_BAR_PANEL`、`NOT_FOCUSABLE`、`NOT_TOUCHABLE`、`TRUSTED_OVERLAY`。两侧表面只绘制，输入仍由 InputMonitor 在识别后接管。前台任务、键盘、通知栏和排除应用会同步隐藏反馈；几何变化、停止和息屏清理窗口及任务监听。配置与已经保存的副本一致时就地更新，保留窗口和输入会话。

36 组本机检查通过。原安装包和 LSP 在同一触点的右侧箭头区域共 180000 个像素完全一致；两段录屏均捕获到 9 个退场中间帧、8 个不同深度，从首个变化帧到清空均为 101 ms。实际查看了左右侧及横屏截图。这里记录的是采样结果，弹簧仍为原来的 response 0.1、bounce 0，没有按测量值另设速度。

检查还覆盖点击与竖滑穿透、撤回、多指、取消、场景隐藏、独立颜色/透明度、关闭系统动画、拖动中停止、快捷/抬手/长滑反馈、静音与零强度、常驻更新不重建窗口，以及结束两端进程后的显示。系统反馈关闭时，View 调用仍可能返回已处理，因此同时检查振动服务记录；没有调用无条件 Vibrator 来补偿系统反馈关闭。

冷进程补测暴露了旧控制通道的缺口：原型进程结束后，后台 Provider 有时不可达。当前输入虽能暂停，关闭设置却未必保存。现在 SystemUI 绑定 `ControllerService`，其入口要求平台签名权限 `STATUS_BAR_SERVICE`，Binder 请求再次核对 UID，只允许刷新、同步和停用。断线后恢复绑定，保存签名通道送来的最新配置；队列保留失败更新，并让撤销请求优先。关闭常驻后释放绑定，不添加常驻通知或修改系统后台策略。

实际结束控制进程后，绑定恢复且两侧窗口保持；在进程死亡时立即发送更新也能写入缓存。通过 CHO 总开关关闭的冷进程路径已核对缓存为关闭。Shell 查询能确认该服务存在，但普通 Shell 无法启动它。未测试通过清除机主数据进行撤销，也没有把这轮短时测试当成长时间后台/耗电结论。

本轮新增 26 项反馈断言，常驻和队列检查增至 41 项；原 172 项输入归属、186 项映射、343 项手势、364 项配置/备份及 46 项窗口更新检查通过。窗口更新测试的 SharedPreferences 替身补齐了生产代码已使用的 `getAll()`。构建、lint 和打包检查通过，隐藏接口与物理边缘定位等警告保留。

入口为 `tools/verify_lsp_feedback.py`，资料在 `verification/lsp-feedback/`。预检用的 Root 命令行进程不能访问 Settings Provider，未计作反馈验证，也未据此改应用权限；最终均在真实 SystemUI 验证。收尾已恢复原 APK 并核对哈希、偏好与服务状态，临时应用及模块记录清理，其他模块四张配置表保持一致，手机为 `Dozing`。没有提交、推送或发布。

## 工具窗口接入（2026-11-17）

快速工具和小组件面板接入了实验输入后端。手势动作携带原始按下与结束坐标，绑定宿主前后核对前台任务；未得到实际窗口显示回执不记为动作成功。系统侧先确认框架窗口接口和受保护的宿主 Service 存在，未接入的工具仍不接管手势。此时还不是正式运行方式设置。

界面留在 `dev.edgeflow` 进程，不将工具代码、AppWidgetHost 或机主数据移到 SystemUI。`LspToolHostService` 要求平台签名权限 `STATUS_BAR_SERVICE`，Binder 内再次核对真实 SystemUI UID。主面板、玻璃子弹窗共用可选的窗口令牌适配；原无障碍上下文仍使用系统分配的服务令牌。AppWidget 宿主和原绑定 ID 保留。

第一种尝试使用公开 `IWindowManager.addWindowToken/removeWindowToken`：普通应用能显示 2032 窗口，但撤销登记后窗口仍可见。这与 AOSP 公开撤销路径不要求移除窗口的行为一致，不能据此宣称已经回收。现由系统框架通过 `WindowManagerInternal.removeWindowToken(token,true,false,0)` 同时移除窗口。接口只接受真实 SystemUI UID，固定窗口类型、主屏幕、机主 CHO 包和同签名宿主；不接受任意窗口类型、目标 UID 或系统命令。独立 debug 原型额外允许固定测试包。

授权关联两端 Binder。宿主退出、SystemUI 退出和主动停用均撤销自己发出的令牌；已撤销令牌不能再次添加窗口。登记与死亡同时发生、部分登记失败及清理失败保留重试状态均有离线检查。停止与快速重开以令牌区分旧会话，避免旧关闭请求清掉新会话。息屏和用户切换结束工具连接。

本机窗口基础检查通过普通 UID 显示、强制移除、旧令牌拒绝、重复授权、宿主死亡、冻结宿主回收、Shell 直连拒绝及 SystemUI 死亡清理。真实手势通过两种工具的左右打开、返回和窗外点击不影响底下应用、快速重开、宿主退出后的下次手势冷启动、小组件菜单／选择页／输入法、移除确认取消，以及停用清理父子窗口。前台在冷绑定期间改变时没有留下工具窗口。测试配置通过签名快照传入，不写入机主动作配置。

子弹窗测试发现 `GlassDialogBuilder` 只按 AccessibilityService 判断悬浮宿主，导致新宿主的选择页使用空 Activity token 并崩溃。共用判断已加入令牌宿主，修正后选择页、键盘和逐层返回通过。首次启动框架接口时还误把现代 API 的 `android` 作用域当成系统框架，造成一次无效重启；已按官方说明改为 `system`，并从框架日志确认加载。两项失败记录均保留。

新增 32 项窗口生命周期断言，动作映射检查从 186 增至 222，覆盖识别器结束后保留工具位置。原 172 项输入归属、41 项常驻、26 项反馈和 66 项弹窗检查通过。构建和 lint 通过：主应用 0 错误、249 警告，实验模块 0 错误、20 警告。主要新警告来自隐藏接口及跨进程 Parcel。证据位于本地 `verification/lsp-tools/`，入口 `tools/verify_lsp_windows.py`；`tools` 子命令验证真实手势，`tools remaining` 可接续后半段。

原无障碍入口的快速工具、小组件与选择页另行通过回归。冷绑定过程中换到系统设置时没有留下工具窗口；息屏后输入、箭头、工具和令牌均释放。退场录屏测到快速工具 11 个中间帧、九成到一成 121 ms，小组件 10 个中间帧、105 ms；这些是录屏测量，原动画参数未改。测试脚本一度使用了错误的搜索框和确认框文案，修正匹配后接续剩余用例，没有为这些测试定位错误修改生产代码。

已清除实验控制状态并恢复原安装包，读回哈希与基线一致；原偏好和小组件布局在重启前逐项核对一致。卸载两项临时应用后完整重启，确认新系统框架及 SystemUI 无原型加载，实验窗口与监听器不存在，LSPosed 四张配置表及导航、无障碍、显示设置保持。最终为 `Dozing`，重启后没有再次要求解锁；私有偏好的最后一次核对在重启前。结果见 `final-state.json`。没有提交、推送或发布。

依据：[现代 API 的系统框架作用域](https://libxposed.github.io/api/io/github/libxposed/api/package-summary.html)、[AOSP WindowManagerService](https://github.com/aosp-mirror/platform_frameworks_base/blob/master/services/core/java/com/android/server/wm/WindowManagerService.java)。实际支持范围以本机调用和窗口读回为准。

## 第二批工具与动作（2026-11-17）

导航键、快速启动、任务切换器、键盘快捷方式及其最近任务数据层改用通用 Context。`ToolActionHost` 区分宿主能力，无障碍仍用原服务，LSP 使用 CHO 自己的 Service 和受保护的系统侧回调。屏幕超时及旧应用启动器复用原对话框，未改材质、图标或动画参数。文件夹动作的非空 ID 解释与原布局共用，保留导入数据中的不透明 ID。

窗口连接从“每个动作重新绑定”改为“同一输入会话共用”。临时面板继续按原动作层互斥，导航键保留。请求有递增序号，过期显示结果不覆盖新请求；关闭仍按令牌识别会话。快速启动的后续划动通过框架类型消息回传，应用仍运行原命中逻辑；滑动选择具体入口尚未完成专门的实机验收。

系统侧 `ToolControls` 仅接受本次宿主 UID，提供限定导航动作与当前前台包查询。没有任意按键编号、目标 UID 或 Shell 入口。应用端“关闭当前应用”沿用原 Root 握手和最终任务检查，窗口收起后再次核对目标。快捷键改用同一等待 Root 就绪的握手，在发送前确认前台仍相同；正常 Ctrl+A 已实测，延迟 Root 授权后切换前台的场景尚未单独实测。

八种入口的打开／清理、导航返回／主页／最近任务、导航与快速工具共存、单独收回临时面板、快捷键编辑取消与实际 Ctrl+A、任务列表加载并切换测试任务、启动系统设置、关闭测试应用均通过。常驻模式下导航键保持 41 秒，仍可同时打开快速工具并单独关闭，明确停用后两者清理。旧无障碍的四类入口以及上一批快速工具、小组件的 12 组回归通过。

应用快捷方式选择器曾只按 AccessibilityService 判断宿主，源码检查后补齐令牌宿主；修正后的选择页和取消已实测。测试工具遇到过最近任务页下回调应用被后台延迟、任务标题在滚动区下方不可见、共用脚本切换证据目录后误比旧基线，分别调整了读取时机、点击目标和目录。当前基线逐项相等，没有为这些工具问题改动生产行为。

证据在 `verification/lsp-tools-controls/`，主要入口 `tools/verify_lsp_tool_controls.py`，可用 `remaining` 从任务切换接续。原 APK、偏好基线、模块表独立保存，不覆盖上一批。构建与 lint 通过，主应用 0 错误／249 警告，原型 0 错误／20 警告；364 项配置／备份、22 项编辑、动作命令、Root 握手／取消、API 33 放大接口链接检查及原 LSP／弹窗断言通过。Windows 构建环境已换用用户新装的 Zulu JDK 21.0.13.0.3。

第二批收尾已经完成。清除实验状态、恢复原 APK 并卸载原型与测试应用后进行了完整重启。ADB 曾未自动恢复；机主解锁重连后，已重新读取安装包哈希、全部偏好、原小组件绑定、模块四张表及设备设置，均与本批基线一致。当前启动日志没有原型加载，实验输入和工具窗口无残留，最终电源状态为 `Dozing`。见 `final-state.json` 与 `postboot-widget-host.json`，没有提交、推送或发布。

## 第三批触控板与旋转（2026-11-17）

触控板沿用原界面、速度与动作时长：单击 60 ms、长按 650 ms、滚动 260 ms，双击的间隔仍为 65 ms。无障碍路径继续调用原 `dispatchGesture`；LSP 路径经宿主和 SystemUI 的受限 Binder 请求框架端的有限单指序列。只接受当前窗口授权、默认显示屏、机主用户和当前前台任务，坐标与时长／次数都有界限。

`TouchSequence` 负责配对按下、抬起与取消，`TouchRequestGate` 拒绝迟到的旧请求；`ToolTouchInjector` 在框架端执行。任一端进程死亡、明确停止、锁屏、旋转或前台变化都会取消。长按没有中间 MOVE 时也定期检查目标，避免到抬起才发现任务已经改变。SystemUI 的 `TouchObservation` 识别本次注入的时间标识与指针类型，避免自己的输入再次进入手势识别，不伪造无障碍事件标志。

常驻工具现在由 `PersistentRuntime` 中的 `ToolRuntime` 持有，显示尺寸变化只重建输入监听，保留窗口授权。应用收到配置变化后关闭临时面板，按原实现重新定位导航键、触控板与光标；输入在几何更新期间暂停。过期旋转方向的打开请求被拒绝。限时开发实验仍会在显示变化时结束，旋转保留是在常驻模式验证的。

最终框架版的基础操作、单双指滚动、触屏通道中断、切换前台、停止以及两端进程退出均通过；目标测试 Activity 的事件记录确认了取消，不只依赖注入 API 的返回值。横竖屏往返保留三个窗口和一份授权，点击坐标正确，宿主 PID 不变。原无障碍单击／长按回归通过。124 项触摸／序列门控／端点死亡检查与原 LSP 检查通过，构建、lint 通过。

测试中特别区分了两种输入：普通 ADB 注入与程序注入共用虚拟设备，重叠时新 DOWN 可能被系统拒绝，不能代表真人打断。中断用例改为从本机触屏设备节点输入，并保留读取端后核对事件顺序。没有绕过系统输入校验。其依据是 [AOSP 输入分发中的注入设备处理](https://android.googlesource.com/platform/frameworks/native/+/refs/heads/main/services/inputflinger/dispatcher/InputDispatcher.cpp) 与 [无障碍触摸事件的构造及取消](https://android.googlesource.com/platform/frameworks/base/+/refs/heads/main/services/accessibility/java/com/android/server/accessibility/MotionEventInjector.java)，实际表现以本机记录为准。

资料在 `verification/lsp-touchpad/`。聚合事件文件曾因测试模块被作为库调用而覆盖了前面的行；已把从真实命令输出确认的 17 项 PASS 汇总到 `framework-device-summary.json`，保留细节另见 `touch-results.json`，未补造缺失的事件数据。记录函数已改为先合并原文件，并逐例保存。测试入口 `tools/verify_lsp_touchpad.py` 的 `death` 应放在最后。

第三批收尾已完成。用户仅首次解锁、未重插 USB 后，ADB 恢复，后续按先等待首次解锁的顺序排查本机连接。原 APK 哈希、偏好、设备设置、小组件绑定和模块四张表均已重新核对。CHO 无障碍在重启前虽已恢复，重启后却再次读为关闭；已恢复原列表并从 Bound services 确认实际连接，撤销原因未单独查证。实验应用、设备节点预检文件及实验窗口／监听器无残留，最后读回 `Dozing`。证据为本批 `final-state.json`，没有提交、推送或发布。

## 下一阶段

继续接音量按钮、放大／屏幕移动、手势试划回传和正式设置入口。快速工具的关闭前台应用已接入；其他依赖设备服务的按钮仍需按功能验收，不能把窗口显示通过写成所有按钮通过。当前 LSP 音量动作只调用系统音量条，辅助按钮等待系统窗口观察通道。触控板和常驻工具的旋转保留见第三批记录。

开启常驻后完整重启的首次解锁恢复、长时间后台与耗电、非机主用户，以及真实清除主应用数据时的恢复边界仍待实测。本轮重启加载的是默认关闭的原型。当前输入有机主用户检查，不代表已经支持多用户。不要把一次 SystemUI 重载和短期进程结束测试写成长期存活或常驻开机验收。

其他工具仍需分别适配动作权限与回调。小组件的应用 UID、宿主 ID、提供方配置和现有布局继续保留；应用内容进程冻结不能靠系统窗口还存在来掩盖。需要 Root 的操作仍在原应用或明确的特权辅助进程执行，不在 SystemUI 中假定能直接运行 `su`。

其余工具窗口和原生预测式返回尚未接入，没有长期后台与耗电结论，也未验证其他固件。正式版仍为 0.4.1，未提交、推送或发布实验包。

接口依据：[libxposed API 102](https://github.com/libxposed/api)、[模块生命周期与热重载约定](https://libxposed.github.io/api/io/github/libxposed/api/XposedModuleInterface.html)、[AOSP ApplicationSharedMemory](https://github.com/aosp-mirror/platform_frameworks_base/blob/main/core/java/com/android/internal/os/ApplicationSharedMemory.java)。Android 隐藏输入方法同时以本机签名读回和实机调用为准。
