$ErrorActionPreference = 'Stop'
$projectRoot = (Resolve-Path (Join-Path $PSScriptRoot '../..')).Path
$checks = Join-Path $projectRoot '.checks/settings-scroll'
New-Item -ItemType Directory -Force -Path $checks | Out-Null
$compiler = (Get-Command javac -CommandType Application -ErrorAction Stop).Source
$runtime = Join-Path (Split-Path $compiler -Parent) 'java.exe'
& $compiler -encoding UTF-8 --release 8 -d $checks (Join-Path $projectRoot 'app/src/main/java/replica/ColorScrollPhysics.java') (Join-Path $PSScriptRoot 'ScrollPhysicsCheck.java')
if ($LASTEXITCODE -ne 0) { throw 'Settings spring compilation failed' }
& $runtime -cp $checks ScrollPhysicsCheck | Tee-Object -FilePath (Join-Path $checks 'results.txt')
if ($LASTEXITCODE -ne 0) { throw 'Settings spring validation failed' }
