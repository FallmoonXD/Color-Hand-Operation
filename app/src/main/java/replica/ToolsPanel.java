package replica;

import android.app.*;
import android.content.*;
import android.content.res.ColorStateList;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.hardware.camera2.*;
import android.media.AudioManager;
import android.net.wifi.WifiManager;
import android.os.*;
import android.provider.Settings;
import android.view.*;
import android.widget.*;
import dev.edgeflow.AppPrefs;
import java.util.concurrent.*;

/** Compact panel with the exact saved OHO tool order, using native Android actions. */
public class ToolsPanel extends android.view.ContextThemeWrapper {
    private final Window window;private final Runnable close;private volatile boolean closed;
    private final java.util.ArrayList<AlertDialog> childDialogs=new java.util.ArrayList<>();
    public boolean hasChildDialogs(){return !childDialogs.isEmpty();}
    boolean dismissChild(){if(childDialogs.isEmpty())return false;childDialogs.get(childDialogs.size()-1).dismiss();return true;}
    private void showChild(AlertDialog dialog){
        if(closed||hasChildDialogs())return;Window child=dialog.getWindow();if(overlay()){child.setType(WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY);child.addFlags(WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE);}
        childDialogs.add(dialog);dialog.setOnDismissListener(d->childDialogs.remove(dialog));try{dialog.show();}catch(RuntimeException error){childDialogs.remove(dialog);ColorDialogBuilder.dismissImmediately(dialog);android.util.Log.w("ReplicaTools","Unable to open settings",error);}
    }
    public float[] anchor(){return gesture;}
    public boolean overlay(){return !(getBaseContext() instanceof Activity);}
    private Window getWindow(){return window;}
    private void setContentView(View v){window.setContentView(v);}
    private void closeThen(Runnable action){
        if(overlay())ToolsOverlay.closeThen(action);
        else if(getBaseContext() instanceof ToolsActivity owner)owner.closeThen(action);
        else{close.run();action.run();}
    }
    private void commandAfterClose(String command){
        closeThen(()->dev.edgeflow.ActionExecutor.root(this,command,"快速工具"));
    }
    private void launchAfterClose(Intent intent){
        closeThen(()->{
            try{startActivity(intent);}
            catch(ActivityNotFoundException|SecurityException unavailable){Toast.makeText(this,"无法打开应用",Toast.LENGTH_SHORT).show();}
        });
    }
    private void runOnUiThread(Runnable r){liveHandler.post(()->{if(!closed)r.run();});}
    public void position(){PanelGeometry.position(this,window,dp(260),dp(360),true,gesture);}
    private final ExecutorService worker=Executors.newSingleThreadExecutor();
    private AudioManager audio; private BrightnessControl brightnessControl; private float[] gesture;
    private SeekBar liveVolume; private ImageView autoIcon,speaker,playPause; private final Handler liveHandler=new Handler();
    private final android.database.ContentObserver brightnessMode=new android.database.ContentObserver(liveHandler){
        @Override public void onChange(boolean self){refresh();}
    };
    private VolumeDrag pendingDrag;
    private Boolean shownMuted,shownPlaying;
    private boolean watchingPlayback;
    private final Runnable refreshPlayback=()->{if(!closed)showPlaying(audio.isMusicActive());};
    private final AudioManager.AudioPlaybackCallback playback=new AudioManager.AudioPlaybackCallback(){
        @Override public void onPlaybackConfigChanged(java.util.List<android.media.AudioPlaybackConfiguration> active){
            playbackChanged();
        }
    };
    private void playbackChanged(){
        if(closed)return;
        // An unprivileged callback can omit or anonymize tracks, including muted media.
        // Read the stream itself, and allow the mixer one more update after a transport command.
        refreshPlayback.run();liveHandler.removeCallbacks(refreshPlayback);liveHandler.postDelayed(refreshPlayback,48);
    }
    private final Runnable liveRefresh=new Runnable(){public void run(){if(closed)return;refresh();liveHandler.postDelayed(this,500);}};
    private String closeTarget;
    private boolean torch;
    private CameraManager cameras;
    private String cameraId;
    private final java.util.LinkedHashMap<String,ImageView> toolViews=new java.util.LinkedHashMap<>();
    private final java.util.ArrayList<ToolsConfig.Item> tiles;
    private int dp(float n){return PanelGeometry.px(this,n);}
    private GradientDrawable bg(int color,int radius){GradientDrawable d=new GradientDrawable();d.setColor(color);d.setCornerRadius(dp(radius));return d;}
    private void command(String command) {
        // Share the independent action layer's timeout and result checks. Live state
        // polling refreshes the controls after the command completes.
        if(!closed)dev.edgeflow.ActionExecutor.root(this,command,"快速工具");
    }
    public ToolsPanel(Context base,Window window,Runnable close){
        this(base,window,close,null,null);
    }
    public ToolsPanel(Context base,Window window,Runnable close,android.graphics.Bitmap retained,float[] anchor){
        super(base,base.getResources().getIdentifier(PanelStyle.dark(base)?"ReplicaGlassDark":"ReplicaGlass","style",base.getPackageName()));this.window=window;this.close=close;tiles=ToolsConfig.load(this);
        getWindow().setBackgroundDrawableResource(android.R.color.transparent);
        getWindow().clearFlags(WindowManager.LayoutParams.FLAG_DIM_BEHIND);
        gesture=anchor==null?NativeState.gesture(this):anchor;audio=(AudioManager)getSystemService(AUDIO_SERVICE);closeTarget=dev.edgeflow.ForegroundAppCloser.target(dev.edgeflow.GestureService.instance());
        cameras=(CameraManager)getSystemService(CAMERA_SERVICE);
        try{for(String id:cameras.getCameraIdList())if(Boolean.TRUE.equals(cameras.getCameraCharacteristics(id).get(CameraCharacteristics.FLASH_INFO_AVAILABLE))){cameraId=id;break;}}catch(Exception ignored){}
        if(cameraId!=null)cameras.registerTorchCallback(torchCallback,new Handler());
        LinearLayout panel=new LinearLayout(this){private boolean blocked;@Override public boolean dispatchTouchEvent(MotionEvent event){int action=event.getActionMasked();if(action==MotionEvent.ACTION_DOWN)blocked=hasChildDialogs();boolean consume=blocked;if(action==MotionEvent.ACTION_UP||action==MotionEvent.ACTION_CANCEL)blocked=false;return consume||super.dispatchTouchEvent(event);}};panel.setOrientation(LinearLayout.VERTICAL);panel.setPadding(dp(14),dp(14),dp(14),dp(14));setContentView(panel);
        ToolsBackdrop backdrop=PanelStyle.controlsWindow(this,window,24,false);
        position();
        LinearLayout brightness=row();brightness.setPadding(dp(7),0,dp(7),0);panel.addView(brightness);
        ImageView sun=icon("brightness_icon",PanelStyle.accent(this));autoIcon=sun;sun.setContentDescription("自动亮度");sun.setOnClickListener(v->command("settings put system screen_brightness_mode "+(Settings.System.getInt(getContentResolver(),"screen_brightness_mode",0)==1?0:1)));brightness.addView(sun,new LinearLayout.LayoutParams(dp(35),dp(34)));
        SeekBar light=new GlassSeekBar(this);LinearLayout.LayoutParams lightParams=new LinearLayout.LayoutParams(0,dp(38),1);if(gesture[0]<getResources().getDisplayMetrics().widthPixels/2f)lightParams.rightMargin=dp(8);else lightParams.leftMargin=dp(8);brightness.addView(light,lightParams);brightnessControl=new BrightnessControl(this,light);
        if(gesture[0]<getResources().getDisplayMetrics().widthPixels/2f){brightness.removeView(sun);brightness.addView(sun);}
        LinearLayout media=new LinearLayout(this);media.setOrientation(LinearLayout.VERTICAL);media.setPadding(dp(12),dp(6),dp(12),dp(10));media.setBackground(PanelStyle.mediaSurface(this));
        LinearLayout.LayoutParams mp=new LinearLayout.LayoutParams(-1,dp(96));mp.topMargin=dp(14);panel.addView(media,mp);
        LinearLayout vr=row();media.addView(vr);speaker=icon("volume_media",Color.WHITE);vr.addView(speaker,new LinearLayout.LayoutParams(dp(35),dp(34)));
        speaker.setOnClickListener(v->adjustMediaVolume(AudioManager.ADJUST_TOGGLE_MUTE));
        speaker.setBackground(new MediaPressRipple(this,14));
        vr.setOnClickListener(v->speaker.performClick());vr.setBackground(new MediaPressRipple(this,14));vr.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);
        SeekBar volume=new GlassSeekBar(this,true);liveVolume=volume;volume.setContentDescription("媒体音量");tintSlider(volume);volume.setMax(audio.getStreamMaxVolume(AudioManager.STREAM_MUSIC));volume.setProgress(audio.getStreamVolume(AudioManager.STREAM_MUSIC));LinearLayout.LayoutParams volumeParams=new LinearLayout.LayoutParams(0,dp(34),1);if(gesture[0]<getResources().getDisplayMetrics().widthPixels/2f)volumeParams.rightMargin=dp(8);else volumeParams.leftMargin=dp(8);vr.addView(volume,volumeParams);
        volume.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener(){public void onStartTrackingTouch(SeekBar b){}public void onStopTrackingTouch(SeekBar b){}public void onProgressChanged(SeekBar b,int v,boolean user){if(user)setMediaVolume(v);}});
        if(gesture[0]<getResources().getDisplayMetrics().widthPixels/2f){vr.removeView(speaker);vr.addView(speaker);}
        LinearLayout controls=row();LinearLayout.LayoutParams controlsParams=new LinearLayout.LayoutParams(-1,dp(36));controlsParams.topMargin=dp(10);media.addView(controls,controlsParams);
        String[] symbols={"player_prev","player_play","player_next","ic_volume_up","ic_volume_down"};
        String[] labels={"上一首","播放／暂停","下一首","音量增加","音量减小"};int[] keys={88,85,87,24,25};
        for(int i=0;i<keys.length;i++){
            int key=keys[i];ImageView t=icon(symbols[i],Color.WHITE);t.setContentDescription(labels[i]);t.setTooltipText(labels[i]);
            t.setBackground(new MediaPressRipple(this,14));
            controls.addView(t,new LinearLayout.LayoutParams(0,dp(36),1));
            if(key==24||key==25)new RepeatPress(t,()->adjustMediaVolume(key==24?AudioManager.ADJUST_RAISE:AudioManager.ADJUST_LOWER));
            else{
                if(key==KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE)playPause=t;
                t.setOnClickListener(v->worker.execute(()->{
                    dev.edgeflow.ActionExecutor.dispatchMediaKey(audio,key==KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE?brightnessControl.mediaKey():key);
                    if(key==KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE)runOnUiThread(this::playbackChanged);
                }));
            }
        }
        for(int r=0;r<3;r++){
            LinearLayout line=row();LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(-1,dp(48));lp.topMargin=dp(13);panel.addView(line,lp);
            for(int col=0;col<4;col++){int at=r*4+col;FrameLayout cell=new FrameLayout(this);line.addView(cell,new LinearLayout.LayoutParams(0,-1,1));if(at>=tiles.size())continue;
                ToolsConfig.Item item=tiles.get(at);ImageView t=new ImageView(this);t.setImageDrawable(ToolsConfig.icon(this,item));t.setScaleType(ImageView.ScaleType.FIT_CENTER);if(item.component()==null)t.setColorFilter(Color.WHITE);else t.setPadding(dp(7),dp(7),dp(7),dp(7));
                toolViews.put(item.key,t);t.setContentDescription(item.label);FrameLayout.LayoutParams p=new FrameLayout.LayoutParams(dp(41),dp(41),Gravity.CENTER);cell.addView(t,p);t.setOnClickListener(v->act(item));t.setOnLongClickListener(v->{Toast.makeText(this,item.label,Toast.LENGTH_SHORT).show();return true;});}
        }
        backdrop.preserve(media,PanelStyle.dp(this,22),0);backdrop.preserve(sun,PanelStyle.dp(this,22),0);backdrop.preserve(light,0,dp(25));
        for(ImageView tile:toolViews.values())backdrop.preserve(tile,PanelStyle.dp(this,22),0);
        try{audio.registerAudioPlaybackCallback(playback,liveHandler);watchingPlayback=true;}
        catch(SecurityException unavailable){android.util.Log.d("ReplicaTools","Playback notifications unavailable");}
        getContentResolver().registerContentObserver(Settings.System.getUriFor(Settings.System.SCREEN_BRIGHTNESS_MODE),false,brightnessMode);
        refresh();brightnessControl.start();liveHandler.postDelayed(liveRefresh,500);
        ColorOsTheme.watch(panel,()->{if(closed)return;if(autoIcon!=null){autoIcon.setTag(null);autoIcon.invalidate();}for(ImageView tile:toolViews.values()){tile.setTag(null);tile.invalidate();}refresh();});
    }
    /** This firmware rejects setStreamVolume for this package, but permits adjustments.
     * Coalesce drag targets off the UI thread; never queue one task per motion event. */
    private synchronized void setMediaVolume(int value){
        if(closed)return;
        int target=Math.max(0,Math.min(audio.getStreamMaxVolume(AudioManager.STREAM_MUSIC),value));
        if(pendingDrag!=null){pendingDrag.target=target;return;}
        pendingDrag=new VolumeDrag(target);worker.execute(pendingDrag);
    }
    private final class VolumeDrag implements Runnable{
        volatile int target;
        VolumeDrag(int target){this.target=target;}
        public void run(){
            try{
                int maximum=audio.getStreamMaxVolume(AudioManager.STREAM_MUSIC),lastTarget=-1,steps=0;
                while(!closed){
                    int current=audio.getStreamVolume(AudioManager.STREAM_MUSIC),wanted=target;
                    if(wanted!=lastTarget){lastTarget=wanted;steps=0;}
                    if(current==wanted){
                        synchronized(ToolsPanel.this){
                            if(target!=wanted)continue;
                            if(pendingDrag==this)pendingDrag=null;
                        }
                        break;
                    }
                    if(++steps>maximum+1)throw new IllegalStateException("Volume target was not reached");
                    audio.adjustStreamVolume(AudioManager.STREAM_MUSIC,wanted>current?AudioManager.ADJUST_RAISE:AudioManager.ADJUST_LOWER,0);
                    if(audio.getStreamVolume(AudioManager.STREAM_MUSIC)==current)throw new IllegalStateException("Volume did not change");
                }
            }catch(RuntimeException error){volumeFailed(error);}
            finally{synchronized(ToolsPanel.this){if(pendingDrag==this)pendingDrag=null;}runOnUiThread(ToolsPanel.this::refreshVolume);}
        }
    }
    private synchronized void adjustMediaVolume(int direction){
        if(closed)return;
        pendingDrag=null; // A later slider change belongs after this button press.
        worker.execute(()->{
            if(closed)return;
            try{
                if(direction==AudioManager.ADJUST_TOGGLE_MUTE){
                    boolean silent=mediaSilent();
                    audio.adjustStreamVolume(AudioManager.STREAM_MUSIC,silent?AudioManager.ADJUST_UNMUTE:AudioManager.ADJUST_MUTE,0);
                    // Zero can also be a volume index without an active mute flag.
                    if(silent&&!audio.isStreamMute(AudioManager.STREAM_MUSIC)&&audio.getStreamVolume(AudioManager.STREAM_MUSIC)==0)
                        audio.adjustStreamVolume(AudioManager.STREAM_MUSIC,AudioManager.ADJUST_RAISE,0);
                }else audio.adjustStreamVolume(AudioManager.STREAM_MUSIC,direction,0);
            }catch(RuntimeException error){volumeFailed(error);}
            runOnUiThread(this::refreshVolume);
        });
    }
    private boolean mediaSilent(){return audio.isStreamMute(AudioManager.STREAM_MUSIC)||audio.getStreamVolume(AudioManager.STREAM_MUSIC)==0;}
    private void volumeFailed(RuntimeException error){
        android.util.Log.w("ReplicaTools","Volume adjustment failed",error);
        runOnUiThread(()->Toast.makeText(this,"音量调整未完成",Toast.LENGTH_SHORT).show());
    }
    private void mediaButton(ImageView button,String icon,String action){
        button.setImageDrawable(SystemIcons.control(this,icon));button.setContentDescription(action);button.setTooltipText(action);
    }
    private LinearLayout row(){LinearLayout r=new LinearLayout(this);r.setOrientation(LinearLayout.HORIZONTAL);r.setGravity(Gravity.CENTER_VERTICAL);return r;}
    private ImageView icon(String name,int color){ImageView v=new ImageView(this);v.setImageDrawable(SystemIcons.control(this,name));v.setColorFilter(color);v.setScaleType(ImageView.ScaleType.FIT_CENTER);return v;}
    private void tintSlider(SeekBar v){v.setProgressTintList(ColorStateList.valueOf(0xff3979ff));v.setThumbTintList(ColorStateList.valueOf(0xff3979ff));}
    private TextView label(String text,int size){TextView t=new TextView(this);t.setText(text);t.setGravity(Gravity.CENTER);t.setTextSize(size);t.setTextColor(0xff3979ff);return t;}
    private int lastRinger=-1;
    private void refreshVolume(){
        if(closed||audio==null)return;
        if(liveVolume!=null&&!liveVolume.isPressed())liveVolume.setProgress(audio.getStreamVolume(3));
        boolean muted=mediaSilent();
        if(!Boolean.valueOf(muted).equals(shownMuted)){
            mediaButton(speaker,muted?"volume_media_mute":"volume_media",muted?"取消媒体静音":"媒体静音");shownMuted=muted;
        }
    }
    private void showPlaying(boolean playing){
        if(!Boolean.valueOf(playing).equals(shownPlaying)){
            mediaButton(playPause,playing?"player_pause":"player_play",playing?"暂停":"播放");shownPlaying=playing;
        }
    }
    private void refresh(){
        if(closed||audio==null)return;
        if(brightnessControl!=null)brightnessControl.prewarm();
        refreshVolume();refreshPlayback.run();
        if(autoIcon!=null){boolean on=Settings.System.getInt(getContentResolver(),"screen_brightness_mode",0)==1;autoIcon.setSelected(on);if(!Boolean.valueOf(on).equals(autoIcon.getTag())){autoIcon.setBackground(PanelStyle.toolButton(this,on));autoIcon.setTag(on);}autoIcon.setColorFilter(Color.WHITE);}

        boolean accentActions=AppPrefs.get(this).getBoolean(AppPrefs.QUICK_TOOLS_ACTION_ACCENT,false);
        for(ToolsConfig.Item item:tiles){
            ImageView tile=toolViews.get(item.key);if(tile==null)continue;
            Boolean state=readState(item);boolean on=Boolean.TRUE.equals(state);
            boolean available=ToolsConfig.available(this,item.builtin());
            boolean highlighted=on||(accentActions&&available&&item.isOneShot());
            tile.setEnabled(available);tile.setAlpha(available?1f:.45f);
            tile.setStateDescription(!available?"此设备不支持":state==null?null:on?"已开启":"已关闭");
            if(!Boolean.valueOf(highlighted).equals(tile.getTag())){
                tile.setBackground(PanelStyle.toolButton(this,highlighted));tile.setTag(highlighted);tile.setElevation(0);
                if(item.component()==null)tile.setImageDrawable(SystemIcons.tile(this,item.builtin(),on,audio.getRingerMode()));
            }
        }
        ImageView sound=toolViews.get("1");int ringer=audio.getRingerMode();if(sound!=null&&lastRinger!=ringer){sound.setImageDrawable(SystemIcons.tile(this,1,ringer!=2,ringer));lastRinger=ringer;}
    }
    private boolean active(ToolsConfig.Item item){return Boolean.TRUE.equals(readState(item));}
    private Boolean readState(ToolsConfig.Item item){try{switch(item.builtin()){
        case 0:return Settings.Global.getInt(getContentResolver(),"wifi_on",0)==1;
        case 1:return audio.getRingerMode()!=AudioManager.RINGER_MODE_NORMAL;
        case 2:return Settings.Global.getInt(getContentResolver(),"bluetooth_on",0)==1;
        case 3:return Settings.System.getInt(getContentResolver(),"accelerometer_rotation",0)==1;
        case 7:return torch;
        case 8:return (getResources().getConfiguration().uiMode&48)==32;
        case 12:return Settings.Global.getInt(getContentResolver(),"mobile_data",0)==1;
        case 13:return Settings.Global.getInt(getContentResolver(),"airplane_mode_on",0)==1;
        case 14:return ((PowerManager)getSystemService(POWER_SERVICE)).isPowerSaveMode();
        case 15:return ((android.location.LocationManager)getSystemService(LOCATION_SERVICE)).isLocationEnabled();
        case 16:return ((NotificationManager)getSystemService(NOTIFICATION_SERVICE)).getCurrentInterruptionFilter()!=NotificationManager.INTERRUPTION_FILTER_ALL;
        case 17:android.nfc.NfcAdapter nfc=android.nfc.NfcAdapter.getDefaultAdapter(this);return nfc!=null&&nfc.isEnabled();
        case 19:return Settings.System.getInt(getContentResolver(),"screen_brightness_mode",0)==1;
        default:return null;
    }}catch(Exception e){return null;}}
    private final CameraManager.TorchCallback torchCallback=new CameraManager.TorchCallback(){@Override public void onTorchModeChanged(String id,boolean enabled){if(id.equals(cameraId)){torch=enabled;refresh();}}};
    private void act(ToolsConfig.Item item){
        if(closed||hasChildDialogs())return;int id=item.builtin();
        if(id<0){Intent intent=ToolsConfig.launchIntent(this,item);if(intent==null){Toast.makeText(this,"应用不可用，请在快速工具设置中重新选择",Toast.LENGTH_SHORT).show();return;}launchAfterClose(intent);return;}
        android.util.Log.i("ReplicaTools","tool="+id);
        switch(id){
            case 12:command("svc data "+(active(item)?"disable":"enable"));break;
            case 13:command("cmd connectivity airplane-mode "+(active(item)?"disable":"enable"));break;
            case 14:command("cmd power set-mode "+(active(item)?"0":"1"));break;
            case 15:command("cmd location set-location-enabled "+(!active(item)));break;
            case 16:command("cmd notification set_dnd "+(active(item)?"all":"priority"));break;
            case 17:command("cmd nfc "+(active(item)?"disable-nfc persist":"enable-nfc"));break;
            case 18:launchAfterClose(new Intent(Settings.ACTION_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));break;
            case 19:command("settings put system screen_brightness_mode "+(active(item)?0:1));break;
            case 0:command("if [ \"$(settings get global wifi_on)\" = 1 ]; then svc wifi disable; else svc wifi enable; fi");break;
            case 1:command("CLASSPATH='"+getApplicationInfo().sourceDir.replace("'","'\\''")+"' app_process /system/bin replica.OplusBridge ringer "+((audio.getRingerMode()+1)%3));break;
            case 2:command("if [ \"$(settings get global bluetooth_on)\" = 1 ]; then svc bluetooth disable; else svc bluetooth enable; fi");break;
            case 3:command("settings put system accelerometer_rotation "+(Settings.System.getInt(getContentResolver(),"accelerometer_rotation",0)==0?1:0));break;
            case 4:commandAfterClose("cmd statusbar expand-notifications");break;
            case 5:commandAfterClose("am start -n com.oplus.screenrecorder/com.oplus.screenrecorder.MainActivity --es recorder_entrance SystemUIScreenAssistant");break;
            case 6:commandAfterClose("input keyevent 120");break;
            case 7:try{if(cameraId!=null)cameras.setTorchMode(cameraId,!torch);}catch(Exception e){Toast.makeText(this,"手电筒不可用",Toast.LENGTH_SHORT).show();}break;
            case 8:command("cmd uimode night "+((getResources().getConfiguration().uiMode&48)==32?"no":"yes"));break;
            case 9:commandAfterClose("input keyevent 26");break;
            case 10:
                final int[] seconds={15,30,60,120,300,600};
                String[] labels={"15 秒","30 秒","1 分钟","2 分钟","5 分钟","10 分钟"};
                int current=Settings.System.getInt(getContentResolver(),"screen_off_timeout",30000)/1000;int selected=-1;
                for(int i=0;i<seconds.length;i++)if(seconds[i]==current)selected=i;
                showChild(new GlassDialogBuilder(this).setTitle("屏幕超时").setSingleChoiceItems(labels,selected,(dialog,which)->{
                    command("settings put system screen_off_timeout "+(seconds[which]*1000));dialog.dismiss();
                }).setNegativeButton("取消",null).create());break;
            case 11:
                closeThen(()->dev.edgeflow.ActionExecutor.closeCurrentApp(dev.edgeflow.GestureService.instance(),closeTarget));
                break;
        }
    }
    public void closeResources(){if(closed)return;closed=true;for(AlertDialog child:new java.util.ArrayList<>(childDialogs))ColorDialogBuilder.dismissImmediately(child);childDialogs.clear();getContentResolver().unregisterContentObserver(brightnessMode);if(watchingPlayback)audio.unregisterAudioPlaybackCallback(playback);liveHandler.removeCallbacksAndMessages(null);MaterialBackdrop.release(this);if(brightnessControl!=null)brightnessControl.close();if(cameras!=null&&cameraId!=null)cameras.unregisterTorchCallback(torchCallback);worker.shutdown();}
}
