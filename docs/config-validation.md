# Configuration and material lifecycle validation

Date: 2026-11-05. Scope: the independent `standalone` source tree. No ADB commands or device changes were performed for this validation.

## Executed checks

A local Java harness executed the actual compiled `ConfigArchive`, `AppPrefs`, `LauncherLayout`, `KeyboardShortcutsOverlay` validators, `ToolsConfig`, and `WidgetMigration` implementations. Result: **111 assertions passed**.

Android service/UI boundaries are test doubles: in-memory `SharedPreferences`, a file-backed `AtomicFile` adapter, widget allocation/binding registry, component identities, key-code lookup, and inert UI scheduler. The preference double deliberately changes memory before returning a failed commit, matching the failure condition that requires explicit restoration. Actual `ToolsConfig` JSON parsing and file-name selection remain production code.

Covered cases:

- OHO menu geometry reads do not migrate or alter saved values. Editing one primary handle materializes legacy center/height overrides without moving its peer; aligned edits update both effective per-handle values. Size and position stay on screen, extra pairs remain isolated, and all action assignments survive.
- Pair counts, alignment flags, per-handle long-slide switches and signed ARGB colors retain their storage types through backup. Color alpha combines with the existing visibility value; unset colors/visibility retain the old appearance. Invalid pair counts, switch types and color values are rejected.
- Launcher columns accept the OHO 2/3/4 values while retaining old 5-column configurations; task counts accept 8 through 13 and retain old 5/20/30 values. No automatic preference coercion occurs.

- Folder names cannot be newly saved above 256 characters. Existing overlength names are preserved; exporting one is explicitly rejected with a rename instruction, and a corrected 256-character name roundtrips.
- Structured shortcut responses accept legitimate labels containing `Exception:` or `permission denied`; nonzero exit, missing protocol markers and invalid field types still fail.

- Six independent handle identities, including disabled-by-default extra pairs, preserve primary mappings and their own geometry/actions through v2 import/export.
- Touchpad speed (0.5–3.0), navigation button size (40–76 dp), and one-to-five unique navigation actions retain float/string types and order. Invalid sizes, duplicate/empty/self-opening navigation lists and malformed storage types are rejected.
- System shortcut identities containing Unicode publisher IDs and their display-label cache roundtrip through both gesture assignments and launcher folders/items. Malformed identities and non-string labels are rejected; icon caches are excluded.
- Pure widget edit transactions cover all four resize edges, combined corner resize, boundaries, zero-sized proposals, occupied cells, last-valid-position retention and cancellation rollback.

- Version 1 imports numeric/boolean/set preferences and tools while preserving newer launcher/shortcut settings.
- Version 2 exports, serializes, imports, and re-exports the same state, including folders, app entries, action entries, task count, and keyboard shortcuts.
- Version 2 removes target-only gesture overrides; version 1 retains its partial-update behavior.
- The OHO settings (`hide_home`, `hide_notifications`, `quick_action_short`, `quick_action_long`, `vibration_strength`) retain their types through an archive roundtrip. New defaults are false/false/false/true/0, and `hide_keyboard` is false for new installations. The UI respects legacy `hide_when_keyboard` when the new key is absent.
- `long_immediate` remains an independent zero-wait flag; it is not converted to the long quick-action setting. Vibration endpoints 0/255 are accepted as integers; negative, fractional, and out-of-range values plus incorrectly typed quick-action flags are rejected.
- Failures in each of the three preference namespaces and in the tools file write restore the previous preference values and tools layout.
- Invalid preference types, launcher ranges, missing folder references, duplicate shortcut keys, duplicate tiles, inconsistent gesture thresholds, unknown top-level sections, and files over 1 MiB are rejected without changing persisted settings.
- Integral JSON floating-point values are normalized to integer preference storage.
- Tools use `files/tools-layout-v1.json`; widgets read `replica_widgets` / `layout_v3`, `pages`, `page`, and `height_v4`.
- Widget export includes provider component and `sourceId`, never an importable live `id`. A missing provider is marked `unavailable`; ordinary config import leaves installed widget IDs untouched.
- Widget migration allocates new host IDs, reports provider configuration requirements, is idempotent for an identical request, rejects a nonempty destination, reports overlapping cells, and deletes allocations after denied binding.
- A widget preference commit failure restores all old values, releases new IDs, and reports zero retained/importable widgets and zero configuration actions.

The corresponding fixture is `validation/config/src/dev/edgeflow/ConfigArchiveHarness.java`. Runtime-only JSON dependency: `org.json:json:20240303`, Maven Central, SHA-256 `3cf6cd6892e32e2b4c1c39e0f52f5248a2f5b37646fdfbb79a66b46b618414ed`. This dependency and every stub stay outside the APK source set.

Reproduce from PowerShell using the Android Gradle plugin's mockable `android.jar` (a plain SDK stub jar throws in `ContextWrapper`):

```powershell
./gradlew.bat :app:processDebugResources
# Locate the mockable android.jar generated in your own Gradle cache.
./validation/config/run.ps1 -AndroidJar '<path-to-your-mockable-android.jar>'
```

The resource step generates the R constants used by the original-arrow vector. The script recompiles current production sources, stops on compilation failure, verifies the test dependency hash, and writes `.checks/config-harness/results.txt`.

## Corrections made during validation

1. A v2 archive previously merged main preferences, retaining target-only side-specific gesture overrides. V2 now replaces that namespace after validation; v1 still merges for backward compatibility.
2. Failed widget migration previously removed only layout/report keys while leaving modified height/page/loop values, and its report retained IDs already scheduled for deletion. Failure now restores the entire old preference map and marks the attempted allocations as failed.
3. `NativeMaterial.attach` previously replaced `window` before releasing its old capture, potentially restoring fallback blur on the wrong window. It now releases the old owner first and removes stale owner-map entries.
4. A late legacy blur-buffer callback could dereference a detached view's null display before closing its `HardwareBuffer`. Rotation lookup now handles a detached display and still enters the buffer-closing path.
5. `BlurServiceBridge` previously entered its cleanup `finally` after several reflection operations following provider acquisition. Those operations now run inside the cleanup scope, so an initialization failure also releases the external provider reference. Provider authority is validated and its terminal suffix determines the app package.
6. The volume overlay now treats external view detachment/dialog dismissal as removal, cancelling timers, repeat presses, animations, and retained static overlay state.

## Lifecycle review and limits

Source review traced `ColorOsSurface` → `NativeMaterial` attachment, pre-draw listener removal, capture REMOVE requests, pending buffer closure, entrance callback cancellation, and removal from `NativeBlur.clients`. Displayed hardware bitmaps are intentionally not forcibly recycled while RenderThread may still reference them. The root helper's existing idle timeout is 15 seconds; afterward its `finally` unbinds the service and releases the provider reference. The app also retains and cleans the binding handle on helper completion. `BlurBridgeProvider.call` restricts access to root or the app's own UID.

The JVM harness does **not** prove Android Binder behavior, real widget bind permission/configuration, RenderThread/GPU buffer lifetime, visual fidelity, compositor timing, rotation, process-death recovery, or on-device file picker behavior. Material lifecycle changes and the widget selection/resize UI received source review and Java compilation only in this task. The widget edit geometry was exercised independently in the JVM; Android pointer dispatch and visual rendering were not. Cross-file import restoration covers reported write failures; it is not a power-loss-atomic transaction or a guarantee when both the initial write and rollback storage fail.
