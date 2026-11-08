# Portable OHO settings integration

Implemented in the independent Settings-style `MainActivity`; existing material constants and primary gesture defaults are unchanged. The new settings use the existing `edgeflow` preference file and therefore participate in `ConfigArchive` v2 validation, export/import and rollback.

| Setting | Key / representation | Default and accepted range |
| --- | --- | --- |
| First additional pair | `extra_left`, `extra_right` handle IDs | Disabled; width 12.3 dp; vertical range 0.70–0.90; all actions `none` |
| Second additional pair | `extra2_left`, `extra2_right` handle IDs | Disabled; width 12.3 dp; vertical range 0.35–0.55; all actions `none` |
| Per-handle controls | `<id>_enabled`, `<id>_handle_width_dp`, `<id>_handle_start_fraction`, `<id>_handle_end_fraction` | Boolean; width 2–30 dp; start 0–0.95; end 0.05–1; minimum height 0.05 |
| Per-handle actions | `action_<id>_<up/straight/down>_<short/long>` | Existing stable action IDs, application component, or a validated system shortcut identity |
| Touchpad speed | `touchpad_speed` float | 1.7; range 0.5–3.0 |
| Navigation orientation | `navigation_vertical` boolean | False |
| Navigation button touch target | `navigation_button_size_dp` float | 58 dp by default, range 40–76 dp. The glass disc sits 6 dp inside each edge of the target. |
| Navigation order | `navigation_actions` JSON string | `["back","home","recents"]`; 1–5 unique supported actions, excluding `none` and `navigation_panel` |

虚拟导航键的初始位置参考 OHO+ 9.3.13 的 `r5.f.t` 和 `m6.j.b`：用起手点确定触发侧和整列的垂直中心，上下端按导航栏高度限位。CHO 另外避开当前可见的手势触钮和屏幕缺口；手动拖动时只调整高度。改名后手势和备份仍使用 `navigation_panel`。

The main settings page exposes each additional handle separately. Navigation settings support adding, replacing, moving and removing buttons, plus preview. Touchpad settings expose speed and preview. Enabling an extra handle does not rewrite either primary handle's action map.

Gesture action selection now includes the actual system shortcut picker. Its portable identity is `launcher_shortcut:1:<userId>:<Base64URL(package)>:<Base64URL(shortcutId)>`. Launcher entries and gesture assignments share that identity; the `edgeflow_launcher/shortcut_labels` JSON string stores a bounded display-name cache. Icon cache files are rebuildable and are not included in the archive. A shortcut unavailable on another installation is not represented as an ordinary application launch.

## Widget editing

The existing Samsung evidence `analysis/oho-9.3.13/reference/interaction/widget-longpress-small.png` shows one selected widget with four edge resize handles. The widget editor now displays selection only on the chosen widget and supports resizing from each edge, combined corner resizing and dragging. Normal display size, provider padding and material parameters are unchanged.

`replica.WidgetGrid` contains independent 4×4 edit geometry. Invalid or occupied proposals retain the previous valid position; cancellation restores the original position and size. The existing persisted layout is saved on pointer-up. This does not add provider minimum-size policy or change existing Alipay widget dimensions.

## Validation boundary

Current production Java compilation succeeded. The local configuration harness now passes **78 assertions**, including extra-handle/shortcut/navigation roundtrips, malformed input rejection, settings failure rollback and pure widget drag/resize/cancellation geometry. See `config-validation.md` and `.checks/config-harness/results.txt`.

These additions were not deployed or tested through Android pointer input by the settings/UI integration task. Actual system shortcut availability, navigation execution, touchpad feel, extra-handle overlap behavior and widget resize rendering require the separate device validation pass; this document makes no on-device success claim.

Root-agent device pass subsequently verified the extra2-right Home assignment with original twelve slots unchanged, custom fourth notification navigation button, real Settings WLAN shortcut selection/start, and Alipay left/bottom resize with restoration plus cancelled resize rollback. See `device-validation.md`; this does not imply every settings combination was tested.
