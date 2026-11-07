package dev.edgeflow;

import android.accessibilityservice.MagnificationConfig;

public final class MagnificationApi33Check {
    public static void main(String[] args) {
        MagnificationConfig zoom = MagnificationCompat.fullscreen(2f, 150f, 280f);
        if (zoom.getMode() != 1 || zoom.getCenterX() != 150 || zoom.getCenterY() != 280 || !MagnificationCompat.active(zoom))
            throw new AssertionError("API 33 fullscreen zoom");
        if (MagnificationCompat.active(null) || MagnificationCompat.active(MagnificationCompat.fullscreen(1f, 0, 0)))
            throw new AssertionError("API 33 idle/reset");
        System.out.println("PASS magnification linkage with the API 33 method surface");
    }
}
