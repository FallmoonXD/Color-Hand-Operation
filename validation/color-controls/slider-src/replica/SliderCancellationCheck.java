package replica;
import android.view.MotionEvent;
import android.widget.SeekBar;
public final class SliderCancellationCheck {
    private static int checks;
    private static void check(boolean condition,String label){checks++;if(!condition)throw new AssertionError(label);}
    private static void event(ColorSlider slider,int action){slider.onTouchEvent(MotionEvent.obtain(0,0,action,0,0,0));}
    private static final class Listener implements SeekBar.OnSeekBarChangeListener {
        int starts,stops,commits,cancelled;boolean detachAtStop;
        public void onStartTrackingTouch(SeekBar bar){starts++;}
        public void onProgressChanged(SeekBar bar,int value,boolean user){}
        public void onStopTrackingTouch(SeekBar bar){ColorSlider slider=(ColorSlider)bar;stops++;if(slider.isTouchCancelled())cancelled++;else commits++;if(detachAtStop)slider.onDetachedFromWindow();}
    }
    private static ColorSlider slider(Listener listener){ColorSlider slider=new ColorSlider();slider.setOnSeekBarChangeListener(listener);return slider;}
    public static void main(String[] args){
        Listener normal=new Listener();normal.detachAtStop=true;ColorSlider a=slider(normal);
        event(a,0);event(a,2);event(a,1);
        check(normal.commits==1&&normal.stops==1,"UP commits once when callback synchronously rebuilds page");
        check(!a.isTouchCancelled()&&a.cancels==0,"post-UP detach does not turn success into cancellation");
        a.onDetachedFromWindow();check(normal.stops==1,"repeated detach does not duplicate stop");

        Listener framework=new Listener();ColorSlider b=slider(framework);event(b,0);event(b,2);event(b,3);
        check(framework.cancelled==1&&framework.commits==0,"real CANCEL is visible inside stop callback");
        event(b,0);check(!b.isTouchCancelled(),"new DOWN clears previous cancellation");event(b,1);
        check(framework.commits==1&&framework.stops==2,"new valid gesture can commit after cancellation");

        Listener detached=new Listener();detached.detachAtStop=true;ColorSlider c=slider(detached);event(c,0);event(c,2);c.onDetachedFromWindow();
        check(detached.cancelled==1&&detached.commits==0,"theme/page detach cancels draft before stop callback");
        check(c.cancels==1&&detached.stops==1,"reentrant detach during cancellation emits one stop");

        Listener disabled=new Listener();ColorSlider d=slider(disabled);event(d,0);d.setEnabled(false);
        check(disabled.cancelled==1&&disabled.commits==0&&!d.isEnabled(),"disable cancels draft before callback");
        d.setEnabled(true);event(d,0);event(d,1);
        check(disabled.commits==1,"reenabled control accepts next complete gesture");

        Listener pending=new Listener();ColorSlider e=slider(pending);e.deferStart=true;event(e,0);e.onDetachedFromWindow();
        check(e.cancels==1&&e.isTouchCancelled(),"pending ScrollView DOWN is cleared on detach");
        check(pending.starts==0&&pending.stops==0,"pending tap cancellation invents no tracking callback");
        event(e,0);event(e,1);check(pending.starts==1&&pending.commits==1,"a later tap completes delayed start and stop");

        Listener delayed=new Listener();delayed.detachAtStop=true;ColorSlider f=slider(delayed);f.deferStart=true;event(f,0);event(f,1);
        check(delayed.commits==1&&delayed.stops==1&&f.cancels==0,"tap UP followed by rebuild does not re-cancel");

        Listener omitted=new Listener();ColorSlider g=slider(omitted);g.dropCancelCallback=true;event(g,0);g.onDetachedFromWindow();
        check(omitted.cancelled==1&&omitted.commits==0,"fallback stop preserves cancellation if framework omits stop");
        g.onDetachedFromWindow();check(omitted.stops==1,"fallback stop also clears tracking before callback");
        System.out.println("PASS "+checks+" slider cancellation assertions");
    }
}
