package android.view;
import java.util.ArrayList;
public class View {
    public boolean attached=true,shown=true,enabled=true,pressed,clickable=true;
    public final ArrayList<Runnable> frames=new ArrayList<>();
    public static final class Parent {public void requestDisallowInterceptTouchEvent(boolean disallow){}}
    public boolean isAttachedToWindow(){return attached;}public boolean isShown(){return shown;}public boolean isEnabled(){return enabled;}public boolean isClickable(){return clickable;}public boolean isPressed(){return pressed;}
    public void setEnabled(boolean value){enabled=value;drawableStateChanged();}public void setPressed(boolean value){pressed=value;drawableStateChanged();}
    public void invalidate(){}public void postOnAnimation(Runnable callback){if(!frames.contains(callback))frames.add(callback);}public void removeCallbacks(Runnable callback){frames.remove(callback);}
    public int getWidth(){return 48;}public int getHeight(){return 44;}public Parent getParent(){return new Parent();}
    public void frame(long time){android.os.SystemClock.now=time;ArrayList<Runnable> current=new ArrayList<>(frames);frames.clear();for(Runnable callback:current)callback.run();}
    protected void onDetachedFromWindow(){attached=false;}protected void drawableStateChanged(){}public void jumpDrawablesToCurrentState(){}
    public boolean onTouchEvent(MotionEvent event){return false;}public boolean performClick(){return true;}
}
