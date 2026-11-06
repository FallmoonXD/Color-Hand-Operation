# Working on Color Hand Operation

- Read `docs/PROJECT_STATE.md` and the affected source before changing behavior. Preserve `dev.edgeflow` data compatibility.
- Public version names use plain `X.Y.Z`; release titles use `Color Hand Operation X.Y.Z`. Keep design/test descriptions in release notes, not version or APK filename suffixes.
- Use source, ADB services and accessibility trees first for logic. For appearance, open the actual rendered images; do not infer visual quality solely from code.
- Preserve the accepted ColorOS panel material unless the task explicitly changes it. Keep native service capture/shader parameters separate from capability/failure handling.
- Icon hand and arm must remain one continuous shape. No invented anatomical detail, palm shading or sleeve divisions. See the design constraints in `docs/PROJECT_STATE.md`.
- Build with JDK 21 and the checked-in Gradle wrapper. Run checks relevant to the change; keep offline simulation and actual device evidence distinct.
- Never publish device serials, private configuration, screenshots containing personal data, signing keys, local.properties or build caches. Existing ignores are intentional.
- Do not assume another ColorOS firmware shares proprietary service/transaction interfaces. Fail closed on unknown write capabilities and document unverified models.
- Before device mutations, retain only the values needed to restore the test. Respect the user's current instructions about screen wake/sleep; end unattended verification with the phone asleep.
