package dev.edgeflow;

import android.accessibilityservice.AccessibilityService;
import android.app.AlertDialog;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.pm.ResolveInfo;
import android.hardware.camera2.CameraCharacteristics;
import android.hardware.camera2.CameraManager;
import android.media.AudioManager;
import android.os.Handler;
import android.os.Looper;
import android.provider.Settings;
import android.view.ContextThemeWrapper;
import android.view.KeyEvent;
import android.view.WindowManager;
import android.view.accessibility.AccessibilityNodeInfo;
import android.widget.Toast;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

/** Independently authored action layer. No original APK classes or action numbers. */
public final class ActionExecutor {
 public static final String[] IDS={"none","back","home","recents","previous_app","notifications","quick_settings","screen_off","screenshot","volume_up","volume_down","volume_mute","quick_tools","widgets","one_hand","sidebar","power_menu","split_screen","assistant","search","menu","media_previous","media_play_pause","media_next","scroll_up","scroll_down","browser_forward","close_app","flashlight","rotation","rotate_right","rotate_left","wifi","bluetooth","mobile_data","airplane","sound_mode","dnd","nfc","dark_mode","auto_brightness","screen_timeout","screen_record","settings","app_launcher","keyboard","color_inversion","battery_saver","location","hotspot_settings","eye_comfort_settings","accessibility_settings","quick_launcher","task_switcher","virtual_touchpad","navigation_panel","keyboard_shortcuts","system_magnification","screen_magnifier","screen_free_move"};
 public static final String[] TITLES={"无","返回","主屏幕","最近任务","上一个应用","通知栏","系统快捷面板","锁屏","截屏","音量增加","音量减小","媒体静音","快速工具","小组件","单手模式","系统侧边栏","电源菜单","分屏","语音助手","搜索","菜单键","上一首","播放 / 暂停","下一首","向上滚动","向下滚动","浏览器前进","关闭当前应用","手电筒","自动旋转","向右旋转","向左旋转","Wi-Fi","蓝牙","移动数据","飞行模式","声音模式","勿扰模式","NFC","深色模式","自动亮度","屏幕超时","录屏","系统设置","应用启动器","切换输入法","颜色反转","省电模式","定位","热点设置","护眼设置","无障碍设置","快速启动","任务切换器","虚拟触控板","虚拟导航键","键盘快捷方式","系统屏幕放大","屏幕放大镜","屏幕自由移动"};
 private static final Handler MAIN=new Handler(Looper.getMainLooper());
 private static final ExecutorService WORK=Executors.newSingleThreadExecutor();
 private static CameraManager torchManager;private static CameraManager.TorchCallback torchCallback;private static String torchId;private static boolean torchOn,torchReady,torchPending;private static Runnable torchTimeout;
 private static final ArrayList<AlertDialog> DIALOGS=new ArrayList<>();private static int dialogGeneration,lifecycleGeneration;
 public static String title(String id){
  for(int i=0;i<IDS.length;i++)if(IDS[i].equals(id))return TITLES[i];
  if(LauncherLayout.isFolderAction(id))return "快速启动文件夹";
  if(id!=null&&id.startsWith("app:"))return "启动应用";
  if(id!=null&&(id.startsWith(ShortcutEntry.PREFIX)||id.startsWith("shortcut:")))return "应用快捷方式";
  return "未配置的动作";
 }
 public static String[] ids(){return IDS.clone();}public static String[] titles(){return TITLES.clone();}
 public static String title(Context context,String id){
  if(LauncherLayout.isFolderAction(id)){
   LauncherLayout.Folder f=LauncherLayout.load(context.getSharedPreferences("edgeflow_launcher",0)).folders.get(id.substring(7));
   return f==null?"文件夹已删除":"文件夹 · "+f.name;
  }
  if(ShortcutEntry.valid(id))return ShortcutPicker.label(context,id);
  if(id!=null&&id.startsWith("app:")){
   ComponentName component=ComponentName.unflattenFromString(id.substring(4));if(component==null)return "应用不可用";
   try{return context.getPackageManager().getActivityInfo(component,0).loadLabel(context.getPackageManager()).toString();}
   catch(android.content.pm.PackageManager.NameNotFoundException unavailable){return "应用不可用";}
  }
  return title(id);
 }
 public static void execute(AccessibilityService c,String id,float[] anchor){
  if(c==null||id==null||id.equals("none"))return;
  float[] points=anchor==null?replica.NativeState.defaultGesture(c):anchor.clone();
  if(Looper.myLooper()!=Looper.getMainLooper()){MAIN.post(()->execute(c,id,points));return;}
  try{
   // The focused window must still exist when Android delivers Back.
   if(id.equals("back")){
    if(!replica.WidgetOverlay.handleBack()&&!replica.ToolsOverlay.handleBack())
     global(c,AccessibilityService.GLOBAL_ACTION_BACK,"input keyevent 4");
    return;
   }
   boolean folder=LauncherLayout.isFolderAction(id);
   preparePanel(folder?"quick_launcher":id);
   if(!id.equals("quick_launcher"))QuickLauncherOverlay.dismissAll();
   if(!id.equals("task_switcher"))TaskSwitcherOverlay.dismissAll();
   if(!id.equals("keyboard_shortcuts"))KeyboardShortcutsOverlay.dismissAll();
   dismissDialogs();
   if(folder){QuickLauncherOverlay.showFolder(c,points,id.substring(7));return;}
   if(id.startsWith(ShortcutEntry.PREFIX)){ShortcutPicker.start(c,id);return;}
   if(id.startsWith("app:")){launchApp(c,id.substring(4));return;}
   if(id.startsWith("shortcut:")){Intent in=Intent.parseUri(id.substring(9),Intent.URI_INTENT_SCHEME);in.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);start(c,in);return;}
   AudioManager audio=(AudioManager)c.getSystemService(Context.AUDIO_SERVICE);
   switch(id){
    case "home":global(c,AccessibilityService.GLOBAL_ACTION_HOME,"input keyevent 3");break;
    case "recents":global(c,AccessibilityService.GLOBAL_ACTION_RECENTS,"input keyevent 187");break;
    case "previous_app":root(c,"input keyevent 187; sleep 0.15; input keyevent 187",title(id));break;
    case "notifications":global(c,AccessibilityService.GLOBAL_ACTION_NOTIFICATIONS,"cmd statusbar expand-notifications");break;
    case "quick_settings":global(c,AccessibilityService.GLOBAL_ACTION_QUICK_SETTINGS,"cmd statusbar expand-settings");break;
    case "screen_off":global(c,AccessibilityService.GLOBAL_ACTION_LOCK_SCREEN,"input keyevent 223");break;
    case "screenshot":int generation=lifecycleGeneration;MAIN.postDelayed(()->{if(generation==lifecycleGeneration)global(c,AccessibilityService.GLOBAL_ACTION_TAKE_SCREENSHOT,"input keyevent 120");},180);break;
    case "volume_up":case "volume_down":audio.adjustStreamVolume(AudioManager.STREAM_MUSIC,id.equals("volume_up")?1:-1,AudioManager.FLAG_SHOW_UI);replica.VolumeOverlay.show(c,points);break;
    case "volume_mute":audio.adjustStreamVolume(AudioManager.STREAM_MUSIC,AudioManager.ADJUST_TOGGLE_MUTE,AudioManager.FLAG_SHOW_UI);break;
    case "quick_tools":replica.ToolsOverlay.show(c,points);break;
    case "quick_launcher":QuickLauncherOverlay.show(c,points);break;
    case "task_switcher":TaskSwitcherOverlay.show(c,points);break;
    case "virtual_touchpad":TouchpadOverlay.show(c,points);break;
    case "navigation_panel":NavigationOverlay.show(c,points);break;
    case "keyboard_shortcuts":KeyboardShortcutsOverlay.show(c,points);break;
    case "system_magnification":magnify(c,points);break;
    case "screen_magnifier":MagnificationOverlay.show(c,points);break;
    case "screen_free_move":ScreenMoveOverlay.show(c,points);break;
    case "widgets":replica.WidgetOverlay.show(c,points);break;
    case "one_hand":root(c,bridge(c,"onehand 1"),title(id));break;
    case "sidebar":root(c,"content call --uri content://com.coloros.sidebar --method OVOICE_OPEN_FLOATBAR_ACTION",title(id));break;
    case "power_menu":if(!c.performGlobalAction(AccessibilityService.GLOBAL_ACTION_POWER_DIALOG))message(c,"系统未允许打开电源菜单");break;
    case "split_screen":if(!c.performGlobalAction(AccessibilityService.GLOBAL_ACTION_TOGGLE_SPLIT_SCREEN))message(c,"当前系统或应用不支持分屏动作");break;
    case "assistant":start(c,new Intent(Intent.ACTION_VOICE_COMMAND));break;
    case "search":root(c,"input keyevent 84",title(id));break;
    case "menu":root(c,"input keyevent 82",title(id));break;
    case "media_previous":dispatchMediaKey(audio,KeyEvent.KEYCODE_MEDIA_PREVIOUS);break;
    case "media_play_pause":dispatchMediaKey(audio,KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE);break;
    case "media_next":dispatchMediaKey(audio,KeyEvent.KEYCODE_MEDIA_NEXT);break;
    case "scroll_up":scroll(c,false);break;case "scroll_down":scroll(c,true);break;
    case "browser_forward":root(c,"input keyevent 125",title(id));break;
    case "close_app":closeCurrentApp(c, ForegroundAppCloser.target(c));break;
    case "flashlight":torch(c);break;
    case "rotation":toggle(c,"system","accelerometer_rotation",title(id));break;
    case "rotate_right":rotate(c,1);break;case "rotate_left":rotate(c,3);break;
    case "wifi":root(c,"if [ \"$(settings get global wifi_on)\" = 1 ]; then svc wifi disable; else svc wifi enable; fi",title(id));break;
    case "bluetooth":root(c,"if [ \"$(settings get global bluetooth_on)\" = 1 ]; then svc bluetooth disable; else svc bluetooth enable; fi",title(id));break;
    case "mobile_data":root(c,"if [ \"$(settings get global mobile_data)\" = 1 ]; then svc data disable; else svc data enable; fi",title(id));break;
    case "airplane":root(c,"if [ \"$(settings get global airplane_mode_on)\" = 1 ]; then cmd connectivity airplane-mode disable; else cmd connectivity airplane-mode enable; fi",title(id));break;
    case "sound_mode":root(c,bridge(c,"ringer "+((audio.getRingerMode()+1)%3)),title(id));break;
    case "dnd":root(c,"if [ \"$(settings get global zen_mode)\" = 0 ]; then cmd notification set_dnd priority; else cmd notification set_dnd all; fi",title(id));break;
    case "nfc":android.nfc.NfcAdapter nfc=android.nfc.NfcAdapter.getDefaultAdapter(c);if(nfc==null)message(c,"此设备不支持 NFC");else root(c,nfc.isEnabled()?"cmd nfc disable-nfc persist":"cmd nfc enable-nfc",title(id));break;
    case "dark_mode":root(c,"cmd uimode night "+(((c.getResources().getConfiguration().uiMode&48)==32)?"no":"yes"),title(id));break;
    case "auto_brightness":toggle(c,"system","screen_brightness_mode",title(id));break;
    case "screen_timeout":timeout(c);break;
    case "screen_record":root(c,"am start -n com.oplus.screenrecorder/com.oplus.screenrecorder.MainActivity --es recorder_entrance SystemUIScreenAssistant",title(id));break;
    case "settings":start(c,new Intent(Settings.ACTION_SETTINGS));break;
    case "app_launcher":apps(c);break;
    case "keyboard":((android.view.inputmethod.InputMethodManager)c.getSystemService(Context.INPUT_METHOD_SERVICE)).showInputMethodPicker();break;
    case "color_inversion":toggle(c,"secure","accessibility_display_inversion_enabled",title(id));break;
    case "battery_saver":root(c,"if [ \"$(settings get global low_power)\" = 1 ]; then cmd power set-mode 0; else cmd power set-mode 1; fi",title(id));break;
    case "location":root(c,"if [ \"$(cmd location is-location-enabled)\" = true ]; then cmd location set-location-enabled false; else cmd location set-location-enabled true; fi",title(id));break;
    case "hotspot_settings":start(c,new Intent("android.settings.TETHER_SETTINGS"));break;
    case "eye_comfort_settings":start(c,new Intent(Settings.ACTION_NIGHT_DISPLAY_SETTINGS));break;
    case "accessibility_settings":start(c,new Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS));break;
    default:message(c,"此动作尚未适配当前系统："+id);
   }
  }catch(Exception e){android.util.Log.e("EdgeActions",id,e);message(c,title(id)+"暂不可用："+e.getClass().getSimpleName());}
 }
 private static void preparePanel(String id){switch(id){case "quick_tools":case "widgets":case "quick_launcher":case "task_switcher":case "virtual_touchpad":case "navigation_panel":case "keyboard_shortcuts":case "app_launcher":case "screen_timeout":case "volume_up":case "volume_down":case "sidebar":case "screen_magnifier":case "screen_free_move":
   replica.ToolsOverlay.dismissAll();if(!id.equals("volume_up")&&!id.equals("volume_down"))replica.VolumeOverlay.close();QuickLauncherOverlay.dismissAll();TaskSwitcherOverlay.dismissAll();KeyboardShortcutsOverlay.dismissAll();ScreenMoveOverlay.dismissAll();ShortcutPicker.dismissAll();if(!id.equals("widgets")){replica.WidgetOverlay.dismissAll();replica.WidgetActivity.dismissAll();}break;default:break;}}
 private static void magnify(AccessibilityService service, float[] anchor) {
  AccessibilityService.MagnificationController controller = service.getMagnificationController();
  android.accessibilityservice.MagnificationConfig current = controller.getMagnificationConfig();
  boolean accepted;
  if (MagnificationCompat.active(current) && current.getScale() > 1.01f) {
   accepted = controller.reset(true);
  } else {
   android.graphics.Point screen = replica.PanelGeometry.screen(service);
   float x = Math.max(0, Math.min(screen.x, anchor[2]));
   float y = Math.max(0, Math.min(screen.y, anchor[3]));
   accepted = controller.setMagnificationConfig(MagnificationCompat.fullscreen(2f, x, y), true);
  }
  if (!accepted) message(service, "系统未允许屏幕放大，请检查无障碍服务状态");
 }
 private static void global(AccessibilityService c,int id,String fallback){if(!c.performGlobalAction(id))root(c,fallback,"系统动作");}
 public static void dispatchMediaKey(AudioManager audio,int key){audio.dispatchMediaKeyEvent(new KeyEvent(KeyEvent.ACTION_DOWN,key));audio.dispatchMediaKeyEvent(new KeyEvent(KeyEvent.ACTION_UP,key));}
 private static void rotate(Context c,int add){int current=Settings.System.getInt(c.getContentResolver(),Settings.System.USER_ROTATION,0);root(c,"settings put system accelerometer_rotation 0; settings put system user_rotation "+((current+add)%4),"屏幕旋转");}
 private static void toggle(Context c,String ns,String key,String label){root(c,"if [ \"$(settings get "+ns+" "+key+")\" = 1 ]; then settings put "+ns+" "+key+" 0; else settings put "+ns+" "+key+" 1; fi",label);}
 private static String quote(String s){return "'"+s.replace("'","'\\''")+"'";}
 private static String bridge(Context c,String args){return "CLASSPATH="+quote(c.getApplicationInfo().sourceDir)+" app_process /system/bin replica.OplusBridge "+args;}
 public static void root(Context c,String command,String label){Context app=c.getApplicationContext();WORK.execute(()->{
  Process p=null;
  try{p=new ProcessBuilder("su","-c",command).redirectErrorStream(true).start();final Process process=p;ByteArrayOutputStream out=new ByteArrayOutputStream();Thread reader=new Thread(()->{try(InputStream in=process.getInputStream()){byte[] b=new byte[1024];int n;while((n=in.read(b))!=-1)if(out.size()<32768)out.write(b,0,Math.min(n,32768-out.size()));}catch(Exception ignored){}},"action-result");reader.start();
   if(!p.waitFor(15,TimeUnit.SECONDS)){p.destroyForcibly();message(app,label+"执行超时，请检查 Root 授权");return;}reader.join(500);String result=out.toString("UTF-8");if(commandFailed(p.exitValue(),result)){android.util.Log.w("EdgeActions",label+": "+result);message(app,label+"执行失败，请检查系统支持及 Root 授权");}
  }catch(Exception e){android.util.Log.e("EdgeActions",label,e);message(app,label+"需要可用的 Root 授权");}finally{if(p!=null){p.destroy();try{p.getInputStream().close();}catch(Exception ignored){}}}
 });}
 static boolean commandFailed(int exit,String output){if(exit!=0)return true;String value=output.toLowerCase(java.util.Locale.ROOT);if(value.contains("exception:")||value.contains("exception in thread")||value.contains("permission denial")||value.contains("permission denied")||value.contains("unknown command"))return true;for(String line:value.split("\\r?\\n")){String s=line.trim();if(s.startsWith("error:")||s.startsWith("error type ")||s.startsWith("failure [")||s.startsWith("/system/bin/sh:")||s.startsWith("sh:"))return true;}return false;}
 private static void start(Context c,Intent i){i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);try{c.startActivity(i);}catch(Exception e){message(c,"当前系统没有可处理此操作的应用");android.util.Log.w("EdgeActions","Launch unavailable",e);}}
 private static void launchApp(Context c,String value){ComponentName component=ComponentName.unflattenFromString(value);Intent i=component!=null?new Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER).setComponent(component):c.getPackageManager().getLaunchIntentForPackage(value);if(i==null){message(c,"找不到所选应用");return;}start(c,i);}
 public static void closeCurrentApp(AccessibilityService service, String expected) {
  if (service == null) return;
  if (expected == null) { message(service, "没有可关闭的前台应用"); return; }
  WORK.execute(() -> {
   try {
    if (!ForegroundAppCloser.close(service, expected)) message(service, "前台应用已变化");
   } catch (InterruptedException interrupted) {
    Thread.currentThread().interrupt();
   } catch (Exception failure) {
    android.util.Log.w("EdgeActions", "Could not confirm or stop focused app", failure);
    message(service, "关闭失败，请检查前台应用和 Root 授权");
   }
  });
 }
 private static void scroll(AccessibilityService c,boolean forward){AccessibilityNodeInfo node=c.getRootInActiveWindow();try{if(node==null||!scrollNode(node,forward))message(c,"当前页面没有可滚动区域");}finally{if(node!=null)node.recycle();}}
 private static boolean scrollNode(AccessibilityNodeInfo n,boolean forward){if(n==null)return false;if(n.isScrollable()&&n.performAction(forward?AccessibilityNodeInfo.ACTION_SCROLL_FORWARD:AccessibilityNodeInfo.ACTION_SCROLL_BACKWARD))return true;for(int i=0;i<n.getChildCount();i++){AccessibilityNodeInfo child=n.getChild(i);try{if(scrollNode(child,forward))return true;}finally{if(child!=null)child.recycle();}}return false;}
 private static void torch(Context c)throws Exception{
  Context app=c.getApplicationContext();
  if(torchManager==null){
   CameraManager manager=(CameraManager)app.getSystemService(Context.CAMERA_SERVICE);String selected=null;
   for(String id:manager.getCameraIdList()){CameraCharacteristics info=manager.getCameraCharacteristics(id);if(Boolean.TRUE.equals(info.get(CameraCharacteristics.FLASH_INFO_AVAILABLE))){selected=id;if(Integer.valueOf(CameraCharacteristics.LENS_FACING_BACK).equals(info.get(CameraCharacteristics.LENS_FACING)))break;}}
   if(selected==null){message(app,"此设备没有可用闪光灯");return;}torchId=selected;torchReady=false;torchPending=false;torchManager=manager;
   torchCallback=new CameraManager.TorchCallback(){public void onTorchModeChanged(String id,boolean on){if(!id.equals(torchId))return;torchOn=on;torchReady=true;if(torchPending){torchPending=false;cancelTorchTimeout();applyTorch(app);}}public void onTorchModeUnavailable(String id){if(!id.equals(torchId))return;torchReady=false;if(torchPending){torchPending=false;cancelTorchTimeout();message(app,"闪光灯正被相机占用，暂不可用");}}};
   try{manager.registerTorchCallback(torchCallback,MAIN);}catch(Exception e){releaseTorch();throw e;}
  }
  if(torchReady)applyTorch(app);else{torchPending=!torchPending;cancelTorchTimeout();if(torchPending){torchTimeout=()->{torchPending=false;torchTimeout=null;message(app,"未能获取闪光灯状态，请稍后重试");};MAIN.postDelayed(torchTimeout,2500);}}
 }
 private static void applyTorch(Context app){try{boolean next=!torchOn;torchManager.setTorchMode(torchId,next);torchOn=next;}catch(Exception e){android.util.Log.w("EdgeActions","torch",e);message(app,"闪光灯暂不可用，可能被相机占用");}}
 private static void cancelTorchTimeout(){if(torchTimeout!=null)MAIN.removeCallbacks(torchTimeout);torchTimeout=null;}
 private static void releaseTorch(){cancelTorchTimeout();if(torchManager!=null&&torchCallback!=null)try{torchManager.unregisterTorchCallback(torchCallback);}catch(Exception ignored){}torchManager=null;torchCallback=null;torchId=null;torchReady=false;torchPending=false;}
 private static AlertDialog.Builder dialog(Context c,String title){boolean dark=(c.getResources().getConfiguration().uiMode&48)==32;int theme=c.getResources().getIdentifier(dark?"ReplicaGlassDark":"ReplicaGlass","style",c.getPackageName());return new replica.GlassDialogBuilder(new ContextThemeWrapper(c,theme)).setTitle(title);}
 private static void show(AlertDialog d){d.getWindow().setType(WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY);d.getWindow().addFlags(WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE);d.setOnDismissListener(x->DIALOGS.remove(d));DIALOGS.add(d);try{d.show();}catch(RuntimeException e){DIALOGS.remove(d);throw e;}}
 private static void dismissDialogs(){dialogGeneration++;for(AlertDialog d:new ArrayList<>(DIALOGS))try{replica.ColorDialogBuilder.dismissImmediately(d);}catch(Exception ignored){}DIALOGS.clear();}
 /** Called when the accessibility service disconnects; never turns off a user-owned torch. */
 public static void release(AccessibilityService service){if(Looper.myLooper()!=Looper.getMainLooper()){MAIN.post(()->release(service));return;}lifecycleGeneration++;dismissDialogs();KeyboardShortcutsOverlay.dismissAll();QuickLauncherOverlay.dismissAll();TaskSwitcherOverlay.dismissAll();MagnificationOverlay.dismissAll();ScreenMoveOverlay.dismissAll();ShortcutPicker.dismissAll();releaseTorch();}
 private static void timeout(Context c){
  int[] seconds={15,30,60,120,300,600};String[] names={"15 秒","30 秒","1 分钟","2 分钟","5 分钟","10 分钟"};
  int current=Settings.System.getInt(c.getContentResolver(),Settings.System.SCREEN_OFF_TIMEOUT,30000)/1000,selected=-1;
  for(int i=0;i<seconds.length;i++)if(seconds[i]==current)selected=i;
  AlertDialog choices=dialog(c,"屏幕超时").setSingleChoiceItems(names,selected,(d,n)->{root(c,"settings put system screen_off_timeout "+(seconds[n]*1000),"屏幕超时");d.dismiss();}).setNegativeButton("取消",null).create();
  // This is the standalone action. The child chooser in quick tools stays modal.
  choices.getWindow().addFlags(WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL|WindowManager.LayoutParams.FLAG_WATCH_OUTSIDE_TOUCH);
  show(choices);
 }
 /** Compatibility for already assigned legacy launchers; the removed option stays out of new menus. */
 private static void apps(Context c){int generation=dialogGeneration;WORK.execute(()->{
  try{
   List<ResolveInfo> queried=c.getPackageManager().queryIntentActivities(new Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER),0);ArrayList<ResolveInfo> valid=new ArrayList<>();
   for(ResolveInfo item:queried)if(item.activityInfo!=null&&item.activityInfo.exported&&item.activityInfo.enabled)valid.add(item);
   java.text.Collator collator=java.text.Collator.getInstance(java.util.Locale.CHINA);valid.sort((a,b)->collator.compare(a.loadLabel(c.getPackageManager()).toString(),b.loadLabel(c.getPackageManager()).toString()));
   ArrayList<String> labels=new ArrayList<>();ArrayList<android.graphics.drawable.Drawable> icons=new ArrayList<>();
   for(ResolveInfo item:valid){labels.add(item.loadLabel(c.getPackageManager()).toString());icons.add(item.loadIcon(c.getPackageManager()));}
   MAIN.post(()->{if(generation!=dialogGeneration)return;if(valid.isEmpty()){message(c,"没有可启动的应用");return;}
    android.widget.BaseAdapter adapter=new android.widget.BaseAdapter(){public int getCount(){return valid.size();}public Object getItem(int p){return valid.get(p);}public long getItemId(int p){return p;}
     public android.view.View getView(int p,android.view.View old,android.view.ViewGroup parent){android.widget.LinearLayout row=new android.widget.LinearLayout(c);row.setGravity(android.view.Gravity.CENTER_VERTICAL);int iconSize=replica.PanelStyle.dp(c,40),inset=replica.PanelStyle.dp(c,24);row.setPadding(inset,replica.PanelStyle.dp(c,12),inset,replica.PanelStyle.dp(c,12));row.setMinimumHeight(replica.PanelStyle.dp(c,72));
      android.widget.ImageView icon=new android.widget.ImageView(c);icon.setImageDrawable(icons.get(p));icon.setScaleType(android.widget.ImageView.ScaleType.FIT_CENTER);icon.setImportantForAccessibility(android.view.View.IMPORTANT_FOR_ACCESSIBILITY_NO);android.widget.LinearLayout.LayoutParams ip=new android.widget.LinearLayout.LayoutParams(iconSize,iconSize);ip.setMarginEnd(replica.PanelStyle.dp(c,16));row.addView(icon,ip);
      android.widget.TextView name=replica.ColorDialogBuilder.listLabel(c,labels.get(p));name.setPadding(0,0,0,0);name.setMinHeight(0);name.setMaxLines(2);name.setEllipsize(android.text.TextUtils.TruncateAt.END);row.addView(name,new android.widget.LinearLayout.LayoutParams(0,-2,1));return row;}
    };
    try{show(dialog(c,"应用启动器").setAdapter(adapter,(d,n)->{ResolveInfo item=valid.get(n);launchApp(c,new ComponentName(item.activityInfo.packageName,item.activityInfo.name).flattenToString());}).setNegativeButton("取消",null).create());}catch(RuntimeException error){android.util.Log.w("EdgeActions","app chooser",error);message(c,"应用启动器暂不可用");}
   });
  }catch(Exception failure){android.util.Log.w("EdgeActions","query apps",failure);message(c,"未能读取已安装应用");}
 });}
 private static void message(Context c,String text){MAIN.post(()->Toast.makeText(c,text,Toast.LENGTH_LONG).show());}
 private ActionExecutor(){}
}
