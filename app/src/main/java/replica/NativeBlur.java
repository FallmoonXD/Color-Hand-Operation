package replica;

import android.content.Context;
import android.os.*;
import java.util.*;

/** Main-thread owner; the root helper holds only the original service binding. */
final class NativeBlur {
    static final Handler main=new Handler(Looper.getMainLooper());
    static final Set<NativeMaterial> clients=new HashSet<>();
    static IBinder service;
    static volatile IBinder connection;
    private static Context context;
    private static boolean launching;
    private static boolean capabilityChecked;
    private static final NativeBlurPolicy policy=new NativeBlurPolicy();
    private static final long STARTUP_TIMEOUT_MS=20000;
    private static IBinder.DeathRecipient death;
    private static long retryAt;
    private static long prepareUntil;
    static final java.util.concurrent.atomic.AtomicInteger frames=new java.util.concurrent.atomic.AtomicInteger();
    static volatile int active;
    static volatile boolean online;
    static int starts, stops;

    static void prepare(Context c){context=c.getApplicationContext();prepareUntil=SystemClock.uptimeMillis()+1000;ensure();}

    static void add(NativeMaterial material, Context c) {
        context=c.getApplicationContext(); clients.add(material);active=clients.size();
        ensure(); if(service!=null) material.connect(service);
    }
    static void remove(NativeMaterial material) { clients.remove(material);active=clients.size(); }
    static boolean unavailable(){return !policy.available();}
    private static void fallback(String reason){
        policy.failed(true);android.util.Log.w("ReplicaBlurBridge","Using material fallback: "+reason);
        for(NativeMaterial material:new ArrayList<>(clients))material.nativeUnavailable();
    }
    static void ensure() {
        if(!policy.available()||service!=null||launching||context==null||(clients.isEmpty()&&SystemClock.uptimeMillis()>=prepareUntil)||SystemClock.uptimeMillis()<retryAt)return;
        if(!capabilityChecked){
            capabilityChecked=true;
            try{
                android.content.Intent intent=new android.content.Intent("com.oplus.BlurService").setPackage("com.oplus.blur");
                if(context.getPackageManager().resolveService(intent,0)==null){fallback("ColorOS blur service is not installed/enabled");return;}
            }catch(RuntimeException e){fallback("Blur service discovery failed");return;}
        }
        launching=true;
        final String apk=context.getApplicationInfo().sourceDir;
        // Android UID = userId * PER_USER_RANGE + appId (same mapping as ShortcutPicker).
        final int user=android.os.Process.myUid()/100000;
        final String authority=context.getPackageName()+".material"+
            (android.app.Application.getProcessName().endsWith(":service")?".service":"");
        new Thread(()->{
            int result=-1;java.lang.Process process=null;Thread reader=null;
            final java.util.concurrent.atomic.AtomicBoolean ready=new java.util.concurrent.atomic.AtomicBoolean();
            final java.util.concurrent.atomic.AtomicBoolean idle=new java.util.concurrent.atomic.AtomicBoolean();
            final java.util.concurrent.CountDownLatch startup=new java.util.concurrent.CountDownLatch(1);
            try {
                String command="CLASSPATH='"+apk.replace("'","'\\''")+"' app_process /system/bin replica.BlurServiceBridge "+authority+" "+user;
                process=new ProcessBuilder("su","-c",command).redirectErrorStream(true).start();
                final java.lang.Process running=process;
                reader=new Thread(()->{
                    try(java.io.BufferedReader r=new java.io.BufferedReader(new java.io.InputStreamReader(running.getInputStream()))){
                        String line;while((line=r.readLine())!=null){
                            android.util.Log.i("ReplicaBlurBridge",line);
                            if(line.equals("NATIVE_BLUR_READY")){ready.set(true);startup.countDown();}
                            else if(line.equals("NATIVE_BLUR_IDLE"))idle.set(true);
                        }
                    }catch(java.io.IOException ignored){}finally{startup.countDown();}
                },"material-service-output");reader.setDaemon(true);reader.start();
                if(!startup.await(STARTUP_TIMEOUT_MS,java.util.concurrent.TimeUnit.MILLISECONDS)||!ready.get()){
                    process.destroyForcibly();throw new IllegalStateException("Root authorization/native binding unavailable or timed out");
                }
                // The authorized helper deliberately lives while this process owns native clients.
                result=process.waitFor();reader.join(500);
            } catch(Exception e) {android.util.Log.w("ReplicaBlurBridge","Root bridge unavailable",e);}
            finally{
                if(process!=null){process.destroy();try{process.getInputStream().close();}catch(java.io.IOException ignored){}}
                if(reader!=null&&reader.isAlive())reader.interrupt();
            }
            final int exitCode=result;
            main.post(()->{
                cleanupBinding();launching=false;connected(null);
                boolean cleanIdle=exitCode==0&&ready.get()&&idle.get();
                if(!cleanIdle&&!policy.failed(!ready.get())){fallback("Native service unavailable; restart the app to retry");return;}
                retryAt=cleanIdle?0:SystemClock.uptimeMillis()+2000;
                if(!clients.isEmpty())main.postDelayed(NativeBlur::ensure,cleanIdle?0:2100);
            });
        },"material-service-owner").start();
    }
    /** AM associates this binding with our app thread, not the root helper's lifetime. */
    private static void cleanupBinding(){
        IBinder retained=connection;connection=null;if(retained==null)return;
        try{
            org.lsposed.hiddenapibypass.HiddenApiBypass.addHiddenApiExemptions("Landroid/app/ActivityManager;","Landroid/app/IActivityManager;","Landroid/app/IServiceConnection");
            Object am=Class.forName("android.app.ActivityManager").getMethod("getService").invoke(null);
            Class<?> conn=Class.forName("android.app.IServiceConnection");
            Object remote=Class.forName("android.app.IServiceConnection$Stub").getMethod("asInterface",IBinder.class).invoke(null,retained);
            Object removed=Class.forName("android.app.IActivityManager").getMethod("unbindService",conn).invoke(am,remote);
            android.util.Log.i("ReplicaBlurBridge","OWNER_UNBIND "+removed);
        }catch(Exception e){android.util.Log.e("ReplicaBlurBridge","Owner unbind failed",e);}
    }
    static void connected(IBinder next) {
        // A late callback after the startup deadline must not reactivate a rejected session.
        if(next!=null&&(!launching||!policy.available()))return;
        if(next==service)return;
        if(service!=null&&death!=null)try{service.unlinkToDeath(death,0);}catch(Exception ignored){}
        service=next;online=next!=null;
        for(NativeMaterial m:new ArrayList<>(clients))m.disconnected();
        if(next!=null)try{
            policy.connected();
            death=()->main.post(()->{if(service==next)connected(null);});
            next.linkToDeath(death,0);
            for(NativeMaterial m:new ArrayList<>(clients))m.connect(next);
        }catch(Exception e){connected(null);}
    }
    static Bundle stats(){Bundle b=new Bundle();b.putInt("active",active);b.putInt("frames",frames.get());b.putBoolean("connected",online);b.putInt("starts",starts);b.putInt("stops",stops);return b;}
}
