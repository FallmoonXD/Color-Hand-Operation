param([Parameter(Mandatory=$true)][string]$AndroidJar)
$ErrorActionPreference = 'Stop'
$projectRoot = (Resolve-Path (Join-Path $PSScriptRoot '../..')).Path
$checks = Join-Path $projectRoot '.checks/config-harness'
$productionClasses = Join-Path $checks 'production-classes'
$harnessClasses = Join-Path $checks 'classes'
New-Item -ItemType Directory -Force -Path $checks,$productionClasses,$harnessClasses | Out-Null
$jsonJar = Join-Path $checks 'json-20240303.jar'
if (-not (Test-Path -LiteralPath $jsonJar)) {
    Invoke-WebRequest 'https://repo.maven.apache.org/maven2/org/json/json/20240303/json-20240303.jar' -OutFile $jsonJar
}
if ((Get-FileHash -LiteralPath $jsonJar -Algorithm SHA256).Hash -ne '3CF6CD6892E32E2B4C1C39E0F52F5248A2F5B37646FDFBB79A66B46B618414ED') {
    throw 'Unexpected JSON test dependency hash'
}
$bypass = Join-Path $projectRoot 'app/libs/hiddenapibypass.jar'
$resources = Join-Path $projectRoot 'app/build/intermediates/compile_and_runtime_r_class_jar/debug/processDebugResources/R.jar'
if (-not (Test-Path -LiteralPath $resources)) { throw 'Run gradlew.bat :app:processDebugResources first to generate the resource constants' }
$sources = Get-ChildItem (Join-Path $projectRoot 'app/src/main/java') -Recurse -Filter '*.java' | ForEach-Object FullName
& javac -encoding UTF-8 -source 17 -target 17 -cp "$AndroidJar;$bypass;$resources" -d $productionClasses @sources
if ($LASTEXITCODE -ne 0) { throw 'Production compilation failed; no stale classes will be executed' }
$harness = Get-ChildItem $PSScriptRoot -Recurse -Filter '*.java' | ForEach-Object FullName
& javac -encoding UTF-8 -cp "$jsonJar;$productionClasses;$AndroidJar" -d $harnessClasses @harness
if ($LASTEXITCODE -ne 0) { throw 'Harness compilation failed' }
& java -cp "$harnessClasses;$jsonJar;$productionClasses;$AndroidJar" dev.edgeflow.ConfigArchiveHarness | Tee-Object -FilePath (Join-Path $checks 'results.txt')
if ($LASTEXITCODE -ne 0) { throw 'Configuration validation failed' }
