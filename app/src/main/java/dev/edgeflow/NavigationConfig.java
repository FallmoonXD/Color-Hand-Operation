package dev.edgeflow;

import android.content.Context;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import org.json.JSONArray;
import org.json.JSONException;

/** Ordered one-to-five-button configuration, stored with the other exported preferences. */
public final class NavigationConfig {
    public static final String DEFAULT="[\"back\",\"home\",\"recents\"]";
    public static final String[] IDS,TITLES;
    static {
        ArrayList<String> ids=new ArrayList<>(),titles=new ArrayList<>();
        for(String id:ActionExecutor.IDS)if(ActionCatalog.isSelectable(id)&&!id.equals("none")&&!id.equals("navigation_panel")){ids.add(id);titles.add(ActionExecutor.title(id));}
        IDS=ids.toArray(new String[0]);TITLES=titles.toArray(new String[0]);
    }
    public static String validate(String raw)throws JSONException{
        if(raw==null||raw.length()>4096)throw new JSONException("导航键配置过长");
        JSONArray input=new JSONArray(raw);if(input.length()<1||input.length()>5)throw new JSONException("导航键数量必须为 1 到 5 个");
        // Validation accepts existing legacy assignments even when they are no longer selectable.
        HashSet<String> valid=new HashSet<>(Arrays.asList(ActionExecutor.IDS)),seen=new HashSet<>();valid.remove("none");valid.remove("navigation_panel");JSONArray output=new JSONArray();
        for(int i=0;i<input.length();i++){Object item=input.get(i);if(!(item instanceof String)||!valid.contains(item))throw new JSONException("不支持的导航动作："+item);String id=(String)item;if(!seen.add(id))throw new JSONException("导航动作不能重复："+id);output.put(id);}
        return output.toString();
    }
    public static String[] load(Context context){
        try{JSONArray values=new JSONArray(validate(context.getSharedPreferences("edgeflow",0).getString("navigation_actions",DEFAULT)));String[] ids=new String[values.length()];for(int i=0;i<ids.length;i++)ids[i]=values.getString(i);return ids;}
        catch(Exception e){android.util.Log.w("EdgeNavigation","Invalid saved navigation configuration; using defaults",e);return new String[]{"back","home","recents"};}
    }
    public static float size(Context context){Object raw=context.getSharedPreferences("edgeflow",0).getAll().get("navigation_button_size_dp");float size=raw instanceof Number?((Number)raw).floatValue():58f;return Float.isFinite(size)?Math.max(40,Math.min(76,size)):58;}
    private NavigationConfig(){}
}
