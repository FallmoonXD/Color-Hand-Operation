$ErrorActionPreference = 'Stop'
$projectRoot = (Resolve-Path (Join-Path $PSScriptRoot '../..')).Path
$checks = Join-Path $projectRoot '.checks/settings-display'
New-Item -ItemType Directory -Force -Path $checks | Out-Null
& javac -encoding UTF-8 --release 8 -d $checks (Join-Path $projectRoot 'app/src/main/java/dev/edgeflow/SettingsDisplay.java') (Join-Path $PSScriptRoot 'SettingsDisplayCheck.java')
if ($LASTEXITCODE -ne 0) { throw 'Settings presentation compilation failed' }
& java -cp $checks dev.edgeflow.SettingsDisplayCheck | Tee-Object -FilePath (Join-Path $checks 'results.txt')
if ($LASTEXITCODE -ne 0) { throw 'Settings presentation validation failed' }
