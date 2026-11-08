package dev.edgeflow;

import android.content.pm.LauncherApps;
import android.content.pm.ShortcutInfo;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.os.IBinder;
import android.os.ParcelFileDescriptor;
import android.os.UserHandle;
import android.system.Os;
import java.io.ByteArrayOutputStream;
import java.util.List;
import org.json.JSONArray;
import org.json.JSONObject;

/** Own-code privileged LauncherApps client; one query enumerates the current user's publishers. */
public final class ShortcutBridge {
 public static void main(String[] args){try{
  if(args.length!=2)throw new IllegalArgumentException("Use list/start <identity> or list-all <user>");
  boolean all=args[0].equals("list-all"),start=args[0].equals("start");
  if(!all&&!start&&!args[0].equals("list"))throw new IllegalArgumentException("Unknown shortcut operation");
  ShortcutEntry entry=all?null:ShortcutEntry.parse(args[1]);int userId=all?parseUser(args[1]):entry.user;
  // UID 0 cannot claim android at LauncherAppsService. UID 1000 has ACCESS_SHORTCUTS.
  // This does not change the default launcher or pin shortcuts.
  if(android.os.Process.myUid()==0){Os.setgid(1000);Os.setuid(1000);}if(android.os.Process.myUid()!=1000)throw new SecurityException("Root system bridge required");
  IBinder binder=(IBinder)Class.forName("android.os.ServiceManager").getMethod("getService",String.class).invoke(null,"launcherapps");
  Object launcher=Class.forName("android.content.pm.ILauncherApps$Stub").getMethod("asInterface",IBinder.class).invoke(null,binder);Class<?> api=Class.forName("android.content.pm.ILauncherApps");UserHandle user=UserHandle.getUserHandleForUid(userId*100000);
  LauncherApps.ShortcutQuery query=new LauncherApps.ShortcutQuery().setQueryFlags(LauncherApps.ShortcutQuery.FLAG_MATCH_DYNAMIC|LauncherApps.ShortcutQuery.FLAG_MATCH_PINNED|LauncherApps.ShortcutQuery.FLAG_MATCH_MANIFEST|LauncherApps.ShortcutQuery.FLAG_MATCH_PINNED_BY_ANY_LAUNCHER);
  if(entry!=null)query.setPackage(entry.packageName);if(start)query.setShortcutIds(java.util.Collections.singletonList(entry.id));
  Class<?> wrapper=Class.forName("android.content.pm.ShortcutQueryWrapper");Object wrapped=wrapper.getConstructor(LauncherApps.ShortcutQuery.class).newInstance(query);Object slice=api.getMethod("getShortcuts",String.class,wrapper,UserHandle.class).invoke(launcher,"android",wrapped,user);
  @SuppressWarnings("unchecked") List<ShortcutInfo> shortcuts=slice==null?null:(List<ShortcutInfo>)slice.getClass().getMethod("getList").invoke(slice);if(shortcuts==null)throw new IllegalStateException("Shortcut service returned no result");
  if(start){
   ShortcutInfo target=null;for(ShortcutInfo s:shortcuts)if(s.getId().equals(entry.id)&&s.getPackage().equals(entry.packageName)){target=s;break;}
   if(target==null||!target.isEnabled())throw new IllegalStateException("Shortcut no longer exists or is disabled");
   Object accepted=null;for(java.lang.reflect.Method method:api.getMethods())if(method.getName().equals("startShortcut")){
    if(method.getParameterCount()==8&&method.getParameterTypes()[0]==IBinder.class)accepted=method.invoke(launcher,null,"android",entry.packageName,null,entry.id,null,null,userId);
    else if(method.getParameterCount()==7)accepted=method.invoke(launcher,"android",entry.packageName,null,entry.id,null,null,userId);else continue;break;
   }
   if(!Boolean.TRUE.equals(accepted))throw new IllegalStateException("System did not start the shortcut");System.out.println("EDGE_SHORTCUT_STARTED");return;
  }
  if(shortcuts.size()>ShortcutCatalog.MAX_ENTRIES)throw new IllegalStateException("Too many shortcuts; no partial catalog returned");
  JSONArray result=new JSONArray();int iconBudget=2*1024*1024;
  for(ShortcutInfo s:shortcuts){
   CharSequence label=s.getShortLabel();JSONObject value=new JSONObject().put("key",ShortcutEntry.create(userId,s.getPackage(),s.getId())).put("label",label==null?s.getId():label.toString()).put("enabled",s.isEnabled()).put("dynamic",s.isDynamic()).put("pinned",s.isPinned()).put("manifest",s.isDeclaredInManifest());
   CharSequence disabled=s.getDisabledMessage();if(disabled!=null)value.put("disabled",disabled.toString());
   iconBudget-=addIcon(api,launcher,userId,s,value,iconBudget);result.put(value);
  }
  String payload=result.toString();if(payload.getBytes(java.nio.charset.StandardCharsets.UTF_8).length>ShortcutCatalog.MAX_OUTPUT-64)throw new IllegalStateException("Shortcut catalog is too large; no partial catalog returned");
  System.out.println("EDGE_SHORTCUTS="+payload);
 }catch(Throwable e){e.printStackTrace(System.err);System.exit(1);}}
 static int parseUser(String input){if(input==null||!input.matches("0|[1-9][0-9]{0,4}"))throw new IllegalArgumentException("Canonical Android user required");int user=Integer.parseInt(input);if(user>21474)throw new IllegalArgumentException("Android user out of range");return user;}
 private static int addIcon(Class<?> api,Object launcher,int user,ShortcutInfo shortcut,JSONObject value,int budget){
  try{
   int resource=(Integer)api.getMethod("getShortcutIconResId",String.class,String.class,String.class,int.class).invoke(launcher,"android",shortcut.getPackage(),shortcut.getId(),user);
   if(resource>0){value.put("iconRes",resource);return 0;}if(budget<24576)return 0;
   try(ParcelFileDescriptor fd=(ParcelFileDescriptor)api.getMethod("getShortcutIconFd",String.class,String.class,String.class,int.class).invoke(launcher,"android",shortcut.getPackage(),shortcut.getId(),user)){
    if(fd==null)return 0;BitmapFactory.Options bounds=new BitmapFactory.Options();bounds.inJustDecodeBounds=true;BitmapFactory.decodeFileDescriptor(fd.getFileDescriptor(),null,bounds);
    int largest=Math.max(bounds.outWidth,bounds.outHeight);if(largest<=0||largest>16384)return 0;BitmapFactory.Options options=new BitmapFactory.Options();options.inSampleSize=1;while(largest/options.inSampleSize>144)options.inSampleSize*=2;
    Bitmap bitmap=BitmapFactory.decodeFileDescriptor(fd.getFileDescriptor(),null,options);if(bitmap==null)return 0;
    Bitmap small=null;try{small=Bitmap.createScaledBitmap(bitmap,72,72,true);ByteArrayOutputStream png=new ByteArrayOutputStream();small.compress(Bitmap.CompressFormat.PNG,100,png);if(png.size()<=24576){value.put("icon",java.util.Base64.getEncoder().encodeToString(png.toByteArray()));return png.size();}}finally{if(small!=null&&small!=bitmap)small.recycle();bitmap.recycle();}
   }
  }catch(Exception unavailable){/* Use the actual publisher application icon when a shortcut icon is absent. */}
  return 0;
 }
 private ShortcutBridge(){}
}

