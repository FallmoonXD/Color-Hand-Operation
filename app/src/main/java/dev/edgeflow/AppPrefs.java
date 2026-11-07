package dev.edgeflow;
import android.content.*;import java.util.*;import org.json.*;
public final class AppPrefs {
 public static SharedPreferences get(Context c){return c.getSharedPreferences("edgeflow",0);}
 public static synchronized void defaults(Context c){SharedPreferences p=get(c);if(p.contains("schema")){HandleSettings.ensureSafeArea(c,p);return;}SharedPreferences.Editor e=p.edit();e.putInt("schema",1).putInt(HandleSettings.VERSION_KEY,HandleSettings.VERSION).putBoolean("enabled",true).putBoolean("left_enabled",true).putBoolean("right_enabled",true).putFloat("handle_width_dp",12.3f).putFloat("handle_start_fraction",.13084841f).putFloat("handle_end_fraction",1f).putFloat("trigger_dp",39.43f).putFloat("long_trigger_dp",87.43f).putInt("long_delay_ms",300).putBoolean("vibration",true).putInt("vibration_strength",0).putBoolean("hide_keyboard",false).putBoolean("hide_home",false).putBoolean("hide_notifications",false).putBoolean("quick_action_short",false).putBoolean("quick_action_long",true).putBoolean("extra_left_enabled",false).putBoolean("extra_right_enabled",false).putBoolean("extra2_left_enabled",false).putBoolean("extra2_right_enabled",false).putFloat("touchpad_speed",1.7f).putFloat("navigation_button_size_dp",58f);
  for(String side:new String[]{"left","right"}){e.putString("action_"+side+"_straight_short",side.equals("left")?"recents":"back").putString("action_"+side+"_up_short","volume_up").putString("action_"+side+"_down_short","volume_down").putString("action_"+side+"_straight_long",side.equals("left")?"one_hand":"sidebar").putString("action_"+side+"_up_long","widgets").putString("action_"+side+"_down_long","quick_tools");}
  for(String side:GestureHandleConfig.IDS){if(!GestureHandleConfig.isExtra(side))continue;e.putFloat(side+"_handle_width_dp",12.3f).putFloat(side+"_handle_start_fraction",GestureHandleConfig.defaultStart(side)).putFloat(side+"_handle_end_fraction",GestureHandleConfig.defaultEnd(side));for(String direction:new String[]{"up","straight","down"})for(String kind:new String[]{"short","long"})e.putString("action_"+side+"_"+direction+"_"+kind,"none");}e.apply();
 }
 public static String action(Context c,String side,String direction,boolean hold){return get(c).getString("action_"+side+"_"+direction+"_"+(hold?"long":"short"),GestureHandleConfig.defaultAction(side,direction,hold));}
 public static int handleColor(SharedPreferences p,String id){return p.getInt(id+"_color",p.getInt("handle_color",0xff188dff));}
 /** Older stores used a floating fraction; current UI and archives use integer percent. */
 private static int opacityPercent(Object value) {
  if (!(value instanceof Number)) return 0;
  double amount = ((Number)value).doubleValue();
  if (!Double.isFinite(amount)) return 0;
  boolean integralType = value instanceof Integer || value instanceof Long || value instanceof Short
          || value instanceof Byte || value instanceof java.math.BigInteger;
  if (!integralType && amount >= 0 && amount <= 1) amount *= 100;
  return (int)Math.round(Math.max(0, Math.min(100, amount)));
 }
 public static int handleOpacity(SharedPreferences prefs) { return opacityPercent(prefs.getAll().get("handle_opacity")); }
 public static int visibleHandleColor(SharedPreferences p,String id){
  int color=handleColor(p,id);
  return (Math.round((color>>>24)*handleOpacity(p)/100f)<<24)|(color&0x00ffffff);
 }
 private static boolean isHandleColor(String key){return key.equals("handle_color")||key.matches("(extra2?_)?(left|right)_color");}
 public static JSONObject export(Context c)throws Exception{JSONObject o=new JSONObject();for(Map.Entry<String,?> e:get(c).getAll().entrySet()){Object v=e.getValue();if(e.getKey().equals("handle_opacity"))v=opacityPercent(v);o.put(e.getKey(),v instanceof Set?new JSONArray((Set<?>)v):v);}return o;}
 /** Validate every field before writing any preference. Imported JSON never controls storage types. */
 public static JSONObject validate(JSONObject input)throws Exception{
  JSONObject out=new JSONObject();Set<String> booleans=new HashSet<>(Arrays.asList("enabled","left_enabled","right_enabled","vibration","haptics","quick_vibration","custom_vibration","long_immediate","hide_landscape","hide_keyboard","hide_when_keyboard","landscape_enabled","navigation_vertical","hide_home","hide_notifications","quick_action_short","quick_action_long","extra_left_enabled","extra_right_enabled","extra2_left_enabled","extra2_right_enabled"));
  for(Iterator<String> it=input.keys();it.hasNext();){String key=it.next();Object value=input.get(key);
   if(booleans.contains(key)||key.matches("(extra2?_)?(left|right)_long_enabled")||key.matches("(extra2?_)?handles_linked")){if(!(value instanceof Boolean))throw new JSONException(key+" 必须为布尔值");out.put(key,value);continue;}
   if(ActionTarget.isKey(key)){out.put(key,ActionTarget.validate(key,value));continue;}
   if(GestureMapping.isKey(key)){
    if(!(value instanceof String))throw new JSONException(key+" 必须为动作名称");String action=(String)value;boolean known=Arrays.asList(ActionExecutor.IDS).contains(action);
    if(action.startsWith("launcher_shortcut:"))known=ShortcutEntry.valid(action);
    if(action.startsWith("app:"))known=ComponentName.unflattenFromString(action.substring(4))!=null;
    if(action.startsWith("folder:"))known=LauncherLayout.isFolderAction(action);
    if(action.startsWith("shortcut:")){Intent.parseUri(action.substring(9),Intent.URI_INTENT_SCHEME);known=true;}
    if(!known)throw new JSONException("未知动作："+action);out.put(key,action);continue;
   }
   if(key.equals("navigation_actions")){if(!(value instanceof String))throw new JSONException("navigation_actions 必须为字符串");out.put(key,NavigationConfig.validate((String)value));continue;}
   if(key.equals("disabled_apps")){if(!(value instanceof JSONArray))throw new JSONException("disabled_apps 必须为数组");JSONArray list=(JSONArray)value;HashSet<String> seen=new HashSet<>();JSONArray clean=new JSONArray();for(int i=0;i<list.length();i++){Object item=list.get(i);if(!(item instanceof String)||!((String)item).matches("[A-Za-z0-9_]+(\\.[A-Za-z0-9_]+)+"))throw new JSONException("无效的停用应用包名");if(seen.add((String)item))clean.put(item);}out.put(key,clean);continue;}
   if(key.equals("excluded_packages")){if(!(value instanceof String))throw new JSONException("excluded_packages 必须为字符串");out.put(key,value);continue;}
   double min,max;boolean integer=false;
   if(key.equals("schema")){min=max=1;integer=true;}
   else if(key.equals(HandleSettings.VERSION_KEY)){min=1;max=HandleSettings.VERSION;integer=true;}
   else if(key.equals("handle_pair_count")){min=1;max=3;integer=true;}
   else if(key.equals("long_delay_ms")){min=0;max=1000;integer=true;}
   else if(key.equals("handle_opacity")){
    if (!(value instanceof Number)) throw new JSONException("handle_opacity 必须为数字");
    double raw=((Number)value).doubleValue();
    if (!Double.isFinite(raw)||raw<0||raw>100) throw new JSONException("handle_opacity 超出范围");
    out.put(key,opacityPercent(value));continue;
   }
   else if(isHandleColor(key)){min=Integer.MIN_VALUE;max=Integer.MAX_VALUE;integer=true;}
   else if(key.equals("vibration_strength")){min=0;max=255;integer=true;}
   else if(key.equals("vibration_duration_ms")){min=0;max=30;integer=true;}
   else if(key.equals("touchpad_speed")){min=.5;max=3;}
   else if(key.equals("navigation_button_size_dp")){min=40;max=76;}
   else if(key.equals("trigger_dp")){min=15;max=100;}
   else if(key.equals("long_trigger_dp")){min=40;max=180;}
   else if(key.equals("direction_angle_degrees")){min=15;max=60;}
   else if(GestureAngles.isKey(key)){min=key.contains("_outer_")?75:5;max=key.contains("_outer_")?85:70;}
   else if(key.matches("((extra2?_)?(left|right)_)?handle_width_dp")){min=2;max=30;}
   else if(key.matches("((extra2?_)?(left|right)_)?handle_start_fraction")){min=0;max=.95;}
   else if(key.matches("((extra2?_)?(left|right)_)?handle_end_fraction")){min=.05;max=1;}
   else if(key.equals("handle_center_fraction")){min=0;max=1;}
   else if(key.equals("handle_height_fraction")){min=.05;max=1;}
   else throw new JSONException("未知配置项："+key);
   if(!(value instanceof Number))throw new JSONException(key+" 必须为数字");double n=((Number)value).doubleValue();if(!Double.isFinite(n)||n<min||n>max||(integer&&n!=Math.rint(n)))throw new JSONException(key+" 超出范围");out.put(key,integer?(Object)(int)n:(Object)(float)n);
  }
  return out;
 }
 public static synchronized void importPrefs(Context c,JSONObject o)throws Exception{
  JSONObject valid=validate(o),old=export(c),prepared=prepare(c,old,valid,false);
  try{if(!write(c,prepared,true))throw new java.io.IOException("配置保存失败");}catch(Exception error){try{if(!write(c,old,true))error.addSuppressed(new java.io.IOException("旧配置恢复失败"));}catch(Exception recovery){error.addSuppressed(recovery);}throw error;}
 }
 private static Map<String,Object> fields(JSONObject o)throws JSONException{Map<String,Object> values=new HashMap<>();for(Iterator<String> keys=o.keys();keys.hasNext();){String key=keys.next();values.put(key,o.get(key));}return values;}
 private static JSONObject prepare(Context c,JSONObject old,JSONObject incoming,boolean replace)throws Exception{
  Map<String,Object> existing=fields(old),values=fields(incoming);GestureHandleConfig.Area portrait=HandleSettings.needsReference(existing,values,replace)?HandleDisplayArea.read(c).portrait:null;
  Map<String,Object> prepared;try{prepared=HandleSettings.prepareImport(existing,values,replace,portrait);}catch(IllegalArgumentException bad){throw new JSONException(bad.getMessage());}
  JSONObject result=new JSONObject();for(Map.Entry<String,Object> entry:prepared.entrySet())result.put(entry.getKey(),entry.getValue());
  if(result.optDouble("long_trigger_dp",87.43)<result.optDouble("trigger_dp",39.43))throw new JSONException("长滑距离不能小于短滑距离");return result;
 }
 private static boolean write(Context c,JSONObject o,boolean clear)throws Exception{SharedPreferences.Editor e=get(c).edit();if(clear)e.clear();Iterator<String> keys=o.keys();while(keys.hasNext()){String k=keys.next();Object v=o.get(k);if(v instanceof Boolean)e.putBoolean(k,(Boolean)v);else if(v instanceof String)e.putString(k,(String)v);else if(v instanceof Number){if(k.equals("long_delay_ms")||k.equals("handle_pair_count")||k.equals(HandleSettings.VERSION_KEY)||k.equals("schema")||k.equals("handle_opacity")||k.equals("vibration_strength")||k.equals("vibration_duration_ms")||isHandleColor(k))e.putInt(k,((Number)v).intValue());else e.putFloat(k,((Number)v).floatValue());}else if(v instanceof JSONArray){HashSet<String> set=new HashSet<>();JSONArray a=(JSONArray)v;for(int i=0;i<a.length();i++)set.add(a.getString(i));e.putStringSet(k,set);}}return e.commit();}
 /** Validate both sections first and restore both on an I/O failure. */
 public static synchronized void importConfiguration(Context c,JSONObject o,java.util.List<replica.ToolsConfig.Item> tools)throws Exception{
  importConfiguration(c,o,tools,false);
 }
 public static synchronized void importConfiguration(Context c,JSONObject o,java.util.List<replica.ToolsConfig.Item> tools,boolean replace)throws Exception{
  JSONObject valid=validate(o),old=export(c);ArrayList<replica.ToolsConfig.Item> previous=replica.ToolsConfig.load(c);
  JSONObject prepared=prepare(c,old,valid,replace);
  try{replica.ToolsConfig.save(c,tools);if(!write(c,prepared,true))throw new java.io.IOException("配置保存失败");}
  catch(Exception error){try{replica.ToolsConfig.save(c,previous);if(!write(c,old,true))error.addSuppressed(new java.io.IOException("旧配置恢复失败"));}catch(Exception recovery){error.addSuppressed(recovery);}throw error;}
 }
 private AppPrefs(){}
}
