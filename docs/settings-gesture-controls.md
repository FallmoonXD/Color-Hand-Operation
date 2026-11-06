# Gesture settings controls

This change is limited to MainActivity slider, angle and vibration presentation. Gesture recognition and haptic runtime changes are implemented by GestureAngles/GestureHaptics and EdgeHandleView separately. No device validation is claimed here.

## OHO source evidence

- `analysis/oho-9.3.13/jadx/sources/com/samsung/android/sidegesturepad/settings/SGPGestureDetailActivity.java`: quick vibration and custom haptic are independent switches; `x()` shows the intensity controller only for custom haptic. The source scale is 0–30 ms, not Android amplitude 0–255.
- `analysis/oho-9.3.13/resources/res/values-zh-rCN/strings.xml`: quick vibration is `快速振动`, with `识别到手势时立即振动，而不是等到手指从屏幕上松开。`; custom haptic is `自定义振动强度`, with `为%s 设置自定义振动强度等级。`; the level title is `振动级别`.
- `SGPAngleSettingView.java` stores floating slopes and rounds sector labels for display. The new sliders use integer-degree increments as explicitly requested for this revision. Unedited fractional saved angles remain unchanged.

## Presentation and accessibility

All slider titles are plain labels. The current value appears between the two endpoint labels. The central TextView has content description `<slider description>当前值`; its text and state description contain the current number. The SeekBar state description carries the same value. The SeekBar touch area remains 44 dp. Container padding is 16/10/16/4 dp and the endpoint row top gap is 4 dp. Main settings sliders use an 8 dp negative top margin to close the gap inside their unchanged 44 dp touch area; prose, where needed, retains natural multiline height.

Geometry uses existing 1000-step draft controls; cancellation, page detach and lifecycle interruptions do not save drafts. Width, duration and percentage readouts now display integers without changing stored precision. Speed is displayed as 50–300% rather than a decimal multiplier; for example, the retained value 2.35 displays as 235%. The illustrative phone/gesture diagram and its unused View class have been removed.

## Controls for device validation

| Visible control / SeekBar description | Preference | Range |
| --- | --- | --- |
| 快速振动 | quick_vibration | boolean |
| 自定义振动强度 | custom_vibration | boolean |
| 振动级别 | vibration_duration_ms | integer 0–30, shown as percentage |
| 短滑上方限位 | short_angle_up_outer_degrees | 75–85 degrees |
| 短滑向上斜滑边界 | short_angle_up_inner_degrees | 5–70 degrees |
| 短滑向下斜滑边界 | short_angle_down_inner_degrees | 5–70 degrees |
| 短滑下方限位 | short_angle_down_outer_degrees | 75–85 degrees |
| 长滑上方限位 | long_angle_up_outer_degrees | 75–85 degrees |
| 长滑向上斜滑边界 | long_angle_up_inner_degrees | 5–70 degrees |
| 长滑向下斜滑边界 | long_angle_down_inner_degrees | 5–70 degrees |
| 长滑下方限位 | long_angle_down_outer_degrees | 75–85 degrees |

The angle page and entry are `手势角度`; the page contains short/long groups with four sliders each. All angle steps are one degree: an inner-boundary SeekBar progress of 40 means 45 degrees. These controls edit real runtime boundaries; they do not rewrite the former symmetric angle on page opening.

Default feedback follows system haptics. Explicit legacy amplitudes remain intact until a new duration is committed. A legacy muted configuration is displayed as custom zero level; before explicit editing it is materialized as custom zero and the old hidden master is enabled, preventing an accidental feedback change from editing quick feedback alone. Disabling custom feedback selects system feedback. The strength row is rebuilt from the saved policy when toggled, so returning to a retained legacy amplitude displays its corresponding level.

## Local verification

- `validation/settings-display/run.ps1`: 22 assertions passed, including all 256 legacy amplitude display values, all 31 duration display levels, fractional units and negative angles.
- `validation/config/run.ps1` with the existing Gradle-transformed mockable Android jar: complete production Java compilation succeeded; 184 configuration/geometry/preview assertions passed.
- Runtime classpath: test stubs, JSON jar, production classes, then the transformed Android jar. The stock SDK stub jar is unsuitable for the harness runtime.

Pending root-agent device checks: final spacing and large font layout, live slider updates and cancellation, eight independent angle boundaries, system/custom haptic switching, and real feedback timing. These are not established by a JVM compilation or the display arithmetic tests.
