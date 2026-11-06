# 0.3.13 OHO 原轮廓与 ColorOS 玻璃图标

日期：2026-11-06。包名 `dev.edgeflow`，版本 `0.3.13-glass-icon` / versionCode 16。实机为 OnePlus PKR110，ColorOS 17 / Android 17。

## 设计与来源

- 手机、屏幕、前手、后手及袖口的五个主体 `pathData` 保持用户提供的 OHO+ 9.3.13.0 原值；没有重新绘制手部结构。
- 原三个圆点替换为向内的左箭头，尾部逐渐隐入屏幕。机身和手使用统一的紫色玻璃明暗、窄边缘高光与柔和接触阴影。
- 背景参考本机 Settings 资源 `about_device_top_video_last_frame_17.webp`，采用紫、珊瑚与橙色。设计数据保留取样颜色及来源；取样来自已渲染位图，并非系统着色器参数。
- 实际资源是 Android 原生矢量中的渐变与裁切反光层。图标的玻璃效果由矢量渐变绘制，未使用 ColorOS 材质着色器。主体不透明遮挡，避免手机边缘透过手部产生叠色。
- 单色主题层删除旧屏幕路径和其中的圆点孔洞，保留手机外框、手与箭头；通知小图标同步更新。单色手机和手合为一个 nonZero 路径，几何不变，一次光栅化消除独立路径交界处的抗锯齿接缝。

原始矢量及 SHA-256 保存在 `design/oho-original-9.3.13.0` 和 [设计数据](../design/color-hand-operation-icon.json)。本轮未改动原始来源文件。

## 可编辑资源

`tools/build_brand_icon.py` 生成七个运行时 XML，并通过 `tools/icon_preview.py` 从同一批原生 XML 生成 SVG、PNG、遮罩及通知预览，不维护另一份独立预览几何。预览可查看 [512px 图标](../design/color-hand-operation.png) 与 [圆形、圆角和主题遮罩](../design/icon-mask-preview.png)。

重新生成预览需要 Pillow 和 resvg_py 0.5.0。后者安装在本地 `.toolchains/icon-preview`，安装命令见 renderer 文件头；源码构建使用已提交的 XML，不依赖这些预览工具。

## 验证

- 原始来源文件哈希、五个主体路径相等、旧圆点移除及单色层不含透明度属性检查通过；生成记录位于 `design/icon-generation-checks.json`。
- 预览转换器的 44 项原生向量 / SVG / resvg 断言通过，覆盖渐变、裁切、填充规则与变换。`assembleDebug --offline` 通过。
- 覆盖安装成功；安装版本及 APK SHA-256 与本地构建一致：`b785c6ee361f06d1ee79c4c6067ca8a728104f999a7ec66a7984cb15c5031ef9`。
- 直接通过手机 PackageManager 和 VectorDrawable 渲染已安装的自适应、单色资源，确认 Android 上的箭头、渐变和裁切。最终单色图以非零 alpha 区域向内收缩 3px 检测，内部非全不透明像素数为 0；旧版独立路径的 153 个接缝像素已消除。
- 桌面“搞机”文件夹中的图标已刷新，并可点击进入 MainActivity。自适应形状由系统启动器决定。
- `edgeflow`、`replica_widgets`、`edgeflow_launcher`、`edgeflow_shortcuts` 四个偏好空间与改动前一致。本轮仅修改品牌资源和版本号，没有重新修改手势、设置页或浮层代码。
- 已关闭 USB 保持唤醒并息屏，读回 `mWakefulness=Dozing`、`mStayOn=false`。

实机图片与结构化结果位于本地 `verification/glass-oho-*`。本轮未切换用户的系统桌面图标主题；单色层通过已安装资源的原生绘制验证。其他启动器及固件未实测。
