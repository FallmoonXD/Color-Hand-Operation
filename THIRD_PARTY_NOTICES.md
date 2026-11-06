# 资源与依赖来源

本项目不是 Samsung、OPPO 或 OnePlus 官方应用。厂商名称仅用于说明兼容对象与设计来源。

## AndroidHiddenApiBypass

- 上游：[LSPosed/AndroidHiddenApiBypass](https://github.com/LSPosed/AndroidHiddenApiBypass)，版本 6.2。
- 随附文件：`app/libs/hiddenapibypass.jar`。
- SHA-256：`72ac984da7629f68e396c6fe54956cf71a05aa0834467cd592deeca72b2d6d8f`。
- 许可：[Apache License 2.0](licenses/AndroidHiddenApiBypass-Apache-2.0.txt)。

Gradle wrapper 来自 Gradle 项目，使用 Apache License 2.0。构建时下载的 Android/Gradle 工具链不包含在本仓库中。

## OEM 图形与视觉来源

- OHO+ 手势箭头、图标原始轮廓及归档矢量来自用户提供的 Samsung One Hand Operation+ 9.3.13.0。当前应用图标改变了配色、材质与滑动符号；`design/color-hand-operation-icon.json` 记录原始文件哈希和改编内容。
- ColorOS 回退向量及材质参考见 `docs/ui-source-provenance.json`、`docs/overlay-submenu-icon-sources.md`。运行时优先读取可用的系统资源。
- `app/src/main/assets/` 的 AGSL 与 ColorOS 光学管线研究相关；出处及验证范围保留在材质文档中。
- 手机上的 OPPO 视频、音乐、主题商店图标仅用于目视参考，未作为新的应用图标位图打包。

这些来源记录不等于第三方授权书。仓库没有将 OEM 素材重新声明为自有版权或统一覆盖为 MIT/Apache 等许可；目前也没有覆盖整个工程的统一再分发许可。

## 开发工具

Python 图标预览使用 Pillow、resvg_py，轮廓合并使用 skia-pathops。它们是可选开发依赖，不包含在 APK 中，也不随仓库复制其安装目录。版本和安装命令记录于相应工具文件。
