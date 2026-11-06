# Settings action selection sheet

`replica.ColorActionSheet` presents the gesture action list from the bottom. The caller retains its custom action rows, selected `ColorRadioButton`, optional settings gear and application/shortcut entries. It replaces the action chooser's centered dialog only; other confirmations and text editors remain on their existing components.

## Local Settings references

- `analysis/coloros17-settings/resources/res/layout/coui_list_bottom_sheet_dialog_layout.xml`: fixed COUI toolbar followed by a scrollable choice list, with no framework list divider.
- `single/COUIListBottomSheetDialog.java`: independently extracted current Settings class; wraps the toolbar/list within `COUIBottomSheetDialog`. That specific choice-list variant hides the drag image; our chooser deliberately retains the parent panel's visible drag affordance requested for this interface. No code from this APK is executed or packaged.
- `res/layout/coui_panel_view_layout.xml`: separate drag region, weighted content, optional bottom button area.
- `res/values/dimens.xml`: `coui_panel_bar_width` / `height` = 28 / 4 dp; panel maximum width 540 dp, tiny-screen maximum height 720 dp; bottom/side follow-hand margins 8 dp, top safe padding 40 dp; `coui_round_corner_xxxl` = 28 dp.
- `res/anim/coui_bottom_dialog_enter.xml` and `exit.xml`: 250 ms vertical entry/exit. `android_alert_dialog_enter.xml` uses cubic (0.3, 0, 0.1, 1); exit uses (0.3, 0, 1, 1).

This standalone component uses those dimensions and translation curves with owned content. Since 0.3.11 the gesture chooser mounts inside its retained Activity page; other callers can still use the Android `Dialog` presentation. Neither path uses AOSP AlertController/list chrome. It uses a live Settings `ColorOsTheme.popup()` surface, text/accent/secondary colors and system typefaces. The 28% animated scrim, title/close treatment, header-only drag dismissal, and reuse of the 720 dp small-screen cap on large displays are explicit adaptations. It does not claim the complete OEM nested-sheet behavior or private animation framework.

## Contract

```java
ColorActionSheet sheet = new ColorActionSheet(activity)
    .setTitle("选择操作")
    .setView(customScrollableActionList)
    .setNegativeButton("取消", null)
    .setOnDismissListener(() -> clearReference());
// Row listeners capture sheet and defer navigation or committing a choice until dismissal.
row.setOnClickListener(view -> sheet.dismissThen(() -> selectAction()));
sheet.show();
```

`show()` returns the component; `isShowing()`, `dismiss()`, and `dismissImmediately()` are available. After asynchronous list loading, `refreshContent()` adopts the new text roles and remeasures the sheet without recreating the window. `dismissThen` ignores additional clicks while closing, animates out, removes the window, emits the dismiss listener once, and only then invokes the action if the Activity still exists. A lifecycle cancellation never dispatches a queued selection. A dismissed instance cannot be reopened.

### Retaining a chooser while editing its configuration

The gesture chooser now also supports `showEmbedded(FrameLayout ownerPage)` and `showEmbeddedRestored(ownerPage)`. These attach the real sheet tree inside the retained settings page and create no Dialog. This lets a configuration page slide over that complete owner page, then reveal the same chooser and scroll position during its return animation. The host retains/reuses its scaffold and calls `resume()` before the returning page is drawn, rather than waiting until the transition finishes. Measured embedded content remains visible even while its parent is temporarily detached.

In embedded mode, `suspend()` blocks chooser input/accessibility without hiding its drawing; page navigation determines whether it is on screen. Pause/stop preserve the embedded tree, including when a translucent widget Activity is on top. If an exit or selected action is already pending, pause/stop instead cancel that exit callback and unmount the sheet so it cannot commit in the background. Destroy or explicit dismissal removes the sheet. Mounting compensates the host's inset padding, and temporarily hides the underlying page children from accessibility; dismissal restores their exact previous flags and the host's clipping policy. The Activity handles Back because there is no separate dialog window. While the chooser is topmost, its Back interaction must not also transform the underlying settings page.

The following Dialog behavior remains available for callers such as the color palette:

`suspend()` returns `true` for an already shown sheet which is not already closing. This includes the interval before its root attaches or completes its first layout, which can occur during rapid Activity recreation. It cancels entry/drag motion and queued entry/layout callbacks, clears pending navigation, sets the final reveal pose, and calls `Dialog.hide()`. The same Dialog, view tree and scrolling position stay allocated. `isShowing()` becomes false and `isSuspended()` becomes true. A call before `show()`, during dismissal, or after closure returns false; the host must not navigate assuming it retained that instance.

The host can navigate its Activity to a configuration page after successful suspension. Returning calls `resume()` explicitly: current theme and insets are applied before the retained window becomes visible, with no new build or entry animation. A retained first-frame window is shown using its same Dialog and waits for valid measured geometry before revealing its panel. Neither the Activity's resume callback nor theme changes automatically restore a hidden sheet. Pause and stop preserve only explicitly suspended sheets; destruction always removes the window and releases subscriptions. The host should invoke `resume()` only when its intended owner page is again in the foreground.

This follows the [AOSP Dialog hide/show contract](https://android.googlesource.com/platform/frameworks/base/+/refs/heads/main/core/java/android/app/Dialog.java): hiding keeps the internal window, while showing it again restores decor visibility. `Dialog.isShowing()` becomes false while hidden, so cleanup deliberately removes any existing dialog without using that method as a gate. Closing a hidden sheet is immediate and discards deferred actions. Late back/cancel/input callbacks cannot operate on a suspended sheet.

After Activity recreation, the old Dialog is destroyed. A **new** instance can use `showRestored()` with reconstructed content and host-restored scroll state; it waits for measured geometry and becomes visible at the final pose without an entry animation. This is distinct from `resume()`, which preserves the original Activity's exact objects. Configuration targets, selected row state and delayed return-to-parent orchestration remain the host's responsibility.

The wrapper does not override custom row click handlers. The caller supplies row pressed backgrounds and its existing radio/gear controls. Text roles are retained while theme/font updates occur; custom controls keep their own theme subscriptions. Insets and IME height constrain the sheet. A compact mode hides the decorative drag bar and redundant bottom cancel button in very short windows while retaining the top close control. Body scrolling remains independent of dragging the header. Back, outside tap, close and accessibility dismissal all use the same exit path.

## Verification

`validation/settings-sheet/run.ps1` checks safe-area bounds, phone/tablet limits, cutouts, keyboard insets, short-content bottom alignment, device-density dimensions and tiny-window transients. All Java sources compile against API 35. These are local checks; actual device rendering, gear return navigation, and drag behavior must be verified by the parent device workflow.

Local coverage consists of 363 geometry assertions plus 53 lifecycle/theme/embedding assertions. `run-retention.py` extracts the actual production suspend/resume/show/mount/close/lifecycle methods, content refresh and queued-entry body into a narrow Java Dialog/View model. It verifies view identity and scroll preservation, no repeated entry, queued callback cancellation, visible-versus-hidden pause/stop, destruction, hidden-window removal, dismissal ordering and idempotence, rejected invalid states, failed resume cleanup, `showRestored()`, first-frame pause/resume before attachment, semantic text colors across successive theme changes, embedded padding, modal accessibility restoration and first-frame painting of reused pages. It does **not** simulate Android WindowManager focus, predictive-back dispatch or touch routing. Earlier Dialog-based installed-device validation is recorded separately in [0.3.10 validation](retained-menu-validation.md); the embedded change requires its own device verification.

The parent completed installed-device navigation, cancellation, drag, background lifecycle, and large-font checks in [0.3.5 validation](design-refinement-validation.md). Device testing found and fixed an early `Window.getInsetsController()` call before DecorView creation; initial color setup now defers system-bar access until the decor exists.

The embedded owner-page design, 53 sheet assertions and 29 page-motion assertions are documented in [0.3.11 continuous pages](continuous-pages-validation.md). This is a new presentation path; the earlier 0.3.10 Dialog retention results remain historical evidence for that implementation.
