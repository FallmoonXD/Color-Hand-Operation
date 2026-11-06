# Original OHO+ icon restoration

2026-11-06. The user requested returning to the OHO+ icon while preparing a later sketch. This change restores the original primary launcher artwork; it does not introduce another design or use image generation.

## Verified original entry point

The user-supplied `analysis/One Hand Operation +_9.3.13.0.apk` declares `com.samsung.android.sidegesturepad.settings.SGPSettingsActivity` as its MAIN/LAUNCHER activity. The application icon is `@mipmap/ic_launcher`; its decoded adaptive XML references `@drawable/oho__bg`, `@drawable/oho__fg` and `@drawable/oho__mono`.

The original viewport is 432×432. The teal background is `#22BDC5`; the foreground contains the white phone, blue screen, pale hand, three gesture dots and blue cuff. Its clip is the central `(72,72)-(360,360)` area. All original paths, colors, clipping and alpha values are preserved.

The original manifest's `roundIcon` references an older pair of 108px PNGs. This app deliberately uses the primary original vectors for both adaptive entries, maintaining the same artwork at arbitrary sizes and supplying the original monochrome resource in either launcher mask.

## Reproducible source

Unchanged decoded originals are stored in `design/oho-original-9.3.13.0/`. The specification records their provenance and SHA-256. The generator copies the three original vectors byte-for-byte into `app_icon_background.xml`, `app_icon_foreground.xml` and `app_icon_monochrome.xml`; hash checks reject changed originals.

| Original | SHA-256 |
| --- | --- |
| `oho__bg.xml` | `6ad39b83d76124a27ecd2ef9926a47ec226f2831913ea3a32211c2e46d650dd0` |
| `oho__fg.xml` | `2985aaeb463e313833fb87f84b3a352947fba48a982b6f67acebe1aab5b1c1df` |
| `oho__mono.xml` | `9359918d190131197d0f940ecdec6d4b6bc1631acff0a00d401ed36ef1323369` |

The active generator only accepts `mode=oho-original-9.3.13.0`; rerunning it cannot restore the rejected generated illustration. The old generated PNG remains in `design/` and release archives are untouched. Its unreferenced drawable was removed from active app resources.

The original monochrome intentionally has seven paths with alpha values `0.3, 0.8, 0.3, 0.5, 1, 1, 0.4`. These original values are retained, superseding the previous handless icon's single opaque silhouette. The notification uses the exact same original paths, colors, alpha and clip with only a 24dp viewport size and centering/scale transform. It is a notification adaptation, not a claim that a separate original notification asset was recovered.

## Checks

- Seven generated native XML resources passed isolated AAPT2 compilation and API 35 resource linking.
- All 13 generated resource/preview outputs were byte-identical after rerunning the generator.
- The three active vector XMLs exactly match the decoded APK originals, and notification path attributes exactly match the original monochrome.
- All native XML and generated SVG files parse. The active resources no longer reference the generated PNG.
- The rounded/circular, themed and actual 72/44px [comparison preview](../design/icon-mask-preview.png) was opened for inspection. [Notification preview](../design/notification-icon-preview.png) uses the same adapted geometry.

These are resource checks. APK builds and installed-resource rendering were tested separately. The artwork remains Samsung's supplied OHO+ resource, not newly authored art or an official Samsung version of this app.
