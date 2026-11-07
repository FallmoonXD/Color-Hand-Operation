package dev.edgeflow;
import android.app.ActivityManager;import android.content.ComponentName;import android.content.Intent;import android.os.Bundle;import org.json.*;import java.lang.reflect.*;import java.util.*;
/** Root-only structured adapter to Android task service. Never loads another APK. */
public final class RecentTasksBridge {
 public static void main(String[] args)throws Exception{
  Object service=Class.forName("android.app.ActivityTaskManager").getMethod("getService").invoke(null);Class<?> api=Class.forName("android.app.IActivityTaskManager");
  if(args.length>0&&args[0].equals("launch")){if(args.length!=2)throw new IllegalArgumentException("launch needs one task ID");int task=Integer.parseInt(args[1]);if(task<0)throw new IllegalArgumentException("Task ID must be non-negative");Object result=api.getMethod("startActivityFromRecents",int.class,Bundle.class).invoke(service,task,new Bundle());if(!(result instanceof Integer)||((Integer)result)<0)throw new IllegalStateException("Task launch rejected: "+result);System.out.println("TASK_STARTED "+result);return;}
  if(args.length!=1||!args[0].equals("list"))throw new IllegalArgumentException("Expected list or launch <task ID>");
  int user=(Integer)ActivityManager.class.getMethod("getCurrentUser").invoke(null);Object slice=api.getMethod("getRecentTasks",int.class,int.class,int.class).invoke(service,50,2,user);List<?> list=(List<?>)slice.getClass().getMethod("getList").invoke(slice);JSONArray out=new JSONArray();
  HashSet<Integer> seen=new HashSet<>();for(Object info:list){Class<?> cls=info.getClass();int task=cls.getField("taskId").getInt(info);if(task<0||!seen.add(task))continue;Intent base=(Intent)cls.getField("baseIntent").get(info);ComponentName component=base==null?null:base.getComponent();if(component==null)continue;String pkg=component.getPackageName();boolean home=pkg.equals("com.android.launcher")||pkg.equals("com.oplus.launcher");try{home=((Integer)cls.getMethod("getActivityType").invoke(info))==2;}catch(ReflectiveOperationException ignored){}if(pkg.equals("dev.edgeflow")||pkg.equals("com.android.systemui")||home)continue;out.put(new JSONObject().put("taskId",task).put("component",component.flattenToString()).put("package",pkg));}
  System.out.println("EDGE_RECENTS="+out.toString());
 }
 /** The launcher groups by package; the task switcher keeps every task in the snapshot. */
 static ArrayList<String> launcherEntries(JSONArray tasks,int limit){
  ArrayList<String> entries=new ArrayList<>();HashSet<String> packages=new HashSet<>();
  for(int i=0;tasks!=null&&i<tasks.length()&&entries.size()<limit;i++){
   JSONObject task=tasks.optJSONObject(i);if(task==null)continue;
   ComponentName component=ComponentName.unflattenFromString(task.optString("component"));int id=task.optInt("taskId",-1);
   if(component!=null&&id>=0&&packages.add(component.getPackageName()))entries.add("recent:"+id+":"+component.flattenToString());
  }
  return entries;
 }
 private RecentTasksBridge(){}
}
