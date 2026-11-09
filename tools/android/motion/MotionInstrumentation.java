package replica;

import android.app.Activity;
import android.app.Instrumentation;
import android.content.Context;
import android.os.Bundle;
import dalvik.system.PathClassLoader;

/** Run in the fixture's process so instrumentation never stops CHO's accessibility service. */
public final class MotionInstrumentation extends Instrumentation {
    @Override public void onCreate(Bundle args){super.onCreate(args);start();}
    @Override public void onStart(){
        Bundle result=new Bundle();
        Activity host=null;
        try{
            Context context=getTargetContext();
            String cho=context.getPackageManager().getApplicationInfo("dev.edgeflow",0).sourceDir;
            String path=context.getApplicationInfo().sourceDir+":"+cho;
            ClassLoader isolated=new PathClassLoader(path,ClassLoader.getSystemClassLoader().getParent());
            Class<?> check=isolated.loadClass("replica.NativeMotionCheck");
            runOnMainSync(()->{
                try{
                    Object count=check.getMethod("run",Context.class).invoke(null,context);
                    result.putString("motion","PASS "+count+" live ColorOS motion comparisons");
                    result.putString("preview_ms",(String)check.getMethod("previewDurations",Context.class).invoke(null,context));
                }catch(Throwable error){result.putString("error",android.util.Log.getStackTraceString(error));}
            });
            if(!result.containsKey("error")){
                host=startActivitySync(new android.content.Intent(context,MotionActivity.class).addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK));
                check.getMethod("interactions",Instrumentation.class,Activity.class).invoke(null,this,host);
                result.putString("input","PASS attached controls, reversal, exit callback ownership, transformed glide targets and synchronized previews");
            }
        }catch(Throwable error){result.putString("error",android.util.Log.getStackTraceString(error));}
        finally{if(host!=null){Activity end=host;runOnMainSync(end::finish);}}
        finish(result.containsKey("error")?0:Activity.RESULT_OK,result);
    }
}
