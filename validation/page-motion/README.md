# Page transition completion

Run `./validation/page-motion/run.ps1` from `standalone`.

29 assertions pass against all fields and method bodies of the production `ColorPageMotion`. The runner only removes Android imports and supplies deterministic view, posted-frame, and animator boundaries. It also compiles the untouched production file against Android SDK 35.

Checks cover idle and nonanimated immediate delivery; waiting during queued startup and running animation; delivery only after the outgoing page is removed and the incoming transform is reset; replacing or clearing a pending callback; cancellation before/after startup; `clear()`; subsequent navigation; disabled animations; duplicate/late animation end events; and reentrant navigation from a completion callback.

Retained-page checks preserve the exact embedded chooser tree while removing the temporary input blocker, restore the original focus policy and accessibility, and verify repeated interrupted navigation does not accumulate blockers. Predictive-back cancellation is exercised at the controller boundary, without simulating Android's gesture dispatcher.

`whenSettled` has one pending callback. `cancel()`, `clear()`, and a new `show()` discard it. It waits for the actual animator completion event rather than a fixed wall-clock delay, so it does not independently apply or assume the system animation scale. The host tests validate this event contract, not Android frame timing or WindowManager behavior on a device.
