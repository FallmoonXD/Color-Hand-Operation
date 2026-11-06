# Handless volume icon

Historical proposal, superseded by the [original OHO+ restoration](original-oho-icon-restoration.md) at the user's request. Its source illustration and existing release archives are retained; it is no longer referenced by active app resources.

2026-11-06. This replaces the earlier hand/phone vector illustration after the user explicitly requested removing the hand and approaching the volume and layered material of Weather, Wallet and OPPO Health. Versioning, APK installation and device rendering were handled separately.

## Source and packaging

The foreground is a project illustration generated with the built-in OpenAI imagegen tool. It is not an extracted ColorOS asset or a native procedural glass shader. The source is `design/color-hand-operation-volume-foreground.png`, 1254×1254 RGBA. Its open icy-blue frame has thickness and curved reflections; the coral ribbon passes in front of the left frame and behind the right frame. There is no hand or filled screen.

`tools/build_brand_icon.py` copies the source byte-for-byte to `app/src/main/res/drawable-nodpi/app_icon_volume_foreground.png`. It does not paint, crop, re-encode or recolor that source. The SHA-256 of both files is `c13c03bf723db6c94ab875c161dbc0171ede7da186f2e753b5a5e5d2fe0ac169`. The source's alpha-greater-than-32 bounds are `(228,191)-(968,1065)`.

The native foreground uses an `InsetDrawable` with **6% on each side**, wrapping a filtered `BitmapDrawable` with fill gravity. The separate native gradient background runs from `#FAFBFD` through `#F6F8FC` to `#EDF2F8`. This preserves adaptive launcher masking and avoids baking a square background into the foreground. Both launcher entries continue to supply a separate monochrome drawable.

The actual reference resources and manifest entry points are recorded in [the three-app asset audit](coloros-weather-wallet-health-icons.md). Weather, Wallet and Health use flattened bitmap launcher assets. Wallet's unused Android Studio robot template was specifically excluded. Their authoring renderer cannot be inferred from those bitmaps.

## Independent monochrome and notification geometry

The themed icon is one white path with fill alpha 1 and three disjoint contours: an open frame, the broad front ribbon, and its smaller tail beyond the right edge. Negative space interrupts the left frame and separates the ribbon from the right frame. Nothing becomes translucent to imply overlap. The small notification icon uses the identical path with a shared transform from the specification.

The generator validates contour separation on a 2160×2160 sampling grid and checks that the notification transform retains viewport padding. Edge antialiasing is expected; interior alpha is a single opaque tint.

## Validation and limits

- Generated the seven Android XML resources, original PNG copy, two SVGs and comparison previews from the updated JSON specification. Regeneration cannot restore the retired hand vectors: those paths are no longer part of the source specification or generator.
- An isolated AAPT2 compile and resource-only API 35 link passed using the actual generated XML and PNG. This validates fraction inset attributes and resource references without building or installing the full app.
- All 14 generated outputs were hashed before and after another generator run; they were byte-identical. All generated XML and SVG files parse, and both native monochrome drawables contain exactly one opaque path.
- The compiled resource PNG was decoded and compared with the source: dimensions, all alpha values, and RGB values for every nontransparent pixel are identical. Standard AAPT2 PNG crunch normalized hidden RGB in 281 fully transparent pixels (maximum channel difference 3); there is no visible pixel change. The source and copied resource PNG remain byte-identical. No global PNG-crunch build option was changed.
- Viewed the original foreground, rounded-square/circle comparison and notification preview. The comparison retains actual **72 px and 44 px** examples. The main frame and ribbon stay recognizable in both masks.
- The normal SVG embeds the same original PNG, while the monochrome SVG contains the native path. The normal preview is an approximation of Android filtering and adaptive masking, not evidence of installed rendering.
- `design/icon-generation-checks.json` records the source/resource hash, alpha bounds, inset, contour test and notification bounds. Parent-side Android framework rendering remains the final installed-resource check.

Assets: [mask and size comparison](../design/icon-mask-preview.png), [normal preview](../design/color-hand-operation.png), [notification preview](../design/notification-icon-preview.png), [specification](../design/color-hand-operation-icon.json).

The complete built-in generation prompt and reference-image roles are preserved in [icon-generation-prompt.txt](../design/icon-generation-prompt.txt). The parent rendered the installed adaptive icon and monochrome resource using the OnePlus Android framework; see [0.3.6 device validation](palette-preview-validation.md).
