package dev.edgeflow;
import android.accessibilityservice.AccessibilityService;import android.app.AlertDialog;import android.content.*;import android.os.*;import android.view.*;import android.widget.*;import org.json.*;import java.util.*;
/** Named per-foreground-app keyboard shortcuts, stored as validated Android key codes. */
public final class KeyboardShortcutsOverlay {
 static final int MAX_NAME_LENGTH=256, MAX_CHORD_LENGTH=128;
 static String nameError(String name) {
  if (name.trim().isEmpty()) return "请输入名称";
  return name.length() > MAX_NAME_LENGTH ? "名称最多 256 个字符，请缩短后保存" : null;
 }
 private static KeyboardShortcutsOverlay current;private static final Handler MAIN=new Handler(Looper.getMainLooper());
 private final AccessibilityService service;private final Context context;private final SharedPreferences prefs;private final String app;private final ArrayList<Entry> entries=new ArrayList<>();
 private final ArrayList<AlertDialog> dialogs=new ArrayList<>();private boolean closed;private Runnable pending;private AlertDialog menuDialog;private LinearLayout menuRows;private ScrollView menuScroll;private replica.ColorPopupMenu popup;private long childDismissedAt;
 private static final class Entry {final String name,chord;Entry(String n,String k){name=n;chord=k;}}
 public static void show(AccessibilityService s,float[] anchor){dismissAll();try{current=new KeyboardShortcutsOverlay(s);current.menu();}catch(Exception e){dismissAll();android.util.Log.e("EdgeShortcuts","show",e);Toast.makeText(s,"键盘快捷方式暂不可用",Toast.LENGTH_SHORT).show();}}
 public static void dismissAll(){if(current!=null)current.dismiss();}
 private void dismiss(){if(closed)return;closed=true;if(pending!=null)MAIN.removeCallbacks(pending);pending=null;closeDialogs();if(current==this)current=null;}
 private void closePopup(){replica.ColorPopupMenu old=popup;popup=null;if(old!=null)old.dismissImmediately();}
 private void closeDialogs(){closePopup();for(AlertDialog d:new ArrayList<>(dialogs))try{replica.ColorDialogBuilder.dismissImmediately(d);}catch(Exception ignored){}dialogs.clear();menuDialog=null;menuRows=null;menuScroll=null;}
 private KeyboardShortcutsOverlay(AccessibilityService s){service=s;app=replica.NativeState.foreground(s);prefs=s.getSharedPreferences("edgeflow_shortcuts",Context.MODE_PRIVATE);int theme=s.getResources().getIdentifier(replica.PanelStyle.dark(s)?"ReplicaGlassDark":"ReplicaGlass","style",s.getPackageName());context=new ContextThemeWrapper(s,theme);String raw=prefs.getString("app:"+app,null);if(raw==null){entries.add(new Entry("全选","CTRL+A"));entries.add(new Entry("复制","CTRL+C"));entries.add(new Entry("粘贴","CTRL+V"));entries.add(new Entry("剪切","CTRL+X"));entries.add(new Entry("撤销","CTRL+Z"));entries.add(new Entry("查找","CTRL+F"));}else try{JSONArray a=new JSONArray(raw);for(int i=0;i<a.length();i++){JSONObject o=a.getJSONObject(i);String chord=o.getString("keys");parse(chord);entries.add(new Entry(o.getString("name"),chord));}}catch(Exception e){android.util.Log.w("EdgeShortcuts","Invalid entry",e);}}
 private int dp(float value){return replica.PanelStyle.dp(context,value);}
 private String appName(){try{return service.getPackageManager().getApplicationLabel(service.getPackageManager().getApplicationInfo(app,0)).toString();}catch(Exception e){return "当前应用";}}
 private replica.ColorDialogBuilder builder(String title){return new replica.GlassDialogBuilder(context).setTitle(title);}
 private TextView text(String value,int size,int weight,boolean secondary){TextView view=new TextView(context);view.setText(value);view.setTextSize(size);view.setTypeface(replica.ColorOsTheme.typeface(context,weight));view.setTextColor(secondary?replica.ColorOsTheme.secondary(context):replica.ColorOsTheme.text(context));view.setGravity(Gravity.START|Gravity.CENTER_VERTICAL);return view;}
 private void show(AlertDialog d,boolean input){
  if(closed)return;Window w=d.getWindow();w.setType(WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY);
  if(input)w.clearFlags(WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE|WindowManager.LayoutParams.FLAG_ALT_FOCUSABLE_IM);else w.addFlags(WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE);
  d.setOnDismissListener(x->{childDismissedAt=SystemClock.uptimeMillis();dialogs.remove(d);if(menuDialog==d){menuDialog=null;menuRows=null;menuScroll=null;}MAIN.post(()->{if(dialogs.isEmpty()&&pending==null)dismiss();});});dialogs.add(d);try{d.show();}catch(RuntimeException e){dialogs.remove(d);dismiss();throw e;}
 }
 private boolean hasChildUi(){return popup!=null||dialogs.size()>1;}
 private void gateParent(View view){QuickLauncherOverlay.ParentTouchGate gate=new QuickLauncherOverlay.ParentTouchGate();view.setOnTouchListener((v,event)->gate.consume(event.getActionMasked(),event.getDownTime(),childDismissedAt,hasChildUi()));}
 private void menu(){
  if(closed)return;if(menuDialog!=null){renderMenu();return;}
  menuRows=new LinearLayout(context){final QuickLauncherOverlay.ParentTouchGate gate=new QuickLauncherOverlay.ParentTouchGate();public boolean dispatchTouchEvent(MotionEvent event){return gate.consume(event.getActionMasked(),event.getDownTime(),childDismissedAt,hasChildUi())||super.dispatchTouchEvent(event);}};menuRows.setOrientation(LinearLayout.VERTICAL);menuRows.setPadding(dp(4),0,dp(4),0);menuScroll=new replica.ColorSettingsScrollView(context);menuScroll.addView(menuRows,new ScrollView.LayoutParams(-1,-2));
  menuDialog=builder("键盘快捷方式").setMessage(appName()).setView(menuScroll).setPositiveButton("添加",null).setNegativeButton("关闭",null).create();renderMenu();AlertDialog shown=menuDialog;show(shown,false);if(!closed){Button add=shown.getButton(AlertDialog.BUTTON_POSITIVE),close=shown.getButton(AlertDialog.BUTTON_NEGATIVE);gateParent(add);gateParent(close);add.setOnClickListener(v->{if(!hasChildUi())edit(null);});close.setOnClickListener(v->{if(!hasChildUi())shown.dismiss();});}
 }
 private void renderMenu(){
  if(menuRows==null)return;int scrollY=menuScroll.getScrollY();menuRows.removeAllViews();
  if(entries.isEmpty()){TextView empty=text("还没有快捷方式，点按“添加”创建。",15,400,true);empty.setPadding(dp(12),dp(18),dp(12),dp(18));menuRows.addView(empty);}
  for(Entry entry:entries){
   LinearLayout row=new LinearLayout(context);row.setBaselineAligned(false);row.setGravity(Gravity.CENTER_VERTICAL);row.setPadding(dp(12),dp(8),dp(4),dp(8));row.setMinimumHeight(dp(72));row.setBackground(replica.ColorDialogBuilder.touchBackground(context,android.graphics.Color.TRANSPARENT,14));
   LinearLayout labels=new LinearLayout(context);labels.setOrientation(LinearLayout.VERTICAL);labels.setPadding(0,0,dp(8),0);labels.addView(text(entry.name,16,500,false));LinearLayout chord=new LinearLayout(context);chord.setGravity(Gravity.CENTER_VERTICAL);chord.setPadding(0,dp(4),0,0);
   for(String part:entry.chord.split("\\+")){
    TextView key=text(part,12,500,false);key.setPadding(dp(5),dp(2),dp(5),dp(2));key.setGravity(Gravity.CENTER);
    key.setBackground(replica.PanelStyle.shape(context,replica.ColorOsTheme.card(context),6));LinearLayout.LayoutParams kp=new LinearLayout.LayoutParams(-2,-2);kp.setMarginEnd(dp(3));chord.addView(key,kp);
   }
   labels.addView(chord);labels.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO_HIDE_DESCENDANTS);row.addView(labels,new LinearLayout.LayoutParams(0,-2,1));
   ImageView gear=new ImageView(context);gear.setImageDrawable(replica.SystemIcons.raw(context,"com.android.settings:ic_edit_24dp"));Runnable gearStyle=()->{gear.setColorFilter(replica.ColorOsTheme.text(context));gear.setBackground(replica.ColorDialogBuilder.touchBackground(context,android.graphics.Color.TRANSPARENT,24));};gearStyle.run();replica.ColorOsTheme.watch(gear,gearStyle);gear.setPadding(dp(13),dp(13),dp(13),dp(13));gear.setContentDescription("编辑"+entry.name);gear.setFocusable(true);gear.setBackground(replica.ColorDialogBuilder.touchBackground(context,android.graphics.Color.TRANSPARENT,24));row.addView(gear,new LinearLayout.LayoutParams(dp(48),dp(48)));
   row.setFocusable(true);row.setContentDescription(entry.name+"，"+entry.chord);row.setOnClickListener(v->run(entry));gear.setOnClickListener(v->manage(entry,gear));row.setOnLongClickListener(v->{manage(entry,row);return true;});menuRows.addView(row,new LinearLayout.LayoutParams(-1,-2));
  }
  ScrollView scroll=menuScroll;scroll.post(()->{if(!closed&&menuScroll==scroll)scroll.scrollTo(0,scrollY);});
 }
 private void manage(Entry item,View source){
  int at=entries.indexOf(item);if(closed||hasChildUi()||at<0||!source.isAttachedToWindow())return;closePopup();ArrayList<String> choices=new ArrayList<>();choices.add("修改");if(at>0)choices.add("前移一位");if(at+1<entries.size())choices.add("后移一位");choices.add("删除");
  replica.ColorPopupMenu next=new replica.ColorPopupMenu(source).setWindowLayoutType(WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY).setTitle(item.name).setItems(choices.toArray(new String[0]),which->{
   if(closed)return;int index=entries.indexOf(item);if(index<0)return;String action=choices.get(which);if(action.equals("修改")){edit(item);return;}
   if(action.equals("删除"))entries.remove(index);else{int destination=index+(action.equals("前移一位")?-1:1);if(destination<0||destination>=entries.size())return;Collections.swap(entries,index,destination);}save();renderMenu();
  }).setDividerBefore(choices.size()-1).setDestructive(choices.size()-1);
  next.setOnDismiss(()->{childDismissedAt=SystemClock.uptimeMillis();if(popup==next)popup=null;});popup=next;next.show();
 }
 private void fieldLabel(LinearLayout fields,String title){TextView label=text(title,13,500,true);label.setPadding(dp(8),dp(12),dp(8),dp(6));fields.addView(label);}
 private void edit(Entry original){
  if(closed)return;if(original==null&&entries.size()>=200){Toast.makeText(service,"最多可保存 200 个快捷方式",Toast.LENGTH_SHORT).show();return;}
  LinearLayout fields=new LinearLayout(context);fields.setOrientation(LinearLayout.VERTICAL);fields.setPadding(dp(4),0,dp(4),0);
  fieldLabel(fields,"名称");EditText name=replica.ColorDialogBuilder.editText(context,"例如：新建标签页");name.setContentDescription("快捷方式名称");name.setSingleLine();fields.addView(name,new LinearLayout.LayoutParams(-1,-2));
  fieldLabel(fields,"组合键");EditText keys=replica.ColorDialogBuilder.editText(context,"例如 CTRL+T 或 ALT+LEFT");keys.setContentDescription("快捷键组合");keys.setSingleLine();keys.setInputType(android.text.InputType.TYPE_CLASS_TEXT|android.text.InputType.TYPE_TEXT_FLAG_CAP_CHARACTERS|android.text.InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS);fields.addView(keys,new LinearLayout.LayoutParams(-1,-2));
  TextView hint=text("支持 Ctrl、Alt、Shift、Meta、字母、数字和方向键，最多 4 个按键。仅用于"+appName()+"。",13,400,true);hint.setPadding(dp(8),dp(12),dp(8),dp(4));fields.addView(hint);
  if(original!=null){name.setText(original.name);keys.setText(original.chord);}AlertDialog editor=builder(original==null?"添加快捷方式":"修改快捷方式").setView(fields).setPositiveButton("保存",null).setNegativeButton("取消",null).create();show(editor,true);if(closed)return;name.requestFocus();
  if(original!=null && original.name.length()>MAX_NAME_LENGTH)name.setError(nameError(original.name));
  editor.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(v->{String title=name.getText().toString().trim(),chord=keys.getText().toString().trim().toUpperCase(Locale.ROOT);String error=nameError(title);if(error!=null){name.setError(error);return;}if(chord.length()>MAX_CHORD_LENGTH){keys.setError("组合键文字最多 128 个字符");return;}try{parse(chord);}catch(IllegalArgumentException invalid){keys.setError(invalid.getMessage());return;}
   replica.ColorDialogBuilder.dismissThen(editor,()->{if(closed)return;Entry entry=new Entry(title,chord);if(original==null){if(entries.size()>=200)return;entries.add(entry);}else{int index=entries.indexOf(original);if(index<0)return;entries.set(index,entry);}save();renderMenu();});
  });
 }
 private void save(){JSONArray a=new JSONArray();for(Entry e:entries)try{a.put(new JSONObject().put("name",e.name).put("keys",e.chord));}catch(JSONException impossible){throw new IllegalStateException(impossible);}prefs.edit().putString("app:"+app,a.toString()).apply();}
 private void run(Entry e){if(closed||pending!=null||hasChildUi())return;int[] keys=parse(e.chord);StringBuilder cmd=new StringBuilder(keys.length==1?"input keyevent":"input keycombination");for(int key:keys)cmd.append(' ').append(key);pending=()->{pending=null;if(closed)return;if(!app.equals(replica.NativeState.foreground(service))){Toast.makeText(service,"前台应用已变化，请重新选择快捷方式",Toast.LENGTH_SHORT).show();dismiss();return;}ActionExecutor.root(service,cmd.toString(),e.name);dismiss();};closeDialogs();MAIN.postDelayed(pending,180);}
 private static int[] parse(String chord){String[] names=chord.toUpperCase(Locale.ROOT).split("\\+",-1);if(names.length<1||names.length>4)throw new IllegalArgumentException("组合键需包含 1–4 个按键");int[] codes=new int[names.length];HashSet<Integer> unique=new HashSet<>();for(int i=0;i<names.length;i++){String name=names[i].trim();if(name.equals("CTRL")||name.equals("CONTROL"))name="CTRL_LEFT";if(name.equals("ALT"))name="ALT_LEFT";if(name.equals("SHIFT"))name="SHIFT_LEFT";if(name.equals("META")||name.equals("WIN"))name="META_LEFT";if(name.equals("LEFT")||name.equals("RIGHT")||name.equals("UP")||name.equals("DOWN"))name="DPAD_"+name;int code=KeyEvent.keyCodeFromString(name.startsWith("KEYCODE_")?name:"KEYCODE_"+name);if(code==KeyEvent.KEYCODE_UNKNOWN||!unique.add(code))throw new IllegalArgumentException("不支持或重复的按键："+names[i]);codes[i]=code;}return codes;}
 /** Raw preference object validator for settings export/import. No changes are applied. */
 public static void validatePreferences(JSONObject values)throws JSONException{Iterator<String> keys=values.keys();while(keys.hasNext()){String key=keys.next();if(!key.startsWith("app:"))throw new JSONException("Invalid shortcut namespace");String pkg=key.substring(4);if(!pkg.isEmpty()&&!pkg.matches("[A-Za-z0-9_]+(\\.[A-Za-z0-9_]+)+"))throw new JSONException("Invalid shortcut package");Object value=values.get(key);if(!(value instanceof String))throw new JSONException("Shortcut list must be string");JSONArray entries=new JSONArray((String)value);if(entries.length()>200)throw new JSONException("Too many keyboard shortcuts");for(int i=0;i<entries.length();i++){JSONObject e=entries.getJSONObject(i);Object name=e.get("name"),chord=e.get("keys");if(!(name instanceof String)||((String)name).trim().isEmpty()||!(chord instanceof String))throw new JSONException("Invalid shortcut entry");try{parse((String)chord);}catch(IllegalArgumentException invalid){throw new JSONException(invalid.getMessage());}}}}
 private KeyboardShortcutsOverlay(){throw new AssertionError();}
}
