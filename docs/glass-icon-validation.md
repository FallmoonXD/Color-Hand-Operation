# Glass phone and opaque hand icon

Historical design, superseded by the [handless volume icon](volume-icon-validation.md). The implementation and validation below describe the earlier hand illustration, not the current foreground.

2026-11-06. This change updates only the editable icon specification, generator and generated icon assets. It does not set an app version or claim device installation.

## Composition and material

The right-hand/phone composition remains informed by the supplied OHO+ 9.3.13.0 icon. All current paths are redrawn. The thumb crosses the front from lower right toward upper left, joining a continuous palm; the rear grip stays behind the device. The hand and cuff are fully opaque. A small nail plane and restrained curved crease distinguish the thumb without the previous hard triangular palm patch.

The phone uses a translucent cyan body, a thin refractive rim and a gradual local sheen. The large hard diagonal reflection was removed. Coral/berry background, ice-cyan glass and mint cuff are local color choices intended to separate the design from the earlier blue/purple icon.

Actual local references read and visually inspected:

- Settings `analysis/coloros17-settings/resources/res/drawable/ic_launcher_settings.xml` and its xxxhdpi foreground/background: separate adaptive layers, narrow light edges, translucent inner parts and light contact shadows.
- `analysis/coloros17-icon-audit/safecenter-original.webp`: a vivid cyan background and a simple white shield with two light planes.

Neither reference contains this glass phone or hand. This is a local adaptation, not an official Samsung/OPPO icon or a claim that these paths were extracted from ColorOS.

## Monochrome correction

The old themed icon contained overlapping alpha 0.16/0.58/0.72 layers. The new monochrome is **one white path at alpha 1**, containing three non-overlapping contours: phone, gesture trail and hand. Negative space separates phone and hand. It has no screen fill or translucent hand layer. Notification uses the exact same path with a shared transform specification.

The generator checks contour intersections at a 1080×1080 sampling grid before reporting success; all intersections are zero. Antialiasing at contour edges is expected, while interior areas are a single opaque tint. Generated normal SVG now includes the same even-odd fill rules as native VectorDrawable. Alpha-bearing sheen stops are represented both in Android gradient colors and preview alpha, so the preview no longer drops the gradient's transparency.

## Validation

- Regeneration completed with `python tools/build_brand_icon.py`.
- Seven generated Android XML resources and two SVG files parse successfully.
- Monochrome and notification each contain exactly one opaque even-odd white path, with identical geometry. Both adaptive launcher entries reference the monochrome drawable.
- `icon-mask-preview.png`, `color-hand-operation.png` and `notification-icon-preview.png` were opened and visually inspected. The comparison board retains actual 72 px and 44 px samples. Circular masking keeps the thumb and palm root visible; only the cuff intentionally extends through the mask edge.
- These checks cover the artwork only. APK installation, Android rendering and launcher tinting were still pending.

Current artifacts: [specification](../design/color-hand-operation-icon.json), [normal SVG](../design/color-hand-operation.svg), [monochrome SVG](../design/color-hand-operation-monochrome.svg), [mask/small-size preview](../design/icon-mask-preview.png), [notification preview](../design/notification-icon-preview.png).

The parent subsequently built and installed 0.3.5, rendered both installed layers with the device framework, and confirmed no translucent monochrome interior pixels. See [installed verification](design-refinement-validation.md).
