package android.appwidget;
import android.content.Context;import java.util.*;
public class AppWidgetHost {
 public static final Set<Integer> live=new HashSet<>();public static int next=1000;public static int allocations;
 public AppWidgetHost(Context c,int host){if(host!=17672)throw new AssertionError("unexpected host");}
 public int allocateAppWidgetId(){int id=next++;live.add(id);allocations++;return id;}
 public void deleteAppWidgetId(int id){live.remove(id);AppWidgetManager.INSTANCE.providers.remove(id);}
}