# ColorOS 17 shared controls

Implemented 2026-11-05 in `replica.ColorSwitch`, `ColorSlider`, `ColorRadioButton` and `ColorCheckBox`. These classes draw their COUI shapes and transitions themselves. Framework widgets provide text, checked-state persistence, keyboard behavior, radio grouping, accessibility and slider input; AOSP switch/slider/button graphics are removed.

## Source evidence

The dimensions and selectors were checked against the **current phone's Settings APK**, extracted to `analysis/coloros17-settings/resources/res`. Rendering and spring behavior were traced through `analysis/visual-test/safecenter-jadx/sources/com/coui/appcompat`; the SystemUI copy has matching basic dimensions.

| Control | Source | Values used |
| --- | --- | --- |
| Switch | Settings `values/dimens.xml`: `bar_width`, `bar_height`, `outer_circle_width`, `circle_padding`; `values/styles.xml`: `COUISwitchStyle` | 44 × 24 dp track, 18 dp thumb, 3 dp inset, 20 dp travel |
| Switch motion | `couiswitch/COUISwitch.java`, `olivelink/COUISwitchParams.java`, `d1/b.java` | Toggle response 0.4 s / bounce 0.3; release attach 0.4 / 0.2; press response 0.3 / bounce 0; thumb horizontal stretch 1 → 1.3 → 1, peak at 133/383 travel |
| Slider | Settings `COUISeekBar` style and `coui_seekbar_*` dimensions; `seekbar/core/COUISeekBarCore.java`, `handler/HorizontalOrientationHandler.java` | 20 dp capsule track, 14 dp center inset, 1.4× pressed track height; thumb radius 6 → 8 dp |
| Slider motion | `seekbar/animation/StandardAnimationHandler.java` and `COUISeekBarCore.java` | Track enlargement 0.5 / 0; thumb enlargement 0.5 / 0.3 and return 0.5 / 0; optional progress transition 0.3 / 0 |
| Slider material | `seekbar/renderer/TexturedThumbRenderer.java` | A: white gradient alpha 230/230/140 at stops 0/0.2/1. B: `#77FFFFFF`, clipped white inner shadow radius 5 px, stroke 10 px, offset (0, 0.4 px). C: white cover, press response 0.5, release 0.3; texture response 0.3 |
| Radio | Settings `drawable/coui_btn_radio_on.xml`, `coui_btn_radio_off.xml`, `coui_btn_radio_off_to_on_animation*` | 24 dp viewport; selected outer radius 10, white inner radius 6; unselected radius 9.2, stroke 1.3; 150 + 150 ms 1 → 1.08 → 1 pulse, 300 ms selection with cubic (0.3,0,0.1,1) |
| Check | Settings `drawable/coui_btn_check_on_normal.xml`, `coui_btn_check_off_normal.xml`, `coui_checkbox_unselected_to_selected*` | 24 dp viewport; selected 18 dp rounded square with radius 4; unselected 16.4 dp square/radius 3.2/stroke 1.3. The animated vector's translated 72-unit check centerline is scaled to 24 units; 300 ms trim animation and 150 + 150 ms pulse |

The radio therefore uses the original filled accent disc with a white center. Recoloring an AOSP radio button would retain the wrong geometry.

`animation/dynamicanimation/COUISpringForce.java` defines angular frequency as `2π / response`, stiffness as its square, and damping ratio as `1 - bounce`. `ColorControlPhysics` uses the analytic critically damped/underdamped/overdamped solutions. It does not approximate a spring with a fixed cubic easing or Euler integration.

## Public contracts

- All four classes have `(Context)`, `(Context, AttributeSet)` and `(Context, AttributeSet, int)` constructors.
- `ColorSwitch` extends `CompoundButton`: checked state/listeners, enabled state, text and content description remain available. It exposes the accessibility class `android.widget.Switch`, supports taps and horizontal dragging, mirrors travel for RTL, rejects a vertical scroll, and restores the current checked state on CANCEL or extra-pointer cancellation.
- `ColorSlider` extends `SeekBar`: `setMin`, `setMax`, `setProgress`, `setProgress(value, animate)` and the standard `SeekBar.OnSeekBarChangeListener` are preserved. The framework retains actual touch, keyboard and accessibility progression, including `fromUser` and tracking callbacks. `isTouchCancelled()` is set **before** stop callbacks for real CANCEL, disable and detach; the next DOWN resets it. A successful UP clears touch ownership before callbacks, so a synchronous page rebuild cannot cancel or commit the same drag twice. Pending ScrollView taps are cancelled even before tracking has begun.
- Radio/check extend their corresponding framework widgets and use a stateful custom button drawable. `RadioGroup` can still enforce one selection; standard state restoration and keyboard/accessibility actions remain intact.
- Radio/check/switch explicitly set `Gravity.CENTER_VERTICAL | Gravity.START`. Their button/text alignment does not depend on an omitted framework style's default TOP gravity, nor on corrective padding.
- `ColorOsTheme` provides dynamic accent/text/disabled colors, the user's font at weight 400, material-effect enablement and animation scale. Attach-scoped `watch` subscriptions refresh colors/fonts without keeping removed pages alive. No control hardcodes a blue accent.

## Animation and lifetime

Physical animations have per-view frame callbacks only while settling. Retargeting keeps velocity; final convergence snaps to the exact target and removes the callback. The driver applies the system animator scale once. Radio/check use finite `ValueAnimator`s, whose timing is already scaled by Android, so their durations are not multiplied a second time. Disabled animations or a detached/hidden host snap to the target. Detach cancels animations, tracking and cached slider material bitmaps. No process-wide animation ticker, executor or loading loop is added.

The slider texture uses a cached maximum-size light layer, scaled during thumb expansion, instead of allocating a new bitmap for every frame. It is disabled with the system material-stroke preference. This is a local renderer derived from COUI sources; it does not claim to instantiate private COUI/Oplus renderer classes or reproduce their entire vendor spotlight/velocity-deformation pipeline. The switch follows the source's solid thumb path; no invented text or extra active-state icon is drawn.

## Verification

Run `validation/color-controls/run.ps1` from `standalone`. The production spring implementation passed **55 assertions**: matching position/velocity at 60 and 120 Hz, matching one delayed frame with many small frames, convergence of every parameter pair used by the controls, monotonic critical damping, bounded toggle overshoot, continuous reversal, slider nonzero minimum and overflow-safe range handling, RTL selection consistency and exact source stretch endpoints.

The same command extracts the actual production slider touch, wrapped-listener and cleanup methods and passes **16 cancellation assertions** with a small framework callback model: real and synthetic cancellation before commit, disable/reenable, pending ScrollView input, reentrant detach, delayed tap-UP and synchronous rebuild after a successful stop. The model verifies our cancellation contract; it does not substitute for Android dispatcher/device testing.

The switch follow-up adds **18 callback/motion assertions** using the actual `ColorControlMotion` and extracted production switch input/state methods: first binding without animation, same-value updates without restarting, synchronous listener timing, rejected/reentrant changes, drag rejection, disabling/removing a control during its callback, rapid reversal, live animation scale, hidden-host cleanup and more than five seconds of repeated retargeting.

The switch now starts its spring clock before the business listener, so a synchronous settings callback does not postpone the clock; after the callback the final checked state remains authoritative. It caches theme colors when attached or notified, instead of resolving/hash-checking the palette two to four times per draw. Motion reads Android's cached `ValueAnimator.getDurationScale()` directly, keeping live system animation scaling without a per-frame theme lookup. Settled no-op animations post no frame; retargeting refreshes the settling timeout without resetting velocity. COUI response/bounce, track/knob dimensions and accepted colors are unchanged. Original `COUISwitch.setChecked(boolean,boolean)` also rejects unchanged state; scheduling before our app callback is a local latency correction, not a claim about that original method's callback order.

The four controls and their helpers compiled with the actual new `ColorOsTheme.java` against Android API 35 using `javac --release 17`. This validation does not exercise Android input dispatch, TalkBack, actual GPU rendering or the phone's visual result. Root owns full application build/deployment and those device checks.
