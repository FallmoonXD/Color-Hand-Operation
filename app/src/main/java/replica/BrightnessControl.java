package replica;

import android.content.Context;
import android.database.ContentObserver;
import android.net.Uri;
import android.os.Handler;
import android.os.Looper;
import android.provider.Settings;
import android.widget.SeekBar;
import android.widget.Toast;
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
    private Request pending;
    private static final class State {
        final BrightnessRangePolicy.Range range;final boolean multibits;
        State(BrightnessRangePolicy.Range range,boolean multibits){this.range=range;this.multibits=multibits;}
    }
    private static final class Request {
        final State state;final int progress;
        Request(State state,int progress){this.state=state;this.progress=progress;}
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
        slider.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener(){
            public void onStartTrackingTouch(SeekBar view){dragging=true;dragInvalidated=state==null;}
            public void onProgressChanged(SeekBar view,int progress,boolean user){if(user&&!dragInvalidated)write(progress);}
            public void onStopTrackingTouch(SeekBar view){
                if(!dragInvalidated)write(view.getProgress());
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
        synchronized(this){
            State old=state;
            if(range!=null&&old!=null&&range.same(old.range)&&multibits==old.multibits)return;
            state=range==null?null:new State(range,multibits);
            pending=null;
            if(dragging)dragInvalidated=true;
        }
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
    private void write(int progress){
        if(closed||dragInvalidated)return;
        State current=state;
        if(current==null){unavailable("无法确认亮度范围，暂不可调整");return;}
        describe();
        synchronized(this){
            if(closed||state!=current)return;
            pending=new Request(current,progress);if(writing)return;writing=true;
        }
        try{worker.submit(this::drainWrites);}
        catch(RejectedExecutionException stopped){synchronized(this){pending=null;writing=false;}}
    }
    private void drainWrites(){
        try{
            while(true){
                Request request;
                synchronized(this){
                    if(closed||pending==null){pending=null;writing=false;break;}
                    request=pending;pending=null;
                }
                if(closed||request.state!=state)continue;
                String command=BrightnessRangePolicy.command(request.state.range,request.state.multibits,request.progress);
                Process process=new ProcessBuilder("su","-c",command).redirectErrorStream(true).start();
                try(java.io.InputStream input=process.getInputStream()){byte[] buffer=new byte[256];while(input.read(buffer)!=-1){}}
                if(process.waitFor()!=0)throw new IllegalStateException("brightness write failed");
            }
        }catch(Exception failure){
            synchronized(this){pending=null;writing=false;}
            if(!closed)handler.post(()->{if(!closed)Toast.makeText(context,"亮度调整未完成",Toast.LENGTH_SHORT).show();});
        }
        if(!closed)handler.post(this::sync);
    }
    public void start(){
        if(closed||started)return;started=true;
        context.getContentResolver().registerContentObserver(Settings.System.getUriFor("screen_brightness"),false,observer);
        context.getContentResolver().registerContentObserver(Settings.System.getUriFor(RANGE_KEY),false,observer);sync();
    }
    public void stop(){if(!started)return;started=false;context.getContentResolver().unregisterContentObserver(observer);}
    public void close(){
        if(closed)return;
        synchronized(this){closed=true;pending=null;}
        stop();handler.removeCallbacksAndMessages(null);slider.setOnSeekBarChangeListener(null);worker.shutdown();
    }
}
