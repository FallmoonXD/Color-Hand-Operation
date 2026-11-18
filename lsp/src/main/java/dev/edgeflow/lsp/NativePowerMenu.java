package dev.edgeflow.lsp;

import android.app.KeyguardManager;
import android.content.Context;
import android.content.pm.PackageManager;
import android.os.IInterface;
import android.os.PowerManager;
import android.os.UserManager;
import android.util.Log;
import java.lang.reflect.Method;

/** The same window-policy request used by the firmware's SystemUI. Runs only in the system UI process. */
final class NativePowerMenu {
    private final IInterface windowManager;
    private final Method showGlobalActions;

    private NativePowerMenu(IInterface windowManager, Method showGlobalActions) {
        this.windowManager = windowManager;
        this.showGlobalActions = showGlobalActions;
    }

    static NativePowerMenu resolve(Context context) {
        if (context.checkSelfPermission("android.permission.INTERNAL_SYSTEM_WINDOW") != PackageManager.PERMISSION_GRANTED) return null;
        try {
            Class<?> contract = Class.forName("android.view.IWindowManager");
            Method show = contract.getMethod("showGlobalActions");
            Object service = Class.forName("android.view.WindowManagerGlobal").getMethod("getWindowManagerService").invoke(null);
            if (show.getReturnType() != void.class || !(service instanceof IInterface manager)
                    || !contract.isInstance(service) || !manager.asBinder().isBinderAlive()) return null;
            return new NativePowerMenu(manager, show);
        } catch (ReflectiveOperationException | RuntimeException | LinkageError unavailable) {
            return null;
        }
    }

    boolean show(Context context) {
        try {
            if (!context.getSystemService(PowerManager.class).isInteractive()
                    || context.getSystemService(KeyguardManager.class).isKeyguardLocked()
                    || !context.getSystemService(UserManager.class).isUserUnlocked()) return false;
            showGlobalActions.invoke(windowManager);
            return true;
        } catch (ReflectiveOperationException | RuntimeException | LinkageError failure) {
            Log.w("CHOPowerMenu", "System power menu unavailable", failure);
            return false;
        }
    }
}
