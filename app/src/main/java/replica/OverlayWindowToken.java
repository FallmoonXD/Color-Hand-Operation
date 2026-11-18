package replica;

import android.content.Context;
import android.content.ContextWrapper;
import android.os.IBinder;
import android.view.Window;
import android.view.WindowManager;

/** Optional app-owned window capability; accessibility contexts retain their native token. */
public interface OverlayWindowToken {
    IBinder overlayWindowToken();
    default void overlayAttached(){}
    static OverlayWindowToken find(Context context){
        for(int depth=0;context!=null&&depth<32;depth++){
            if(context instanceof OverlayWindowToken owner)return owner;
            if(!(context instanceof ContextWrapper wrapper))break;
            Context next=wrapper.getBaseContext();if(next==context)break;context=next;
        }
        return null;
    }
    static void apply(Context context,WindowManager.LayoutParams attributes){
        if(attributes.type!=WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY)return;
        OverlayWindowToken owner=find(context);
        if(owner==null)return;
        attributes.token=owner.overlayWindowToken();
        if(attributes.token==null)throw new IllegalStateException("Tool window session has ended");
    }
    static void apply(Context context,Window window){
        if(window==null||window.getAttributes().type!=WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY)return;
        OverlayWindowToken owner=find(context);
        if(owner==null)return;
        IBinder token=owner.overlayWindowToken();
        if(token==null)throw new IllegalStateException("Tool window session has ended");
        WindowManager.LayoutParams attributes=window.getAttributes();attributes.token=token;window.setAttributes(attributes);
        window.getDecorView().addOnAttachStateChangeListener(new android.view.View.OnAttachStateChangeListener(){
            public void onViewAttachedToWindow(android.view.View view){owner.overlayAttached();view.removeOnAttachStateChangeListener(this);}
            public void onViewDetachedFromWindow(android.view.View view){}
        });
    }
}
