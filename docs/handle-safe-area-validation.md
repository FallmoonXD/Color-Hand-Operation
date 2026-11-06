# Handle safe area and coordinate migration

2026-11-06. The user's correction was that OHO+ handle size 100% means the region between the status and navigation bars. The old standalone code multiplied percentages by the entire physical display height. This change gives real touch windows and global settings previews one shared safe-area definition.

## Original and current-device evidence

In the supplied OHO+ 9.3.13.0 source, `n6/y.java` method `F(int)` (approximately lines 1151–1189) calculates handle top and length from real height `H`, status height `S`, and bottom reserve `N`. At size 100%, its capped length is `H-S-N` and top is `S`. `SGPHandleView.c()` applies that calculated top/height. `n6/y.d()` (approximately lines 2235–2246) reads display size/rotation and the framework status/navigation dimensions.

The original has separate landscape settings and an optional IME adjustment. Its IME branch in `F(int)` only applies when `use_adjust_position` is enabled; the keyboard is not part of the ordinary percentage denominator. This patch corrects the available-area baseline. It does not claim to reproduce OHO+'s entire nonlinear minimum-size formula or add its separate IME reposition setting.

Read-only ADB state on the connected OnePlus, before installation:

| Field | Value |
| --- | --- |
| Display | 1264×2780, density 560 / 3.5 |
| `statusBars` frame | `[0,0]-[1264,140]` |
| Cutout safe top | 140 px |
| `navigationBars` frame | `[0,2626]-[1264,2780]` |
| Stable usable rectangle | `[0,140]-[1264,2626]`, height 2486 |
| Framework status height | 40.109985 dp, rounds to 140 px |
| Framework navigation height | 44 dp, 154 px |

The saved primary ranges were approximately `.04987502..950125` of the old full screen, resulting in top 139 px and an exclusive bottom near 2642 px. Both endpoints extend into system-bar areas, so conversion clips them to exactly `0..1` of the safe area: y=140, height=2486. The saved 20.284dp width remains 71 physical pixels; expected left/right x values are 0 and 1193.

## Shared geometry

`HandleDisplayArea` reads maximum WindowMetrics for the display and the union of `statusBars | navigationBars | displayCutout` using `getInsetsIgnoringVisibility`. Insets are not added together. IME, mandatory gesture margins and transient bar visibility are excluded from the percentage denominator.

`GestureHandleConfig.Area` is the immutable display-coordinate rectangle consumed by both real input bounds and global preview bounds. A `0..1` span is exactly its top-to-bottom extent. Right-side x is derived from its right boundary, including landscape side navigation/cutout insets. Width remains dp-based.

Insets changes use `updateViewLayout` while retaining input/preview windows. A real display-size change rebuilds the eight gesture windows, preserves the current preview ownership/draft, and updates the preview to the same new rectangle. Narrow wave windows move horizontally with the safe input edges; their fixed black curve and animation parameters are unchanged. Keyboard visibility still follows the existing `hide_keyboard` preference; it does not resize the handle percentages.

## Version 2 migration and archives

The integer key `handle_geometry_version=2` identifies safe-area percentages. New defaults write it directly. Existing settings initialize through one synchronized migration:

1. Capture all six effective ranges from one preference snapshot, including old shared center/height overrides.
2. Convert legacy pixel endpoints using an upright portrait reference. Keep endpoints already inside the usable area within one pixel; clip endpoints in system bars. A clipped region smaller than the supported 5% expands inward within the usable area.
3. Commit twelve per-handle start/end values and the version marker together. Remove the old shared start/end and center/height aliases after materializing their effective meaning. Width, colors, actions, enabled flags and unrelated preferences are preserved.

Disabled extra handles are included so that enabling them later cannot reinterpret old percentages under the new baseline. Other settings are not unnecessarily materialized. Subsequent initialization is a no-op.

The archive container remains version 2; `handle_geometry_version` is a separate coordinate-space version inside its preferences. Import validates its integer type/range. A legacy complete replacement converts all effective spans before the single transactional preference write. An unversioned partial import converts only affected geometry; a pure action/settings patch never downgrades or re-converts current geometry. A legacy center-only patch preserves each side's current effective height, and a height-only patch preserves each side's effective center. Current version-2 fractions survive export/import unchanged. Preference or archive-store write failures restore the pre-import values.

## Rotation reference and limits

When migration first runs upright in portrait, it uses the actual WindowMetrics safe rectangle. A first landscape or upside-down initialization uses a documented upright-portrait reference: longer display dimension for portrait height, portrait-qualified framework bar resources, and inverse-rotated cutout insets. It never converts legacy positions against the shorter landscape height. The current OnePlus resource dimensions were independently checked against its actual insets.

Public SDK APIs cannot query the real insets of an arbitrary rotation that has not occurred. The resource-derived first-landscape reference is therefore a compatibility rule for this phone, not proof for every foldable, naturally landscape display, external screen or vendor navigation mode. Those device classes require further actual rotation measurements. Normal runtime bounds always use the actual current WindowMetrics, not the portrait migration reference.

## Verification

- `validation/handle-safe-area/run.ps1`: **1562 assertions** covering full-span edges, all four rotated layouts, cutout inverse rotation, left/right symmetry, tiny/invalid ranges, and randomized legacy endpoint preservation.
- `validation/gesture-window/run.ps1`: **40 assertions** against extracted production Service methods. Includes unchanged window identities on inset changes, exact preview/input rectangle equality, no layout movement for unchanged stable insets, rotation, and wave/input edge alignment.
- `validation/config/run.ps1`: **211 assertions**, including one-commit/idempotent/concurrent migration, alias materialization, portrait reference on first landscape initialization, old/current full and partial imports, invalid versions, and rollback. Android measurement is a deterministic test boundary; actual migration/import code is executed.
- Existing `validation/gesture/run.ps1`: **339 assertions** across gesture state, direction, long-duration boundaries, input timers/cancel and handle identities.
- All production Java sources compiled against API 35. Total: **2152 assertions**. The config harness uses the documented Gradle mockable Android jar, because the ordinary SDK stub jar intentionally throws at runtime.

ADB checks here read system state and resources. Migration after installation, WindowManager rectangles, rotation and IME behavior require separate device tests.
