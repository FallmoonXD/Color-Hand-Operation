package replica;

import android.view.View;
import java.lang.reflect.Constructor;
import java.lang.reflect.Method;

/** COUI's TYPE_FRAMEWORK_CIRCLE_1 for a toolbar button. */
final class ToolbarEdge {
    private static final Api API = Api.load();
    private final View view;
    private boolean active, failed;

    ToolbarEdge(View view) { this.view = view; }

    void update(boolean enabled, boolean dark, float radius) {
        if (API == null || failed || (!enabled && !active)) return;
        try {
            if (enabled) API.cornerSetter.invoke(null, view, API.corner.newInstance(radius, 2f));
            setEdgeAndShadow(enabled, dark);
            active = enabled;
        } catch (ReflectiveOperationException | RuntimeException | LinkageError unavailable) {
            try { setEdgeAndShadow(false, dark); }
            catch (ReflectiveOperationException | RuntimeException | LinkageError ignored) { }
            active = false;
            failed = true;
        }
    }

    private void setEdgeAndShadow(boolean enabled, boolean dark) throws ReflectiveOperationException {
        float width = enabled ? view.getResources().getDisplayMetrics().density * 1.9f : 0;
        float alpha = enabled ? (dark ? .6f : .2f) : 0;
        float fadeIn = enabled ? (dark ? .1f : .15f) : 0;
        float fadeScale = enabled ? 1f : 0f;
        API.edgeSetter.invoke(null, view, API.edge.newInstance(2, width, alpha, 0f));
        API.shadowSetter.invoke(null, view, API.shadow.newInstance(2, fadeScale, fadeIn, fadeScale));
    }

    private static final class Api {
        final Constructor<?> corner, edge, shadow;
        final Method cornerSetter, edgeSetter, shadowSetter;
        static Api load() {
            try { return new Api(); }
            catch (ReflectiveOperationException | RuntimeException | LinkageError unavailable) { return null; }
        }
        private Api() throws ReflectiveOperationException {
            String pkg = "com.oplus.view.material.";
            Class<?> util = Class.forName(pkg + "OplusMaterialUtil");
            Class<?> corners = Class.forName(pkg + "OplusMaterialCornerParams");
            Class<?> edges = Class.forName(pkg + "OplusMaterialEdgeParams");
            Class<?> shadows = Class.forName(pkg + "OplusMaterialShadowParams");
            corner = corners.getConstructor(float.class, float.class);
            edge = edges.getConstructor(int.class, float.class, float.class, float.class);
            shadow = shadows.getConstructor(int.class, float.class, float.class, float.class);
            cornerSetter = util.getMethod("setCornerParams", View.class, corners);
            edgeSetter = util.getMethod("setEdgeParams", View.class, edges);
            shadowSetter = util.getMethod("setShadowParams", View.class, shadows);
        }
    }
}
