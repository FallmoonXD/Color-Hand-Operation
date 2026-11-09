package replica;

import android.app.Activity;
import android.appwidget.AppWidgetManager;
import android.appwidget.AppWidgetProviderInfo;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.view.Window;
import android.view.WindowManager;
import android.widget.Toast;
import dev.edgeflow.GestureService;
import java.lang.ref.WeakReference;

/** Owns results for system widget binding/configuration and the preview without a service. */
public final class WidgetActivity extends Activity {
    private static final String SETUP = "dev.edgeflow.WIDGET_SETUP";
    private static final int BIND = 1, CONFIGURE = 2;
    private static WeakReference<WidgetActivity> current = new WeakReference<>(null);
    private WidgetPanel panel;
    private ColorMotion motion;
    private float[] anchor;
    private int pending = -1, targetPage;
    private ComponentName provider;
    private boolean existing, fromOverlay, returnToOverlay, closing;

    static Intent setupIntent(Context context, ComponentName provider, int id, boolean bound, boolean existing, int page, float[] anchor) {
        return new Intent(context, WidgetActivity.class).setAction(SETUP).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                .putExtra("provider", provider.flattenToString()).putExtra("id", id).putExtra("bound", bound)
                .putExtra("existing", existing).putExtra("page", page).putExtra(NativeState.GESTURE_EXTRA, anchor);
    }

    static boolean setupInProgress() {
        WidgetActivity owner = current.get();
        return owner != null && !owner.isFinishing() && (owner.pending >= 0 || owner.fromOverlay);
    }

    public static void dismissAll() {
        WidgetActivity owner = current.get();
        if (owner != null && !setupInProgress() && !owner.isFinishing()) owner.finish();
    }

    @Override protected void onCreate(Bundle state) {
        setTheme(getResources().getIdentifier(PanelStyle.dark(this) ? "ReplicaGlassDark" : "ReplicaGlass", "style", getPackageName()));
        super.onCreate(state);
        current = new WeakReference<>(this);
        requestWindowFeature(Window.FEATURE_NO_TITLE);
        getWindow().setBackgroundDrawableResource(android.R.color.transparent);
        getWindow().clearFlags(WindowManager.LayoutParams.FLAG_DIM_BEHIND);
        anchor = NativeState.intentGesture(this, state);
        fromOverlay = SETUP.equals(getIntent().getAction());
        panel = new WidgetPanel(this, getWindow(), anchor, false, this::startSetup);
        if (state != null) {
            pending = state.getInt("pending", -1);
            existing = state.getBoolean("existing");
            targetPage = state.getInt("page");
            provider = component(state.getString("provider"));
        }
        if (fromOverlay) {
            setContentView(new View(this));
            getWindow().setLayout(1, 1);
            if (state == null) startIntent(getIntent());
            else if (pending < 0) finish();
        } else {
            setFinishOnTouchOutside(true);
            panel.render();
            motion = ColorMotion.forTool(getWindow(),ToolAnimation.WIDGETS,anchor);
            motion.enter();
        }
        overridePendingTransition(0, 0);
    }

    private static ComponentName component(String raw) { return raw == null ? null : ComponentName.unflattenFromString(raw); }
    private void startIntent(Intent intent) {
        startSetup(component(intent.getStringExtra("provider")), intent.getIntExtra("id", -1),
                intent.getBooleanExtra("bound", false), intent.getBooleanExtra("existing", false), intent.getIntExtra("page", 0));
    }

    private void startSetup(ComponentName name, int id, boolean bound, boolean configureExisting, int page) {
        pending = id; provider = name; existing = configureExisting; targetPage = page;
        if (name == null || id < 0) { endSetup(false); return; }
        try {
            if (bound) configure();
            else startActivityForResult(new Intent(AppWidgetManager.ACTION_APPWIDGET_BIND)
                    .putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, id).putExtra(AppWidgetManager.EXTRA_APPWIDGET_PROVIDER, name), BIND);
        } catch (RuntimeException failure) {
            Toast.makeText(this, "无法绑定此插件", Toast.LENGTH_SHORT).show();
            endSetup(false);
        }
    }

    private void configure() {
        AppWidgetProviderInfo info = AppWidgetManager.getInstance(this).getAppWidgetInfo(pending);
        if (info == null || !info.provider.equals(provider)) { endSetup(false); return; }
        if (info.configure == null) { endSetup(true); return; }
        try { panel.host.startAppWidgetConfigureActivityForResult(this, pending, 0, CONFIGURE, null); }
        catch (RuntimeException failure) {
            Toast.makeText(this, "插件设置无法打开", Toast.LENGTH_SHORT).show();
            endSetup(false);
        }
    }

    private void endSetup(boolean success) {
        if (pending >= 0 && !existing) {
            if (success) panel.commitWidget(pending, targetPage);
            else panel.host.deleteAppWidgetId(pending);
        }
        pending = -1; existing = false;
        if (fromOverlay) { returnToOverlay = true; finish(); }
        else panel.render();
    }

    @Override protected void onActivityResult(int request, int result, Intent data) {
        super.onActivityResult(request, result, data);
        if (pending < 0 || (request != BIND && request != CONFIGURE)) return;
        if (result != RESULT_OK) endSetup(false);
        else if (request == BIND) configure();
        else endSetup(true);
    }

    @Override protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        if (pending >= 0) {
            int incoming = intent.getIntExtra("id", -1);
            if (SETUP.equals(intent.getAction()) && incoming >= 0 && incoming != pending && !intent.getBooleanExtra("existing", false))
                panel.host.deleteAppWidgetId(incoming);
            return;
        }
        setIntent(intent); anchor = NativeState.intentGesture(this, null);
        if (motion != null) { motion.cancel(); motion = null; }
        closing = false;
        returnToOverlay = false;
        getWindow().clearFlags(WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE);
        fromOverlay = SETUP.equals(intent.getAction());
        if (fromOverlay) {
            panel.dismissEditors();
            panel.stop();
            setContentView(new View(this));
            getWindow().setLayout(1, 1);
            startIntent(intent);
        } else {
            setFinishOnTouchOutside(true);
            panel.reanchor(anchor);
            panel.start();
            motion = ColorMotion.forTool(getWindow(),ToolAnimation.WIDGETS,anchor);
            motion.enter();
        }
    }

    @Override protected void onSaveInstanceState(Bundle out) {
        super.onSaveInstanceState(out);
        out.putFloatArray(NativeState.GESTURE_EXTRA, anchor); out.putInt("pending", pending);
        out.putBoolean("existing", existing); out.putInt("page", targetPage);
        if (provider != null) out.putString("provider", provider.flattenToString());
    }
    @Override protected void onStart() { super.onStart(); if (!fromOverlay) panel.start(); }
    @Override protected void onPause() { panel.dismissEditors(); super.onPause(); }
    @Override protected void onStop() { panel.stop(); super.onStop(); }
    @Override public void onBackPressed(){if(panel==null||!panel.backInEditor())super.onBackPressed();}
    @Override public void finish() {
        if (closing) return;
        closing = true;
        panel.dismissEditors();
        if (motion == null) super.finish();
        else motion.exit(() -> { WidgetActivity.super.finish(); overridePendingTransition(0, 0); });
    }
    @Override protected void onDestroy() {
        if (current.get() == this) current.clear();
        if (motion != null) motion.cancel();
        if (isFinishing() && pending >= 0 && !existing) panel.host.deleteAppWidgetId(pending);
        panel.close(); super.onDestroy();
        GestureService service = GestureService.instance();
        if (returnToOverlay && service != null) WidgetOverlay.show(service, anchor);
    }
}
