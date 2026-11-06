param()
$ErrorActionPreference='Stop'
$projectRoot=(Resolve-Path (Join-Path $PSScriptRoot '../..')).Path
$output=Join-Path $projectRoot '.checks/handle-safe-area/classes'
New-Item -ItemType Directory -Path $output -Force | Out-Null
$android=Join-Path $projectRoot '.toolchains/android-sdk/platforms/android-35/android.jar'
$compiler=(Get-Command javac -CommandType Application).Source
$runtime=Join-Path (Split-Path $compiler -Parent) 'java.exe'
& $compiler -encoding UTF-8 --release 8 -cp $android -d $output (Join-Path $projectRoot 'app/src/main/java/dev/edgeflow/GestureHandleConfig.java') (Join-Path $projectRoot 'app/src/main/java/dev/edgeflow/HandleDisplayArea.java') (Join-Path $PSScriptRoot 'SafeAreaCheck.java')
if($LASTEXITCODE -ne 0){throw 'Safe-area compilation failed'}
& $runtime -cp "$output;$android" dev.edgeflow.SafeAreaCheck | Tee-Object -FilePath (Join-Path $output 'results.txt')
if($LASTEXITCODE -ne 0){throw 'Safe-area geometry validation failed'}
