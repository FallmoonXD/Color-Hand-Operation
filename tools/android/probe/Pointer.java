package probe;

import android.os.SystemClock;
import android.view.InputDevice;
import android.view.InputEvent;
import android.view.MotionEvent;

/** Shell-only held pointer probe: inspect drafts during a drag, then UP or CANCEL. */
public final class Pointer {
    public static void main(String[] args) throws Exception {
        if (args.length != 6 && args.length != 8) throw new IllegalArgumentException("x y endX endY holdMs UP|CANCEL [followupTapX followupTapY]");
        float x=Float.parseFloat(args[0]), y=Float.parseFloat(args[1]);
        float endX=Float.parseFloat(args[2]), endY=Float.parseFloat(args[3]);
        long hold=Math.max(0,Math.min(15000,Long.parseLong(args[4])));
        int end=args[5].equals("CANCEL")?MotionEvent.ACTION_CANCEL:MotionEvent.ACTION_UP;
        Class<?> manager=Class.forName("android.hardware.input.InputManagerGlobal");
        Object instance=manager.getMethod("getInstance").invoke(null);
        java.lang.reflect.Method inject=manager.getMethod("injectInputEvent",InputEvent.class,int.class);
        long down=SystemClock.uptimeMillis();
        try {
            send(instance,inject,down,MotionEvent.ACTION_DOWN,x,y);
            for(int step=1;step<=12;step++){
                Thread.sleep(16);send(instance,inject,down,MotionEvent.ACTION_MOVE,x+(endX-x)*step/12,y+(endY-y)*step/12);
            }
            System.out.println("HOLDING");System.out.flush();Thread.sleep(hold);
        } finally { send(instance,inject,down,end,endX,endY); }
        if(args.length==8){
            Thread.sleep(16);long next=SystemClock.uptimeMillis();
            float tapX=Float.parseFloat(args[6]),tapY=Float.parseFloat(args[7]);
            send(instance,inject,next,MotionEvent.ACTION_DOWN,tapX,tapY);
            Thread.sleep(16);send(instance,inject,next,MotionEvent.ACTION_UP,tapX,tapY);
        }
        System.out.println("RELEASED");
    }
    private static void send(Object manager,java.lang.reflect.Method method,long down,int action,float x,float y)throws Exception{
        MotionEvent event=MotionEvent.obtain(down,SystemClock.uptimeMillis(),action,x,y,0);
        event.setSource(InputDevice.SOURCE_TOUCHSCREEN);
        try { if(!Boolean.TRUE.equals(method.invoke(manager,event,2)))throw new IllegalStateException("Pointer injection rejected"); }
        finally { event.recycle(); }
    }
}
