# 首页标题的连续折叠

## 当前系统源码依据

只针对本机 Settings APK 定向反编译，没有加载它的类或重新解整个 APK：

- `analysis/coloros17-settings/resources/res/layout/homepage_toolbar.xml`：首页使用 `SettingsCollapsingToolbarLayout`，内含固定的 COUIToolbar。
- `analysis/coloros17-settings/single/SettingsCollapsingToolbarLayout.java:19-30`：偏移回调读取连续 `expansionFraction`，大标题 alpha 从 255 插值至 0，scale 从 1 插值至 0.7；展开起点为 24dp start margin。
- `single/COUICollapsingToolbarLayout.java:238-266`：fraction 来自实际 appbar 偏移与可折叠高度，而非一次性计时动画。`:919-925` 设置文字位置/字号插值器；`single/COUICollapsingTextHelper.java:221-246` 连续插值坐标和混合比例。
- `single/COUIEaseInterpolator.java:7-15`：位置/字号曲线为 cubic Bezier `(0.33,0,0.67,1)`。未凭空增加标题弹簧或回弹参数。

## EdgeFlow 的适配边界

保留已认可的 32sp/600 展开标题、18sp/550 收起标题，以及原有栏高、标题 padding、内容起点和两端基线。实际端点均从完成布局的 TextView 测量获取，折叠距离取两个基线的距离，不再使用固定 18/54dp 阈值。

原来两个标题分别处于栏和 ScrollView，交叉淡入时会重影，展开标题还被滚动容器截断。现在它们仅保留测量位置，根层只绘制一份文字；同一文字随滚动连续移动、缩放并插值 600→550 字重。完全收起后固定在原小标题位置。边界下拉沿已有滚动容器位移跟随，不启动第二套回弹。父页前进/返回、insets 和主题变化仍由既有页面流程管理。

由于本应用需要保留收起后的标题，没有照搬 Settings 大标题最终完全消失的 alpha。透明度采用互补交叉淡入叠加的等效值 `1−t(1−t)`，仅绘制一份字形，最小 0.75、两个端点均为 1；这是本应用明确的适配设计，不宣称是系统原值。字号端点也保留用户已认可的尺寸，未采用 Settings XML 中另一套 40sp 声明。

滚动比例直接决定每一帧；没有持续 animator、延时切换、小标题突然替换或不同刷新率的时间积分。PreDraw 监听仅在视图附着时注册，脱离时移除；静止且几何不变时不主动请求新帧。唯一标题提供 heading 语义及其实际字形边界，不拦截点击。

## 验证

`validation/settings-title/run.ps1` 的 **32 项断言通过**：端点字号/不透明度、超范围钳制、单调连续位置、反向路径、RTL 镜像、insets 平移不变、无消失区间和零斜率端点。全部生产 Java 对 API35 独立编译通过。

本代理未操作手机或构建 APK；真实绘制、顶部菜单点击与无障碍节点由主流程录屏/设备验证，纯 Java 断言不能代替这些检查。
