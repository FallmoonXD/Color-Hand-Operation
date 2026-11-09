package replica;

import android.content.Context;
import android.view.WindowManager;
import java.io.*;
import java.util.concurrent.*;
import java.util.function.BooleanSupplier;

/** The panel's background workers share one ordered pipe to the privileged helper. */
final class QuickControlsSession {
    private static volatile boolean previouslyAuthorized;
    private final String command;
    private final Object exchange=new Object();
    private final BlockingQueue<String> replies=new LinkedBlockingQueue<>();
    private Process process;
    private OutputStream input;
    private boolean ready,closed;
    QuickControlsSession(Context context,BrightnessRangePolicy.Range range){
        int display=context.getSystemService(WindowManager.class).getDefaultDisplay().getDisplayId();
        int user=android.os.Process.myUid()/100000;
        String apk=context.getApplicationInfo().sourceDir.replace("'","'\"'\"'");
        command="CLASSPATH='"+apk+"' exec app_process /system/bin replica.QuickControlsBridge "+display+" "+user+" "+(range==null?"-1 -1":range.min+" "+range.max);
    }
    static boolean canPrewarm(){return previouslyAuthorized;}
    synchronized boolean isReady(){return ready&&!closed;}
    void connect()throws Exception{
        synchronized(exchange){connectLocked();}
    }
    private void connectLocked()throws Exception{
        synchronized(this){
            if(closed)throw new EOFException();
            if(ready)return;
            process=new ProcessBuilder("su","-c",command).redirectErrorStream(true).start();
            input=process.getOutputStream();
            Process child=process;
            Thread reader=new Thread(()->{
                try(BufferedReader lines=new BufferedReader(new InputStreamReader(child.getInputStream()))){
                    for(String line;(line=lines.readLine())!=null;){
                        if(line.equals("READY")||line.equals("APPLIED")||line.startsWith("KEY "))replies.offer(line);
                        else android.util.Log.d("ChoQuickControls",line);
                    }
                }catch(IOException ignored){}finally{replies.offer("EOF");}
            },"cho-brightness-replies");reader.setDaemon(true);reader.start();
        }
        if(!"READY".equals(replies.poll(15,TimeUnit.SECONDS)))throw new IOException("Quick controls helper unavailable");
        synchronized(this){if(closed)throw new EOFException();ready=true;previouslyAuthorized=true;}
    }
    void apply(String request,BooleanSupplier valid)throws Exception{
        synchronized(exchange){
            connectLocked();if(!valid.getAsBoolean())return;
            if(!"APPLIED".equals(send(request)))throw new IOException("Brightness request did not complete");
        }
    }
    int mediaKey()throws Exception{
        synchronized(exchange){
            connectLocked();String key=send("media");
            if("KEY 126".equals(key))return android.view.KeyEvent.KEYCODE_MEDIA_PLAY;
            if("KEY 127".equals(key))return android.view.KeyEvent.KEYCODE_MEDIA_PAUSE;
            if("KEY 85".equals(key))return android.view.KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE;
            throw new IOException("No media-key reply");
        }
    }
    private String send(String request)throws Exception{
        synchronized(this){
            if(closed)throw new EOFException();
            input.write((request+"\n").getBytes(java.nio.charset.StandardCharsets.US_ASCII));input.flush();
        }
        return replies.poll(3,TimeUnit.SECONDS);
    }
    synchronized void close(){
        if(closed)return;closed=true;
        try{if(input!=null)input.close();}catch(IOException ignored){}
        if(process!=null&&!ready)process.destroy();
        replies.offer("EOF");
    }
}
