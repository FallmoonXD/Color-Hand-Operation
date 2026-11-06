# 0.3.11 页面连续性

版本：`0.3.11-continuous-pages`，versionCode `14`。

本页记录本轮源码设计、离线验证和一加 PKR110 的设备验证。离线断言与设备交互、逐帧观察分别列出，不相互替代。

## 返回时的真实页面层级

0.3.10 保留的是独立 Dialog。进入配置页时隐藏 Dialog，等配置页返回动画结束再恢复，所以操作列表虽然保留了滚动位置，却在页面退场后才出现。该版本的已完成实机记录继续保存在 [0.3.10 选择页保留测试](retained-menu-validation.md)，不覆盖为本次实现的结论。

0.3.11 使用 `ColorActionSheet.showEmbedded()` 将选择页的真实视图挂到所属触钮页的 `ColorSettingsScaffold.root` 中。`MainActivity.ActionChooser` 保留这个 scaffold、滚动容器、勾选状态绑定和颜色指示器：

1. 进入配置页时，原触钮页连同选择页一起参与向左退场，配置页从右侧进入。
2. 转场完成后，原 scaffold 从显示容器移除，但同一对象及其子视图由 ActionChooser 保留。
3. 返回时，在动画开始前复用原 scaffold，并恢复选择页交互。原选择页成为退场配置页下的真实视图，随着配置页滑出自然露出；不再等待 `whenSettled()` 后打开一个窗口。

这条路径不创建 Dialog，也不把选择页截图当作替身。已测量的选择页即使尚未重新 attach，也保持可绘制状态，避免返回首帧缺失。Activity 因主题、字体或配置变化重建时，旧视图仍按生命周期释放；由保存的动作键、所属页、栈深度和滚动位置重建新视图，并使用 `showEmbeddedRestored()` 跳过重复入场动画。

## 生命周期、输入和无障碍

- 嵌入模式的 `suspend()` 只停止输入、拖动和无障碍访问，不隐藏其绘制。原页面可以继续作为转场的底层内容。
- 挂载时暂时将底层设置正文和工具栏设为 `NO_HIDE_DESCENDANTS`。关闭选择页后恢复各子视图原有值，不用 `GONE` 破坏可见背景。面板通过负 margin 抵消 scaffold 的系统栏 padding，避免重复扣除安全区；关闭时恢复宿主的 clipping 设置。
- `ColorPageMotion` 为退场页添加透明输入拦截层，并记录其原焦点与无障碍属性。保留页再次使用前移除拦截层、恢复属性；不改变蓝色控件的 enabled 绘制状态。记录使用弱引用，不为已经弃用的页面保留对象链。
- 选择页显示时由 MainActivity 单独处理返回，避免同一轮预测返回既关闭选择页又缩放底层设置页。配置页返回仍执行页面转场。
- 普通 Activity pause/stop 保留嵌入选择页。若用户已经选中动作且退场回调尚未完成，pause/stop 会取消待提交动作并清理面板，避免切到后台后继续提交。destroy 始终释放视图和订阅。

## 小组件返回时的页面保留

`WidgetActivity` 使用现有透明、浮动 Activity 和自己的材质/动画。此前 MainActivity 每次从暂停恢复到非首页都会整体 `render()`，因此小组件关闭后即使设置数据未变，也会更换页面树。

本轮 MainActivity 改为比较当前页实际显示的数据及主题状态，仅在需要时重建。没有变化时保留当前页面和滚动状态。此项修复在设置页生命周期中完成，没有修改小组件材质、组件绑定或手势动作引擎。

## 离线验证

在仓库根目录执行：

```powershell
python -X utf8 standalone/validation/settings-sheet/run-retention.py
& standalone/validation/page-motion/run.ps1
```

| 检查 | 本轮结果 | 覆盖范围 |
| --- | --- | --- |
| 选择页状态与主题 | 53 项通过 | 抽取生产 suspend/resume/show/mount/close/lifecycle/refresh 方法及首帧任务；同一视图和滚动位置、首帧连续暂停恢复、Dialog 隐藏窗口清理、主题角色重采集、嵌入 inset、底层无障碍恢复、暂停期间取消待提交动作 |
| 页面转场 | 29 项通过 | 保留全部生产字段与方法，仅替换 Android 视图/动画边界；完成回调、取消与迟到回调、快速连续切页、保留页拦截层清理、焦点恢复、返回取消后状态 |
| Android 类型编译 | 通过 | ColorActionSheet / ColorPageMotion 独立编译；page-motion 脚本也对未改写生产文件执行 Android SDK 35 编译 |

共享几何的既有 363 项测试本轮重新运行通过；本次 53/29 项主要验证状态与对象层级，不模拟 WindowManager 焦点、系统预测返回分发、实际触摸路由或逐帧像素。操作目录 39 项、包含旧动作兼容的配置 217 项也通过。

## 列表和导航键界面

- 恢复“需要设置”分组标题，与其他标题统一为 13sp / 500 字重，上下 padding 从 18/6dp 收到 8/3dp。选项摘要与标题间距收为 2dp，单行选项维持 56dp。
- 启动指定应用的行高从 56dp 增到 72dp，40dp 应用图标保持原尺寸。
- 删除 `app_launcher` 的新增候选，保留其历史执行 ID、导航配置和导入兼容，不重写已有手势。
- 导航键行尾使用统一齿轮，编辑菜单锚定于该按钮；隐藏当前位置不适用的移动/移除命令。添加、更换进入采用同一 Settings scaffold 的“选择导航动作”二级页，不再叠加旧居中列表。
- 从配置页返回时，选择列表绑定和底层手势摘要都读取最新配置。嵌入的原页面保持身份，不靠重建页面刷新摘要。

## 设备验证

在已连接的一加 PKR110 / ColorOS 17 上安装并验证。构建 APK SHA-256：`dcb9500bf3c941432e334ffaf7f68c72af52e2e7081fad87fe0ad3e23188dc4c`。

| 设备检查 | 结果与证据 |
| --- | --- |
| 七个配置页返回 | 同一选择页节点、窗口、滚动位置和勾选状态保留；包含快速工具编辑 Activity 的往返。`verification/continuous-pages-ui-validation.json` |
| 应用与快捷方式 | 右侧配置不改变原不同类型动作；原行读取新目标；首次配置取消返回选择页，成功选择后关闭；应用行高至少 252px（本机 72dp）。同一 UI 报告和 `continuous-pages-app-rows-small.png` |
| 实际返回动画 | `continuous-pages-return.mp4` 及 `continuous-pages-return-frames.png` 显示：配置页尚未退出时，原列表已在其下方参与转场；结束时没有另开选择窗口 |
| 字体、主题与后台 | 配置页内字体重建后再进子 Activity，返回原列表；深色、恢复字体/主题、Home 再进入均通过。显式关闭后不复活选择页。`continuous-pages-environment-validation.json` |
| 导航键编辑 | 方向开关节点不变；前后移动、更换、添加、移除正确保存；从嵌套编辑返回原选择页。`continuous-pages-navigation-validation.json` |
| 小组件手势 | 修复前快速工具页返回后节点被替换，见 `continuous-pages-before-widget.json`；修复后快速工具页以及手势设置、快速启动、可见选择页、应用列表均保留原节点和窗口。后四页完整记录为 `continuous-pages-widget-validation.json`，每页均验证全部偏好不变 |

上述配置编辑使用限定键的 `PreferenceTrial`，结束后恢复测试前值；字体和深色模式也已恢复。最终安装哈希、所有配置存储、快速工具布局、服务与息屏状态另由 `continuous-pages-final-verification.json` 记录。
