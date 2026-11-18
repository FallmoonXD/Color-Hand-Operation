package dev.edgeflow.lsp;

import android.app.ActivityManager;
import android.app.TaskStackListener;
import android.content.Context;
import android.graphics.*;
import android.os.*;
import android.view.*;
import android.widget.FrameLayout;
import dev.edgeflow.*;
import java.lang.reflect.Method;

/** Two passive edge surfaces, using SystemUI's navigation-panel window type. */
final class GestureFeedbackWindows implements AutoCloseable {
    private final int navigationPanel;
    private final Context context,windowContext,resources;
    private final Handler main;
    private final SystemScene scene;
    private final GestureHandleConfig.Area area;
    private final WindowManager windows;
    private final java.util.function.BooleanSupplier eligible;
    private final java.util.function.Consumer<String> cancelContact;
    private final Runnable geometryChanged;
    private final java.util.List<Rect> holes;
    private final FeedbackTiming timing=new FeedbackTiming();
    private final float density;
    private GestureProfile profile;
    private Side left,right,current;
    private String focus="",error="",lastHaptic="";
    private boolean closed,shown,released=true,taskRegistered;
    private int begins,releases,hapticRequests,hapticAccepted,lastDuration,lastAmplitude;
    private Object taskService;
    private IBinder previewOwner;private IBinder.DeathRecipient previewDeath;
    private final java.util.Map<String,float[]> previewRegions=new java.util.HashMap<>();
    private Bundle previewColors=new Bundle();private String previewFocus="";private boolean previewVisible;
    private Method unregisterTasks;
    private final Runnable sceneSignal=this::schedule;
    private final Runnable checkScene=this::refreshScene;
    private final TaskStackListener tasks=new TaskStackListener(){
        @Override public void onTaskStackChanged(){schedule();}
        @Override public void onTaskMovedToFront(ActivityManager.RunningTaskInfo task){schedule();}
    };

    GestureFeedbackWindows(Context context,Handler main,SystemScene scene,GestureProfile profile,
                           java.util.function.BooleanSupplier eligible,java.util.function.Consumer<String> cancelContact,Runnable geometryChanged)throws ReflectiveOperationException,android.content.pm.PackageManager.NameNotFoundException{
        this.context=context;this.main=main;this.scene=scene;this.profile=profile;this.eligible=eligible;this.cancelContact=cancelContact;
        this.geometryChanged=geometryChanged;
        area=scene.area();density=context.getResources().getDisplayMetrics().density;
        Display display=context.getSystemService(android.hardware.display.DisplayManager.class).getDisplay(0);
        navigationPanel=WindowManager.LayoutParams.class.getField("TYPE_NAVIGATION_BAR_PANEL").getInt(null);
        windowContext=context.createWindowContext(display,navigationPanel,null);
        windows=windowContext.getSystemService(WindowManager.class);
        DisplayCutout cutout=windows.getMaximumWindowMetrics().getWindowInsets().getDisplayCutout();
        holes=area.landscape()&&cutout!=null?cutout.getBoundingRects():java.util.Collections.emptyList();
        resources=context.createPackageContext(BackendIdentity.module(),Context.CONTEXT_IGNORE_SECURITY).createConfigurationContext(windowContext.getResources().getConfiguration());
        try{
            left=new Side(true);right=new Side(false);
            windows.addView(left,parameters(true));left.added=true;windows.addView(right,parameters(false));right.added=true;
            Class<?> callback=Class.forName("android.app.ITaskStackListener"),manager=Class.forName("android.app.IActivityTaskManager");
            taskService=Class.forName("android.app.ActivityTaskManager").getMethod("getService").invoke(null);
            unregisterTasks=manager.getMethod("unregisterTaskStackListener",callback);
            manager.getMethod("registerTaskStackListener",callback).invoke(taskService,tasks);taskRegistered=true;
            SystemScene.listen(sceneSignal);refreshScene();
        }catch(ReflectiveOperationException|RuntimeException|LinkageError failure){close();throw failure;}
    }
    private int width(){
        int width=Math.min(area.displayWidth/2,Math.round(135f*density/3.5f+8*density));
        for(String id:GestureHandleConfig.IDS)width=Math.max(width,GestureHandleConfig.bounds(id,profile.values,density,area)[2]);
        for(var entry:previewRegions.entrySet()){float[] p=entry.getValue();width=Math.max(width,GestureHandleConfig.bounds(entry.getKey(),area.landscape()?p[3]:p[0],p[1],p[2],density,area)[2]);}
        return width;
    }
    private WindowManager.LayoutParams parameters(boolean isLeft)throws ReflectiveOperationException{
        int width=width();
        WindowManager.LayoutParams p=new WindowManager.LayoutParams(width,area.displayHeight,navigationPanel,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE|WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE|WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,PixelFormat.TRANSLUCENT);
        p.gravity=Gravity.TOP|Gravity.LEFT;p.x=isLeft?area.handleEdge(true):area.handleEdge(false)-width;p.y=0;
        p.alpha=1;p.setFitInsetsTypes(0);p.layoutInDisplayCutoutMode=WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_ALWAYS;p.windowAnimations=0;
        p.setTitle("CHO LSP feedback "+(isLeft?"L":"R"));
        // Same trusted-overlay contract as the installed DisplayBackGestureHandlerImpl.
        WindowManager.LayoutParams.class.getMethod("setTrustedOverlay").invoke(p);
        return p;
    }
    private void schedule(){if(closed)return;main.removeCallbacks(checkScene);main.post(checkScene);}
    private void refreshScene(){
        if(closed)return;
        try{
            if(!area.equals(scene.area())){geometryChanged.run();return;}
            SystemScene.Focus now=scene.focus();
            boolean previewNow=previewOwner!=null&&previewFocus.equals(now.key());if(previewNow!=previewVisible){previewVisible=previewNow;left.invalidate();right.invalidate();}
            boolean visible=eligible.getAsBoolean()&&profile.enabled()&&scene.allowed(profile,now)&&!(area.landscape()&&profile.hideLandscape());
            if(!visible||(!focus.isEmpty()&&!focus.equals(now.key()))){String why=!visible?"scene-hidden:"+SystemScene.flags:"focus-changed";clear();cancelContact.accept(why);}
            shown=visible;left.setVisibility(visible?View.VISIBLE:View.GONE);right.setVisibility(visible?View.VISIBLE:View.GONE);
        }catch(RuntimeException failure){disable(failure);}
    }
    void update(GestureProfile profile){
        if(closed)return;clear();this.profile=profile;
        try{windows.updateViewLayout(left,parameters(true));windows.updateViewLayout(right,parameters(false));left.invalidate();right.invalidate();refreshScene();}
        catch(ReflectiveOperationException|RuntimeException failure){disable(failure);}
    }
    void settingsPreview(IBinder owner,Bundle regions,Bundle colors){
        if(closed||owner==null)return;
        if(regions==null||regions.isEmpty()){if(owner==previewOwner)clearPreview();return;}
        if(!BackendIdentity.APP.equals(scene.focus().pkg()))return;
        if(owner!=previewOwner){
            clearPreview();previewOwner=owner;previewDeath=()->main.post(()->{if(owner==previewOwner)clearPreview();});
            try{owner.linkToDeath(previewDeath,0);}catch(RemoteException dead){clearPreview();return;}
        }
        previewRegions.clear();
        for(String id:GestureHandleConfig.IDS){float[] p=regions.getFloatArray(id);if(p==null)continue;if(p.length!=4)throw new IllegalArgumentException("Invalid preview");for(float v:p)if(!Float.isFinite(v))throw new IllegalArgumentException("Invalid preview value");previewRegions.put(id,p.clone());}
        previewColors=colors==null?new Bundle():new Bundle(colors);previewFocus=scene.focus().key();previewVisible=true;
        try{windows.updateViewLayout(left,parameters(true));windows.updateViewLayout(right,parameters(false));}catch(ReflectiveOperationException failure){clearPreview();}
        left.invalidate();right.invalidate();
    }
    private void clearPreview(){
        if(previewOwner!=null&&previewDeath!=null)previewOwner.unlinkToDeath(previewDeath,0);
        previewOwner=null;previewDeath=null;previewRegions.clear();previewFocus="";previewVisible=false;if(left!=null)left.invalidate();if(right!=null)right.invalidate();
    }
    void begin(MappedGestureSession.Feedback frame,String focus){
        if(closed||frame==null)return;clear();refreshScene();if(!shown)return;
        this.focus=focus;current=frame.left()?left:right;released=false;timing.begin(GestureHaptics.quick(profile.values));
        current.wave.begin(frame.left(),frame.startY());begins++;
    }
    void progress(MappedGestureSession.Feedback frame,boolean owned){
        progress(frame,owned,false);
    }
    void progress(MappedGestureSession.Feedback frame,boolean owned,boolean silent){
        if(closed||current==null||frame==null||released)return;
        if(frame.cancelled()){release();return;}
        if(!frame.fired())current.wave.update(frame.inward(),frame.vertical(),frame.direction());
        if(!silent&&timing.progress(frame.shortReady(),owned))haptic(false);
    }
    void action(boolean longer){if(closed||current==null)return;if(timing.action(longer))haptic(longer);release();}
    private void haptic(boolean longer){
        try{
            GestureHapticPlayer.Result result=GestureHapticPlayer.perform(current,profile.values,longer);
            lastHaptic=result.mode();lastDuration=result.duration();lastAmplitude=result.amplitude();
            if(result.requested())hapticRequests++;if(result.accepted())hapticAccepted++;
        }catch(RuntimeException failure){error="Haptic: "+failure.getClass().getSimpleName();}
    }
    void release(){if(closed||current==null||released)return;current.wave.release();released=true;focus="";releases++;}
    void clear(){if(left!=null)left.wave.clear();if(right!=null)right.wave.clear();current=null;focus="";released=true;}
    private void disable(Throwable failure){error=failure.getClass().getSimpleName()+": "+failure.getMessage();close();}
    void describe(Bundle b){
        b.putBoolean("feedback_attached",!closed);b.putBoolean("feedback_visible",shown&&!closed);b.putString("feedback_error",error);
        b.putInt("wave_begins",begins);b.putInt("wave_releases",releases);b.putInt("haptic_requests",hapticRequests);b.putInt("haptic_accepted",hapticAccepted);
        b.putBoolean("wave_visible",current!=null&&current.wave.isDrawing());b.putFloat("wave_depth",current==null?0:current.wave.currentDepth());
        b.putString("last_haptic",lastHaptic);b.putInt("haptic_duration",lastDuration);b.putInt("haptic_amplitude",lastAmplitude);
    }
    @Override public void close(){
        if(closed)return;closed=true;shown=false;main.removeCallbacks(checkScene);SystemScene.unlisten(sceneSignal);clear();clearPreview();
        if(taskRegistered)try{unregisterTasks.invoke(taskService,tasks);}catch(ReflectiveOperationException|RuntimeException failure){error="Task listener disposal: "+failure.getClass().getSimpleName();}
        taskRegistered=false;
        for(Side side:new Side[]{left,right})if(side!=null&&side.added){try{windows.removeViewImmediate(side);}catch(RuntimeException failure){error="Feedback window disposal: "+failure.getClass().getSimpleName();}side.added=false;}
    }
    private final class Side extends FrameLayout {
        final boolean isLeft;
        boolean added;
        final GestureWaveRenderer wave;
        final Paint paint=new Paint(Paint.ANTI_ALIAS_FLAG);
        Side(boolean isLeft){
            super(windowContext);this.isLeft=isLeft;setFocusable(false);setClickable(false);setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO_HIDE_DESCENDANTS);
            var res=resources.getResources();String pkg=BackendIdentity.module();
            wave=new GestureWaveRenderer(windowContext,resources.getDrawable(res.getIdentifier("oho_gesture_arrow","drawable",pkg)),res.getDimensionPixelSize(res.getIdentifier("oho_curve_arrow_size","dimen",pkg)));
            addView(wave,new FrameLayout.LayoutParams(-1,-1));setVisibility(View.GONE);
            setOnApplyWindowInsetsListener((view,insets)->{schedule();return insets;});
        }
        @Override protected void dispatchDraw(Canvas canvas){
            super.dispatchDraw(canvas);int save=canvas.save();
            int origin=isLeft?area.handleEdge(true):area.handleEdge(false)-getWidth();
            for(Rect hole:holes)canvas.clipOutRect(hole.left-origin,hole.top,hole.right-origin,hole.bottom);
            for(String id:GestureHandleConfig.IDS){
                if(GestureHandleConfig.isLeft(id)!=isLeft||!profile.flag(id+"_enabled",!GestureHandleConfig.isExtra(id)))continue;
                int color=GestureAppearance.visibleColor(profile.values,id);if((color>>>24)==0)continue;
                int[] bounds=GestureHandleConfig.bounds(id,profile.values,density,area);paint.setColor(color);
                float x=bounds[0]-origin,radius=bounds[2]/2f;
                canvas.drawRoundRect(x,bounds[1],x+bounds[2],bounds[1]+bounds[3],radius,radius,paint);
            }
            if(previewVisible)for(var entry:previewRegions.entrySet()){
                String id=entry.getKey();if(GestureHandleConfig.isLeft(id)!=isLeft)continue;float[] p=entry.getValue();int[] bounds=GestureHandleConfig.bounds(id,area.landscape()?p[3]:p[0],p[1],p[2],density,area);
                paint.setColor((previewColors.getInt(id,0xff188dff)&0xffffff)|0x88000000);float x=bounds[0]-origin;
                canvas.drawRoundRect(x,bounds[1],x+bounds[2],bounds[1]+bounds[3],3*density,3*density,paint);
            }
            canvas.restoreToCount(save);
        }
    }
}
