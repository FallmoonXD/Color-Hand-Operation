package dev.edgeflow;

import android.accessibilityservice.AccessibilityService;
import android.app.Activity;
import android.app.AlertDialog;
import android.content.Context;
import android.content.ContextWrapper;
import android.content.SharedPreferences;
import android.content.pm.ApplicationInfo;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.os.Handler;
import android.os.Looper;
import android.view.ContextThemeWrapper;
import android.view.Gravity;
import android.view.View;
import android.view.WindowManager;
import android.view.accessibility.AccessibilityNodeInfo;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.text.Collator;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;
import org.json.*;

/** Publisher-grouped system shortcuts, embeddable in settings or shown in an existing picker window. */
public final class ShortcutPicker {
 public interface Callback {void onSelected(String actionId);default void onClosed(){}}
 private static final Handler MAIN=new Handler(Looper.getMainLooper());
 private static final ExecutorService WORK=Executors.newSingleThreadExecutor();
 private static Session current;
 private static void watchNow(View view,Runnable refresh){refresh.run();replica.ColorOsTheme.watch(view,refresh);}
 public static Session embedded(Activity owner,String selected,Callback callback){return new Session(owner,selected,callback,true);}
 public static Session show(Context context,Callback callback){return show(context,"",callback);}
 public static Session show(Context context,String selected,Callback callback){dismissAll();Session s=new Session(context,selected,callback,false);current=s;if(s.showWindow())s.start();return s;}
 public static void dismissAll(){if(current!=null)current.close();}
 public static String label(Context c,String key){try{return new JSONObject(prefs(c).getString("shortcut_labels","{}")).optString(key,"应用快捷方式");}catch(Exception e){return "应用快捷方式";}}
 public static Drawable icon(Context c,String key){try{Drawable cached=Drawable.createFromPath(iconFile(c,key).getAbsolutePath());if(cached!=null)return cached;return c.getPackageManager().getApplicationIcon(ShortcutEntry.parse(key).packageName);}catch(Exception e){return c.getPackageManager().getDefaultActivityIcon();}}
 public static void start(Context context,String key){ShortcutEntry.parse(key);ActionExecutor.root(context,command(context,"start",key),label(context,key));}
 private static SharedPreferences prefs(Context c){return c.getSharedPreferences("edgeflow_launcher",Context.MODE_PRIVATE);}
 private static String shell(String value){return "'"+value.replace("'","'\\''")+"'";}
 private static String command(Context c,String operation,String argument){return "CLASSPATH="+shell(c.getApplicationInfo().sourceDir)+" app_process /system/bin dev.edgeflow.ShortcutBridge "+operation+" "+shell(argument);}
 private static File iconFile(Context c,String key)throws Exception{byte[] hash=MessageDigest.getInstance("SHA-256").digest(key.getBytes(StandardCharsets.UTF_8));StringBuilder name=new StringBuilder();for(byte b:hash)name.append(String.format(Locale.ROOT,"%02x",b&255));return new File(new File(c.getCacheDir(),"shortcut-icons"),name+".png");}
 private static void remember(Context c,String key,String title){try{SharedPreferences p=prefs(c);JSONObject labels=new JSONObject(p.getString("shortcut_labels","{}"));if(labels.length()<1000||labels.has(key)){labels.put(key,title.length()>256?title.substring(0,256):title);p.edit().putString("shortcut_labels",labels.toString()).apply();}}catch(Exception e){android.util.Log.w("EdgeShortcuts","label cache",e);}}
 private static void cacheIcon(Context c,JSONObject item,ShortcutEntry entry){try{
  byte[] bytes=null;String png=item.optString("icon");
  if(!png.isEmpty()&&png.length()<=32768)bytes=Base64.getDecoder().decode(png);
  else if(item.optInt("iconRes")>0){
   Drawable drawable=c.getPackageManager().getResourcesForApplication(entry.packageName).getDrawable(item.getInt("iconRes"),null);android.graphics.Bitmap bitmap=android.graphics.Bitmap.createBitmap(72,72,android.graphics.Bitmap.Config.ARGB_8888);
   try{drawable.setBounds(0,0,72,72);drawable.draw(new Canvas(bitmap));ByteArrayOutputStream out=new ByteArrayOutputStream();bitmap.compress(android.graphics.Bitmap.CompressFormat.PNG,100,out);if(out.size()<=24576)bytes=out.toByteArray();}finally{bitmap.recycle();}
  }
  if(bytes!=null){File file=iconFile(c,item.getString("key"));file.getParentFile().mkdirs();android.util.AtomicFile atomic=new android.util.AtomicFile(file);FileOutputStream out=null;try{out=atomic.startWrite();out.write(bytes);atomic.finishWrite(out);}catch(IOException error){atomic.failWrite(out);throw error;}}
 }catch(Exception e){android.util.Log.d("EdgeShortcuts","Using publisher app icon",e);}}
 static void validateLabels(Object value)throws JSONException{if(!(value instanceof String)||((String)value).length()>1048576)throw new JSONException("Invalid shortcut label cache");JSONObject labels=new JSONObject((String)value);if(labels.length()>1000)throw new JSONException("Too many shortcut labels");Iterator<String> keys=labels.keys();while(keys.hasNext()){String key=keys.next();Object title=labels.get(key);if(!ShortcutEntry.valid(key)||!(title instanceof String)||((String)title).length()>256)throw new JSONException("Invalid shortcut label");}}
 /** Retained for old callers/tests; null package validates a complete current-user catalog. */
 static JSONArray parseResult(int exit,String output,int expectedUser,String expectedPackage)throws IOException,JSONException{return ShortcutCatalog.parse(exit,output,expectedUser,expectedPackage);}

 public static final class Session {
  private final Context context,themed;private final Callback callback;private final Activity owner;private final boolean overlay,embedded;private final String selected;
  private final LinearLayout content;private final ScrollView scroll;
  private List<AppGroup> loadedGroups;
  private String query="";
  private final Map<String,Boolean> openGroups=new HashMap<>();
  private Runnable firstResults;
  public android.os.Bundle expandedGroups(){
   android.os.Bundle result=new android.os.Bundle();openGroups.forEach(result::putBoolean);return result;
  }
  public void restoreGroups(android.os.Bundle state,Runnable afterLayout){
   for(String key:state.keySet())openGroups.put(key,state.getBoolean(key));firstResults=afterLayout;
  }
  public void search(String value){
   query=value.trim().toLowerCase(Locale.ROOT);
   if(loadedGroups!=null&&alive())render(loadedGroups);
  }
  private replica.ColorActionSheet sheet;private AlertDialog dialog;private volatile Process process;private Future<?> work;
  private final HashMap<View,android.animation.ValueAnimator> expansions=new HashMap<>();
  private volatile boolean closed;private volatile int generation;private boolean started,selecting,selectionCancelled;
  Session(Context c,String chosen,Callback cb,boolean embedded){
   context=c;callback=cb;this.embedded=embedded;selected=ShortcutEntry.valid(chosen)?chosen:"";
   Context base=c;while(base instanceof ContextWrapper&&!(base instanceof Activity)&&!(base instanceof AccessibilityService)){Context next=((ContextWrapper)base).getBaseContext();if(next==base)break;base=next;}
   owner=base instanceof Activity?(Activity)base:null;overlay=base instanceof AccessibilityService;if(embedded&&owner==null)throw new IllegalArgumentException("Embedded shortcuts require an Activity");
   boolean dark=(c.getResources().getConfiguration().uiMode&48)==32;int theme=c.getResources().getIdentifier(dark?"ReplicaGlassDark":"ReplicaGlass","style",c.getPackageName());themed=new ContextThemeWrapper(c,theme);
   content=new LinearLayout(c){@Override protected void onDetachedFromWindow(){super.onDetachedFromWindow();if(Session.this.embedded&&started&&!closed)Session.this.close();}};content.setOrientation(LinearLayout.VERTICAL);
   content.setContentDescription("应用快捷方式分组列表");
   if(embedded)scroll=null;else{scroll=new replica.ColorSettingsScrollView(c);scroll.setFillViewport(false);scroll.addView(content,new ScrollView.LayoutParams(-1,-2));}
  }
  /** Bare content; the host owns scrolling, page transitions and lifecycle. */
  public View view(){return content;}
  /** Idempotent initial load. A visible retry is the only operation that loads again. */
  public void start(){if(started||closed)return;started=true;load();}
  private int dp(float value){return Math.round(value*context.getResources().getDisplayMetrics().density);}
  private boolean ownerAvailable(){return owner==null||!owner.isFinishing()&&!owner.isDestroyed();}
  private boolean alive(){return !closed&&ownerAvailable();}
  private boolean active(int token){return !closed&&generation==token&&!Thread.currentThread().isInterrupted();}
  private boolean showWindow(){
   if(embedded)return alive();if(!alive()){close();return false;}if(sheet!=null||dialog!=null)return true;
   try{
    LinearLayout picker=new LinearLayout(themed);picker.setOrientation(LinearLayout.VERTICAL);picker.setFocusableInTouchMode(true);
    picker.addView(new replica.SearchField(themed,"搜索应用或快捷方式","",this::search),new LinearLayout.LayoutParams(-1,-2));
    picker.addView(scroll,new LinearLayout.LayoutParams(-1,-2));
    if(owner!=null){
     replica.ColorActionSheet next=new replica.ColorActionSheet(owner).setTitle("应用快捷方式").setView(picker).setNegativeButton("取消",null);
     sheet=next;next.setOnDismissListener(()->{if(sheet==next){sheet=null;finishClose();}});next.show();
    }else{
     AlertDialog next=(overlay?new replica.GlassDialogBuilder(themed):new replica.ColorDialogBuilder(context)).setTitle("应用快捷方式").setView(picker).setNegativeButton("取消",null).create();
     dialog=next;if(overlay){next.getWindow().setType(WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY);next.getWindow().clearFlags(WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE|WindowManager.LayoutParams.FLAG_ALT_FOCUSABLE_IM);}
     next.setOnDismissListener(d->{if(dialog==next){dialog=null;finishClose();}});next.show();
    }
    return alive();
   }catch(RuntimeException failure){close();Toast.makeText(context,"无法显示快捷方式选择器",Toast.LENGTH_SHORT).show();return false;}
  }
  private void refresh(){if(sheet!=null)sheet.refreshContent();content.requestLayout();if(scroll!=null)scroll.requestLayout();}
  private TextView text(String value,int size,int role,int weight){
   TextView label=new TextView(context);label.setText(value);label.setTextSize(size);label.setLineSpacing(0,1.1f);
   watchNow(label,()->{label.setTypeface(replica.ColorOsTheme.typeface(context,weight));label.setTextColor(role==1?replica.ColorOsTheme.secondary(context):role==2?replica.ColorOsTheme.accent(context):role==3?replica.ColorOsTheme.error(context):replica.ColorOsTheme.text(context));});return label;
  }
  private void feedback(String message,boolean error,boolean retry){
   content.removeAllViews();TextView label=text(message,15,error?3:1,400);label.setPadding(dp(16),dp(22),dp(16),dp(22));label.setAccessibilityLiveRegion(View.ACCESSIBILITY_LIVE_REGION_POLITE);content.addView(label);
   if(retry){TextView button=text("重新读取",16,2,500);button.setPadding(dp(16),dp(14),dp(16),dp(14));button.setMinHeight(dp(56));button.setFocusable(true);touch(button);button.setOnClickListener(v->{if(alive()&&!selecting)load();});content.addView(button);}refresh();
  }
  private void touch(View row){watchNow(row,()->row.setBackground(replica.ColorDialogBuilder.touchBackground(context,Color.TRANSPARENT,16)));}
  private void stopWork(){Future<?> task=work;work=null;if(task!=null)task.cancel(true);Process p=process;process=null;if(p!=null)p.destroyForcibly();}
  private void load(){
   if(!alive()||selecting)return;loadedGroups=null;int token=++generation;stopWork();feedback("正在读取应用快捷方式…",false,false);
   final int user=android.os.Process.myUid()/100000;
   work=WORK.submit(()->{
    ArrayList<AppGroup> groups=new ArrayList<>();String error=null;Process running=null;
    try{
     if(!active(token))return;running=new ProcessBuilder("su","-c",command(context,"list-all",Integer.toString(user))).redirectErrorStream(true).start();process=running;if(!active(token))throw new InterruptedException();
     String raw=readResult(running,35);JSONArray result=parseResult(running.exitValue(),raw,user,null);
     for(int i=0;i<result.length();i++){if(!active(token))throw new InterruptedException();JSONObject item=result.getJSONObject(i);cacheIcon(context,item,ShortcutEntry.parse(item.getString("key")));}
     for(ShortcutCatalog.Group group:ShortcutCatalog.group(result)){
      if(!active(token))throw new InterruptedException();String title=group.packageName;Drawable icon;
      try{ApplicationInfo app=context.getPackageManager().getApplicationInfo(group.packageName,0);CharSequence name=app.loadLabel(context.getPackageManager());if(name!=null)title=name.toString();icon=app.loadIcon(context.getPackageManager());}catch(Exception unavailable){icon=context.getPackageManager().getDefaultActivityIcon();}
      groups.add(new AppGroup(group,title,icon));
     }
     Collator order=Collator.getInstance();groups.sort((a,b)->{int compared=order.compare(a.title,b.title);return compared!=0?compared:a.data.packageName.compareTo(b.data.packageName);});
    }catch(InterruptedException cancelled){Thread.currentThread().interrupt();return;}
    catch(Exception failure){if(active(token))android.util.Log.w("EdgeShortcuts","catalog",failure);error=failure.getMessage();if(error==null||error.isEmpty())error="读取快捷方式失败";}
    finally{if(process==running)process=null;if(running!=null){running.destroy();try{running.getInputStream().close();}catch(IOException ignored){}}}
    String failure=error;MAIN.post(()->{if(!active(token)||!alive())return;if(failure!=null){feedback(failure,true,true);return;}render(groups);});
   });
  }
  private String readResult(Process running,int seconds)throws Exception{
   ByteArrayOutputStream output=new ByteArrayOutputStream();AtomicBoolean overflow=new AtomicBoolean();Thread reader=new Thread(()->{
    try(InputStream in=running.getInputStream()){byte[] bytes=new byte[8192];int count;while((count=in.read(bytes))!=-1){synchronized(output){int space=ShortcutCatalog.MAX_OUTPUT-output.size();if(count>space)overflow.set(true);if(space>0)output.write(bytes,0,Math.min(space,count));}}}catch(IOException ignored){}
   },"shortcut-catalog-output");reader.setDaemon(true);reader.start();
   try{
    if(!running.waitFor(seconds,TimeUnit.SECONDS)){running.destroyForcibly();throw new IOException("读取超时，请检查 Root 授权");}reader.join(1500);
    if(reader.isAlive())throw new IOException("快捷方式响应未结束");if(overflow.get())throw new IOException("快捷方式响应过大");
    synchronized(output){return output.toString("UTF-8");}
   }finally{if(reader.isAlive()){try{running.getInputStream().close();}catch(IOException ignored){}reader.interrupt();}}
  }
  private void render(List<AppGroup> groups){
   loadedGroups=groups;
   for(android.animation.ValueAnimator anim:expansions.values()){anim.removeAllListeners();anim.removeAllUpdateListeners();anim.cancel();}
   expansions.clear();content.removeAllViews();
   if(groups.isEmpty()){feedback("当前没有应用向系统提供快捷方式。打开应用或创建桌面快捷方式后，可重新读取。",false,true);return;}
   if(!selected.isEmpty()){
    boolean found=false;for(AppGroup group:groups)if(group.data.selected(selected)!=null){found=true;break;}
    if(!found){TextView unavailable=text("已选快捷方式暂不可用，请重新选择。",14,1,400);unavailable.setPadding(dp(16),dp(8),dp(16),dp(14));content.addView(unavailable);}
   }
   int matched=0;
   for(AppGroup group:groups){
    boolean publisher=query.isEmpty()||group.title.toLowerCase(Locale.ROOT).contains(query)||group.data.packageName.toLowerCase(Locale.ROOT).contains(query);
    List<ShortcutCatalog.Entry> items=group.data.entries;
    if(!publisher){
     ArrayList<ShortcutCatalog.Entry> found=new ArrayList<>();
     for(ShortcutCatalog.Entry item:items)if(item.label.toLowerCase(Locale.ROOT).contains(query))found.add(item);
     if(found.isEmpty())continue;items=found;
    }
    addGroup(group,items);matched++;
   }
   if(matched==0)content.addView(text("没有匹配的快捷方式",15,1,400));
   refresh();
   if(firstResults!=null){Runnable after=firstResults;firstResults=null;content.post(()->{if(alive())after.run();});}
  }
  private void addGroup(AppGroup group,List<ShortcutCatalog.Entry> items){
   LinearLayout card=new LinearLayout(context);card.setOrientation(LinearLayout.VERTICAL);card.setClipToOutline(true);
   watchNow(card,()->{GradientDrawable background=new GradientDrawable();background.setColor(replica.ColorOsTheme.card(context));background.setCornerRadius(dp(20));card.setBackground(background);});
   LinearLayout.LayoutParams cardParams=new LinearLayout.LayoutParams(-1,-2);cardParams.bottomMargin=dp(12);content.addView(card,cardParams);
   LinearLayout header=new LinearLayout(context);header.setBaselineAligned(false);header.setGravity(Gravity.CENTER_VERTICAL);header.setPadding(dp(16),dp(12),dp(8),dp(12));header.setMinimumHeight(dp(72));header.setFocusable(true);touch(header);
   ImageView image=new ImageView(context);image.setImageDrawable(group.icon);image.setScaleType(ImageView.ScaleType.FIT_CENTER);image.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);LinearLayout.LayoutParams iconParams=new LinearLayout.LayoutParams(dp(40),dp(40));iconParams.setMarginEnd(dp(14));header.addView(image,iconParams);
   LinearLayout labels=new LinearLayout(context);labels.setOrientation(LinearLayout.VERTICAL);labels.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO_HIDE_DESCENDANTS);labels.addView(text(group.title,16,0,500));
   ShortcutCatalog.Entry selectedEntry=group.data.selected(selected);String summary=selectedEntry==null?group.data.entries.size()+" 个快捷方式":"已选："+selectedEntry.label+(selectedEntry.enabled?"":"（已停用）");
   TextView subtitle=text(summary,13,selectedEntry==null?1:2,400);subtitle.setPadding(0,dp(4),0,0);labels.addView(subtitle);header.addView(labels,new LinearLayout.LayoutParams(0,-2,1));
   Chevron arrow=new Chevron(context);arrow.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);header.addView(arrow,new LinearLayout.LayoutParams(dp(44),dp(44)));card.addView(header);
   LinearLayout children=new LinearLayout(context);children.setOrientation(LinearLayout.VERTICAL);children.setPadding(dp(12),0,dp(12),dp(8));card.addView(children);
   final boolean[] expanded={!query.isEmpty()||openGroups.getOrDefault(group.data.packageName,selectedEntry!=null)},populated={false},animate={false};
   Runnable bind=()->{
    if(query.isEmpty())openGroups.put(group.data.packageName,expanded[0]);
    if(expanded[0]&&!populated[0]){for(ShortcutCatalog.Entry item:items)itemRow(children,item);populated[0]=true;}
    expansion(children,arrow,expanded[0],animate[0]);animate[0]=false;
    header.setContentDescription((expanded[0]?"收起":"展开")+group.title);header.setStateDescription(summary);
   };
   header.setAccessibilityDelegate(new View.AccessibilityDelegate(){@Override public void onInitializeAccessibilityNodeInfo(View host,AccessibilityNodeInfo info){super.onInitializeAccessibilityNodeInfo(host,info);info.setClassName("android.widget.Button");info.addAction(expanded[0]?AccessibilityNodeInfo.AccessibilityAction.ACTION_COLLAPSE:AccessibilityNodeInfo.AccessibilityAction.ACTION_EXPAND);}@Override public boolean performAccessibilityAction(View host,int action,android.os.Bundle args){if(action==AccessibilityNodeInfo.AccessibilityAction.ACTION_EXPAND.getId()||action==AccessibilityNodeInfo.AccessibilityAction.ACTION_COLLAPSE.getId()){if(!alive()||selecting)return false;expanded[0]=action==AccessibilityNodeInfo.AccessibilityAction.ACTION_EXPAND.getId();animate[0]=true;bind.run();refresh();return true;}return super.performAccessibilityAction(host,action,args);}});
   header.setOnClickListener(v->{if(!alive()||selecting)return;expanded[0]=!expanded[0];animate[0]=true;bind.run();refresh();});bind.run();
  }
  private void expansion(LinearLayout body,Chevron arrow,boolean expanded,boolean animate){
   android.animation.ValueAnimator old=expansions.remove(body);if(old!=null){old.removeAllUpdateListeners();old.removeAllListeners();old.cancel();}
   float targetRotation=expanded?-180:0;android.view.ViewGroup.LayoutParams params=body.getLayoutParams();
   int measuredWidth=body.getWidth();if(measuredWidth==0&&body.getParent() instanceof View){View parent=(View)body.getParent();measuredWidth=Math.max(0,parent.getWidth()-parent.getPaddingLeft()-parent.getPaddingRight());}
   if(!animate||!embedded||measuredWidth==0||replica.ColorOsTheme.animationScale(context)==0||!android.animation.ValueAnimator.areAnimatorsEnabled()){
    params.height=-2;body.setLayoutParams(params);body.setVisibility(expanded?View.VISIBLE:View.GONE);body.setAlpha(1);arrow.setRotation(targetRotation);return;
   }
   int initial=body.getVisibility()==View.VISIBLE?body.getHeight():0;float initialAlpha=initial==0?0:body.getAlpha(),initialRotation=arrow.getRotation();
   body.measure(View.MeasureSpec.makeMeasureSpec(measuredWidth,View.MeasureSpec.EXACTLY),View.MeasureSpec.makeMeasureSpec(0,View.MeasureSpec.UNSPECIFIED));int target=expanded?body.getMeasuredHeight():0;
   body.setVisibility(View.VISIBLE);params.height=initial;body.setLayoutParams(params);body.setAlpha(initialAlpha);
   android.animation.ValueAnimator animator=android.animation.ValueAnimator.ofFloat(0,1);expansions.put(body,animator);
   animator.setDuration(Math.round(replica.ColorMotion.EXPAND_MS*Math.min(1f,Math.abs(target-initial)/(float)Math.max(1,body.getMeasuredHeight()))));
   animator.setInterpolator(replica.ColorMotion.ENTER);
   animator.addUpdateListener(a->{float fraction=(Float)a.getAnimatedValue();params.height=Math.round(initial+(target-initial)*fraction);body.setLayoutParams(params);body.setAlpha(initialAlpha+((expanded?1:0)-initialAlpha)*fraction);arrow.setRotation(initialRotation+(targetRotation-initialRotation)*fraction);});
   animator.addListener(new android.animation.AnimatorListenerAdapter(){@Override public void onAnimationEnd(android.animation.Animator animation){if(expansions.get(body)!=animator)return;expansions.remove(body);params.height=-2;body.setLayoutParams(params);body.setVisibility(expanded?View.VISIBLE:View.GONE);body.setAlpha(1);arrow.setRotation(targetRotation);refresh();}});animator.start();
  }
  private void itemRow(LinearLayout parent,ShortcutCatalog.Entry item){
   boolean checked=item.key.equals(selected);
   LinearLayout row=new LinearLayout(context){@Override public CharSequence getAccessibilityClassName(){return android.widget.RadioButton.class.getName();}@Override public void onInitializeAccessibilityNodeInfo(AccessibilityNodeInfo info){super.onInitializeAccessibilityNodeInfo(info);info.setCheckable(true);info.setChecked(checked);}};
   row.setBaselineAligned(false);row.setGravity(Gravity.CENTER_VERTICAL);row.setPadding(dp(16),dp(12),dp(8),dp(12));row.setMinimumHeight(dp(60));row.setFocusable(true);row.setEnabled(item.enabled);touch(row);
   ImageView image=new ImageView(context);image.setImageDrawable(icon(context,item.key));image.setScaleType(ImageView.ScaleType.FIT_CENTER);image.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);LinearLayout.LayoutParams iconParams=new LinearLayout.LayoutParams(dp(32),dp(32));iconParams.setMarginEnd(dp(12));row.addView(image,iconParams);
   LinearLayout labels=new LinearLayout(context);labels.setOrientation(LinearLayout.VERTICAL);labels.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO_HIDE_DESCENDANTS);labels.addView(text(item.label,16,item.enabled?0:1,500));
   String detail=item.enabled?"":item.disabled.isEmpty()?"此快捷方式已停用":item.disabled;
   if(!detail.isEmpty()){TextView subtitle=text(detail,12,1,400);subtitle.setPadding(0,dp(3),0,0);labels.addView(subtitle);}
   row.addView(labels,new LinearLayout.LayoutParams(0,-2,1));
   replica.ColorRadioButton radio=new replica.ColorRadioButton(context);radio.setChecked(checked);radio.setEnabled(item.enabled);radio.setClickable(false);radio.setFocusable(false);radio.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);row.addView(radio);
   row.setContentDescription(item.label+(detail.isEmpty()?"":"，"+detail));row.setOnClickListener(v->{if(alive()&&!selecting&&item.enabled)select(item.key,item.label);});parent.addView(row);
  }
  private void select(String key,String title){
   if(!alive()||selecting)return;selecting=true;
   Runnable deliver=()->{if(selectionCancelled||!ownerAvailable()||!embedded&&current!=null)return;remember(context,key,title);callback.onSelected(key);};
   if(embedded){finishClose();deliver.run();return;}
   if(sheet!=null){sheet.dismissThen(deliver);return;}
   AlertDialog leaving=dialog;if(leaving!=null){leaving.setOnDismissListener(d->{if(dialog!=leaving)return;dialog=null;finishClose();deliver.run();});leaving.dismiss();}else close();
  }
  /** Embedded pages retain their visual tree while the host animates the old page out. */
  public void close(){selectionCancelled=true;finishClose();}
  private void finishClose(){
   if(closed)return;closed=true;++generation;if(current==this)current=null;stopWork();
   for(android.animation.ValueAnimator animator:expansions.values()){animator.removeAllUpdateListeners();animator.removeAllListeners();animator.cancel();}expansions.clear();
   replica.ColorActionSheet oldSheet=sheet;sheet=null;AlertDialog oldDialog=dialog;dialog=null;
   if(oldSheet!=null)oldSheet.dismissImmediately();if(oldDialog!=null)replica.ColorDialogBuilder.dismissImmediately(oldDialog);
   if(embedded){content.setEnabled(false);content.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO_HIDE_DESCENDANTS);}else content.removeAllViews();
   callback.onClosed();
  }
 }
 private static final class AppGroup {
  final ShortcutCatalog.Group data;final String title;final Drawable icon;
  AppGroup(ShortcutCatalog.Group data,String title,Drawable icon){this.data=data;this.title=title;this.icon=icon;}
 }
 private static final class Chevron extends ImageView {
  Chevron(Context c){super(c);setScaleType(ImageView.ScaleType.CENTER_INSIDE);float density=getResources().getDisplayMetrics().density;setPadding(Math.round(14*density),Math.round(10*density),Math.round(14*density),Math.round(10*density));watchNow(this,()->{setImageDrawable(replica.SystemIcons.raw(c,"com.android.settings:coui_line_arrow_collapsed"));setColorFilter(replica.ColorOsTheme.secondary(c));});}
 }
 private ShortcutPicker(){}
}

