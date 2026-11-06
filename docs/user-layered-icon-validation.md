# 新彩色图标：分层与导出

彩色图标在 Clip Studio Paint 中调整为橙紫渐变和叠加混合前景。MD3 主题图标沿用原来的三色阶矢量，通知小图标也没有改动。分层源文件在 `design/user-layered/`，画布为 2048×2048、sRGB；文件哈希记录在 `design/color-hand-operation-icon.json`。

## 为什么需要转换混合方式

前景组虽然叫“整体 94%”，实际参数是 **Overlay、100%**。Android 的普通图层合成不能直接照搬这个 PSD 混合模式，所以先用 Clip Studio 导出合成 PNG 和分层 PSD，再保留 alpha、换算前景 RGB。

设原生合成图为 C、背景为 B、前景 alpha 为 A，有效前景颜色为：

```text
F = (C - B × (1 - A)) / A
A = 0 时，F = 0
```

转换后的前景和背景重新合成，与 Clip Studio 导出图逐像素相同，最大通道差值为 0。

## 打包到 Android

运行时使用 864×864 的 nodpi PNG 和 BitmapDrawable 的 fill/filter。原画布对应 432 单位图标中的 `72,72,288,288` 区域；背景向外延伸边缘像素，前景外围保持透明，以适应启动器裁切。

`tools/build_brand_icon.py` 根据当前设计模式调用 `build_layered_icon.py`。生成前检查源文件哈希、合成结果和保留资源，输出彩色图层及不同遮罩预览。APK 构建不需要 Clip Studio 或 PSD 解析库。

## 检查结果

最初接入时使用本地 0.3.15 / 18 构建测试，之后随 0.3.16 发布：

- 圆角、圆形、72px/44px 预览已目视检查。
- `assembleDebug --offline` 构建成功，覆盖安装后 APK 哈希与本地一致。
- 设备 AdaptiveIconDrawable 已绘制 512、96、48 像素图标，检查了 512 和 48 像素彩色结果。
- MD3 的 512 像素 RGBA 结果及 PNG 哈希与更新前完全一致；MD3 XML/SVG 和通知图标文件也未改变。
- 应用偏好文件、无障碍服务列表和原始 `.clip` 文件保持不变。

这次验证的是已安装资源的原生绘制，未检查所有桌面缓存和主题替换情况。本地截图、哈希和设备状态在 `verification/user-icon-import/`。0.3.16 只增加版本号和文档修改，应用逻辑及这些图标资源沿用已测试内容。
