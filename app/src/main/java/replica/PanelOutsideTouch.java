package replica;

import android.view.MotionEvent;
import android.view.View;
import android.view.Window;
import android.view.WindowManager;

/** A touch-modal floating window owns the complete gesture that starts outside its frame. */
final class PanelOutsideTouch {
    private boolean outside;

    static void makeModal(Window window) {
        window.clearFlags(WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
                | WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL
                | WindowManager.LayoutParams.FLAG_WATCH_OUTSIDE_TOUCH
                | WindowManager.LayoutParams.FLAG_SPLIT_TOUCH);
    }

    static boolean isOutside(View decor, MotionEvent event) {
        float x = event.getX(), y = event.getY();
        return x < 0 || y < 0 || x >= decor.getWidth() || y >= decor.getHeight();
    }

    boolean consume(View decor, MotionEvent event, Runnable dismiss) {
        int action = event.getActionMasked();
        if (action == MotionEvent.ACTION_DOWN) {
            outside = isOutside(decor, event);
        }
        if (!outside) return false;
        if (action == MotionEvent.ACTION_UP || action == MotionEvent.ACTION_CANCEL) {
            outside = false;
            if (action == MotionEvent.ACTION_UP) dismiss.run();
        }
        return true;
    }
}
