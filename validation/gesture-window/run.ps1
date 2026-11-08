$ErrorActionPreference = 'Stop'
$projectRoot = (Resolve-Path (Join-Path $PSScriptRoot '../..')).Path
$checks = Join-Path $projectRoot '.checks/gesture-window'
New-Item -ItemType Directory -Force -Path $checks | Out-Null
$source = Get-Content -Raw (Join-Path $projectRoot 'app/src/main/java/dev/edgeflow/GestureService.java')
function Extract-ServiceMethod([string]$signature) {
    $begin = $source.IndexOf($signature)
    if ($begin -lt 0) { throw "Missing production method: $signature" }
    $at = $source.IndexOf('{',$begin) + 1
    $depth = 1
    while ($depth -gt 0 -and $at -lt $source.Length) {
        if ($source[$at] -eq '{') { $depth++ }
        elseif ($source[$at] -eq '}') { $depth-- }
        $at++
    }
    if ($depth -ne 0) { throw "Incomplete production method: $signature" }
    return $source.Substring($begin,$at-$begin)
}
$code = @'
package dev.edgeflow;
public final class GestureService {
 static final String TAG="test";final WindowManager windows=new WindowManager();final SharedPreferences prefs=new SharedPreferences();final Handler main=new Handler();
 EdgeHandleView left,right,extraLeft,extraRight,extra2Left,extra2Right;GestureWaveView wave,leftWave,rightWave;
 boolean connected=true,handlesVisible,pendingWindowState,pendingConfiguration,pendingGeometry,allowed=true;int screenWidth,screenHeight,rootReads,panelDismissals;float screenDensity;
 GestureHandleConfig.Area handleArea;
 final java.util.HashMap<String,View> handlePreviews=new java.util.HashMap<>();final java.util.HashMap<String,float[]> handlePreviewRegions=new java.util.HashMap<>();
 private final Runnable applyPreferences=this::applyPreferenceChanges;
 boolean canStartGesture(){return allowed&&prefs.getBoolean("enabled",true);}void refreshWindowState(){rootReads++;}
 Resources getResources(){return new Resources();}String getString(int id){return "Test";}void removeHandlePreviews(){}
 void dismissPanels(){panelDismissals++;}
 void boot(){rebuild();}void queue(){schedulePreferences(false);}void configure(){pendingConfiguration=true;schedulePreferences(true);}void visibility(){updateVisibility();}
'@
foreach ($signature in @('private void schedulePreferences(','private void applyPreferenceChanges(','private void scheduleGeometryRefresh(','private int[] previewBounds(','private void syncPreviewVisibility(','private void updateHandlePreviews(','private void updateWaveLayout(','private boolean handleEnabled(','private EdgeHandleView[] handleViews(','public EdgeHandleView handleAt(','private void updateVisibility(','private float number(','private void rebuild(','private int[] handleBounds(','private EdgeHandleView addHandle(','private void updateHandleLayout(','private WindowManager.LayoutParams parameters(','private void cancelGesture(','private void removeWindows(','private void removeGestureWindows(','private void remove(View','public void onSharedPreferenceChanged(')) { $code += (Extract-ServiceMethod $signature) }
$code += '}'
$extracted = Join-Path $checks 'GestureService.java'
[System.IO.File]::WriteAllText($extracted,$code,[System.Text.UTF8Encoding]::new($false))
$compiler = (Get-Command javac -CommandType Application -ErrorAction Stop).Source
$runtime = Join-Path (Split-Path $compiler -Parent) 'java.exe'
& $compiler -encoding UTF-8 --release 8 -d $checks $extracted (Join-Path $projectRoot 'app/src/main/java/dev/edgeflow/GestureHandleConfig.java') (Join-Path $PSScriptRoot 'WindowUpdateCheck.java')
if ($LASTEXITCODE -ne 0) { throw 'Production window update compilation failed' }
& $runtime -cp $checks dev.edgeflow.WindowUpdateCheck | Tee-Object -FilePath (Join-Path $checks 'results.txt')
if ($LASTEXITCODE -ne 0) { throw 'Production window update validation failed' }
