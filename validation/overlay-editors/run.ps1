param([string]$AndroidJar)
$ErrorActionPreference='Stop'
$project=(Resolve-Path (Join-Path $PSScriptRoot '../..')).Path
if (-not $AndroidJar) { $AndroidJar=Join-Path $project '.toolchains/android-sdk/platforms/android-35/android.jar' }
$production=Join-Path $project '.checks/config-harness/production-classes'
$json=Join-Path $project '.checks/config-harness/json-20240303.jar'
if (-not (Test-Path (Join-Path $production 'dev/edgeflow/ActionExecutor.class'))) { throw 'Run validation/config/run.ps1 first to compile current production dependencies' }
$resources=Join-Path $project 'app/build/intermediates/compile_and_runtime_r_class_jar/debug/processDebugResources/R.jar'
$output=Join-Path $project '.checks/overlay-editors'
New-Item -ItemType Directory -Force -Path $output | Out-Null
$sources=@(
 (Join-Path $project 'app/src/main/java/dev/edgeflow/QuickLauncherOverlay.java'),
 (Join-Path $project 'app/src/main/java/dev/edgeflow/KeyboardShortcutsOverlay.java')
)
& javac -encoding UTF-8 --release 17 -cp "$production;$json;$resources;$AndroidJar" -d $output @sources
if ($LASTEXITCODE -ne 0) { throw 'Overlay editor production compilation failed' }
$tests=@(
 (Join-Path $project 'validation/config/stubs/android/os/Handler.java'),
 (Join-Path $project 'validation/config/stubs/android/os/Looper.java'),
 (Join-Path $project 'validation/config/stubs/android/view/KeyEvent.java'),
 (Join-Path $PSScriptRoot 'OverlayEditorCheck.java')
)
& javac -encoding UTF-8 --release 17 -cp "$output;$production;$json;$resources;$AndroidJar" -d $output @tests
if ($LASTEXITCODE -ne 0) { throw 'Overlay editor production/test compilation failed' }
& java '-Dfile.encoding=UTF-8' '-Dstdout.encoding=UTF-8' -cp "$output;$production;$json;$resources;$AndroidJar" dev.edgeflow.OverlayEditorCheck | Tee-Object -FilePath (Join-Path $output 'results.txt')
if ($LASTEXITCODE -ne 0) { throw 'Overlay editor validation failed' }
