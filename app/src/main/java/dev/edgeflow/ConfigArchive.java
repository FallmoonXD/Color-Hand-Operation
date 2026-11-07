package dev.edgeflow;

import android.appwidget.AppWidgetManager;
import android.appwidget.AppWidgetProviderInfo;
import android.content.ComponentName;
import android.content.Context;
import android.content.SharedPreferences;
import org.json.*;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.*;

/** Portable settings archive. Widget host IDs are intentionally never imported. */
public final class ConfigArchive {
 private static final int MAX_BYTES=1024*1024, MAX_KEYS=2048;
 private static final String[] NAMES={"edgeflow","edgeflow_launcher","edgeflow_shortcuts"};
 public static JSONObject export(Context c)throws Exception{
  JSONObject o=new JSONObject().put("version",2).put("preferences",AppPrefs.export(c))
   .put("tools",new JSONObject(replica.ToolsConfig.encode(replica.ToolsConfig.load(c))))
   .put("launcher",launcher(snapshot(c.getSharedPreferences(NAMES[1],0))))
   .put("shortcuts",shortcuts(snapshot(c.getSharedPreferences(NAMES[2],0)))).put("widgets",widgets(c));
  size(o);return o;
 }
 static final class Import {
  final int version;final JSONObject preferences,launcher,shortcuts;final ArrayList<replica.ToolsConfig.Item> tools;
  Import(int version,JSONObject preferences,ArrayList<replica.ToolsConfig.Item> tools,JSONObject launcher,JSONObject shortcuts){
   this.version=version;this.preferences=preferences;this.tools=tools;this.launcher=launcher;this.shortcuts=shortcuts;
  }
  String description(){
   return "将应用 "+preferences.length()+" 项设置，并替换快速工具的 "+tools.size()+" 个布局项。"
     +(launcher!=null?"\n快速启动布局将一并导入。":"")+(shortcuts!=null?"\n键盘快捷方式将一并导入。":"")
     +"\n现有小组件绑定会保留。";
  }
 }
 static Import prepareImport(JSONObject o)throws Exception{
   size(o);keys(o,"version","preferences","tools","launcher","shortcuts","widgets");
   int version=integer(o.get("version"),1,2,"version");
   JSONObject preferences=AppPrefs.validate(o.getJSONObject("preferences"));
   JSONObject toolObject=o.getJSONObject("tools");keys(toolObject,"version","items");integer(toolObject.get("version"),1,1,"快速工具版本");
   JSONArray toolItems=toolObject.getJSONArray("items");if(toolItems.length()>replica.ToolsConfig.LIMIT)throw new JSONException("快速工具项目过多");
   for(int i=0;i<toolItems.length();i++){JSONObject item=toolItems.getJSONObject(i);keys(item,"key","label");string(item.get("key"),2048,"工具标识");if(item.has("label"))string(item.get("label"),256,"工具名称");}
   ArrayList<replica.ToolsConfig.Item> tools=replica.ToolsConfig.parse(toolObject.toString());
   if(tools.size()!=toolObject.getJSONArray("items").length())throw new JSONException("快速工具包含重复或无效项目");
   JSONObject launcher=o.has("launcher")?launcher(o.getJSONObject("launcher")):null;
   JSONObject shortcuts=o.has("shortcuts")?shortcuts(o.getJSONObject("shortcuts")):null;
   if(o.has("widgets"))o.getJSONObject("widgets"); // Informational provider map; never reuse another host's IDs.
   if(version==1&&(launcher!=null||shortcuts!=null))throw new JSONException("旧版配置不能包含新版面板字段");
   return new Import(version,preferences,tools,launcher,shortcuts);
 }
 /** Validate all sections before mutation, with synchronous rollback of every touched store. */
 public static void restore(Context c,JSONObject o)throws Exception{apply(c,prepareImport(o));}
 static void apply(Context c,Import plan)throws Exception{
  synchronized(AppPrefs.class){
   LinkedHashMap<String,Map<String,?>> old=new LinkedHashMap<>();
   for(String name:NAMES)old.put(name,new HashMap<>(c.getSharedPreferences(name,0).getAll()));
   ArrayList<replica.ToolsConfig.Item> oldTools=replica.ToolsConfig.load(c);
   try{
    AppPrefs.importConfiguration(c,plan.preferences,plan.tools,plan.version>=2);
    if(plan.launcher!=null)write(c.getSharedPreferences(NAMES[1],0),map(plan.launcher));
    if(plan.shortcuts!=null)write(c.getSharedPreferences(NAMES[2],0),map(plan.shortcuts));
   }catch(Exception failure){
    for(Map.Entry<String,Map<String,?>> e:old.entrySet())try{write(c.getSharedPreferences(e.getKey(),0),e.getValue());}catch(Exception ex){failure.addSuppressed(ex);}
    try{replica.ToolsConfig.save(c,oldTools);}catch(Exception ex){failure.addSuppressed(ex);}throw failure;
   }
  }
 }
 private static void size(JSONObject o)throws JSONException{if(o.toString().getBytes(StandardCharsets.UTF_8).length>MAX_BYTES)throw new JSONException("配置超过 1 MiB");}
 private static JSONObject snapshot(SharedPreferences p)throws JSONException{JSONObject o=new JSONObject();for(Map.Entry<String,?> e:p.getAll().entrySet())o.put(e.getKey(),e.getValue() instanceof Set?new JSONArray((Set<?>)e.getValue()):e.getValue());return o;}
 private static Map<String,Object> map(JSONObject o)throws JSONException{Map<String,Object> m=new LinkedHashMap<>();for(Iterator<String> it=o.keys();it.hasNext();){String k=it.next();m.put(k,o.get(k));}return m;}
 private static void write(SharedPreferences p,Map<String,?> m)throws IOException{SharedPreferences.Editor e=p.edit().clear();for(Map.Entry<String,?> entry:m.entrySet()){String k=entry.getKey();Object v=entry.getValue();if(v instanceof Boolean)e.putBoolean(k,(Boolean)v);else if(v instanceof Integer)e.putInt(k,(Integer)v);else if(v instanceof Long)e.putLong(k,(Long)v);else if(v instanceof Float)e.putFloat(k,(Float)v);else if(v instanceof String)e.putString(k,(String)v);else if(v instanceof Set){Set<String> set=new HashSet<>();for(Object item:(Set<?>)v)set.add((String)item);e.putStringSet(k,set);}else throw new IOException("不支持的配置类型："+k);}if(!e.commit())throw new IOException("配置保存失败");}
 private static void keys(JSONObject o,String...allowed)throws JSONException{if(o.length()>MAX_KEYS)throw new JSONException("配置项过多");Set<String> valid=new HashSet<>(Arrays.asList(allowed));for(Iterator<String> it=o.keys();it.hasNext();){String k=it.next();if(!valid.contains(k))throw new JSONException("未知配置项："+k);}}
 private static int integer(Object value,int min,int max,String key)throws JSONException{if(!(value instanceof Number))throw new JSONException(key+" 必须为整数");double n=((Number)value).doubleValue();if(!Double.isFinite(n)||n!=Math.rint(n)||n<min||n>max)throw new JSONException(key+" 超出范围");return (int)n;}
 private static String string(Object v,int max,String key)throws JSONException{if(!(v instanceof String)||((String)v).length()>max)throw new JSONException(key+" 字符串无效或过长");return (String)v;}
 private static void bool(Object v,String key)throws JSONException{if(!(v instanceof Boolean))throw new JSONException(key+" 必须为布尔值");}
 private static JSONObject launcher(JSONObject p)throws Exception{
  LauncherLayout.validatePreferences(p);keys(p,"layout_v2","favorites","recent_page","columns","page","recent_limit","shortcut_labels");JSONObject clean=new JSONObject();
  for(Iterator<String> it=p.keys();it.hasNext();){String k=it.next();Object v=p.get(k);
   switch(k){
    case "columns":v=integer(v,2,5,k);break;
    case "page":v=integer(v,0,1000,k);break;
    case "recent_limit":v=integer(v,1,30,k);break;
    case "recent_page":bool(v,k);break;
    case "shortcut_labels":{JSONObject labels=new JSONObject(string(v,MAX_BYTES,k));if(labels.length()>MAX_KEYS)throw new JSONException("快捷方式名称过多");for(Iterator<String> ids=labels.keys();ids.hasNext();){String id=ids.next();if(!ShortcutEntry.valid(id))throw new JSONException("快捷方式标识无效");string(labels.get(id),256,"快捷方式名称");}break;}
    case "favorites":{JSONArray list=new JSONArray(string(v,MAX_BYTES,k));if(list.length()>1000)throw new JSONException("收藏项目过多");for(int i=0;i<list.length();i++)if(ComponentName.unflattenFromString(string(list.get(i),1024,k))==null)throw new JSONException("无效应用组件");break;}
    case "layout_v2":{JSONObject layout=new JSONObject(string(v,MAX_BYTES,k));keys(layout,"version","recent_page","items","folders");integer(layout.get("version"),2,2,"启动器版本");if(layout.has("recent_page"))bool(layout.get("recent_page"),"recent_page");JSONObject folders=layout.getJSONObject("folders");if(folders.length()>200)throw new JSONException("文件夹过多");for(Iterator<String> ids=folders.keys();ids.hasNext();){String id=ids.next();if(!id.matches("[A-Za-z0-9_-]{1,128}"))throw new JSONException("文件夹标识无效");JSONObject f=folders.getJSONObject(id);keys(f,"name","items");string(f.get("name"),256,"文件夹名");items(f.getJSONArray("items"),false,folders);}items(layout.getJSONArray("items"),true,folders);break;}
   }clean.put(k,v);
  }return clean;
 }
 private static void items(JSONArray a,boolean folder,JSONObject folders)throws JSONException{if(a.length()>1000)throw new JSONException("启动器项目过多");for(int i=0;i<a.length();i++){String item=string(a.get(i),2048,"启动器项目");if(item.startsWith("folder:")&&!folders.has(item.substring(7)))throw new JSONException("缺少文件夹");}ArrayList<String> parsed=new ArrayList<>();LauncherLayout.read(a,parsed,folder);if(parsed.size()!=a.length())throw new JSONException("启动器包含重复或无效项目");}
 // The editor caps new names. Preserve older nonempty names through export/restore under the archive size limit.
 private static JSONObject shortcuts(JSONObject p)throws Exception{KeyboardShortcutsOverlay.validatePreferences(p);if(p.length()>MAX_KEYS)throw new JSONException("应用快捷键过多");JSONObject clean=new JSONObject();for(Iterator<String> it=p.keys();it.hasNext();){String k=it.next();if(!k.equals("app:")&&!k.matches("app:[A-Za-z0-9_]+(\\.[A-Za-z0-9_]+)+"))throw new JSONException("无效快捷键应用包名");String raw=string(p.get(k),MAX_BYTES,k);JSONArray entries=new JSONArray(raw);if(entries.length()>200)throw new JSONException("快捷键过多");for(int i=0;i<entries.length();i++){JSONObject entry=entries.getJSONObject(i);keys(entry,"name","keys");string(entry.get("keys"),KeyboardShortcutsOverlay.MAX_CHORD_LENGTH,"组合键");}clean.put(k,raw);}return clean;}
 private static JSONObject widgets(Context c)throws Exception{
  SharedPreferences p=c.getSharedPreferences("replica_widgets",0);JSONObject out=new JSONObject().put("version",1).put("pages",p.getInt("pages",1)).put("page",p.getInt("page",0)).put("height",p.getInt("height_v4",330)).put("loop",p.getBoolean("loop",false));
  JSONArray items=new JSONArray(),saved=new JSONArray(p.getString("layout_v3","[]"));AppWidgetManager manager=AppWidgetManager.getInstance(c);
  for(int i=0;i<saved.length();i++){JSONObject cell=saved.getJSONObject(i),copy=new JSONObject();int id=cell.getInt("id");AppWidgetProviderInfo info=manager.getAppWidgetInfo(id);copy.put("sourceId",id);if(info!=null)copy.put("component",info.provider.flattenToString());else copy.put("unavailable",true);for(String k:new String[]{"page","x","y","w","h","color"})if(cell.has(k))copy.put(k,cell.get(k));items.put(copy);}
  return out.put("items",items).put("restore_requires_rebinding",true);
 }
 private ConfigArchive(){}
}
