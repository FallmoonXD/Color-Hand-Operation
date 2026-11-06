$ErrorActionPreference = 'Stop'
$projectRoot = (Resolve-Path (Join-Path $PSScriptRoot '../..')).Path
$checks = Join-Path $projectRoot '.checks/shortcut-catalog'
New-Item -ItemType Directory -Force -Path $checks | Out-Null
$compiler = (Get-Command javac -CommandType Application -ErrorAction Stop).Source
$runtime = Join-Path (Split-Path $compiler -Parent) 'java.exe'
$android = Join-Path $projectRoot '.toolchains/android-sdk/platforms/android-35/android.jar'
$json = Join-Path $projectRoot '.checks/config-harness/json-20240303.jar'
if (-not (Test-Path -LiteralPath $json)) { throw 'Run the existing configuration harness once to prepare its JSON test dependency' }
if ((Get-FileHash -LiteralPath $json -Algorithm SHA256).Hash -ne '3CF6CD6892E32E2B4C1C39E0F52F5248A2F5B37646FDFBB79A66B46B618414ED') { throw 'Unexpected JSON test dependency hash' }
$sources = @('ShortcutCatalog.java','ShortcutEntry.java','ShortcutBridge.java') | ForEach-Object { Join-Path $projectRoot "app/src/main/java/dev/edgeflow/$_" }
& $compiler -encoding UTF-8 --release 17 -cp "$android;$json" -d $checks @sources (Join-Path $PSScriptRoot 'ShortcutCatalogCheck.java')
if ($LASTEXITCODE -ne 0) { throw 'Shortcut catalog compilation failed; no stale classes will be executed' }
& $runtime -cp "$checks;$json;$android" dev.edgeflow.ShortcutCatalogCheck | Tee-Object -FilePath (Join-Path $checks 'results.txt')
if ($LASTEXITCODE -ne 0) { throw 'Shortcut catalog validation failed' }
