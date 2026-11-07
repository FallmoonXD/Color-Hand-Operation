package dev.edgeflow;

import android.content.Context;
import android.content.SharedPreferences;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Rect;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import android.os.VibrationEffect;
import android.os.Vibrator;
import android.os.VibratorManager;
import android.view.HapticFeedbackConstants;
import android.view.MotionEvent;
import android.view.View;
import java.util.Collections;

/** The narrow touch surface owns a complete gesture, even after leaving its bounds. */
public final class EdgeHandleView extends View {
    private final GestureService service;
    private final boolean left;
    private final String handleId;
    private final Handler main=new Handler(Looper.getMainLooper());
    private final Paint paint=new Paint(Paint.ANTI_ALIAS_FLAG);
    private GestureRecognizer gesture;
    private GestureTrial trial;
    private boolean trialContact;
    private String application="";
    private long decisionTime;
    private boolean hapticReady,quickVibration;
    private int pointer=-1;
    private float visibility;
    private final Runnable longCheck=new Runnable(){public void run(){
        decisionTime=SystemClock.uptimeMillis();
        if(gesture!=null&&gesture.shouldFireLong(decisionTime))dispatch(true);
    }};
    public EdgeHandleView(GestureService service,boolean left){this(service,left?"left":"right");}
    public EdgeHandleView(GestureService service,String handleId){
        super(service);this.service=service;this.handleId=handleId;this.left=GestureHandleConfig.isLeft(handleId);setFocusable(false);setClickable(true);
        setContentDescription((handleId.startsWith("extra2_")?"额外第二组":GestureHandleConfig.isExtra(handleId)?"额外":"")+(left?"左侧手势区域":"右侧手势区域"));
        refreshAppearance();
    }
    /** Repaint only when effective ARGB changes; preference updates keep this input window. */
    void refreshAppearance(){SharedPreferences p=service.getSharedPreferences("edgeflow",Context.MODE_PRIVATE);int color=AppPrefs.visibleHandleColor(p,handleId);boolean changed=paint.getColor()!=color;paint.setColor(color);visibility=paint.getAlpha()/255f;if(changed)invalidate();}
    @Override protected void onSizeChanged(int w,int h,int oldw,int oldh){super.onSizeChanged(w,h,oldw,oldh);setSystemGestureExclusionRects(Collections.singletonList(new Rect(0,0,w,h)));}
    @Override protected void onDraw(Canvas canvas){if(visibility>0){float radius=getWidth()/2f;canvas.drawRoundRect(0,0,getWidth(),getHeight(),radius,radius,paint);}}
    @Override public boolean onTouchEvent(MotionEvent event){
        int action=event.getActionMasked();
        if(action==MotionEvent.ACTION_DOWN)trialContact=GestureTrial.current()!=null;
        boolean testing=trialContact||GestureTrial.current()!=null;
        boolean handled=handleGestureEvent(event);
        if(action==MotionEvent.ACTION_UP||action==MotionEvent.ACTION_CANCEL)trialContact=false;
        if(testing)return handled;
        QuickLauncherOverlay.onGestureMotion(event.getActionMasked(),event.getRawX(),event.getRawY());
        MagnificationOverlay.onGestureMotion(event.getActionMasked(),event.getRawX(),event.getRawY());
        ScreenMoveOverlay.onGestureMotion(event.getActionMasked(),event.getRawX(),event.getRawY());
        return handled;
    }
    private boolean handleGestureEvent(MotionEvent event){
        int action=event.getActionMasked();
        if(action==MotionEvent.ACTION_DOWN){
            if(gesture!=null)cancel();
            trial=GestureTrial.current();
            application=service.gesturePackage();
            if(!service.canStartGesture(handleId,application)){trial=null;return false;}
            if(trial!=null)trial.begin(handleId);
            main.removeCallbacks(longCheck);pointer=event.getPointerId(0);hapticReady=false;
            SharedPreferences p=service.getSharedPreferences("edgeflow",Context.MODE_PRIVATE);float density=getResources().getDisplayMetrics().density;
            quickVibration=GestureHaptics.quick(p.getAll());
            // OHO's per-handle long switch disables long classification/timers,
            // while long-distance releases still execute the selected short action.
            float longDistance=p.getBoolean(handleId+"_long_enabled",true)?number(p,"long_trigger_dp",87.43f)*density:Float.MAX_VALUE;
            gesture=new GestureRecognizer(number(p,"trigger_dp",39.43f)*density,longDistance,p.getInt("long_delay_ms",300),GestureAngles.read(p.getAll(),false),GestureAngles.read(p.getAll(),true),p.getBoolean("long_immediate",false),p.getBoolean("quick_action_short",false),p.getBoolean("quick_action_long",true));
            gesture.begin(left,event.getRawX(),event.getRawY(),event.getDownTime());replica.NativeState.setGesture(gesture.anchor());service.beginWave(left,event.getRawY());return true;
        }
        if(gesture==null)return false;
        if(action==MotionEvent.ACTION_POINTER_DOWN||action==MotionEvent.ACTION_CANCEL){cancel();return true;}
        if(event.findPointerIndex(pointer)<0){cancel();return true;}
        if(action==MotionEvent.ACTION_MOVE||action==MotionEvent.ACTION_UP){
            // Touch duration belongs to the input event, not time spent queued on
            // the UI thread. Handler timers use this same uptime clock domain.
            long now=event.getEventTime();decisionTime=now;gesture.move(event.getRawX(),event.getRawY(),now);replica.NativeState.setGesture(gesture.anchor());
            if(!gesture.isFired())service.updateWave(gesture.inward(),gesture.vertical(),gesture.direction(),handleId,gesture.isLongDistanceReached(),trial==null?application:trial.application);
            // OHO latches its first short-threshold feedback for the whole contact.
            // Retracting and crossing again must not produce repeated vibrations.
            if(gesture.isShortReady()&&!hapticReady){hapticReady=true;if(quickVibration&&trial==null)haptic(false);}
            if(gesture.shouldFireShort()){
                dispatch(false);
                if(gesture==null)return true;
            }
            main.removeCallbacks(longCheck);long delay=gesture.pendingLongDelay(now);
            if(action==MotionEvent.ACTION_MOVE&&!gesture.isFired()&&delay>=0)main.postAtTime(longCheck,now+delay);
            if(action==MotionEvent.ACTION_UP){boolean alreadyFired=gesture.isFired();int result=gesture.finish(now);if(result!=0)dispatch(result==2);main.removeCallbacks(longCheck);if(!alreadyFired&&result==0){if(trial!=null)trial.show(handleId,gesture,0,now);service.releaseWave();}pointer=-1;gesture=null;trial=null;}
            return true;
        }
        return true;
    }
    private static float number(SharedPreferences p,String key,float fallback){try{return p.getFloat(key,fallback);}catch(ClassCastException e){try{return p.getInt(key,(int)fallback);}catch(ClassCastException ignored){return fallback;}}}
    private void dispatch(boolean longSwipe){
        if(gesture==null||gesture.isFired())return;
        if(!service.canStartGesture(handleId)){cancel();return;}
        String direction=gesture.directionName(longSwipe);float[] anchor=gesture.anchor();gesture.fired();main.removeCallbacks(longCheck);
        if(trial==null&&(!quickVibration||longSwipe||!hapticReady))haptic(longSwipe);service.releaseWave();
        if(trial!=null)trial.show(handleId,gesture,longSwipe?2:1,decisionTime);
        else if(GestureTrial.current()==null)service.dispatchGesture(handleId,direction,longSwipe,anchor,application);
    }
    private void haptic(boolean longer){
        java.util.Map<String,?> values=service.getSharedPreferences("edgeflow",Context.MODE_PRIVATE).getAll();
        if(GestureHaptics.legacyMuted(values))return;
        if(!GestureHaptics.custom(values)){
            // Delegate both the effect and its strength to ColorOS; a disabled system
            // haptic must not be replaced with an unconditional Vibrator fallback.
            performHapticFeedback(HapticFeedbackConstants.GESTURE_END);
            return;
        }
        boolean legacy=GestureHaptics.legacyAmplitude(values);int duration=legacy?(longer?18:8):GestureHaptics.level(values);
        if(duration==0)return;
        try{VibratorManager manager=(VibratorManager)service.getSystemService(Context.VIBRATOR_MANAGER_SERVICE);Vibrator vibrator=manager==null?null:manager.getDefaultVibrator();
            if(vibrator!=null&&vibrator.hasVibrator())vibrator.vibrate(VibrationEffect.createOneShot(duration,legacy?GestureHaptics.amplitude(values):VibrationEffect.DEFAULT_AMPLITUDE));
        }catch(RuntimeException ignored){}
    }
    public void cancel(){main.removeCallbacks(longCheck);if(gesture!=null){gesture.cancel();if(trial!=null)trial.cancel();else{QuickLauncherOverlay.onGestureMotion(MotionEvent.ACTION_CANCEL,0,0);MagnificationOverlay.onGestureMotion(MotionEvent.ACTION_CANCEL,0,0);ScreenMoveOverlay.onGestureMotion(MotionEvent.ACTION_CANCEL,0,0);}}gesture=null;trial=null;pointer=-1;service.releaseWave();}
    @Override protected void onDetachedFromWindow(){cancel();super.onDetachedFromWindow();}
}
