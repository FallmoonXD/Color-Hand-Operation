$ErrorActionPreference = 'Stop'
$projectRoot = (Resolve-Path (Join-Path $PSScriptRoot '../..')).Path
$checks = Join-Path $projectRoot '.checks/color-controls'
New-Item -ItemType Directory -Force -Path $checks | Out-Null
$compiler = (Get-Command javac -CommandType Application -ErrorAction Stop).Source
$runtime = Join-Path (Split-Path $compiler -Parent) 'java.exe'
& $compiler -encoding UTF-8 --release 8 -d $checks (Join-Path $projectRoot 'app/src/main/java/replica/ColorControlPhysics.java') (Join-Path $PSScriptRoot 'ControlPhysicsCheck.java')
if ($LASTEXITCODE -ne 0) { throw 'Control physics compilation failed' }
& $runtime -cp $checks ControlPhysicsCheck | Tee-Object -FilePath (Join-Path $checks 'results.txt')
if ($LASTEXITCODE -ne 0) { throw 'Control physics validation failed' }

# Exercise the real production touch/callback bodies against a narrow SeekBar model.
# Android's dispatcher is still device-owned; these check our lifecycle contract.
$slider = Get-Content -Raw (Join-Path $projectRoot 'app/src/main/java/replica/ColorSlider.java')
function Extract-Body([string]$signature) {
    $begin = $slider.IndexOf($signature)
    if ($begin -lt 0) { throw "Missing production slider method: $signature" }
    $at = $slider.IndexOf('{',$begin) + 1
    $depth = 1
    while ($depth -gt 0 -and $at -lt $slider.Length) {
        if ($slider[$at] -eq '{') { $depth++ }
        elseif ($slider[$at] -eq '}') { $depth-- }
        $at++
    }
    if ($depth -ne 0) { throw "Incomplete production slider method: $signature" }
    return $slider.Substring($begin,$at-$begin)
}
$listenerStart = $slider.IndexOf('super.setOnSeekBarChangeListener(new OnSeekBarChangeListener(){')
if ($listenerStart -lt 0) { throw 'Missing production slider listener' }
$listenerEnd = $slider.IndexOf('});',$listenerStart) + 3
$prefix = @'
package replica;import android.view.MotionEvent;import android.widget.SeekBar;
public final class ColorSlider extends SeekBar {
 private OnSeekBarChangeListener client;private boolean tracking,touchActive,touchCancelled;
 private final Motion enlarge=new Motion(),thumbEnlarge=new Motion(),texture=new Motion(),cover=new Motion(),progress=new Motion();
 private final Material material=new Material();private Object thumbGradient;
 private static final class Motion {void snap(float value){}void animate(float target,float response,float bounce){}}
 private static final class Material {void clear(){}}
 private float fraction(){return getProgress()/100f;}
 public ColorSlider(){
'@
$code = $prefix + $slider.Substring($listenerStart,$listenerEnd-$listenerStart) + '}'
foreach ($signature in @('public void setOnSeekBarChangeListener(','public boolean isTouchCancelled(','public boolean onTouchEvent(','public void setEnabled(','private void finishTracking(','protected void onDetachedFromWindow(')) { $code += (Extract-Body $signature) }
$code += '}'
$extracted = Join-Path $checks 'ColorSlider.java'
[System.IO.File]::WriteAllText($extracted,$code,[System.Text.UTF8Encoding]::new($false))
$sources = Get-ChildItem (Join-Path $PSScriptRoot 'slider-src') -Recurse -Filter '*.java' | ForEach-Object FullName
& $compiler -encoding UTF-8 --release 8 -d $checks $extracted @sources
if ($LASTEXITCODE -ne 0) { throw 'Slider cancellation compilation failed' }
& $runtime -cp $checks replica.SliderCancellationCheck | Tee-Object -FilePath (Join-Path $checks 'results.txt') -Append
if ($LASTEXITCODE -ne 0) { throw 'Slider cancellation validation failed' }

$motionChecks = Join-Path $checks 'motion'
New-Item -ItemType Directory -Force -Path $motionChecks | Out-Null
$slider = Get-Content -Raw (Join-Path $projectRoot 'app/src/main/java/replica/ColorSwitch.java')
$prefix = @'
package replica;import android.view.MotionEvent;import android.widget.CompoundButton;
public final class ColorSwitch extends CompoundButton {
 final ColorControlMotion position=new ColorControlMotion(this,0),press=new ColorControlMotion(this,0);
 private int pointer=-1,slop=4;private float downX,downY,downPosition;private boolean dragging,aborted;
 private float dp(float value){return value;}private boolean rtl(){return false;}
'@
$code = $prefix
foreach ($signature in @('public void setChecked(','private void moveChecked(','public void setEnabled(','public boolean onTouchEvent(','private void finishTouch(','public boolean performClick(','protected void drawableStateChanged(','public void jumpDrawablesToCurrentState(','protected void onDetachedFromWindow(')) { $code += (Extract-Body $signature) }
$code += '}'
$extractedSwitch = Join-Path $motionChecks 'ColorSwitch.java'
[System.IO.File]::WriteAllText($extractedSwitch,$code,[System.Text.UTF8Encoding]::new($false))
$motionSources = Get-ChildItem (Join-Path $PSScriptRoot 'motion-src') -Recurse -Filter '*.java' | ForEach-Object FullName
& $compiler -encoding UTF-8 --release 8 -d $motionChecks $extractedSwitch (Join-Path $projectRoot 'app/src/main/java/replica/ColorControlMotion.java') (Join-Path $projectRoot 'app/src/main/java/replica/ColorControlPhysics.java') @motionSources
if ($LASTEXITCODE -ne 0) { throw 'Switch/motion lifecycle compilation failed' }
& $runtime -cp $motionChecks replica.SwitchMotionCheck | Tee-Object -FilePath (Join-Path $checks 'results.txt') -Append
if ($LASTEXITCODE -ne 0) { throw 'Switch/motion lifecycle validation failed' }
