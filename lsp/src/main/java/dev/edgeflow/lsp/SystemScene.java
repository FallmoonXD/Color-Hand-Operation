package dev.edgeflow.lsp;
import android.app.ActivityManager;
import android.content.*;
import android.content.pm.PackageManager;
import android.view.*;
import android.graphics.Insets;
import android.provider.Settings;
import dev.edgeflow.GestureHandleConfig;
import java.util.List;

final class SystemScene {
    static volatile String flags;
    private static final java.util.Set<Runnable> listeners=new java.util.concurrent.CopyOnWriteArraySet<>();
    static void listen(Runnable listener){listeners.add(listener);}
    static void unlisten(Runnable listener){listeners.remove(listener);}
    static void updateFlags(String next){
        if(java.util.Objects.equals(flags,next))return;flags=next;
        for(Runnable listener:listeners)try{listener.run();}catch(RuntimeException ignored){}
    }
    record Focus(String key,String pkg,boolean trialPage){}
    private final Context context;
    private final String home;
    private boolean legacyInputSuppressed;
    void profile(GestureProfile profile){legacyInputSuppressed=BackendIdentity.integrated()&&profile!=null&&profile.flag("backend_lsp",false);}
    SystemScene(Context context){this.context=context;
        var resolved=context.getPackageManager().resolveActivity(new Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME),PackageManager.MATCH_DEFAULT_ONLY);
        home=resolved==null?"":resolved.activityInfo.packageName;
    }
    Focus focus(){
        List<ActivityManager.RunningTaskInfo> list=context.getSystemService(ActivityManager.class).getRunningTasks(1);
        if(list.isEmpty()||list.get(0).topActivity==null)return new Focus("","",false);
        var task=list.get(0);ComponentName top=task.topActivity;
        boolean trialPage=BackendIdentity.APP.equals(top.getPackageName())&&"dev.edgeflow.GestureTrialActivity".equals(top.getClassName());
        return new Focus(task.taskId+":"+top.flattenToString(),top.getPackageName(),trialPage);
    }
    boolean accessibilityInput(){
        if(legacyInputSuppressed)return false;
        String enabled=Settings.Secure.getString(context.getContentResolver(),Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES);
        if(enabled==null)return false;
        ComponentName own=new ComponentName("dev.edgeflow","dev.edgeflow.GestureService");
        for(String item:enabled.split(":"))if(own.equals(ComponentName.unflattenFromString(item)))return true;
        return false;
    }
    boolean allowed(GestureProfile profile,Focus focus){
        if(focus.key().isEmpty()||profile.excluded(focus.pkg()))return false;
        if(profile.flag("hide_home",false)&&home.equals(focus.pkg()))return false;
        return profile.sceneAllowed(flags);
    }
    GestureHandleConfig.Area area(){
        WindowMetrics metrics=context.getSystemService(WindowManager.class).getMaximumWindowMetrics();
        Insets inset=metrics.getWindowInsets().getInsetsIgnoringVisibility(WindowInsets.Type.systemBars()|WindowInsets.Type.displayCutout());
        return GestureHandleConfig.safeArea(metrics.getBounds().width(),metrics.getBounds().height(),inset.left,inset.top,inset.right,inset.bottom);
    }
}
