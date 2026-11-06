# Gallery color palette adaptation

The connected phone's `com.coloros.gallery3d` is version **17.10.41**, versionCode **17010041**. It was inspected through read-only package/path queries and an APK pull, without opening Gallery or changing device state. The APK and decoded references are under `analysis/coloros17-gallery/`.

The relevant picker belongs to Gallery's **doodle toolkit**, not the photo filter/toning panel. `de_toolkit_color_picker_portrait.xml` contains “网格 / 色环” chips, `DeToolkitHsvPicker`, `DeToolkitRGBPicker`, an alpha gradient, a selected-color pane and stored swatches. Those classes live in `com.oplusos.vfxsdk.doodleengine.toolkit.internal`; they are coupled to private toolkit, AndroidX/COUI and editing infrastructure. The standalone app does not load, ship or invoke those classes or `libcolor_select.so`.

Source evidence:

- `resources/res/layout/de_toolkit_color_picker_portrait.xml` / `small.xml`: 328dp / 268dp picker regions, 64dp selected-color pane, alpha track 20dp tall.
- `resources/res/values/dimens.xml`: main hue ring 233dp with 38dp stroke and 90dp SV square; compact variant 183/30/70dp; hue marker 14dp +5dp white border, SV marker 6dp +2dp border.
- `single/DeToolkitHsvPicker.java`: counterclockwise hue mapping, red at right; saturation increases rightward; value increases upward; separate white marker rings.
- `single/DeToolkitRGBPicker.java`: 10 rows ×12 columns. `GalleryPaletteColors` contains only its numeric color table. Gray `-12105913` and the generated constant reference `-700` were resolved from `DeToolkitColorPicker.STABLE_COLOR_GRAY` and `single/LanConstants.java` respectively.
- `resources/res/drawable/de_toolkit_seekbar_bg.xml` and `de_toolkit_seekbar_point.xml`: transparent-to-color track with a 14dp white thumb.

`ColorPalettePicker` independently draws the ring, SV square and grid with Canvas, and supplies virtual accessibility controls for all grid cells and HSV parameters. `ColorPaletteSheet` uses the existing app-owned ColorActionSheet container, live system palette/fonts and a custom gradient visibility slider. It includes keyboard/HEX editing and a bounded scrollable layout. It does not include Gallery's image eyedropper, color-history pages or editor integrations; no image is being edited here. The sheet presentation and confirm/cancel flow are specific to handle configuration.

## State contract

```java
new ColorPaletteSheet(activity, currentArgb, currentGlobalOpacity,
    new ColorPaletteSheet.Listener() {
        public void onPreview(int argb, int opacity) { /* transient swatch only */ }
        public void onConfirm(int argb, int opacity) { /* persist once */ }
        public void onCancel() { /* redraw stored values */ }
    }).setOnDismissListener(() -> clearReference()).show();
```

The caller decides which handle(s) the color belongs to. `handle_opacity` stays a separate global 0..100 setting. Ring/grid changes preserve the existing ARGB alpha byte. The checker preview alone multiplies alpha by visibility; it never writes that product back as the stored color. Eight-digit HEX edits the full ARGB; six-digit HEX means opaque RGB. Invalid input displays an error and disables confirm; keyboard confirmation validates again.

All values remain local to the sheet until its exit completes and `onConfirm` runs. Outside/back/cancel and Activity lifecycle dismissal invoke `onCancel`; cancelling during a confirmation exit does not submit. The wrapper resolves cancellation on the next main-thread turn because the shared sheet's dismissal callback precedes deferred actions. Main owns the preference write and current-handle scope.

## Verification

`validation/color-palette/run.ps1` tests exact signed ARGB preservation, HEX validity, alpha/visibility independence, cancel restoration, hue orientation and every source grid color's HSV round trip. Component Java compiles against API35. This records local model/source verification; root device tests separately cover gestures, invalid input, cancel and commit.
