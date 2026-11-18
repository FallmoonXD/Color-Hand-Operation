package dev.edgeflow.lsp;

import android.content.Context;
import android.graphics.Point;
import android.hardware.display.DisplayManager;
import android.hardware.input.InputManager;
import android.os.*;
import android.view.*;
import java.lang.reflect.Method;
import java.util.*;

/** Framework-owned touch sequence, cancelled with either endpoint's window lease. */
final class ToolTouchInjector {
    private final Handler main=new Handler(Looper.getMainLooper());
    private final Context context;
    private final java.util.function.BooleanSupplier allowed;
    private final SystemScene scene;
    private final Object manager;
    private final Method inject,setDisplay;
    private final Display display;
    private final Method currentUser;
    private java.util.function.BooleanSupplier requestAllowed=()->false;
    private TouchSequence.Delivery delivery;
    private ResultReceiver result;
    private List<TouchSequence.Step> steps;
    private String focus;
    private int rotation,index;
    private long start,down;
    private final Runnable frame=this::frame;
    ToolTouchInjector(Context context,java.util.function.BooleanSupplier allowed)throws ReflectiveOperationException{
        this.context=context;this.allowed=allowed;scene=new SystemScene(context);
        manager=context.getSystemService(InputManager.class);inject=manager.getClass().getMethod("injectInputEvent",InputEvent.class,int.class);
        setDisplay=MotionEvent.class.getMethod("setDisplayId",int.class);display=context.getSystemService(DisplayManager.class).getDisplay(0);
        currentUser=android.app.ActivityManager.class.getMethod("getCurrentUser");
        if(display==null)throw new IllegalStateException("No default display");
    }
    void start(float[] points,long duration,int count,long base,String expectedFocus,int expectedRotation,java.util.function.BooleanSupplier valid,ResultReceiver reply){
        main.post(()->{
            cancel();result=reply;requestAllowed=valid;
            try{
                if(!usable())throw new IllegalStateException("Touch input is stopped");
                Point size=new Point();display.getRealSize(size);rotation=expectedRotation;focus=expectedFocus;
                if(focus.isEmpty()||rotation!=display.getRotation()||!focus.equals(scene.focus().key()))throw new IllegalStateException("Touch target changed");
                int interval=Math.max(4,Math.min(16,Math.round(1000/Math.max(1,display.getRefreshRate()))));
                steps=TouchSequence.create(points,duration,count,interval,size.x,size.y);index=0;start=base;
                delivery=new TouchSequence.Delivery(this::send);frame();
            }catch(RuntimeException failure){finish(false);}
        });
    }
    private void frame(){
        if(delivery==null)return;
        try{
        if(!usable()||display.getRotation()!=rotation||!focus.equals(scene.focus().key())){cancel();return;}
        TouchSequence.Step step=steps.get(index);long due=start+step.offset(),now=SystemClock.uptimeMillis();
        // A held pointer has no MOVE events. Still cancel promptly on a target change.
        if(now<due){main.postAtTime(frame,Math.min(due,now+32));return;}
        index++;
        if(!delivery.send(step)){finish(false);return;}
        if(index==steps.size()){finish(delivery.finish());return;}
        main.postAtTime(frame,Math.min(start+steps.get(index).offset(),SystemClock.uptimeMillis()+32));
        }catch(RuntimeException|LinkageError failure){android.util.Log.w("CHOTouch","Touch target became unavailable",failure);cancel();}
    }
    private boolean send(int action,float x,float y){
        long now=SystemClock.uptimeMillis();if(action==MotionEvent.ACTION_DOWN)down=start+steps.get(index-1).offset();
        MotionEvent.PointerProperties pointer=new MotionEvent.PointerProperties();pointer.id=0;pointer.toolType=MotionEvent.TOOL_TYPE_UNKNOWN;
        MotionEvent.PointerCoords point=new MotionEvent.PointerCoords();point.x=x;point.y=y;point.pressure=1;point.size=1;
        MotionEvent event=MotionEvent.obtain(down,now,action,1,new MotionEvent.PointerProperties[]{pointer},new MotionEvent.PointerCoords[]{point},0,0,1,1,KeyCharacterMap.VIRTUAL_KEYBOARD,0,InputDevice.SOURCE_TOUCHSCREEN,0);
        long identity=Binder.clearCallingIdentity();
        try{setDisplay.invoke(event,0);return Boolean.TRUE.equals(inject.invoke(manager,event,0));}
        catch(ReflectiveOperationException|RuntimeException failure){android.util.Log.w("CHOTouch","Touch injection rejected",failure);return false;}
        finally{event.recycle();Binder.restoreCallingIdentity(identity);}
    }
    private boolean usable(){
        try{return allowed.getAsBoolean()&&requestAllowed.getAsBoolean()&&context.getSystemService(PowerManager.class).isInteractive()&&!context.getSystemService(android.app.KeyguardManager.class).isKeyguardLocked()&&((Number)currentUser.invoke(null)).intValue()==0;}
        catch(ReflectiveOperationException|RuntimeException unavailable){return false;}
    }
    void cancel(){
        if(Looper.myLooper()!=main.getLooper()){main.post(this::cancel);return;}
        if(delivery!=null)delivery.cancel();finish(false);
    }
    private void finish(boolean completed){
        main.removeCallbacks(frame);delivery=null;steps=null;
        ResultReceiver reply=result;result=null;if(reply!=null){Bundle state=new Bundle();state.putBoolean("completed",completed);reply.send(0,state);}
    }
}
