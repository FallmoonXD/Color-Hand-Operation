package dev.edgeflow;

import replica.ColorOsTheme;import replica.ColorSwitch;import replica.ColorSlider;import replica.ColorRadioButton;import replica.ColorCheckBox;import replica.ColorDialogBuilder;import replica.ColorSettingsScaffold;import replica.ColorPageMotion;import replica.ColorPopupMenu;
import android.app.*;import android.os.*;import android.content.*;import android.content.pm.*;import android.graphics.Color;import android.net.Uri;import android.provider.Settings;import android.view.*;import android.widget.*;import android.text.*;import org.json.*;import java.io.*;import java.util.*;

/** OHO menu hierarchy with the accepted ColorOS settings palette and native controls. */
public final class MainActivity extends Activity {
 private final Object handlePreviewOwner=new Object();
 private FrameLayout pageHost;private ColorSettingsScaffold scaffold;private final ColorPageMotion pageMotion=new ColorPageMotion();private int transition;private boolean uiActive,previewNavigation,chooserBack;private AutoCloseable themeObserver;private android.window.OnBackInvokedCallback backCallback;
 private HomeControls homeControls;private ColorPopupMenu openMenu;private replica.ColorActionSheet actionSheet;private ActionChooser chooser;private replica.ColorPaletteSheet colorPalette;private final HashMap<String,ColorSwatch> colorSwatches=new HashMap<>();private boolean resumedOnce,themeDirty;
 private boolean serviceReceiverRegistered;private final BroadcastReceiver serviceStateReceiver=new BroadcastReceiver(){public void onReceive(Context context,Intent intent){if(uiActive&&screen.equals("home")&&homeControls!=null)homeControls.refresh(false);else if(uiActive&&screen.equals("permissions"))render();else if(uiActive)refreshGeometryPreview();}};
 private LinearLayout content;private String screen="home";private int bg,card,fg,secondary;private final ArrayList<String> history=new ArrayList<>();private String externalGestureKey;private boolean externalSelect;private ScrollView scroller;private String renderedScreen;private String colorHandle="left";private boolean colorBoth,angleMirrored;private final HashMap<String,Integer> scrollPositions=new HashMap<>();private int renderGeneration;private static boolean migrationRunning;
 private ShortcutPicker.Session shortcutSession;private android.content.SharedPreferences prefs;private int navigationEditIndex=-1;private String renderedPageData;private final java.util.concurrent.ExecutorService io=java.util.concurrent.Executors.newSingleThreadExecutor();
 private final HashMap<String,Runnable> directionSummaries=new HashMap<>();
 private final android.content.SharedPreferences.OnSharedPreferenceChangeListener pagePreferenceChanged=(store,key)->{if(pageHost!=null)pageHost.post(()->{if(uiActive&&!isFinishing()&&!isDestroyed()&&!Objects.equals(renderedPageData,pageData()))render();});};
 private void observePageData(boolean observe){for(String name:new String[]{"edgeflow","replica_widgets","edgeflow_launcher"}){android.content.SharedPreferences store=getSharedPreferences(name,0);if(observe)store.registerOnSharedPreferenceChangeListener(pagePreferenceChanged);else store.unregisterOnSharedPreferenceChangeListener(pagePreferenceChanged);}}
 private int dp(float v){return Math.round(v*getResources().getDisplayMetrics().density);}
 private String appName(){return getString(R.string.app_name);}
 @Override public void onCreate(Bundle b){boolean dark=(getResources().getConfiguration().uiMode&48)==32;setTheme(getResources().getIdentifier(dark?"AppThemeDark":"AppTheme","style",getPackageName()));super.onCreate(b);AppPrefs.defaults(this);prefs=AppPrefs.get(this);if(b!=null){screen=b.getString("screen","home");ArrayList<String> saved=b.getStringArrayList("history");if(saved!=null)history.addAll(saved);externalGestureKey=b.getString("externalGestureKey");externalSelect=b.getBoolean("externalSelect",false);colorHandle=b.getString("colorHandle","left");colorBoth=b.getBoolean("colorBoth",false);angleMirrored=b.getBoolean("angleMirrored",false);navigationEditIndex=b.getInt("navigationEditIndex",-1);String chooserKey=b.getString("chooserKey"),chooserOwner=b.getString("chooserOwner");if(chooserKey!=null&&ActionTarget.isKey("target_app_"+chooserKey)&&isHandlePage(chooserOwner)){chooser=new ActionChooser(chooserKey,chooserOwner,b.getInt("chooserDepth",history.size()));chooser.savedY=Math.max(0,b.getInt("chooserScroll",0));}}if(screen.equals("color")||(isExternalActionPage()&&(externalGestureKey==null||!ActionTarget.isKey("target_app_"+externalGestureKey))))screen=history.isEmpty()?"home":history.remove(history.size()-1);pageHost=new FrameLayout(this);getWindow().setDecorFitsSystemWindows(false);setContentView(pageHost);registerReceiver(serviceStateReceiver,new IntentFilter(getPackageName()+".SERVICE_STATE"),Context.RECEIVER_NOT_EXPORTED);serviceReceiverRegistered=true;render();registerBack();observePageData(true);themeObserver=ColorOsTheme.observe(this,()->runOnUiThread(()->{themeDirty=true;if(uiActive&&!isFinishing()&&!isDestroyed())render();}));migrateWidgetsIfPresent();}
 @Override public void onSaveInstanceState(Bundle b){super.onSaveInstanceState(b);b.putString("screen",screen);b.putStringArrayList("history",history);b.putString("externalGestureKey",externalGestureKey);b.putBoolean("externalSelect",externalSelect);b.putString("colorHandle",colorHandle);b.putBoolean("colorBoth",colorBoth);b.putBoolean("angleMirrored",angleMirrored);b.putInt("navigationEditIndex",navigationEditIndex);if(chooser!=null){b.putString("chooserKey",chooser.key);b.putString("chooserOwner",chooser.owner);b.putInt("chooserDepth",chooser.depth);b.putInt("chooserScroll",chooser.position());}}
 @Override public void onResume(){
  super.onResume();uiActive=true;previewNavigation=false;boolean first=!resumedOnce;resumedOnce=true;
  if(themeDirty||!first&&!Objects.equals(renderedPageData,pageData()))render();
  else{if(!first&&homeControls!=null)homeControls.refresh(false);refreshGeometryPreview();restoreActionChooser();}
 }
 @Override public void onPause(){
  uiActive=false;dismissMenu();
  dismissColorPalette();if(content!=null)android.transition.TransitionManager.endTransitions(content);endGeometryPreview();pageMotion.cancel();super.onPause();
 }
 @Override public void onStop(){endGeometryPreview();super.onStop();}
 /** Returning from an overlay does not invalidate a settings page. Compare only its displayed data. */
 private String pageData(){
  if(screen.equals("widgets"))return Boolean.toString(getSharedPreferences("replica_widgets",0).getBoolean("loop",false));
  if(screen.equals("launcher")){android.content.SharedPreferences store=getSharedPreferences("edgeflow_launcher",0);return LauncherLayout.load(store).recent+":"+store.getInt("columns",4);}
  if(screen.equals("tasks"))return Integer.toString(getSharedPreferences("edgeflow_launcher",0).getInt("recent_limit",10));
  if(screen.equals("navigation"))return Boolean.toString(prefs.getBoolean("navigation_vertical",false));
  if(screen.equals("permissions"))return (GestureService.instance()!=null)+":"+((PowerManager)getSystemService(POWER_SERVICE)).isIgnoringBatteryOptimizations(getPackageName());
  return "";
 }
 @Override public void onBackPressed(){
  previewNavigation=false;
  if(actionSheet!=null&&actionSheet.isShowing()){actionSheet.dismiss();return;}
  previewNavigation=false;endGeometryPreview();if(screen.equals("home")){dismissActionSheet();finish();return;}
  transition=-1;screen=history.isEmpty()?"home":history.remove(history.size()-1);
  if(chooser!=null&&(history.size()<chooser.depth||history.size()==chooser.depth&&!screen.equals(chooser.owner)))dismissActionSheet();
  render();if(!isExternalActionPage())externalGestureKey=null;
 }
 private void page(String name){endGeometryPreview();transition=1;if(scroller!=null)scrollPositions.put(screen,scroller.getScrollY());history.add(screen);screen=name;render();}
 private TextView text(String s,int sp,int color){TextView t=new TextView(this);t.setText(s);t.setTextSize(sp);t.setTextColor(color);t.setTypeface(ColorOsTheme.typeface(this,400));t.setIncludeFontPadding(false);t.setGravity(Gravity.CENTER_VERTICAL);return t;}
 private void render(){
  if(themeDirty)invalidateChooserView();
  closeShortcutPage();dismissMenu();homeControls=null;colorSwatches.clear();directionSummaries.clear();themeDirty=false;
  if(scroller!=null&&screen.equals(renderedScreen))scrollPositions.put(screen,scroller.getScrollY());renderedScreen=screen;
  endGeometryPreview();renderGeneration++;boolean dark=ColorOsTheme.dark(this);
  bg=ColorOsTheme.background(this);card=ColorOsTheme.card(this);fg=ColorOsTheme.text(this);secondary=ColorOsTheme.secondary(this);
  ColorOsTheme.install(this);
  if(chooser!=null&&chooser.atOwner()&&chooser.scaffold!=null){
   scaffold=chooser.scaffold;scroller=scaffold.scroll;content=scaffold.content;colorSwatches.putAll(chooser.swatches);directionSummaries.putAll(chooser.summaries);refreshDirectionSummaries();
   chooser.refresh();chooser.sheet.resume();pageMotion.show(pageHost,scaffold.root,transition);transition=0;renderedPageData=pageData();refreshGeometryPreview();return;
  }
  scaffold=new ColorSettingsScaffold(this,pageTitle(),screen.equals("home"),this::onBackPressed);
  if(screen.equals("home"))scaffold.setMore(this::moreMenu,"更多选项");
  scroller=scaffold.scroll;content=scaffold.content;
  switch(screen){
   case "left":case "right":case "extra_left":case "extra_right":case "extra2_left":case "extra2_right":side(screen);break;
   case "advanced":advanced();break;case "gestures":gestureSettings();break;case "angle":angleSettings();break;case "exceptions":excludedApps();break;
   case "touchpad":touchpadSettings();break;case "navigation":navigationSettings();break;case "launcher":launcherSettings();break;case "launcher_columns":launcherColumns();break;case "tasks":taskSettings();break;case "widgets":widgetSettings();break;case "tools":toolsSettings();break;case "keyboard":keyboardSettings();break;
   case "action_app":applicationActionSettings();break;case "action_shortcut":shortcutActionSettings();break;case "navigation_action":navigationActionSettings();break;
   case "permissions":permissions();break;case "backup":backup();break;case "widget_migration":widgetMigrationReport();break;case "about":about();break;default:home();
  }
  int y=scrollPositions.getOrDefault(screen,0);ScrollView currentScroll=scroller;if(y>0)currentScroll.post(()->currentScroll.scrollTo(0,y));
  if(chooser!=null&&chooser.atOwner()&&chooser.sheet==null)buildActionChooser(chooser,true);
  pageMotion.show(pageHost,scaffold.root,transition);transition=0;renderedPageData=pageData();refreshGeometryPreview();restoreActionChooser();
 }
 private String pageTitle(){return switch(screen){case "left","right","extra_left","extra_right","extra2_left","extra2_right"->handleTitle(screen);case "advanced"->"高级设置";case "gestures"->"手势设置";case "angle"->"手势角度";case "exceptions"->"应用例外";case "touchpad"->"虚拟触控板";case "navigation"->"导航键面板";case "launcher"->"快速启动";case "launcher_columns"->"列数";case "tasks"->"任务切换器";case "widgets"->"小组件";case "tools"->"快速工具";case "keyboard"->"键盘快捷方式";case "navigation_action"->"选择导航动作";case "action_app"->"启动指定应用";case "action_shortcut"->"应用快捷方式";case "permissions"->"权限与后台运行";case "backup"->"备份与恢复";case "widget_migration"->"小组件迁移结果";case "about"->"关于 "+appName();default->appName();};}
 private ImageView icon(String name,String description){return icon(name,description,false);}
 private ImageView icon(String name,String description,boolean accent){ImageView view=new ImageView(this);view.setImageDrawable(replica.SystemIcons.raw(this,name));Runnable tint=()->view.setColorFilter(accent?ColorOsTheme.accent(this):ColorOsTheme.text(this));tint.run();ColorOsTheme.watch(view,tint);view.setScaleType(ImageView.ScaleType.FIT_CENTER);view.setContentDescription(description);return view;}
 private LinearLayout card(){return card(content);}
 private LinearLayout card(LinearLayout parent){LinearLayout box=new LinearLayout(this);box.setOrientation(1);box.setPadding(dp(4),dp(4),dp(4),dp(4));box.setBackground(replica.PanelStyle.shape(this,card,20));LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(-1,-2);lp.bottomMargin=dp(14);parent.addView(box,lp);return box;}
 private void note(String value){TextView view=text(value,14,secondary);view.setLineSpacing(0,1.2f);view.setPadding(dp(12),dp(2),dp(12),dp(18));content.addView(view);}
 private void divider(LinearLayout box){if(box.getChildCount()==0||"settings_divider".equals(box.getChildAt(box.getChildCount()-1).getTag()))return;View line=new View(this);line.setTag("settings_divider");line.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);line.setBackgroundColor((fg&0x00ffffff)|0x10000000);LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(-1,Math.max(1,dp(.5f)));lp.leftMargin=dp(16);lp.rightMargin=dp(16);box.addView(line,lp);}
 private LinearLayout group(String heading){if(!heading.isEmpty()){TextView label=text(heading,14,secondary);label.setTypeface(ColorOsTheme.typeface(this,500));label.setPadding(dp(6),dp(10),0,dp(8));content.addView(label);}return card();}
 private LinearLayout row(LinearLayout box,String title,String detail){divider(box);LinearLayout row=new LinearLayout(this);row.setBaselineAligned(false);row.setGravity(Gravity.CENTER_VERTICAL);boolean hasDetail=detail!=null&&!detail.isEmpty();row.setPadding(dp(16),dp(hasDetail?10:4),dp(12),dp(hasDetail?10:4));row.setMinimumHeight(dp(hasDetail?64:56));LinearLayout labels=new LinearLayout(this);labels.setOrientation(1);TextView rowTitle=text(title,16,fg);rowTitle.setTypeface(ColorOsTheme.typeface(this,500));rowTitle.setLineSpacing(0,1.05f);labels.addView(rowTitle);if(detail!=null&&!detail.isEmpty()){TextView sub=text(detail,13,secondary);sub.setLineSpacing(0,1.2f);sub.setPadding(0,dp(4),dp(4),0);labels.addView(sub);}row.addView(labels,new LinearLayout.LayoutParams(0,-2,1));box.addView(row);return row;}
 private void link(LinearLayout box,String title,String detail,Runnable action){LinearLayout r=row(box,title,detail);ImageView arrow=new ImageView(this);arrow.setImageDrawable(replica.SystemIcons.raw(this,"com.android.settings:ic_arrow_forward"));arrow.setScaleType(ImageView.ScaleType.FIT_CENTER);arrow.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);Runnable tint=()->arrow.setColorFilter(ColorOsTheme.secondary(this));tint.run();ColorOsTheme.watch(arrow,tint);r.addView(arrow,new LinearLayout.LayoutParams(dp(18),dp(18)));r.setContentDescription(title);r.setBackground(new android.graphics.drawable.RippleDrawable(android.content.res.ColorStateList.valueOf(0x15808080),null,replica.PanelStyle.shape(this,Color.WHITE,16)));r.setOnClickListener(v->action.run());}
 private void toggle(LinearLayout box,String title,String detail,String key,boolean def){LinearLayout r=row(box,title,detail);ColorSwitch sw=new ColorSwitch(this);sw.setChecked(prefs.getBoolean(key,def));sw.setContentDescription(title);r.addView(sw);sw.setOnCheckedChangeListener((v,on)->{prefs.edit().putBoolean(key,on).apply();renderedPageData=pageData();reload();});r.setOnClickListener(v->sw.setChecked(!sw.isChecked()));}
 private void reload(){GestureService.reload(this);}
 private void home(){new HomeControls();}
 private void beginListChange(LinearLayout parent){
  android.transition.TransitionManager.endTransitions(parent);
  if(!parent.isLaidOut()||!android.animation.ValueAnimator.areAnimatorsEnabled())return;
  android.transition.TransitionSet changes=new android.transition.TransitionSet().setOrdering(android.transition.TransitionSet.ORDERING_TOGETHER);
  changes.addTransition(new android.transition.ChangeBounds().setDuration(280));
  changes.addTransition(new android.transition.Fade().setDuration(180));
  changes.setInterpolator(new android.view.animation.PathInterpolator(.3f,0,.1f,1));
  android.transition.TransitionManager.beginDelayedTransition(parent,changes);
 }
 /** Keep switches, the title and scroll position alive when state changes. */
 private final class HomeControls {
  final ColorSwitch enabled;final TextView status,add,remove;final LinearLayout body,pairsHost,serviceCard,geometryCard;
  final ArrayList<LinearLayout> pairs=new ArrayList<>();final HashMap<String,ColorSwitch> switches=new HashMap<>();final HashMap<String,View> markers=new HashMap<>();
  boolean binding;
  HomeControls(){
   homeControls=this;
   LinearLayout master=card();LinearLayout masterRow=row(master,"",null);status=(TextView)((LinearLayout)masterRow.getChildAt(0)).getChildAt(0);
   enabled=new ColorSwitch(MainActivity.this);enabled.setChecked(prefs.getBoolean("enabled",true));enabled.setContentDescription("启用手势");masterRow.addView(enabled);
   enabled.setOnCheckedChangeListener((v,on)->{if(binding)return;prefs.edit().putBoolean("enabled",on).apply();refresh(false);reload();});masterRow.setOnClickListener(v->enabled.setChecked(!enabled.isChecked()));
   note("在屏幕左侧或右侧添加手势触钮，以便单手轻松控制手机。\n您可以自定义从各个触钮向不同方向滑动时要执行的操作。");
   serviceCard=card();link(serviceCard,"开启无障碍服务","启用后即可从屏幕边缘滑动操作",()->open(new Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)));
   body=new LinearLayout(MainActivity.this);body.setOrientation(1);content.addView(body,new LinearLayout.LayoutParams(-1,-2));
   pairsHost=new LinearLayout(MainActivity.this);pairsHost.setOrientation(1);body.addView(pairsHost,new LinearLayout.LayoutParams(-1,-2));
   LinearLayout controls=new LinearLayout(MainActivity.this);LinearLayout.LayoutParams cp=new LinearLayout.LayoutParams(-1,dp(48));cp.bottomMargin=dp(20);body.addView(controls,cp);
   add=button("添加触钮",()->{int n=HandleSettings.pairCount(prefs);if(n>=3)return;endGeometryPreview();prefs.edit().putInt("handle_pair_count",n+1).apply();refresh(true);reload();});
   remove=button("移除触钮",MainActivity.this::removePair);controls.addView(add,new LinearLayout.LayoutParams(0,-1,1));LinearLayout.LayoutParams rp=new LinearLayout.LayoutParams(0,-1,1);rp.leftMargin=dp(12);controls.addView(remove,rp);
   LinearLayout settings=card(body);link(settings,"高级设置","应用例外 · 隐藏触钮",()->page("advanced"));link(settings,"手势设置","快捷操作 · 滑动距离 · 振动",()->page("gestures"));
   geometryCard=card(body);if(HandleSettings.linked(prefs,"left"))geometry(geometryCard,"left",true);
   refresh(false);
  }
  void refresh(boolean animate){
   if(homeControls!=this)return;binding=true;
   try{
    int count=HandleSettings.pairCount(prefs);if(animate)beginListChange(body);
    while(pairs.size()>count){int index=pairs.size()-1;String prefix=index==1?"extra_":"extra2_";switches.remove(prefix+"left");switches.remove(prefix+"right");markers.remove(prefix+"left");markers.remove(prefix+"right");pairsHost.removeView(pairs.remove(index));}
    while(pairs.size()<count){int index=pairs.size();LinearLayout pair=card(pairsHost);pairs.add(pair);String prefix=index==0?"":index==1?"extra_":"extra2_";handleRow(pair,prefix+"left");handleRow(pair,prefix+"right");}
    boolean active=prefs.getBoolean("enabled",true);enabled.setChecked(active);status.setText(active?"已开启":"已关闭");
    serviceCard.setVisibility(GestureService.instance()==null?View.VISIBLE:View.GONE);
    geometryCard.setVisibility(count==1&&HandleSettings.linked(prefs,"left")?View.VISIBLE:View.GONE);
    for(Map.Entry<String,ColorSwitch> item:switches.entrySet()){String id=item.getKey();boolean on=prefs.getBoolean(id+"_enabled",!GestureHandleConfig.isExtra(id));item.getValue().setChecked(on);setGroupMarker(markers.get(id),id,on);}
    enableTree(body,active);body.animate().cancel();if(body.isAttachedToWindow()&&android.animation.ValueAnimator.areAnimatorsEnabled())body.animate().alpha(active?1:.4f).setDuration(160).start();else body.setAlpha(active?1:.4f);
    add.setEnabled(active&&count<3);remove.setEnabled(active&&count>1);add.setAlpha(count<3?1:.35f);remove.setAlpha(count>1?1:.35f);
   }finally{binding=false;}refreshGeometryPreview();
  }
 }
 private void enableTree(View v,boolean enabled){v.setEnabled(enabled);if(v instanceof ViewGroup)for(int i=0;i<((ViewGroup)v).getChildCount();i++)enableTree(((ViewGroup)v).getChildAt(i),enabled);}
 private TextView button(String title,Runnable action){TextView t=text(title,15,ColorOsTheme.accent(this));t.setGravity(Gravity.CENTER);t.setBackground(ColorDialogBuilder.touchBackground(this,card,24));t.setContentDescription(title);t.setOnClickListener(v->action.run());return t;}
 private String handleTitle(String id){String name=GestureHandleConfig.isLeft(id)?"左侧触钮":"右侧触钮";return name+(id.startsWith("extra2_")?" 3":id.startsWith("extra_")?" 2":"");}
 private String handleSummary(String id){ArrayList<String> actions=new ArrayList<>();for(boolean longer:new boolean[]{false,true}){if(longer&&!prefs.getBoolean(id+"_long_enabled",true))continue;for(String direction:new String[]{"straight","up","down"}){String action=AppPrefs.action(this,id,direction,longer);if(!action.equals("none"))actions.add(actionTitle(action));}}return actions.isEmpty()?"未设置操作":android.text.TextUtils.join("、",actions);}
 private void handleRow(LinearLayout box,String id){boolean extra=GestureHandleConfig.isExtra(id);LinearLayout r=row(box,handleTitle(id),handleSummary(id));LinearLayout labels=(LinearLayout)r.getChildAt(0);TextView summary=(TextView)labels.getChildAt(1);summary.setMaxLines(2);summary.setEllipsize(android.text.TextUtils.TruncateAt.END);View color=new View(this);setGroupMarker(color,id,prefs.getBoolean(id+"_enabled",!extra));color.setContentDescription(handleTitle(id)+"区域标识");LinearLayout.LayoutParams colorLp=new LinearLayout.LayoutParams(dp(25),dp(25));colorLp.leftMargin=dp(10);colorLp.rightMargin=dp(12);r.addView(color,colorLp);ColorSwitch sw=new ColorSwitch(this);sw.setChecked(prefs.getBoolean(id+"_enabled",!extra));sw.setContentDescription("启用"+handleTitle(id));r.addView(sw);if(homeControls!=null){homeControls.switches.put(id,sw);homeControls.markers.put(id,color);}sw.setOnCheckedChangeListener((v,on)->{if(homeControls!=null&&homeControls.binding)return;prefs.edit().putBoolean(id+"_enabled",on).apply();setGroupMarker(color,id,on);reload();refreshGeometryPreview();});r.setContentDescription(handleTitle(id));r.setOnClickListener(v->{if(!sw.isChecked())sw.setChecked(true);else page(id);});}
 private void dismissMenu(){if(openMenu!=null){openMenu.dismissImmediately();openMenu=null;}}
 private void removePair(){
  int count=HandleSettings.pairCount(prefs);if(count<=1||homeControls==null)return;dismissMenu();
  openMenu=new ColorPopupMenu(homeControls.remove).setTitle("移除触钮").setMessage("已设置的动作和位置会保留，添加后可再次使用。").setRowHeightDp(56).setDividerBefore(1).setDestructive(0).setItems(new String[]{"移除第 "+count+" 对触钮","取消"},n->{
   if(n!=0||homeControls==null||HandleSettings.pairCount(prefs)!=count)return;endGeometryPreview();String prefix=count==3?"extra2_":"extra_";
   prefs.edit().putBoolean(prefix+"left_enabled",false).putBoolean(prefix+"right_enabled",false).putInt("handle_pair_count",count-1).apply();homeControls.refresh(true);reload();
  }).show();
 }
 private void moreMenu(View anchor){dismissMenu();openMenu=new ColorPopupMenu(anchor).setItems(new String[]{"权限与后台运行","备份与恢复","恢复默认设置","关于"},n->{switch(n){case 0:page("permissions");break;case 1:page("backup");break;case 2:resetSettings();break;case 3:page("about");break;}}).setDividerBefore(3).show();}
 private void resetSettings(){new ColorDialogBuilder(this).setTitle("恢复默认设置？").setMessage("重置触钮、手势动作和识别设置。面板布局、应用快捷方式和已绑定的小组件保留。").setNegativeButton("取消",null).setPositiveDestructive("恢复",(d,n)->{prefs.edit().clear().commit();AppPrefs.defaults(this);history.clear();screen="home";scrollPositions.clear();reload();render();}).show();}
 private void permissions(){LinearLayout g=card();link(g,"无障碍服务",GestureService.instance()!=null?"已连接":"需要开启 "+appName()+" 服务",()->open(new Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)));link(g,"后台运行",((PowerManager)getSystemService(POWER_SERVICE)).isIgnoringBatteryOptimizations(getPackageName())?"已允许后台运行":"允许不受电池优化限制",()->open(new Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS,Uri.parse("package:"+getPackageName()))));link(g,"系统自启动","允许 ColorOS 在应用异常退出后恢复服务",()->ActionExecutor.root(this,"am start -n com.oplus.battery/com.oplus.startupapp.view.StartupAppListActivity","系统自启动"));g=card();link(g,"相机、通知与蓝牙权限","手电筒、后台通知和蓝牙控制",()->requestPermissions(new String[]{android.Manifest.permission.CAMERA,android.Manifest.permission.POST_NOTIFICATIONS,android.Manifest.permission.BLUETOOTH_CONNECT},10));link(g,"勿扰访问权限","声音模式和勿扰控制",()->open(new Intent(Settings.ACTION_NOTIFICATION_POLICY_ACCESS_SETTINGS)));}
 private void backup(){LinearLayout g=card();link(g,"导出配置","手势、面板和快捷键，以及小组件绑定信息",()->openDocument(true));link(g,"导入配置","恢复配置；小组件需要重新绑定",()->openDocument(false));if(new File(getFilesDir(),"migration-widgets-report.json").isFile()){g=card();link(g,"小组件迁移结果","查看需要重新设置的组件",this::showWidgetMigrationReport);}}
 private void about(){String version="";try{version=getPackageManager().getPackageInfo(getPackageName(),0).versionName;}catch(Exception ignored){}LinearLayout g=card();row(g,appName(),version);note("按 One Hand Operation+ 的操作方式设计，采用 ColorOS 风格界面。");}
 private void preview(String id){GestureService s=GestureService.instance();if(s!=null)ActionExecutor.execute(s,id,replica.NativeState.defaultGesture(this));else if(id.equals("widgets"))open(new Intent(this,replica.WidgetActivity.class));else toast("请先开启无障碍服务");}
 private void side(String id){
  LinearLayout shortActions=group("短距离滑动");directionRows(shortActions,id,false);
  LinearLayout longActions=group("");LinearLayout switchRow=row(longActions,"长距离滑动","当执行较长距离滑动并用手指按住屏幕时，执行不同的操作。");ColorSwitch longer=new ColorSwitch(this);longer.setChecked(prefs.getBoolean(id+"_long_enabled",true));longer.setContentDescription("长距离滑动");switchRow.addView(longer);
  divider(longActions);View longLine=longActions.getChildAt(longActions.getChildCount()-1);LinearLayout longRows=new LinearLayout(this);longRows.setOrientation(1);longActions.addView(longRows);directionRows(longRows,id,true);longRows.setVisibility(longer.isChecked()?View.VISIBLE:View.GONE);longLine.setVisibility(longer.isChecked()?View.VISIBLE:View.GONE);
  longer.setOnCheckedChangeListener((v,on)->{prefs.edit().putBoolean(id+"_long_enabled",on).apply();beginListChange(content);longRows.setVisibility(on?View.VISIBLE:View.GONE);longLine.setVisibility(on?View.VISIBLE:View.GONE);reload();});switchRow.setOnClickListener(v->longer.setChecked(!longer.isChecked()));
  LinearLayout adjust=group("触钮设置");LinearLayout align=row(adjust,"对齐触钮","左侧和右侧保持相同的尺寸和位置");ColorSwitch linked=new ColorSwitch(this);linked.setChecked(HandleSettings.linked(prefs,id));linked.setContentDescription("对齐触钮");align.addView(linked);
  LinearLayout geometryRows=new LinearLayout(this);geometryRows.setOrientation(1);adjust.addView(geometryRows);
  if(HandleSettings.pairCount(prefs)>1||!linked.isChecked())geometry(geometryRows,id,linked.isChecked());
  linked.setOnCheckedChangeListener((v,on)->{endGeometryPreview();if(on)HandleSettings.write(prefs,id,HandleSettings.read(prefs,id),true);prefs.edit().putBoolean(HandleSettings.linkedKey(id),on).apply();beginListChange(content);geometryRows.removeAllViews();if(HandleSettings.pairCount(prefs)>1||!on)geometry(geometryRows,id,on);reload();refreshGeometryPreview();});align.setOnClickListener(v->linked.setChecked(!linked.isChecked()));
 }
 private void directionRows(LinearLayout box,String id,boolean longer){String[] directions={"straight","up","down"};for(int i=0;i<3;i++){String direction=directions[i];String name=i==0?(GestureHandleConfig.isLeft(id)?"直线向右":"直线向左"):i==1?"对角线向上":"对角线向下";LinearLayout r=row(box,name,actionTitle(AppPrefs.action(this,id,direction,longer)));bindDirectionSummary(r,id,direction,longer);ImageView arrow=icon("status_bar_qs_back_arrow",null,true);arrow.setPadding(dp(9),dp(9),dp(9),dp(9));arrow.setRotation(GestureHandleConfig.isLeft(id)?(i==0?180:i==1?135:225):(i==0?0:i==1?45:-45));arrow.setColorFilter(ColorOsTheme.accent(this));FrameLayout iconContainer=new FrameLayout(this);iconContainer.setBackground(replica.PanelStyle.shape(this,(ColorOsTheme.accent(this)&0x00ffffff)|0x12000000,longer?12:22));iconContainer.addView(arrow,new FrameLayout.LayoutParams(-1,-1));LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(dp(42),dp(42));lp.rightMargin=dp(16);r.addView(iconContainer,0,lp);r.setContentDescription((longer?"长滑":"短滑")+name);r.setOnClickListener(v->chooseAction(GestureHandleConfig.actionKey(id,direction,longer)));if(i<2)divider(box);}}
 private void bindDirectionSummary(LinearLayout row,String id,String direction,boolean longer){TextView summary=(TextView)((LinearLayout)row.getChildAt(0)).getChildAt(1);directionSummaries.put(GestureHandleConfig.actionKey(id,direction,longer),()->summary.setText(actionTitle(AppPrefs.action(this,id,direction,longer))));}
 private void refreshDirectionSummaries(){for(Runnable bind:directionSummaries.values())bind.run();}
 private interface NumberEdit{void save(float value);}
 private LinearLayout sliderBody(LinearLayout box,String title,String description){
  divider(box);LinearLayout wrap=new LinearLayout(this);wrap.setOrientation(1);wrap.setPadding(dp(16),dp(10),dp(16),dp(4));box.addView(wrap);TextView label=text(title,16,fg);label.setTypeface(ColorOsTheme.typeface(this,500));wrap.addView(label);
  if(description!=null&&!description.isEmpty()){TextView detail=text(description,13,secondary);detail.setLineSpacing(0,1.2f);detail.setPadding(0,dp(2),0,0);wrap.addView(detail);}return wrap;
 }
 private interface NumberLabel{String text(float value);}
 private void valueSlider(LinearLayout box,String title,float value,float min,float max,String unit,String low,String high,NumberEdit edit){editableSlider(box,title,null,title,value,min,max,1000,low,high,v->format(v,unit,false),edit);}
 private void editableSlider(LinearLayout box,String title,String description,String accessibilityLabel,float value,float min,float max,int steps,String low,String high,NumberLabel labeler,NumberEdit edit){
  LinearLayout wrap=sliderBody(box,title,description);TextView number=sliderEndpoints(wrap,accessibilityLabel,low,high,labeler.text(value));ColorSlider seek=new ColorSlider(this);seek.setMax(steps);seek.setProgress(Math.round((value-min)/(max-min)*steps));seek.setContentDescription(accessibilityLabel);seek.setStateDescription(number.getText());wrap.addView(seek,settingsSliderLayout());
  final boolean[] cancelled={false},tracking={false};seek.setOnTouchListener((v,e)->{if(e.getActionMasked()==MotionEvent.ACTION_DOWN)cancelled[0]=false;if(e.getActionMasked()==MotionEvent.ACTION_CANCEL)cancelled[0]=true;return false;});
  seek.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener(){float current=value,committed=value;public void onProgressChanged(SeekBar b,int p,boolean user){current=min+(max-min)*p/steps;sliderNumber(number,seek,labeler.text(current));if(user&&!tracking[0]){edit.save(current);committed=current;reload();}}public void onStartTrackingTouch(SeekBar b){tracking[0]=true;cancelled[0]=false;}public void onStopTrackingTouch(SeekBar b){tracking[0]=false;if(!sliderCancelled(b,cancelled[0])){edit.save(current);committed=current;reload();}else seek.setProgress(Math.round((committed-min)/(max-min)*steps));}});
 }
 private void setGroupMarker(View view,String id,boolean enabled){int color=HandlePreviewView.groupColor(this,id);view.setBackground(replica.PanelStyle.shape(this,enabled&&prefs.getBoolean("enabled",true)?(color&0x00ffffff)|(ColorOsTheme.dark(this)?0x55000000:0x44000000):Color.TRANSPARENT,14));}
 private interface GeometryDisplay{void show(HandleSettings.Geometry value);}
 private final class GeometryControls{
  final String id;final boolean linked;final ArrayList<GeometryDisplay> displays=new ArrayList<>();
  GeometryControls(String id,boolean linked){this.id=id;this.linked=linked;}
  void show(HandleSettings.Geometry value){for(GeometryDisplay display:displays)display.show(value);}
  void restore(){show(HandleSettings.read(prefs,id));refreshGeometryPreview();}
  void commit(HandleSettings.Geometry value){HandleSettings.write(prefs,id,value,linked);show(HandleSettings.read(prefs,id));reload();refreshGeometryPreview();}
 }
 private void geometry(LinearLayout box,String id,boolean linked){
  GeometryControls controls=new GeometryControls(id,linked);
  if(!GestureHandleConfig.isExtra(id))geometrySlider(box,"触摸宽度","小","大",controls,0);
  geometrySlider(box,"大小","小","大",controls,1);
  geometrySlider(box,"位置","低","高",controls,2);
  LinearLayout colorRow=row(box,"触钮颜色",null);ColorSwatch swatch=new ColorSwatch(handleColor(id));colorSwatches.put(id,swatch);colorRow.addView(swatch,new LinearLayout.LayoutParams(dp(30),dp(30)));colorRow.setContentDescription("触钮颜色");colorRow.setBackground(ColorDialogBuilder.touchBackground(this,Color.TRANSPARENT,16));colorRow.setOnClickListener(v->showColorPalette(id,screen.equals("home")&&linked));
 }
 private TextView sliderEndpoints(LinearLayout box,String title,String low,String high,String value){
  LinearLayout limits=new LinearLayout(this);limits.setBaselineAligned(false);limits.setPadding(0,dp(4),0,0);TextView left=text(low,12,secondary),number=text(value,12,secondary),right=text(high,12,secondary);number.setGravity(Gravity.CENTER);number.setContentDescription(title+"当前值");number.setStateDescription(value);number.setFontFeatureSettings("tnum");right.setGravity(Gravity.END);limits.addView(left,new LinearLayout.LayoutParams(0,-2,1));limits.addView(number,new LinearLayout.LayoutParams(0,-2,1));limits.addView(right,new LinearLayout.LayoutParams(0,-2,1));box.addView(limits);return number;
 }
 private LinearLayout.LayoutParams settingsSliderLayout(){LinearLayout.LayoutParams layout=new LinearLayout.LayoutParams(-1,dp(44));layout.topMargin=-dp(8);return layout;}
 private void sliderNumber(TextView number,SeekBar seek,String value){number.setText(value);number.setStateDescription(value);seek.setStateDescription(value);}
 private void geometrySlider(LinearLayout box,String title,String low,String high,GeometryControls controls,int property){
  String id=controls.id;boolean linked=controls.linked;
  LinearLayout wrap=sliderBody(box,title,null);TextView number=sliderEndpoints(wrap,title,low,high,"");
  ColorSlider seek=new ColorSlider(this);seek.setMax(1000);seek.setContentDescription(title);wrap.addView(seek,settingsSliderLayout());
  HandleSettings.Geometry initial=HandleSettings.read(prefs,id);seek.setProgress(initial.sliderProgress(property));
  final boolean[] cancelled={false},tracking={false};final HandleSettings.Geometry[] baseline={initial},draft={initial};
  GeometryDisplay display=value->{if(!tracking[0])seek.setProgress(value.sliderProgress(property));sliderNumber(number,seek,property==0?format(value.width,"dp",false):property==1?format(value.size(),"%",false):format(value.sliderProgress(2)/1000f,"%",false));};controls.displays.add(display);
  seek.setOnTouchListener((v,e)->{if(e.getActionMasked()==MotionEvent.ACTION_DOWN)cancelled[0]=false;if(e.getActionMasked()==MotionEvent.ACTION_CANCEL)cancelled[0]=true;return false;});
  seek.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener(){
   public void onProgressChanged(SeekBar bar,int progress,boolean user){if(!user)return;HandleSettings.Geometry start=tracking[0]?baseline[0]:HandleSettings.read(prefs,id);draft[0]=start.withSliderProgress(property,progress);if(tracking[0]){controls.show(draft[0]);showGeometryPreview(id,linked,draft[0]);}else controls.commit(draft[0]);}
   public void onStartTrackingTouch(SeekBar bar){tracking[0]=true;cancelled[0]=false;baseline[0]=HandleSettings.read(prefs,id);draft[0]=baseline[0];showGeometryPreview(id,linked,draft[0]);}
   public void onStopTrackingTouch(SeekBar bar){tracking[0]=false;if(!sliderCancelled(bar,cancelled[0]))controls.commit(draft[0]);else controls.restore();}
  });
  display.show(initial);
 }
 private void refreshGeometryPreview(){showGeometryPreview(null,false,null);}
 private void showGeometryPreview(String changed,boolean both,HandleSettings.Geometry draft){
  if(!uiActive||previewNavigation){endGeometryPreview();return;}
  GestureService.setSettingsHandlePreviews(this,handlePreviewOwner,HandleSettings.previewRegions(prefs,screen,changed,both,draft));
 }
 private void endGeometryPreview(){GestureService.endSettingsHandlePreviews(handlePreviewOwner);}
 private void registerBack(){
  if(android.os.Build.VERSION.SDK_INT>=34){backCallback=new android.window.OnBackAnimationCallback(){
   public void onBackStarted(android.window.BackEvent event){chooserBack=actionSheet!=null&&actionSheet.isShowing();previewNavigation=true;endGeometryPreview();if(!chooserBack)pageMotion.cancel();}
   public void onBackProgressed(android.window.BackEvent event){if(!chooserBack&&!screen.equals("home"))pageMotion.progress(event.getProgress(),event.getSwipeEdge());}
   public void onBackCancelled(){previewNavigation=false;if(!chooserBack)pageMotion.cancelBack();chooserBack=false;refreshGeometryPreview();}
   public void onBackInvoked(){chooserBack=false;onBackPressed();}
  };}else backCallback=this::onBackPressed;
  getOnBackInvokedDispatcher().registerOnBackInvokedCallback(android.window.OnBackInvokedDispatcher.PRIORITY_DEFAULT,backCallback);
 }
 private int handleColor(String id){return prefs.getInt(id+"_color",prefs.getInt("handle_color",0xff188dff));}
 private final class ColorSwatch extends View{
  private final android.graphics.Paint paint=new android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG);private int value;
  ColorSwatch(int color){super(MainActivity.this);setColor(color);ColorOsTheme.watch(this,this::invalidate);}
  void setColor(int color){value=color;setContentDescription(String.format(java.util.Locale.ROOT,"#%08X",color));invalidate();}
  @Override protected void onDraw(android.graphics.Canvas canvas){super.onDraw(canvas);float x=getWidth()/2f,y=getHeight()/2f,r=Math.min(x,y)-dp(1);if(r<=0)return;android.graphics.Path clip=new android.graphics.Path();clip.addCircle(x,y,r,android.graphics.Path.Direction.CW);int saved=canvas.save();canvas.clipPath(clip);int cell=Math.max(1,dp(6));boolean dark=ColorOsTheme.dark(MainActivity.this);paint.setStyle(android.graphics.Paint.Style.FILL);for(int row=0;row<getHeight();row+=cell)for(int col=0;col<getWidth();col+=cell){paint.setColor(((row/cell+col/cell)&1)==0?(dark?0xff454951:0xffe8ecf2):(dark?0xff656b76:0xffc5cdd8));canvas.drawRect(col,row,col+cell,row+cell,paint);}paint.setColor(value);canvas.drawCircle(x,y,r,paint);canvas.restoreToCount(saved);paint.setStyle(android.graphics.Paint.Style.STROKE);paint.setStrokeWidth(dp(1));paint.setColor((ColorOsTheme.secondary(MainActivity.this)&0xffffff)|0x30000000);canvas.drawCircle(x,y,r,paint);}
 }
 private void refreshColorSwatches(){for(Map.Entry<String,ColorSwatch> entry:colorSwatches.entrySet())entry.getValue().setColor(handleColor(entry.getKey()));}
 private void dismissColorPalette(){if(colorPalette!=null){replica.ColorPaletteSheet previous=colorPalette;colorPalette=null;previous.dismissImmediately();}}
 private void showColorPalette(String id,boolean both){
  dismissColorPalette();colorHandle=id;colorBoth=both;
  replica.ColorPaletteSheet sheet=new replica.ColorPaletteSheet(this,handleColor(id),prefs.getInt("handle_opacity",0),new replica.ColorPaletteSheet.Listener(){
   public void onPreview(int argb,int opacity){ColorSwatch swatch=colorSwatches.get(id);if(swatch!=null)swatch.setColor(argb);}
   public void onConfirm(int argb,int opacity){if(isFinishing()||isDestroyed())return;android.content.SharedPreferences.Editor edit=prefs.edit().putInt(id+"_color",argb).putInt("handle_opacity",Math.max(0,Math.min(100,opacity)));if(both)edit.putInt(HandleSettings.peer(id)+"_color",argb);edit.apply();refreshColorSwatches();reload();}
   public void onCancel(){refreshColorSwatches();}
  });sheet.setOnDismissListener(()->{if(colorPalette==sheet)colorPalette=null;});colorPalette=sheet;sheet.show();
 }
 private String actionTitle(String id){return ShortcutEntry.valid(id)?ShortcutPicker.label(this,id):ActionExecutor.title(id);}
 private static boolean isHandlePage(String value){return value!=null&&Arrays.asList("left","right","extra_left","extra_right","extra2_left","extra2_right").contains(value);}
 private final class ActionChooser {
  final String key,owner;final int depth;int savedY;ScrollView scroll;replica.ColorActionSheet sheet;ColorSettingsScaffold scaffold;
  final HashMap<String,ColorSwatch> swatches=new HashMap<>();
  final HashMap<String,Runnable> summaries=new HashMap<>();
  final ArrayList<Runnable> bindings=new ArrayList<>();
  ActionChooser(String key,String owner,int depth){this.key=key;this.owner=owner;this.depth=depth;}
  boolean atOwner(){return screen.equals(owner)&&history.size()==depth;}
  int position(){return scroll!=null&&scroll.isLaidOut()?scroll.getScrollY():savedY;}
  void refresh(){for(Runnable bind:bindings)bind.run();if(sheet!=null)sheet.refreshContent();}
 }
 private void dismissActionSheet(){
  chooser=null;replica.ColorActionSheet old=actionSheet;actionSheet=null;if(old!=null)old.dismissImmediately();
 }
 private void invalidateChooserView(){
  ActionChooser state=chooser;if(state==null)return;state.savedY=state.position();replica.ColorActionSheet old=state.sheet;
  state.sheet=null;state.scroll=null;state.scaffold=null;state.bindings.clear();state.swatches.clear();state.summaries.clear();actionSheet=null;if(old!=null)old.dismissImmediately();
 }
 private void chooseAction(String key){dismissActionSheet();ActionChooser state=new ActionChooser(key,screen,history.size());chooser=state;buildActionChooser(state,false);}
 private void restoreActionChooser(){
  ActionChooser state=chooser;if(!uiActive||state==null||!state.atOwner())return;
  if(isFinishing()||isDestroyed())return;
  if(state.sheet==null)buildActionChooser(state,true);else{state.refresh();state.sheet.resume();}
 }
 private void enterChooserSettings(ActionChooser state,String destination,Runnable prepare){
  if(chooser!=state||state.sheet==null||!state.atOwner()||!state.sheet.suspend())return;
  state.savedY=state.scroll.getScrollY();if(prepare!=null)prepare.run();page(destination);
 }
 private void buildActionChooser(ActionChooser state,boolean restoring){
  state.scaffold=scaffold;state.swatches.clear();state.swatches.putAll(colorSwatches);state.summaries.clear();state.summaries.putAll(directionSummaries);
  LinearLayout list=new LinearLayout(this);list.setOrientation(LinearLayout.VERTICAL);list.setPadding(dp(16),dp(8),dp(16),dp(12));
  ScrollView scroll=new replica.ColorSettingsScrollView(this);scroll.setClipToPadding(false);scroll.addView(list);state.scroll=scroll;
  replica.ColorActionSheet sheet=new replica.ColorActionSheet(this).setTitle("选择操作").setView(scroll).setNegativeButton("取消",null);state.sheet=sheet;state.bindings.clear();
  for(ActionCatalog.Group group:ActionCatalog.groups()){
   if(!group.title.isEmpty())actionSection(list,group.title);
   for(String id:group.ids){
    String destination=ActionCatalog.destination(id);
    ActionChoice row=actionChoiceRow(list,ActionExecutor.title(id),destination!=null,
     ()->sheet.dismissThen(()->selectAction(state.key,id)),
     destination==null?null:()->enterChooserSettings(state,destination,null));
    state.bindings.add(()->row.update(id.equals(prefs.getString(state.key,"none")),null));
   }
  }
  for(boolean shortcut:new boolean[]{false,true}){
   String title=shortcut?"应用快捷方式":"启动指定应用";
   ActionChoice row=actionChoiceRow(list,title,true,
    ()->{String target=ActionTarget.get(prefs,state.key,shortcut);if(target.isEmpty())configureExternalAction(state,shortcut,true);else sheet.dismissThen(()->selectAction(state.key,target));},
    ()->configureExternalAction(state,shortcut,false));
   state.bindings.add(()->{String target=ActionTarget.get(prefs,state.key,shortcut);row.update(ActionTarget.matches(prefs.getString(state.key,"none"),shortcut),target.isEmpty()?null:externalActionLabel(target));});
  }
  state.refresh();sheet.setOnDismissListener(()->{if(actionSheet==sheet)actionSheet=null;if(chooser==state&&state.sheet==sheet)chooser=null;if(uiActive&&state.atOwner())refreshGeometryPreview();});actionSheet=sheet;
  if(restoring){
   // ScrollView applies this before its first draw; the restored sheet has no entrance animation.
   scroll.addOnLayoutChangeListener(new View.OnLayoutChangeListener(){public void onLayoutChange(View v,int l,int t,int r,int b,int ol,int ot,int or,int ob){if(scroll.getHeight()>0&&list.getHeight()>0){scroll.removeOnLayoutChangeListener(this);scroll.scrollTo(0,state.savedY);}}});
   sheet.showEmbeddedRestored(scaffold.root);
  }else sheet.showEmbedded(scaffold.root);
 }
 private void actionSection(LinearLayout list,String title){
  TextView label=text(title,13,ColorOsTheme.secondary(this));label.setTypeface(ColorOsTheme.typeface(this,500));
  label.setPadding(dp(12),dp(8),dp(12),dp(3));label.setAccessibilityHeading(true);list.addView(label);
 }
 private final class ActionChoice {
  final String name;final LinearLayout row,choice;final TextView summary;final ColorRadioButton radio;
  ActionChoice(String name,LinearLayout row,LinearLayout choice,TextView summary,ColorRadioButton radio){this.name=name;this.row=row;this.choice=choice;this.summary=summary;this.radio=radio;}
  void update(boolean selected,String detail){
   radio.setChecked(selected);boolean hasDetail=detail!=null&&!detail.isEmpty();summary.setText(hasDetail?detail:"");summary.setVisibility(hasDetail?View.VISIBLE:View.GONE);
   row.setMinimumHeight(dp(hasDetail?68:56));choice.setContentDescription(name+(hasDetail?"，"+detail:""));choice.setStateDescription(selected?"已选择":null);
  }
 }
 private ActionChoice actionChoiceRow(LinearLayout list,String name,boolean configurable,Runnable select,Runnable configure){
  LinearLayout r=new LinearLayout(this);r.setBaselineAligned(false);r.setGravity(Gravity.CENTER_VERTICAL);r.setPadding(dp(8),dp(2),dp(8),dp(2));r.setMinimumHeight(dp(56));
  ColorRadioButton radio=new ColorRadioButton(this);radio.setClickable(false);radio.setFocusable(false);radio.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);
  LinearLayout choice=new LinearLayout(this){
   @Override public CharSequence getAccessibilityClassName(){return RadioButton.class.getName();}
   @Override public void onInitializeAccessibilityNodeInfo(android.view.accessibility.AccessibilityNodeInfo info){super.onInitializeAccessibilityNodeInfo(info);info.setCheckable(true);info.setChecked(radio.isChecked());}
  };
  choice.setBaselineAligned(false);choice.setGravity(Gravity.CENTER_VERTICAL);choice.setMinimumHeight(dp(52));choice.setFocusable(true);
  Runnable choiceStyle=()->choice.setBackground(ColorDialogBuilder.touchBackground(this,Color.TRANSPARENT,16));choiceStyle.run();ColorOsTheme.watch(choice,choiceStyle);
  choice.addView(radio,new LinearLayout.LayoutParams(dp(40),dp(48)));
  LinearLayout labels=new LinearLayout(this);labels.setOrientation(LinearLayout.VERTICAL);labels.setPadding(0,dp(6),dp(8),dp(6));
  TextView title=text(name,16,configurable?ColorOsTheme.accent(this):ColorOsTheme.text(this));title.setTypeface(ColorOsTheme.typeface(this,500));title.setLineSpacing(0,1.08f);title.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);labels.addView(title);
  TextView detail=text("",13,ColorOsTheme.secondary(this));detail.setMaxLines(1);detail.setEllipsize(android.text.TextUtils.TruncateAt.END);detail.setPadding(0,dp(2),0,0);detail.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);detail.setVisibility(View.GONE);labels.addView(detail);
  choice.addView(labels,new LinearLayout.LayoutParams(0,-2,1));choice.setOnClickListener(v->select.run());r.addView(choice,new LinearLayout.LayoutParams(0,-2,1));
  if(configure!=null){
   ImageView setting=icon("status_bar_qs_footer_settings_icon_no_theme","设置"+name,true);setting.setPadding(dp(14),dp(14),dp(14),dp(14));setting.setFocusable(true);setting.setBackground(ColorDialogBuilder.touchBackground(this,Color.TRANSPARENT,24));setting.setOnClickListener(v->configure.run());r.addView(setting,new LinearLayout.LayoutParams(dp(48),dp(48)));
  }
  list.addView(r,new LinearLayout.LayoutParams(-1,-2));return new ActionChoice(name,r,choice,detail,radio);
 }
 private String externalActionLabel(String target){if(ShortcutEntry.valid(target))return ShortcutPicker.label(this,target);try{return getPackageManager().getActivityInfo(ComponentName.unflattenFromString(target.substring(4)),0).loadLabel(getPackageManager()).toString();}catch(Exception unavailable){return "应用不可用";}}
 private void selectAction(String key,String target){ActionTarget.select(prefs,key,target);reload();render();}
 private boolean isExternalActionPage(){return screen.equals("action_app")||screen.equals("action_shortcut");}
 private void closeShortcutPage(){if(shortcutSession!=null){ShortcutPicker.Session old=shortcutSession;shortcutSession=null;old.close();}}
 private void configureExternalAction(ActionChooser state,boolean shortcut,boolean select){
  String destination=shortcut?"action_shortcut":"action_app";
  enterChooserSettings(state,destination,()->{externalGestureKey=state.key;externalSelect=select;scrollPositions.remove(destination);});
 }
 private void finishExternalAction(String key,boolean shortcut,String target,boolean select){
  if(!uiActive||!isExternalActionPage()||!Objects.equals(key,externalGestureKey))return;
  if(select)dismissActionSheet();
  ActionTarget.configure(prefs,key,shortcut,target,select);reload();onBackPressed();
 }
 private void applicationActionSettings(){
  final String key=externalGestureKey;final boolean select=externalSelect;final int generation=renderGeneration,iconSize=dp(40);
  LinearLayout list=card();TextView loading=text("正在读取应用…",15,secondary);loading.setPadding(dp(16),dp(16),dp(16),dp(16));list.addView(loading);
  io.execute(()->{
   ArrayList<AppDisplay> apps;try{apps=loadAppDisplays(false,iconSize);}catch(RuntimeException error){runOnUiThread(()->{if(generation==renderGeneration&&screen.equals("action_app"))loading.setText("无法读取应用列表");});return;}
   runOnUiThread(()->{
    if(!screen.equals("action_app")||generation!=renderGeneration||isFinishing()||isDestroyed()){recycleUnboundApps(apps);return;}
    list.removeAllViews();String selected=ActionTarget.get(prefs,key,false);
    for(AppDisplay app:apps){
     String action="app:"+app.component.flattenToString();LinearLayout r=row(list,app.label,null);r.setMinimumHeight(dp(72));r.setPadding(dp(16),dp(12),dp(12),dp(12));bindAppIcon(r,app,iconSize);
     r.setBackground(ColorDialogBuilder.touchBackground(this,Color.TRANSPARENT,16));ColorRadioButton chosen=new ColorRadioButton(this);chosen.setChecked(action.equals(selected));chosen.setClickable(false);chosen.setFocusable(false);chosen.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);r.addView(chosen);
     r.setFocusable(true);r.setContentDescription(app.label);r.setStateDescription(action.equals(selected)?"已选择":null);
     r.setOnClickListener(v->{if(generation==renderGeneration)finishExternalAction(key,false,action,select);});
    }
    if(apps.isEmpty()){loading.setText("没有可用的应用");list.addView(loading);}
   });
  });
 }
 private void shortcutActionSettings(){
  final String key=externalGestureKey;final boolean select=externalSelect;final int generation=renderGeneration;
  ShortcutPicker.Session session=ShortcutPicker.embedded(this,ActionTarget.get(prefs,key,true),action->{
   if(uiActive&&generation==renderGeneration&&screen.equals("action_shortcut"))finishExternalAction(key,true,action,select);
  });
  shortcutSession=session;content.addView(session.view(),new LinearLayout.LayoutParams(-1,-2));session.start();
 }
 private void slider(LinearLayout box,String name,String key,float min,float max,float def,boolean integer,String unit){
  slider(box,name,key,min,max,def,integer,unit,null);
 }
 private void slider(LinearLayout box,String name,String key,float min,float max,float def,boolean integer,String unit,String description){slider(box,name,key,min,max,def,integer,unit,description,null,null);}
 private void slider(LinearLayout box,String name,String key,float min,float max,float def,boolean integer,String unit,String description,String low,String high){
  LinearLayout outer=sliderBody(box,name,description);float value=integer?prefs.getInt(key,(int)def):prefs.getFloat(key,def);TextView number=sliderEndpoints(outer,name,low==null?sliderValue(key,min,unit,integer):low,high==null?sliderValue(key,max,unit,integer):high,sliderValue(key,value,unit,integer));ColorSlider seek=new ColorSlider(this);int steps=integer&&max-min<=255?Math.max(1,Math.round(max-min)):100;seek.setMax(steps);seek.setProgress(Math.round((value-min)/(max-min)*steps));seek.setContentDescription(name);seek.setStateDescription(number.getText());outer.addView(seek,settingsSliderLayout());
  final boolean[] cancelled={false},tracking={false};seek.setOnTouchListener((v,e)->{if(e.getActionMasked()==MotionEvent.ACTION_DOWN)cancelled[0]=false;if(e.getActionMasked()==MotionEvent.ACTION_CANCEL)cancelled[0]=true;return false;});
  seek.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener(){float current=value,committed=value;private void save(){float adjusted=normalizeNumber(key,current);if(integer)prefs.edit().putInt(key,Math.round(adjusted)).apply();else prefs.edit().putFloat(key,adjusted).apply();sliderNumber(number,seek,sliderValue(key,adjusted,unit,integer));committed=adjusted;if(adjusted!=current)toast("已保持有效的手势范围");reload();}
   public void onProgressChanged(SeekBar b,int p,boolean user){current=min+(max-min)*p/steps;sliderNumber(number,seek,sliderValue(key,current,unit,integer));if(user){if(!tracking[0])save();}}
   public void onStartTrackingTouch(SeekBar b){tracking[0]=true;cancelled[0]=false;}
   public void onStopTrackingTouch(SeekBar b){tracking[0]=false;if(!sliderCancelled(b,cancelled[0]))save();else{seek.setProgress(Math.round((committed-min)/(max-min)*steps));}}
  });
 }
 private boolean sliderCancelled(SeekBar bar,boolean eventCancelled){return eventCancelled||!uiActive||(bar instanceof ColorSlider&&((ColorSlider)bar).isTouchCancelled());}
 private String sliderValue(String key,float value,String unit,boolean integer){return key.equals("vibration_strength")?SettingsDisplay.vibrationPercent(Math.round(value))+"%":format(value,unit,integer);}
 private float normalizeNumber(String key,float value){
  if(key.equals("trigger_dp"))return Math.min(value,prefs.getFloat("long_trigger_dp",87.43f));
  if(key.equals("long_trigger_dp"))return Math.max(value,prefs.getFloat("trigger_dp",39.43f));
  for(String side:GestureHandleConfig.IDS){boolean extra=GestureHandleConfig.isExtra(side);
   if(key.equals(side+"_handle_start_fraction"))return Math.min(value,prefs.getFloat(side+"_handle_end_fraction",extra?GestureHandleConfig.defaultEnd(side):prefs.getFloat("handle_end_fraction",1f))-.05f);
   if(key.equals(side+"_handle_end_fraction"))return Math.max(value,prefs.getFloat(side+"_handle_start_fraction",extra?GestureHandleConfig.defaultStart(side):prefs.getFloat("handle_start_fraction",.13084841f))+.05f);
  }return value;
 }
 private String format(float value,String unit,boolean integer){return SettingsDisplay.number(value,unit,integer);}
 private void advanced(){LinearLayout g=card();link(g,"应用例外","在选定应用中停用手势",()->page("exceptions"));g=group("隐藏触钮");toggle(g,"横屏模式","","hide_landscape",false);toggle(g,"快捷面板","通知面板展开时隐藏","hide_notifications",false);toggle(g,"主屏幕","","hide_home",false);toggle(g,"键盘显示时隐藏","","hide_keyboard",prefs.getBoolean("hide_when_keyboard",false));}
 private void gestureSettings(){
  LinearLayout g=group("短距离滑动");toggle(g,"快捷操作","开始操作后再从屏幕上松开手指","quick_action_short",false);link(g,"手势角度",null,()->page("angle"));slider(g,"滑动距离","trigger_dp",15,100,39.43f,false,"dp",null,"短","长");
  if(prefs.getBoolean("left_long_enabled",true)||prefs.getBoolean("right_long_enabled",true)){g=group("长距离滑动");toggle(g,"快捷操作",null,"quick_action_long",true);slider(g,"滑动距离","long_trigger_dp",40,180,87.43f,false,"dp",null,"短","长");valueSlider(g,"长距离滑动持续时间",prefs.getBoolean("long_immediate",false)?0:prefs.getInt("long_delay_ms",300),0,1000,"ms","短","长",v->prefs.edit().putInt("long_delay_ms",Math.round(v)).putBoolean("long_immediate",false).apply());}
  vibrationSettings(group("振动"));
 }
 private android.content.SharedPreferences.Editor hapticEdit(){
  android.content.SharedPreferences.Editor edit=prefs.edit();if(GestureHaptics.legacyMuted(prefs.getAll()))edit.putBoolean("custom_vibration",true).putInt("vibration_duration_ms",0);return edit.putBoolean("vibration",true);
 }
 private void vibrationSettings(LinearLayout group){
  LinearLayout quickRow=row(group,"快速振动","识别到手势时立即振动，而不是等到手指从屏幕上松开。");ColorSwitch quick=new ColorSwitch(this);quick.setChecked(GestureHaptics.quick(prefs.getAll()));quick.setContentDescription("快速振动");quickRow.addView(quick);
  LinearLayout customRow=row(group,"自定义振动强度","为"+appName()+" 设置自定义振动强度等级。");ColorSwitch custom=new ColorSwitch(this);custom.setChecked(GestureHaptics.custom(prefs.getAll()));custom.setContentDescription("自定义振动强度");customRow.addView(custom);
  divider(group);View line=group.getChildAt(group.getChildCount()-1);LinearLayout strength=new LinearLayout(this);strength.setOrientation(1);group.addView(strength);final boolean[] syncing={false};
  Runnable bindStrength=()->{strength.removeAllViews();boolean on=GestureHaptics.custom(prefs.getAll());syncing[0]=true;custom.setChecked(on);syncing[0]=false;if(on)editableSlider(strength,"振动级别",null,"振动级别",GestureHaptics.level(prefs.getAll()),0,30,30,"低","高",v->SettingsDisplay.vibrationLevelPercent(v)+"%",v->hapticEdit().putInt("vibration_duration_ms",Math.round(v)).apply());line.setVisibility(on?View.VISIBLE:View.GONE);strength.setVisibility(on?View.VISIBLE:View.GONE);};bindStrength.run();
  quick.setOnCheckedChangeListener((v,on)->{hapticEdit().putBoolean("quick_vibration",on).apply();beginListChange(content);bindStrength.run();reload();});quickRow.setOnClickListener(v->quick.setChecked(!quick.isChecked()));
  custom.setOnCheckedChangeListener((v,on)->{if(syncing[0])return;hapticEdit().putBoolean("custom_vibration",on).apply();beginListChange(content);bindStrength.run();reload();});customRow.setOnClickListener(v->custom.setChecked(!custom.isChecked()));
 }
 private void angleSettings(){
  ArrayList<AngleEditorView> editors=new ArrayList<>();ArrayList<ImageView> mirrors=new ArrayList<>();
  Runnable showDirection=()->{for(AngleEditorView editor:editors){FrameLayout.LayoutParams ep=(FrameLayout.LayoutParams)editor.getLayoutParams();ep.leftMargin=angleMirrored?dp(56):0;ep.rightMargin=angleMirrored?0:dp(56);editor.setLayoutParams(ep);editor.setMirrored(angleMirrored);}for(int i=0;i<mirrors.size();i++){ImageView button=mirrors.get(i);FrameLayout.LayoutParams lp=(FrameLayout.LayoutParams)button.getLayoutParams();lp.gravity=(angleMirrored?Gravity.LEFT:Gravity.RIGHT)|Gravity.CENTER_VERTICAL;button.setLayoutParams(lp);button.setRotation(angleMirrored?180:0);button.setContentDescription((i==0?"短滑":"长滑")+(angleMirrored?"从左侧查看":"从右侧查看"));}};
  for(boolean longer:new boolean[]{false,true}){
   LinearLayout group=group(longer?"长距离滑动":"短距离滑动");float[] values=new float[4];for(int index=0;index<4;index++)values[index]=GestureAngles.value(prefs.getAll(),longer,index);
   FrameLayout frame=new FrameLayout(this);AngleEditorView editor=new AngleEditorView(this,longer,values,(index,value)->{prefs.edit().putFloat(GestureAngles.key(longer,index),value).apply();reload();});editors.add(editor);frame.addView(editor,new FrameLayout.LayoutParams(-1,-2));
   ImageView mirror=icon("status_bar_qs_back_arrow",null);mirror.setPadding(dp(13),dp(13),dp(13),dp(13));mirror.setFocusable(true);Runnable mirrorStyle=()->mirror.setBackground(ColorDialogBuilder.touchBackground(this,ColorOsTheme.fill(this),24));mirrorStyle.run();ColorOsTheme.watch(mirror,mirrorStyle);mirror.setOnClickListener(v->{angleMirrored=!angleMirrored;showDirection.run();});FrameLayout.LayoutParams lp=new FrameLayout.LayoutParams(dp(48),dp(48),Gravity.RIGHT|Gravity.CENTER_VERTICAL);lp.leftMargin=dp(4);lp.rightMargin=dp(4);frame.addView(mirror,lp);mirrors.add(mirror);group.addView(frame,new LinearLayout.LayoutParams(-1,-2));
  }
  showDirection.run();
 }
 private void toolsSettings(){LinearLayout g=card();link(g,"编辑按钮","调整三排按钮顺序，添加系统开关和应用",()->open(new Intent(this,replica.ToolsSettingsActivity.class)));link(g,"预览快速工具",null,()->preview("quick_tools"));}
 private void widgetSettings(){android.content.SharedPreferences store=getSharedPreferences("replica_widgets",0);LinearLayout g=card();storeToggle(g,"循环翻页","从最后一页继续翻到第一页",store.getBoolean("loop",false),on->store.edit().putBoolean("loop",on).apply());g=card();link(g,"打开小组件面板","长按编辑页面和组件",()->preview("widgets"));}
 private interface BooleanEdit{void save(boolean value);}
 private void storeToggle(LinearLayout box,String title,String detail,boolean checked,BooleanEdit edit){LinearLayout r=row(box,title,detail);ColorSwitch sw=new ColorSwitch(this);sw.setChecked(checked);sw.setContentDescription(title);r.addView(sw);sw.setOnCheckedChangeListener((v,on)->{edit.save(on);renderedPageData=pageData();});r.setOnClickListener(v->sw.setChecked(!sw.isChecked()));}
 private void launcherSettings(){
  android.content.SharedPreferences store=getSharedPreferences("edgeflow_launcher",0);LinearLayout g=card();storeToggle(g,"显示最近应用页面",null,LauncherLayout.load(store).recent,on->{LauncherLayout current=LauncherLayout.load(store);current.recent=on;current.save(store);});
  int columns=store.getInt("columns",4);link(g,"列数",columns+" 列",()->page("launcher_columns"));g=card();link(g,"编辑快速启动","添加应用、快捷方式和文件夹",()->preview("quick_launcher"));
 }
 private void launcherColumns(){
  android.content.SharedPreferences store=getSharedPreferences("edgeflow_launcher",0);int columns=store.getInt("columns",4);LinearLayout list=card();
  for(int value:new int[]{2,3,4,5}){if(value==5&&columns!=5)continue;ActionChoice row=actionChoiceRow(list,value+" 列",false,()->{store.edit().putInt("columns",value).apply();onBackPressed();},null);row.update(value==columns,null);}
 }
 private void taskSettings(){android.content.SharedPreferences store=getSharedPreferences("edgeflow_launcher",0);LinearLayout g=group("显示任务数量");int selected=store.getInt("recent_limit",10);ArrayList<Integer> counts=new ArrayList<>(Arrays.asList(8,9,10,11,12,13));if(!counts.contains(selected))counts.add(selected);for(int n:counts){LinearLayout r=row(g,n+" 个任务",n<8||n>13?"保留的现有配置":null);ColorRadioButton radio=new ColorRadioButton(this);radio.setChecked(selected==n);radio.setClickable(false);r.addView(radio);r.setOnClickListener(v->{store.edit().putInt("recent_limit",n).apply();render();});}g=card();link(g,"预览任务切换器",null,()->preview("task_switcher"));}
 private void keyboardSettings(){note("键盘快捷方式按应用分别保存。打开要操作的应用后，用手势调出此面板，再添加或编辑组合按键。");LinearLayout g=card();link(g,"预览键盘快捷方式","查看当前应用的组合按键",()->preview("keyboard_shortcuts"));}

 private void touchpadSettings(){LinearLayout g=group("光标");slider(g,"移动速度","touchpad_speed",.5f,3f,1.7f,false,"×");link(g,"预览触控板","调整后下次打开生效",()->preview("virtual_touchpad"));}
 private void navigationSettings(){
  LinearLayout g=group("外观");toggle(g,"纵向排列","关闭时横向排列","navigation_vertical",false);slider(g,"按键大小","navigation_button_size_dp",40,76,58,false,"dp");
  g=group("按键顺序");String[] actions=NavigationConfig.load(this);
  for(int i=0;i<actions.length;i++){
   final int index=i;String label=(i+1)+". "+ActionExecutor.title(actions[i]);LinearLayout r=row(g,label,null);
   ImageView settings=icon("status_bar_qs_footer_settings_icon_no_theme","编辑"+label,false);settings.setPadding(dp(14),dp(14),dp(14),dp(14));settings.setFocusable(true);settings.setBackground(ColorDialogBuilder.touchBackground(this,Color.TRANSPARENT,24));
   r.addView(settings,new LinearLayout.LayoutParams(dp(48),dp(48)));r.setBackground(ColorDialogBuilder.touchBackground(this,Color.TRANSPARENT,16));r.setContentDescription(label);r.setOnClickListener(v->editNavigation(settings,index));settings.setOnClickListener(v->editNavigation(settings,index));
  }
  if(actions.length<5)link(g,"添加按键","最多 5 个不同动作",()->chooseNavigation(-1));
  g=card();link(g,"预览导航键",null,()->preview("navigation_panel"));
 }
 private ArrayList<String> navigationActions(){return new ArrayList<>(Arrays.asList(NavigationConfig.load(this)));}
 private boolean saveNavigation(ArrayList<String> actions){try{prefs.edit().putString("navigation_actions",NavigationConfig.validate(new JSONArray(actions).toString())).apply();reload();return true;}catch(Exception e){toast("无法保存导航键："+e.getMessage());return false;}}
 private void editNavigation(View anchor,int index){
  dismissMenu();ArrayList<String> actions=navigationActions();if(index<0||index>=actions.size())return;
  ArrayList<String> labels=new ArrayList<>();labels.add("更换动作");if(index>0)labels.add("前移");if(index+1<actions.size())labels.add("后移");if(actions.size()>1)labels.add("移除");
  ColorPopupMenu menu=new ColorPopupMenu(anchor).setItems(labels.toArray(new String[0]),n->{
   String command=labels.get(n);if(command.equals("更换动作")){chooseNavigation(index);return;}
   ArrayList<String> current=navigationActions();if(index>=current.size())return;
   if(command.equals("移除")){if(current.size()==1)return;current.remove(index);}else{int next=index+(command.equals("前移")?-1:1);if(next<0||next>=current.size())return;Collections.swap(current,index,next);}
   if(saveNavigation(current))render();
  });
  int remove=labels.indexOf("移除");if(remove>=0)menu.setDividerBefore(remove).setDestructive(remove);openMenu=menu.show();
 }
 private void chooseNavigation(int index){navigationEditIndex=index;scrollPositions.remove("navigation_action");page("navigation_action");}
 private void navigationActionSettings(){
  final int index=navigationEditIndex;ArrayList<String> current=navigationActions();String selected=index>=0&&index<current.size()?current.get(index):"";
  HashSet<String> allowed=new HashSet<>(Arrays.asList(NavigationConfig.IDS));LinearLayout list=card();
  for(ActionCatalog.Group group:ActionCatalog.groups()){
   ArrayList<String> visible=new ArrayList<>();for(String id:group.ids)if(allowed.contains(id)&&(!current.contains(id)||id.equals(selected)))visible.add(id);
   if(visible.isEmpty())continue;if(!group.title.isEmpty())actionSection(list,group.title);
   for(String id:visible){ActionChoice choice=actionChoiceRow(list,ActionExecutor.title(id),false,()->{
    ArrayList<String> values=navigationActions();if(index<0){if(values.size()>=5||values.contains(id))return;values.add(id);}else{if(index>=values.size()||values.contains(id)&&!values.get(index).equals(id))return;values.set(index,id);}
    if(saveNavigation(values))onBackPressed();
   },null);choice.update(id.equals(selected),null);}
  }
 }
 private static final class AppDisplay{
  final String packageName,label;final ComponentName component;final android.graphics.Bitmap icon;
  AppDisplay(String packageName,String label,ComponentName component,android.graphics.Bitmap icon){this.packageName=packageName;this.label=label;this.component=component;this.icon=icon;}
 }
 private android.graphics.Bitmap appIcon(android.graphics.drawable.Drawable drawable,int size){
  android.graphics.Bitmap bitmap=android.graphics.Bitmap.createBitmap(size,size,android.graphics.Bitmap.Config.ARGB_8888);
  try{drawable=drawable.mutate();drawable.setBounds(0,0,size,size);drawable.draw(new android.graphics.Canvas(bitmap));return bitmap;}catch(RuntimeException error){bitmap.recycle();throw error;}
 }
 private void recycleUnboundApps(List<AppDisplay> apps){HashSet<android.graphics.Bitmap> seen=new HashSet<>();for(AppDisplay app:apps)if(app.icon!=null&&seen.add(app.icon)&&!app.icon.isRecycled())app.icon.recycle();}
 /** Worker-only loader. Returned bitmaps have the display size, not the APK icon size. */
 private ArrayList<AppDisplay> loadAppDisplays(boolean uniquePackages,int iconSize){
  ArrayList<AppDisplay> apps=new ArrayList<>();PackageManager manager=getPackageManager();HashSet<String> seen=new HashSet<>();
  try{
   for(ResolveInfo resolved:manager.queryIntentActivities(new Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER),0)){
    ActivityInfo activity=resolved.activityInfo;if(activity==null||activity.applicationInfo==null||activity.name==null)continue;String pkg=activity.packageName;if(pkg==null||(uniquePackages&&pkg.equals(getPackageName())))continue;ComponentName component=new ComponentName(pkg,activity.name);if(!seen.add(uniquePackages?pkg:component.flattenToString()))continue;
    ApplicationInfo info=activity.applicationInfo;if(!uniquePackages&&(!activity.enabled||!activity.exported||!info.enabled))continue;String label=pkg;android.graphics.Bitmap icon=null;
    try{CharSequence title=uniquePackages?info.loadLabel(manager):resolved.loadLabel(manager);if(title!=null&&!title.toString().isBlank())label=title.toString();}catch(RuntimeException ignored){}
    try{icon=appIcon(uniquePackages?info.loadIcon(manager):resolved.loadIcon(manager),iconSize);}catch(RuntimeException ignored){try{icon=appIcon(manager.getDefaultActivityIcon(),iconSize);}catch(RuntimeException unavailable){}}
    apps.add(new AppDisplay(pkg,label,component,icon));
   }
   java.text.Collator order=java.text.Collator.getInstance(getResources().getConfiguration().getLocales().get(0));apps.sort((a,b)->{int result=order.compare(a.label,b.label);return result!=0?result:a.component.flattenToString().compareTo(b.component.flattenToString());});return apps;
  }catch(RuntimeException error){recycleUnboundApps(apps);throw error;}
 }
 private void bindAppIcon(LinearLayout row,AppDisplay app,int iconSize){ImageView image=new ImageView(this);image.setImageBitmap(app.icon);image.setScaleType(ImageView.ScaleType.FIT_CENTER);image.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);LinearLayout.LayoutParams params=new LinearLayout.LayoutParams(iconSize,iconSize);params.rightMargin=dp(12);row.addView(image,0,params);}
 private void excludedApps(){
  final int generation=renderGeneration,iconSize=dp(40);LinearLayout g=group("在这些应用中停用手势");TextView loading=text("正在读取应用…",15,secondary);loading.setPadding(dp(16),dp(10),dp(16),dp(10));g.addView(loading);
  io.execute(()->{
   final ArrayList<AppDisplay> apps;try{apps=loadAppDisplays(true,iconSize);}catch(RuntimeException error){runOnUiThread(()->{if(screen.equals("exceptions")&&!isFinishing()&&!isDestroyed()&&generation==renderGeneration)loading.setText("无法读取应用列表");});return;}
   runOnUiThread(()->{
    if(!screen.equals("exceptions")||isFinishing()||isDestroyed()||generation!=renderGeneration){recycleUnboundApps(apps);return;}
    g.removeAllViews();
    for(AppDisplay app:apps){
     String pkg=app.packageName;LinearLayout r=row(g,app.label,null);bindAppIcon(r,app,iconSize);
     ColorCheckBox check=new ColorCheckBox(this);check.setContentDescription("在“"+app.label+"”中停用手势");check.setChecked(prefs.getStringSet("disabled_apps",Collections.emptySet()).contains(pkg));r.addView(check);
     check.setOnCheckedChangeListener((v,on)->{HashSet<String> set=new HashSet<>(prefs.getStringSet("disabled_apps",Collections.emptySet()));if(on)set.add(pkg);else set.remove(pkg);prefs.edit().putStringSet("disabled_apps",set).apply();reload();});r.setOnClickListener(v->check.setChecked(!check.isChecked()));
    }
   });
  });
 }
 private void openDocument(boolean export){Intent in=new Intent(export?Intent.ACTION_CREATE_DOCUMENT:Intent.ACTION_OPEN_DOCUMENT).setType("application/json").addCategory(Intent.CATEGORY_OPENABLE);if(export)in.putExtra(Intent.EXTRA_TITLE,"color-hand-operation-settings.json");startActivityForResult(in,export?41:42);}
 @Override protected void onActivityResult(int req,int result,Intent data){super.onActivityResult(req,result,data);if(result!=RESULT_OK||data==null||(req!=41&&req!=42))return;Uri uri=data.getData();if(uri==null)return;io.execute(()->{try{if(req==41){JSONObject obj=ConfigArchive.export(this);try(OutputStream out=getContentResolver().openOutputStream(uri,"wt")){if(out==null)throw new IOException("无法打开目标文件");out.write(obj.toString().getBytes(java.nio.charset.StandardCharsets.UTF_8));}}else{String raw;try(InputStream in=getContentResolver().openInputStream(uri)){raw=readLimited(in);}JSONObject obj=new JSONObject(raw);ConfigArchive.restore(this,obj);}runOnUiThread(()->{reload();if(isFinishing()||isDestroyed())return;render();toast(req==41?"已导出":"已导入");});}catch(Exception e){runOnUiThread(()->toast("配置操作失败："+e.getMessage()));}});}
 private static String readLimited(InputStream in)throws IOException{if(in==null)throw new IOException("无法打开文件");ByteArrayOutputStream out=new ByteArrayOutputStream();byte[] buffer=new byte[8192];int n;while((n=in.read(buffer))!=-1){if(out.size()+n>1024*1024)throw new IOException("文件过大");out.write(buffer,0,n);}return out.toString("UTF-8");}
 private void migrateWidgetsIfPresent(){final File input=new File(getFilesDir(),"migration-widgets.json");synchronized(MainActivity.class){if(migrationRunning||!input.isFile())return;migrationRunning=true;}
  io.execute(()->{JSONObject report;try(InputStream stream=new FileInputStream(input)){report=replica.WidgetMigration.migrate(getApplicationContext(),new JSONObject(readLimited(stream)));}catch(Exception e){report=new JSONObject();try{report.put("status","error").put("reason",e.toString());}catch(JSONException ignored){}}
   try{android.util.AtomicFile output=new android.util.AtomicFile(new File(getFilesDir(),"migration-widgets-report.json"));FileOutputStream out=null;try{out=output.startWrite();out.write(report.toString(2).getBytes(java.nio.charset.StandardCharsets.UTF_8));output.finishWrite(out);}catch(Exception e){if(out!=null)output.failWrite(out);throw e;}
    if(!input.renameTo(new File(getFilesDir(),"migration-widgets.processed.json")))android.util.Log.w("EdgeFlowMigration","Input remains; helper is idempotent");
   }catch(Exception e){android.util.Log.e("EdgeFlowMigration","Cannot persist migration report",e);}finally{synchronized(MainActivity.class){migrationRunning=false;}}
   runOnUiThread(()->{if(!isFinishing()&&!isDestroyed()&&screen.equals("home"))render();});
  });
 }
 private void showWidgetMigrationReport(){page("widget_migration");}
 private void widgetMigrationReport(){
  try(InputStream in=new FileInputStream(new File(getFilesDir(),"migration-widgets-report.json"))){
   JSONObject report=new JSONObject(readLimited(in));LinearLayout group=card();String state=report.optString("status");String title=switch(state){case "imported"->"已迁移";case "partial"->"部分迁移完成";case "destination_not_empty"->"已有小组件，未覆盖";case "nothing_imported"->"没有迁入的小组件";default->"未完成";};
   row(group,"迁移状态",title);row(group,"已迁移",Integer.toString(report.optInt("importedCount")));row(group,"失败",Integer.toString(report.optInt("failedCount")));row(group,"需要组件设置",Integer.toString(report.optInt("configurationCount")));
   JSONArray entries=report.optJSONArray("needsConfiguration");if(entries!=null&&entries.length()>0){group=group("需要设置的小组件");for(int i=0;i<entries.length();i++){String component=entries.getJSONObject(i).optString("component");ComponentName name=ComponentName.unflattenFromString(component);String label=component;try{if(name!=null)label=getPackageManager().getApplicationLabel(getPackageManager().getApplicationInfo(name.getPackageName(),0)).toString();}catch(Exception ignored){}row(group,label,component);}}
   note("长按小组件进入编辑，再点按组件选择“组件设置”。");
  }catch(Exception failure){note("无法读取迁移结果。");}
 }
 private void open(Intent intent){try{startActivity(intent);}catch(Exception e){toast("此系统入口不可用");}}
 private void toast(String message){if(!isFinishing()&&!isDestroyed())Toast.makeText(this,message,Toast.LENGTH_SHORT).show();}
 @Override public void onDestroy(){observePageData(false);dismissMenu();dismissActionSheet();dismissColorPalette();if(serviceReceiverRegistered){unregisterReceiver(serviceStateReceiver);serviceReceiverRegistered=false;}if(themeObserver!=null)try{themeObserver.close();}catch(Exception ignored){}endGeometryPreview();pageMotion.clear();if(backCallback!=null)getOnBackInvokedDispatcher().unregisterOnBackInvokedCallback(backCallback);if(shortcutSession!=null)shortcutSession.close();io.shutdown();super.onDestroy();}
}
