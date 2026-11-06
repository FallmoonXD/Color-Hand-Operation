# Overlay submenu icon sources

Source: the connected OnePlus ColorOS 17 Settings APK decoded under `analysis/coloros17-settings/resources/res/drawable`. The original vector paths, dimensions, group transforms and alpha attributes are retained. Only tint/fill/stroke base colors are normalized to opaque white; `SystemIcons` applies the runtime theme tint. Runtime lookup uses `com.android.settings:<name>` and these `cos_*` vectors are the same-geometry fallback.

| Resource | Source SHA-256 | Archived SHA-256 |
|---|---|---|
| ic_apps | `e38306e9490cdd6d4bc3b301e64a803811bfa6c9712adbe610204b21b40d315a` | `55eb6648facd7848019ba7456656c494dfae761ed0f7d44e7ac90053c37af428` |
| ic_add_24dp | `7951e5ec54955b8fddb5491266252f258d25362aea035c649f5e13da472ebfd6` | `2d8984afa269846ac7527f6a1778ace0b603486b20f878adde5f46f2c5ed4116` |
| ic_remove_24dp | `f97f58e0c4796164be8dfc8cb509e36f91327a245181cd86031fa00b6b0fdf2e` | `064b4b68ca3df0966bba5f4a86cbf4838ee8c77d6968651cdb3236fabe1a035a` |
| ic_folder_vd_theme_24 | `524a60f539e73e3ba481a2bb960a3979e17091834e583ffec7ba3d2a028b944a` | `e3a3014fa1d5328406fa71243637cc029b9b499a31fc75ab6af1ce72efcfc90e` |
| ic_keyboard_black_24dp | `07a1cc5091cbb1e6b68f00c48058553fea0cf5c9aefd49b872989aa7722ee4ce` | `72872b445b5b9321872416dd8a62ae352e61494e07ec4ee200507f2cdc0f7897` |
| ic_edit_24dp | `bae99830f3f2c8808e3ba9e1920cfd34761978fc5068ab2739dba1722109b568` | `e5ef5683a9db6e053bec80439ef7b4d6b56712775716d1ac27aa6cb7da230a8e` |

The magnification controls use `com.android.settings:ic_add_24dp` and `com.android.settings:ic_remove_24dp`, with opaque white tint and the existing 40dp-high button targets/material. The 8dp vertical padding leaves a 24dp icon box. The source minus has a 48-unit viewport and a 24dp intrinsic size; its 28-by-4-unit path is retained without resizing the path to imitate the plus. `SystemIcons.raw` already resolves unavailable system resources through `cos_` + the resource name, so both archived vectors use the existing fallback mapping. The separate circle assets are not used: the reference plus circle is outlined while the minus circle is filled.
