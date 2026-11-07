param([Parameter(Mandatory=$true)][string]$AndroidJar)
$ErrorActionPreference='Stop'
$projectRoot=(Resolve-Path (Join-Path $PSScriptRoot '../..')).Path
$harness=Join-Path $projectRoot '.checks/config-harness'
$production=Join-Path $harness 'production-classes'
if (-not (Test-Path -LiteralPath (Join-Path $harness 'classes'))) {
 & (Join-Path $projectRoot 'validation/config/run.ps1') -AndroidJar $AndroidJar
}
$output=Join-Path $projectRoot '.checks/action'
New-Item -ItemType Directory -Force -Path $output | Out-Null
$bypass=Join-Path $projectRoot 'app/libs/hiddenapibypass.jar'
$resources=Join-Path $projectRoot 'app/build/intermediates/compile_and_runtime_r_class_jar/debug/processDebugResources/R.jar'
if (-not (Test-Path -LiteralPath $resources)) { throw 'Run gradlew.bat :app:processDebugResources first to generate R.jar' }
$sources=Get-ChildItem (Join-Path $projectRoot 'app/src/main/java') -Recurse -Filter '*.java' | ForEach-Object FullName
& javac -encoding UTF-8 -source 17 -target 17 -cp "$AndroidJar;$bypass;$resources" -d $production @sources
if ($LASTEXITCODE -ne 0) { throw 'Action source compilation failed' }
& javac -encoding UTF-8 -cp "$production;$AndroidJar" -d $output (Join-Path $PSScriptRoot 'ActionCommandCheck.java') (Join-Path $PSScriptRoot 'PendingRootCommandCheck.java')
if ($LASTEXITCODE -ne 0) { throw 'Action checks compilation failed' }
& java -cp "$output;$harness/classes;$harness/json-20240303.jar;$production;$AndroidJar" dev.edgeflow.ActionCommandCheck | Tee-Object -FilePath (Join-Path $output 'results.txt')
if ($LASTEXITCODE -ne 0) { throw 'Action checks failed' }
& java -cp "$output;$production" dev.edgeflow.PendingRootCommandCheck $output
if ($LASTEXITCODE -ne 0) { throw 'Pending root command checks failed' }
$api33 = Join-Path $output 'api33'
New-Item -ItemType Directory -Force -Path $api33 | Out-Null
$stubs = Get-ChildItem (Join-Path $PSScriptRoot 'api33-src') -Recurse -Filter '*.java' | ForEach-Object FullName
& javac -encoding UTF-8 -cp "$production;$AndroidJar" -d $api33 @stubs
if ($LASTEXITCODE -ne 0) { throw 'API 33 magnification fixture compilation failed' }
& java -cp "$api33;$production" dev.edgeflow.MagnificationApi33Check
if ($LASTEXITCODE -ne 0) { throw 'API 33 magnification linkage check failed' }
