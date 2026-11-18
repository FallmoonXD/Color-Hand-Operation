package dev.edgeflow.lsp;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.os.ResultReceiver;
import android.os.Parcel;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.ScrollView;
import java.util.TreeSet;

/** Debug-only controls for the separate development package. */
public final class PilotActivity extends Activity {
    private final Handler main=new Handler();
    private TextView status;
    private int requestId;
    private boolean waiting;
    @Override public void onCreate(Bundle state) {
        super.onCreate(state);
        LinearLayout body=new LinearLayout(this);body.setOrientation(LinearLayout.VERTICAL);
        int padding=Math.round(20*getResources().getDisplayMetrics().density);body.setPadding(padding,padding*2,padding,padding);
        TextView title=new TextView(this);title.setText(R.string.pilot_title);title.setTextSize(24);body.addView(title);
        TextView description=new TextView(this);description.setText(R.string.pilot_description);body.addView(description);
        status=new TextView(this);status.setTextIsSelectable(true);
        int[] names={R.string.pilot_check,R.string.pilot_enable_persistent,R.string.pilot_disable_persistent,R.string.pilot_observe,R.string.pilot_capture,R.string.pilot_configured,R.string.pilot_stop};
        String[] operations={"status","persistent_enable","persistent_disable","observe","capture","configured","stop"};
        for(int i=0;i<names.length;i++){Button button=new Button(this);button.setText(names[i]);String op=operations[i];button.setOnClickListener(v->request(op));body.addView(button);}
        body.addView(status);
        ScrollView scroll=new ScrollView(this);scroll.addView(body);setContentView(scroll);request("status");
    }
    private void request(String operation) {
        if(operation.startsWith("persistent_")){
            int id=++requestId;waiting=true;status.setText(R.string.pilot_waiting);
            new Thread(()->{
                String failure=null;
                try{Bundle reply=getContentResolver().call(PersistentBridge.uri(),operation.substring(11),null,null);if(reply==null)throw new IllegalStateException("No controller response");if(!reply.getString("source_error","").isEmpty())throw new IllegalStateException(reply.getString("source_error"));}
                catch(RuntimeException error){failure=error.toString();}
                String result=failure;
                main.post(()->{if(id!=requestId||isDestroyed())return;waiting=false;if(result!=null)status.setText(result);else main.postDelayed(()->request("status"),150);});
            },"CHO-persistent-control").start();return;
        }
        String profile=null;
        if("configured".equals(operation))try{
            Bundle snapshot=getContentResolver().call(android.net.Uri.parse("content://dev.edgeflow.gesture_config"),"snapshot",null,null);
            if(snapshot==null||snapshot.getString("profile")==null)throw new IllegalStateException("CHO 未返回配置");
            profile=snapshot.getString("profile");
        }catch(RuntimeException failure){status.setText("读取 CHO 配置失败："+failure.getMessage());return;}
        int id=++requestId;waiting=true;status.setText(R.string.pilot_waiting);
        ResultReceiver reply=new ResultReceiver(main){
            @Override protected void onReceiveResult(int code,Bundle values){
                if(id!=requestId || isFinishing() || isDestroyed())return;
                waiting=false;
                StringBuilder text=new StringBuilder();for(String key:new TreeSet<>(values.keySet()))text.append(key).append(": ").append(values.get(key)).append('\n');status.setText(text);
            }
        };
        // Parcel the framework base type, not this app's anonymous subclass.
        Parcel parcel=Parcel.obtain();
        try{reply.writeToParcel(parcel,0);parcel.setDataPosition(0);reply=ResultReceiver.CREATOR.createFromParcel(parcel);}
        finally{parcel.recycle();}
        sendBroadcast(new Intent(PilotServer.ACTION).setPackage("com.android.systemui").putExtra("operation",operation).putExtra("profile",profile).putExtra("reply",reply));
        main.postDelayed(()->{if(id==requestId && waiting)status.setText(R.string.pilot_disconnected);},2500);
    }
    @Override public void onDestroy(){requestId++;main.removeCallbacksAndMessages(null);super.onDestroy();}
}
