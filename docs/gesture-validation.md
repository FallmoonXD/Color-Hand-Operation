# Gesture state validation

Date: 2026-11-05. Scope: the independent `standalone` source tree. Result: **68 gesture-state assertions and 51 handle-configuration assertions passed** against the actual production `GestureRecognizer.java` and `GestureHandleConfig.java`.

Run from the `standalone` directory in PowerShell:

```powershell
./validation/gesture/run.ps1
```

The script requires a JDK 9 or newer on PATH. It recompiles the current production recognizer and handle configuration with the versioned `validation/gesture/GestureRecognizerCheck.java` and `GestureHandleConfigCheck.java` harnesses, targets Java 8 bytecode, and stops on either compilation or assertion failure. No Android SDK, Gradle, network dependency, mock recognizer, or device connection is required. Class files and the captured result go to ignored `.checks/gesture/`; test sources and this report remain outside ignored `verification/`.

Recorded execution used Zulu OpenJDK 21.0.10 on Windows and printed:

```text
PASS 68 gesture state assertions
PASS 51 handle configuration assertions
PASS 136 long-duration boundary assertions
PASS 25 production edge input/timer assertions
```

## Covered behavior

- Threshold boundaries, inward versus outward motion, and rejection of nearly vertical edge scrolling.
- The three directions, both sides, and short/long distance classification: all 12 mirrored gesture combinations.
- Radial diagonal distance and the recorded right-side long-down trace `(1259,1300)` to `(859,1900)` over 1100 ms.
- The 300 ms held-action timer measured from DOWN, not from the first threshold crossing; direction changes do not restart it.
- Long-distance release before the duration expires falls back to the corresponding short action; retraction below the long threshold also retains short release behavior.
- Exactly one action per gesture, cancellation, no pending timer after dispatch or release, independent anchor copies, and resetting state on a subsequent DOWN.
- The four short/long quick-action combinations. Quick short fires at its threshold and prevents a second long action. Disabling quick long suppresses held dispatch; long release still requires both distance and duration.
- Legacy `long_immediate` represents an effective duration of zero. It does not override quick-long being disabled: that mode still waits for UP.
- A first sample already beyond the long threshold still respects quick-short priority.
- A disabled per-handle long swipe retains short actions beyond the old long threshold in all six mirrored directions. It suppresses held-long scheduling even in zero-delay mode, preserves quick-short dispatch, and respects cancellation and one-action-per-gesture behavior.

The harness uses the accepted reference thresholds of 138 px and 306 px, a 300 ms delay, and a 30-degree direction threshold.

## Duration bypass correction (2026-11-05)

The previous `GestureRecognizer.finish()` returned a long result whenever `longDistanceReached` was true. Consequently an UP at 100 ms could execute a long action despite a 300 ms setting, even though the held timer correctly waited. The earlier harness also asserted that erroneous behavior. The corrected `finish(eventTime)` uses the same distance **and** duration requirement as the held decision. An early UP executes the corresponding short action if its distance/direction remains valid; it never executes the long action. The original recorded 1100 ms gesture remains a long action.

Exact current rules:

- Duration starts at the original DOWN. Moving between directions or retracting/reentering the long region does not restart that clock. While retracted, long readiness and the pending timer are cleared; reentry recomputes eligibility from the original DOWN.
- A long action requires the current valid inward/radial distance and elapsed event duration at least `long_delay_ms`. Equality is inclusive. `quick_action_long=true` dispatches when these conditions hold while touching; false waits for UP but uses the same eligibility conditions.
- `quick_action_short=true` retains priority and one action per touch. Disabled per-handle long actions, nearly vertical/outward movement, CANCEL, extra pointers, service teardown and a subsequent DOWN still suppress stale long dispatch.
- `EdgeHandleView` uses `MotionEvent.getEventTime()` for MOVE/UP classification. Processing delay on the main thread is not added to the user's gesture duration. The timer is scheduled with `Handler.postAtTime(eventTime + remaining)`, keeping the original uptime deadline instead of delaying it by input queue latency. Its callback checks the current recognizer again and dispatch removes the callback.
- `GestureService` does not classify long actions or own a separate duration timer. Its visibility/configuration/disconnect paths call handle cancellation before replacing or removing input windows; no service modification was needed for this bug.

Original APK evidence: `analysis/oho-9.3.13/jadx/sources/b6/a.java:710` reads duration in 50 ms units and line 777 schedules `max(duration - (eventTime - downTime), 0)`. `w5/a.java:25` runs the held callback through `w5/b.g()`, which executes long actions. The UP branch at `b6/a.java:727` uses `w5/b.h()` (short action) when quick long is enabled; when it is disabled, UP can use `g()` directly. `settings/SGPGestureDetailActivity.java:71` and line 150 accordingly hide duration in that original release-only mode. This means the previous claim that **all** OHO long-distance releases ignore duration was incorrect. The user's latest explicit requirement applies the duration gate in **both** modes here; retaining it with quick long disabled is an intentional difference from that original OHO option.

The additional 136 pure-recognizer assertions cover 1/300/1000 ms, both sides, both quick-long modes, duration minus one/exact/plus one boundaries, stationary release, direction changes, retraction/reentry, cancellation and effective zero duration. The 23 input/timer assertions compile the **actual production** `EdgeHandleView` event/dispatch/cancel methods and long callback with a deterministic Handler/input model. They cover queued MOVE absolute scheduling, queued early UP, exact-boundary UP, timer cancellation, late reentry, replacement DOWN and quick-short priority. All 278 gesture/geometry/duration/input assertions pass. These modeled scheduling tests do not replace Android input-dispatch testing on a device.

## Restored curve-style arrow and per-handle long switch

The captured Samsung preferences (`analysis/oho-9.3.13/reference/prefs.xml`) select `arrow_animation_style=curve_style`, with an opaque black fill and arrow color `-1644826` (`#FFE6E6E6`). In `SGPSwipeAnimationView.onAttachedToWindow`, this style uses `SGPFluidAnimationView` and removes the other arrow-style views. `SGPFluidAnimationView.a()` loads `res/drawable/ic_arrow_forward.xml` and caches rotations 0, -45, +45, 180, 225 and 135 degrees. Its fluid draw path contains no action title or action-icon lookup. The accepted `analysis/visual-test/test15/wave-left.png` also shows the shafted forward arrow inside the black curve.

`GestureWaveView` now bundles that exact 24-by-24 vector path as `oho_gesture_arrow.xml`. It displays the three inward directions on each side, without visual action labels or SystemUI icon substitutions. The action-hint API remains only for the View content description. The labels had been added during the independent engine rewrite as an extra action hint; they were not derived from OHO behavior. Curve geometry, opaque black fill, arrow color/scale, position, expansion threshold, and rebound timing/interpolator are unchanged by this correction. The forward-arrow asset requires the corresponding direction rotations: left up/straight/down = -45/0/45; right up/straight/down = 225/180/135.

The OHO per-handle long switch is backed by `SGPGestureChooserActivity.onCheckedChanged`, which writes only `h.f4896h`; `n6/i.u()` reads it. The DOWN path in `b6/a.java` copies it into `bVar.F` and leaves long directions `I/J/K` disabled when false. The MOVE path then skips the long-action timer; `w5/b.b()` falls back to short direction values and its release path executes the short action. Saved long-action strings remain separate.

The independent engine follows this with `<handleId>_long_enabled` (default `true`) for all six handles. At DOWN, `EdgeHandleView` uses an unreachable long-distance threshold when disabled. This suppresses both long classification and held timers without rewriting any saved long action. Re-enabling the setting restores those existing mappings. The additional 16 recognizer assertions exercise this disabled-threshold mode using the production recognizer.

Validation after both changes: `validation/gesture/run.ps1` passed 68 + 51 assertions, and `gradlew.bat :app:assembleDebug --offline --console=plain` completed successfully, including resource compilation and APK packaging. This check did not include installation or on-device inspection.

## Per-handle visible color

The reference stores each handle's ARGB color separately in `n6/h.java::f4892c`. `SGPHandleView.c()` applies its RGB to the handle drawable and its alpha to the visible handle area. The independent engine now reads `<handleId>_color`, with fallback to `handle_color`, then the prior `#FF188DFF`. No default color values are written into old preferences. The existing global `handle_opacity` remains a separate visibility multiplier, defaulting to zero; final alpha is the selected color's alpha multiplied by that visibility. Consequently the existing default remains invisible, and `#AARRGGBB` can set individual handle transparency without changing other handles.

`AppPrefs.handleColor()` supplies the stored color to settings; `visibleHandleColor()` supplies its effective ARGB to `EdgeHandleView`. These keys are validated and persisted as signed 32-bit integers through configuration import/export. The color is used only by the narrow touch-handle paint, never by `GestureWaveView`; the opaque black curve and original arrow color are unchanged. The configuration harness adds 16 assertions for untouched defaults, fallback, alpha composition, all six independent integer roundtrips, signed ARGB endpoints, and rejection of fractional, out-of-range, string, or boolean values without mutation.

Final verification after the settings integration: the configuration harness completed with **111 passed**, including these 16 color cases; all production Java sources compiled against API 35, and `:app:assembleDebug` succeeded. The run also exposed an inconsistent secondary launcher-column check in `ConfigArchive`; it was corrected before the successful full rerun. This was a host-side test.

## Additional portable OHO controls

The reference `analysis/oho-9.3.13/jadx/sources/n6/i.java::a()` adds paired handles from 2 to 4 to 6 and stops at six. The independent engine now supports the same total: primary left/right, `extra_left`/`extra_right`, and `extra2_left`/`extra2_right`. Both extra pairs default to disabled, with all extra actions set to `none`; the migrated 12 primary mappings are unchanged. Each additional segment has its own enabled flag, width, start/end position, and six short/long actions in the `edgeflow` preference namespace. When segments overlap, the later extra segment has touch priority without rewriting primary coordinates.

The 51 handle assertions cover all 36 independent default mappings, key isolation, unchanged primary bounds on the recorded 1264x2780 display, both extra pairs in portrait and landscape, bounded invalid geometry, and invalid profile rejection. Actual WindowManager touch ordering still requires device validation.

`TouchpadOverlay` reads `touchpad_speed` at each new pad touch, clamps it to 0.5–3.0, and retains the prior 1.7 default. The reference pointer-speed setting appears in `SGPTouchAssistConfigActivity.java` and `a6/c.java`; this implementation exposes a gain multiplier, rather than claiming Samsung's numeric scale is identical.

`NavigationConfig` validates an ordered JSON list of 1–5 distinct supported actions. `NavigationOverlay` uses that order, configurable 40–76 dp button size, and the existing horizontal/vertical choice. Default back/home/recents and the default horizontal dimensions remain unchanged. The five-button limit, persisted ordering, and size setting are based on the reference `m6/j.java` and `settings/softkey/b.java`. Known controls reuse installed ColorOS icons; actions without a corresponding native icon use their action title. Settings and archive validation are handled by the separate configuration harness.

The seven changed/new engine and panel classes compiled against API 35 after these additions. New panel behaviors and enabled extra windows were not device-tested by this validation agent; default-disabled additions do not imply parity in every OHO option.

## Source review and limits

Source review traced `EdgeHandleView` CANCEL through removal of its pending `longCheck`, recognizer cancellation, pointer reset, and quick-launcher cancellation forwarding. `GestureService` cancels active recognition before configuration rebuilds and uses a shared cleanup path on unbind and destruction. Gesture drawing surfaces are two fixed narrow, non-touchable edge windows; their physical coordinates include explicit inset handling and right-edge mirroring.

These assertions exercise recognition state, not Android event dispatch or UI rendering. They do not prove Binder/WindowManager behavior, keyboard or notification focus detection, haptic amplitude, panel touch regions, orientation/inset behavior on real displays, accessibility recovery after process death, or visual fidelity. Those require the separate device regression. No production engine source was changed while publishing this harness.

## Process recovery follow-up

A subsequent device regression reported that killing the app process left the component enabled but recorded as crashed, with no new process after 20 seconds. Ordinary removal of the settings task from Recents had already passed and is a separate case.

The minimal recovery change explicitly starts the existing `GestureService` after its authorized accessibility connection, and its `onStartCommand` returns `START_STICKY` only while the component remains in the system's enabled list. A settings observer and `onUnbind` stop the started lifecycle when access is removed. The change does not add a root watchdog, rewrite the enabled-services list, call a trusted-service enable API, or alter the manifest.

The design follows two distinct platform states. [AOSP AccessibilityServiceConnection](https://android.googlesource.com/platform/frameworks/base/+/refs/heads/main/services/accessibility/java/com/android/server/accessibility/AccessibilityServiceConnection.java) marks disconnected services as crashed and accepts the existing connection again when its Binder returns, while checking that the component is still enabled. [AOSP AccessibilityManagerService](https://android.googlesource.com/platform/frameworks/base/+/refs/heads/main/services/accessibility/java/com/android/server/accessibility/AccessibilityManagerService.java) skips creating fresh bindings for crashed components. Its global `ACCESSIBILITY_ENABLED` setting reflects runtime service state, so the component list, rather than that aggregate flag, is the read-only authorization check.

### Device result and required conditions

The first deployment of the sticky version still failed to recover while ColorOS denied EdgeFlow self-start. The subsequent device test changed only EdgeFlow's system self-start authorization with `setStartupState` and confirmed the readback changed from `false` to `true`.

With that authorization and the same sticky implementation, killing the main process using `kill -9` produced an automatic replacement process: **PID 21973 → 24369**. The narrow edge handles returned, and the quick-tools panel opened successfully afterward. The local raw record is `verification/controls-validation.json`, with `process_rebound.before`, `process_rebound.after`, and `tools_after_process_rebound: true`.

The verified recovery therefore requires both **ColorOS permitting EdgeFlow self-start** and **the EdgeFlow accessibility component remaining enabled by the user**, together with the started sticky service. This result does not establish that `START_STICKY` alone bypasses ColorOS restart restrictions. It verifies recovery from that main-process kill on the tested phone, not a device reboot, user force-stop, removal of accessibility authorization, or all OEM background-kill policies.

This remains a request for normal service process recreation, not a public accessibility rebind API. Force-stop behavior remains under Android's control; the app does not independently relaunch itself afterward. No production code was changed while recording this result.
