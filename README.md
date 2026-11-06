# Color Hand Operation

<p align="center"><img src="design/color-hand-operation.png" width="160" alt="Color Hand Operation"></p>

面向 ColorOS 的单手边缘手势应用，采用 OHO+ 的交互结构与 ColorOS 17 的设置、浮层视觉。独立包名 `dev.edgeflow`，构建与运行不需要 gulugulu。

**当前为实验版本。实机验证对象为 OnePlus PKR110、ColorOS 17、Android 17、Root 环境；其他 ColorOS 机型欢迎测试，但尚未验证全部专有接口。**

[下载 APK](https://github.com/FallmoonXD/Color-Hand-Operation/releases) · [功能状态](docs/feature-status.md) · [兼容说明](docs/compatibility.md) · [版本记录](CHANGELOG.md) · [开发接续说明](docs/PROJECT_STATE.md)

## 功能

- 左右最多六段触钮，短滑、长滑与方向角设置；系统栏之间的触摸区域预览。
- 快速工具自定义按钮、亮度、自动亮度、媒体音量与连续调节。
- 可分页添加和管理真实 Android 小组件。
- 应用及快捷方式、快速启动、任务切换、触控板、导航键和键盘组合键。
- ColorOS 风格设置页面与浮层；支持系统配色、深色模式及可用的原生材质服务。
- 配置导入导出、应用例外、场景隐藏与独立触钮配置。

与三星 OHO+ 的功能并非完全等价。三星专属服务以及部分高级交互的边界见[功能状态](docs/feature-status.md)。

## 安装

1. 从 Releases 安装 APK，打开应用并启用无障碍服务。
2. 在“权限与后台运行”中按需要开启通知、悬浮窗、修改系统设置、自启动等权限。
3. 完整的 ColorOS 原生材质及部分系统动作需要在 Root 管理器中授权 `dev.edgeflow`。基础无障碍手势不等于所有高级功能均无需 Root。
4. 首次添加小组件时按系统提示授予绑定权限；不要复制其他应用的小组件 ID。

安装 APK 本身不需要运行开发部署脚本。当前 Releases 使用开发签名以兼容已安装测试版；签名密钥不放入仓库。自行构建或 CI 的不同签名 APK 不能直接覆盖该版本。

## 兼容策略

| 项目 | 当前行为 |
| --- | --- |
| Android 版本 | 安装下限 Android 13；不是跨厂商功能保证 |
| 原生玻璃材质 | 探测 ColorOS 服务；缺失、拒绝 Root 或绑定失败时使用回退材质 |
| 亮度 | 从系统读取可信范围；无法确认时禁用调整；仅确认 Oplus 多位亮度能力时写入对应调整值 |
| 单手模式、侧边栏、录屏等 | 使用 OEM 接口，可能随机型或固件变化 |
| 后台常驻 | 优先使用系统自启动和电池设置；可选 Athena 脚本仅在明确验证过的完整固件上执行 |

不要把其他机型的 Binder transaction 编号直接用于当前设备。提交兼容反馈时请提供机型、系统版本、是否 Root、具体操作和经过筛选的相关日志，不必提供序列号或私人配置。

## 构建

已验证工具链：JDK 21、Gradle 9.5.1、AGP 9.2.1、Android SDK platform 35 与 build-tools 36.0.0。

1. 安装上述 JDK 和 Android SDK。
2. 在本地 `local.properties` 设置 `sdk.dir`，或通过 `ANDROID_HOME` 提供 SDK 路径。该文件不提交。
3. Windows 执行 `./gradlew.bat assembleDebug`；Linux/macOS 执行 `./gradlew assembleDebug`。
4. 产物为 `app/build/outputs/apk/debug/app-debug.apk`。离线构建须先缓存依赖，再加 `--offline`。

工程包含 Gradle wrapper 和带来源、哈希及许可文本的 HiddenApiBypass JAR；不需要原 APK 或分析目录。Python 只用于验证、部署和重新生成设计预览，不参与普通 APK 构建。

### 开发设备部署

```powershell
# 只有一台已授权设备时自动选择；多设备必须明确指定。
$env:EDGEFLOW_SERIAL = 'your-device-serial'
python tools/deploy.py
```

脚本覆盖安装并保留数据，开启 CHO 所需的部分调试授权，添加 CHO 无障碍服务并保留其他服务；不会替用户批准 Root。仅在明确迁移旧视觉测试版时使用 `--migrate-visualtest`。普通用户使用系统授权页面即可。

### 图标与测试

- 图标来源与可编辑参数：`design/color-hand-operation-icon.json`。
- `tools/build_brand_icon.py` 使用原始轮廓生成运行时 XML 和预览。可选依赖安装说明见 `tools/icon_preview.py`、`tools/icon_geometry.py`。
- `validation/` 包含手势、配置、菜单、几何与能力失败路径检查；各目录提供运行脚本。
- 设备验证脚本使用 ADB 和独立 UI 树探针。部分脚本针对已验证手机的坐标，不能直接假定适合其他屏幕。
- 个人截图、完整设备配置、ADB 结果和构建缓存不提交。历史文档中指向 `verification/` 的链接代表开发者本地证据。

## 来源

本项目不是三星或 OPPO 官方应用。OHO+ 的交互与原始图形、ColorOS 的视觉参考和回退资源均保留来源记录，见 [THIRD_PARTY_NOTICES.md](THIRD_PARTY_NOTICES.md)。仓库目前没有为整个工程授予统一的再分发许可，不能将第三方素材视为本项目自有资源。
