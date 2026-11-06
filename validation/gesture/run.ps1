$ErrorActionPreference = 'Stop'
$projectRoot = (Resolve-Path (Join-Path $PSScriptRoot '../..')).Path
$checks = Join-Path $projectRoot '.checks/gesture'
New-Item -ItemType Directory -Force -Path $checks | Out-Null

# Use the compiler's Java runtime so PATH cannot select a different JDK version.
$compiler = (Get-Command javac -CommandType Application -ErrorAction Stop).Source
$runtime = Join-Path (Split-Path $compiler -Parent) 'java.exe'
$recognizer = Join-Path $projectRoot 'app/src/main/java/dev/edgeflow/GestureRecognizer.java'
$angles = Join-Path $projectRoot 'app/src/main/java/dev/edgeflow/GestureAngles.java'
$haptics = Join-Path $projectRoot 'app/src/main/java/dev/edgeflow/GestureHaptics.java'
$handleConfig = Join-Path $projectRoot 'app/src/main/java/dev/edgeflow/GestureHandleConfig.java'
$harness = Join-Path $PSScriptRoot 'GestureRecognizerCheck.java'
$handleHarness = Join-Path $PSScriptRoot 'GestureHandleConfigCheck.java'
$durationHarness = Join-Path $PSScriptRoot 'LongGestureDurationCheck.java'
$angleHarness = Join-Path $PSScriptRoot 'AngleHapticCheck.java'

& $compiler -encoding UTF-8 --release 8 -d $checks $recognizer $angles $haptics $handleConfig $harness $handleHarness $durationHarness $angleHarness
if ($LASTEXITCODE -ne 0) { throw 'Gesture compilation failed; no stale classes will be executed' }
& $runtime -cp $checks GestureRecognizerCheck | Tee-Object -FilePath (Join-Path $checks 'results.txt')
if ($LASTEXITCODE -ne 0) { throw 'Gesture state validation failed' }
& $runtime -cp $checks GestureHandleConfigCheck | Tee-Object -FilePath (Join-Path $checks 'results.txt') -Append
if ($LASTEXITCODE -ne 0) { throw 'Handle configuration validation failed' }
& $runtime -cp $checks LongGestureDurationCheck | Tee-Object -FilePath (Join-Path $checks 'results.txt') -Append
if ($LASTEXITCODE -ne 0) { throw 'Long-duration boundary validation failed' }
& $runtime -cp $checks AngleHapticCheck | Tee-Object -FilePath (Join-Path $checks 'results.txt') -Append
if ($LASTEXITCODE -ne 0) { throw 'Independent angle and haptic configuration validation failed' }

# Compile the actual event/dispatch/cancel bodies, not a second implementation.
# The deterministic Handler model checks scheduling; Android dispatch remains a device test.
$inputChecks = Join-Path $checks 'input'
New-Item -ItemType Directory -Force -Path $inputChecks | Out-Null
$edge = Get-Content -Raw (Join-Path $projectRoot 'app/src/main/java/dev/edgeflow/EdgeHandleView.java')
function Extract-InputMethod([string]$signature) {
    $begin = $edge.IndexOf($signature)
    if ($begin -lt 0) { throw "Missing production edge method: $signature" }
    $at = $edge.IndexOf('{',$begin) + 1
    $depth = 1
    while ($depth -gt 0 -and $at -lt $edge.Length) {
        if ($edge[$at] -eq '{') { $depth++ }
        elseif ($edge[$at] -eq '}') { $depth-- }
        $at++
    }
    if ($depth -ne 0) { throw "Incomplete production edge method: $signature" }
    return $edge.Substring($begin,$at-$begin)
}
$timerStart = $edge.IndexOf('private final Runnable longCheck=')
if ($timerStart -lt 0) { throw 'Missing production long timer' }
$timerEnd = $edge.IndexOf('};',$timerStart) + 2
$prefix = @'
package dev.edgeflow;
public final class EdgeHandleView {
 private final GestureService service;private final boolean left;private final String handleId;
 private final Handler main=new Handler();private GestureRecognizer gesture;private boolean hapticReady,quickVibration;private int pointer=-1;
 EdgeHandleView(GestureService service,String id){this.service=service;handleId=id;left=GestureHandleConfig.isLeft(id);}
 private Resources getResources(){return service.resources;}private void haptic(boolean longer){service.feedback.add(longer);}
'@
$code = $prefix + $edge.Substring($timerStart,$timerEnd-$timerStart)
foreach ($signature in @('public boolean onTouchEvent(','private boolean handleGestureEvent(','private static float number(','private void dispatch(','public void cancel(')) { $code += (Extract-InputMethod $signature) }
$code += '}'
$extracted = Join-Path $inputChecks 'EdgeHandleView.java'
[System.IO.File]::WriteAllText($extracted,$code,[System.Text.UTF8Encoding]::new($false))
$inputSources = Get-ChildItem (Join-Path $PSScriptRoot 'input-src') -Recurse -Filter '*.java' | ForEach-Object FullName
& $compiler -encoding UTF-8 --release 8 -d $inputChecks $recognizer $angles $haptics $handleConfig $extracted @inputSources
if ($LASTEXITCODE -ne 0) { throw 'Production edge input validation compilation failed' }
& $runtime -cp $inputChecks dev.edgeflow.EdgeInputCheck | Tee-Object -FilePath (Join-Path $checks 'results.txt') -Append
if ($LASTEXITCODE -ne 0) { throw 'Production edge input/timer validation failed' }
