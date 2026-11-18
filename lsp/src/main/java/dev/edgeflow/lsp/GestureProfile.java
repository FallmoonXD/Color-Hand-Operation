package dev.edgeflow.lsp;
import dev.edgeflow.*;
import java.util.*;
import org.json.*;

/** Immutable copy of CHO input preferences. Never writes or migrates user data. */
final class GestureProfile {
    final Map<String,Object> values;
    final boolean accessibilityInput;
    final String revision;
    GestureProfile(Map<String,?> values,boolean a11y){this.values=Collections.unmodifiableMap(new HashMap<>(values));accessibilityInput=a11y;revision=Integer.toHexString(this.values.hashCode())+":"+a11y;}
    static GestureProfile parse(String raw)throws JSONException{
        if(raw==null||raw.length()>131072)throw new IllegalArgumentException("Invalid snapshot size");
        JSONObject root=new JSONObject(raw);if(root.getInt("version")!=1)throw new IllegalArgumentException("Snapshot version");
        JSONObject data=root.getJSONObject("values");if(data.length()>2048)throw new IllegalArgumentException("Too many preference fields");
        Map<String,Object> map=new HashMap<>();
        for(Iterator<String> it=data.keys();it.hasNext();){String key=it.next();Object value=data.get(key);
            if(key.length()>512)throw new IllegalArgumentException("Preference key too long");
            if(value instanceof JSONArray list){if(!key.equals("disabled_apps"))throw new IllegalArgumentException("Unexpected list");Set<String> set=new HashSet<>();for(int i=0;i<list.length();i++)set.add(list.getString(i));value=Collections.unmodifiableSet(set);}
            else if(value instanceof Number number){if(!Double.isFinite(number.doubleValue()))throw new IllegalArgumentException("Non-finite setting");}
            else if(value instanceof String s){if(s.length()>4096)throw new IllegalArgumentException("Setting too long");}
            else if(!(value instanceof Boolean))throw new IllegalArgumentException("Unsupported setting type");
            if((key.startsWith("action_")||key.startsWith("app_override/"))&&!(value instanceof String))throw new IllegalArgumentException("Action must be a string");
            map.put(key,value);
        }
        GestureProfile result=new GestureProfile(map,root.getBoolean("accessibility_input"));
        if(result.number("trigger_dp",39.43f)<=0||result.number("long_trigger_dp",87.43f)<=0||result.number("long_delay_ms",300)<0)throw new IllegalArgumentException("Invalid gesture threshold");
        return result;
    }
    boolean flag(String key,boolean fallback){return GestureHandleConfig.flag(values,key,fallback);}
    float number(String key,float fallback){return GestureHandleConfig.number(values,key,fallback);}
    boolean enabled(){return flag("enabled",true)&&!accessibilityInput;}
    boolean excluded(String pkg){
        Object disabled=values.get("disabled_apps");if(disabled instanceof Set<?> set&&set.contains(pkg))return true;
        Object text=values.get("excluded_packages");if(text instanceof String s)for(String item:s.split("[\\s,;]+"))if(!item.isEmpty()&&item.equals(pkg))return true;
        return false;
    }
    String action(String pkg,String handle,String direction,boolean longer){return GestureHandleConfig.resolveAction(values,pkg,handle,direction,longer);}
    boolean hideLandscape(){return flag("hide_landscape",false)||!flag("landscape_enabled",true);}
    boolean same(GestureProfile other){return other!=null&&accessibilityInput==other.accessibilityInput&&values.equals(other.values);}
    boolean sceneAllowed(String state){
        Set<String> flags=state==null?Set.of():new HashSet<>(Arrays.asList(state.split("\\|")));
        if(flag("hide_notifications",false)&&(state==null||flags.contains("notif_visible")||flags.contains("notif_expanded")||flags.contains("qs_visible")))return false;
        return !flag("hide_keyboard",flag("hide_when_keyboard",false))||(state!=null&&!flags.contains("ime_visible"));
    }
}
