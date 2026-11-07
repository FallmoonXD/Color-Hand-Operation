package replica;

import android.app.Dialog;
import android.content.Context;
import android.view.KeyEvent;
import android.view.MotionEvent;
import android.view.Window;
import android.view.WindowManager;
import android.window.OnBackInvokedCallback;
import android.window.OnBackInvokedDispatcher;

/** A panel owns Back without putting an Activity above the user's task. */
final class PanelOverlayDialog extends Dialog {
    private final Runnable back, outside;
    private final OnBackInvokedCallback systemBack;

    PanelOverlayDialog(Context context, Runnable back, Runnable outside) {
        super(context, context.getResources().getIdentifier(PanelStyle.dark(context) ? "ReplicaGlassDark" : "ReplicaGlass", "style", context.getPackageName()));
        this.back = back;
        this.outside = outside;
        systemBack = back::run;
        requestWindowFeature(Window.FEATURE_NO_TITLE);
        setCanceledOnTouchOutside(false);
        Window window = getWindow();
        window.setType(WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY);
        window.clearFlags(WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE | WindowManager.LayoutParams.FLAG_DIM_BEHIND);
        window.addFlags(WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL | WindowManager.LayoutParams.FLAG_WATCH_OUTSIDE_TOUCH
                | WindowManager.LayoutParams.FLAG_ALT_FOCUSABLE_IM);
    }

    @Override protected void onStart() {
        super.onStart();
        getOnBackInvokedDispatcher().registerOnBackInvokedCallback(OnBackInvokedDispatcher.PRIORITY_DEFAULT, systemBack);
    }

    @Override protected void onStop() {
        getOnBackInvokedDispatcher().unregisterOnBackInvokedCallback(systemBack);
        super.onStop();
    }

    @Override public void onBackPressed() { back.run(); }

    @Override public boolean dispatchKeyEvent(KeyEvent event) {
        if (event.getKeyCode() != KeyEvent.KEYCODE_BACK) return super.dispatchKeyEvent(event);
        if (event.getAction() == KeyEvent.ACTION_UP && !event.isCanceled()) back.run();
        return true;
    }

    @Override public boolean onTouchEvent(MotionEvent event) {
        if (event.getActionMasked() == MotionEvent.ACTION_OUTSIDE) { outside.run(); return true; }
        return super.onTouchEvent(event);
    }
}
