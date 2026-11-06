$ErrorActionPreference = 'Stop'
$projectRoot = (Resolve-Path (Join-Path $PSScriptRoot '../..')).Path
$checks = Join-Path $projectRoot '.checks/material-policy/lifecycle'
New-Item -ItemType Directory -Force -Path $checks | Out-Null
# Compile the production binding helper itself, isolated from Android rendering classes.
$source = Get-Content -Raw (Join-Path $projectRoot 'app/src/main/java/replica/ColorOsSurface.java')
$index = $source.IndexOf('final class MaterialThemeBinding')
if ($index -lt 0) { throw 'Production binding helper was not found' }
$header = "package replica;`nimport android.graphics.drawable.Drawable;import android.view.View;import java.lang.ref.WeakReference;import java.util.ArrayList;import java.util.WeakHashMap;`n"
$extracted = Join-Path $checks 'MaterialThemeBinding.java'
[System.IO.File]::WriteAllText($extracted,$header+$source.Substring($index),[System.Text.UTF8Encoding]::new($false))
$native = Get-Content -Raw (Join-Path $projectRoot 'app/src/main/java/replica/NativeMaterial.java')
$methodStart = $native.IndexOf('private void applyPalette()')
if ($methodStart -lt 0) { throw 'Production palette method was not found' }
$brace = $native.IndexOf('{',$methodStart)
$depth = 1
$methodEnd = $brace + 1
while ($depth -gt 0 -and $methodEnd -lt $native.Length) {
    if ($native[$methodEnd] -eq '{') { $depth++ }
    elseif ($native[$methodEnd] -eq '}') { $depth-- }
    $methodEnd++
}
if ($depth -ne 0) { throw 'Production palette method is incomplete' }
$palette = Join-Path $checks 'NativeMaterialPalette.java'
$prefix = 'package replica; final class NativeMaterialPalette {final java.util.Map<String,float[]> values=new java.util.HashMap<>(); final boolean dark,strokeAllowed;final int kind;final float density; NativeMaterialPalette(boolean dark,boolean stroke,int kind,float density){this.dark=dark;strokeAllowed=stroke;this.kind=kind;this.density=density;applyPalette();} private void uniform(String name,float... values){this.values.put(name,values);}'
[System.IO.File]::WriteAllText($palette,$prefix+$native.Substring($methodStart,$methodEnd-$methodStart)+'}',[System.Text.UTF8Encoding]::new($false))
$light = Get-Content -Raw (Join-Path $projectRoot 'app/src/main/java/replica/LightSurface.java')
$methodStart = $light.IndexOf('static int opaqueColor(')
if ($methodStart -lt 0) { throw 'Production opaque palette method was not found' }
$brace = $light.IndexOf('{',$methodStart)
$depth = 1
$methodEnd = $brace + 1
while ($depth -gt 0 -and $methodEnd -lt $light.Length) {
    if ($light[$methodEnd] -eq '{') { $depth++ }
    elseif ($light[$methodEnd] -eq '}') { $depth-- }
    $methodEnd++
}
if ($depth -ne 0) { throw 'Production opaque palette method is incomplete' }
$opaque = Join-Path $checks 'MaterialOpaquePalette.java'
[System.IO.File]::WriteAllText($opaque,'package replica; final class MaterialOpaquePalette {'+$light.Substring($methodStart,$methodEnd-$methodStart)+'}',[System.Text.UTF8Encoding]::new($false))
$sources = Get-ChildItem (Join-Path $PSScriptRoot 'src') -Recurse -Filter '*.java' | ForEach-Object FullName
$compiler = (Get-Command javac -CommandType Application -ErrorAction Stop).Source
$runtime = Join-Path (Split-Path $compiler -Parent) 'java.exe'
& $compiler -encoding UTF-8 --release 8 -d $checks $extracted $palette $opaque @sources
if ($LASTEXITCODE -ne 0) { throw 'Material host validation compilation failed' }
& $runtime -cp $checks replica.MaterialBindingCheck | Tee-Object -FilePath (Join-Path $checks 'results.txt')
if ($LASTEXITCODE -ne 0) { throw 'Material host lifecycle validation failed' }
& $runtime -cp $checks replica.MaterialPaletteCheck | Tee-Object -FilePath (Join-Path $checks 'results.txt') -Append
if ($LASTEXITCODE -ne 0) { throw 'Accepted material palette validation failed' }
& $runtime -cp $checks replica.MaterialOpaqueCheck | Tee-Object -FilePath (Join-Path $checks 'results.txt') -Append
if ($LASTEXITCODE -ne 0) { throw 'Opaque surface readability validation failed' }
