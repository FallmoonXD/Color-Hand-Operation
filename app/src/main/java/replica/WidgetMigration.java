package replica;

import android.appwidget.AppWidgetHost;
import android.appwidget.AppWidgetManager;
import android.appwidget.AppWidgetProviderInfo;
import android.content.ComponentName;
import android.content.Context;
import android.content.SharedPreferences;
import android.os.Bundle;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import java.util.ArrayList;
import java.util.HashMap;

/** Imports layout geometry while allocating widget IDs owned by this application.
 * The caller supplies provider components discovered from the old host. Neither
 * its files nor its widget IDs are touched. Binding permission is granted outside
 * this helper; a denied bind is reported rather than opening UI unexpectedly.
 */
public final class WidgetMigration {
    public static final int HOST_ID = 17672;

    public static synchronized JSONObject migrate(Context context, JSONObject input) throws JSONException {
        JSONObject report = new JSONObject();
        JSONArray imported = new JSONArray(), failed = new JSONArray(), needsSetup = new JSONArray();
        report.put("imported", imported).put("failed", failed).put("needsConfiguration", needsSetup);
        if (input.optInt("version") != 1) return report.put("status", "unsupported_version");
        JSONArray source = input.optJSONArray("items");
        if (source == null) return report.put("status", "missing_items");
        int pages = input.optInt("pages", 1), page = input.optInt("page", 0), height = input.optInt("height", 330);
        if (pages < 1 || pages > 100 || page < 0 || page >= pages || height < 100 || height > 2000 || source.length() > 1600)
            return report.put("status", "invalid_layout");
        SharedPreferences prefs = context.getSharedPreferences("replica_widgets", Context.MODE_PRIVATE);
        java.util.Map<String, ?> previousPreferences = new HashMap<>(prefs.getAll());
        String fingerprint = fingerprint(input.toString());
        if (fingerprint.equals(prefs.getString("migration_source", ""))) {
            String prior = prefs.getString("migration_report", "");
            if (!prior.isEmpty()) return new JSONObject(prior).put("repeatedRequest", true);
        }
        try {
            if (new JSONArray(prefs.getString("layout_v3", "[]")).length() != 0)
                return report.put("status", "destination_not_empty");
        } catch (JSONException invalid) { return report.put("status", "invalid_destination_layout"); }

        AppWidgetHost host = new AppWidgetHost(context.getApplicationContext(), HOST_ID);
        AppWidgetManager manager = AppWidgetManager.getInstance(context);
        HashMap<ComponentName, AppWidgetProviderInfo> available = new HashMap<>();
        for (AppWidgetProviderInfo info : manager.getInstalledProviders()) available.put(info.provider, info);
        JSONArray layout = new JSONArray();
        ArrayList<Integer> allocated = new ArrayList<>();
        boolean persisted = false;
        try {
            for (int i = 0; i < source.length(); i++) {
                JSONObject old = source.optJSONObject(i);
                int id = -1;
                JSONObject outcome = new JSONObject().put("index", i);
                try {
                    if (old == null) throw new IllegalArgumentException("invalid_entry");
                    outcome.put("sourceId", old.optInt("sourceId", -1));
                    String component = old.optString("component", "");
                    outcome.put("component", component);
                    ComponentName provider = ComponentName.unflattenFromString(component);
                    AppWidgetProviderInfo info = available.get(provider);
                    if (info == null) throw new IllegalArgumentException("provider_unavailable");
                    int p = old.getInt("page"), x = old.getInt("x"), y = old.getInt("y");
                    int w = old.getInt("w"), h = old.getInt("h");
                    if (p < 0 || p >= pages || x < 0 || y < 0 || w < 1 || h < 1 || w > 4 || h > 4 || x > 4-w || y > 4-h)
                        throw new IllegalArgumentException("invalid_bounds");
                    for (int n = 0; n < layout.length(); n++) {
                        JSONObject other = layout.getJSONObject(n);
                        if (p == other.getInt("page") && x < other.getInt("x")+other.getInt("w") && x+w > other.getInt("x")
                                && y < other.getInt("y")+other.getInt("h") && y+h > other.getInt("y"))
                            throw new IllegalArgumentException("overlapping_widget");
                    }
                    Bundle options = options(old.optJSONObject("options"));
                    id = host.allocateAppWidgetId();
                    if (!manager.bindAppWidgetIdIfAllowed(id, provider, options))
                        throw new IllegalStateException("binding_permission_required");
                    JSONObject fresh = new JSONObject().put("id", id).put("page", p).put("x", x).put("y", y).put("w", w).put("h", h)
                            .put("provider", provider.flattenToString());
                    if (old.has("color")) fresh.put("color", (int) old.getLong("color"));
                    layout.put(fresh); allocated.add(id);
                    outcome.put("appWidgetId", id).put("hasConfigurationActivity", info.configure != null);
                    imported.put(outcome);
                    if (info.configure != null) needsSetup.put(new JSONObject().put("appWidgetId", id)
                            .put("component", component).put("configure", info.configure.flattenToString())
                            .put("reason", "provider_specific_settings_are_not_transferable_between_widget_ids"));
                } catch (Exception error) {
                    if (id >= 0 && !allocated.contains(id)) try { host.deleteAppWidgetId(id); } catch (Exception ignored) {}
                    outcome.put("reason", error.getMessage() == null ? error.getClass().getSimpleName() : error.getMessage());
                    failed.put(outcome);
                }
            }
            report.put("importedCount", imported.length()).put("failedCount", failed.length())
                    .put("configurationCount", needsSetup.length());
            if (source.length() > 0 && imported.length() == 0) return report.put("status", "nothing_imported");
            report.put("status", failed.length() == 0 ? "imported" : "partial");
            persisted = prefs.edit().putString("layout_v3", layout.toString()).putInt("pages", pages).putInt("page", page)
                    .putInt("height_v4", height).putBoolean("loop", input.optBoolean("loop", false))
                    .putString("migration_source", fingerprint).putString("migration_report", report.toString()).commit();
            if (!persisted) {
                // commit() updates memory before disk I/O; restore every changed value as well.
                boolean restored = restore(prefs, previousPreferences);
                for (int i = 0; i < imported.length(); i++)
                    failed.put(imported.getJSONObject(i).put("reason", "persistence_failed"));
                report.put("status", "persistence_failed").put("rolledBack", true).put("rollbackPersisted", restored)
                        .put("attemptedImportedCount", imported.length()).put("importedCount", 0).put("configurationCount", 0)
                        .put("failedCount", failed.length()).put("imported", new JSONArray()).put("needsConfiguration", new JSONArray());
            }
            return report;
        } finally {
            if (!persisted) for (Integer id : allocated) try { host.deleteAppWidgetId(id); } catch (Exception ignored) {}
        }
    }

    private static boolean restore(SharedPreferences prefs, java.util.Map<String, ?> values) {
        SharedPreferences.Editor editor = prefs.edit().clear();
        for (java.util.Map.Entry<String, ?> entry : values.entrySet()) {
            String key = entry.getKey(); Object value = entry.getValue();
            if (value instanceof String) editor.putString(key, (String) value);
            else if (value instanceof Integer) editor.putInt(key, (Integer) value);
            else if (value instanceof Long) editor.putLong(key, (Long) value);
            else if (value instanceof Float) editor.putFloat(key, (Float) value);
            else if (value instanceof Boolean) editor.putBoolean(key, (Boolean) value);
            else if (value instanceof java.util.Set) {
                java.util.HashSet<String> copy = new java.util.HashSet<>();
                for (Object item : (java.util.Set<?>) value) copy.add((String) item);
                editor.putStringSet(key, copy);
            }
        }
        return editor.commit();
    }

    private static Bundle options(JSONObject old) {
        Bundle result = new Bundle();
        result.putInt(AppWidgetManager.OPTION_APPWIDGET_HOST_CATEGORY, AppWidgetProviderInfo.WIDGET_CATEGORY_HOME_SCREEN);
        if (old == null) return result;
        String[] dimensions = {AppWidgetManager.OPTION_APPWIDGET_MIN_WIDTH, AppWidgetManager.OPTION_APPWIDGET_MIN_HEIGHT,
                AppWidgetManager.OPTION_APPWIDGET_MAX_WIDTH, AppWidgetManager.OPTION_APPWIDGET_MAX_HEIGHT};
        for (String key : dimensions) if (old.has(key)) {
            int value = old.optInt(key, -1);
            if (value >= 0 && value <= 10000) result.putInt(key, value);
        }
        return result;
    }

    private static String fingerprint(String value) {
        try {
            byte[] bytes = java.security.MessageDigest.getInstance("SHA-256").digest(value.getBytes(java.nio.charset.StandardCharsets.UTF_8));
            StringBuilder out = new StringBuilder();for (byte b : bytes) out.append(String.format(java.util.Locale.ROOT, "%02x", b & 255));return out.toString();
        } catch (java.security.NoSuchAlgorithmException impossible) { throw new AssertionError(impossible); }
    }
    private WidgetMigration() {}
}
