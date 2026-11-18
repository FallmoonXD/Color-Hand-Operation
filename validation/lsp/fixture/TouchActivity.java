package dev.edgeflow.lspfixture;

import android.app.Activity;
import android.os.Bundle;
import android.graphics.*;
import android.view.*;
import org.json.*;
import java.nio.charset.StandardCharsets;

/** Disposable target: real Android gesture detection, plus the delivered event sequence. */
public final class TouchActivity extends Activity {
    private int taps,doubles,longs,moves,cancels,downs,ups;
    private float scroll;
    private JSONArray events=new JSONArray();
    private View target;
    private void save(){
        try{JSONObject state=new JSONObject().put("taps",taps).put("doubles",doubles).put("longs",longs).put("moves",moves).put("cancels",cancels).put("downs",downs).put("ups",ups).put("scroll",scroll).put("events",events);
            try(var out=openFileOutput("touch_state.json",MODE_PRIVATE)){out.write(state.toString().getBytes(StandardCharsets.UTF_8));}
        }catch(Exception failure){android.util.Log.e("CHOTouchFixture","Cannot save",failure);}
        if(target!=null)target.invalidate();
    }
    @Override public void onCreate(Bundle state){
        super.onCreate(state);getWindow().setDecorFitsSystemWindows(false);
        GestureDetector detector=new GestureDetector(this,new GestureDetector.SimpleOnGestureListener(){
            public boolean onDown(MotionEvent e){return true;}
            public boolean onSingleTapUp(MotionEvent e){taps++;save();return true;}
            public boolean onDoubleTap(MotionEvent e){doubles++;save();return true;}
            public void onLongPress(MotionEvent e){longs++;save();}
            public boolean onScroll(MotionEvent a,MotionEvent b,float dx,float dy){scroll+=dy;save();return true;}
        });
        target=new View(this){
            final Paint paint=new Paint(Paint.ANTI_ALIAS_FLAG);
            @Override protected void onDraw(Canvas canvas){
                canvas.drawColor(0xffefeff4);paint.setColor(0xff287e55);canvas.drawCircle(getWidth()*.5f,getHeight()*.35f,70,paint);
                paint.setColor(0xff222222);paint.setTextSize(42);canvas.drawText("tap="+taps+" double="+doubles+" long="+longs+" cancel="+cancels,50,230,paint);
            }
            @Override public boolean onTouchEvent(MotionEvent e){
                int action=e.getActionMasked();if(action==0)downs++;if(action==1)ups++;if(action==2)moves++;if(action==3)cancels++;
                if(events.length()<512)try{events.put(new JSONObject().put("action",action).put("time",e.getEventTime()).put("down",e.getDownTime()).put("x",e.getRawX()).put("y",e.getRawY()).put("device",e.getDeviceId()).put("tool",e.getToolType(0)).put("flags",e.getFlags()));}catch(JSONException ignored){}
                detector.onTouchEvent(e);save();return true;
            }
        };target.setContentDescription("touch target");setContentView(target);save();
    }
}
