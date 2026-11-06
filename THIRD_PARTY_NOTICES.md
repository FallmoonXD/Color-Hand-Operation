# 资源与依赖来源

Color Hand Operation 是个人项目，与 Samsung、OPPO、OnePlus 官方无关。这里记录使用到的依赖和素材来源。

## AndroidHiddenApiBypass

- 上游：[LSPosed/AndroidHiddenApiBypass](https://github.com/LSPosed/AndroidHiddenApiBypass)，版本 6.2。
- 随附文件：`app/libs/hiddenapibypass.jar`。
- SHA-256：`72ac984da7629f68e396c6fe54956cf71a05aa0834467cd592deeca72b2d6d8f`。
- 许可：[Apache License 2.0](licenses/AndroidHiddenApiBypass-Apache-2.0.txt)。

Gradle wrapper 来自 Gradle 项目，使用 Apache License 2.0。构建时下载的 Android/Gradle 工具链不包含在本仓库中。

## OEM 图形与视觉来源

- OHO+ 手势箭头、图标原始轮廓及归档矢量来自 Samsung One Hand Operation+ 9.3.13.0。当前彩色图标在 Clip Studio Paint 中重新调整了背景、配色和混合效果，MD3 保留原有三色阶设计。分层文件在 `design/user-layered/`，原始素材和导出文件哈希见 `design/color-hand-operation-icon.json`。
- ColorOS 回退向量及材质参考见 `docs/ui-source-provenance.json`、`docs/overlay-submenu-icon-sources.md`。运行时优先读取可用的系统资源。
- `app/src/main/assets/` 的 AGSL 与 ColorOS 光学管线研究相关；出处及验证范围保留在材质文档中。
- 手机上的 OPPO 视频、音乐、主题商店图标仅用于目视参考，未作为新的应用图标位图打包。

第三方素材的权利归原作者所有。项目目前没有统一的开源许可证；上面的来源说明不改变各项素材原有的授权范围。

## 开发工具

图标预览使用 Pillow 和 resvg_py，旧矢量图标的轮廓合并使用 skia-pathops。分层图的导入还用到了 Clip Studio Paint 和 psd-tools。这些仅用于开发，不打包进 APK，普通构建也不需要安装。
