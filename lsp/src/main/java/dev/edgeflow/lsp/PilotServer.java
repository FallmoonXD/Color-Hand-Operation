package dev.edgeflow.lsp;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.ResultReceiver;

/** The signed pilot app can request a bounded experiment, not arbitrary commands. */
final class PilotServer {
    static final String ACTION="dev.edgeflow.lsp.pilot.CONTROL_REQUEST";
    static final String PERMISSION="dev.edgeflow.lsp.pilot.CONTROL";
    static final String CONFIG_CHANGED="dev.edgeflow.lsp.pilot.CONFIG_CHANGED";
    static final String PERSISTENT_CHANGED="dev.edgeflow.lsp.pilot.PERSISTENT_CHANGED";
    private static PilotServer instance;
    private final Context context;
    private final Handler main=new Handler(Looper.getMainLooper());
    private NativeInputSession session;
    private ToolWindowLease probeLease;
    private ToolHostConnection toolProbe;
    private PersistentRuntime persistent;
    private String initializationState="WAITING_CONFIG",initializationError="";
    private Bundle runtimeStatus(){
        if(persistent!=null)return persistent.snapshot();
        Bundle state=new Bundle();
        state.putString("persistent_state",initializationState);
        state.putString("persistent_error",initializationError);
        return state;
    }
    private String error="";
    private final BroadcastReceiver requests=new BroadcastReceiver(){
        @Override public void onReceive(Context c,Intent request) {
            String target=request.getStringExtra("module_package");if(target!=null&&!target.equals(BackendIdentity.module()))return;
            error="";
            ResultReceiver reply=null;
            try {
                if(PERSISTENT_CHANGED.equals(request.getAction())){if(persistent!=null)persistent.configurationChanged();return;}
                if(CONFIG_CHANGED.equals(request.getAction())){
                    if(persistent!=null)persistent.configurationChanged(request.getStringExtra("profile"));
                    if(session!=null)session.update(GestureProfile.parse(request.getStringExtra("profile")));
                    return;
                }
                reply=request.getParcelableExtra("reply",ResultReceiver.class);
                String operation=request.getStringExtra("operation");
                if("preview".equals(operation)){
                    if(persistent!=null)persistent.preview(request.getStringExtra("action"),request.getFloatArrayExtra("anchor"),reply);return;
                }
                if("settings_preview".equals(operation)){
                    Bundle data=request.getBundleExtra("preview");if(persistent!=null&&data!=null)persistent.settingsPreview(data.getBinder("owner"),data.getBundle("regions"),data.getBundle("colors"));return;
                }
                if("trial".equals(operation)){
                    Bundle data=request.getBundleExtra("trial");if(persistent!=null&&data!=null)persistent.trial(data.getBinder("owner"),data.getString("application"),reply,data.getBoolean("active"));return;
                }
                // Production commands have returned above. Only status can fall through in CHO.
                BackendIdentity.requireDiagnosticOperation(operation);
                if("tool_probe_quick_tools".equals(operation)||"tool_probe_widgets".equals(operation)){
                    if(toolProbe!=null)toolProbe.close();
                    android.graphics.Point size=new android.graphics.Point();context.getSystemService(android.hardware.display.DisplayManager.class).getDisplay(0).getRealSize(size);
                    toolProbe=new ToolHostConnection(context,main,operation.substring(11),new float[]{size.x-10,size.y*.5f,size.x-200,size.y*.5f},reply);
                    ToolHostConnection issued=toolProbe;issued.start();main.postDelayed(()->{issued.close();if(toolProbe==issued)toolProbe=null;},30000);return;
                }
                if("tool_probe_stop".equals(operation)){if(toolProbe!=null)toolProbe.close();toolProbe=null;operation="status";}
                if("window_probe".equals(operation)){
                    if(probeLease!=null)probeLease.close();
                    Bundle host=request.getBundleExtra("window_host");
                    if(host==null)throw new IllegalArgumentException("Missing tool host");
                    probeLease=new ToolWindowLease(host.getBinder("lifetime"),"dev.edgeflow.lspfixture");ToolWindowLease issued=probeLease;
                    main.postDelayed(()->{issued.close();if(probeLease==issued)probeLease=null;},15000);
                    Bundle grant=new Bundle();grant.putBinder("window_token",issued.token);if(reply!=null)reply.send(0,grant);return;
                }
                if("window_probe_stop".equals(operation)){if(probeLease!=null)probeLease.close();probeLease=null;operation="status";}
                if("window_probe_status".equals(operation)){
                    Bundle result=new Bundle();result.putInt("window_leases",ToolWindowLease.activeCount());if(reply!=null)reply.send(0,result);return;
                }
                if("observe".equals(operation) || "capture".equals(operation)||"configured".equals(operation)) {
                    if(persistent!=null&&persistent.requested())throw new IllegalStateException("Disable persistent input before a bounded test");
                    GestureProfile profile="configured".equals(operation)?GestureProfile.parse(request.getStringExtra("profile")):null;
                    if(session!=null)session.close();
                    session=null;error="";
                    session=new NativeInputSession(context,main,!"observe".equals(operation),30000,null,profile);
                } else if("stop".equals(operation)){
                    if(persistent!=null&&persistent.requested())throw new IllegalStateException("Use the persistent disable control to save the stopped state");
                    if(session!=null)session.close();
                }
                else if(!"status".equals(operation) && !"stop".equals(operation))throw new IllegalArgumentException("Unknown operation");
            }catch(ReflectiveOperationException | android.os.RemoteException | org.json.JSONException | RuntimeException | LinkageError failure){
                error=failure.toString();
                if(CONFIG_CHANGED.equals(request.getAction())&&session!=null)session.close("invalid-configuration");
            }
            Bundle result;
            try{result=session==null?new NativeInputApi(context).capabilities():session.snapshot();}
            catch(ReflectiveOperationException | RuntimeException | LinkageError failure){result=new Bundle();error=failure.toString();}
            result.putBoolean("connected",true);
            result.putString("module_package",BackendIdentity.module());result.putString("module_source",BackendIdentity.source());
            try{result.putBoolean("framework_ready",ToolWindowLease.activeCount()>=0);}catch(Exception unavailable){result.putBoolean("framework_ready",false);}
            Bundle state=runtimeStatus();
            if(state.getBoolean("persistent_requested"))result.putAll(state);else{
                for(String key:new String[]{"persistent_available","persistent_requested","source_checked"})result.putBoolean(key,state.getBoolean(key));
                result.putString("persistent_state",state.getString("persistent_state"));result.putString("persistent_error",state.getString("persistent_error"));
            }
            if(!error.isEmpty())result.putString("error",error);
            if(reply!=null)reply.send(0,result);
        }
    };
    private PilotServer(Context context) {
        this.context=context;
        IntentFilter filter=new IntentFilter(ACTION);filter.addAction(CONFIG_CHANGED);filter.addAction(PERSISTENT_CHANGED);
        context.registerReceiver(requests,filter,PERMISSION,main,Context.RECEIVER_EXPORTED);
    }
    static void attach(Context c,java.util.function.Supplier<android.content.SharedPreferences> remote) {
        if(instance!=null)return;
        instance=new PilotServer(c.getApplicationContext());
        new PreferenceRetry<>(remote,instance::initialize,(work,delay)->instance.main.postDelayed(work,delay),
            failure->android.util.Log.w("CHOLsp","Waiting for remote preferences",failure)).run();
    }
    private void initialize(android.content.SharedPreferences remote){
        try{
            persistent=new PersistentRuntime(context,main,remote,()->{if(session!=null)session.close("persistent-input-started");session=null;});
        }catch(RuntimeException | LinkageError failure){
            initializationState="FAULTED";initializationError=failure.toString();
            android.util.Log.e("CHOLsp","Could not initialize runtime",failure);
        }
    }
    /** Read-only Android service dump. Does not depend on the app/controller being unfrozen. */
    static void dump(java.io.PrintWriter writer){
        org.json.JSONObject result=new org.json.JSONObject();
        try{
            result.put("connected",instance!=null);result.put("uptime",android.os.SystemClock.uptimeMillis());
            if(instance!=null){
                Context c=instance.context;Bundle status=instance.runtimeStatus();
                for(String key:new String[]{"active","reason","error","persistent_requested","persistent_state","persistent_error","source_checked","session_starts","previous_session_reason","lifecycle_event","actions","claims","edge_starts",dev.edgeflow.BackendActions.POWER_MENU_AVAILABLE,dev.edgeflow.BackendActions.SPLIT_SCREEN_AVAILABLE,"rejected_actions","last_action_error","trial_generation","feedback_error","tool_windows_available","contact_end","contact_cancel","contact_event","contact_time"})result.put(key,status.get(key));
                result.put("interactive",c.getSystemService(android.os.PowerManager.class).isInteractive());
                result.put("locked",c.getSystemService(android.app.KeyguardManager.class).isKeyguardLocked());
                result.put("user_unlocked",c.getSystemService(android.os.UserManager.class).isUserUnlocked());
                result.put("display_state",c.getSystemService(android.hardware.display.DisplayManager.class).getDisplay(0).getState());
                result.put("scene",SystemScene.flags);
            }
        }catch(RuntimeException|org.json.JSONException failure){writer.println("CHO_STATUS_ERROR: "+failure);return;}
        writer.println("CHO_STATUS: "+result);
    }
}
