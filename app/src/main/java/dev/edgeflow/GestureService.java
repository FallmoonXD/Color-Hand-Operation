package dev.edgeflow;

import android.accessibilityservice.AccessibilityService;
import android.accessibilityservice.AccessibilityServiceInfo;
import android.app.KeyguardManager;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.content.res.Configuration;
import android.database.ContentObserver;
import android.graphics.PixelFormat;
import android.graphics.Point;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.os.PowerManager;
import android.provider.Settings;
import android.util.Log;
import android.view.Gravity;
import android.view.View;
import android.view.WindowManager;
import android.view.accessibility.AccessibilityEvent;
import android.view.accessibility.AccessibilityWindowInfo;

/** Standalone accessibility-bound gesture host. No dependency on an existing APK. */
public final class GestureService extends AccessibilityService implements SharedPreferences.OnSharedPreferenceChangeListener {
    private static final String TAG="EdgeGesture";
    private static volatile GestureService running;
    private final Handler main=new Handler(Looper.getMainLooper());
    private WindowManager windows;
    private SharedPreferences prefs;
    private EdgeHandleView left,right,extraLeft,extraRight,extra2Left,extra2Right;
    private GestureWaveView wave,leftWave,rightWave;
    private boolean connected,handlesVisible,screenOn=true,imeVisible,notificationsFocused,receiverRegistered;
    private String foreground="",previousForeground="",visibilityPackage="",homePackage="";
    private int screenWidth,screenHeight;
    private float screenDensity;
    private GestureHandleConfig.Area handleArea;
    private final java.util.HashMap<String,View> handlePreviews=new java.util.HashMap<>();
    private final java.util.HashMap<String,float[]> handlePreviewRegions=new java.util.HashMap<>();
    private final HandlePreviewOwner handlePreviewOwner=new HandlePreviewOwner();
    private final Runnable clearHandlePreviews=()->{if(handlePreviewOwner.release(HandlePreviewOwner.TEMPORARY))removeHandlePreviews();};
    private final ContentObserver authorization=new ContentObserver(main){@Override public void onChange(boolean selfChange){if(!isEnabledByUser(GestureService.this)){disconnect();stopSelf();}}};
    private boolean pendingWindowState,pendingConfiguration,pendingGeometry;
    private final Runnable applyPreferences=this::applyPreferenceChanges;
    private final BroadcastReceiver changes=new BroadcastReceiver(){@Override public void onReceive(Context c,Intent intent){
        String a=intent.getAction();if(Intent.ACTION_SCREEN_OFF.equals(a)){screenOn=false;cancelGesture();removeHandlePreviews();dismissPanels();}
        else if(Intent.ACTION_SCREEN_ON.equals(a)||Intent.ACTION_USER_PRESENT.equals(a))screenOn=true;
        if((getPackageName()+".RELOAD_GESTURES").equals(a))schedulePreferences(true);else updateVisibility();
    }};
    public static GestureService instance(){return running;}
    /** Temporary visual aid; drafts never change the real input windows or preferences. */
    public static void beginHandlePreview(Context context,String id,float width,float start,float end){
        GestureService s=running;if(s==null||!java.util.Arrays.asList(GestureHandleConfig.IDS).contains(id))return;
        if(!Float.isFinite(width)||!Float.isFinite(start)||!Float.isFinite(end))return;
        s.main.post(()->{s.handlePreviewOwner.replace(HandlePreviewOwner.TEMPORARY);s.showHandlePreview(id,width,start,end,false);});
    }
    /** Settings-only visuals follow their Activity lifecycle; no real handle state is edited. */
    public static void setSettingsHandlePreviews(Context context,Object owner,java.util.Map<String,float[]> regions){
        GestureService s=running;if(s==null||owner==null)return;java.util.HashMap<String,float[]> copy=new java.util.HashMap<>();
        for(java.util.Map.Entry<String,float[]> entry:regions.entrySet()){
            float[] value=entry.getValue();if(!java.util.Arrays.asList(GestureHandleConfig.IDS).contains(entry.getKey())||value==null||value.length!=3)continue;
            if(Float.isFinite(value[0])&&Float.isFinite(value[1])&&Float.isFinite(value[2]))copy.put(entry.getKey(),value.clone());
        }
        s.main.post(()->{
            if(!s.connected||!s.screenOn){s.removeHandlePreviews();return;}s.handlePreviewOwner.replace(owner);s.main.removeCallbacks(s.clearHandlePreviews);
            for(String id:new java.util.ArrayList<>(s.handlePreviews.keySet()))if(!copy.containsKey(id)){s.remove(s.handlePreviews.remove(id));s.handlePreviewRegions.remove(id);}
            for(java.util.Map.Entry<String,float[]> entry:copy.entrySet()){float[] value=entry.getValue();s.showHandlePreview(entry.getKey(),value[0],value[1],value[2],true);}
        });
    }
    /** Cleanup is checked when executed, after any newer Activity has claimed the windows. */
    public static void endSettingsHandlePreviews(Object owner){GestureService s=running;if(s!=null)s.main.post(()->{if(s.handlePreviewOwner.release(owner))s.removeHandlePreviews();});}
    public static void endHandlePreview(){GestureService s=running;if(s!=null)s.main.post(s.clearHandlePreviews);}
    private void showHandlePreview(String id,float width,float start,float end,boolean settingsPage){
        if(!connected||!screenOn)return;
        if(handleArea==null)return;int[] b=GestureHandleConfig.bounds(id,width,start,end,getResources().getDisplayMetrics().density,handleArea);
        try{
            handlePreviewRegions.put(id,new float[]{width,start,end});
            View view=handlePreviews.get(id);WindowManager.LayoutParams lp=parameters(b[2],b[3],b[0],b[1]);lp.flags|=WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE;lp.setTitle(getString(R.string.app_name)+" 触控区域预览 "+id);
            if(view==null){view=new View(this);view.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);view.setBackground(replica.PanelStyle.shape(this,(HandlePreviewView.groupColor(this,id)&0xffffff)|0x88000000,3));handlePreviews.put(id,view);windows.addView(view,lp);}else windows.updateViewLayout(view,lp);
            main.removeCallbacks(clearHandlePreviews);if(!settingsPage)main.postDelayed(clearHandlePreviews,10000);
        }catch(RuntimeException e){Log.w(TAG,"Cannot show handle preview",e);removeHandlePreviews();}
    }
    private void removeHandlePreviews(){handlePreviewOwner.clear();main.removeCallbacks(clearHandlePreviews);for(View view:handlePreviews.values())remove(view);handlePreviews.clear();handlePreviewRegions.clear();}
    private void updateHandlePreviews(){
        for(java.util.Map.Entry<String,float[]> entry:handlePreviewRegions.entrySet()){
            View view=handlePreviews.get(entry.getKey());if(view==null)continue;float[] value=entry.getValue();int[] b=GestureHandleConfig.bounds(entry.getKey(),value[0],value[1],value[2],screenDensity,handleArea);
            WindowManager.LayoutParams old=(WindowManager.LayoutParams)view.getLayoutParams();if(old.x==b[0]&&old.y==b[1]&&old.width==b[2]&&old.height==b[3])continue;
            WindowManager.LayoutParams next=new WindowManager.LayoutParams();next.copyFrom(old);next.x=b[0];next.y=b[1];next.width=b[2];next.height=b[3];try{windows.updateViewLayout(view,next);}catch(RuntimeException error){Log.w(TAG,"Cannot update handle preview",error);}
        }
    }
    private void scheduleGeometryRefresh(){pendingGeometry=true;schedulePreferences(false);}
    public static void reload(Context c){GestureService s=running;if(s!=null)s.schedulePreferences(false);else c.sendBroadcast(new Intent(c.getPackageName()+".RELOAD_GESTURES").setPackage(c.getPackageName()));}
    private void schedulePreferences(boolean refreshWindows){
        if(Looper.myLooper()!=main.getLooper()){main.post(()->schedulePreferences(refreshWindows));return;}
        if(!connected)return;pendingWindowState|=refreshWindows;main.removeCallbacks(applyPreferences);main.postDelayed(applyPreferences,16);
    }
    private void applyPreferenceChanges(){
        if(!connected)return;
        if(pendingConfiguration||leftWave==null||rightWave==null){pendingConfiguration=pendingWindowState=pendingGeometry=false;rebuild();return;}
        if(pendingGeometry){pendingGeometry=false;try{GestureHandleConfig.Area next=HandleDisplayArea.current(this);if(next.displayWidth!=screenWidth||next.displayHeight!=screenHeight){rebuild();return;}handleArea=next;}catch(RuntimeException error){Log.w(TAG,"Cannot refresh safe display bounds",error);}}
        if(pendingWindowState){pendingWindowState=false;refreshWindowState();}
        EdgeHandleView[] handles=handleViews();
        for(int i=0;i<handles.length;i++)if(handles[i]!=null){updateHandleLayout(handles[i],GestureHandleConfig.IDS[i]);handles[i].refreshAppearance();}
        updateHandlePreviews();
        updateWaveLayout(leftWave,true);updateWaveLayout(rightWave,false);
        updateVisibility();
    }
    public String foregroundPackage(){return foreground;}
    public String previousForegroundPackage(){return previousForeground;}
    @Override public void onCreate(){super.onCreate();windows=(WindowManager)getSystemService(WINDOW_SERVICE);AppPrefs.defaults(this);prefs=getSharedPreferences("edgeflow",MODE_PRIVATE);prefs.registerOnSharedPreferenceChangeListener(this);getContentResolver().registerContentObserver(Settings.Secure.getUriFor(Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES),false,authorization);}
    private static boolean isEnabledByUser(Context c){
        // ACCESSIBILITY_ENABLED describes bound runtime state and can fall to zero
        // after the last service crashes; the component list is the user's consent.
        String enabled=Settings.Secure.getString(c.getContentResolver(),Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES);if(enabled==null)return false;
        ComponentName own=new ComponentName(c,GestureService.class);for(String entry:enabled.split(":"))if(own.equals(ComponentName.unflattenFromString(entry)))return true;return false;
    }
    @Override public int onStartCommand(Intent intent,int flags,int startId){
        // A sticky start keeps the existing system binding eligible for process recovery.
        // It never grants access and cannot create the accessibility Binder connection.
        if(!isEnabledByUser(this)){stopSelf(startId);return START_NOT_STICKY;}
        notification();Log.i(TAG,"Started gesture host; awaiting/retaining authorized system binding");return START_STICKY;
    }
    @Override protected void onServiceConnected(){
        super.onServiceConnected();running=this;connected=true;
        AccessibilityServiceInfo info=getServiceInfo();if(info!=null){info.eventTypes=AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED|AccessibilityEvent.TYPE_WINDOWS_CHANGED;info.feedbackType=AccessibilityServiceInfo.FEEDBACK_GENERIC;info.flags|=AccessibilityServiceInfo.FLAG_RETRIEVE_INTERACTIVE_WINDOWS;info.notificationTimeout=30;setServiceInfo(info);}
        IntentFilter f=new IntentFilter();f.addAction(Intent.ACTION_SCREEN_OFF);f.addAction(Intent.ACTION_SCREEN_ON);f.addAction(Intent.ACTION_USER_PRESENT);f.addAction(getPackageName()+".RELOAD_GESTURES");
        if(!receiverRegistered){registerReceiver(changes,f,Context.RECEIVER_NOT_EXPORTED);receiverRegistered=true;}screenOn=((PowerManager)getSystemService(POWER_SERVICE)).isInteractive();
        notification();rebuild();
        if(isEnabledByUser(this))try{startService(new Intent(this,GestureService.class));}catch(RuntimeException e){Log.w(TAG,"Cannot retain started lifecycle; system binding remains",e);}
        Log.i(TAG,"Accessibility gesture service connected");
        sendBroadcast(new Intent(getPackageName()+".SERVICE_STATE").setPackage(getPackageName()));
    }
    private void notification(){
        try{
            NotificationManager nm=(NotificationManager)getSystemService(NOTIFICATION_SERVICE);String channel="gesture_service";nm.createNotificationChannel(new NotificationChannel(channel,"侧边手势服务",NotificationManager.IMPORTANCE_LOW));
            Intent open=new Intent(Intent.ACTION_MAIN).setClassName(this,"dev.edgeflow.MainActivity").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK|Intent.FLAG_ACTIVITY_CLEAR_TOP);
            PendingIntent pi=PendingIntent.getActivity(this,1,open,PendingIntent.FLAG_UPDATE_CURRENT|PendingIntent.FLAG_IMMUTABLE);
            Notification n=new Notification.Builder(this,channel).setSmallIcon(R.drawable.ic_stat_hand_operation).setContentTitle(getString(R.string.app_name)+" 手势已启用").setContentText("左右侧滑动 · 点此调整手势与工具").setContentIntent(pi).setOngoing(true).setCategory(Notification.CATEGORY_SERVICE).build();
            startForeground(17001,n);
        }catch(Exception e){Log.w(TAG,"Foreground notification unavailable; accessibility binding remains",e);}
    }
    @Override public void onAccessibilityEvent(AccessibilityEvent e){
        if(!connected)return;
        if(e.getEventType()==AccessibilityEvent.TYPE_WINDOWS_CHANGED||e.getEventType()==AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED)
            replica.VolumeOverlay.systemWindowsChanged(this);
        if(e.getEventType()==AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED&&e.getPackageName()!=null){
            String p=e.getPackageName().toString();if(!p.equals(getPackageName())&&!p.equals("com.android.systemui")&&!p.equals("android")){
                boolean app=false;for(AccessibilityWindowInfo w:getWindows())if(w.getId()==e.getWindowId()&&w.getType()==AccessibilityWindowInfo.TYPE_APPLICATION&&(w.isActive()||w.isFocused())){app=true;break;}
                if(app){RecentTaskList.invalidate(this);visibilityPackage=p;if(!p.equals(foreground)){previousForeground=foreground;foreground=p;replica.NativeState.setForeground(p);}}
            }
        }
        refreshWindowState();
        scheduleGeometryRefresh();
        updateVisibility();
    }
    /** Initial service binding need not receive a WINDOW_STATE_CHANGED for the current app. */
    private void refreshWindowState(){
        imeVisible=false;notificationsFocused=false;
        try{for(AccessibilityWindowInfo w:getWindows()){
            if(w.getType()==AccessibilityWindowInfo.TYPE_INPUT_METHOD)imeVisible=true;
            if(!w.isFocused())continue;
            android.view.accessibility.AccessibilityNodeInfo root=w.getRoot();if(root==null)continue;
            try{CharSequence value=root.getPackageName();if(value==null)continue;String pkg=value.toString();
                // SystemUI's permanently visible status bar is not a focused shade.
                if(pkg.equals("com.android.systemui")){notificationsFocused=true;continue;}
                if(w.getType()!=AccessibilityWindowInfo.TYPE_APPLICATION)continue;
                if(!pkg.equals("com.android.systemui")&&!pkg.equals("android")){
                    // Own settings must not inherit the last excluded app's hidden handles.
                    visibilityPackage=pkg;
                    if(!pkg.equals(getPackageName())&&!pkg.equals(foreground)){previousForeground=foreground;foreground=pkg;replica.NativeState.setForeground(pkg);}
                }
            }finally{root.recycle();}
        }}catch(Exception e){Log.w(TAG,"Cannot refresh window state",e);}
        if(prefs.getBoolean("hide_home",false))try{
            android.content.pm.ResolveInfo home=getPackageManager().resolveActivity(new Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME),android.content.pm.PackageManager.MATCH_DEFAULT_ONLY);
            homePackage=home!=null&&home.activityInfo!=null&&!"android".equals(home.activityInfo.packageName)?home.activityInfo.packageName:"";
        }catch(RuntimeException e){homePackage="";Log.w(TAG,"Cannot resolve home application",e);}
    }
    public boolean canStartGesture(){
        if(!connected||!screenOn||!prefs.getBoolean("enabled",true))return false;
        if(((KeyguardManager)getSystemService(KEYGUARD_SERVICE)).isKeyguardLocked())return false;
        if((prefs.getBoolean("hide_landscape",false)||!prefs.getBoolean("landscape_enabled",true))&&getResources().getConfiguration().orientation==Configuration.ORIENTATION_LANDSCAPE)return false;
        if(imeVisible&&prefs.getBoolean("hide_keyboard",prefs.getBoolean("hide_when_keyboard",false)))return false;
        String visible=visibilityPackage.isEmpty()?foreground:visibilityPackage;
        if(notificationsFocused&&prefs.getBoolean("hide_notifications",false))return false;
        if(prefs.getBoolean("hide_home",false)&&!homePackage.isEmpty()&&homePackage.equals(visible))return false;
        return !excluded(visible);
    }
    private boolean excluded(String visible){
        if(prefs.getStringSet("disabled_apps",java.util.Collections.emptySet()).contains(visible))return true;
        for(String pkg:prefs.getString("excluded_packages","").split("[\\s,;]+"))if(!pkg.isEmpty()&&pkg.equals(visible))return true;
        return false;
    }
    private boolean handleEnabled(String id){return prefs.getBoolean(id+"_enabled",!GestureHandleConfig.isExtra(id));}
    boolean canStartGesture(String id){return handleEnabled(id)&&canStartGesture();}
    boolean canStartGesture(String id,String pkg){return canStartGesture(id)&&!excluded(pkg);}
    private EdgeHandleView[] handleViews(){return new EdgeHandleView[]{left,right,extraLeft,extraRight,extra2Left,extra2Right};}
    /** A touch-modal panel can receive the physical edge contact before the handle window. */
    public EdgeHandleView handleAt(float x,float y){
        EdgeHandleView[] handles=handleViews();
        for(int i=handles.length-1;i>=0;i--){
            EdgeHandleView h=handles[i];
            if(h==null||!h.isShown())continue;
            WindowManager.LayoutParams p=(WindowManager.LayoutParams)h.getLayoutParams();
            if(x>=p.x&&x<p.x+p.width&&y>=p.y&&y<p.y+p.height)return h;
        }
        return null;
    }
    int visibleHandleWidth(boolean leftSide){
        int width=0;
        EdgeHandleView[] views=handleViews();
        for(int i=0;i<views.length;i++){
            EdgeHandleView handle=views[i];
            if(handle!=null&&handle.isShown()&&GestureHandleConfig.isLeft(GestureHandleConfig.IDS[i])==leftSide)
                width=Math.max(width,handle.getLayoutParams().width);
        }
        return width;
    }
    private void updateVisibility(){
        boolean show=canStartGesture(),wasVisible=handlesVisible;handlesVisible=show;EdgeHandleView[] handles=handleViews();
        for(int i=0;i<handles.length;i++){EdgeHandleView handle=handles[i];if(handle==null)continue;int visibility=show&&handleEnabled(GestureHandleConfig.IDS[i])?View.VISIBLE:View.GONE;
            if(handle.getVisibility()!=visibility){if(visibility!=View.VISIBLE)handle.cancel();handle.setVisibility(visibility);}}
        if(!show&&wasVisible)cancelGesture();
    }
    private float number(String key,float fallback){try{return prefs.getFloat(key,fallback);}catch(ClassCastException e){try{return prefs.getInt(key,(int)fallback);}catch(ClassCastException ignored){return fallback;}}}
    private void rebuild(){
        if(!connected)return;removeGestureWindows();
        try{
            handleArea=HandleDisplayArea.current(this);screenWidth=handleArea.displayWidth;screenHeight=handleArea.displayHeight;screenDensity=getResources().getDisplayMetrics().density;refreshWindowState();
            // The vendor fraud detector considers window bounds, not transparent pixels.
            // Two fixed narrow surfaces keep opaque wave color without a fullscreen overlay.
            int waveWidth=Math.min(screenWidth/2,Math.round(135f*getResources().getDisplayMetrics().density/3.5f+8*getResources().getDisplayMetrics().density));
            leftWave=new GestureWaveView(this);rightWave=new GestureWaveView(this);
            leftWave.setOnApplyWindowInsetsListener((v,insets)->{scheduleGeometryRefresh();return insets;});
            WindowManager.LayoutParams lp=parameters(waveWidth,screenHeight,handleArea.left,0);lp.flags|=WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE;lp.setTitle(getString(R.string.app_name)+" 手势绘制 L");windows.addView(leftWave,lp);
            WindowManager.LayoutParams rp=parameters(waveWidth,screenHeight,handleArea.right-waveWidth,0);rp.flags|=WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE;rp.setTitle(getString(R.string.app_name)+" 手势绘制 R");windows.addView(rightWave,rp);
            // Keep all six narrow input windows, hiding disabled segments before add.
            // Stable add order preserves extra2 > extra > primary overlap priority.
            handlesVisible=canStartGesture();left=addHandle("left");right=addHandle("right");
            extraLeft=addHandle("extra_left");extraRight=addHandle("extra_right");
            extra2Left=addHandle("extra2_left");extra2Right=addHandle("extra2_right");
            updateVisibility();
            int dpi=getResources().getDisplayMetrics().densityDpi;
            NavigationOverlay.displayChanged(handleArea,dpi);TouchpadOverlay.displayChanged(handleArea,dpi);MagnificationOverlay.displayChanged(handleArea);
            updateHandlePreviews();
        }catch(Exception e){Log.e(TAG,"Cannot attach gesture surfaces",e);removeWindows();}
    }
    private int[] handleBounds(String id){
        boolean extra=GestureHandleConfig.isExtra(id);float density=getResources().getDisplayMetrics().density;
        float width=number(id+"_handle_width_dp",extra?12.3f:number("handle_width_dp",12.3f));
        float start=number(id+"_handle_start_fraction",extra?GestureHandleConfig.defaultStart(id):number("handle_start_fraction",.13084841f)),end=number(id+"_handle_end_fraction",extra?GestureHandleConfig.defaultEnd(id):number("handle_end_fraction",1f));
        if(!extra&&(prefs.contains("handle_center_fraction")||prefs.contains("handle_height_fraction"))){float center=number("handle_center_fraction",.5654242f),height=number("handle_height_fraction",.8691516f);start=center-height/2;end=center+height/2;}
        return GestureHandleConfig.bounds(id,width,start,end,density,handleArea);
    }
    private EdgeHandleView addHandle(String id){
        int[] b=handleBounds(id);EdgeHandleView handle=new EdgeHandleView(this,id);handle.setVisibility(handlesVisible&&handleEnabled(id)?View.VISIBLE:View.GONE);
        WindowManager.LayoutParams p=parameters(b[2],b[3],b[0],b[1]);p.setTitle(getString(R.string.app_name)+" "+(id.startsWith("extra2_")?"额外第二组":GestureHandleConfig.isExtra(id)?"额外":"")+(GestureHandleConfig.isLeft(id)?"左":"右")+"手势区域");windows.addView(handle,p);return handle;
    }
    private void updateHandleLayout(EdgeHandleView handle,String id){
        int[] b=handleBounds(id);WindowManager.LayoutParams old=(WindowManager.LayoutParams)handle.getLayoutParams();
        if(old.x==b[0]&&old.y==b[1]&&old.width==b[2]&&old.height==b[3])return;
        handle.cancel();WindowManager.LayoutParams next=new WindowManager.LayoutParams();next.copyFrom(old);next.x=b[0];next.y=b[1];next.width=b[2];next.height=b[3];
        try{windows.updateViewLayout(handle,next);}catch(RuntimeException e){Log.w(TAG,"Cannot update handle geometry "+id,e);}
    }
    private void updateWaveLayout(GestureWaveView view,boolean isLeft){
        if(view==null||handleArea==null)return;WindowManager.LayoutParams old=(WindowManager.LayoutParams)view.getLayoutParams();int x=isLeft?handleArea.left:handleArea.right-old.width;if(old.x==x)return;
        view.clear();WindowManager.LayoutParams next=new WindowManager.LayoutParams();next.copyFrom(old);next.x=x;try{windows.updateViewLayout(view,next);}catch(RuntimeException error){Log.w(TAG,"Cannot align wave with safe edge",error);}
    }
    private WindowManager.LayoutParams parameters(int width,int height,int x,int y){
        WindowManager.LayoutParams p=new WindowManager.LayoutParams(width,height,WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE|WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL|WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,PixelFormat.TRANSLUCENT);p.gravity=Gravity.TOP|Gravity.LEFT;p.x=x;p.y=y;p.alpha=1;p.setFitInsetsTypes(0);p.layoutInDisplayCutoutMode=WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_ALWAYS;p.windowAnimations=0;return p;
    }
    void beginWave(boolean isLeft,float y){if(wave!=null)wave.clear();wave=isLeft?leftWave:rightWave;if(wave!=null)wave.begin(isLeft,y);}
    void updateWave(float inward,float vertical,int direction,String handleId,boolean longer,String application){
        if(wave!=null){wave.setActionHint(GestureMapping.resolve(prefs,application,handleId,direction<0?"up":direction>0?"down":"straight",longer));wave.update(inward,vertical,direction);}
    }
    void releaseWave(){if(wave!=null)wave.release();}
    String gesturePackage(){
        try{for(AccessibilityWindowInfo w:getWindows())if(w.isFocused()&&w.getType()==AccessibilityWindowInfo.TYPE_APPLICATION){
            android.view.accessibility.AccessibilityNodeInfo root=w.getRoot();if(root==null)return "";
            try{return root.getPackageName()==null?"":root.getPackageName().toString();}finally{root.recycle();}
        }}catch(RuntimeException unavailable){Log.w(TAG,"Cannot resolve gesture window",unavailable);}
        return "";
    }
    void dispatchGesture(String handleId,String direction,boolean longer,float[] anchor,String application){
        if(!canStartGesture(handleId,application)||!application.equals(gesturePackage()))return;
        String action=GestureMapping.resolve(prefs,application,handleId,direction,longer);String key=GestureHandleConfig.actionKey(handleId,direction,longer);Log.i(TAG,"gesture="+key+" action="+action+" anchor="+java.util.Arrays.toString(anchor));
        try{ActionExecutor.execute(this,action,anchor.clone());}catch(Exception e){Log.e(TAG,"Action failed: "+action,e);}
    }
    private void cancelGesture(){for(EdgeHandleView handle:handleViews())if(handle!=null)handle.cancel();if(wave!=null)wave.clear();}
    static void enterTrial(){GestureService service=running;if(service!=null)service.cancelGesture();}
    private void removeGestureWindows(){cancelGesture();remove(left);remove(right);remove(extraLeft);remove(extraRight);remove(extra2Left);remove(extra2Right);remove(leftWave);remove(rightWave);left=right=extraLeft=extraRight=extra2Left=extra2Right=null;wave=leftWave=rightWave=null;handlesVisible=false;}
    private void removeWindows(){removeGestureWindows();removeHandlePreviews();}
    private void remove(View v){if(v!=null)try{windows.removeViewImmediate(v);}catch(IllegalArgumentException ignored){}}
    @Override public void onSharedPreferenceChanged(SharedPreferences p,String key){
        if("enabled".equals(key)&&!p.getBoolean("enabled",true)){dismissPanels();}
        schedulePreferences(key==null||key.equals("hide_home")||key.equals("hide_notifications")||key.equals("hide_keyboard")||key.equals("hide_when_keyboard")||key.equals("disabled_apps")||key.equals("excluded_packages"));
    }
    @Override public void onConfigurationChanged(Configuration c){super.onConfigurationChanged(c);cancelGesture();Point size=new Point();windows.getDefaultDisplay().getRealSize(size);if(size.x!=screenWidth||size.y!=screenHeight||screenDensity!=getResources().getDisplayMetrics().density)dismissTransientPanels();replica.WidgetOverlay.configurationChanged();pendingConfiguration=true;schedulePreferences(true);}
    @Override public void onInterrupt(){cancelGesture();removeHandlePreviews();}
    @Override public void onTaskRemoved(Intent rootIntent){super.onTaskRemoved(rootIntent);Log.i(TAG,"Settings task removed; accessibility binding retained");}
    private void dismissTransientPanels(){replica.ToolsOverlay.dismissAll();replica.VolumeOverlay.dismissAll();QuickLauncherOverlay.dismissAll();TaskSwitcherOverlay.dismissAll();ScreenMoveOverlay.dismissAll();}
    private void dismissPanels(){dismissTransientPanels();TouchpadOverlay.dismissAll();NavigationOverlay.dismissAll();MagnificationOverlay.dismissAll();replica.WidgetOverlay.dismissAll();}
    private void disconnect(){
        RecentTaskList.release(this);
        connected=false;if(running==this)running=null;main.removeCallbacksAndMessages(null);pendingConfiguration=pendingWindowState=pendingGeometry=false;
        if(receiverRegistered){try{unregisterReceiver(changes);}catch(IllegalArgumentException ignored){}receiverRegistered=false;}
        removeWindows();dismissPanels();ActionExecutor.release(this);stopForeground(STOP_FOREGROUND_REMOVE);
        sendBroadcast(new Intent(getPackageName()+".SERVICE_STATE").setPackage(getPackageName()));
    }
    @Override public boolean onUnbind(Intent intent){disconnect();stopSelf();return super.onUnbind(intent);}
    @Override public void onDestroy(){disconnect();getContentResolver().unregisterContentObserver(authorization);if(prefs!=null)prefs.unregisterOnSharedPreferenceChangeListener(this);super.onDestroy();}
}
