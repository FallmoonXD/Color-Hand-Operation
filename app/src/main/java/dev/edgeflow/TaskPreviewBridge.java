package dev.edgeflow;

import android.app.ActivityManager;
import android.content.ComponentName;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.ColorSpace;
import android.hardware.HardwareBuffer;
import java.io.ByteArrayOutputStream;
import java.lang.reflect.*;
import java.util.*;
import org.json.*;

/** Reads only system-maintained recent-task snapshots. No capture request and no screenshot files. */
public final class TaskPreviewBridge {
 public static void main(String[] args)throws Exception{
  if(args.length!=1||!args[0].matches("[0-9]+(,[0-9]+){0,29}"))throw new IllegalArgumentException("Expected 1..30 recent task IDs");
  Object service=Class.forName("android.app.ActivityTaskManager").getMethod("getService").invoke(null);Class<?> api=Class.forName("android.app.IActivityTaskManager");
  int user=(Integer)ActivityManager.class.getMethod("getCurrentUser").invoke(null);Object slice=api.getMethod("getRecentTasks",int.class,int.class,int.class).invoke(service,50,2,user);List<?> tasks=(List<?>)slice.getClass().getMethod("getList").invoke(slice);Set<Integer> allowed=new HashSet<>();
  for(Object info:tasks){Class<?> c=info.getClass();int id=c.getField("taskId").getInt(info);Intent base=(Intent)c.getField("baseIntent").get(info);ComponentName component=base==null?null:base.getComponent();if(component==null||id<0)continue;String pkg=component.getPackageName();if(pkg.equals("dev.edgeflow")||pkg.equals("com.android.systemui")||pkg.equals("com.android.launcher")||pkg.equals("com.oplus.launcher"))continue;try{if((Integer)c.getMethod("getActivityType").invoke(info)==2)continue;}catch(ReflectiveOperationException ignored){}allowed.add(id);}
  JSONArray results=new JSONArray();Set<Integer> seen=new HashSet<>();for(String text:args[0].split(",")){int id=Integer.parseInt(text);if(!seen.add(id))continue;JSONObject result=new JSONObject().put("taskId",id);if(!allowed.contains(id))result.put("reason","task_not_available");else read(service,api,id,result);results.put(result);}System.out.println("EDGE_TASK_PREVIEWS="+results);
 }
 private static void read(Object service,Class<?> api,int task,JSONObject out)throws JSONException{
  Object snapshot=null;HardwareBuffer buffer=null;Bitmap bitmap=null,software=null,small=null;
  try{
   try{Object manager=api.getMethod("getTaskSnapshotManager").invoke(service);Class<?> managerApi=Class.forName("android.window.ITaskSnapshotManager");int low=Class.forName("android.window.TaskSnapshotManager").getField("RESOLUTION_LOW").getInt(null);snapshot=managerApi.getMethod("getTaskSnapshot",int.class,long.class,int.class).invoke(manager,task,0L,low);}
   catch(NoSuchMethodException older){Method method;try{method=api.getMethod("getTaskSnapshot",int.class,boolean.class,boolean.class);snapshot=method.invoke(service,task,true,false);}catch(NoSuchMethodException old){method=api.getMethod("getTaskSnapshot",int.class,boolean.class);snapshot=method.invoke(service,task,true);}}
   if(snapshot==null){out.put("reason","no_snapshot");return;}Class<?> type=snapshot.getClass();if(!Boolean.TRUE.equals(type.getMethod("isRealSnapshot").invoke(snapshot))){out.put("reason","system_placeholder");return;}
   try{if(Boolean.TRUE.equals(type.getMethod("hasProtectedContent").invoke(snapshot))){out.put("reason","protected_content");return;}}catch(NoSuchMethodException old){}
   buffer=(HardwareBuffer)type.getMethod("getHardwareBuffer").invoke(snapshot);if(buffer==null||buffer.isClosed()){out.put("reason","buffer_unavailable");return;}if((buffer.getUsage()&HardwareBuffer.USAGE_PROTECTED_CONTENT)!=0){out.put("reason","protected_content");return;}
   ColorSpace color=(ColorSpace)type.getMethod("getColorSpace").invoke(snapshot);bitmap=Bitmap.wrapHardwareBuffer(buffer,color);if(bitmap==null){out.put("reason","buffer_unavailable");return;}software=bitmap.copy(Bitmap.Config.ARGB_8888,false);if(software==null){out.put("reason","copy_unavailable");return;}float scale=Math.min(1f,320f/Math.max(software.getWidth(),software.getHeight()));small=Bitmap.createScaledBitmap(software,Math.max(1,Math.round(software.getWidth()*scale)),Math.max(1,Math.round(software.getHeight()*scale)),true);ByteArrayOutputStream png=new ByteArrayOutputStream();small.compress(Bitmap.CompressFormat.PNG,100,png);if(png.size()>98304){out.put("reason","preview_size_limit");return;}
   out.put("width",small.getWidth()).put("height",small.getHeight()).put("image",Base64.getEncoder().encodeToString(png.toByteArray()));
  }catch(Throwable failure){Throwable cause=failure;while(cause.getCause()!=null)cause=cause.getCause();out.put("reason","unavailable").put("error",cause.getClass().getSimpleName()+": "+String.valueOf(cause.getMessage()));}
  finally{if(small!=null&&small!=software)small.recycle();if(software!=null&&software!=bitmap)software.recycle();if(bitmap!=null)bitmap.recycle();if(snapshot!=null)try{snapshot.getClass().getMethod("closeBuffer").invoke(snapshot);}catch(Exception ignored){}if(buffer!=null&&!buffer.isClosed())buffer.close();}
 }
 private TaskPreviewBridge(){}
}
