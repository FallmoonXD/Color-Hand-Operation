package replica;

import android.accessibilityservice.AccessibilityService;
import android.app.KeyguardManager;
import android.os.PowerManager;
import android.view.WindowManager;

/** The normal widget entry point shares its host ID and layout with the manual preview. */
public final class WidgetOverlay {
    private static WidgetOverlay current;
    private final PanelOverlayDialog dialog;
    private final WidgetPanel panel;
    private ColorMotion motion;
    private float[] anchor;
    private boolean closing, removed;

    private WidgetOverlay(AccessibilityService service, float[] anchor) {
        this.anchor = anchor.clone();
        dialog = new PanelOverlayDialog(service, this::back, this::outside);
        WindowManager.LayoutParams lp = dialog.getWindow().getAttributes();
        lp.setTitle(service.getString(dev.edgeflow.R.string.app_name) + " 插件");
        dialog.getWindow().setAttributes(lp);
        panel = new WidgetPanel(service, dialog.getWindow(), anchor, true, (provider, id, bound, existing, page) -> {
            service.startActivity(WidgetActivity.setupIntent(service, provider, id, bound, existing, page, this.anchor));
            remove();
        });
        dialog.setOnDismissListener(d -> remove());
    }

    public static void show(AccessibilityService service, float[] anchor) {
        if (WidgetActivity.setupInProgress() || !service.getSystemService(PowerManager.class).isInteractive()
                || service.getSystemService(KeyguardManager.class).isKeyguardLocked()) return;
        if (current != null && !current.closing) {
            current.anchor = anchor.clone();
            if(current.motion!=null)current.motion.reanchor(anchor[0]<PanelGeometry.screen(service).x/2f,anchor[1]);
            current.panel.reanchor(anchor);
            return;
        }
        dismissAll();
        try {
            WidgetOverlay owner = new WidgetOverlay(service, anchor);
            current = owner;
            owner.panel.render();
            owner.motion = ColorMotion.forTool(owner.dialog.getWindow(),ToolAnimation.WIDGETS,anchor);
            owner.dialog.show();
            owner.panel.start();
            owner.motion.enter();
        } catch (RuntimeException failure) {
            dismissAll();
            android.util.Log.w("EdgeWidgets", "Cannot open widget panel", failure);
            android.widget.Toast.makeText(service, "无法打开插件面板", android.widget.Toast.LENGTH_SHORT).show();
        }
    }

    public static boolean handleBack() {
        if (current == null) return false;
        current.back();
        return true;
    }

    public static void configurationChanged() {
        if (current != null && !current.closing) current.panel.render();
    }

    public static void dismissAll() { if (current != null) current.remove(); }
    private void back() { if (!closing && !panel.backInEditor()) close(); }
    private void outside() { if (!panel.hasEditor()) close(); }
    private void close() {
        if (closing || removed) return;
        closing = true;
        if (motion == null) remove(); else motion.exit(this::remove);
    }
    private void remove() {
        if (removed) return;
        removed = true;
        if (current == this) current = null;
        if (motion != null) motion.cancel();
        try { panel.close(); } finally { dialog.dismiss(); }
    }
}
