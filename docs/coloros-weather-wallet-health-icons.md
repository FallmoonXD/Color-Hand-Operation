# Weather, Wallet and OPPO Health icon asset audit

2026-11-06. Source: launcher resources from the installed OnePlus PKR110 packages, located with `pm path`, pulled over ADB and decoded locally.

## Actual manifest resources

| App | Installed version | Resolved launcher activity | Actual icon resource | Largest referenced bitmap |
| --- | --- | --- | --- | --- |
| Weather (`com.coloros.weather2`) | 17.9.4 / 17009004 | `com.oplus.weather.main.view.WeatherMainActivity` | `@drawable/ic_launcher_weather` | `drawable-xxxhdpi/ic_launcher_weather.webp`, 240×240 |
| Wallet (`com.finshell.wallet`) | 5.51.5 / 551053 | `com.nearme.wallet.SplashActivity` | `@drawable/icon_wallet_launcher`; round icon `@drawable/round_icon_launcher` | `drawable-xxhdpi/icon_wallet_launcher.webp`, 150×150 |
| OPPO Health (`com.heytap.health`) | 6.9.42_4b2ba32_261012 / 6094200 | `com.heytap.health.oobe.LaunchActivity` | `@mipmap/lib_base_ic_launcher` | `mipmap-xxhdpi/lib_base_ic_launcher.webp`, 156×156 |

All three resolved launcher activities use the application icon; none overrides it with an adaptive XML. Wallet's alternative `CNYIconActivity` has a separate seasonal icon but is disabled in the manifest and was not the resolved launcher. `cmd package resolve-activity` confirmed the activities listed above without starting them.

**Important false reference:** Wallet also bundles `mipmap-anydpi-v26/ic_launcher.xml` and `drawable-v24/ic_launcher_foreground.xml`. Those are an unused Android Studio green-grid/robot template, not the Wallet icon referenced by its application or resolved launcher. They must not be used to infer Wallet's material layers.

These APK resources are flattened bitmaps, not separable adaptive foreground/background layers or runnable material shaders. Their apparent layering, reflection, shadow and translucency are baked into the pixels. The audit confirms the installed app resources; it does not separately prove that the launcher is applying no icon-pack/theme override.

## Raw assets and verification

The complete pulled APKs and decoded manifests/resources are in `analysis/coloros17-icon-audit/apps-2026-11-06/`. `asset-audit.json` records package versions, manifest attributes, original ZIP entries, SHA-256 hashes, dimensions, alpha counts and unique color counts. Each extracted reference was checked byte-for-byte by SHA-256 against a matching image entry in its APK.

Convenient reference copies (the `.webp` files preserve the original bytes; PNGs are lossless decoded pixel copies):

- `originals/weather-launcher-original.webp` and `weather-launcher-decoded.png`
- `originals/wallet-launcher-original.webp` and `wallet-launcher-decoded.png`
- `originals/health-launcher-original.webp` and `health-launcher-decoded.png`

## Visual findings

All three original bitmaps were opened for inspection.

- **Weather:** the cloud reads as a rounded volume through changing surface normals implied by light/dark shading, a thin bright silhouette and a soft underside. The sun is occluded by the cloud; yellow bleeds into the upper cloud region while the lower body stays cooler. Depth comes from curvature, occlusion and localized color influence, rather than one global alpha gradient.
- **Wallet:** several saturated cards occupy distinct planes. Their rounded top edges catch narrow light; darker contacts separate them. A pale blue front pocket masks the lower cards and has a curved cutout. Bright rim and a darker blue band around the cutout make its thickness readable. White surroundings keep these effects visible.
- **OPPO Health:** blue and coral rounded heart halves overlap with a clear front/back relationship and a soft displaced shadow. It is more restrained than Weather/Wallet; it supports simple bold geometry and warm/cool color separation, not a claim that every component is optically transparent.

The originating authoring software or physical rendering pipeline cannot be inferred from these images alone. The evidence supports using a pre-rendered volumetric foreground if the goal is to approach these particular assets. The previous few flat vector paths with linear gradients did not represent changing thickness, curved normals or localized occlusion, so increasing their transparency could not supply the missing volume.

These references led to an early handless icon experiment: an icy-blue frame and coral inward curve on a light background. That design is historical; the current icon is documented in [user-layered-icon-validation.md](user-layered-icon-validation.md).
