package replica;

import android.app.Activity;
import android.content.*;
import android.content.res.*;
import android.database.ContentObserver;
import android.graphics.Color;
import android.graphics.Typeface;
import android.os.Handler;
import android.os.Looper;
import android.provider.Settings;
import android.util.TypedValue;
import android.view.View;
import android.widget.TextView;
import java.lang.ref.WeakReference;
import java.util.*;

/** Live ColorOS Settings tokens. Reads resource tables, never another app's code. */
public final class ColorOsTheme {
 private static final Handler MAIN=new Handler(Looper.getMainLooper());
 private static final ArrayList<WeakReference<Subscription>> listeners=new ArrayList<>();
 private static Context application;private static Palette cached;private static int configurationKey;private static boolean initialized;
 private static final HashMap<Integer,Typeface> fonts=new HashMap<>();
 private static final Runnable changed=()->{
  synchronized(ColorOsTheme.class){cached=null;fonts.clear();}
  ArrayList<Subscription> live=new ArrayList<>();
  synchronized(listeners){Iterator<WeakReference<Subscription>> it=listeners.iterator();while(it.hasNext()){Subscription s=it.next().get();if(s==null||s.closed)it.remove();else live.add(s);}}
  for(Subscription s:live)if(!s.closed)try{s.action.run();}catch(RuntimeException e){android.util.Log.w("ColorOsTheme","Theme listener",e);}
 };
 private static void invalidate(){MAIN.removeCallbacks(changed);MAIN.postDelayed(changed,64);}
 private static final ContentObserver observer=new ContentObserver(MAIN){@Override public void onChange(boolean self){invalidate();}};
 private static final BroadcastReceiver configuration=new BroadcastReceiver(){@Override public void onReceive(Context c,Intent i){invalidate();}};
 public static final class Subscription implements AutoCloseable {
  private Runnable action;private boolean closed;
  private Subscription(Runnable action){this.action=action;}
  @Override public void close(){closed=true;action=()->{};synchronized(listeners){listeners.removeIf(w->w.get()==null||w.get()==this);}}
 }
 public static Subscription observe(Context c,Runnable action){initialize(c);Subscription s=new Subscription(action);synchronized(listeners){listeners.add(new WeakReference<>(s));}return s;}
 public static void watch(View view,Runnable action){
  final Subscription[] hold={null};View.OnAttachStateChangeListener l=new View.OnAttachStateChangeListener(){
   public void onViewAttachedToWindow(View v){if(hold[0]==null)hold[0]=observe(v.getContext(),action);}
   public void onViewDetachedFromWindow(View v){if(hold[0]!=null){hold[0].close();hold[0]=null;}}
  };view.addOnAttachStateChangeListener(l);if(view.isAttachedToWindow())l.onViewAttachedToWindow(view);
 }
 public static synchronized void initialize(Context c){
  if(initialized)return;application=c.getApplicationContext();if(application==null)application=c;
  for(String k:new String[]{"material_color_value","font_variation_settings","font_scale","system_material_stroke_enable","system_material_blur_enable","super_powersave_mode_state","color_theme_setting","custom_theme_path_setting"})try{application.getContentResolver().registerContentObserver(Settings.System.getUriFor(k),false,observer);}catch(RuntimeException ignored){}
  for(String k:new String[]{"sysui_type_accent_color","theme_customization_overlay_packages","global_theme_enabled"})try{application.getContentResolver().registerContentObserver(Settings.Secure.getUriFor(k),false,observer);}catch(RuntimeException ignored){}
  for(String k:new String[]{"low_power","disable_window_blurs","animator_duration_scale","transition_animation_scale"})try{application.getContentResolver().registerContentObserver(Settings.Global.getUriFor(k),false,observer);}catch(RuntimeException ignored){}
  try{IntentFilter f=new IntentFilter(Intent.ACTION_CONFIGURATION_CHANGED);f.addAction(Intent.ACTION_WALLPAPER_CHANGED);f.addAction(android.os.PowerManager.ACTION_POWER_SAVE_MODE_CHANGED);application.registerReceiver(configuration,f,Context.RECEIVER_NOT_EXPORTED);}catch(RuntimeException ignored){}
  initialized=true;
 }
 private static final class Palette {int accent,text,secondary,tertiary,background,card,popup,error,fill,disabled;boolean dark,stroke,blur;float animations;long material;String source="fallback",skin="not-checked";}
 private static int configKey(Context c){Configuration q=c.getResources().getConfiguration();return Objects.hash(q.uiMode,q.densityDpi,q.fontScale,q.fontWeightAdjustment,q.getLocales());}
 private static synchronized Palette palette(Context c){initialize(c);int key=configKey(c);if(cached==null||key!=configurationKey){configurationKey=key;cached=load(c);}return cached;}
 private static Palette load(Context c){
  Palette p=new Palette();p.dark=(c.getResources().getConfiguration().uiMode&Configuration.UI_MODE_NIGHT_MASK)==Configuration.UI_MODE_NIGHT_YES;
  p.accent=p.dark?0xff1a8cff:0xff0080ff;p.text=p.dark?0xe6ffffff:0xff000000;p.secondary=p.dark?0x99ffffff:0x99000000;p.tertiary=p.dark?0x66ffffff:0x66000000;p.background=p.dark?Color.BLACK:0xfff0f1f2;p.card=p.dark?0xff1e1e1e:Color.WHITE;p.popup=p.dark?0xff333333:Color.WHITE;p.error=p.dark?0xffff6c61:0xffdb382c;p.fill=p.dark?0x26ffffff:0x14000000;p.disabled=p.dark?0x4dffffff:0x26000000;
  p.material=systemLong(c,"material_color_value",0);boolean low=globalInt(c,"low_power",0)==1||systemInt(c,"super_powersave_mode_state",0)==1;
  p.stroke=systemInt(c,"system_material_stroke_enable",0)==1&&!low;p.blur=systemInt(c,"system_material_blur_enable",0)==1&&globalInt(c,"disable_window_blurs",0)==0&&!low;p.animations=Math.max(0,globalFloat(c,"animator_duration_scale",1));
  try{
   Context foreign=c.createPackageContext("com.android.settings",0).createConfigurationContext(new Configuration(c.getResources().getConfiguration()));
   Resources r=foreign.getResources();Resources.Theme theme=r.newTheme();String pkg="com.android.settings";
   int style=r.getIdentifier(p.dark?"AppBaseDarkTheme":"AppBaseTheme","style",pkg);if(style==0)throw new Resources.NotFoundException("Settings theme unavailable");theme.applyStyle(style,true);if(!rejectColorOverlay(foreign,p))applyColorOverlay(r,theme,p.material,pkg);
   p.accent=attr(r,theme,pkg,"couiColorPrimary",p.accent);p.text=attr(r,theme,pkg,"couiColorLabelPrimary",p.text);p.secondary=attr(r,theme,pkg,"couiColorLabelSecondary",p.secondary);p.tertiary=attr(r,theme,pkg,"couiColorLabelTertiary",p.tertiary);
   p.background=attr(r,theme,pkg,"couiColorBackgroundWithCard",p.background);p.card=attr(r,theme,pkg,"couiColorSurface",p.card);p.popup=attr(r,theme,pkg,"couiColorBackgroundTop",p.popup);p.error=attr(r,theme,pkg,"couiColorError",p.error);p.fill=attr(r,theme,pkg,"couiColorFillQuaternary",p.fill);p.disabled=attr(r,theme,pkg,"couiColorDisable",p.disabled);p.source="com.android.settings:"+(p.dark?"AppBaseDarkTheme":"AppBaseTheme");
   int blur=r.getIdentifier("coui_blur_enable","bool",pkg),immersive=r.getIdentifier("is_immersive_theme","bool",pkg);if((blur!=0&&!r.getBoolean(blur))||(immersive!=0&&r.getBoolean(immersive)))p.blur=p.stroke=false;
  }catch(Exception unavailable){try{String value=Settings.Secure.getString(c.getContentResolver(),"sysui_type_accent_color");if(value!=null&&!value.isEmpty())p.accent=Color.parseColor(value);}catch(RuntimeException ignored){}}
  return p;
 }
 /** COUIThemeOverlay.isRejectTheme: an installed daytime Settings skin owns its colors. */
 private static boolean rejectColorOverlay(Context settings,Palette p){
  if(p.dark){p.skin="night-mode";return false;}
  Configuration config=settings.getResources().getConfiguration();Object extra=null;long flags;
  try{
   Class<?> base=Class.forName("android.content.res.OplusBaseConfiguration");
   org.lsposed.hiddenapibypass.HiddenApiBypass.addHiddenApiExemptions("Landroid/content/res/OplusBaseConfiguration;","Loplus/content/res/OplusExtraConfiguration;","Lcom/oplus/inner/content/res/ConfigurationWrapper;");
   if(base.isInstance(config))extra=base.getField("mOplusExtraConfiguration").get(config);
   if(extra!=null)flags=extra.getClass().getField("mThemeChangedFlags").getLong(extra);
   else flags=((Number)Class.forName("com.oplus.inner.content.res.ConfigurationWrapper").getMethod("getThemeChangedFlags",Configuration.class).invoke(null,config)).longValue();
   if(flags<0)throw new ReflectiveOperationException("Theme flags unavailable");
  }catch(ReflectiveOperationException|RuntimeException|LinkageError unavailable){p.skin="unverified-flags";return false;}
  if((flags&1)==0){p.skin="inactive";return false;}
  try{
   String pkg=settings.getPackageName();boolean installed;
   if((flags&256)!=0){
    java.nio.file.Path company=java.nio.file.Paths.get("/my_company/media/theme");
    if(!themePathExists(company))installed=false;
    else if(themePathExists(company.resolve(pkg)))installed=true;
    else{
     boolean hasFiles;try(java.nio.file.DirectoryStream<java.nio.file.Path> files=java.nio.file.Files.newDirectoryStream(company)){hasFiles=files.iterator().hasNext();}
     String path=hasFiles?Settings.System.getString(settings.getContentResolver(),"custom_theme_path_setting"):null;
     installed=path!=null&&!path.isEmpty()&&themePathExists(java.nio.file.Paths.get(path).resolve(pkg));
    }
   }else{
    int user=extra==null?0:extra.getClass().getField("mUserId").getInt(extra);
    java.nio.file.Path data=java.nio.file.Paths.get("/data/theme");if(user>0)data=data.resolve(Integer.toString(user));installed=themePathExists(data.resolve(pkg));
   }
   p.skin=installed?"settings-package-skin":"no-settings-package-skin";return installed;
  }catch(java.io.IOException|ReflectiveOperationException|RuntimeException unavailable){p.skin="unverified-package-access";return false;}
 }
 private static boolean themePathExists(java.nio.file.Path path)throws java.io.IOException{
  try{java.nio.file.Files.readAttributes(path,java.nio.file.attribute.BasicFileAttributes.class);return true;}
  catch(java.nio.file.NoSuchFileException absent){return false;}
 }
 /** Current COUIThemeOverlay selection. The overlay arrays remain system-owned. */
 private static void applyColorOverlay(Resources r,Resources.Theme t,long material,String pkg){
  if(material==0)return;int index=(int)(material&65535),kind=(int)(material&16711680);if(index==0&&kind==0)return;
  if(kind==131072){int id=r.getIdentifier("COUIOverlay.Theme.Single.First","style",pkg);if(id!=0)t.applyStyle(id,true);return;}
  int array=0;TypedValue identity=new TypedValue();int attr=r.getIdentifier("couiThemeIdentifier","attr",pkg);int themeIndex=attr!=0&&t.resolveAttribute(attr,identity,true)?identity.data-1:0;
  if(kind==65536)array=r.getIdentifier("coui_theme_arrays_single_patch_p","array",pkg);
  else if(kind==262144){array=r.getIdentifier("coui_theme_arrays_default_patch","array",pkg);index=themeIndex;}
  else if(kind==0||kind==1048576){int all=r.getIdentifier("coui_theme_arrays_ids","array",pkg);if(all!=0&&index>0){TypedArray a=r.obtainTypedArray(all);try{if(index<=a.length())array=a.getResourceId(index-1,0);}finally{a.recycle();}}index=themeIndex;}
  if(array!=0&&index>=0){TypedArray a=r.obtainTypedArray(array);try{if(index<a.length()){int id=a.getResourceId(index,0);if(id!=0)t.applyStyle(id,true);}}finally{a.recycle();}}
 }
 private static int attr(Resources r,Resources.Theme t,String pkg,String name,int fallback){int id=r.getIdentifier(name,"attr",pkg);TypedValue value=new TypedValue();if(id!=0&&t.resolveAttribute(id,value,true)){if(value.type>=TypedValue.TYPE_FIRST_COLOR_INT&&value.type<=TypedValue.TYPE_LAST_COLOR_INT)return value.data;if(value.resourceId!=0)try{return r.getColorStateList(value.resourceId,t).getDefaultColor();}catch(Exception ignored){}}return fallback;}
 private static int systemInt(Context c,String key,int fallback){try{return Settings.System.getInt(c.getContentResolver(),key,fallback);}catch(RuntimeException e){return fallback;}}
 private static long systemLong(Context c,String key,long fallback){try{return Settings.System.getLong(c.getContentResolver(),key,fallback);}catch(RuntimeException e){return fallback;}}
 private static int globalInt(Context c,String key,int fallback){try{return Settings.Global.getInt(c.getContentResolver(),key,fallback);}catch(RuntimeException e){return fallback;}}
 private static float globalFloat(Context c,String key,float fallback){try{return Settings.Global.getFloat(c.getContentResolver(),key,fallback);}catch(RuntimeException e){return fallback;}}
 public static int accent(Context c){return palette(c).accent;} public static int text(Context c){return palette(c).text;}
 public static int secondary(Context c){return palette(c).secondary;} public static int tertiary(Context c){return palette(c).tertiary;}
 public static int background(Context c){return palette(c).background;} public static int card(Context c){return palette(c).card;}
 public static int popup(Context c){return palette(c).popup;} public static int error(Context c){return palette(c).error;}
 public static int fill(Context c){return palette(c).fill;} public static int disabled(Context c){return palette(c).disabled;}
 public static boolean dark(Context c){return palette(c).dark;} public static boolean materialStroke(Context c){return palette(c).stroke;}
 public static boolean materialBlur(Context c){return palette(c).blur;} public static float animationScale(Context c){return palette(c).animations;}
 public static Typeface typeface(Context c,int weight){
  palette(c);int adjusted=c.getResources().getConfiguration().fontWeightAdjustment;if(adjusted==Integer.MAX_VALUE)adjusted=0;int target=Math.max(100,Math.min(900,weight+adjusted));
  synchronized(fonts){Typeface font=fonts.get(target);if(font==null){font=Typeface.create(Typeface.DEFAULT,target,false);fonts.put(target,font);}return font;}
 }
 public static void text(TextView view,int weight){view.setTypeface(typeface(view.getContext(),weight));}
 public static void install(Activity a){initialize(a);a.getWindow().getDecorView();a.getWindow().setStatusBarColor(0);a.getWindow().setNavigationBarColor(0);a.getWindow().setNavigationBarContrastEnforced(false);android.view.WindowInsetsController c=a.getWindow().getInsetsController();if(c!=null)c.setSystemBarsAppearance(dark(a)?0:24,24);}
 public static org.json.JSONObject describe(Context c){Palette p=palette(c);org.json.JSONObject out=new org.json.JSONObject();try{out.put("source",p.source).put("accent",String.format("#%08X",p.accent)).put("text",String.format("#%08X",p.text)).put("secondary",String.format("#%08X",p.secondary)).put("background",String.format("#%08X",p.background)).put("card",String.format("#%08X",p.card)).put("dark",p.dark).put("material",p.material).put("skin",p.skin).put("stroke",p.stroke).put("blur",p.blur).put("animationScale",p.animations).put("fontWeightAdjustment",c.getResources().getConfiguration().fontWeightAdjustment).put("fontVariation",systemInt(c,"font_variation_settings",0));}catch(org.json.JSONException ignored){}return out;}
 private ColorOsTheme(){}
}

