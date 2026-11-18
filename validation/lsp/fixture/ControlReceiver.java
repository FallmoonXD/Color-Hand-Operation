package dev.edgeflow.lspfixture;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.ResultReceiver;
import android.os.Parcel;
import java.io.File;
import java.io.FileOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.atomic.AtomicBoolean;
import org.json.JSONObject;

/** Shell/DUMP-only test entry. The SystemUI request runs as the fixture UID. */
public final class ControlReceiver extends BroadcastReceiver {
    @Override public void onReceive(Context context,Intent request) {
        String id=request.getStringExtra("request_id"), operation=request.getStringExtra("operation");
        if(id==null || !id.matches("[a-zA-Z0-9_-]{1,64}"))return;
        PendingResult pending=goAsync();Handler main=new Handler(Looper.getMainLooper());
        AtomicBoolean finished=new AtomicBoolean();
        java.util.function.Consumer<Bundle> complete=values->{
            if(!finished.compareAndSet(false,true))return;
            try{
                JSONObject result=new JSONObject();for(String key:values.keySet())result.put(key,values.get(key));
                result.put("client_uid",android.os.Process.myUid());
                result.put("client_permission",context.checkSelfPermission("dev.edgeflow.lsp.pilot.CONTROL")==PackageManager.PERMISSION_GRANTED);
                File directory=new File(context.getFilesDir(),"control");directory.mkdirs();
                try(FileOutputStream file=new FileOutputStream(new File(directory,id+".json"))){file.write(result.toString().getBytes(StandardCharsets.UTF_8));}
            }catch(Exception failure){android.util.Log.e("CHOLspControl","Cannot store test result",failure);}
            finally{pending.finish();}
        };
        if("window_probe_reuse".equals(operation)){
            complete.accept(WindowProbe.mount(context,WindowProbe.lastToken,2032));return;
        }
        if(operation!=null&&operation.startsWith("persistent_")){
            new Thread(()->{
                try{
                    String module=request.getStringExtra("module_package");
                    String authority="dev.edgeflow".equals(module)?"dev.edgeflow.lsp.persistent":"dev.edgeflow.lsp.pilot.persistent";
                    Bundle result=context.getContentResolver().call(android.net.Uri.parse("content://"+authority),request.getStringExtra("operation").substring(11),null,null);
                    if(result==null)throw new IllegalStateException("No persistent controller response");complete.accept(result);
                }catch(RuntimeException failure){Bundle result=new Bundle();result.putString("provider_error",failure.toString());complete.accept(result);}
            },"CHO-fixture-persistent").start();return;
        }
        String profile=request.getStringExtra("profile");
        if("snapshot".equals(operation)||("configured".equals(operation)&&profile==null))try{
            Bundle snapshot=context.getContentResolver().call(android.net.Uri.parse("content://dev.edgeflow.gesture_config"),"snapshot",null,null);
            if(snapshot==null||snapshot.getString("profile")==null)throw new IllegalStateException("No CHO snapshot");
            if("snapshot".equals(operation)){complete.accept(snapshot);return;}
            profile=snapshot.getString("profile");
        }catch(RuntimeException failure){Bundle result=new Bundle();result.putString("provider_error",failure.toString());complete.accept(result);return;}
        ResultReceiver reply=new ResultReceiver(main){@Override protected void onReceiveResult(int code,Bundle values){
            if(values.getBinder("window_token")!=null)complete.accept(WindowProbe.mount(context,values.getBinder("window_token"),values.getInt("window_type",2032)));
            else complete.accept(values);
        }};
        Parcel parcel=Parcel.obtain();
        try{reply.writeToParcel(parcel,0);parcel.setDataPosition(0);reply=ResultReceiver.CREATOR.createFromParcel(parcel);}
        finally{parcel.recycle();}
        main.postDelayed(()->{Bundle timeout=new Bundle();timeout.putBoolean("timeout",true);complete.accept(timeout);},4000);
        if("update".equals(operation)){
            context.sendBroadcast(new Intent("dev.edgeflow.lsp.pilot.CONFIG_CHANGED").setPackage("com.android.systemui").putExtra("module_package",request.getStringExtra("module_package")).putExtra("profile",profile));
            operation="status";
        }
        Bundle host=new Bundle();host.putBinder("lifetime",WindowProbe.lifetime);
        context.sendBroadcast(new Intent("dev.edgeflow.lsp.pilot.CONTROL_REQUEST").setPackage("com.android.systemui").putExtra("module_package",request.getStringExtra("module_package")).putExtra("operation",operation).putExtra("profile",profile).putExtra("reply",reply).putExtra("window_host",host));
    }
}
