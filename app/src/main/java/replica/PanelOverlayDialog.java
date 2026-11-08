package replica;

import android.app.Dialog;
import android.content.Context;
import android.view.KeyEvent;
import android.view.MotionEvent;
import android.view.Window;
import android.view.WindowManager;
import android.view.ViewConfiguration;
import android.window.OnBackInvokedCallback;
import android.window.OnBackInvokedDispatcher;

/** A panel owns Back without putting an Activity above the user's task. */
public final class PanelOverlayDialog extends Dialog {
    private final Runnable back, outside;
    private final OnBackInvokedCallback systemBack;
    private final PanelOutsideTouch touch = new PanelOutsideTouch();
    private dev.edgeflow.EdgeHandleView edgeHandle;
    private MotionEvent edgeDown;
    private boolean edgeSwipe;
    private boolean exiting;

    static void beginWindowExit(Window window) {
        if (window.getCallback() instanceof PanelOverlayDialog panel) {
            panel.exiting = true;
        } else if (!(window.getCallback() instanceof android.app.AlertDialog
                && window.getAttributes().type == WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY)) {
            window.addFlags(WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE);
        }
        // Service alerts already consume input in their closing state. Keep both
        // kinds of modal window in the input stack until their last visible frame.
    }

    public PanelOverlayDialog(Context context, Runnable back, Runnable outside) {
        super(context, context.getResources().getIdentifier(PanelStyle.dark(context) ? "ReplicaGlassDark" : "ReplicaGlass", "style", context.getPackageName()));
        this.back = back;
        this.outside = outside;
        systemBack = back::run;
        requestWindowFeature(Window.FEATURE_NO_TITLE);
        setCanceledOnTouchOutside(false);
        Window window = getWindow();
        window.setType(WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY);
        PanelOutsideTouch.makeModal(window);
        window.clearFlags(WindowManager.LayoutParams.FLAG_DIM_BEHIND);
        window.addFlags(WindowManager.LayoutParams.FLAG_ALT_FOCUSABLE_IM);
    }

    @Override protected void onStart() {
        super.onStart();
        getOnBackInvokedDispatcher().registerOnBackInvokedCallback(OnBackInvokedDispatcher.PRIORITY_DEFAULT, systemBack);
    }

    @Override protected void onStop() {
        clearEdge();
        getOnBackInvokedDispatcher().unregisterOnBackInvokedCallback(systemBack);
        super.onStop();
    }

    @Override public void onBackPressed() { back.run(); }

    @Override public boolean dispatchKeyEvent(KeyEvent event) {
        if (event.getKeyCode() != KeyEvent.KEYCODE_BACK) return super.dispatchKeyEvent(event);
        if (event.getAction() == KeyEvent.ACTION_UP && !event.isCanceled()) back.run();
        return true;
    }

    @Override public boolean dispatchTouchEvent(MotionEvent event) {
        if (exiting) return true;
        // Handle ordinary outside taps here; an inward swipe on a real CHO handle
        // still uses the original recognizer, mapping, timers and foreground checks.
        if (event.getActionMasked() == MotionEvent.ACTION_DOWN) {
            clearEdge();
            android.view.View decor = getWindow().getDecorView();
            if (PanelOutsideTouch.isOutside(decor, event)) {
                dev.edgeflow.GestureService service = dev.edgeflow.GestureService.instance();
                if (service != null) edgeHandle = service.handleAt(event.getRawX(), event.getRawY());
                if (edgeHandle != null) edgeDown = MotionEvent.obtain(event);
            }
        }
        if (edgeHandle != null) {
            int action = event.getActionMasked();
            if (edgeDown != null && action == MotionEvent.ACTION_MOVE) {
                int slop = ViewConfiguration.get(getContext()).getScaledTouchSlop();
                if (Math.hypot(event.getRawX()-edgeDown.getRawX(),event.getRawY()-edgeDown.getRawY()) > slop) {
                    MotionEvent down = edgeDown;
                    edgeDown = null;
                    try { edgeSwipe = edgeHandle.isShown() && edgeHandle.onTouchEvent(down); }
                    finally { down.recycle(); }
                }
            }
            boolean swipe = edgeSwipe;
            if (swipe) edgeHandle.onTouchEvent(event);
            if (action == MotionEvent.ACTION_UP || action == MotionEvent.ACTION_CANCEL) {
                // The recognizer has already handled UP/CANCEL (or never received DOWN).
                edgeHandle = null;
                clearEdge();
                if (!swipe && action == MotionEvent.ACTION_UP) outside.run();
            }
            return true;
        }
        return touch.consume(getWindow().getDecorView(),event,outside) || super.dispatchTouchEvent(event);
    }

    private void clearEdge() {
        if (edgeSwipe && edgeHandle != null) edgeHandle.cancel();
        edgeHandle = null;
        edgeSwipe = false;
        if (edgeDown != null) { edgeDown.recycle(); edgeDown = null; }
    }
}
