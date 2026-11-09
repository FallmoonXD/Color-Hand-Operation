package replica;
import android.os.Handler;
import android.view.*;
/** Immediate step, system long-press delay, then 100ms repeat; cancellation never queues input. */
public class RepeatPress implements View.OnTouchListener {
 private final Handler handler=new Handler();private final View view;private final Runnable step;private boolean held;
 private final Runnable repeat=new Runnable(){public void run(){if(held){step.run();handler.postDelayed(this,100);}}};
 public RepeatPress(View view,Runnable step){this.view=view;this.step=step;view.setOnClickListener(v->step.run());view.setOnTouchListener(this);view.addOnAttachStateChangeListener(new View.OnAttachStateChangeListener(){public void onViewAttachedToWindow(View v){}public void onViewDetachedFromWindow(View v){stop();}});}
 public void stop(){held=false;handler.removeCallbacks(repeat);view.setPressed(false);}
 public boolean onTouch(View v,MotionEvent e){switch(e.getActionMasked()){
 case MotionEvent.ACTION_DOWN:held=true;v.drawableHotspotChanged(e.getX(),e.getY());v.setPressed(true);step.run();handler.postDelayed(repeat,ViewConfiguration.getLongPressTimeout());return true;
 case MotionEvent.ACTION_MOVE:if(e.getX()<0||e.getY()<0||e.getX()>v.getWidth()||e.getY()>v.getHeight())stop();else if(held)v.drawableHotspotChanged(e.getX(),e.getY());return true;
 case MotionEvent.ACTION_POINTER_DOWN:case MotionEvent.ACTION_UP:case MotionEvent.ACTION_CANCEL:stop();return true;}return true;}
}
