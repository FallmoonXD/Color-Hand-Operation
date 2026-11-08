package dev.edgeflow;

import android.accessibilityservice.AccessibilityService;
import android.app.AlertDialog;import android.app.Dialog;
import android.content.*;import android.content.pm.ResolveInfo;
import android.graphics.Color;import android.graphics.drawable.Drawable;
import android.os.Handler;import android.os.Looper;
import android.view.*;import android.widget.*;
import org.json.*;import java.util.*;import java.io.*;import java.util.concurrent.*;

/** Independent paged launcher: apps, actions, folders and real recent tasks. */
public final class QuickLauncherOverlay {
 private static QuickLauncherOverlay current;private static final ExecutorService WORK=Executors.newSingleThreadExecutor();private static final Handler MAIN=new Handler(Looper.getMainLooper());
 private final AccessibilityService service;private final Context context;private final SharedPreferences prefs;private final LauncherLayout layout;
 private final Dialog dialog;private final LinearLayout root;private final float[] anchor;
 private final ArrayList<View> hitViews=new ArrayList<>();private final ArrayList<String> hitKeys=new ArrayList<>(),recent=new ArrayList<>();private final ArrayList<AlertDialog> children=new ArrayList<>();private replica.ColorMotion motion;
 private ShortcutPicker.Session shortcutPicker;private replica.ColorPopupMenu popup;private int pickerGeneration;private long childDismissedAt;
 private final String directFolder;private int page,folderParentPage,columns,hovered=-1;private String folderId=null,recentError=null;private boolean editing,hoverMoved,recentLoading;private volatile boolean closed;private RecentTaskList.Request recentRequest;private boolean recentLoaded;
 private QuickLauncherOverlay(AccessibilityService s,float[] a,LauncherLayout layout,String folder){service=s;anchor=a.clone();prefs=s.getSharedPreferences("edgeflow_launcher",Context.MODE_PRIVATE);columns=Math.max(2,Math.min(5,prefs.getInt("columns",4)));this.layout=layout;directFolder=folder;page=prefs.getInt("page",0);
  if(folder!=null){folderParentPage=page;folderId=folder;page=0;}
  int theme=s.getResources().getIdentifier(replica.PanelStyle.dark(s)?"ReplicaGlassDark":"ReplicaGlass","style",s.getPackageName());context=new ContextThemeWrapper(s,theme);
  dialog=new replica.PanelOverlayDialog(context,this::closeFromOutside,this::closeFromOutside);
  root=new LinearLayout(context){float sx,sy;boolean swiping;final ParentTouchGate parentTouch=new ParentTouchGate();public boolean dispatchTouchEvent(MotionEvent e){return parentTouch.consume(e.getActionMasked(),e.getDownTime(),childDismissedAt,hasChildUi())||super.dispatchTouchEvent(e);}public boolean onInterceptTouchEvent(MotionEvent e){if(e.getActionMasked()==MotionEvent.ACTION_DOWN){sx=e.getX();sy=e.getY();swiping=false;}if(e.getActionMasked()==MotionEvent.ACTION_MOVE&&Math.abs(e.getX()-sx)>dp(35)&&Math.abs(e.getX()-sx)>Math.abs(e.getY()-sy)*1.5f){swiping=true;return true;}return false;}public boolean onTouchEvent(MotionEvent e){if(swiping){if(e.getActionMasked()==MotionEvent.ACTION_UP){turn(e.getX()<sx?1:-1);swiping=false;}if(e.getActionMasked()==MotionEvent.ACTION_CANCEL)swiping=false;return true;}return super.onTouchEvent(e);}};root.setOrientation(1);root.setPadding(dp(12),dp(8),dp(12),dp(10));dialog.setContentView(root);dialog.setOnDismissListener(d->cleanup());Window w=dialog.getWindow();w.setTitle(context.getString(dev.edgeflow.R.string.app_name)+" 快速启动");replica.PanelStyle.window(context,w,24);render();dialog.show();position();motion=new replica.ColorMotion(w);motion.enter();if(isRecent())loadRecent(false);
 }
 public static void show(AccessibilityService s,float[] anchor){showFolder(s,anchor,null);}
 static void showFolder(AccessibilityService s,float[] anchor,String folder){
  dismissAll();TaskSwitcherOverlay.dismissAll();
  try{
   LauncherLayout layout=LauncherLayout.load(s.getSharedPreferences("edgeflow_launcher",0));
   if(folder!=null&&!layout.folders.containsKey(folder)){Toast.makeText(s,"文件夹已删除",Toast.LENGTH_SHORT).show();return;}
   current=new QuickLauncherOverlay(s,anchor,layout,folder);
  }catch(RuntimeException failure){android.util.Log.w("EdgeLauncher","Cannot show launcher",failure);Toast.makeText(s,"快速启动暂不可用",Toast.LENGTH_SHORT).show();}
 }
 public static void openEditor(AccessibilityService service,float[] anchor){
  ActionExecutor.execute(service,"quick_launcher",anchor);
  QuickLauncherOverlay panel=current;
  if(panel!=null&&panel.service==service){
   panel.editing=true;if(panel.isRecent())panel.page=1;
   panel.render();panel.position();
  }
 }
 public static void dismissAll(){if(current!=null)current.remove();}
 private int dp(float n){return replica.PanelStyle.dp(context,n);}private int capacity(){return columns*3;}private boolean hasRecent(){return folderId==null&&layout.recent;}private boolean isRecent(){return hasRecent()&&page==0;}
 private ArrayList<String> items(){return folderId==null?layout.items:layout.folders.get(folderId).items;}
 private int pages(){return Math.max(1,(items().size()+capacity()-1)/capacity())+(hasRecent()?1:0);}
 private int offset(){return (page-(hasRecent()?1:0))*capacity();}
 private String component(String key){if(key.startsWith("app:"))return key.substring(4);if(key.startsWith("recent:")){int at=key.indexOf(':',7);return at<0?"":key.substring(at+1);}return "";}
 private String label(String key){if(key.startsWith(ShortcutEntry.PREFIX))return ShortcutPicker.label(service,key);if(key.startsWith("folder:")){LauncherLayout.Folder f=layout.folders.get(key.substring(7));return f==null?"文件夹":f.name;}if(key.startsWith("action:"))return ActionExecutor.title(key.substring(7));try{return service.getPackageManager().getActivityInfo(ComponentName.unflattenFromString(component(key)),0).loadLabel(service.getPackageManager()).toString();}catch(Exception e){return "应用不可用";}}
 private Drawable icon(String key){if(key.startsWith(ShortcutEntry.PREFIX))return ShortcutPicker.icon(service,key);if(key.startsWith("action:")){String id=key.substring(7);String[] map={"wifi","sound_mode","bluetooth","rotation","notifications","screen_record","screenshot","flashlight","dark_mode","screen_off","screen_timeout","close_app","mobile_data","airplane","battery_saver","location","dnd","nfc","settings","auto_brightness"};for(int i=0;i<map.length;i++)if(map[i].equals(id))return replica.SystemIcons.tile(context,i,false,2);if(id.equals("back"))return replica.SystemIcons.raw(context,"status_bar_qs_back_arrow");if(id.startsWith("volume_"))return replica.SystemIcons.control(context,id.equals("volume_up")?"ic_volume_up":"ic_volume_down");if(id.startsWith("media_"))return replica.SystemIcons.control(context,id.equals("media_previous")?"player_prev":id.equals("media_next")?"player_next":"player_play");return replica.SystemIcons.raw(context,"com.android.settings:ic_apps");}try{return service.getPackageManager().getActivityIcon(ComponentName.unflattenFromString(component(key)));}catch(Exception e){return service.getPackageManager().getDefaultActivityIcon();}}
 private TextView text(String label,int size){TextView t=new TextView(context);t.setText(label);t.setTextSize(size);Runnable style=()->{t.setTypeface(replica.ColorOsTheme.typeface(context,size>=18?600:500));t.setTextColor(replica.ColorOsTheme.text(context));};style.run();replica.ColorOsTheme.watch(t,style);t.setGravity(Gravity.CENTER);return t;}
 private boolean hasChildUi(){return popup!=null||shortcutPicker!=null||!children.isEmpty();}
 static final class ParentTouchGate {private boolean blocked;boolean consume(int action,long downTime,long dismissedAt,boolean childOpen){boolean modal=childOpen||dismissedAt>0&&downTime<=dismissedAt;if(action==MotionEvent.ACTION_DOWN)blocked=modal;else if(modal)blocked=true;boolean consume=blocked;if(action==MotionEvent.ACTION_UP||action==MotionEvent.ACTION_CANCEL)blocked=false;return consume;}}
 private TextView button(String label,Runnable action){TextView t=text(label,14);t.setMinHeight(dp(38));t.setPadding(dp(10),0,dp(10),0);t.setBackground(replica.ColorDialogBuilder.touchBackground(context,Color.TRANSPARENT,14));t.setOnClickListener(v->{if(!closed&&!hasChildUi())action.run();});return t;}
 private TextView anchoredButton(String label,View.OnClickListener action){TextView t=button(label,()->{});t.setOnClickListener(v->{if(!closed&&!hasChildUi())action.onClick(v);});return t;}
 private View iconView(String key){if(!key.startsWith("folder:")){ImageView icon=new ImageView(context);icon.setImageDrawable(icon(key));if(key.startsWith("action:"))icon.setColorFilter(replica.ColorOsTheme.text(context));return icon;}LinearLayout box=new LinearLayout(context);box.setOrientation(1);box.setPadding(dp(4),dp(4),dp(4),dp(4));box.setBackground(replica.PanelStyle.shape(context,0x30ffffff,10));LauncherLayout.Folder folder=layout.folders.get(key.substring(7));for(int row=0;row<2;row++){LinearLayout line=new LinearLayout(context);box.addView(line,new LinearLayout.LayoutParams(-1,0,1));for(int col=0;col<2;col++){int at=row*2+col;ImageView mini=new ImageView(context);mini.setPadding(dp(1),dp(1),dp(1),dp(1));if(folder!=null&&at<folder.items.size()){String child=folder.items.get(at);mini.setImageDrawable(icon(child));if(child.startsWith("action:"))mini.setColorFilter(replica.ColorOsTheme.text(context));}else{mini.setImageDrawable(replica.SystemIcons.raw(context,"com.android.settings:ic_add_24dp"));mini.setColorFilter(replica.ColorOsTheme.tertiary(context));}line.addView(mini,new LinearLayout.LayoutParams(0,-1,1));}}return box;}
 private android.graphics.drawable.Drawable tileBackground(boolean hover){
  return replica.ColorDialogBuilder.touchBackground(context,hover?(replica.ColorOsTheme.accent(context)&0xffffff)|0x66000000:replica.ColorOsTheme.card(context),14);
 }
 private void render(){hovered=-1;hitViews.clear();hitKeys.clear();root.removeAllViews();if(folderId!=null&&!layout.folders.containsKey(folderId))folderId=null;page=Math.max(0,Math.min(pages()-1,page));
  LinearLayout header=new LinearLayout(context);header.setGravity(Gravity.CENTER_VERTICAL);header.setPadding(dp(10),0,dp(4),0);replica.PanelStyle.headerCard(header,16);if(folderId!=null)header.addView(button("返回",()->{folderId=null;page=folderParentPage;render();position();}));TextView title=text(folderId==null?(isRecent()?"最近应用":editing?"编辑快速启动":"快速启动"):layout.folders.get(folderId).name,18);title.setGravity(Gravity.LEFT|Gravity.CENTER_VERTICAL);header.addView(title,new LinearLayout.LayoutParams(0,dp(46),1));if(!editing)header.addView(button("编辑",()->{editing=true;render();position();}));root.addView(header);
  ArrayList<String> visible=isRecent()?recent:items();int start=isRecent()?0:Math.max(0,offset());
  if(visible.isEmpty()){
   boolean recentPage=isRecent();String message=recentPage?(recentLoading?"正在读取最近任务…":recentError!=null?recentError:"暂无最近应用，点此刷新"):folderId!=null?"文件夹为空":"暂无常用入口";
   TextView empty=text(message,14);replica.PanelStyle.contentCard(empty,16,recentPage&&!recentLoading);
   if(recentPage&&!recentLoading)empty.setOnClickListener(v->loadRecent(true));
   LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(-1,dp(96));lp.topMargin=dp(6);root.addView(empty,lp);
  }
  else for(int row=0;row<(isRecent()||directPage()?(Math.min(capacity(),visible.size()-start)+columns-1)/columns:3);row++){
   LinearLayout line=new LinearLayout(context);
   if(directPage())line.setLayoutDirection(anchor[0]<replica.PanelGeometry.screen(context).x/2f?View.LAYOUT_DIRECTION_LTR:View.LAYOUT_DIRECTION_RTL);LinearLayout.LayoutParams rowParams=new LinearLayout.LayoutParams(-1,launcherRowHeight());rowParams.topMargin=dp(3);root.addView(line,rowParams);for(int col=0;col<columns;col++){int at=start+row*columns+col;if(at>=visible.size()){line.addView(new View(context),new LinearLayout.LayoutParams(0,-1,1));continue;}String key=visible.get(at);LinearLayout tile=new LinearLayout(context);tile.setLayoutDirection(context.getResources().getConfiguration().getLayoutDirection());tile.setOrientation(1);tile.setGravity(Gravity.CENTER);tile.setPadding(dp(3),dp(3),dp(3),dp(3));tile.setBackground(tileBackground(false));tile.setContentDescription(label(key));tile.addView(iconView(key),new LinearLayout.LayoutParams(dp(42),0,1));TextView name=text(label(key),11);name.setSingleLine();name.setEllipsize(android.text.TextUtils.TruncateAt.END);tile.addView(name,new LinearLayout.LayoutParams(-1,dp(23)));LinearLayout.LayoutParams tileParams=new LinearLayout.LayoutParams(0,-1,1);tileParams.setMargins(dp(2),0,dp(2),0);line.addView(tile,tileParams);tile.setOnClickListener(v->{if(closed||hasChildUi())return;if(editing)manage(key,v);else activate(key);});tile.setOnLongClickListener(v->{if(!closed&&!hasChildUi())manage(key,v);return true;});hitViews.add(tile);hitKeys.add(key);}}
  if(isRecent())root.addView(button("全部任务",()->ActionExecutor.execute(service,"task_switcher",anchor)),new LinearLayout.LayoutParams(-1,dp(44)));
  if(pages()>1){
   LinearLayout footer=new LinearLayout(context);footer.setGravity(Gravity.CENTER);replica.PanelStyle.contentCard(footer,16,false);
   footer.addView(button("上一页",()->turn(-1)),new LinearLayout.LayoutParams(0,dp(42),1));
   replica.PageDots dots=new replica.PageDots(context,pages(),page);
   dots.setClickable(false);dots.setContentDescription("第 "+(page+1)+" 页，共 "+pages()+" 页");
   footer.addView(dots,new LinearLayout.LayoutParams(dp(Math.min(pages(),9)*10+18),dp(24)));
   footer.addView(button("下一页",()->turn(1)),new LinearLayout.LayoutParams(0,dp(42),1));root.addView(footer);
  }
  if(editing){
   LinearLayout edit=new LinearLayout(context);edit.setGravity(Gravity.CENTER);replica.PanelStyle.contentCard(edit,20,false);
   edit.addView(anchoredButton("添加",this::addMenu),new LinearLayout.LayoutParams(0,dp(46),1));
   edit.addView(anchoredButton("选项",this::options),new LinearLayout.LayoutParams(0,dp(46),1));
   TextView done=button("完成",()->{editing=false;render();position();});done.setTextColor(replica.ColorOsTheme.accent(context));edit.addView(done,new LinearLayout.LayoutParams(0,dp(46),1));
   LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(-1,-2);lp.topMargin=dp(6);root.addView(edit,lp);
  }
 }
 private void turn(int delta){if(closed||hasChildUi()||pages()<2)return;page=(page+delta+pages())%pages();if(folderId==null)prefs.edit().putInt("page",page).apply();render();position();if(isRecent()&&!recentLoaded)loadRecent(false);}
 private int launcherRowHeight(){
  int available=replica.PanelGeometry.screen(context).y-replica.PanelGeometry.inset(context,"status_bar_height")-replica.PanelGeometry.inset(context,"navigation_bar_height");
  int chrome=dp(84+(editing?46:0)+(isRecent()?44:0)+(pages()>1?42:0));return Math.max(dp(48),Math.min(dp(82),(available-chrome)/3));
 }
 private boolean directPage(){return directFolder!=null&&directFolder.equals(folderId);}
 private void position(){
  android.graphics.Point screen=replica.PanelGeometry.screen(context);
  int width=Math.min(dp(320),screen.x-dp(28));
  GestureHandleConfig.Area area=null;
  if(directPage()){
   area=HandleDisplayArea.current(service);
   width=Math.min(Math.min(width,area.width()),Math.max(dp(206),dp(72)*Math.min(items().size(),columns)+dp(24)));
  }
  root.measure(View.MeasureSpec.makeMeasureSpec(width,View.MeasureSpec.EXACTLY),View.MeasureSpec.makeMeasureSpec(0,View.MeasureSpec.UNSPECIFIED));
  Window window=dialog.getWindow();replica.PanelGeometry.position(context,window,width,root.getMeasuredHeight(),false,anchor);
  if(area!=null){WindowManager.LayoutParams lp=window.getAttributes();int gap=Math.min(dp(8),area.width()-width);
   lp.x=anchor[0]<screen.x/2f?area.left+gap:area.right-width-gap;window.setAttributes(lp);}
 }
 private replica.ColorDialogBuilder builder(String title){return new replica.GlassDialogBuilder(context).setTitle(title);}
 private void showDialog(AlertDialog d){showDialog(d,false);}
 private void showDialog(AlertDialog d,boolean keyboard){
  if(closed)return;Window w=d.getWindow();w.setType(WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY);
  if(keyboard)w.clearFlags(WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE|WindowManager.LayoutParams.FLAG_ALT_FOCUSABLE_IM);else w.addFlags(WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE);
  d.setOnDismissListener(x->{childDismissedAt=android.os.SystemClock.uptimeMillis();children.remove(d);});children.add(d);try{d.show();}catch(RuntimeException e){children.remove(d);throw e;}
 }
 private void closePopup(){replica.ColorPopupMenu old=popup;popup=null;if(old!=null)old.dismissImmediately();}
 private void menu(View source,String title,String[] labels,int destructive,java.util.function.IntConsumer action){
  if(closed||hasChildUi()||source==null||!source.isAttachedToWindow())return;closePopup();
  replica.ColorPopupMenu next=new replica.ColorPopupMenu(source).setWindowLayoutType(WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY).setTitle(title).setItems(labels,index->{if(!closed)action.accept(index);});
  if(destructive>=0)next.setDividerBefore(destructive).setDestructive(destructive);
  next.setOnDismiss(()->{childDismissedAt=android.os.SystemClock.uptimeMillis();if(popup==next)popup=null;});popup=next;next.show();
 }
 private TextView listText(String value,int size,int weight,boolean secondary){TextView text=new TextView(context);text.setText(value);text.setTextSize(size);text.setTypeface(replica.ColorOsTheme.typeface(context,weight));text.setTextColor(secondary?replica.ColorOsTheme.secondary(context):replica.ColorOsTheme.text(context));text.setGravity(Gravity.CENTER_VERTICAL|Gravity.START);return text;}
 private LinearLayout listBox(){LinearLayout list=new LinearLayout(context);list.setOrientation(LinearLayout.VERTICAL);list.setPadding(dp(4),0,dp(4),0);return list;}
 private ScrollView scrolling(LinearLayout list){ScrollView scroll=new replica.ColorSettingsScrollView(context);scroll.setFillViewport(false);scroll.addView(list,new ScrollView.LayoutParams(-1,-2));return scroll;}
 private final class Choices extends LinearLayout {
  private final LinearLayout entries;private final TextView empty;private String query="";
  Choices(LinearLayout entries,String hint){
   super(context);this.entries=entries;setOrientation(VERTICAL);setFocusableInTouchMode(true);
   addView(new replica.SearchField(context,hint,"",value->{query=value.trim().toLowerCase(Locale.ROOT);filter();}),new LayoutParams(-1,-2));
   empty=listText("没有匹配的项目",14,400,true);empty.setGravity(Gravity.CENTER);empty.setMinimumHeight(dp(54));empty.setVisibility(GONE);addView(empty);
   addView(scrolling(entries),new LayoutParams(-1,-2));
  }
  void filter(){
   TextView heading=null;boolean any=false;String section="";
   for(int i=0;i<entries.getChildCount();i++){
    View child=entries.getChildAt(i);
    if(child instanceof TextView&&child.isAccessibilityHeading()){
     heading=(TextView)child;section=heading.getText().toString();heading.setVisibility(GONE);continue;
    }
    CharSequence desc=child.getContentDescription();
    boolean visible=desc==null||query.isEmpty()||section.contains(query)||desc.toString().toLowerCase(Locale.ROOT).contains(query);
    child.setVisibility(visible?VISIBLE:GONE);if(visible){any=true;if(heading!=null)heading.setVisibility(VISIBLE);}
   }
   empty.setVisibility(any?GONE:VISIBLE);
  }
 }
 private void section(LinearLayout list,String title){TextView heading=listText(title,13,500,true);heading.setPadding(dp(12),dp(14),dp(12),dp(6));heading.setAccessibilityHeading(true);list.addView(heading);}
 private LinearLayout listRow(LinearLayout list,String title,String summary,Drawable drawable,boolean tint){
  LinearLayout row=new LinearLayout(context);row.setBaselineAligned(false);row.setGravity(Gravity.CENTER_VERTICAL);row.setPadding(dp(12),dp(10),dp(8),dp(10));row.setMinimumHeight(dp(summary==null?64:72));row.setFocusable(true);Runnable rowStyle=()->row.setBackground(replica.ColorDialogBuilder.touchBackground(context,Color.TRANSPARENT,14));rowStyle.run();replica.ColorOsTheme.watch(row,rowStyle);
  if(drawable!=null){ImageView image=new ImageView(context);image.setImageDrawable(drawable);image.setScaleType(ImageView.ScaleType.FIT_CENTER);if(tint){Runnable tintIcon=()->image.setColorFilter(replica.ColorOsTheme.text(context));tintIcon.run();replica.ColorOsTheme.watch(image,tintIcon);}image.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);LinearLayout.LayoutParams ip=new LinearLayout.LayoutParams(dp(40),dp(40));ip.rightMargin=dp(12);row.addView(image,ip);}
  LinearLayout labels=new LinearLayout(context);labels.setOrientation(LinearLayout.VERTICAL);labels.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO_HIDE_DESCENDANTS);TextView name=listText(title,16,500,false);name.setMaxLines(2);name.setEllipsize(android.text.TextUtils.TruncateAt.END);labels.addView(name);
  if(summary!=null){TextView detail=listText(summary,13,400,true);detail.setPadding(0,dp(3),0,0);labels.addView(detail);}row.addView(labels,new LinearLayout.LayoutParams(0,-2,1));row.setContentDescription(title+(summary==null?"":"，"+summary));list.addView(row,new LinearLayout.LayoutParams(-1,-2));return row;
 }
 private void choiceRow(LinearLayout list,String title,Drawable icon,boolean tint,boolean[] selected,int index){
  LinearLayout row=listRow(list,title,null,icon,tint);replica.ColorCheckBox check=new replica.ColorCheckBox(context);check.setChecked(selected[index]);check.setClickable(false);check.setFocusable(false);check.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);row.addView(check,new LinearLayout.LayoutParams(dp(40),dp(48)));
  row.setAccessibilityDelegate(new View.AccessibilityDelegate(){public void onInitializeAccessibilityNodeInfo(View view,android.view.accessibility.AccessibilityNodeInfo info){super.onInitializeAccessibilityNodeInfo(view,info);info.setClassName(CheckBox.class.getName());info.setCheckable(true);info.setChecked(check.isChecked());}});
  row.setOnClickListener(v->{selected[index]=!selected[index];check.setChecked(selected[index]);});
 }
 /** Apply only choices exposed by this picker; legacy or unavailable entries are preserved. */
 static void applyChoices(ArrayList<String> target,List<String> keys,boolean[] selected){for(int i=0;i<keys.size();i++){String key=keys.get(i);if(selected[i]){if(!target.contains(key))target.add(key);}else target.remove(key);}}
 private void save(){layout.save(prefs);}private void changed(){save();render();position();}
 private void options(View source){menu(source,"快速启动",new String[]{"列数："+columns,"最近应用页："+(layout.recent?"开":"关"),"新建文件夹"},-1,n->{
  if(n==0)chooseColumns();if(n==1){layout.recent=!layout.recent;page=0;changed();if(layout.recent)loadRecent(false);}if(n==2)nameFolder(null);
 });}
 private void chooseColumns(){int[] values=replica.PanelChoices.columns(columns);String[] labels=new String[values.length];int selected=0;for(int i=0;i<values.length;i++){labels[i]=values[i]+" 列";if(values[i]==columns)selected=i;}
  AlertDialog picker=builder("列数").setSingleChoiceItems(labels,selected,(d,n)->replica.ColorDialogBuilder.dismissThen(d,()->{if(closed)return;columns=values[n];prefs.edit().putInt("columns",columns).apply();render();position();})).setNegativeButton("取消",null).create();showDialog(picker);
 }
 private void addMenu(View source){menu(source,"添加入口",folderId==null?new String[]{"应用","系统动作","应用快捷方式","文件夹"}:new String[]{"应用","系统动作","应用快捷方式"},-1,n->{if(n==0)chooseApps();if(n==1)chooseActions();if(n==2)chooseShortcuts();if(n==3)nameFolder(null);});}
 private void nameFolder(String id){nameFolder(id,null,null);}
 private void nameFolder(String id,String moving,ArrayList<String> source){
  if(closed||id!=null&&!layout.folders.containsKey(id))return;LinearLayout fields=listBox();section(fields,"名称");EditText input=replica.ColorDialogBuilder.editText(context,"文件夹名称");input.setContentDescription("文件夹名称");input.setSingleLine();input.setText(id==null?"新文件夹":layout.folders.get(id).name);input.setSelectAllOnFocus(true);fields.addView(input,new LinearLayout.LayoutParams(-1,-2));
  AlertDialog editor=builder(id==null?"新建文件夹":"重命名文件夹").setView(fields).setPositiveButton("保存",null).setNegativeButton("取消",null).create();showDialog(editor,true);if(closed)return;input.requestFocus();
  editor.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(v->{final String name;try{name=LauncherLayout.folderName(input.getText().toString());}catch(IllegalArgumentException invalid){input.setError(invalid.getMessage());return;}
   replica.ColorDialogBuilder.dismissThen(editor,()->{if(closed)return;if(id==null){String created=layout.createFolder(name);if(moving!=null&&source!=null&&source.remove(moving))layout.folders.get(created).items.add(moving);folderId=null;page=pages()-1;}else{LauncherLayout.Folder folder=layout.folders.get(id);if(folder==null)return;folder.name=name;}changed();});
  });
 }
 private void manage(String key,View source){
  if(key.startsWith("recent:")){menu(source,label(key),new String[]{"添加到常用入口","切换到此任务"},-1,n->{if(n==0){String app="app:"+component(key);if(!layout.items.contains(app))layout.items.add(app);changed();}else activate(key);});return;}
  final ArrayList<String> target=items();final boolean inFolder=folderId!=null;
  ArrayList<String> choices=new ArrayList<>(Arrays.asList("前移一位","后移一位","移到第一页","移到最后一页",key.startsWith("folder:")?"重命名文件夹":"移到文件夹",inFolder?"移回主页面":"移除"));if(inFolder)choices.add("移除");
  menu(source,label(key),choices.toArray(new String[0]),choices.size()-1,n->{int at=target.indexOf(key);if(at<0)return;
   if(n==4){if(key.startsWith("folder:"))nameFolder(key.substring(7));else moveToFolder(key,target);return;}
   if(n==5&&inFolder){target.remove(key);if(!layout.items.contains(key))layout.items.add(key);changed();return;}
   if(n>=5&&key.startsWith("folder:")){String id=key.substring(7);showDialog(builder("删除文件夹？").setMessage("文件夹内的快捷入口也将移除，应用不会被卸载。").setPositiveDestructive("删除",(d,which)->{if(closed)return;layout.items.remove(key);layout.folders.remove(id);changed();}).setNegativeButton("取消",null).create());return;}
   target.remove(at);if(n==0)target.add(Math.max(0,at-1),key);if(n==1)target.add(Math.min(target.size(),at+1),key);if(n==2)target.add(0,key);if(n==3)target.add(key);changed();
  });
 }
 private void moveToFolder(String key,ArrayList<String> source){
  LinearLayout list=listBox();AlertDialog picker=builder("移到文件夹").setView(scrolling(list)).setNegativeButton("取消",null).create();
  for(Map.Entry<String,LauncherLayout.Folder> item:layout.folders.entrySet())if(!item.getKey().equals(folderId)){
   String id=item.getKey();LinearLayout row=listRow(list,item.getValue().name,item.getValue().items.size()+" 个入口",replica.SystemIcons.raw(context,"com.android.settings:ic_folder_vd_theme_24"),true);
   row.setOnClickListener(v->replica.ColorDialogBuilder.dismissThen(picker,()->{if(closed)return;LauncherLayout.Folder destination=layout.folders.get(id);if(destination==null||!source.remove(key))return;if(!destination.items.contains(key))destination.items.add(key);changed();}));
  }
  LinearLayout create=listRow(list,"新建文件夹",null,replica.SystemIcons.raw(context,"com.android.settings:ic_add_24dp"),true);create.setOnClickListener(v->replica.ColorDialogBuilder.dismissThen(picker,()->{if(!closed)nameFolder(null,key,source);}));showDialog(picker);
 }
 private Drawable pickerIcon(Drawable source){int size=dp(40);android.graphics.Bitmap bitmap=android.graphics.Bitmap.createBitmap(size,size,android.graphics.Bitmap.Config.ARGB_8888);try{Drawable icon=source.mutate();icon.setBounds(0,0,size,size);icon.draw(new android.graphics.Canvas(bitmap));return new android.graphics.drawable.BitmapDrawable(context.getResources(),bitmap);}catch(RuntimeException failure){bitmap.recycle();throw failure;}}
 private static void recyclePickerIcons(List<AppChoice> apps){for(AppChoice app:apps)if(app.icon instanceof android.graphics.drawable.BitmapDrawable){android.graphics.Bitmap bitmap=((android.graphics.drawable.BitmapDrawable)app.icon).getBitmap();if(!bitmap.isRecycled())bitmap.recycle();}}
 private static final class AppChoice {final String key,label;final Drawable icon;AppChoice(String key,String label,Drawable icon){this.key=key;this.label=label;this.icon=icon;}}
 private void chooseApps(){
  final int token=++pickerGeneration;final ArrayList<String> target=items();LinearLayout list=listBox();TextView loading=listText("正在读取应用…",15,400,true);loading.setPadding(dp(12),dp(18),dp(12),dp(18));list.addView(loading);
  Choices choices=new Choices(list,"搜索应用");AlertDialog picker=builder("选择应用").setView(choices).setPositiveButton("保存",null).setNegativeButton("取消",null).create();showDialog(picker,true);if(closed)return;picker.getButton(AlertDialog.BUTTON_POSITIVE).setEnabled(false);ArrayList<AppChoice> displayed=new ArrayList<>();picker.setOnDismissListener(d->{childDismissedAt=android.os.SystemClock.uptimeMillis();children.remove(picker);list.removeAllViews();recyclePickerIcons(displayed);displayed.clear();});
  WORK.execute(()->{if(closed)return;ArrayList<AppChoice> apps=new ArrayList<>();String error=null;try{
   android.content.pm.PackageManager pm=service.getPackageManager();List<ResolveInfo> resolved=pm.queryIntentActivities(new Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER),0);HashSet<String> seen=new HashSet<>();
   for(ResolveInfo app:resolved){if(closed)return;if(app.activityInfo==null||app.activityInfo.name==null||app.activityInfo.packageName==null||!app.activityInfo.enabled||!app.activityInfo.exported||app.activityInfo.applicationInfo==null||!app.activityInfo.applicationInfo.enabled)continue;
    String key="app:"+new ComponentName(app.activityInfo.packageName,app.activityInfo.name).flattenToString();if(!seen.add(key))continue;String title=app.activityInfo.packageName;Drawable drawable;
    try{CharSequence label=app.loadLabel(pm);if(label!=null&&!label.toString().isBlank())title=label.toString();}catch(RuntimeException unavailable){}
    try{drawable=pickerIcon(app.loadIcon(pm));}catch(RuntimeException unavailable){try{drawable=pickerIcon(pm.getDefaultActivityIcon());}catch(RuntimeException missing){drawable=null;}}apps.add(new AppChoice(key,title,drawable));
   }
   java.text.Collator order=java.text.Collator.getInstance();apps.sort((a,b)->{int value=order.compare(a.label,b.label);return value==0?a.key.compareTo(b.key):value;});
  }catch(RuntimeException unavailable){error="无法读取应用，请关闭后重试";}final String failure=error;
   MAIN.post(()->{if(closed||token!=pickerGeneration||!children.contains(picker)||!picker.isShowing()){recyclePickerIcons(apps);return;}list.removeAllViews();if(failure!=null||apps.isEmpty()){recyclePickerIcons(apps);loading.setText(failure==null?"没有可用的应用":failure);list.addView(loading);return;}
    displayed.addAll(apps);ArrayList<String> keys=new ArrayList<>();boolean[] selected=new boolean[apps.size()];for(int i=0;i<apps.size();i++){AppChoice app=apps.get(i);keys.add(app.key);selected[i]=target.contains(app.key);choiceRow(list,app.label,app.icon,false,selected,i);}
    choices.filter();Button save=picker.getButton(AlertDialog.BUTTON_POSITIVE);save.setEnabled(true);save.setOnClickListener(v->replica.ColorDialogBuilder.dismissThen(picker,()->{if(closed)return;applyChoices(target,keys,selected);changed();}));
   });
  });
 }
 private void chooseShortcuts(){ArrayList<String> target=items();shortcutPicker=ShortcutPicker.show(service,new ShortcutPicker.Callback(){public void onSelected(String key){if(closed)return;if(!target.contains(key))target.add(key);changed();}public void onClosed(){childDismissedAt=android.os.SystemClock.uptimeMillis();shortcutPicker=null;}});}
 private void chooseActions(){
  ArrayList<String> keys=new ArrayList<>();for(String id:ActionCatalog.orderedIds())if(!id.equals("none")&&!id.equals("quick_launcher"))keys.add("action:"+id);
  final ArrayList<String> target=items();boolean[] selected=new boolean[keys.size()];for(int i=0;i<keys.size();i++)selected[i]=target.contains(keys.get(i));LinearLayout list=listBox();
  for(ActionCatalog.Group group:ActionCatalog.groups()){ArrayList<String> visible=new ArrayList<>();for(String id:group.ids)if(keys.contains("action:"+id))visible.add(id);if(visible.isEmpty())continue;if(!group.title.isEmpty())section(list,group.title);for(String id:visible){int index=keys.indexOf("action:"+id);choiceRow(list,ActionExecutor.title(id),icon("action:"+id),true,selected,index);}}
  Choices choices=new Choices(list,"搜索系统动作");choices.filter();showDialog(builder("选择系统动作").setView(choices).setPositiveButton("保存",(d,n)->{if(closed)return;applyChoices(target,keys,selected);changed();}).setNegativeButton("取消",null).create(),true);
 }
 private void activate(String key){if(closed||hasChildUi())return;if(key.startsWith("folder:")){folderParentPage=page;folderId=key.substring(7);page=0;hoverMoved=false;render();position();return;}dismiss();if(key.startsWith("recent:")){int at=key.indexOf(':',7);int task=Integer.parseInt(key.substring(7,at));ActionExecutor.root(service,command("launch "+task),"切换任务");}else if(key.startsWith("action:"))ActionExecutor.execute(service,key.substring(7),anchor);else ActionExecutor.execute(service,key,anchor);}
 private String command(String args){return "CLASSPATH="+shell(service.getApplicationInfo().sourceDir)+" app_process /system/bin dev.edgeflow.RecentTasksBridge "+args;}private static String shell(String s){return "'"+s.replace("'","'\\''")+"'";}
 private void loadRecent(boolean refresh){
  if(closed||recentLoading)return;recentLoading=true;recentError=null;render();
  recentRequest=RecentTaskList.read(service,refresh,(tasks,error)->{
   if(closed)return;recentLoading=false;recentLoaded=tasks!=null;recentError=error==null?null:error+"，点此重试";recent.clear();
   recent.addAll(RecentTasksBridge.launcherEntries(tasks,Math.min(6,capacity())));
   if(isRecent()){render();position();}
  });
 }
 public static void onGestureMotion(int action,float x,float y){QuickLauncherOverlay p=current;if(p==null||p.closed||p.editing||p.shortcutPicker!=null||p.popup!=null||!p.children.isEmpty())return;if(action==MotionEvent.ACTION_CANCEL){p.dismiss();return;}if(Math.hypot(x-p.anchor[2],y-p.anchor[3])>p.dp(8))p.hoverMoved=true;if(!p.hoverMoved)return;int hit=-1;for(int i=0;i<p.hitViews.size();i++){View v=p.hitViews.get(i);int[] pos=new int[2];v.getLocationOnScreen(pos);if(x>=pos[0]&&x<pos[0]+v.getWidth()&&y>=pos[1]&&y<pos[1]+v.getHeight()){hit=i;break;}}if(hit!=p.hovered){for(int i=0;i<p.hitViews.size();i++)p.hitViews.get(i).setBackground(p.tileBackground(i==hit));p.hovered=hit;}if(action==MotionEvent.ACTION_UP&&hit>=0)p.activate(p.hitKeys.get(hit));}
 private void closeChildren(){pickerGeneration++;closePopup();if(shortcutPicker!=null){shortcutPicker.close();shortcutPicker=null;}for(AlertDialog d:new ArrayList<>(children))try{replica.ColorDialogBuilder.dismissImmediately(d);}catch(Exception ignored){}children.clear();if(recentRequest!=null){recentRequest.cancel();recentRequest=null;}}
 private void closeFromOutside(){if(children.isEmpty()&&shortcutPicker==null&&popup==null)dismiss();}
 private void dismiss(){if(closed)return;closed=true;closeChildren();if(current==this)current=null;if(motion!=null)motion.exit(()->{try{dialog.dismiss();}catch(Exception ignored){}});else dialog.dismiss();}
 private void remove(){closed=true;closeChildren();if(current==this)current=null;if(motion!=null)motion.cancel();try{dialog.dismiss();}catch(Exception ignored){}}
 private void cleanup(){closed=true;closeChildren();if(current==this)current=null;if(motion!=null)motion.cancel();}
}
