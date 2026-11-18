package dev.edgeflow;
import android.content.*;
import android.database.Cursor;
import android.net.Uri;
import android.os.*;
import android.provider.Settings;
import java.util.*;
import org.json.*;

/** Same-signature read-only snapshot; updates only an already armed pilot. */
public final class GestureConfigProvider extends ContentProvider {
    private final Handler main=new Handler(Looper.getMainLooper());
    private SharedPreferences preferences;
    private final java.util.concurrent.ExecutorService sync=java.util.concurrent.Executors.newSingleThreadExecutor();
    private final Runnable publish=()->{
        Context c=getContext();
        if(c.checkSelfPermission("dev.edgeflow.lsp.pilot.CONTROL")!=android.content.pm.PackageManager.PERMISSION_GRANTED)return;
        String authority=BackendChoice.lsp(c)?"dev.edgeflow.lsp.persistent":"dev.edgeflow.lsp.pilot.persistent";
        if(c.getPackageManager().resolveContentProvider(authority,0)==null)return;
        String profile=snapshot();
        sync.execute(()->{
            // The source pushes while its settings are being edited. ColorOS can deny
            // a background companion's attempt to pull from this provider.
            try{c.getContentResolver().call(Uri.parse("content://"+authority),"sync",profile,null);}
            catch(RuntimeException unavailable){android.util.Log.w("CHOConfig","Persistent sync unavailable: "+unavailable.getClass().getSimpleName());}
            c.sendBroadcast(new Intent("dev.edgeflow.lsp.pilot.CONFIG_CHANGED").setPackage("com.android.systemui").putExtra("profile",profile).putExtra("module_package",authority.equals("dev.edgeflow.lsp.persistent")?"dev.edgeflow":"dev.edgeflow.lsp.pilot"));
        });
    };
    private final SharedPreferences.OnSharedPreferenceChangeListener changes=(p,key)->{
        if(key==null||included(key)){main.removeCallbacks(publish);main.postDelayed(publish,100);}
    };
    @Override public boolean onCreate(){preferences=getContext().getSharedPreferences("edgeflow",0);preferences.registerOnSharedPreferenceChangeListener(changes);return true;}
    private static boolean included(String key){
        return GestureMapping.isKey(key)||key.matches("(?:extra2?_)?(?:left|right)_(?:enabled|long_enabled|color|(?:landscape_)?handle_.*)")
            ||key.startsWith("handle_")||key.startsWith("short_angle_")||key.startsWith("long_angle_")
            ||Arrays.asList("schema","enabled","trigger_dp","long_trigger_dp","long_delay_ms","long_immediate","quick_action_short","quick_action_long","direction_angle_degrees","hide_landscape","landscape_enabled","hide_home","hide_notifications","hide_keyboard","hide_when_keyboard","disabled_apps","excluded_packages","quick_vibration","vibration","haptics","custom_vibration","vibration_duration_ms","vibration_strength").contains(key);
    }
    private String snapshot(){
        try{
            JSONObject values=new JSONObject();Map<String,?> all=preferences.getAll();
            for(String key:new TreeSet<>(all.keySet()))if(included(key)){
                Object value=all.get(key);if(key.equals("handle_opacity"))value=GestureAppearance.opacityPercent(value);
                if(value instanceof Set)value=new JSONArray(new TreeSet<>((Set<String>)value));values.put(key,value);
            }
            if(all.isEmpty())values.put("enabled",false);
            boolean lsp=BackendChoice.lsp(getContext());values.put("backend_lsp",lsp);
            String enabled=Settings.Secure.getString(getContext().getContentResolver(),Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES);
            boolean a11y=false;ComponentName own=new ComponentName(getContext(),GestureService.class);
            if(enabled!=null)for(String item:enabled.split(":"))if(own.equals(ComponentName.unflattenFromString(item)))a11y=true;
            return new JSONObject().put("version",1).put("accessibility_input",a11y&&!lsp&&GestureHandleConfig.flag(all,"enabled",true)).put("values",values).toString();
        }catch(JSONException error){throw new IllegalStateException(error);}
    }
    @Override public Bundle call(String method,String arg,Bundle extras){
        getContext().enforceCallingOrSelfPermission("dev.edgeflow.permission.READ_GESTURES","Gesture configuration is private");
        if(!"snapshot".equals(method))throw new IllegalArgumentException("Read-only snapshot endpoint");
        Bundle result=new Bundle();result.putString("profile",snapshot());return result;
    }
    @Override public Cursor query(Uri uri,String[] projection,String selection,String[] args,String order){throw new UnsupportedOperationException();}
    @Override public String getType(Uri uri){return "application/json";}
    @Override public Uri insert(Uri uri,ContentValues values){throw new UnsupportedOperationException();}
    @Override public int delete(Uri uri,String where,String[] args){throw new UnsupportedOperationException();}
    @Override public int update(Uri uri,ContentValues values,String where,String[] args){throw new UnsupportedOperationException();}
}
