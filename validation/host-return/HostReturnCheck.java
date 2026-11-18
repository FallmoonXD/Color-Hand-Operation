package replica;

import android.app.Activity;
import android.appwidget.*;
import android.content.*;
import android.os.*;
import android.widget.*;
import dev.edgeflow.RootAccess;
import java.io.*;
import java.lang.reflect.*;
import org.json.*;

public final class HostReturnCheck extends Activity implements OverlayWindowToken {
    private static boolean grant;
    private static int launches;
    private boolean revoked;
    private final Handler main=new Handler(Looper.getMainLooper());
    private static final float[] anchor={48,700,175,700};
    public static java.lang.Process checkRoot() throws IOException {
        return new ProcessBuilder("/system/bin/sh","-c",grant?"echo 0":"exit 1").start();
    }
    public static java.lang.Process startBlur() throws IOException {
        launches++;
        if(!grant)return new ProcessBuilder("/system/bin/false").start();
        NativeBlur.main.post(()->NativeBlur.connected(new Binder()));
        return new ProcessBuilder("/system/bin/sh","-c","echo NATIVE_BLUR_READY; sleep 4; echo NATIVE_BLUR_IDLE").start();
    }
    @Override public IBinder overlayWindowToken(){return revoked?null:getWindow().getDecorView().getWindowToken();}
    @Override public void onCreate(Bundle state){super.onCreate(state);TextView page=new TextView(this);page.setText("CHO validation host");setContentView(page);getWindow().getDecorView().post(()->handle(getIntent()));}
    @Override public void onNewIntent(Intent intent){super.onNewIntent(intent);setIntent(intent);main.post(()->handle(intent));}
    private void save(String name,JSONObject value){try(FileOutputStream out=openFileOutput(name+".json",MODE_PRIVATE)){out.write(value.toString().getBytes(java.nio.charset.StandardCharsets.UTF_8));}catch(Exception error){throw new IllegalStateException(error);}}
    private void handle(Intent intent){
        String operation=intent.getStringExtra("op");
        try{
            if("material".equals(operation)){grant=false;NativeBlur.keepWarm(this,true);waitBlocked(0);return;}
            if("again".equals(operation)){WidgetOverlay.show(this,anchor);return;}
            if("inspect".equals(operation)){report("inspect");return;}
            if("clear".equals(operation)){WidgetOverlay.dismissAll();revoked=false;return;}
            boolean existing=intent.getBooleanExtra("existing",false);
            WidgetOverlay.show(this,anchor);
            Field current=WidgetOverlay.class.getDeclaredField("current");current.setAccessible(true);
            Field field=WidgetOverlay.class.getDeclaredField("panel");field.setAccessible(true);
            WidgetPanel panel=(WidgetPanel)field.get(current.get(null));
            AppWidgetManager manager=AppWidgetManager.getInstance(this);
            ComponentName component=new ComponentName("dev.edgeflow.widgettest","dev.edgeflow.widgettest.Provider$Required");
            if(existing){
                int id=intent.getIntExtra("id",-1);
                Field setup=WidgetPanel.class.getDeclaredField("setup");setup.setAccessible(true);
                ((WidgetPanel.Setup)setup.get(panel)).request(component,android.os.Process.myUserHandle(),id,true,true,0);
            }else{
                AppWidgetProviderInfo info=manager.getInstalledProviders().stream().filter(p->component.equals(p.provider)).findFirst().orElseThrow();
                Method bind=WidgetPanel.class.getDeclaredMethod("beginBind",AppWidgetProviderInfo.class);bind.setAccessible(true);bind.invoke(panel,info);
            }
            if("cancelReturn".equals(operation))WidgetOverlay.dismissAll();
            if("revoke".equals(operation))revoked=true;
            report("started");
        }catch(Exception exception){android.util.Log.e("CHOHostCheck",operation,exception);try{save("error",new JSONObject().put("error",exception.toString()));}catch(Exception ignored){}}
    }
    private void report(String name)throws Exception{
        JSONObject state=new JSONObject().put("panel",WidgetOverlay.isShowing()).put("configuring",WidgetActivity.setupInProgress()).put("pid",android.os.Process.myPid());
        state.put("ids",new JSONArray(new AppWidgetHost(this,WidgetMigration.HOST_ID).getAppWidgetIds()));
        state.put("layout",new JSONArray(getSharedPreferences("replica_widgets",0).getString("layout_v3","[]")));
        save(name,state);
    }
    private void waitBlocked(int n){
        if(!NativeBlur.unavailable()&&n<120){main.postDelayed(()->waitBlocked(n+1),100);return;}
        try{
            final boolean blocked=NativeBlur.unavailable();
            final int before=launches;
            RootAccess.check(this,true,denied->{
                grant=true;
                RootAccess.check(this,true,granted->main.postDelayed(()->{
                    try{
                        Bundle stats=NativeBlur.stats();
                        save("material",new JSONObject().put("blocked",blocked).put("first",denied.name()).put("second",granted.name())
                            .put("launches_before",before).put("launches_after",launches).put("online",stats.getBoolean("connected"))
                            .put("active",stats.getInt("active")).put("frames",stats.getInt("frames")).put("pid",android.os.Process.myPid()));
                        NativeBlur.keepWarm(this,false);grant=false;
                    }catch(Exception error){throw new IllegalStateException(error);}
                },350));
            });
        }catch(Exception error){throw new IllegalStateException(error);}
    }
}
