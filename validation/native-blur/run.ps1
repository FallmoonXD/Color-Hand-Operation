$ErrorActionPreference='Stop'
$project=(Resolve-Path (Join-Path $PSScriptRoot '../..')).Path
$output=Join-Path $project '.checks/native-blur-policy'
New-Item -ItemType Directory -Force -Path $output | Out-Null
& javac -encoding UTF-8 --release 17 -d $output (Join-Path $project 'app/src/main/java/replica/NativeBlurPolicy.java') (Join-Path $PSScriptRoot 'NativeBlurPolicyCheck.java')
if($LASTEXITCODE -ne 0){throw 'Native blur policy compilation failed'}
& java -cp $output replica.NativeBlurPolicyCheck | Tee-Object -FilePath (Join-Path $output 'results.txt')
if($LASTEXITCODE -ne 0){throw 'Native blur policy checks failed'}
