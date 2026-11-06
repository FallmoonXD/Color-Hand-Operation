# Live system material policy

Implemented 2026-11-05 in `ColorOsSurface`, `NativeMaterial`, `OriginalMaterial` and `LightSurface`. The existing AGSL assets, capture radius, crop mapping, accepted tint/edge/shadow/optics values and native entrance parameters remain unchanged when both system material switches are enabled.

`ColorOsTheme.materialBlur`, `materialStroke` and `dark` are the source of current policy. Theme already combines the user's material settings with power-saving/global window-blur restrictions. No private system setting is written by these classes.

## Runtime behavior

| Change while a panel is open | Result |
| --- | --- |
| Blur disabled | Immediately removes this native capture request, invalidates its callback generation, clears pending/displayed-frame references and unsubscribes from native clients. Window background blur is forced to zero. White-content floating panels use the system volume panel's opaque inverse fallback; Settings-style dialogs keep their matching system card. Existing Drawable/window alpha still controls entrance/exit. A pending first-frame entrance is released immediately. |
| Blur enabled again | Re-enrolls the attached surface as a native client and obtains a fresh frame. The existing fallback/native transition remains in use. Old buffer callbacks cannot reattach a stale frame. |
| Stroke disabled, blur enabled | Keeps capture, backdrop mixing and tint. Sets only edge-near/edge-far alpha, inner-light alpha and optics alpha to zero. Fallback reflection, rim and inner-light drawing are skipped. |
| Stroke enabled again | Restores the same accepted values for the current light/dark palette. |
| Night mode changed | Updates the current shader's tint and optical uniforms before its next draw, reusing the existing backdrop. It does not restart capture, reset entrance, or wait until the panel is fully visible to recolor. |
| Host detached | Cancels entrance timeout/initial-capture retry, removes capture and pre-draw observation, clears frame references and closes the host theme subscription. Reattaching reads current policy again. |

Native attachment and active capture are separate states. The pre-draw observer remains attached while blur is disabled so older window callers cannot turn blur back on by setting their radius after assigning the background. This observer posts no animation frames or timer; it only enforces the zero radius when an ordinary traversal occurs.

Blur and stroke remain independent: disabling blur selects an opaque surface, while any enabled fallback optical edge still follows the stroke setting. Disabling both produces the opaque system surface without highlights.

## Opaque fallback and foreground pairing

The accepted floating tools/volume panels have white foregrounds even in light mode. Using the light Settings card here would hide their labels and filled slider tracks. `LightSurface` therefore reads `com.android.systemui` resources by name for style 2/0, with the captured values as portable defaults:

- Outer floating surface (style 2): `oplus_volume_panel_background_default`, `#FF333333`. Source: `analysis/visual-test/systemui-jadx/sources/com/oplus/systemui/volume/utils/material/VolumeBlurManager.java`, `getVolumePanelBackground`, which supplies this color to `AutoBlurDrawable`; resource `analysis/visual-test/systemui-res/res/values/colors.xml:1858`.
- Child control (style 0): `oplus_volume_settings_coui_background`, `#33FFFFFF`, composited onto that opaque panel to produce `#FF5C5C5C`. Source: `VolumeSurfaceFactory.solidFor` / `createSolidPlaceholder`; resource `colors.xml:1864`. Compositing prevents the disabled-blur path from exposing the scene through children and keeps controls visibly separate.
- Dialog surface (style 3): `ColorOsTheme.card`. `ColorDialogBuilder` uses `ColorOsTheme.text/secondary`, so a light dialog must retain a light card, while a dark dialog retains its dark card.
- Accent surface (style 1): current `ColorOsTheme.accent` with opaque alpha.

Resource reads happen on construction/policy changes, not every draw. The enabled material shader paths are unchanged. Fallback selection is established before drawing/entrance; there is no post-entrance recoloring step.

## Child drawable subscription ownership

`MaterialThemeBinding` walks the Drawable callback chain, including wrappers such as `RippleDrawable`, to find its actual View. A host has one shared theme attach listener/subscription with weak entries for its material drawables. Repeated draws do not add listeners. Replaced backgrounds are pruned; reusing the same Drawable or moving it to another View can register again. The subscription closes on detach. Explicit window attachments work before their Drawable callback is assigned.

Nested `OriginalMaterial` / `LightSurface` fallbacks are marked as managed by their parent, so they do not create duplicate watchers. Standalone instances can still observe their host directly. Palette-only changes also invalidate opaque fallback drawables.

## Validation and limits

Run `validation/material-policy/run.ps1` from `standalone`. It extracts and compiles the **actual production** host-binding helper and palette method, rather than a copied implementation:

- **14 lifecycle assertions** passed using minimal View/Drawable/theme stubs: nested callbacks, repeated draw registration, detach/reattach, replacing and reusing a background, empty-host cleanup, explicit pre-attach windows, and moving a Drawable between hosts.
- **64 palette assertions** passed across light/dark, outer window/dialog styles and two densities. Every enabled uniform array matches the frozen accepted values exactly; disabling stroke changes only the four intended alpha entries and preserves tint and geometry.
- **10 opaque readability assertions** passed: native inverse tokens, distinct control layer, white-label contrast, light/dark dialog foreground pairing, accent preservation, and alpha compositing boundaries.
- All four modified production classes and the real `ColorOsTheme` compiled against API 35 with `javac --release 17`.

These checks do not execute the OEM Binder capture service, GPU shaders, Window blur or actual on-screen theme transitions. Root owns those device tests. The shared native bridge already unbinds after approximately 15 seconds with zero clients; this change stops capture immediately and leaves that existing bounded idle grace period intact. It does not add a new persistent service or loop.

The old sampled-background compatibility renderer releases its cached sample when blur is disabled and requires a fresh sample afterward. Production surfaces use the live native pipeline. Displayed hardware bitmaps are dereferenced rather than forcibly recycled because RenderThread may still hold them; only unpublished pending buffers are explicitly recycled.
