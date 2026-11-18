package dev.edgeflow.lspfixture;

import android.os.SystemClock;
import android.view.InputDevice;
import android.view.InputEvent;
import android.view.MotionEvent;
import java.lang.reflect.Method;

/** Bounded, deterministic touch sequences for cancellation and multi-pointer tests. */
public final class Events {
    static Object manager;static Method inject;static long down;static boolean mouse;
    static void event(int action,float x,float y,boolean two)throws Exception {
        int count=two?2:1;
        MotionEvent.PointerProperties[] properties=new MotionEvent.PointerProperties[count];
        MotionEvent.PointerCoords[] positions=new MotionEvent.PointerCoords[count];
        for(int i=0;i<count;i++){
            properties[i]=new MotionEvent.PointerProperties();properties[i].id=i;properties[i].toolType=mouse?MotionEvent.TOOL_TYPE_MOUSE:MotionEvent.TOOL_TYPE_FINGER;
            positions[i]=new MotionEvent.PointerCoords();positions[i].x=x+i*10;positions[i].y=y+i*30;positions[i].pressure=1;positions[i].size=1;
        }
        MotionEvent event=MotionEvent.obtain(down,SystemClock.uptimeMillis(),action,count,properties,positions,0,mouse?MotionEvent.BUTTON_PRIMARY:0,1,1,0,0,mouse?InputDevice.SOURCE_MOUSE:InputDevice.SOURCE_TOUCHSCREEN,0);
        event.getClass().getMethod("setDisplayId",int.class).invoke(event,0);
        try {if(!Boolean.TRUE.equals(inject.invoke(manager,event,2)))throw new IllegalStateException("Injection rejected");}
        finally{event.recycle();}
        Thread.sleep(20);
    }
    static void move(float x,float y,float end,boolean two)throws Exception {
        for(int i=1;i<=12;i++)event(MotionEvent.ACTION_MOVE,x+(end-x)*i/12,y,two);
    }
    public static void main(String[] args)throws Exception {
        if(args.length!=4)throw new IllegalArgumentException("case x y delta");
        String kind=args[0];float x=Float.parseFloat(args[1]),y=Float.parseFloat(args[2]),delta=Float.parseFloat(args[3]);
        if(!(kind.equals("cancel")||kind.equals("retreat")||kind.equals("multi-before")||kind.equals("multi-after")||kind.equals("mouse")||kind.equals("hold")||kind.equals("two-scroll")))throw new IllegalArgumentException("case");
        mouse=kind.equals("mouse");
        Class<?> api=Class.forName("android.hardware.input.InputManagerGlobal");manager=api.getMethod("getInstance").invoke(null);inject=api.getMethod("injectInputEvent",InputEvent.class,int.class);
        down=SystemClock.uptimeMillis();event(MotionEvent.ACTION_DOWN,x,y,false);
        if(kind.equals("two-scroll")){
            try{event(MotionEvent.ACTION_POINTER_DOWN|(1<<MotionEvent.ACTION_POINTER_INDEX_SHIFT),x,y,true);
                for(int i=1;i<=12;i++)event(MotionEvent.ACTION_MOVE,x,y+delta*i/12,true);
                event(MotionEvent.ACTION_POINTER_UP|(1<<MotionEvent.ACTION_POINTER_INDEX_SHIFT),x,y+delta,true);event(MotionEvent.ACTION_UP,x,y+delta,false);
            }catch(Exception failure){event(MotionEvent.ACTION_CANCEL,x,y,false);throw failure;}return;
        }
        boolean before=kind.equals("multi-before"),after=kind.equals("multi-after");
        try{
            if(before)event(MotionEvent.ACTION_POINTER_DOWN|(1<<MotionEvent.ACTION_POINTER_INDEX_SHIFT),x,y,true);
            move(x,y,x+delta,before);
            if(kind.equals("retreat")){move(x+delta,y,x+Math.signum(delta)*4,false);x+=Math.signum(delta)*4;}
            else x+=delta;
            if(kind.equals("hold")){System.out.println("HOLD");System.out.flush();Thread.sleep(2500);}
            if(after)event(MotionEvent.ACTION_POINTER_DOWN|(1<<MotionEvent.ACTION_POINTER_INDEX_SHIFT),x,y,true);
            if(before||after)event(MotionEvent.ACTION_POINTER_UP|(1<<MotionEvent.ACTION_POINTER_INDEX_SHIFT),x,y,true);
            event(kind.equals("cancel")?MotionEvent.ACTION_CANCEL:MotionEvent.ACTION_UP,x,y,false);
        }catch(Exception failure){event(MotionEvent.ACTION_CANCEL,x,y,false);throw failure;}
    }
}
