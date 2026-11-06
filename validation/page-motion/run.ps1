param()
$ErrorActionPreference='Stop'
$project=(Resolve-Path (Join-Path $PSScriptRoot '../..')).Path
$output=Join-Path $project '.checks/page-motion'
New-Item -ItemType Directory -Force -Path $output | Out-Null
$production=Join-Path $project 'app/src/main/java/replica/ColorPageMotion.java'
# Preserve every production field and method; only bind Android imports to deterministic boundaries.
$source=Get-Content -LiteralPath $production -Raw
$source=[regex]::Replace($source,'(?m)^import android\.[^;]+;\r?\n','')
$adapted=Join-Path $output 'ColorPageMotion.java'
[System.IO.File]::WriteAllText($adapted,$source,[System.Text.UTF8Encoding]::new($false))
& javac -encoding UTF-8 --release 17 -d $output $adapted (Join-Path $PSScriptRoot 'PageMotionCheck.java')
if ($LASTEXITCODE -ne 0) { throw 'Page motion validation compilation failed' }
& java '-Dfile.encoding=UTF-8' '-Dstdout.encoding=UTF-8' -cp $output replica.PageMotionCheck | Tee-Object -FilePath (Join-Path $output 'results.txt')
if ($LASTEXITCODE -ne 0) { throw 'Page motion validation failed' }
$sdk=Join-Path $project '.toolchains/android-sdk/platforms/android-35/android.jar'
& javac -encoding UTF-8 --release 17 -cp $sdk -d (Join-Path $output 'android-compile') $production
if ($LASTEXITCODE -ne 0) { throw 'Unmodified ColorPageMotion Android SDK compilation failed' }
