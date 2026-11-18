package dev.edgeflow;

import android.content.*;
import android.net.Uri;
import android.os.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;

/** Module authorization and live status. No privileged reflection runs in the settings UI. */
public final class RuntimeBackend {
    private static final Uri CONTROL=Uri.parse("content://dev.edgeflow.lsp.persistent");
    private static final Handler main=new Handler(Looper.getMainLooper());
    private static final ExecutorService io=Executors.newSingleThreadExecutor();
    private static final AtomicBoolean changing=new AtomicBoolean();
    private static volatile Status last=new Status(false,false,false,false,false,"","正在检查连接",new Bundle());
    public record Status(boolean connected,boolean framework,boolean current,boolean active,boolean requested,String phase,String message,Bundle raw){
        BackendActions.Capabilities actions(){
            return ready()?new BackendActions.Capabilities(raw.getBoolean(BackendActions.POWER_MENU_AVAILABLE),
                    raw.getBoolean(BackendActions.SPLIT_SCREEN_AVAILABLE)):BackendActions.Capabilities.NONE;
        }
        String homeLabel(){
            if(phase.equals("FAULTED")||!raw.getString("persistent_error","").isEmpty())return "运行异常";
            if(active&&ready())return "已开启";
            return switch(phase){
                case "WAITING_CONFIG","CHECKING"->"正在读取配置";
                case "LOCKED","OTHER_USER","ACCESSIBILITY"->"已暂停";
                case "SOURCE_UNAVAILABLE"->"配置不可用";
                case "MASTER_OFF"->"已关闭";
                default->ready()?"等待启用":"等待连接";
            };
        }
        public boolean ready(){return connected&&framework&&current&&raw.getBoolean("persistent_available");}
    }
    public static Status status(){return last;}
    public static boolean starting(){return changing.get();}
    public static void query(Context context,Consumer<Status> completion){
        Context c=context.getApplicationContext();
        io.execute(()->{
            try{
                Bundle info=c.getContentResolver().call(CONTROL,"status",null,null);
                if(info==null||!info.getBoolean("framework_service")){deliver(new Status(false,false,false,false,false,"","未连接 LSPosed",new Bundle()),completion);return;}
                request(c,"status",null,values->{
                    boolean connected=values.getBoolean("connected"),framework=values.getBoolean("framework_ready");
                    boolean current=c.getPackageName().equals(values.getString("module_package"))&&c.getApplicationInfo().sourceDir.equals(values.getString("module_source"));
                    boolean active=values.getBoolean("active"),requested=values.getBoolean("persistent_requested");String phase=values.getString("persistent_state","");
                    String message=!connected?"系统界面未加载模块":!current?"更新后需要重启":!framework?"系统框架未加载模块":active?"已连接":phase.equals("MASTER_OFF")?"已连接 · 手势已关闭":requested?"已连接 · 等待恢复":"已连接 · 等待启用";
                    if(connected&&current&&framework&&phase.equals("WAITING_CONFIG"))message="已连接 · 等待读取配置";
                    if(connected&&current&&framework&&(!values.getString("error","").isEmpty()||!values.getString("persistent_error","").isEmpty()))message="连接异常";
                    deliver(new Status(connected,framework,current,active,requested,phase,message,new Bundle(values)),completion);
                });
            }catch(RuntimeException unavailable){deliver(new Status(false,false,false,false,false,"","无法读取运行状态",new Bundle()),completion);}
        });
    }
    private static void deliver(Status state,Consumer<Status> completion){last=state;main.post(()->completion.accept(state));}
    public static void request(Context c,String operation,Bundle extras,Consumer<Bundle> completion){
        AtomicBoolean done=new AtomicBoolean();
        ResultReceiver callback=new ResultReceiver(main){protected void onReceiveResult(int code,Bundle value){if(done.compareAndSet(false,true))completion.accept(value==null?new Bundle():value);}};
        Parcel parcel=Parcel.obtain();try{callback.writeToParcel(parcel,0);parcel.setDataPosition(0);callback=ResultReceiver.CREATOR.createFromParcel(parcel);}finally{parcel.recycle();}
        Intent intent=new Intent("dev.edgeflow.lsp.pilot.CONTROL_REQUEST").setPackage("com.android.systemui").putExtra("module_package",c.getPackageName()).putExtra("operation",operation).putExtra("reply",callback);
        if(extras!=null)intent.putExtras(extras);c.sendBroadcast(intent);
        // Tool creation has its own 3.5 s attachment deadline, including a cold app host.
        main.postDelayed(()->{if(done.compareAndSet(false,true))completion.accept(new Bundle());},operation.equals("preview")?4500:2000);
    }
    public static void signal(Context c,String operation,Bundle extras){Intent intent=new Intent("dev.edgeflow.lsp.pilot.CONTROL_REQUEST").setPackage("com.android.systemui").putExtra("module_package",c.getPackageName()).putExtra("operation",operation);if(extras!=null)intent.putExtras(extras);c.sendBroadcast(intent);}
    public static void preview(Context c,String action,float[] anchor,Consumer<String> completion){
        if(!BackendActions.window(action)&&!"edit_launcher".equals(action)){completion.accept("暂不支持此预览");return;}
        Bundle data=new Bundle();data.putString("action",action);data.putFloatArray("anchor",anchor);
        request(c,"preview",data,result->completion.accept((result.getBoolean("tool_shown")||result.getBoolean("widget_setup"))?null:result.getString("tool_error","请先确认 LSPosed 已连接并开启手势")));
    }
    public static void trial(Context c,IBinder owner,String application,boolean enabled,Consumer<Bundle> report){
        Bundle data=new Bundle();data.putBinder("owner",owner);data.putString("application",application);data.putBoolean("active",enabled);Bundle outer=new Bundle();outer.putBundle("trial",data);
        if(!enabled){signal(c,"trial",outer);return;}
        ResultReceiver callback=new ResultReceiver(main){protected void onReceiveResult(int code,Bundle result){report.accept(result==null?new Bundle():result);}};
        Parcel parcel=Parcel.obtain();try{callback.writeToParcel(parcel,0);parcel.setDataPosition(0);callback=ResultReceiver.CREATOR.createFromParcel(parcel);}finally{parcel.recycle();}
        c.sendBroadcast(new Intent("dev.edgeflow.lsp.pilot.CONTROL_REQUEST").setPackage("com.android.systemui").putExtra("module_package",c.getPackageName()).putExtra("operation","trial").putExtra("trial",data).putExtra("reply",callback));
    }
    private static void changed(Context c){
        GestureService.backendChanged();
        try{Bundle snapshot=c.getContentResolver().call(Uri.parse("content://dev.edgeflow.gesture_config"),"snapshot",null,null);
            if(snapshot!=null)c.sendBroadcast(new Intent("dev.edgeflow.lsp.pilot.CONFIG_CHANGED").setPackage("com.android.systemui").putExtra("module_package",c.getPackageName()).putExtra("profile",snapshot.getString("profile")));
        }catch(RuntimeException unavailable){android.util.Log.w("CHOBackend","Backend choice notification unavailable",unavailable);}
        c.sendBroadcast(new Intent(c.getPackageName()+".SERVICE_STATE").setPackage(c.getPackageName()));
    }
    public static void enable(Context context,Consumer<String> completion){
        if(!RootAccess.granted()){completion.accept("请先授予 Root 权限");return;}
        if(!changing.compareAndSet(false,true))return;
        Context c=context.getApplicationContext();
        query(c,state->{
            if(!state.ready()){changing.set(false);completion.accept(state.message());return;}
            if(state.requested()){changing.set(false);completion.accept(null);return;}
            changed(c);
            io.execute(()->{
                String error=null;
                try{Bundle response=c.getContentResolver().call(CONTROL,"enable",null,null);if(response==null||!response.getString("source_error","").isEmpty())throw new IllegalStateException("配置未同步");}
                catch(RuntimeException failure){error="LSPosed 启动失败";}
                String failure=error;main.post(()->{if(failure!=null){changing.set(false);completion.accept(failure);}else awaitRunning(c,SystemClock.uptimeMillis()+5500,completion);});
            });
        });
    }
    private static void awaitRunning(Context c,long deadline,Consumer<String> completion){
        query(c,state->{
            if(state.ready()&&state.requested()&&(state.active()||state.phase().equals("MASTER_OFF"))){
                GestureService.disableForLsp();changing.set(false);changed(c);completion.accept(null);
            }else if(SystemClock.uptimeMillis()<deadline)main.postDelayed(()->awaitRunning(c,deadline,completion),180);
            else{changing.set(false);completion.accept("手势未启动，请查看 LSPosed 状态");}
        });
    }
    private RuntimeBackend(){}
}
