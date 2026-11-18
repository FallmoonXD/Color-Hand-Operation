package dev.edgeflow.lsp;

import android.content.Context;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import org.json.JSONObject;
import java.io.BufferedReader;
import java.io.InputStreamReader;

/** Debug-only app_process runner, without installing or reloading LSPosed. */
public final class RootProbe {
    private static void output(String event,Bundle values){
        try{JSONObject json=new JSONObject();json.put("event",event);for(String key:values.keySet())json.put(key,values.get(key));System.out.println(json);System.out.flush();}
        catch(Exception failure){throw new IllegalStateException(failure);}
    }
    public static void main(String[] args) {
        try{run(args);}catch(Throwable failure){failure.printStackTrace(System.err);System.exit(2);}
    }
    private static void bootstrapSharedMemory()throws ReflectiveOperationException {
        final Class<?> memory;
        try{memory=Class.forName("com.android.internal.os.ApplicationSharedMemory");}
        catch(ClassNotFoundException olderAndroid){return;}
        try{memory.getMethod("getInstance").invoke(null);}
        catch(java.lang.reflect.InvocationTargetException missing){
            if(!(missing.getCause() instanceof IllegalStateException))throw missing;
            // app_process has not received ApplicationBindData. This is private
            // memory for this short-lived test process, never the system's map.
            Object local=memory.getMethod("create").invoke(null);
            memory.getMethod("setInstance",memory).invoke(null,local);
        }
    }
    private static void run(String[] args)throws Exception {
        if(android.os.Process.myUid()!=0)throw new SecurityException("Root probe requires UID 0");
        if(args.length!=2 || !(args[0].equals("observe") || args[0].equals("capture")))throw new IllegalArgumentException("observe|capture durationSeconds");
        long duration=Long.parseLong(args[1])*1000;
        if(duration<1000 || duration>NativeInputSession.MAX_DURATION_MS)throw new IllegalArgumentException("Duration 1..30 seconds");
        Bundle process=new Bundle();process.putInt("pid",android.os.Process.myPid());process.putInt("uid",android.os.Process.myUid());output("starting",process);
        Looper.prepareMainLooper();Handler main=new Handler(Looper.getMainLooper());
        bootstrapSharedMemory();
        Class<?> thread=Class.forName("android.app.ActivityThread");Object runtime=thread.getMethod("systemMain").invoke(null);
        Context context=(Context)thread.getMethod("getSystemContext").invoke(runtime);
        output("context-ready",process);
        NativeInputSession session=new NativeInputSession(context,main,args[0].equals("capture"),duration,b->output("state",b));
        output("ready",session.snapshot());
        Runnable finish=()->{session.close();output("finished",session.snapshot());System.exit(0);};
        main.postDelayed(finish,duration+50);
        Thread input=new Thread(()->{
            try{BufferedReader reader=new BufferedReader(new InputStreamReader(System.in));String line;
                while((line=reader.readLine())!=null){if(line.equals("stop")){main.post(finish);return;}}
                main.post(finish);
            }catch(Exception failure){main.post(finish);}
        },"CHO-probe-stop");input.setDaemon(true);input.start();
        Looper.loop();
    }
}
