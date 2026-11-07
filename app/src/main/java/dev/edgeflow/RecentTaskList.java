package dev.edgeflow;

import android.accessibilityservice.AccessibilityService;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import org.json.JSONArray;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

/** Both recent-task surfaces consume the same ordered task snapshot. Main-thread subscriptions. */
final class RecentTaskList {
    interface Listener { void ready(JSONArray tasks,String error); }
    static final class Request {
        private final Listener listener;
        private boolean cancelled;
        Request(Listener listener){this.listener=listener;}
        void cancel(){
            cancelled=true;listeners.remove(this);
            if(listeners.isEmpty()&&job!=null){
                Job previous=job;
                // The full task panel can subscribe in the same turn that closes the launcher.
                main.post(()->{if(job==previous&&listeners.isEmpty()){job.stop();job=null;}});
            }
        }
        void deliver(JSONArray tasks,String error){if(!cancelled)listener.ready(tasks,error);}
    }
    private static final Handler main=new Handler(Looper.getMainLooper());
    private static final ExecutorService worker=Executors.newSingleThreadExecutor();
    private static final ArrayList<Request> listeners=new ArrayList<>();
    private static AccessibilityService owner;
    private static JSONArray cached;
    private static long timestamp;
    private static Job job;
    static void invalidate(AccessibilityService service){if(owner==service){cached=null;if(job!=null)job.cacheable=false;}}
    static void release(AccessibilityService service){
        if(owner!=service)return;
        for(Request r:listeners)r.cancelled=true;listeners.clear();
        if(job!=null)job.stop();job=null;cached=null;owner=null;
    }
    static Request read(AccessibilityService service,boolean refresh,Listener listener){
        if(service!=owner){release(owner);owner=service;}
        Request request=new Request(listener);
        if(job==null&&!refresh&&cached!=null&&SystemClock.uptimeMillis()-timestamp<5000){
            JSONArray data=cached;main.post(()->request.deliver(data,null));return request;
        }
        listeners.add(request);
        if(job==null){job=new Job(service.getApplicationInfo().sourceDir);worker.execute(job);}
        return request;
    }
    private static final class Job implements Runnable {
        final String apk;
        volatile boolean stopped;
        boolean cacheable=true;
        volatile Process process;
        Job(String apk){this.apk=apk;}
        void stop(){stopped=true;Process p=process;if(p!=null)p.destroyForcibly();}
        @Override public void run(){
            JSONArray data=null;String error=null;
            try{
                if(stopped)return;
                String cmd="CLASSPATH='"+apk.replace("'","'\\''")+"' app_process /system/bin dev.edgeflow.RecentTasksBridge list";
                Process p=new ProcessBuilder("su","-c",cmd).redirectErrorStream(true).start();
                process=p;if(stopped)return;
                ByteArrayOutputStream output=new ByteArrayOutputStream();
                Thread reader=new Thread(()->{
                    try(InputStream stream=p.getInputStream()){
                        byte[] bytes=new byte[2048];int n;
                        while((n=stream.read(bytes))!=-1){
                            if(output.size()+n>131072){p.destroyForcibly();return;}
                            output.write(bytes,0,n);
                        }
                    }catch(IOException ignored){}
                },"CHO recent tasks");
                reader.start();
                if(!p.waitFor(12,TimeUnit.SECONDS))throw new IOException("最近任务读取超时");
                reader.join(1000);if(reader.isAlive())throw new IOException("最近任务响应未结束");
                String raw=output.toString("UTF-8");int start=raw.indexOf("EDGE_RECENTS=");
                if(start<0||ActionExecutor.commandFailed(p.exitValue(),raw))throw new IOException("系统任务接口不可用或未获得 Root 授权");
                data=new JSONArray(raw.substring(start+13).trim());
            }catch(Exception failure){error=failure.getMessage()==null?"无法读取最近任务":failure.getMessage();}
            finally{if(process!=null){process.destroyForcibly();try{process.getInputStream().close();}catch(IOException ignored){}process=null;}}
            JSONArray result=data;String reason=error;
            main.post(()->{
                if(job!=this||stopped)return;
                job=null;cached=cacheable?result:null;timestamp=SystemClock.uptimeMillis();
                ArrayList<Request> waiting=new ArrayList<>(listeners);listeners.clear();
                for(Request r:waiting)r.deliver(result,reason);
            });
        }
    }
    private RecentTaskList(){}
}
