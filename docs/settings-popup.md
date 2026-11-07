# Settings anchored menus

`replica.ColorPopupMenu` anchors a `PopupWindow` to the invoking control. The home overflow and navigation settings use activity windows. Widgets, the launcher and keyboard shortcuts use their accessibility-service window token.

## Opening and closing

ColorOS composites the blur region separately from the menu text. The Settings implementation connects `RoundFrameLayout.setAlpha()` to `COUIBackgroundBlurBuilder.onTargetViewAlphaChanged()`. In CHO the card root stays at full View alpha while its content and background drawable animate with the same reveal value. Apply that value before installing a new native or solid drawable, including when blur availability changes during the transition. The root still scales around the invoking control. Centered confirmation dialogs use the same approach. Bottom sheets start outside the visible area and reveal the blur region as they slide in.

On PKR110, frame-by-frame recordings of the home menu showed the background appearing nearly fully formed before the labels, fading backwards, then changing abruptly at the end. The device check samples the controls visible through the trailing edge of the menu, and checks both the first visible frame and the end of the fade. Run it on an unlocked phone at normal animation speed with `python tools/verify_popup_reveal.py`. [Device check](../tools/verify_popup_reveal.py)

## Source evidence

Local ColorOS 17 Settings resources:

- `analysis/coloros17-settings/resources/res/layout/coui_popup_list_window_item.xml`: title uses body-L, regular 400; icon/title horizontal spacing is separate from the popup container.
- `res/values/dimens.xml` entries `coui_popup_list_window_min_width` / `max_width`: 168 / 256 dp; title text 16 sp; default vertical gap to anchor 8 dp; horizontal safe margin 16 dp; vertical mask radius 20 dp, with a separate 28 dp 16.1 variant. The standalone component uses the 20 dp circular outline, not an OEM smooth-corner implementation.
- `res/drawable-v36/coui_popup_window_background.xml`: `?couiColorBackgroundTop`, whose default colors are white / #333333. `ColorOsTheme.popup()` reads that attribute from the current Settings theme. `ColorOsTheme.error()` likewise reads `couiColorError` (default #DB382C / #FF6C61), including current palette overlay / theme rejection logic.
- `res/anim/coui_popup_list_window_exit.xml` and `coui_popup_list_window_exit_interpolator.xml`: 200 ms alpha exit with cubic (0.3, 0, 0.8, 0.15).
- Extracted current Settings `single/PopupMenuLocateHelper.java`: anchor and window barriers, cutout boundaries, separate above/below positioning.
- Extracted `single/DefaultScreenAnimationController.java` and `COUIPopupTouchMotionAnimator.java`: location-dependent popup pivot, scale/alpha motion and native animation-kit orchestration. No classes from those APKs are loaded or bundled.

The 48 dp accessible action minimum (56 dp optional), 20 dp outline, 250 ms enter cubic (0.2, 0, 0.1, 1), 0.94 initial scale, 0.96 exit scale and 12 dp elevation are explicit standalone design choices. They preserve the anchored scale/fade behavior without claiming an exact port of ColorOS's animation kit. The 200 ms exit fade curve is taken from the native resource. Android's global animator scale applies normally; disabled animation is immediate.

## API and lifecycle

```java
ColorPopupMenu menu = new ColorPopupMenu(actualButton)
    .setItems(new String[]{"新增触钮", "移除触钮"}, index -> handleChoice(index))
    .setDividerBefore(1)
    .setOnDismiss(() -> currentMenu = null)
    .show();
```

Optional `setTitle`, `setMessage`, `setDestructive(int...)`, `setRowHeightDp(56)`, and `setAlignEnd(false)` cover a brief removal confirmation near its trigger. Strings and messages may wrap; long content scrolls within the usable window. End alignment respects RTL. Positioning intersects the visible window, current system bars/cutout/IME bounds, and an edge margin. If neither side has space for one row, a bounded overlapping menu remains usable.

Outside touch, Back/Escape and accessibility dismissal close the menu. An action runs only after exit animation and window removal. Activity stop/destroy, anchor detachment and explicit `dismissImmediately()` cancel queued actions. Registered lifecycle, geometry, theme and animation callbacks are released on every close path. Open menus recolor and remeasure when the system theme/font changes.

## Verification

`validation/settings-popup/run.ps1` checks toolbar/bottom anchors, RTL edge placement, split-window origins, keyboard/large-content constraints, tiny-window fallback and a broad bounds grid. These are geometry assertions, not device rendering tests. Root integration/device verification is recorded separately.
