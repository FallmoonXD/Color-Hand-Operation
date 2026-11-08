package replica;

import android.accessibilityservice.AccessibilityService;
import android.content.Context;
import android.content.pm.PackageManager;
import android.content.res.Resources;
import android.graphics.Rect;
import android.view.accessibility.AccessibilityNodeInfo;
import android.view.accessibility.AccessibilityWindowInfo;

import java.util.List;

/** Locate the SystemUI volume window exposed to the existing accessibility service. */
final class SystemVolumeWindow {
    private static final String SYSTEM_UI = "com.android.systemui";
    private final Rect bounds = new Rect();
    private final String prefix, suffix;
    private int id = -1;

    SystemVolumeWindow(Context context) {
        String sample = "";
        try {
            Resources res = context.getPackageManager().getResourcesForApplication(SYSTEM_UI);
            int title = res.getIdentifier("volume_dialog_title", "string", SYSTEM_UI);
            if (title != 0) sample = res.getString(title, "\ufffc");
        } catch (PackageManager.NameNotFoundException | RuntimeException ignored) {}
        int placeholder = sample.indexOf('\ufffc');
        prefix = placeholder < 0 ? "" : sample.substring(0, placeholder);
        suffix = placeholder < 0 ? "" : sample.substring(placeholder + 1);
    }

    boolean tracked() { return id != -1; }

    /** Returns current screen bounds, including the region still on screen during dismissal. */
    Rect find(AccessibilityService service, int display) {
        try {
            return findWindow(service, display);
        } catch (SecurityException | IllegalStateException unavailable) {
            return null;
        }
    }

    private Rect findWindow(AccessibilityService service, int display) {
        List<AccessibilityWindowInfo> windows = service.getWindows();
        for (AccessibilityWindowInfo w : windows) {
            if (tracked() && w.getId() == id && eligible(w, display)) return bounds;
        }
        // Some ROMs expose the localized SystemUI title but withhold the view tree.
        // Prefer that title before requesting roots of other system windows.
        for (AccessibilityWindowInfo w : windows) {
            if (!eligible(w, display) || !matchesTitle(w.getTitle())) continue;
            AccessibilityNodeInfo root = w.getRoot();
            try {
                if (root == null || android.text.TextUtils.equals(SYSTEM_UI, root.getPackageName())) {
                    id = w.getId();
                    return bounds;
                }
            } finally { if (root != null) root.recycle(); }
        }
        for (AccessibilityWindowInfo w : windows) {
            if (!eligible(w, display)) continue;
            AccessibilityNodeInfo root = w.getRoot();
            if (root == null) continue;
            try {
                if (android.text.TextUtils.equals(SYSTEM_UI, root.getPackageName()) && hasVolumeControls(root)) {
                    id = w.getId();
                    return bounds;
                }
            } finally { root.recycle(); }
        }
        return null;
    }

    private boolean matchesTitle(CharSequence label) {
        if (label == null || (prefix.isEmpty() && suffix.isEmpty())) return false;
        String title = label.toString();
        return title.length() >= prefix.length() + suffix.length()
                && title.startsWith(prefix) && title.endsWith(suffix);
    }

    private boolean eligible(AccessibilityWindowInfo w,int display) {
        if (w.getType() != AccessibilityWindowInfo.TYPE_SYSTEM || w.getDisplayId() != display) return false;
        w.getBoundsInScreen(bounds);
        return !bounds.isEmpty();
    }

    private static boolean hasVolumeControls(AccessibilityNodeInfo root) {
        for (String name : new String[]{"volume_dialog", "volume_dialog_rows", "volume_recyclerview"}) {
            List<AccessibilityNodeInfo> nodes = root.findAccessibilityNodeInfosByViewId(SYSTEM_UI + ":id/" + name);
            boolean found = !nodes.isEmpty();
            for (AccessibilityNodeInfo node : nodes) node.recycle();
            if (found) return true;
        }
        return false;
    }
}
