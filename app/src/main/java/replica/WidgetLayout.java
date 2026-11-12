package replica;

import android.appwidget.AppWidgetManager;
import android.appwidget.AppWidgetProviderInfo;
import android.content.Context;
import android.content.SharedPreferences;
import android.graphics.Point;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

/** Grid preferences and atomic reflow. Widget bindings belong to the existing host. */
public final class WidgetLayout {
    public static int columns(SharedPreferences prefs) { return count(prefs, "columns"); }
    public static int rows(SharedPreferences prefs) { return count(prefs, "rows"); }
    private static int count(SharedPreferences prefs, String key) {
        int value = prefs.getInt(key, 4);
        return value >= 2 && value <= 6 ? value : 4;
    }

    static Point board(Context context, int columns, int rows) {
        Point screen = PanelGeometry.screen(context);
        int footer = new LauncherWidgetStyle(context).dim("workspace_page_indicator_height", 28);
        int width = Math.round(screen.x * .936f) - PanelStyle.dp(context, 10);
        int height = screen.y - PanelGeometry.inset(context, "status_bar_height")
                - PanelGeometry.inset(context, "navigation_bar_height") - footer - PanelStyle.dp(context, 30);
        int cell = Math.max(1, Math.min(width / columns, height / rows));
        return new Point(cell * columns, cell * rows);
    }

    private static int span(int pixels, int side, int count) {
        return Math.max(1, (int) Math.ceil((double) pixels * count / Math.max(1, side)));
    }

    static WidgetGrid.Cell initialSize(AppWidgetProviderInfo info, int width, int height, int columns, int rows, int padding) {
        int w = Math.max(info.targetCellWidth, span(info.minWidth + padding, width, columns));
        int h = Math.max(info.targetCellHeight, span(info.minHeight + padding, height, rows));
        return new WidgetGrid.Cell(0, 0, Math.min(columns, w), Math.min(rows, h));
    }

    /** Keep old positions where possible; move overflow to subsequent pages before committing. */
    public static synchronized void apply(Context context, int columns, int rows) throws Exception {
        if (!WidgetGrid.dimensions(columns, rows)) throw new IllegalArgumentException("布局超出范围");
        SharedPreferences prefs = context.getSharedPreferences("replica_widgets", Context.MODE_PRIVATE);
        if (columns == columns(prefs) && rows == rows(prefs)) return;
        Map<String, ?> before = new HashMap<>(prefs.getAll());
        JSONArray items = new JSONArray(prefs.getString("layout_v3", "[]"));
        Point board = board(context, columns, rows);
        Point previousBoard = board(context, columns(prefs), rows(prefs));
        double widthRatio = (double) previousBoard.x / columns(prefs) * columns / board.x;
        double heightRatio = (double) previousBoard.y / rows(prefs) * rows / board.y;
        int padding = PanelStyle.dp(context, 16);
        AppWidgetManager manager = AppWidgetManager.getInstance(context);
        Map<Integer, Long> occupied = new HashMap<>();
        ArrayList<JSONObject> displaced = new ArrayList<>();
        int pages = Math.max(1, prefs.getInt("pages", 1));
        for (int i = 0; i < items.length(); i++) {
            JSONObject item = items.getJSONObject(i);
            AppWidgetProviderInfo info = manager.getAppWidgetInfo(item.getInt("id"));
            int minW = 1, minH = 1;
            if (info != null) {
                int width = (info.resizeMode & AppWidgetProviderInfo.RESIZE_HORIZONTAL) != 0 && info.minResizeWidth > 0
                        ? info.minResizeWidth : info.minWidth;
                int height = (info.resizeMode & AppWidgetProviderInfo.RESIZE_VERTICAL) != 0 && info.minResizeHeight > 0
                        ? info.minResizeHeight : info.minHeight;
                minW = Math.min(columns, span(width + padding, board.x, columns));
                minH = Math.min(rows, span(height + padding, board.y, rows));
            }
            int w = Math.max(minW, Math.min(columns, (int) Math.round(item.getInt("w") * widthRatio)));
            int h = Math.max(minH, Math.min(rows, (int) Math.round(item.getInt("h") * heightRatio)));
            item.put("w", w).put("h", h);
            WidgetGrid.Cell cell = cell(item);
            int page = item.getInt("page");
            if (WidgetGrid.valid(cell, columns, rows) && free(occupied, page, cell, columns))
                occupy(occupied, page, cell, columns);
            else displaced.add(item);
            pages = Math.max(pages, page + 1);
        }
        for (JSONObject item : displaced) {
            int page = item.getInt("page");
            boolean placed = false;
            for (; page <= pages && !placed; page++) {
                for (int y = 0; y <= rows - item.getInt("h") && !placed; y++) {
                    for (int x = 0; x <= columns - item.getInt("w"); x++) {
                        WidgetGrid.Cell cell = new WidgetGrid.Cell(x, y, item.getInt("w"), item.getInt("h"));
                        if (free(occupied, page, cell, columns)) {
                            item.put("page", page).put("x", x).put("y", y);
                            occupy(occupied, page, cell, columns);
                            pages = Math.max(pages, page + 1);
                            placed = true;
                            break;
                        }
                    }
                }
            }
            if (!placed) throw new JSONException("无法调整小组件位置");
        }
        SharedPreferences.Editor edit = prefs.edit().putString("layout_v3", items.toString()).putInt("pages", pages);
        if (columns == 4) edit.remove("columns"); else edit.putInt("columns", columns);
        if (rows == 4) edit.remove("rows"); else edit.putInt("rows", rows);
        if (!edit.commit()) {
            java.io.IOException failure = new java.io.IOException("布局保存失败");
            if (!WidgetMigration.restore(prefs, before))
                failure.addSuppressed(new java.io.IOException("旧布局恢复失败"));
            throw failure;
        }
    }

    private static WidgetGrid.Cell cell(JSONObject item) throws JSONException {
        return new WidgetGrid.Cell(item.getInt("x"), item.getInt("y"), item.getInt("w"), item.getInt("h"));
    }
    private static boolean free(Map<Integer, Long> occupied, int page, WidgetGrid.Cell cell, int columns) {
        return (occupied.getOrDefault(page, 0L) & WidgetDrop.mask(cell, columns)) == 0;
    }
    private static void occupy(Map<Integer, Long> occupied, int page, WidgetGrid.Cell cell, int columns) {
        occupied.put(page, occupied.getOrDefault(page, 0L) | WidgetDrop.mask(cell, columns));
    }
    private WidgetLayout() {}
}
