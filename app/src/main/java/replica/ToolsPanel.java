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
import java.util.concurrent.*;

/** Compact panel with the exact saved OHO tool order, using native Android actions. */
public class ToolsPanel extends android.view.ContextThemeWrapper {
    private final Window window;private final Runnable close;private volatile boolean closed;
    private final java.util.ArrayList<AlertDialog> childDialogs=new java.util.ArrayList<>();
    public boolean hasChildDialogs(){return !childDialogs.isEmpty();}
    private void showChild(AlertDialog dialog){
        if(closed||hasChildDialogs())return;Window child=dialog.getWindow();if(overlay()){child.setType(WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY);child.addFlags(WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE);}
        childDialogs.add(dialog);dialog.setOnDismissListener(d->childDialogs.remove(dialog));try{dialog.show();}catch(RuntimeException error){childDialogs.remove(dialog);ColorDialogBuilder.dismissImmediately(dialog);android.util.Log.w("ReplicaTools","Unable to open settings",error);}
    }
    public float[] anchor(){return gesture;}
    public boolean overlay(){return !(getBaseContext() instanceof Activity);}
    private Window getWindow(){return window;}
    private void setContentView(View v){window.setContentView(v);}
    private void finish(){close.run();}
    private void runOnUiThread(Runnable r){liveHandler.post(()->{if(!closed)r.run();});}
    public void position(){PanelGeometry.position(this,window,dp(260),dp(360),true,gesture);}
    private final ExecutorService worker=Executors.newSingleThreadExecutor();
    private AudioManager audio; private BrightnessControl brightnessControl; private float[] gesture;
    private SeekBar liveVolume; private ImageView autoIcon; private final Handler liveHandler=new Handler();
    private String lastPoll="";private int pendingVolume=-1;private boolean volumeWriting;
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
        LinearLayout panel=new LinearLayout(this){private boolean blocked;@Override public boolean dispatchTouchEvent(MotionEvent event){int action=event.getActionMasked();if(action==MotionEvent.ACTION_DOWN)blocked=hasChildDialogs();boolean consume=blocked;if(action==MotionEvent.ACTION_UP||action==MotionEvent.ACTION_CANCEL)blocked=false;return consume||super.dispatchTouchEvent(event);}};panel.setOrientation(1);panel.setPadding(dp(14),dp(14),dp(14),dp(14));setContentView(panel);PanelStyle.toolsWindow(this,window);
        ToolsBackdrop backdrop=new ToolsBackdrop(this,window);window.setBackgroundDrawable(backdrop);
        position();
        LinearLayout brightness=row();brightness.setPadding(dp(7),0,dp(7),0);panel.addView(brightness);
        ImageView sun=icon("brightness_icon",PanelStyle.accent(this));autoIcon=sun;sun.setContentDescription("自动亮度");sun.setOnClickListener(v->command("settings put system screen_brightness_mode "+(Settings.System.getInt(getContentResolver(),"screen_brightness_mode",0)==1?0:1)));brightness.addView(sun,new LinearLayout.LayoutParams(dp(35),dp(34)));
        SeekBar light=new GlassSeekBar(this);LinearLayout.LayoutParams lightParams=new LinearLayout.LayoutParams(0,dp(38),1);if(gesture[0]<getResources().getDisplayMetrics().widthPixels/2f)lightParams.rightMargin=dp(8);else lightParams.leftMargin=dp(8);brightness.addView(light,lightParams);brightnessControl=new BrightnessControl(this,light);
        if(gesture[0]<getResources().getDisplayMetrics().widthPixels/2f){brightness.removeView(sun);brightness.addView(sun);}
        LinearLayout media=new LinearLayout(this);media.setOrientation(1);media.setPadding(dp(12),dp(6),dp(12),dp(10));media.setBackground(PanelStyle.mediaSurface(this));
        LinearLayout.LayoutParams mp=new LinearLayout.LayoutParams(-1,dp(96));mp.topMargin=dp(14);panel.addView(media,mp);
        LinearLayout vr=row();media.addView(vr);vr.addView(icon("volume_media",Color.WHITE),new LinearLayout.LayoutParams(dp(35),dp(34)));
        SeekBar volume=new GlassSeekBar(this,true);liveVolume=volume;volume.setContentDescription("媒体音量");tintSlider(volume);volume.setMax(audio.getStreamMaxVolume(AudioManager.STREAM_MUSIC));volume.setProgress(audio.getStreamVolume(AudioManager.STREAM_MUSIC));LinearLayout.LayoutParams volumeParams=new LinearLayout.LayoutParams(0,dp(34),1);if(gesture[0]<getResources().getDisplayMetrics().widthPixels/2f)volumeParams.rightMargin=dp(8);else volumeParams.leftMargin=dp(8);vr.addView(volume,volumeParams);
        volume.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener(){public void onStartTrackingTouch(SeekBar b){}public void onStopTrackingTouch(SeekBar b){}public void onProgressChanged(SeekBar b,int v,boolean user){if(user)setMediaVolume(v);}});
        if(gesture[0]<getResources().getDisplayMetrics().widthPixels/2f){View speaker=vr.getChildAt(0);vr.removeView(speaker);vr.addView(speaker);}
        LinearLayout controls=row();LinearLayout.LayoutParams controlsParams=new LinearLayout.LayoutParams(-1,dp(36));controlsParams.topMargin=dp(10);media.addView(controls,controlsParams);
        String[] symbols={"player_prev","player_play","player_next","ic_volume_up","ic_volume_down"};int[] keys={88,85,87,24,25};
        for(int i=0;i<5;i++){final int key=keys[i];ImageView t=icon(symbols[i],Color.WHITE);controls.addView(t,new LinearLayout.LayoutParams(0,dp(36),1));if(key==24||key==25){t.setContentDescription(key==24?"音量增加":"音量减小");new RepeatPress(t,()->{audio.adjustStreamVolume(3,key==24?1:-1,0);refresh();});}else t.setOnClickListener(v->command("input keyevent "+key));}
        for(int r=0;r<3;r++){
            LinearLayout line=row();LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(-1,dp(48));lp.topMargin=dp(13);panel.addView(line,lp);
            for(int col=0;col<4;col++){int at=r*4+col;FrameLayout cell=new FrameLayout(this);line.addView(cell,new LinearLayout.LayoutParams(0,-1,1));if(at>=tiles.size())continue;
                ToolsConfig.Item item=tiles.get(at);ImageView t=new ImageView(this);t.setImageDrawable(ToolsConfig.icon(this,item));t.setScaleType(ImageView.ScaleType.FIT_CENTER);if(item.component()==null)t.setColorFilter(Color.WHITE);else t.setPadding(dp(7),dp(7),dp(7),dp(7));
                toolViews.put(item.key,t);t.setContentDescription(item.label);FrameLayout.LayoutParams p=new FrameLayout.LayoutParams(dp(41),dp(41),Gravity.CENTER);cell.addView(t,p);t.setOnClickListener(v->act(item));t.setOnLongClickListener(v->{Toast.makeText(this,item.label,Toast.LENGTH_SHORT).show();return true;});}
        }
        backdrop.preserve(media,PanelStyle.dp(this,22),0);backdrop.preserve(sun,PanelStyle.dp(this,22),0);backdrop.preserve(light,0,dp(25));
        for(ImageView tile:toolViews.values())backdrop.preserve(tile,PanelStyle.dp(this,22),0);
        refresh();brightnessControl.start();liveHandler.post(liveRefresh);
        ColorOsTheme.watch(panel,()->{if(closed)return;if(autoIcon!=null){autoIcon.setTag(null);autoIcon.invalidate();}for(ImageView tile:toolViews.values()){tile.setTag(null);tile.invalidate();}refresh();});
    }
    /** This firmware rejects setStreamVolume for this package, but permits adjustments.
     * Coalesce drag targets off the UI thread; never queue one task per motion event. */
    private void setMediaVolume(int target){
        synchronized(this){if(closed)return;pendingVolume=Math.max(0,Math.min(audio.getStreamMaxVolume(3),target));if(volumeWriting)return;volumeWriting=true;}
        worker.submit(()->{
            try{
                while(!closed){
                    int desired;
                    synchronized(this){if(pendingVolume<0){volumeWriting=false;return;}desired=pendingVolume;pendingVolume=-1;}
                    for(int step=0;step<=audio.getStreamMaxVolume(3)&&!closed;step++){
                        synchronized(this){if(pendingVolume>=0){desired=pendingVolume;pendingVolume=-1;step=0;}}
                        int current=audio.getStreamVolume(3);if(current==desired)break;
                        audio.adjustStreamVolume(3,desired>current?1:-1,0);
                        if(audio.getStreamVolume(3)==current)throw new IllegalStateException("System did not accept volume adjustment");
                    }
                    runOnUiThread(this::refresh);
                }
                synchronized(this){volumeWriting=false;pendingVolume=-1;}
            }catch(Exception e){synchronized(this){volumeWriting=false;pendingVolume=-1;}android.util.Log.e("ReplicaTools","Volume adjustment failed",e);runOnUiThread(()->{refresh();Toast.makeText(this,"音量调整未完成",Toast.LENGTH_SHORT).show();});}
        });
    }
    private LinearLayout row(){LinearLayout r=new LinearLayout(this);r.setOrientation(0);r.setGravity(Gravity.CENTER_VERTICAL);return r;}
    private ImageView icon(String name,int color){ImageView v=new ImageView(this);v.setImageDrawable(SystemIcons.control(this,name));v.setColorFilter(color);v.setScaleType(ImageView.ScaleType.FIT_CENTER);return v;}
    private void tintSlider(SeekBar v){v.setProgressTintList(ColorStateList.valueOf(0xff3979ff));v.setThumbTintList(ColorStateList.valueOf(0xff3979ff));}
    private TextView label(String text,int size){TextView t=new TextView(this);t.setText(text);t.setGravity(Gravity.CENTER);t.setTextSize(size);t.setTextColor(0xff3979ff);return t;}
    private int lastRinger=-1;
    private void refresh(){
        if(closed||audio==null)return;
        if(liveVolume!=null&&!liveVolume.isPressed())liveVolume.setProgress(audio.getStreamVolume(3));
        if(autoIcon!=null){boolean on=Settings.System.getInt(getContentResolver(),"screen_brightness_mode",0)==1;autoIcon.setSelected(on);if(!Boolean.valueOf(on).equals(autoIcon.getTag())){autoIcon.setBackground(PanelStyle.toolButton(this,on));autoIcon.setTag(on);}autoIcon.setColorFilter(Color.WHITE);}

        for(ToolsConfig.Item item:tiles){ImageView tile=toolViews.get(item.key);boolean on=active(item);if(tile!=null&&!Boolean.valueOf(on).equals(tile.getTag())){tile.setBackground(PanelStyle.toolButton(this,on));tile.setTag(on);tile.setElevation(0);if(item.component()==null)tile.setImageDrawable(SystemIcons.tile(this,item.builtin(),on,audio.getRingerMode()));}}
        ImageView sound=toolViews.get("1");int ringer=audio.getRingerMode();if(sound!=null&&lastRinger!=ringer){sound.setImageDrawable(SystemIcons.tile(this,1,ringer!=2,ringer));lastRinger=ringer;}
    }
    private boolean active(ToolsConfig.Item item){try{switch(item.builtin()){
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
        default:return true; // Immediate commands and app launchers are always highlighted.
    }}catch(Exception e){return false;}}
    private final CameraManager.TorchCallback torchCallback=new CameraManager.TorchCallback(){@Override public void onTorchModeChanged(String id,boolean enabled){if(id.equals(cameraId)){torch=enabled;refresh();}}};
    private void act(ToolsConfig.Item item){
        if(closed||hasChildDialogs())return;int id=item.builtin();
        if(id<0){Intent intent=ToolsConfig.launchIntent(this,item);if(intent==null){Toast.makeText(this,"应用不可用，请在快速工具设置中重新选择",Toast.LENGTH_SHORT).show();return;}try{finish();startActivity(intent);}catch(Exception e){Toast.makeText(this,"无法打开应用",Toast.LENGTH_SHORT).show();}return;}
        android.util.Log.i("ReplicaTools","tool="+id);
        switch(id){
            case 12:command("svc data "+(active(item)?"disable":"enable"));break;
            case 13:command("cmd connectivity airplane-mode "+(active(item)?"disable":"enable"));break;
            case 14:command("cmd power set-mode "+(active(item)?"0":"1"));break;
            case 15:command("cmd location set-location-enabled "+(!active(item)));break;
            case 16:command("cmd notification set_dnd "+(active(item)?"all":"priority"));break;
            case 17:command("cmd nfc "+(active(item)?"disable-nfc persist":"enable-nfc"));break;
            case 18:finish();startActivity(new Intent(Settings.ACTION_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));break;
            case 19:command("settings put system screen_brightness_mode "+(active(item)?0:1));break;
            case 0:command("if [ \"$(settings get global wifi_on)\" = 1 ]; then svc wifi disable; else svc wifi enable; fi");break;
            case 1:command("CLASSPATH='"+getApplicationInfo().sourceDir.replace("'","'\\''")+"' app_process /system/bin replica.OplusBridge ringer "+((audio.getRingerMode()+1)%3));break;
            case 2:command("if [ \"$(settings get global bluetooth_on)\" = 1 ]; then svc bluetooth disable; else svc bluetooth enable; fi");break;
            case 3:command("settings put system accelerometer_rotation "+(Settings.System.getInt(getContentResolver(),"accelerometer_rotation",0)==0?1:0));break;
            case 4:finish();command("cmd statusbar expand-notifications");break;
            case 5:finish();command("am start -n com.oplus.screenrecorder/com.oplus.screenrecorder.MainActivity --es recorder_entrance SystemUIScreenAssistant");break;
            case 6:finish();command("sleep 0.3; input keyevent 120");break;
            case 7:try{if(cameraId!=null)cameras.setTorchMode(cameraId,!torch);}catch(Exception e){Toast.makeText(this,"手电筒不可用",Toast.LENGTH_SHORT).show();}break;
            case 8:command("cmd uimode night "+((getResources().getConfiguration().uiMode&48)==32?"no":"yes"));break;
            case 9:finish();command("input keyevent 26");break;
            case 10:
                final int[] seconds={15,30,60,120,300,600};
                String[] labels={"15 秒","30 秒","1 分钟","2 分钟","5 分钟","10 分钟"};
                int current=Settings.System.getInt(getContentResolver(),"screen_off_timeout",30000)/1000;int selected=-1;
                for(int i=0;i<seconds.length;i++)if(seconds[i]==current)selected=i;
                showChild(new GlassDialogBuilder(this).setTitle("屏幕超时").setSingleChoiceItems(labels,selected,(dialog,which)->{
                    command("settings put system screen_off_timeout "+(seconds[which]*1000));dialog.dismiss();
                }).setNegativeButton("取消",null).create());break;
            case 11:
                finish();
                dev.edgeflow.ActionExecutor.closeCurrentApp(dev.edgeflow.GestureService.instance(), closeTarget);
                break;
        }
    }
    public void closeResources(){if(closed)return;closed=true;for(AlertDialog child:new java.util.ArrayList<>(childDialogs))ColorDialogBuilder.dismissImmediately(child);childDialogs.clear();liveHandler.removeCallbacksAndMessages(null);MaterialBackdrop.release(this);if(brightnessControl!=null)brightnessControl.close();if(cameras!=null&&cameraId!=null)cameras.unregisterTorchCallback(torchCallback);worker.shutdown();}
}
