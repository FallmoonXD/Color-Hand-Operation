# Settings spacing and integer readouts

## Evidence and cause

The previous Main row used 64 dp minimum height and 10 dp vertical padding for every entry. A 44 dp ColorSwitch therefore made even a title-only row 64 dp tall. The local ColorOS Settings resources use a wrap-content COUI preference, `support_preference_min_height=48dp`, and `homepage_preference_min_height=52dp`; they do not impose one fixed height on rows with and without summaries.

Main now uses 56 dp minimum height and 4 dp vertical padding when the summary is absent or empty. Rows with a summary retain 64 dp minimum height and 10 dp padding, and multiline content can grow naturally. This preserves switch and radio hit areas rather than shrinking the controls.

ColorSlider draws its normal 20 dp track at the center of its 44 dp view. That leaves 12 dp above the visible track even with no layout gap. Main-specific slider layout now applies `topMargin=-8dp`: the track top sits 4 dp below the endpoint-label row, while the expanded 28 dp pressed track reaches that row's bottom. The entire 44 dp SeekBar hit area remains in place; ColorSlider drawing, touch arbitration and other callers are unchanged. The overlapping part contains non-clickable endpoint text. Device checks must confirm actual text clearance and slider/scroll gestures with the final font size.

## Numbers

SettingsDisplay performs integer formatting only. Stored floats, slider steps, draft state and cancellation are unchanged. Fractional speed is represented as an integer percentage, avoiding ambiguity: 0.5 becomes 50%, 2.35 becomes 235%, and 3 becomes 300%. Raw values remain 0.5, 2.35 and 3.

## Verification

- `validation/settings-display/run.ps1`: 22 assertions passed, including integer labels across all 1001 width slider positions, percentage units, distinct 50–300% speed endpoints and legacy haptic display values.
- `validation/config/run.ps1` with the Gradle-transformed mockable Android jar: production Java compilation and 184 configuration/geometry/preview assertions passed.
- No ADB/device operation, screenshot or final visual validation was performed by this agent. Root handles real layout, large-font and touch validation.

This edit does not change angleSettings, Service geometry, preview bounds or the shared ColorSlider implementation.
