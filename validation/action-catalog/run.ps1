param([string]$AndroidJar)
$ErrorActionPreference='Stop'
$project=(Resolve-Path (Join-Path $PSScriptRoot '../..')).Path
if (-not $AndroidJar) { $AndroidJar=Join-Path $project '.toolchains/android-sdk/platforms/android-35/android.jar' }
$output=Join-Path $project '.checks/action-catalog'
New-Item -ItemType Directory -Force -Path $output | Out-Null
$sources=@(
 (Join-Path $project 'app/src/main/java/dev/edgeflow/ActionCatalog.java'),
 (Join-Path $project 'app/src/main/java/dev/edgeflow/ActionTarget.java'),
 (Join-Path $project 'app/src/main/java/dev/edgeflow/GestureMapping.java'),
 (Join-Path $project 'app/src/main/java/dev/edgeflow/GestureHandleConfig.java'),
 (Join-Path $project 'app/src/main/java/dev/edgeflow/ShortcutEntry.java'),
 (Join-Path $project 'validation/config/stubs/android/content/ComponentName.java'),
 (Join-Path $PSScriptRoot 'ActionCatalogCheck.java')
)
& javac -encoding UTF-8 --release 17 -cp $AndroidJar -d $output @sources
if ($LASTEXITCODE -ne 0) { throw 'Action catalogue validation compilation failed' }
& java '-Dfile.encoding=UTF-8' '-Dstdout.encoding=UTF-8' '-Dstderr.encoding=UTF-8' -cp "$output;$AndroidJar" dev.edgeflow.ActionCatalogCheck $project | Tee-Object -FilePath (Join-Path $output 'results.txt')
if ($LASTEXITCODE -ne 0) { throw 'Action catalogue validation failed' }
