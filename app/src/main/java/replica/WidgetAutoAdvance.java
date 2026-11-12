package replica;

import android.appwidget.AppWidgetHostView;
import android.appwidget.AppWidgetProviderInfo;
import android.graphics.Rect;
import android.os.SystemClock;
import android.util.SparseBooleanArray;
import android.view.View;
import android.widget.Advanceable;

final class WidgetAutoAdvance implements Runnable {
    private static final long INTERVAL = 20000;
    private static final SparseBooleanArray ACTIVE = new SparseBooleanArray();
    private final AppWidgetHostView host;
    private final Rect visible = new Rect();
    private Advanceable target;
    private int registeredId = -1;
    private boolean scheduled;

    WidgetAutoAdvance(AppWidgetHostView host) { this.host = host; }

    void refresh() {
        AppWidgetProviderInfo info = host.getAppWidgetInfo();
        View child = info == null || info.autoAdvanceViewId == -1 ? null : host.findViewById(info.autoAdvanceViewId);
        Advanceable next = child instanceof Advanceable ? (Advanceable) child : null;
        if (!host.isAttachedToWindow() || !host.isShown() || host.getWindowVisibility() != View.VISIBLE
                || next == null || host.getAppWidgetId() < 0) {
            stop();
            return;
        }
        if (next != target || registeredId != host.getAppWidgetId()) {
            stop();
            target = next;
            registeredId = host.getAppWidgetId();
            ACTIVE.put(registeredId, true);
            target.fyiWillBeAdvancedByHostKThx();
        }
        schedule();
    }

    private void schedule() {
        if (scheduled || target == null) return;
        long now = SystemClock.uptimeMillis();
        long delay = INTERVAL - now % INTERVAL + Math.max(0, ACTIVE.indexOfKey(registeredId)) * 250L;
        scheduled = host.postDelayed(this, delay);
    }

    @Override public void run() {
        scheduled = false;
        if (!host.isAttachedToWindow() || !host.isShown() || host.getWindowVisibility() != View.VISIBLE) {
            stop();
            return;
        }
        if (target != null && host.getGlobalVisibleRect(visible)) target.advance();
        schedule();
    }

    void stop() {
        host.removeCallbacks(this);
        if (registeredId >= 0) ACTIVE.delete(registeredId);
        registeredId = -1;
        scheduled = false;
        target = null;
    }
}
