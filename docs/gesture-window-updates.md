# Incremental handle preference updates

Implemented 2026-11-05 in `GestureService` and `EdgeHandleView`.

The previous settings path called `GestureService.reload()` immediately and also queued the same full rebuild through `SharedPreferences` with an 80 ms delay. Each rebuild removed/recreated both wave windows and all enabled input windows, queried accessibility roots and cancelled active recognition. Toggling a single switch could therefore cause two rounds of WindowManager work during its spring animation.

The current path merges explicit reload and preference notification into one 16 ms main-thread update. Connection/configuration changes still rebuild display-dependent windows, but ordinary preference changes keep the existing window identities:

- Six narrow input windows are created once in primary, extra, extra2 order. Disabled handles are GONE before they are added, preserving extra2 > extra > primary overlap priority when enabled later.
- Master/per-handle enable changes update visibility only. Hiding cancels active input, and DOWN/dispatch independently check the per-handle preference so a pending action cannot escape during the deferred visibility update.
- Geometry is compared against existing WindowManager bounds. Only changed bounds call `updateViewLayout`; the window and both wave surfaces remain the same objects. An affected active gesture is cancelled before moving its input area.
- Handle color/opacity repaint only when effective ARGB changes. Black remains a valid visible color. Recognition parameters are still read at the next DOWN; action mappings are resolved on dispatch.
- Ordinary enable/color/geometry/recognition changes use cached foreground/window state. Changes to exclusion/home/keyboard/notification visibility policy request one merged state refresh. Screen, keyguard, permission, accessibility-window events and disconnect cleanup retain their existing checks.
- `reload()` can still be called off the main thread: scheduling marshals to the service Handler before touching pending flags. Pending work cannot recreate windows after disconnect.

`validation/gesture-window/run.ps1` extracts the actual production service scheduling, bounds, add/update/visibility and removal methods. **33 assertions** passed using a small WindowManager/Handler model: stable add order, initial hidden extras, retained identities, no add/remove/relayout for toggles, coalescing, correct bounds, unchanged-geometry no-op, repaint-only changes, necessary root refreshes, full configuration rebuild and disconnected work suppression. The real recognizer/input suite adds two checks for disable-before-DOWN/dispatch and now passes **280 assertions** (68 state + 51 geometry + 136 duration + 25 production input/timer).

All modified production classes compiled against API 35. These modeled checks verify which operations the production code requests; they do not prove actual SurfaceControl identity, compositor ordering or frame times. WindowManager identity and frame timing require separate on-device checks.
