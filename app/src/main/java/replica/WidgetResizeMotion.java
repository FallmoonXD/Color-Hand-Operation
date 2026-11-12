package replica;

import android.graphics.Rect;
import android.os.SystemClock;
import android.view.View;

/** Launcher resize springs, in pixels. New grid targets retain the current velocity. */
final class WidgetResizeMotion implements Runnable {
    private final View host;
    private final Runnable settled;
    private final ColorControlPhysics[] axes = {
            new ColorControlPhysics(0), new ColorControlPhysics(0),
            new ColorControlPhysics(1), new ColorControlPhysics(1)
    };
    private final int[] target = new int[4];
    private boolean running;
    private long last, started;

    WidgetResizeMotion(View host, Runnable settled) {
        this.host = host;
        this.settled = settled;
    }

    boolean running() { return running; }

    void reset(int left, int top, int width, int height) {
        cancel();
        target[0] = left; target[1] = top; target[2] = width; target[3] = height;
        for (int i = 0; i < axes.length; i++) {
            axes[i].position = target[i];
            axes[i].velocity = 0;
        }
    }

    void animateTo(int left, int top, int width, int height) {
        if (target[0] == left && target[1] == top && target[2] == width && target[3] == height) return;
        target[0] = left; target[1] = top; target[2] = width; target[3] = height;
        if (!ColorControlMotion.canAnimate(host)) { finish(); return; }
        started = SystemClock.uptimeMillis();
        if (!running) {
            running = true;
            last = started;
            host.postOnAnimation(this);
        }
    }

    void bounds(Rect out) {
        int x = (int) Math.round(axes[0].position), y = (int) Math.round(axes[1].position);
        out.set(x, y, x + Math.max(1, (int) Math.round(axes[2].position)),
                y + Math.max(1, (int) Math.round(axes[3].position)));
    }

    void cancel() { host.removeCallbacks(this); running = false; }

    private void finish() {
        reset(target[0], target[1], target[2], target[3]);
        host.requestLayout();
        settled.run();
    }

    @Override public void run() {
        if (!running) return;
        float scale = ColorControlMotion.durationScale(host);
        if (!host.isAttachedToWindow() || !host.isShown() || scale <= 0) { finish(); return; }
        long now = SystemClock.uptimeMillis();
        double dt = Math.max(0, (now - last) / (1000d * scale));
        last = now;
        boolean done = true;
        for (int i = 0; i < axes.length; i++) {
            axes[i].step(target[i], .35, .2, dt);
            done &= Math.abs(axes[i].position - target[i]) < .75 && Math.abs(axes[i].velocity) < 46.875;
        }
        if (done || now - started >= 5000 * scale) { finish(); return; }
        host.requestLayout();
        host.postOnAnimation(this);
    }
}
