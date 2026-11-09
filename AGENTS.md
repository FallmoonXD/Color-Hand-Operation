# Working on Color Hand Operation

- Read `docs/PROJECT_STATE.md` and the affected source before changing behavior. Preserve `dev.edgeflow` data compatibility.
- Public version names use plain `X.Y.Z`; release titles use `Color Hand Operation X.Y.Z`. Keep design/test descriptions in release notes, not version or APK filename suffixes.
- Use source, ADB services and accessibility trees first for logic. For appearance, open the actual rendered images; do not infer visual quality solely from code.
- When matching ColorOS, identify the requested control in the installed layout and trace its drawing and input paths. Compare the same interaction on the phone, including movement and release for touch effects.
- Preserve the accepted ColorOS panel material unless the task explicitly changes it. Keep native service capture/shader parameters separate from capability/failure handling.
- Icon hand and arm must remain one continuous shape. No invented anatomical detail, palm shading or sleeve divisions. See the design constraints in `docs/PROJECT_STATE.md`.
- Build with JDK 21 and the checked-in Gradle wrapper. Run checks relevant to the change; keep offline simulation and actual device evidence distinct.
- Never publish device serials, private configuration, screenshots containing personal data, signing keys, local.properties or build caches. Existing ignores are intentional.
- Do not assume another ColorOS firmware shares proprietary service/transaction interfaces. Fail closed on unknown write capabilities and document unverified models.
- Before device tests, read the current charging keep-awake, brightness and timeout settings. Keep the unlocked screen available during interactive work. Preserve the owner's settings and restore any temporary overrides. Once screen work is finished, choose sleep when appropriate to avoid leaving a static image on the display. Follow newer user instructions about screen wake/sleep.
