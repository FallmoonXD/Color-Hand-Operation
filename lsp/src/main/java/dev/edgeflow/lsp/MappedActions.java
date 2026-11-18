package dev.edgeflow.lsp;
import android.content.*;
import android.media.AudioManager;
import android.view.KeyEvent;
import java.util.*;

final class MappedActions {
    static final Map<String,Integer> KEYS=Map.ofEntries(Map.entry("back",4),Map.entry("home",3),Map.entry("recents",187),Map.entry("search",84),Map.entry("menu",82),Map.entry("browser_forward",125));
    static final Map<String,Integer> MEDIA=Map.of("media_previous",88,"media_play_pause",85,"media_next",87);
    static boolean supported(String action,NativeInputApi api){return api.actions.nativeAction(action);}
    static boolean execute(Context context,NativeInputApi api,String action)throws ReflectiveOperationException{
        if(action.equals("none"))return true;
        if(dev.edgeflow.BackendActions.POWER_MENU.equals(action))return api.powerMenu!=null&&api.powerMenu.show(context);
        if(KEYS.containsKey(action))return api.key(KEYS.get(action));
        AudioManager audio=context.getSystemService(AudioManager.class);
        if(MEDIA.containsKey(action)){int key=MEDIA.get(action);audio.dispatchMediaKeyEvent(new KeyEvent(0,key));audio.dispatchMediaKeyEvent(new KeyEvent(1,key));return true;}
        if(action.startsWith("volume_")){audio.adjustStreamVolume(AudioManager.STREAM_MUSIC,action.equals("volume_up")?1:action.equals("volume_down")?-1:AudioManager.ADJUST_TOGGLE_MUTE,AudioManager.FLAG_SHOW_UI);return true;}
        if(action.equals("notifications")||action.equals("quick_settings")){Object bar=context.getSystemService(Class.forName("android.app.StatusBarManager"));bar.getClass().getMethod(action.equals("notifications")?"expandNotificationsPanel":"expandSettingsPanel").invoke(bar);return true;}
        if(action.equals("settings")){context.startActivity(new Intent(android.provider.Settings.ACTION_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));return true;}
        if(action.startsWith("app:")){
            String target=action.substring(4);ComponentName component=ComponentName.unflattenFromString(target);
            Intent launch=component==null?context.getPackageManager().getLaunchIntentForPackage(target):new Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER).setComponent(component);
            if(launch==null)throw new ActivityNotFoundException("Application unavailable");
            context.startActivity(launch.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));return true;
        }
        return false;
    }
}
