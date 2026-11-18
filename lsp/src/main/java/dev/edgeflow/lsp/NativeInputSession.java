package dev.edgeflow.lsp;

import android.app.KeyguardManager;
import android.content.Context;
import android.graphics.Point;
import android.graphics.Rect;
import android.hardware.display.DisplayManager;
import android.os.Bundle;
import android.os.Handler;
import android.os.PowerManager;
import android.os.SystemClock;
import android.util.DisplayMetrics;
import android.view.Display;
import android.view.DisplayCutout;
import android.view.InputChannel;
import android.view.InputEvent;
import android.view.InputEventReceiver;
import android.view.InputDevice;
import android.view.MotionEvent;
import java.util.function.Consumer;

/** One input monitor with optional passive feedback surfaces; no touch-consuming window. */
final class NativeInputSession implements AutoCloseable {
    static final class NotReady extends IllegalStateException {NotReady(String message){super(message);}}
    static final long MAX_DURATION_MS=30000;
    private final Context context;
    private final Handler handler;
    private final NativeInputApi api;
    private final boolean capture;
    private final Consumer<Bundle> observer;
    private final DisplayManager displays;
    private final Display display;
    private final int width,height,rotation;
    private final EdgeInputSession decisions;
    private final SystemScene scene;
    private final float density,claimDistance;
    private MappedGestureSession mapped;
    private GestureFeedbackWindows feedback;
    private ToolRuntime tools;
    private final boolean sharedTools;
    private final TrialChannel trial;private boolean trialContact;private android.os.IBinder trialOwner;
    private final boolean toolWindowsAvailable;
    private String feedbackError="",lastActionError="";
    private long expiresAt;
    private String lastAction="",lastHandle="",lastDirection="";
    private boolean lastLong;
    private int noops,unsupported;
    private Object monitor;
    private Receiver receiver;
    private volatile boolean active;
    private boolean listenerRegistered, closed;
    private int edgeStarts, wouldClaim, claims, actions, rejectedActions;
    private int toolEvents;
    private String error="", reason="created";
    private String contactEnd="",contactCancel="";private int contactEvent=-1;private long contactTime;
    private final Runnable expiry=()->close("expired");
    private final Runnable longCheck=this::checkLong;
    private void checkLong(){
        if(!active||mapped==null||!mapped.tracking())return;
        try{
            SystemScene.Focus focus=scene.focus();
            if(!mappedEligible(focus)||!focus.key().equals(mapped.focus())){mapped.cancel();return;}
            advanceMapped(SystemClock.uptimeMillis());
        }catch(ReflectiveOperationException | RuntimeException failure){fail(failure);}
    }
    private final DisplayManager.DisplayListener changes=new DisplayManager.DisplayListener() {
        public void onDisplayAdded(int id) {}
        public void onDisplayRemoved(int id) { if(id==0) close("display-removed"); }
        public void onDisplayChanged(int id) {
            if(id!=0 || !active)return;
            try {
                if(display.getState()!=Display.STATE_ON){close("display-off");return;}
                Point size=new Point();display.getRealSize(size);
                if(size.x!=width || size.y!=height || display.getRotation()!=rotation)close("display-changed");
            }catch(RuntimeException failure){fail(failure);}
        }
    };

    NativeInputSession(Context context,Handler handler,boolean capture,long duration,Consumer<Bundle> observer)
            throws ReflectiveOperationException {
        this(context,handler,capture,duration,observer,null);
    }
    NativeInputSession(Context context,Handler handler,boolean capture,long duration,Consumer<Bundle> observer,GestureProfile profile)
            throws ReflectiveOperationException {
        this(context,handler,capture,duration,observer,profile,false,null,null);
    }
    static NativeInputSession persistent(Context context,Handler handler,GestureProfile profile,Consumer<Bundle> observer,ToolRuntime tools,TrialChannel trial)throws ReflectiveOperationException{
        return new NativeInputSession(context,handler,true,0,observer,profile,true,tools,trial);
    }
    private NativeInputSession(Context context,Handler handler,boolean capture,long duration,Consumer<Bundle> observer,GestureProfile profile,boolean persistent,ToolRuntime tools,TrialChannel trial)
            throws ReflectiveOperationException {
        this.context=context;this.handler=handler;this.capture=capture;this.observer=observer;
        this.tools=tools;sharedTools=tools!=null;
        this.trial=trial;
        if(persistent?duration!=0||profile==null:duration<1000 || duration>MAX_DURATION_MS)throw new IllegalArgumentException("Session duration 1..30 seconds, or configured persistent mode");
        api=new NativeInputApi(context);
        displays=context.getSystemService(DisplayManager.class);
        display=displays.getDisplay(Display.DEFAULT_DISPLAY);
        if(display==null)throw new IllegalStateException("Default display unavailable");
        Point size=new Point();display.getRealSize(size);width=size.x;height=size.y;rotation=display.getRotation();
        DisplayMetrics metrics=new DisplayMetrics();display.getRealMetrics(metrics);
        density=metrics.density;claimDistance=android.view.ViewConfiguration.get(context).getScaledTouchSlop();scene=new SystemScene(context);
        scene.profile(profile);
        toolWindowsAvailable=profile!=null&&ToolHostConnection.available(context);
        if(profile!=null){
            if(!profile.enabled())throw new IllegalStateException("CHO disabled");
            if(scene.accessibilityInput())throw new NotReady("Accessibility input became active");
            mapped=new MappedGestureSession(profile,scene.area(),density,claimDistance,id->trialContact||supported(id));
        }
        decisions=new EdgeInputSession(width,height,Math.min(metrics.density*40,width/4f),
                height*.15f,height*.85f,metrics.density*24);
        if(!eligible())throw new NotReady("Display is asleep, locked or changing geometry");
        try {
            monitor=api.open(capture);
            receiver=new Receiver(api.channel(monitor));
            displays.registerDisplayListener(changes,handler);listenerRegistered=true;
            active=true;reason="running";expiresAt=persistent?0:SystemClock.uptimeMillis()+duration;
            if(tools!=null)tools.geometryReady();
            if(!persistent)handler.postDelayed(expiry,duration);
            if(mapped!=null)try{
                feedback=new GestureFeedbackWindows(context,handler,scene,profile,()->active&&eligible()&&!scene.accessibilityInput(),why->{if(mapped.tracking())contactCancel="feedback-"+why;mapped.cancel();handler.removeCallbacks(longCheck);},()->handler.post(()->close("display-changed")));
            }catch(ReflectiveOperationException|android.content.pm.PackageManager.NameNotFoundException|RuntimeException|LinkageError unavailable){
                feedbackError=unavailable.getClass().getSimpleName()+": "+unavailable.getMessage();
            }
        } catch(ReflectiveOperationException | RuntimeException | LinkageError failure) {
            close("start-failed");throw failure;
        }
    }

    private boolean eligible() {
        PowerManager power=context.getSystemService(PowerManager.class);
        KeyguardManager lock=context.getSystemService(KeyguardManager.class);
        return power!=null && power.isInteractive() && lock!=null && !lock.isKeyguardLocked()
                && display.getState()==Display.STATE_ON&&display.getRotation()==rotation;
    }
    private boolean clearOrigin(float x,float y) {
        DisplayCutout cutout=display.getCutout();
        if(cutout!=null)for(Rect area:cutout.getBoundingRects())if(area.contains((int)x,(int)y))return false;
        return true;
    }
    Bundle snapshot() {
        Bundle b=api.capabilities();b.putBoolean("active",active);b.putBoolean("capture",capture);
        b.putInt("width",width);b.putInt("height",height);b.putInt("edge_starts",edgeStarts);
        b.putInt("would_claim",wouldClaim);b.putInt("claims",claims);b.putInt("actions",actions);
        b.putString("last_action_error",lastActionError);b.putInt("rejected_actions",rejectedActions);b.putString("error",error);b.putString("reason",reason);
        b.putBoolean("configured",mapped!=null);b.putLong("expires_at",expiresAt);b.putString("scene",SystemScene.flags);
        b.putBoolean("tool_windows_available",toolWindowsAvailable);
        b.putInt("ignored_tool_events",toolEvents);
        b.putString("contact_end",contactEnd);b.putString("contact_cancel",contactCancel);b.putInt("contact_event",contactEvent);b.putLong("contact_time",contactTime);
        b.putFloat("claim_distance",claimDistance);
        if(feedback!=null)feedback.describe(b);else{b.putBoolean("feedback_attached",false);b.putString("feedback_error",feedbackError);}
        if(mapped!=null){
            b.putString("revision",mapped.profile.revision);b.putString("last_action",lastAction);b.putString("last_handle",lastHandle);
            b.putString("last_direction",lastDirection);b.putBoolean("last_long",lastLong);b.putInt("noops",noops);b.putInt("unsupported",unsupported);
            java.util.Set<String> missing=new java.util.TreeSet<>();
            for(String id:dev.edgeflow.GestureHandleConfig.IDS)for(String direction:new String[]{"up","straight","down"})for(boolean longer:new boolean[]{false,true}){
                String action=mapped.profile.action("",id,direction,longer);if(!supported(action))missing.add(action);
            }
            for(var entry:mapped.profile.values.entrySet())if(entry.getKey().startsWith("app_override/")&&entry.getValue() instanceof String action&&!supported(action))missing.add(action);
            b.putString("unsupported_actions",String.join(",",missing));
        }
        return b;
    }
    void update(GestureProfile profile){
        scene.profile(profile);
        if(!active||mapped==null)return; // Updates never arm or extend a session.
        if(!profile.enabled()||scene.accessibilityInput()){close("configuration-disabled");return;}
        if(mapped.profile.same(profile))return;
        if(mapped.tracking())contactCancel="configuration-update";handler.removeCallbacks(longCheck);mapped.reset();
        if(tools!=null)tools.close();
        mapped=new MappedGestureSession(profile,scene.area(),density,claimDistance,id->trialContact||supported(id));
        if(feedback!=null)feedback.update(profile);emit();
    }
    void settingsPreview(android.os.IBinder owner,Bundle regions,Bundle colors){if(feedback!=null)feedback.settingsPreview(owner,regions,colors);}
    void cancelContact(){if(mapped!=null){if(mapped.tracking())contactCancel="explicit-cancel";mapped.cancel();}handler.removeCallbacks(longCheck);if(feedback!=null)feedback.clear();}
    private void reportTrial(MappedGestureSession.Trial value){
        if(trial==null||value==null)return;Bundle b=new Bundle();b.putString("trial_event","result");b.putString("handle",value.handle());b.putInt("kind",value.kind());b.putString("direction",value.direction());b.putString("action",value.action());b.putString("source",value.source());b.putString("reason",value.reason());b.putFloat("distance",value.distance()/density);b.putFloat("short_distance",value.shortDistance()/density);b.putFloat("long_distance",value.longDistance()==Float.MAX_VALUE?Float.MAX_VALUE:value.longDistance()/density);b.putLong("duration",value.duration());b.putLong("elapsed",value.elapsed());b.putFloat("angle",value.angle());trial.send(trialOwner,b);
    }
    private boolean mappedEligible(SystemScene.Focus focus){
        if(scene.accessibilityInput()){mapped.cancel();handler.removeCallbacks(longCheck);handler.post(()->close("accessibility-input-active"));return false;}
        return active&&eligible()&&scene.allowed(mapped.profile,focus);
    }
    private void advanceMapped(long now)throws ReflectiveOperationException{
        handler.removeCallbacks(longCheck);
        if(mapped.wantsClaim(now)){
            wouldClaim++;api.claim(monitor);mapped.claim();claims++;
        }
        if(feedback!=null)feedback.progress(mapped.feedback(),mapped.owned(),trialContact);
        MappedGestureSession.Action action=mapped.poll(now);
        if(trialContact){if(action!=null){reportTrial(mapped.report(action.longer()?2:1,now));if(feedback!=null)feedback.release();}}
        else performMapped(action);
        long delay=mapped.pending(now);
        if(active&&delay>0)handler.postDelayed(longCheck,delay);
    }
    private void performMapped(MappedGestureSession.Action action){
        if(action==null||!active)return;
        try{
            SystemScene.Focus focus=scene.focus();
            if(!mappedEligible(focus)||!focus.key().equals(action.focus())){if(feedback!=null)feedback.clear();return;}
            lastActionError="";lastAction=action.id();lastHandle=action.handle();lastDirection=action.direction();lastLong=action.longer();
            if(!supported(action.id())){unsupported++;if(feedback!=null)feedback.release();emit();return;}
            if(feedback!=null)feedback.action(action.longer());
            if(action.id().equals("none"))noops++;
            else if(toolWindowsAvailable&&isTool(action.id())){
                android.os.ResultReceiver completion=new android.os.ResultReceiver(handler){
                    @Override protected void onReceiveResult(int code,Bundle result){
                        if(!active)return;
                        if(result.getBoolean("tool_shown")||result.getBoolean("action_dispatched"))actions++;
                        else{rejectedActions++;lastActionError=result.getString("tool_error","Tool did not attach");}
                        emit();
                    }
                };
                if(tools==null)tools=new ToolRuntime(context,handler,()->{
                    try{return active&&eligible()&&!scene.accessibilityInput();}
                    catch(RuntimeException failure){return false;}
                });
                tools.show(action.id(),action.anchor(),completion);
            }
            else if(action.id().startsWith("app:")){
                try{MappedActions.execute(context,api,action.id());actions++;}
                catch(android.content.ActivityNotFoundException | SecurityException unavailable){
                    rejectedActions++;
                    lastActionError=unavailable.getClass().getSimpleName()+": "+unavailable.getMessage();
                }
            }
            else if(dev.edgeflow.BackendActions.SPLIT_SCREEN.equals(action.id())){
                String refusal=api.splitScreen.enter(context,action.focus());
                if(refusal==null)actions++;
                else{
                    rejectedActions++;lastActionError=refusal;
                    if(eligible())android.widget.Toast.makeText(context,refusal,android.widget.Toast.LENGTH_SHORT).show();
                }
            }
            else if(MappedActions.execute(context,api,action.id()))actions++;
            else if(dev.edgeflow.BackendActions.POWER_MENU.equals(action.id())){rejectedActions++;lastActionError="System power menu unavailable";}
            else{rejectedActions++;error="Action rejected: "+action.id();handler.post(()->close("action-rejected"));}
        }catch(ReflectiveOperationException | RuntimeException failure){rejectedActions++;fail(failure);}
        emit();
    }
    private static boolean isTool(String action){return dev.edgeflow.ToolHostContract.supports(action);}
    private boolean supported(String action){return MappedActions.supported(action,api)||toolWindowsAvailable&&isTool(action);}
    private boolean mappedInput(MotionEvent motion)throws ReflectiveOperationException{
        boolean handled=mapped.owned();int action=motion.getActionMasked();
        if(api.displayId(motion)!=Display.DEFAULT_DISPLAY){mapped.cancel();handler.removeCallbacks(longCheck);if(feedback!=null)feedback.clear();return handled;}
        if(action!=MotionEvent.ACTION_DOWN&&!mapped.tracking())return handled;
        contactEvent=action;contactTime=motion.getEventTime();
        if(action==MotionEvent.ACTION_CANCEL){contactCancel="system-cancel";if(trialContact&&trial!=null)trial.event(trialOwner,"cancel",null);if(!trialContact&&tools!=null)tools.motion(action,0,0);mapped.reset();handler.removeCallbacks(longCheck);if(feedback!=null)feedback.release();emit();return handled;}
        if(action==MotionEvent.ACTION_POINTER_DOWN||action==MotionEvent.ACTION_POINTER_UP){contactCancel="multi-touch";if(trialContact&&trial!=null)trial.event(trialOwner,"cancel",null);mapped.cancel();handler.removeCallbacks(longCheck);if(feedback!=null)feedback.release();return handled;}
        SystemScene.Focus focus=scene.focus();boolean allowed=mappedEligible(focus);
        if(!allowed){if(mapped.tracking())contactCancel="input-scene:"+SystemScene.flags;mapped.cancel();handler.removeCallbacks(longCheck);if(feedback!=null)feedback.clear();return handled;}
        if(action==MotionEvent.ACTION_DOWN){
            contactCancel=contactEnd="";
            handler.removeCallbacks(longCheck);
            boolean registered=trial!=null&&trial.active(focus);
            trialContact=focus.trialPage();trialOwner=registered?trial.owner():null;
            // Keep an unregistered trial contact observational through UP, including during recovery.
            if(mapped.begin(motion.getPointerId(0),motion.getRawX(),motion.getRawY(),motion.getEventTime(),trialContact?(registered?trial.application():""):focus.pkg(),focus.key(),
                    motion.getPointerCount()==1&&clearOrigin(motion.getRawX(),motion.getRawY()),trialContact)){
                edgeStarts++;if(feedback!=null)feedback.begin(mapped.feedback(),focus.key());
                if(registered)trial.event(trialOwner,"begin",mapped.feedback().handle());
            }
        }else if(action==MotionEvent.ACTION_MOVE){
            mapped.move(motion.getPointerId(0),motion.getPointerCount(),motion.getRawX(),motion.getRawY(),motion.getEventTime(),focus.key(),allowed);
            if(!mapped.rejection().isEmpty())contactCancel=mapped.rejection();
            advanceMapped(motion.getEventTime());
        }else if(action==MotionEvent.ACTION_UP){
            handler.removeCallbacks(longCheck);
            if(trialContact){reportTrial(mapped.endTrial(motion.getPointerId(0),motion.getRawX(),motion.getRawY(),motion.getEventTime(),focus.key(),allowed));if(feedback!=null)feedback.release();emit();return handled;}
            mapped.move(motion.getPointerId(0),motion.getPointerCount(),motion.getRawX(),motion.getRawY(),motion.getEventTime(),focus.key(),allowed);
            if(feedback!=null)feedback.progress(mapped.feedback(),mapped.owned());
            MappedGestureSession.Trial diagnostic=mapped.report(0,motion.getEventTime());
            MappedGestureSession.Action result=mapped.end(motion.getPointerId(0),motion.getRawX(),motion.getRawY(),motion.getEventTime(),focus.key(),allowed);
            contactEnd=result!=null?"action":diagnostic==null?"not-tracking":diagnostic.reason()+" distance="+diagnostic.distance()+" elapsed="+diagnostic.elapsed();
            // Finish the original input before injecting a navigation key.
            MappedGestureSession owner=mapped;
            if(feedback!=null)feedback.release();
            if(result!=null)handler.post(()->{if(mapped==owner)performMapped(result);});emit();
        }
        if(!trialContact&&tools!=null&&(handled||mapped.owned()))tools.motion(action,motion.getRawX(),motion.getRawY());
        return handled||mapped.owned();
    }
    private void emit(){if(observer!=null)observer.accept(snapshot());}
    private void fail(Throwable failure) {
        Throwable cause=failure.getCause()==null?failure:failure.getCause();
        error=cause.getClass().getSimpleName()+": "+String.valueOf(cause.getMessage());
        decisions.cancel();handler.post(()->close("failed"));
        if(mapped!=null)mapped.cancel();handler.removeCallbacks(longCheck);
        if(feedback!=null)feedback.clear();
    }
    private void performBack() {
        if(!active || !eligible())return;
        try {
            if(api.back())actions++;else {rejectedActions++;error="Input injection rejected";close("action-rejected");}
        }catch(ReflectiveOperationException | RuntimeException failure){rejectedActions++;fail(failure);}
        emit();
    }

    private final class Receiver extends InputEventReceiver {
        Receiver(InputChannel channel){super(channel,handler.getLooper());}
        @Override public void onBatchedInputEventPending(int source){consumeBatchedInputEvents(-1);}
        @Override public void onInputEvent(InputEvent event) {
            boolean handled=false;
            try {
                if(!active || !(event instanceof MotionEvent motion))return;
                if(TouchObservation.observe(motion)){toolEvents++;return;}
                if(!motion.isFromSource(InputDevice.SOURCE_TOUCHSCREEN))return;
                if(mapped!=null){handled=mappedInput(motion);return;}
                handled=decisions.owned();
                int action=motion.getActionMasked();
                if(api.displayId(motion)!=Display.DEFAULT_DISPLAY){decisions.cancel();return;}
                if(action!=MotionEvent.ACTION_DOWN && !decisions.tracking())return;
                boolean allowed=(action!=MotionEvent.ACTION_DOWN && action!=MotionEvent.ACTION_UP) || eligible();
                if(!allowed){decisions.cancel();handler.post(()->close("inactive-display"));return;}
                if(action==MotionEvent.ACTION_DOWN) {
                    if(decisions.begin(motion.getPointerId(0),motion.getRawX(),motion.getRawY(),motion.getEventTime(),
                            motion.getPointerCount()==1 && clearOrigin(motion.getRawX(),motion.getRawY())))edgeStarts++;
                } else if(action==MotionEvent.ACTION_MOVE) {
                    if(decisions.move(motion.getPointerId(0),motion.getPointerCount(),motion.getRawX(),motion.getRawY(),
                            motion.getEventTime(),allowed)==EdgeInputSession.Decision.CLAIM) {
                        wouldClaim++;
                        if(capture && eligible()){api.claim(monitor);decisions.claim();claims++;}
                        else decisions.cancel();
                    }
                } else if(action==MotionEvent.ACTION_UP) {
                    if(decisions.end(motion.getPointerId(0),motion.getRawX(),motion.getRawY(),motion.getEventTime(),allowed)
                            ==EdgeInputSession.Decision.BACK)handler.post(NativeInputSession.this::performBack);
                    emit();
                } else if(action==MotionEvent.ACTION_CANCEL) {
                    decisions.reset();emit();
                } else if(action==MotionEvent.ACTION_POINTER_DOWN || action==MotionEvent.ACTION_POINTER_UP) {
                    decisions.cancel();
                }
                handled|=decisions.owned();
            }catch(ReflectiveOperationException | RuntimeException | LinkageError failure){fail(failure);}
            finally {finishInputEvent(event,handled);}
        }
    }

    public void close(){close("stopped");}
    void close(String why) {
        if(closed)return;
        closed=true;
        active=false;reason=why;decisions.reset();handler.removeCallbacks(expiry);
        if(mapped!=null)mapped.reset();handler.removeCallbacks(longCheck);
        if(feedback!=null)feedback.close();
        if(tools!=null){if(sharedTools&&"display-changed".equals(why))tools.geometryChanging();else tools.close();}
        if(listenerRegistered){try{displays.unregisterDisplayListener(changes);}catch(RuntimeException failure){error="Display listener disposal: "+failure.getClass().getSimpleName();}listenerRegistered=false;}
        Receiver old=receiver;receiver=null;if(old!=null)try{old.dispose();}catch(RuntimeException | LinkageError failure){error="Receiver disposal: "+failure.getClass().getSimpleName();}
        Object oldMonitor=monitor;monitor=null;
        if(oldMonitor!=null)try{api.close(oldMonitor);}catch(ReflectiveOperationException | RuntimeException failure){
            error="Monitor disposal: "+failure.getClass().getSimpleName();
        }
        emit();
    }
}
