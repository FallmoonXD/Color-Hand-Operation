package replica;

import android.content.Context;
import android.graphics.RenderEffect;
import android.graphics.drawable.GradientDrawable;
import android.os.PowerManager;
import android.view.View;
import java.lang.reflect.Method;

/** The Settings app bar samples the scrolling content behind it in the same window. */
final class ColorAppBarMask extends View {
    private static final Method CREATE_GRADIENT = gradientMethod();
    private boolean material, applied, failed;
    private float fraction;

    ColorAppBarMask(Context context) {
        super(context);
        setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_NO);
        ColorOsTheme.watch(this, this::refresh);
        refresh();
    }

    private static Method gradientMethod() {
        try {
            return Class.forName("com.oplus.graphics.OplusRenderEffect").getMethod("createGradientBlurEffect",
                    float.class, float.class, boolean.class, float.class, int.class, int.class, int.class);
        } catch (ReflectiveOperationException | RuntimeException | LinkageError unavailable) { return null; }
    }

    boolean hasGradient() { return material; }

    void refresh() {
        Context context = getContext();
        PowerManager power = context.getSystemService(PowerManager.class);
        boolean enabled = ColorOsTheme.materialBlur(context) && (power == null || !power.isPowerSaveMode());
        if (material != enabled) { material = enabled; requestLayout(); }
        if (!enabled) {
            clearEffect();
            setBackgroundColor(ColorOsTheme.background(context));
        } else {
            if (!applied && !failed && isHardwareAccelerated() && isAttachedToWindow() && CREATE_GRADIENT != null) {
                try {
                    RenderEffect effect = (RenderEffect) CREATE_GRADIENT.invoke(null, 30f, 0f, true, 3f, 0, 0, 0);
                    if (effect != null) { SettingsSurface.setLocalBackgroundEffect(this, effect); applied = true; }
                } catch (ReflectiveOperationException | RuntimeException | LinkageError unavailable) {
                    failed = true;
                    android.util.Log.w("ColorAppBar", "Using the app bar color gradient", unavailable);
                }
            }
            int rgb = ColorOsTheme.background(context) & 0xffffff;
            boolean dark = ColorOsTheme.dark(context);
            int[] colors = dark ? new int[]{rgb, rgb | (applied ? 0x66000000 : 0xff000000)}
                                : new int[]{rgb, rgb | 0xb2000000, rgb | 0xff000000};
            GradientDrawable gradient = new GradientDrawable();
            gradient.setOrientation(GradientDrawable.Orientation.BOTTOM_TOP);
            gradient.setColors(colors, dark ? new float[]{0, 1} : new float[]{0, .4f, 1});
            setBackground(gradient);
        }
        setFraction(fraction);
    }

    void setFraction(float value) {
        fraction = value;
        float alpha = material ? value : 1;
        if (getAlpha() != alpha) setAlpha(alpha);
        int visibility = alpha == 0 ? INVISIBLE : VISIBLE;
        if (getVisibility() != visibility) setVisibility(visibility);
    }

    private void clearEffect() {
        if (!applied) return;
        try { SettingsSurface.setLocalBackgroundEffect(this, null); }
        catch (ReflectiveOperationException | RuntimeException | LinkageError unavailable) {
            android.util.Log.w("ColorAppBar", "Could not clear the app bar effect", unavailable);
        }
        applied = false;
    }

    @Override protected void onAttachedToWindow() {
        super.onAttachedToWindow();
        refresh();
    }

    @Override protected void onDetachedFromWindow() {
        clearEffect();
        failed = false;
        super.onDetachedFromWindow();
    }
}
