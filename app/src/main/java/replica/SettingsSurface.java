package replica;

import android.content.Context;
import android.graphics.BlendMode;
import android.graphics.BlendModeColorFilter;
import android.graphics.Color;
import android.graphics.Outline;
import android.graphics.RenderEffect;
import android.graphics.Shader;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.Drawable;
import android.os.PowerManager;
import android.view.View;
import android.view.ViewOutlineProvider;
import android.view.Window;
import android.view.WindowManager;
import java.lang.reflect.Constructor;
import java.lang.reflect.Method;
import java.util.function.Consumer;

/** Backgrounds for Settings controls. A View effect samples siblings behind it;
 * a window blur samples the surface behind that window. */
final class SettingsSurface implements View.OnAttachStateChangeListener {
    enum Kind { TOOLBAR, EMBEDDED_SHEET, POPUP, DIALOG, SHEET }
    private static final Method VIEW_EFFECT = viewEffectMethod();
    private final View view;
    private final Kind kind;
    private final int corner;
    private final WindowManager wm;
    private final ToolbarEdge edge;
    private final ToolbarShadow shadow;
    private final TopEdgeGradient panelTint;
    private final Consumer<Boolean> windowBlurChanged = enabled -> refresh();
    private Object rootManager;
    private Drawable cardBackground, cardBase;
    private int revealAlpha = 255;
    private boolean listening, applied, failed;

    SettingsSurface(View view, Kind kind, int corner, Window window) {
        this.view = view;
        this.kind = kind;
        this.corner = corner;
        if (window != null) window.setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
        wm = view.getContext().getSystemService(WindowManager.class);
        edge = kind == Kind.TOOLBAR ? new ToolbarEdge(view) : null;
        shadow = kind == Kind.TOOLBAR ? new ToolbarShadow(view) : null;
        int px = PanelStyle.dp(view.getContext(), corner);
        panelTint = kind == Kind.TOOLBAR ? null : TopEdgeGradient.panel(view.getContext(), px, ColorOsTheme.popup(view.getContext()));
        view.setOutlineProvider(new ViewOutlineProvider() {
            @Override public void getOutline(View v, Outline outline) {
                outline.setRoundRect(0, 0, v.getWidth(), v.getHeight(), px);
                outline.setAlpha(revealAlpha / 255f);
            }
        });
        view.setClipToOutline(true);
        view.addOnAttachStateChangeListener(this);
        if (view.isAttachedToWindow()) onViewAttachedToWindow(view);
        else solid();
    }

    private static Method viewEffectMethod() {
        try {
            return Class.forName("com.oplus.view.OplusViewBackgroundRenderEffect")
                    .getMethod("setBackgroundRenderEffect", RenderEffect.class, View.class);
        } catch (ReflectiveOperationException | RuntimeException | LinkageError unavailable) { return null; }
    }

    static void setLocalBackgroundEffect(View view, RenderEffect effect) throws ReflectiveOperationException {
        if (VIEW_EFFECT == null) throw new NoSuchMethodException("setBackgroundRenderEffect");
        VIEW_EFFECT.invoke(null, effect, view);
    }

    private boolean local() { return kind == Kind.TOOLBAR || kind == Kind.EMBEDDED_SHEET; }

    @Override public void onViewAttachedToWindow(View v) {
        if (!local() && WindowCardApi.API != null && wm != null && !listening) {
            wm.addCrossWindowBlurEnabledListener(windowBlurChanged);
            listening = true;
        }
        refresh();
    }

    @Override public void onViewDetachedFromWindow(View v) {
        if (listening) { wm.removeCrossWindowBlurEnabledListener(windowBlurChanged); listening = false; }
        if (edge != null) edge.update(false, false, 0);
        if (shadow != null) shadow.clear();
        clear();
        failed = false;
    }

    private void background(int color) {
        Drawable shape = kind == Kind.TOOLBAR
                ? ColorDialogBuilder.touchBackground(view.getContext(), color, corner)
                : PanelStyle.shape(view.getContext(), color, corner);
        if (local()) view.setBackground(panelTint == null ? shape : panelTint.over(shape));
        else setCardBackground(shape);
    }

    private void solid() {
        Context context = view.getContext();
        background(kind == Kind.TOOLBAR ? ColorOsTheme.card(context) : ColorOsTheme.popup(context));
    }

    void refresh() {
        Context c = view.getContext();
        if (panelTint != null) panelTint.setSurfaceColor(ColorOsTheme.popup(c));
        PowerManager power = c.getSystemService(PowerManager.class);
        boolean available = view.isAttachedToWindow() && view.isHardwareAccelerated()
                && (power == null || !power.isPowerSaveMode());
        if (edge != null) edge.update(available && ColorOsTheme.materialStroke(c), ColorOsTheme.dark(c),
                PanelStyle.dp(c, corner));
        if (shadow != null && view.isAttachedToWindow()) shadow.update(available && ColorOsTheme.materialStroke(c), ColorOsTheme.dark(c));
        boolean enabled = available && !failed && ColorOsTheme.materialBlur(c);
        if (!enabled || (local() ? VIEW_EFFECT == null :
                wm == null || !wm.isCrossWindowBlurEnabled() || WindowCardApi.API == null)) {
            clear();
            return;
        }
        boolean dark = ColorOsTheme.dark(c);
        try {
            if (local()) {
                setLocalBackgroundEffect(view, localEffect(dark));
                applied = true;
                background(kind == Kind.TOOLBAR ? Color.TRANSPARENT : ((ColorOsTheme.popup(c) & 0xffffff) | 0xe6000000));
            } else {
                int radius = kind == Kind.POPUP ? 160 : kind == Kind.DIALOG ? 200 : 400;
                applyWindowCard(radius, dark);
            }
        } catch (ReflectiveOperationException | RuntimeException | LinkageError error) {
            failed = true;
            android.util.Log.w("SettingsSurface", "Using theme background for " + kind, error);
            clear();
        }
    }

    // PopupWindow and the palette dialog need a blur confined to one card.
    // These are the SDR parameters from Settings' RoundFrameLayout and COUIBottomSheetDialog.
    private void applyWindowCard(int radius, boolean dark) throws ReflectiveOperationException {
        WindowCardApi api = WindowCardApi.API;
        if (rootManager == null) rootManager = api.manager.newInstance(view);
        boolean popup = kind == Kind.POPUP;
        int blend = popup ? (dark ? 0x99262626 : 0xbfeeeeee) : (dark ? 0xb30f0f0f : 0x376e6e6e);
        int mix = popup ? (dark ? 0x99262626 : 0x99cdcdcd) : (dark ? 0xa4515151 : 0xdcc8c8c8);
        Object params = api.params.newInstance();
        api.blurType.invoke(params, 2);
        api.material.invoke(params, popup ? 4 : dark ? 2 : 3, rgba(blend), rgba(mix));
        Drawable blur = (Drawable) api.drawable.invoke(rootManager);
        if (blur == null) throw new IllegalStateException("No window blur drawable");
        api.parameters.invoke(rootManager, params);
        api.radius.invoke(rootManager, radius);
        float px = PanelStyle.dp(view.getContext(), corner);
        api.corners.invoke(rootManager, px, px, px, px);
        setCardBackground(blur);
        applied = true;
    }

    void setRevealAlpha(float alpha) {
        int value = Math.round(255 * Math.max(0f, Math.min(1f, alpha)));
        if (value == revealAlpha) return;
        revealAlpha = value;
        if (cardBackground != null) cardBackground.setAlpha(value);
        view.invalidateOutline();
    }

    private void setCardBackground(Drawable drawable) {
        // A window blur is composited separately. Seed even the first drawable with
        // the current reveal value before publishing it to the attached View.
        if (cardBase == drawable && view.getBackground() == cardBackground) return;
        cardBase = drawable;
        cardBackground = panelTint.over(drawable);
        cardBackground.setAlpha(revealAlpha);
        view.setBackground(cardBackground);
    }

    private static float[] rgba(int color) {
        return new float[]{Color.red(color)/255f, Color.green(color)/255f, Color.blue(color)/255f, Color.alpha(color)/255f};
    }

    private static final class WindowCardApi {
        static final WindowCardApi API = load();
        final Constructor<?> manager, params;
        final Method drawable, parameters, radius, corners, blurType, material;
        private static WindowCardApi load() {
            try { return new WindowCardApi(); }
            catch (ReflectiveOperationException | RuntimeException | LinkageError unavailable) { return null; }
        }
        private WindowCardApi() throws ReflectiveOperationException {
            Class<?> root = Class.forName("com.oplus.view.ViewRootManager");
            Class<?> param = Class.forName("com.oplus.graphics.OplusBlurParam");
            manager = root.getConstructor(View.class);
            params = param.getConstructor();
            drawable = root.getMethod("getBackgroundBlurDrawable");
            parameters = root.getMethod("setBlurParams", param);
            radius = root.getMethod("setBlurRadius", int.class);
            corners = root.getMethod("setCornerRadius", float.class, float.class, float.class, float.class);
            blurType = param.getMethod("setBlurType", int.class);
            material = param.getMethod("setMaterialParams", int.class, float[].class, float[].class);
        }
    }

    private RenderEffect localEffect(boolean dark) {
        // Settings' TYPE_FRAMEWORK_TOP_BAR_BLUR has a 20 px radius and two blend layers.
        int radius = kind == Kind.EMBEDDED_SHEET ? PanelStyle.dp(view.getContext(), 14) : 20;
        RenderEffect blur = RenderEffect.createBlurEffect(radius, radius, Shader.TileMode.MIRROR);
        if (kind == Kind.EMBEDDED_SHEET) return blur;
        RenderEffect first = RenderEffect.createColorFilterEffect(new BlendModeColorFilter(
                dark ? 0xcc262626 : 0x66333333, dark ? BlendMode.SRC_OVER : BlendMode.COLOR_DODGE), blur);
        return RenderEffect.createColorFilterEffect(new BlendModeColorFilter(
                dark ? 0xb37f7f7f : 0x99f5f5f5, dark ? BlendMode.OVERLAY : BlendMode.LUMINOSITY), first);
    }

    private void clear() {
        cardBackground = null;
        cardBase = null;
        if (applied && local()) {
            try { VIEW_EFFECT.invoke(null, null, view); }
            catch (ReflectiveOperationException | RuntimeException | LinkageError ignored) { }
        }
        if (rootManager != null) {
            try { WindowCardApi.API.radius.invoke(rootManager, 0); }
            catch (ReflectiveOperationException | RuntimeException | LinkageError ignored) { }
            rootManager = null;
        }
        applied = false;
        solid();
    }
}
