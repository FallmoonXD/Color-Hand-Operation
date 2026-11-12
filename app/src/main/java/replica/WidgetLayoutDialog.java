package replica;

import android.app.AlertDialog;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.view.Gravity;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.SeekBar;
import android.widget.TextView;
import android.widget.Toast;

public final class WidgetLayoutDialog {
    public static AlertDialog create(Context context, int[] draft) {
        Preview preview = new Preview(context, draft);
        LinearLayout content = new LinearLayout(context);
        content.setOrientation(LinearLayout.VERTICAL);
        content.setPadding(dp(context, 16), 0, dp(context, 16), dp(context, 8));
        content.addView(preview, new LinearLayout.LayoutParams(-1, dp(context, 180)));
        count(content, preview, true);
        count(content, preview, false);
        TextView note = new TextView(context);
        note.setText("已有小组件会自动重排，空间不足时增加页面。");
        note.setTextSize(13);
        note.setTextColor(ColorOsTheme.secondary(context));
        note.setPadding(0, dp(context, 12), 0, 0);
        content.addView(note);
        return new ColorDialogBuilder(context).setTitle("布局").setView(content)
                .setNegativeButton("取消", null)
                .setPositiveButton("应用", (dialog, which) -> {
                    try { WidgetLayout.apply(context, preview.columns, preview.rows); }
                    catch (Exception failure) {
                        android.util.Log.w("WidgetLayout", "Cannot apply grid", failure);
                        Toast.makeText(context, failure.getMessage(), Toast.LENGTH_LONG).show();
                    }
                }).create();
    }

    private static void count(LinearLayout content, Preview preview, boolean columns) {
        Context context = content.getContext();
        LinearLayout row = new LinearLayout(context);
        row.setGravity(Gravity.CENTER_VERTICAL);
        TextView label = new TextView(context), value = new TextView(context);
        label.setText(columns ? "列数" : "行数");
        label.setTextSize(16); value.setTextSize(16);
        label.setGravity(Gravity.CENTER_VERTICAL);
        label.setTextColor(ColorOsTheme.text(context)); value.setTextColor(ColorOsTheme.secondary(context));
        row.addView(label, new LinearLayout.LayoutParams(0, dp(context, 36), 1));
        row.addView(value);
        content.addView(row);
        ColorSlider slider = new ColorSlider(context);
        slider.setMax(4);
        slider.setProgress((columns ? preview.columns : preview.rows) - 2);
        slider.setContentDescription(label.getText());
        value.setText(Integer.toString(slider.getProgress() + 2));
        slider.setStateDescription(value.getText());
        slider.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            public void onProgressChanged(SeekBar bar, int progress, boolean user) {
                if (columns) preview.columns = progress + 2; else preview.rows = progress + 2;
                preview.draft[0] = preview.columns; preview.draft[1] = preview.rows;
                value.setText(Integer.toString(progress + 2));
                slider.setStateDescription(value.getText());
                preview.describe(); preview.invalidate();
            }
            public void onStartTrackingTouch(SeekBar bar) {}
            public void onStopTrackingTouch(SeekBar bar) {}
        });
        content.addView(slider, new LinearLayout.LayoutParams(-1, dp(context, 48)));
    }

    private static final class Preview extends View {
        int columns, rows;
        final int[] draft;
        final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
        Preview(Context context, int[] draft) {
            super(context); this.draft = draft; columns = draft[0]; rows = draft[1]; describe();
        }
        void describe() { setContentDescription(columns + " 列 × " + rows + " 行"); }
        @Override protected void onDraw(Canvas canvas) {
            float cell = Math.min(getWidth() / (columns + 1f), getHeight() / (rows + 1f));
            float left = (getWidth() - columns * cell) / 2, top = (getHeight() - rows * cell) / 2;
            float gap = dp(getContext(), 3), radius = dp(getContext(), 4);
            paint.setColor(ColorOsTheme.accent(getContext()));
            for (int row = 0; row < rows; row++) for (int col = 0; col < columns; col++)
                canvas.drawRoundRect(left + col * cell + gap, top + row * cell + gap,
                        left + (col + 1) * cell - gap, top + (row + 1) * cell - gap, radius, radius, paint);
        }
    }
    private static int dp(Context context, int value) { return PanelStyle.dp(context, value); }
    private WidgetLayoutDialog() {}
}
