package probe;

import android.content.Context;
import android.net.Uri;
import android.os.Bundle;
import android.os.Looper;
import android.os.Binder;
import android.os.IBinder;
import java.util.ArrayList;

/** Explicit deployment helper, not an app watchdog. Applies only to EdgeFlow. */
public final class StartupPolicy {
 public static void main(String[] args)throws Exception {
  String authority="com.oplus.startup.provider";
  Class<?> amc=Class.forName("android.app.IActivityManager");
  Object am=Class.forName("android.app.ActivityManager").getMethod("getService").invoke(null);
  Binder token=new Binder();
  Object holder=amc.getMethod("getContentProviderExternal",String.class,int.class,IBinder.class,String.class).invoke(am,authority,0,token,"edgeflow-deployment");
  if(holder==null)throw new IllegalStateException("Provider unavailable");
  try {
  Object provider=holder.getClass().getField("provider").get(holder);
  Class<?> ac=Class.forName("android.content.AttributionSource");
  Object builder=Class.forName("android.content.AttributionSource$Builder").getConstructor(int.class).newInstance(0);
  builder=builder.getClass().getMethod("setPackageName",String.class).invoke(builder,"com.android.shell");
  Object identity=builder.getClass().getMethod("build").invoke(builder);
  java.lang.reflect.Method call=Class.forName("android.content.IContentProvider").getMethod("call",ac,String.class,String.class,String.class,Bundle.class);
  Bundle query=new Bundle();ArrayList<String> names=new ArrayList<>();names.add("dev.edgeflow");query.putStringArrayList("packageList",names);
  Bundle before=(Bundle)call.invoke(provider,identity,authority,"getStartupState",null,query);
  System.out.println("before allowed="+before.getBoolean("dev.edgeflow")+" canBeOpened="+before.getBoolean("dev.edgeflow_canBeOpened"));
  if(args.length==1 && args[0].equals("allow") && !before.getBoolean("dev.edgeflow")) {
   if(!before.getBoolean("dev.edgeflow_canBeOpened"))throw new IllegalStateException("System did not allow changing this entry");
   query.putBoolean("dev.edgeflow",true);call.invoke(provider,identity,authority,"setStartupState",null,query);
  }
  Bundle after=(Bundle)call.invoke(provider,identity,authority,"getStartupState",null,query);
  System.out.println("after allowed="+after.getBoolean("dev.edgeflow")+" canBeOpened="+after.getBoolean("dev.edgeflow_canBeOpened"));
  } finally {amc.getMethod("removeContentProviderExternal",String.class,IBinder.class).invoke(am,authority,token);}
  System.exit(0);
 }
}
