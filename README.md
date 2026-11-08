# Color Hand Operation

<p align="center"><img src="design/color-hand-operation.png" width="128" alt="Color Hand Operation 图标"></p>

给一加和 ColorOS 做的侧边手势工具，灵感来自三星的 One Hand Operation+。默认从右边缘往里短划是返回，往左下方长划会打开快速工具，单手就能调亮度和音量。

[下载 APK](https://github.com/FallmoonXD/Color-Hand-Operation/releases/latest) · [功能说明](docs/feature-status.md) · [更新记录](CHANGELOG.md)

## 安装和设置

需要 Android 13 及以上。主要测试设备是一加 PKR110，运行 ColorOS 17 / Android 17。基础手势需要无障碍服务，ColorOS 原生模糊和部分系统动作还需要 Root。[兼容与已知问题](docs/compatibility.md)

1. 安装 APK，打开右上角菜单里的「权限与后台运行」，按提示开启 Color Hand Operation 的无障碍服务。
2. 回到首页，点「左侧触钮」或「右侧触钮」，给直滑、上斜滑、下斜滑分配动作。短滑和长滑可以分别设置。
3. 在「触钮范围」调好位置和宽度，再进「手势试划」，从边缘的色条往里划一次，就能看到识别结果。

## 常用功能

- **按应用设置手势。** 平时用右侧短滑返回，也可以单独给某个应用改成打开快速工具。游戏里容易误触的话，把游戏加入「应用例外」就能停用触钮。
- **从边缘打开工具。** 快速工具里有亮度、音量和播放控制。常看的 Android 小组件可以放进小组件面板，划一下就能看，不用先回桌面。
- **快速启动和切换应用。** 可以把常用应用放进一个文件夹，给文件夹绑一个手势。想切回刚才用过的应用，就打开任务切换器。
- **虚拟触控板和导航键。** 单手够不到屏幕上方时，可以用触控板移动光标。返回、主页和最近任务也可以做成一列虚拟按键，放在拇指够得到的那一侧。

左右两侧各支持最多三段触钮。横屏可以单独调宽度，触区在「手势试划」里可见。

## 自己编译

使用 JDK 21，安装 Android SDK Platform 35 和 Build Tools 36.0.0。设置 `ANDROID_HOME`，或在 `local.properties` 里填写 `sdk.dir`，然后从项目根目录编译。

```sh
./gradlew assembleDebug
```

Windows 用 `.\gradlew.bat assembleDebug`。调试包在 `app/build/outputs/apk/debug/`，编译发布包用 `assembleRelease`。自己编译的 APK 使用本机签名，覆盖安装需要与已装版本签名一致。[开发笔记](docs/README.md)

## 反馈

遇到手势冲突或面板显示异常，可以在 Issue 里附上机型、系统版本和复现步骤。涉及边缘手势时，也请注明横竖屏和系统导航方式。[提交问题](https://github.com/FallmoonXD/Color-Hand-Operation/issues)

---

Color Hand Operation 是独立开发的个人项目。仓库目前未指定统一的开源许可证。图标和系统素材的来源见[第三方素材说明](THIRD_PARTY_NOTICES.md)。
