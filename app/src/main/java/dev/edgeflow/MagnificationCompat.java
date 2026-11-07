package dev.edgeflow;

import android.accessibilityservice.MagnificationConfig;
import android.os.Build;

final class MagnificationCompat {
    static boolean active(MagnificationConfig config) {
        if (config == null) return false;
        if (Build.VERSION.SDK_INT >= 34) return config.isActivated();
        return config.getScale() > 1f;
    }

    static MagnificationConfig fullscreen(float scale, float x, float y) {
        MagnificationConfig.Builder builder = new MagnificationConfig.Builder()
                .setMode(MagnificationConfig.MAGNIFICATION_MODE_FULLSCREEN)
                .setScale(scale).setCenterX(x).setCenterY(y);
        if (Build.VERSION.SDK_INT >= 34) builder.setActivated(true);
        return builder.build();
    }

    private MagnificationCompat() {}
}
