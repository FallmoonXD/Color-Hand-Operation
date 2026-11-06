package replica;

import android.content.*;import android.content.pm.*;import android.graphics.drawable.Drawable;import android.util.AtomicFile;
import org.json.*;import java.io.*;import java.util.*;

/** Stable tile IDs; one atomic file shared by the settings and accessibility processes. */
public final class ToolsConfig {
 public static final int LIMIT=12;
 public static final String[] TITLES={"Wi-Fi","声音模式","蓝牙","自动旋转","通知栏","录屏","截屏","手电筒","深色模式","锁屏","屏幕超时","关闭当前应用","移动数据","飞行模式","省电模式","定位","勿扰模式","NFC","系统设置","自动亮度"};
 public static final class Item {
  public final String key,label;
  public Item(String key,String label){this.key=key;this.label=label;}
  public int builtin(){try{return Integer.parseInt(key);}catch(Exception e){return -1;}}
  public ComponentName component(){return key.startsWith("app:")?ComponentName.unflattenFromString(key.substring(4)):null;}
 }
 public static Item builtin(int id){return new Item(Integer.toString(id),TITLES[id]);}
 public static ArrayList<Item> defaults(){ArrayList<Item> list=new ArrayList<>();for(int i=0;i<LIMIT;i++)list.add(builtin(i));return list;}
 public static ArrayList<Item> parse(String raw)throws JSONException {
  JSONObject root=new JSONObject(raw);if(root.getInt("version")!=1)throw new JSONException("Unsupported layout version");JSONArray data=root.getJSONArray("items");ArrayList<Item> list=new ArrayList<>();HashSet<String> seen=new HashSet<>();
  for(int i=0;i<data.length()&&list.size()<LIMIT;i++){JSONObject o=data.optJSONObject(i);if(o==null)continue;String key=o.optString("key");if(!seen.add(key))continue;
   try{int id=Integer.parseInt(key);if(id>=0&&id<TITLES.length)list.add(builtin(id));continue;}catch(NumberFormatException ignored){}
   if(key.startsWith("app:")&&ComponentName.unflattenFromString(key.substring(4))!=null)list.add(new Item(key,o.optString("label","应用")));
  }return list;
 }
 public static String encode(List<Item> items)throws JSONException {JSONArray a=new JSONArray();for(Item i:items)a.put(new JSONObject().put("key",i.key).put("label",i.label));return new JSONObject().put("version",1).put("items",a).toString();}
 private static AtomicFile file(Context c){return new AtomicFile(new File(c.getFilesDir(),"tools-layout-v1.json"));}
 public static ArrayList<Item> load(Context c){try{return parse(new String(file(c).readFully(),"UTF-8"));}catch(FileNotFoundException e){return defaults();}catch(Exception e){android.util.Log.w("ReplicaTools","Invalid layout; using defaults",e);return defaults();}}
 public static void save(Context c,List<Item> items)throws Exception {String data=encode(items);if(parse(data).size()!=items.size()||items.size()>LIMIT)throw new IllegalArgumentException("Invalid tile selection");AtomicFile f=file(c);FileOutputStream out=null;try{out=f.startWrite();out.write(data.getBytes("UTF-8"));f.finishWrite(out);}catch(Exception e){if(out!=null)f.failWrite(out);throw e;}}
 public static boolean available(Context c,int id){return id!=17||c.getPackageManager().hasSystemFeature(PackageManager.FEATURE_NFC);}
 public static Drawable icon(Context c,Item item){if(item.component()!=null)try{return c.getPackageManager().getActivityIcon(item.component());}catch(Exception ignored){return c.getPackageManager().getDefaultActivityIcon();}return SystemIcons.tile(c,item.builtin(),false,2);}
 public static Intent launchIntent(Context c,Item item){ComponentName component=item.component();if(component==null)return null;Intent intent=new Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER).setComponent(component).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
  for(ResolveInfo r:c.getPackageManager().queryIntentActivities(intent,0))if(r.activityInfo!=null&&r.activityInfo.enabled&&r.activityInfo.exported)return intent;return null;
 }
}
