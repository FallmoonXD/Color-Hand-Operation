package dev.edgeflow;

import android.accessibilityservice.AccessibilityService;
import android.app.KeyguardManager;
import android.content.ComponentName;
import android.content.Intent;
import android.content.pm.ResolveInfo;
import android.os.PowerManager;
import android.view.accessibility.AccessibilityNodeInfo;
import android.view.accessibility.AccessibilityWindowInfo;

/** Resolves a fresh target for the close-app action. Never uses recent-app history. */
public final class ForegroundAppCloser {
    public static String target(AccessibilityService service) {
        if (service == null || service != GestureService.instance()) return null;
        try {
            if (!service.getSystemService(PowerManager.class).isInteractive()
                    || service.getSystemService(KeyguardManager.class).isKeyguardLocked()) return null;
            String candidate = null;
            for (AccessibilityWindowInfo window : service.getWindows()) {
                if (!window.isFocused()) continue;
                if (candidate != null || window.getType() != AccessibilityWindowInfo.TYPE_APPLICATION) return null;
                AccessibilityNodeInfo root = window.getRoot();
                if (root == null) return null;
                try {
                    if (root.getPackageName() == null) return null;
                    candidate = root.getPackageName().toString();
                } finally { root.recycle(); }
            }
            if (!eligible(candidate, service.getPackageName())) return null;
            Intent home = new Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME);
            for (ResolveInfo info : service.getPackageManager().queryIntentActivities(home, 0)) {
                if (info.activityInfo != null && candidate.equals(info.activityInfo.packageName)) return null;
            }
            return candidate;
        } catch (RuntimeException unavailable) {
            return null;
        }
    }

    private static boolean eligible(String pkg, String own) {
        return pkg != null && pkg.matches("[A-Za-z0-9_]+(\\.[A-Za-z0-9_]+)+")
                && !pkg.equals(own) && !pkg.equals("com.android.systemui");
    }

    static boolean close(AccessibilityService service, String expected) throws Exception {
        if (expected == null || !expected.equals(target(service))) return false;
        String apk = service.getApplicationInfo().sourceDir.replace("'", "'\\''");
        int user = android.os.Process.myUid() / 100000;
        String command = "CLASSPATH='" + apk + "' exec app_process /system/bin "
                + ForegroundAppCloser.class.getName() + " " + expected + " " + user;
        return PendingRootCommand.forWaitingProgram(command).run(() -> expected.equals(target(service)));
    }

    /** Root-side last check also identifies the task's user and excludes home/recents. */
    public static void main(String[] args) {
        try {
            stopFocusedTask(args);
        } catch (ReflectiveOperationException | java.io.IOException | RuntimeException | LinkageError failure) {
            System.err.println("Close target unavailable: " + failure);
            System.exit(1);
        }
    }

    private static void stopFocusedTask(String[] args) throws ReflectiveOperationException, java.io.IOException {
        if (android.os.Process.myUid() != 0 || args.length != 2 || !eligible(args[0], "dev.edgeflow"))
            throw new IllegalArgumentException("Invalid close target");
        int user = Integer.parseInt(args[1]);
        Object binder = Class.forName("android.os.ServiceManager").getMethod("getService", String.class).invoke(null, "window");
        Object wm = Class.forName("android.view.IWindowManager$Stub").getMethod("asInterface", android.os.IBinder.class).invoke(null, binder);
        Object atm = Class.forName("android.app.ActivityTaskManager").getMethod("getService").invoke(null);
        Object am = Class.forName("android.app.ActivityManager").getMethod("getService").invoke(null);
        if (!PendingRootCommand.awaitApproval()) return;
        if (Boolean.TRUE.equals(Class.forName("android.view.IWindowManager").getMethod("isKeyguardLocked").invoke(wm)))
            throw new IllegalStateException("Screen is locked");
        Object task = Class.forName("android.app.IActivityTaskManager").getMethod("getFocusedRootTaskInfo").invoke(atm);
        if (task == null) throw new IllegalStateException("No focused task");
        Class<?> type = task.getClass();
        ComponentName top = (ComponentName) type.getField("topActivity").get(task);
        if (top == null || !args[0].equals(top.getPackageName()) || type.getField("userId").getInt(task) != user
                || (Integer) type.getMethod("getActivityType").invoke(task) != 1)
            throw new IllegalStateException("Focused task changed");
        int[] users = (int[]) type.getField("childTaskUserIds").get(task);
        if (users != null) for (int childUser : users) if (childUser != user)
            throw new IllegalStateException("Ambiguous task user");
        Class.forName("android.app.IActivityManager").getMethod("forceStopPackage", String.class, int.class)
                .invoke(am, args[0], user);
    }

    private ForegroundAppCloser() {}
}
