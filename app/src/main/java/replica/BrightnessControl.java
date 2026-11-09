package replica;

import android.content.Context;
import android.database.ContentObserver;
import android.net.Uri;
import android.os.Handler;
import android.os.Looper;
import android.provider.Settings;
import android.widget.SeekBar;
import android.widget.Toast;
import android.view.MotionEvent;
import java.util.ArrayDeque;
import dev.edgeflow.PendingRootCommand;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.RejectedExecutionException;

/** Uses only discovered raw ranges; Oplus adjustment semantics require an explicit feature. */
public final class BrightnessControl {
    private static final String RANGE_KEY="screen_brightness_range";
    private static final String MULTIBITS="oplus.software.display.multibits_dimming_support";
    private final Context context;
    private final SeekBar slider;
    private final Handler handler=new Handler(Looper.getMainLooper());
    private final ExecutorService worker=Executors.newSingleThreadExecutor();
    private boolean dragging,dragInvalidated,started,warnedUnavailable;
    private volatile boolean writing,closed;
    private volatile State state;
    private final ArrayDeque<Request> pending=new ArrayDeque<>();
    private PendingRootCommand inFlight;
    private QuickControlsSession session;
    private boolean cancelled,warmAttempted;
    private long gesture,failedGesture=-1;
    private enum Phase {DRAG,SAVE,CANCEL}
    private static final class State {
        final BrightnessRangePolicy.Range range;final boolean multibits;
        State(BrightnessRangePolicy.Range range,boolean multibits){this.range=range;this.multibits=multibits;}
    }
    private static final class Request {
        final State state;final int progress;final Phase phase;final long gesture;
        Request(State state,int progress,Phase phase,long gesture){this.state=state;this.progress=progress;this.phase=phase;this.gesture=gesture;}
    }
    private final ContentObserver observer=new ContentObserver(handler){
        @Override public void onChange(boolean self){if(!closed){readRange();sync();}}
        @Override public void onChange(boolean self,Uri uri){
            if(closed)return;
            if(uri==null||Settings.System.getUriFor(RANGE_KEY).equals(uri))readRange();
            sync();
        }
    };
    public BrightnessControl(Context context,SeekBar slider){
        this.context=context;this.slider=slider;readRange();sync();
        slider.setOnTouchListener((v,e)->{
            if(e.getActionMasked()==MotionEvent.ACTION_DOWN)cancelled=false;
            if(e.getActionMasked()==MotionEvent.ACTION_CANCEL)cancelled=true;
            return false;
        });
        slider.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener(){
            public void onStartTrackingTouch(SeekBar view){gesture++;dragging=true;dragInvalidated=state==null;}
            public void onProgressChanged(SeekBar view,int progress,boolean user){
                if(user&&!dragInvalidated&&!cancelled){if(!dragging)gesture++;write(progress,dragging?Phase.DRAG:Phase.SAVE);}
            }
            public void onStopTrackingTouch(SeekBar view){
                if(!dragInvalidated)write(view.getProgress(),cancelled?Phase.CANCEL:Phase.SAVE);
                dragging=false;dragInvalidated=false;sync();
            }
        });
    }
    private void readRange(){
        BrightnessRangePolicy.Range range=BrightnessRangePolicy.probe(
                ()->BrightnessRangePolicy.parse(Settings.System.getString(context.getContentResolver(),RANGE_KEY)),
                ()->{
                    Class<?> type=Class.forName("android.os.OplusPowerManager");Object power=type.getConstructor().newInstance();
                    int min=(Integer)type.getMethod("getMinimumScreenBrightnessSetting").invoke(power);
                    int max=(Integer)type.getMethod("getMaximumScreenBrightnessSetting").invoke(power);
                    return BrightnessRangePolicy.values(min,max);
                },
                ()->{
                    Class<?> type=Class.forName("android.os.SystemProperties");
                    java.lang.reflect.Method get=type.getMethod("get",String.class,String.class);
                    String min=(String)get.invoke(null,"sys.oplus.multibrightness.min","");
                    String max=(String)get.invoke(null,"sys.oplus.multibrightness","");
                    return BrightnessRangePolicy.parse(min+","+max);
                });
        boolean multibits=false;
        try{
            Class<?> type=Class.forName("com.oplus.content.OplusFeatureConfigManager");
            Object manager=type.getMethod("getInstance").invoke(null);
            multibits=Boolean.TRUE.equals(type.getMethod("hasFeature",String.class).invoke(manager,MULTIBITS));
        }catch(Exception|LinkageError unavailable){/* Never infer raw adjustment from the model or range. */}
        updateRange(range, multibits);
    }
    private synchronized void updateRange(BrightnessRangePolicy.Range range, boolean multibits) {
        if (closed || (range == null && state == null)) return;
        if (state != null && range != null && range.same(state.range) && multibits == state.multibits) return;
        state = range == null ? null : new State(range, multibits);
        pending.clear();
        if(session!=null){session.close();session=null;}warmAttempted=false;
        if (inFlight != null) inFlight.cancel();
        if (dragging) dragInvalidated = true;
    }
    public void sync(){
        if(closed)return;
        State current=state;
        if(current==null){unavailable("无法确认亮度范围，暂不可调整");return;}
        if(dragging||writing)return;
        try{
            int raw=Integer.parseInt(Settings.System.getString(context.getContentResolver(),"screen_brightness"));
            slider.setMax(current.range.max-current.range.min);
            slider.setProgress(current.range.progress(raw));slider.setEnabled(true);
            warnedUnavailable=false;describe();
        }catch(Exception unavailable){unavailable("无法读取当前亮度");}
    }
    private void unavailable(String message){
        slider.setEnabled(false);slider.setStateDescription(message);slider.setContentDescription("屏幕亮度，"+message);
        if(!warnedUnavailable){warnedUnavailable=true;Toast.makeText(context,message,Toast.LENGTH_SHORT).show();}
    }
    private void describe(){
        State current=state;if(current==null)return;
        String value=Math.round(slider.getProgress()*100f/(current.range.max-current.range.min))+"%";
        slider.setStateDescription(value);slider.setContentDescription("屏幕亮度 "+value);
    }
    private void write(int progress,Phase phase){
        if(closed||dragInvalidated)return;
        State current=state;
        if(current==null){unavailable("无法确认亮度范围，暂不可调整");return;}
        describe();
        synchronized(this){
            if(closed||state!=current||failedGesture==gesture)return;
            // New points replace unsent drag samples, never a previous gesture's release.
            if(!pending.isEmpty()&&pending.peekLast().phase==Phase.DRAG)pending.removeLast();
            pending.addLast(new Request(current,progress,phase,gesture));if(writing)return;writing=true;
        }
        try{worker.submit(this::drainWrites);}
        catch(RejectedExecutionException stopped){synchronized(this){pending.clear();writing=false;}}
    }
    private synchronized boolean valid(Request request){
        return !closed&&state==request.state&&(request.phase!=Phase.DRAG||pending.isEmpty());
    }
    private void drainWrites(){
        while (true) {
            Request request;
            QuickControlsSession display=null;
            PendingRootCommand command=null;
            synchronized (this) {
                if (closed || pending.isEmpty()) { pending.clear(); writing = false; break; }
                request = pending.removeFirst();
                if (request.state != state) continue;
                if(state.multibits){
                    if(session==null)session=new QuickControlsSession(context,state.range);
                    display=session;
                }else{
                    if(request.phase==Phase.CANCEL)continue;
                    command=new PendingRootCommand(BrightnessRangePolicy.command(state.range,request.progress));
                    inFlight=command;
                }
            }
            try {
                if(display!=null){
                    String operation=request.phase==Phase.CANCEL?"cancel":
                            (request.phase==Phase.SAVE?"save ":"drag ")+request.state.range.raw(request.progress);
                    display.apply(operation,()->valid(request));
                }else command.run(()->valid(request));
            } catch (InterruptedException interrupted) {
                Thread.currentThread().interrupt();
            } catch (Exception failure) {
                synchronized(this){
                    if(display!=null&&display==session){session.close();session=null;}
                    failedGesture=request.gesture;pending.removeIf(next->next.gesture==request.gesture);
                }
                handler.post(() -> {
                    if (!closed && state == request.state) Toast.makeText(context,"亮度调整未完成",Toast.LENGTH_SHORT).show();
                });
            } finally {
                synchronized (this) { inFlight = null; }
            }
        }
        if(!closed)handler.post(this::sync);
    }
    public void start(){
        if(closed||started)return;started=true;
        context.getContentResolver().registerContentObserver(Settings.System.getUriFor("screen_brightness"),false,observer);
        context.getContentResolver().registerContentObserver(Settings.System.getUriFor(RANGE_KEY),false,observer);sync();
        prewarm();
    }
    synchronized void prewarm(){
        // The glass helper may finish its already-authorized root startup after this panel opens.
        if(!closed&&!warmAttempted&&session==null&&(NativeBlur.online||QuickControlsSession.canPrewarm())){
            warmAttempted=true;
            QuickControlsSession warm=new QuickControlsSession(context,state!=null&&state.multibits?state.range:null);session=warm;
            worker.submit(()->{try{warm.connect();}catch(Exception error){
                synchronized(this){warm.close();if(session==warm)session=null;}
            }});
        }
    }
    int mediaKey(){
        QuickControlsSession target;
        synchronized(this){target=session;}
        if(target!=null&&target.isReady())try{return target.mediaKey();}
        catch(Exception unavailable){
            synchronized(this){if(session==target){target.close();session=null;}}
        }
        return android.view.KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE;
    }
    public void stop(){if(!started)return;started=false;context.getContentResolver().unregisterContentObserver(observer);}
    public void close(){
        if(closed)return;
        synchronized(this){closed=true;pending.clear();if(session!=null){session.close();session=null;}if(inFlight!=null)inFlight.cancel();}
        stop();handler.removeCallbacksAndMessages(null);slider.setOnTouchListener(null);slider.setOnSeekBarChangeListener(null);worker.shutdown();
    }
}
