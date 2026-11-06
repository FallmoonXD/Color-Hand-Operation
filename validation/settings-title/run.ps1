$ErrorActionPreference = 'Stop'
$projectRoot = (Resolve-Path (Join-Path $PSScriptRoot '../..')).Path
$checks = Join-Path $projectRoot '.checks/settings-title'
New-Item -ItemType Directory -Force -Path $checks | Out-Null
$compiler = (Get-Command javac -CommandType Application -ErrorAction Stop).Source
$runtime = Join-Path (Split-Path $compiler -Parent) 'java.exe'
& $compiler -encoding UTF-8 --release 8 -d $checks (Join-Path $projectRoot 'app/src/main/java/replica/ColorTitleMotion.java') (Join-Path $PSScriptRoot 'TitleMotionCheck.java')
if ($LASTEXITCODE -ne 0) { throw 'Title motion compilation failed' }
& $runtime -cp $checks TitleMotionCheck | Tee-Object -FilePath (Join-Path $checks 'results.txt')
if ($LASTEXITCODE -ne 0) { throw 'Title motion validation failed' }
