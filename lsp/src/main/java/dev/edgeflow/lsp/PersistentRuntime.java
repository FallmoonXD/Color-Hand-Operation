package dev.edgeflow.lsp;

import android.app.*;
import android.content.*;
import android.database.ContentObserver;
import android.hardware.display.DisplayManager;
import android.os.*;
import android.provider.Settings;
import java.util.concurrent.Executors;

/** SystemUI lifecycle owner. All mutations are serialized on its main looper. */
final class PersistentRuntime {
    private final Context context;
    private final Handler main;
    private final SharedPreferences remote;
    private final SystemScene scene;
    private final Runnable takeOwnership;
    private final ControllerConnection controller;
    private final ToolRuntime tools;
    private final TrialChannel trial;
    private final java.util.concurrent.ExecutorService io=Executors.newSingleThreadExecutor();
    private NativeInputSession session;
    private GestureProfile profile;
    private PersistentState saved;
    private boolean checked,refreshing,refreshAgain,faulted;
    private final ControllerUpdates updates=new ControllerUpdates();
    private int remoteChanges;
    private String error="",lastReason="",lastEvent="",state="DISABLED";
    private int starts,refreshGeneration;
    private final LifecycleDispatch dispatch;
    private String reportedState="",reportedError="";
    private final Runnable sceneChanged=this::sceneChanged;
    private void sceneChanged(){main.post(dispatch::waking);}
    private final SharedPreferences.OnSharedPreferenceChangeListener changed=(p,key)->mainPostRemote();
    private final ContentObserver accessibility;
    private final DisplayManager.DisplayListener displays=new DisplayManager.DisplayListener(){
        public void onDisplayAdded(int id){schedule();}
        public void onDisplayRemoved(int id){schedule();}
        public void onDisplayChanged(int id){if(id==0)dispatch.waking();}
    };
    private final BroadcastReceiver events=new BroadcastReceiver(){
        @Override public void onReceive(Context c,Intent event){
            String action=event.getAction();
            lastEvent=action;
            if(Intent.ACTION_SCREEN_OFF.equals(action)){dispatch.pause();close("screen-off");reconcile();return;}
            if(Intent.ACTION_SCREEN_ON.equals(action)){dispatch.waking();return;}
            if(Intent.ACTION_USER_PRESENT.equals(action)||Intent.ACTION_USER_UNLOCKED.equals(action)){
                dispatch.waking();return;
            }
            if(event.getData()!=null){
                String pkg=event.getData().getSchemeSpecificPart();
                if(!pkg.equals("dev.edgeflow")&&!pkg.equals(BackendIdentity.module()))return;
                checked=false;close("package-changed");
                boolean replacing=event.getBooleanExtra(Intent.EXTRA_REPLACING,false);
                if(PersistentPolicy.revokesSource(action,replacing))updates.revoke();
                if(!replacing||Intent.ACTION_PACKAGE_REPLACED.equals(action)){
                    if(refreshing)refreshAgain=true;else refresh();
                }
                return;
            }
            schedule();
        }
    };
    PersistentRuntime(Context context,Handler main,SharedPreferences remote,Runnable takeOwnership){
        this.context=context;this.main=main;this.remote=remote;this.takeOwnership=takeOwnership;scene=new SystemScene(context);
        dispatch=new LifecycleDispatch(new LifecycleDispatch.Queue(){public long now(){return SystemClock.uptimeMillis();}public void post(Runnable work,long delay){main.postDelayed(work,delay);}public void remove(Runnable work){main.removeCallbacks(work);}},this::reconcile);
        tools=new ToolRuntime(context,main,()->requested()&&checked&&!faulted&&owner()&&unlocked()&&profile!=null&&profile.flag("enabled",true)&&!scene.accessibilityInput());
        trial=new TrialChannel(main);
        controller=new ControllerConnection(context,main,()->{if(!checked||updates.pending())refresh();else schedule();});
        accessibility=new ContentObserver(main){@Override public void onChange(boolean self){schedule();}};
        remote.registerOnSharedPreferenceChangeListener(changed);
        context.getContentResolver().registerContentObserver(Settings.Secure.getUriFor(Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES),false,accessibility);
        context.getSystemService(DisplayManager.class).registerDisplayListener(displays,main);
        SystemScene.listen(sceneChanged);
        IntentFilter display=new IntentFilter();display.addAction(Intent.ACTION_SCREEN_ON);display.addAction(Intent.ACTION_SCREEN_OFF);display.addAction(Intent.ACTION_USER_PRESENT);display.addAction(Intent.ACTION_USER_UNLOCKED);display.addAction("android.intent.action.USER_SWITCHED");
        context.registerReceiver(events,display,null,main,Context.RECEIVER_NOT_EXPORTED);
        IntentFilter packages=new IntentFilter();packages.addDataScheme("package");packages.addAction(Intent.ACTION_PACKAGE_REMOVED);packages.addAction(Intent.ACTION_PACKAGE_REPLACED);packages.addAction(Intent.ACTION_PACKAGE_DATA_CLEARED);packages.addAction(Intent.ACTION_PACKAGE_CHANGED);
        context.registerReceiver(events,packages,null,main,Context.RECEIVER_NOT_EXPORTED);
        readRemote();refresh();
    }
    private void mainPostRemote(){main.post(()->{remoteChanges++;readRemote();if(!checked)refresh();else reconcile();});}
    private void readRemote(){
        try{
            String raw=remote.getString(PersistentState.KEY,null);
            if(raw==null){saved=null;profile=null;faulted=false;return;}
            PersistentState next=PersistentState.parse(raw);
            if(saved==null||!next.revision.equals(saved.revision))faulted=false;
            saved=next;profile=next.requested?GestureProfile.parse(next.profile):null;
        }catch(Exception failure){saved=null;profile=null;faulted=true;error=failure.toString();close("invalid-persistent-state");}
    }
    boolean requested(){return saved!=null&&saved.requested;}
    void preview(String action,float[] anchor,ResultReceiver reply){
        if(!"RUNNING".equals(state)||!dev.edgeflow.ToolHostContract.supports(action)||anchor==null||anchor.length!=4){Bundle result=new Bundle();result.putString("tool_error","请先开启手势并确认连接");if(reply!=null)reply.send(0,result);return;}
        for(float value:anchor)if(!Float.isFinite(value))throw new IllegalArgumentException("Invalid preview anchor");
        tools.show(action,anchor,reply);
    }
    void settingsPreview(IBinder owner,Bundle regions,Bundle colors){if(session!=null)session.settingsPreview(owner,regions,colors);}
    void trial(IBinder owner,String application,ResultReceiver reply,boolean start)throws RemoteException{
        if(!start){if(owner==trial.owner()){trial.stop(owner);if(session!=null)session.cancelContact();}return;}
        if(!"RUNNING".equals(state))throw new IllegalStateException("Gesture backend is not running");
        boolean changed=trial.owner()!=owner;
        trial.start(owner,application,reply,scene.focus());
        if(changed){tools.close();if(session!=null)session.cancelContact();}
    }
    private boolean owner(){
        try{return ((Number)ActivityManager.class.getMethod("getCurrentUser").invoke(null)).intValue()==0;}
        catch(ReflectiveOperationException | RuntimeException failure){return false;}
    }
    private boolean unlocked(){
        return context.getSystemService(UserManager.class).isUserUnlocked()&&!context.getSystemService(KeyguardManager.class).isKeyguardLocked();
    }
    private boolean source(){
        if(saved==null)return false;
        try{
            context.getPackageManager().getApplicationInfo(BackendIdentity.module(),0);
            return saved.source.equals(PersistentBridge.sourceIdentity(context));
        }catch(Exception missing){return false;}
    }
    void configurationChanged(){checked=false;close("configuration-refresh");if(refreshing)refreshAgain=true;else refresh();}
    void configurationChanged(String expectedProfile){
        if(expectedProfile!=null)try{
            GestureProfile incoming=GestureProfile.parse(expectedProfile);
            if(BackendIdentity.integrated()&&!incoming.flag("backend_lsp",false)){
                updates.revoke();configurationChanged();return;
            }
            if(checked&&!refreshing){readRemote();if(profile!=null&&profile.same(incoming)){reconcile();return;}}
            // Retain the authenticated source update until the controller confirms persistence.
            updates.offer(expectedProfile);
        }catch(RuntimeException|org.json.JSONException invalid){checked=false;faulted=true;error=invalid.toString();close("invalid-source-update");state="FAULTED";return;}
        configurationChanged();
    }
    void refresh(){
        if(refreshing||!owner()||!unlocked()){schedule();return;}
        refreshing=true;int generation=++refreshGeneration,initialChanges=remoteChanges;
        ControllerUpdates.Request update=updates.take();
        Runnable timeout=()->{if(generation==refreshGeneration&&refreshing){checked=false;error="Configuration refresh timed out";close("refresh-timeout");state="CHECKING";}};
        main.postDelayed(timeout,5500);
        io.execute(()->{
            String raw=null,failure=null,sourceError="";
            try{Bundle response=controller.request(update.revoke()?"disable":update.profile()!=null?"sync":"refresh",update.revoke()?null:update.profile());raw=response.getString("state");sourceError=response.getString("source_error","");}
            catch(RuntimeException unavailable){failure=unavailable.toString();}
            String value=raw,problem=failure,sourceProblem=sourceError;
            main.post(()->{
                main.removeCallbacks(timeout);if(generation!=refreshGeneration)return;refreshing=false;
                if(problem!=null)updates.failed(update);
                if(refreshAgain){refreshAgain=false;refresh();return;}
                if(problem!=null){checked=false;faulted=true;error=problem;close("refresh-unavailable");state="CHECKING";return;}
                try{
                    if(remoteChanges==initialChanges){saved=PersistentState.parse(value);profile=saved.requested?GestureProfile.parse(saved.profile):null;}
                    else readRemote();
                    checked=true;faulted=false;error=sourceProblem;reconcile();
                }
                catch(Exception invalid){checked=false;faulted=true;error=invalid.toString();close("invalid-persistent-state");state="FAULTED";}
            });
        });
    }
    private void schedule(){dispatch.signal();}
    private void reconcile(){
        try{
            scene.profile(profile);
            if(PersistentPolicy.shouldRefresh(checked,refreshing,faulted,context.getSystemService(PowerManager.class).isInteractive(),unlocked(),owner())){
                state="CHECKING";refresh();return;
            }
            PersistentPolicy.State decision=PersistentPolicy.decide(requested(),checked,context.getSystemService(PowerManager.class).isInteractive(),unlocked(),owner(),source(),scene.accessibilityInput(),profile!=null&&profile.flag("enabled",true),faulted);
            state=decision.name();
            if(decision!=PersistentPolicy.State.RUNNING){
                close(state.toLowerCase(java.util.Locale.ROOT));if(decision==PersistentPolicy.State.DISABLED&&!refreshing)controller.release();
                if((decision==PersistentPolicy.State.LOCKED||decision==PersistentPolicy.State.CHECKING)&&context.getSystemService(PowerManager.class).isInteractive())dispatch.retry();
                return;
            }
            // Accessibility is evaluated live. A stale serialized accessibility flag must not prevent resuming after it is disabled.
            GestureProfile live=new GestureProfile(profile.values,false);
            if(session!=null&&session.snapshot().getBoolean("active")){session.update(live);tools.prepare();return;}
            takeOwnership.run();session=NativeInputSession.persistent(context,main,live,this::sessionChanged,tools,trial);starts++;error="";tools.prepare();
        }catch(NativeInputSession.NotReady transition){
            // Keyguard, power or rotation can change after the policy check, while IPCs run.
            // No input has been acquired yet. This is a suspension, not a broken backend.
            state="LOCKED";error="";close("start-deferred");dispatch.arm();dispatch.retry();
        }catch(ReflectiveOperationException | RuntimeException | LinkageError failure){faulted=true;state="FAULTED";error=failure.toString();close("start-failed");}
        finally{reportState();}
    }
    private void reportState(){
        if(state.equals(reportedState)&&error.equals(reportedError))return;reportedState=state;reportedError=error;
        android.util.Log.i("CHOLspLifecycle","state="+state+" starts="+starts+" reason="+lastReason+(error.isEmpty()?"":" error="+error));
    }
    private void sessionChanged(Bundle value){
        if(value.getBoolean("active"))return;
        lastReason=value.getString("reason","");
        if(lastReason.equals("failed")||lastReason.equals("action-rejected")||lastReason.equals("start-failed")){
            faulted=true;error=value.getString("error",lastReason);
        }
        schedule();
    }
    private void close(String reason){trial.clear();tools.close();if(session!=null&&session.snapshot().getBoolean("active"))session.close(reason);}
    Bundle snapshot(){
        Bundle b=session==null?new Bundle():session.snapshot();
        b.putLong("trial_generation",trial.generation());
        b.putBoolean("persistent_available",true);b.putBoolean("persistent_requested",requested());b.putBoolean("source_checked",checked);
        b.putString("persistent_revision",saved==null?"":saved.revision);b.putInt("remote_notifications",remoteChanges);
        b.putString("lifecycle_event",lastEvent);
        b.putString("persistent_state",state);b.putString("persistent_error",error);b.putInt("session_starts",starts);b.putString("previous_session_reason",lastReason);
        return b;
    }
}
