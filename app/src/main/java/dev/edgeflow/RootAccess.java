package dev.edgeflow;

import android.content.Context;
import android.os.*;
import java.util.concurrent.*;
import java.util.function.Consumer;

/** Checks this app's su authorization, independently of the LSPosed module connection. */
public final class RootAccess {
    public enum State { UNKNOWN, CHECKING, GRANTED, DENIED, UNAVAILABLE, TIMEOUT }
    private static final Handler main=new Handler(Looper.getMainLooper());
    private static final ExecutorService worker=Executors.newSingleThreadExecutor();
    private static final java.util.List<Consumer<State>> callbacks=new java.util.ArrayList<>();
    private static volatile State state=State.UNKNOWN;
    private static long checkedAt;
    private static boolean retryMaterials;
    public static State state(){return state;}
    public static boolean granted(){return state==State.GRANTED;}
    public static boolean previouslyGranted(Context c){return c.createDeviceProtectedStorageContext().getSharedPreferences("runtime_permissions",0).getBoolean("root_granted",false);}
    public static String summary(){return switch(state){case UNKNOWN->"尚未检查";case CHECKING->"正在检查";case GRANTED->"已授权";case DENIED->"未授权";case UNAVAILABLE->"未找到 su";case TIMEOUT->"授权超时";};}
    public static void check(Context context,boolean force,Consumer<State> done){
        if(Looper.myLooper()!=Looper.getMainLooper()){main.post(()->check(context,force,done));return;}
        if(state==State.CHECKING){retryMaterials|=force;callbacks.add(done);return;}
        if(!force&&state!=State.UNKNOWN&&(state!=State.GRANTED||SystemClock.uptimeMillis()-checkedAt<30000)){done.accept(state);return;}
        Context app=context.getApplicationContext();callbacks.add(done);retryMaterials=force||state!=State.GRANTED;state=State.CHECKING;
        worker.execute(()->{
            State checked=State.valueOf(RootAuthorization.check(()->new ProcessBuilder("su","-c","id -u").redirectErrorStream(true).start(),15000).name());
            app.createDeviceProtectedStorageContext().getSharedPreferences("runtime_permissions",0).edit().putBoolean("root_granted",checked==State.GRANTED).commit();
            main.post(()->{
                state=checked;checkedAt=SystemClock.uptimeMillis();
                boolean retry=retryMaterials;retryMaterials=false;
                if(checked==State.GRANTED)replica.ColorOsSurface.rootVerified(app,retry);
                LspToolHostService.prepareMaterials();
                var listeners=new java.util.ArrayList<>(callbacks);callbacks.clear();
                for(var listener:listeners)listener.accept(checked);
            });
        });
    }
    private RootAccess(){}
}
