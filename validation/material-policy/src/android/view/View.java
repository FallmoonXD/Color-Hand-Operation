package android.view;
import android.content.Context;
import android.graphics.drawable.Drawable;
import java.util.ArrayList;
public class View implements Drawable.Callback {
    public interface OnAttachStateChangeListener {void onViewAttachedToWindow(View v);void onViewDetachedFromWindow(View v);}
    private final Context context=new Context();
    public final ArrayList<OnAttachStateChangeListener> listeners=new ArrayList<>();
    private boolean attached;
    public void addOnAttachStateChangeListener(OnAttachStateChangeListener l){listeners.add(l);}
    public void removeOnAttachStateChangeListener(OnAttachStateChangeListener l){listeners.remove(l);}
    public boolean isAttachedToWindow(){return attached;}
    public Context getContext(){return context;}
    public void attach(){attached=true;for(OnAttachStateChangeListener l:new ArrayList<>(listeners))l.onViewAttachedToWindow(this);}
    public void detach(){attached=false;for(OnAttachStateChangeListener l:new ArrayList<>(listeners))l.onViewDetachedFromWindow(this);}
}
