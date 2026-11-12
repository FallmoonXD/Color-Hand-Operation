package replica;

import android.content.Context;
import android.graphics.Insets;
import android.graphics.Rect;
import android.view.View;
import android.view.WindowInsets;
import android.view.WindowManager;
import android.view.WindowMetrics;

/** Screen-coordinate task space, independent of a small floating/overlay window's root bounds. */
final class ColorWindowSpace {
    private ColorWindowSpace(){}

    static Rect available(Context context,View reference){
        return available(context,reference,null);
    }
    static Rect available(Context context,View reference,WindowInsets animated){
        return available(context,reference,animated,null);
    }
    static Rect available(Context context,View reference,WindowInsets animated,Rect insetFrame){
        Rect task=new Rect(0,0,context.getResources().getDisplayMetrics().widthPixels,
                context.getResources().getDisplayMetrics().heightPixels);
        Insets insets=Insets.NONE,bars=Insets.NONE;
        try{
            WindowMetrics metrics=context.getSystemService(WindowManager.class).getCurrentWindowMetrics();
            if(!metrics.getBounds().isEmpty())task.set(metrics.getBounds());
            bars=metrics.getWindowInsets().getInsets(WindowInsets.Type.systemBars()|WindowInsets.Type.displayCutout());
            // Metrics insets are relative to the entire task, unlike a floating decor's
            // already-consumed/partial IME insets. Include the currently visible keyboard.
            insets=metrics.getWindowInsets().getInsets(WindowInsets.Type.systemBars()
                    |WindowInsets.Type.displayCutout()|WindowInsets.Type.ime());
        }catch(RuntimeException ignored){}
        if(animated!=null){
            Insets keyboard=animated.getInsets(WindowInsets.Type.ime());
            if(insetFrame!=null&&!insetFrame.isEmpty())keyboard=Insets.of(
                    keyboard.left==0?0:keyboard.left+Math.max(0,insetFrame.left-task.left),
                    keyboard.top==0?0:keyboard.top+Math.max(0,insetFrame.top-task.top),
                    keyboard.right==0?0:keyboard.right+Math.max(0,task.right-insetFrame.right),
                    keyboard.bottom==0?0:keyboard.bottom+Math.max(0,task.bottom-insetFrame.bottom));
            insets=Insets.max(bars,keyboard);
        }
        Rect safe=new Rect(task);
        safe.inset(insets.left,insets.top,insets.right,insets.bottom);
        if(safe.isEmpty())safe.set(task);
        if(animated==null&&reference!=null&&reference.isAttachedToWindow()){
            Rect visible=new Rect();reference.getWindowVisibleDisplayFrame(visible);
            // This is the display frame, not getGlobalVisibleRect()/the floating root.
            // It also catches OEM floating-window IME policies that omit metrics IME.
            Rect clipped=new Rect(safe);
            if(!visible.isEmpty()&&clipped.intersect(visible)&&!clipped.isEmpty())safe.set(clipped);
        }
        return safe;
    }
}
