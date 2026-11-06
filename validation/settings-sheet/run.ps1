$ErrorActionPreference = 'Stop'
$projectRoot = (Resolve-Path (Join-Path $PSScriptRoot '../..')).Path
$checks = Join-Path $projectRoot '.checks/settings-sheet'
New-Item -ItemType Directory -Force -Path $checks | Out-Null
$compiler = (Get-Command javac -CommandType Application -ErrorAction Stop).Source
$runtime = Join-Path (Split-Path $compiler -Parent) 'java.exe'
& $compiler -encoding UTF-8 --release 8 -d $checks (Join-Path $projectRoot 'app/src/main/java/replica/ColorSheetGeometry.java') (Join-Path $PSScriptRoot 'SheetGeometryCheck.java')
if ($LASTEXITCODE -ne 0) { throw 'Sheet geometry compilation failed' }
& $runtime -cp $checks SheetGeometryCheck | Tee-Object -FilePath (Join-Path $checks 'results.txt')
if ($LASTEXITCODE -ne 0) { throw 'Sheet geometry validation failed' }
python -X utf8 (Join-Path $PSScriptRoot 'run-retention.py')
if ($LASTEXITCODE -ne 0) { throw 'Sheet retention validation failed' }
