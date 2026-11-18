package dev.edgeflow.lsp;

import android.os.SystemClock;
import android.view.MotionEvent;
import java.util.*;

/** SystemUI observes input; the framework owns the corresponding finite injection sequence. */
final class TouchObservation {
    private static final java.util.concurrent.ConcurrentSkipListMap<Long,Long> generated=new java.util.concurrent.ConcurrentSkipListMap<>();
    private static final Set<Runnable> cancellations=new java.util.concurrent.CopyOnWriteArraySet<>();
    private static volatile boolean down;
    static void listen(Runnable cancel){cancellations.add(cancel);}
    static void unlisten(Runnable cancel){cancellations.remove(cancel);}
    static boolean touching(){return down;}
    static void remember(long base,long duration,int count){
        long now=SystemClock.uptimeMillis();generated.entrySet().removeIf(e->e.getValue()<now);
        for(int n=0;n<count;n++)generated.put(base+n*(duration+65),now+10000);
        while(generated.size()>128)generated.pollFirstEntry();
    }
    static boolean observe(MotionEvent event){
        long now=SystemClock.uptimeMillis();generated.entrySet().removeIf(e->e.getValue()<now);
        if(event.getPointerCount()==1&&event.getToolType(0)==MotionEvent.TOOL_TYPE_UNKNOWN&&generated.containsKey(event.getDownTime()))return true;
        int action=event.getActionMasked();
        if(action==MotionEvent.ACTION_DOWN||action==MotionEvent.ACTION_POINTER_DOWN){down=true;for(Runnable cancel:cancellations)cancel.run();}
        if(action==MotionEvent.ACTION_UP||action==MotionEvent.ACTION_CANCEL)down=false;
        return false;
    }
    private TouchObservation(){}
}
