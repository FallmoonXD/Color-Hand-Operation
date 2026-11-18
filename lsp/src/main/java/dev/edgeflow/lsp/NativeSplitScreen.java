package dev.edgeflow.lsp;

import android.app.ActivityManager;
import android.app.KeyguardManager;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.PowerManager;
import android.os.UserManager;
import android.util.Log;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.List;

/** Uses the stock task-specific entry on the PKR110 firmware whose framework and services were inspected. */
final class NativeSplitScreen {
    private static final String VERIFIED_BUILD="OnePlus/PKR110/OP60EBL1:17/AP3A.240617.008/V.33b0c14-cf643a-d39add:user/release-keys";
    private final Object manager, canvas;
    private final Method topState, componentState, splitActive, choosingPartner, enter, windowingMode, activityType;
    private final Field display, user;
    private final int supported;

    private NativeSplitScreen(Class<?> api,Object manager)throws ReflectiveOperationException{
        this.manager=manager;
        topState=api.getMethod("getTopAppSplitScreenState");
        componentState=api.getMethod("getSplitScreenState",Intent.class);
        Class<?> flexible=Class.forName("com.oplus.flexiblewindow.FlexibleWindowManager");
        canvas=flexible.cast(flexible.getMethod("getInstance").invoke(null));
        splitActive=flexible.getMethod("isInPocketStudio",int.class);
        choosingPartner=flexible.getMethod("isMinimizedPocketStudio",int.class);
        enter=api.getMethod("splitScreenForRecentTasks",int.class);
        supported=api.getField("STATE_SUPPORT").getInt(null);
        Class<?> task=ActivityManager.RunningTaskInfo.class;
        windowingMode=task.getMethod("getWindowingMode");
        activityType=task.getMethod("getActivityType");
        display=task.getField("displayId");
        user=task.getField("userId");
        if(canvas==null||topState.getReturnType()!=int.class||componentState.getReturnType()!=int.class||splitActive.getReturnType()!=boolean.class
                ||choosingPartner.getReturnType()!=boolean.class||enter.getReturnType()!=boolean.class)
            throw new NoSuchMethodException("Unexpected split-screen interface");
    }

    static NativeSplitScreen resolve(Context context){
        if(!VERIFIED_BUILD.equals(Build.FINGERPRINT)
                ||context.checkSelfPermission("oplus.permission.OPLUS_COMPONENT_SAFE")!=PackageManager.PERMISSION_GRANTED)return null;
        try{
            Method deviceSupport=Class.forName("android.app.ActivityTaskManager").getMethod("supportsSplitScreenMultiWindow",Context.class);
            if(!Boolean.TRUE.equals(deviceSupport.invoke(null,context)))return null;
            Class<?> api=Class.forName("com.oplus.splitscreen.OplusSplitScreenManager");
            Object manager=api.getMethod("getInstance").invoke(null);
            return api.isInstance(manager)?new NativeSplitScreen(api,manager):null;
        }catch(ReflectiveOperationException|RuntimeException|LinkageError unavailable){return null;}
    }

    private static ActivityManager.RunningTaskInfo foreground(Context context,String expected){
        List<ActivityManager.RunningTaskInfo> tasks=context.getSystemService(ActivityManager.class).getRunningTasks(1);
        if(tasks.isEmpty())return null;
        ActivityManager.RunningTaskInfo task=tasks.get(0);
        return task.taskId>0&&task.topActivity!=null&&(task.taskId+":"+task.topActivity.flattenToString()).equals(expected)?task:null;
    }

    private static boolean unlocked(Context context){
        return context.getSystemService(PowerManager.class).isInteractive()
                &&context.getSystemService(UserManager.class).isUserUnlocked()
                &&!context.getSystemService(KeyguardManager.class).isKeyguardLocked();
    }

    /** Null means the system accepted the request. Only the original, eligible task can be submitted. */
    String enter(Context context,String expected){
        try{
            if(!unlocked(context))return "屏幕已锁定";
            ActivityManager.RunningTaskInfo task=foreground(context,expected);
            if(task==null)return "前台任务已变化";
            if(user.getInt(task)!=0||display.getInt(task)!=0)return "当前用户或显示屏不支持分屏";
            if(Boolean.TRUE.equals(splitActive.invoke(canvas,0)))return "已处于分屏";
            if(Boolean.TRUE.equals(choosingPartner.invoke(canvas,0)))return "请先选择另一个分屏应用";
            // Standard activity in a fullscreen task. Home, recents and floating windows stay in place.
            if(!Integer.valueOf(1).equals(activityType.invoke(task))||!Integer.valueOf(1).equals(windowingMode.invoke(task))
                    ||!Integer.valueOf(supported).equals(topState.invoke(manager))
                    ||!Integer.valueOf(supported).equals(componentState.invoke(manager,new Intent().setComponent(task.topActivity))))
                return "当前应用或系统状态不支持分屏";
            if(!unlocked(context)||foreground(context,expected)==null)return "前台任务已变化";
            return Boolean.TRUE.equals(enter.invoke(manager,task.taskId))?null:"系统未接受分屏请求";
        }catch(ReflectiveOperationException|RuntimeException|LinkageError failure){
            Log.w("CHOSplitScreen","System split screen unavailable",failure);
            return "系统分屏暂不可用";
        }
    }
}
