package android.appwidget;
import android.content.*;import android.os.Bundle;import java.util.*;
public class AppWidgetManager {
 public static final String OPTION_APPWIDGET_HOST_CATEGORY="appWidgetCategory",OPTION_APPWIDGET_MIN_WIDTH="appWidgetMinWidth",OPTION_APPWIDGET_MIN_HEIGHT="appWidgetMinHeight",OPTION_APPWIDGET_MAX_WIDTH="appWidgetMaxWidth",OPTION_APPWIDGET_MAX_HEIGHT="appWidgetMaxHeight";
 public static final AppWidgetManager INSTANCE=new AppWidgetManager();public final Map<Integer,AppWidgetProviderInfo> providers=new HashMap<>();public final List<AppWidgetProviderInfo> installed=new ArrayList<>();public boolean allowBinding=true;
 public static AppWidgetManager getInstance(Context c){return INSTANCE;}public AppWidgetProviderInfo getAppWidgetInfo(int id){return providers.get(id);}public List<AppWidgetProviderInfo> getInstalledProviders(){return new ArrayList<>(installed);}public boolean bindAppWidgetIdIfAllowed(int id,ComponentName component,Bundle options){if(!allowBinding)return false;for(AppWidgetProviderInfo p:installed)if(p.provider.equals(component)){providers.put(id,p);return true;}return false;}
}