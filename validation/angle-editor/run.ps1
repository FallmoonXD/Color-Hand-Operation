$ErrorActionPreference = 'Stop'
$projectRoot = (Resolve-Path (Join-Path $PSScriptRoot '../..')).Path
$checks = Join-Path $projectRoot '.checks/angle-editor'
New-Item -ItemType Directory -Force -Path $checks | Out-Null
$compiler = (Get-Command javac -CommandType Application -ErrorAction Stop).Source
$runtime = Join-Path (Split-Path $compiler -Parent) 'java.exe'
$sources = @('GestureRecognizer.java','GestureAngles.java','AngleEditorGeometry.java') | ForEach-Object { Join-Path $projectRoot "app/src/main/java/dev/edgeflow/$_" }
& $compiler -encoding UTF-8 --release 8 -d $checks @sources (Join-Path $PSScriptRoot 'AngleEditorCheck.java')
if ($LASTEXITCODE -ne 0) { throw 'Angle editor compilation failed; no stale classes will be executed' }
& $runtime -cp $checks AngleEditorCheck | Tee-Object -FilePath (Join-Path $checks 'results.txt')
if ($LASTEXITCODE -ne 0) { throw 'Angle editor geometry validation failed' }
