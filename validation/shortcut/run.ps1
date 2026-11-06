param()
$ErrorActionPreference='Stop'
$projectRoot=(Resolve-Path (Join-Path $PSScriptRoot '../..')).Path
$output=Join-Path $projectRoot '.checks/shortcut-identity'
New-Item -ItemType Directory -Force -Path $output | Out-Null
& javac -encoding UTF-8 -d $output (Join-Path $projectRoot 'app/src/main/java/dev/edgeflow/ShortcutEntry.java') (Join-Path $PSScriptRoot 'ShortcutIdentityCheck.java')
if ($LASTEXITCODE -ne 0) { throw 'Shortcut identity compilation failed' }
& java -cp $output dev.edgeflow.ShortcutIdentityCheck | Tee-Object -FilePath (Join-Path $output 'results.txt')
if ($LASTEXITCODE -ne 0) { throw 'Shortcut identity checks failed' }
