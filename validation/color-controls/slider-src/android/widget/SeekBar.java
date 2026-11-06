package android.widget;
import android.view.MotionEvent;
/** Narrow framework callback model, including ScrollView-delayed tracking. */
public class SeekBar {
    public interface OnSeekBarChangeListener {
        void onProgressChanged(SeekBar bar,int value,boolean fromUser);
        void onStartTrackingTouch(SeekBar bar);
        void onStopTrackingTouch(SeekBar bar);
    }
    private OnSeekBarChangeListener listener;
    private boolean enabled=true,dragging,pending;
    public boolean deferStart,dropCancelCallback;
    public int cancels;
    public void setOnSeekBarChangeListener(OnSeekBarChangeListener listener){this.listener=listener;}
    public boolean isEnabled(){return enabled;}
    public void setEnabled(boolean value){enabled=value;}
    public void invalidate(){}
    public int getProgress(){return 25;}
    private void start(){pending=false;dragging=true;if(listener!=null)listener.onStartTrackingTouch(this);}
    private void stop(){dragging=false;if(listener!=null)listener.onStopTrackingTouch(this);}
    public boolean onTouchEvent(MotionEvent event){
        switch(event.getActionMasked()){
            case MotionEvent.ACTION_DOWN:pending=true;if(!deferStart)start();break;
            case MotionEvent.ACTION_MOVE:if(pending)start();if(dragging&&listener!=null)listener.onProgressChanged(this,25,true);break;
            case MotionEvent.ACTION_UP:if(pending)start();if(dragging)stop();break;
            case MotionEvent.ACTION_CANCEL:cancels++;pending=false;if(dragging){if(dropCancelCallback)dragging=false;else stop();}break;
        }
        return enabled;
    }
    protected void onDetachedFromWindow(){}
}
