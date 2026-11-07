package replica;

import android.content.Context;
import android.graphics.RenderEffect;
import android.graphics.drawable.GradientDrawable;
import android.os.PowerManager;
import android.view.View;
import android.widget.FrameLayout;
import java.lang.reflect.Method;

/** The Settings app bar samples the scrolling content behind it in the same window. */
final class ColorAppBarMask extends FrameLayout {
    private static final Method CREATE_GRADIENT = gradientMethod();
    private final View backdrop;
    private GradientDrawable resting;
    private boolean applied, failed;
    private float fraction;

    ColorAppBarMask(Context context) {
        super(context);
        setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_NO_HIDE_DESCENDANTS);
        backdrop=new View(context);
        addView(backdrop,new FrameLayout.LayoutParams(-1,-1));
        ColorOsTheme.watch(this, this::refresh);
        refresh();
    }

    private static Method gradientMethod() {
        try {
            return Class.forName("com.oplus.graphics.OplusRenderEffect").getMethod("createGradientBlurEffect",
                    float.class, float.class, boolean.class, float.class, int.class, int.class, int.class);
        } catch (ReflectiveOperationException | RuntimeException | LinkageError unavailable) { return null; }
    }

    void refresh() {
        Context context = getContext();
        PowerManager power = context.getSystemService(PowerManager.class);
        boolean enabled = ColorOsTheme.materialBlur(context) && (power == null || !power.isPowerSaveMode());
        if (!enabled) {
            clearEffect();
        } else {
            if (!applied && !failed && isHardwareAccelerated() && isAttachedToWindow() && CREATE_GRADIENT != null) {
                try {
                    RenderEffect effect = (RenderEffect) CREATE_GRADIENT.invoke(null, 30f, 0f, true, 3f, 0, 0, 0);
                    if (effect != null) { SettingsSurface.setLocalBackgroundEffect(backdrop, effect); applied = true; }
                } catch (ReflectiveOperationException | RuntimeException | LinkageError unavailable) {
                    failed = true;
                    android.util.Log.w("ColorAppBar", "Using the app bar color gradient", unavailable);
                }
            }
        }
        int rgb = ColorOsTheme.background(context) & 0xffffff;
        boolean dark = ColorOsTheme.dark(context);
        GradientDrawable gradient = new GradientDrawable();
        gradient.setOrientation(GradientDrawable.Orientation.BOTTOM_TOP);
        if (!applied) {
            // Keep text passing behind the header legible without a blur-capable renderer.
            gradient.setColors(new int[]{rgb, rgb | 0xff000000, rgb | 0xff000000}, new float[]{0, .33f, 1});
        } else if (dark) {
            gradient.setColors(new int[]{rgb, rgb | 0x66000000}, new float[]{0, 1});
        } else {
            gradient.setColors(new int[]{rgb, rgb | 0xb2000000, rgb | 0xff000000}, new float[]{0, .4f, 1});
        }
        backdrop.setBackground(gradient);
        // Keep a page-colored fade even when the list fits. Its lower edge picks up the card
        // color; the status-bar end stays the color of the page in both themes.
        int card=ColorOsTheme.card(context)&0xffffff;
        resting=new GradientDrawable();
        resting.setOrientation(GradientDrawable.Orientation.TOP_BOTTOM);
        resting.setColors(new int[]{rgb|0xff000000,rgb|0xc0000000,card|0x55000000,card},
                new float[]{0,.68f,.88f,1});
        setForeground(resting);
        applyFraction();
    }

    void setTravel(int listOffset,float edgeTranslation,int ramp) {
        float next=ColorHeaderFade.blur(listOffset,edgeTranslation,ramp);
        if(next!=fraction){fraction=next;applyFraction();}
    }
    private void applyFraction(){
        backdrop.setAlpha(fraction);
        resting.setAlpha(ColorHeaderFade.restingAlpha(fraction));
    }
    @Override protected void onLayout(boolean changed,int left,int top,int right,int bottom){
        backdrop.layout(0,0,right-left,bottom-top);
    }

    private void clearEffect() {
        if (!applied) return;
        try { SettingsSurface.setLocalBackgroundEffect(backdrop, null); }
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
