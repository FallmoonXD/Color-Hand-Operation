package dev.edgeflow.lspfixture;

import android.app.Activity;
import android.os.Bundle;
import android.util.Log;
import android.view.*;
import android.widget.*;

public class ProbeActivity extends Activity {
    int clicks;
    TextView label;
    @Override public void onCreate(Bundle state){
        super.onCreate(state);getWindow().setDecorFitsSystemWindows(false);
        FrameLayout root=new FrameLayout(this);root.setBackgroundColor(0xffefeff4);setContentView(root);
        label=new TextView(this);label.setTextColor(0xff000000);label.setTextSize(22);label.setGravity(Gravity.CENTER);
        root.addView(label,new FrameLayout.LayoutParams(-1,-1));showStatus();
        for(int i=0;i<2;i++){
            String side=i==0?"left":"right";Button button=new Button(this);button.setText(side);button.setContentDescription("pilot "+side);button.setPadding(0,0,0,0);
            FrameLayout.LayoutParams p=new FrameLayout.LayoutParams(dp(50),dp(200),Gravity.CENTER_VERTICAL|(i==0?Gravity.LEFT:Gravity.RIGHT));
            root.addView(button,p);button.setOnClickListener(v->{clicks++;showStatus();Log.i("CHOLspFixture","CLICK "+side);});
        }
        EditText input=new EditText(this);input.setSingleLine(true);input.setHint("Input method test");input.setContentDescription("pilot text input");
        FrameLayout.LayoutParams textLayout=new FrameLayout.LayoutParams(dp(240),dp(60),Gravity.TOP|Gravity.CENTER_HORIZONTAL);textLayout.topMargin=dp(100);
        root.addView(input,textLayout);root.setFocusableInTouchMode(true);root.requestFocus();
    }
    int dp(float value){return Math.round(value*getResources().getDisplayMetrics().density);}
    void showStatus(){label.setText(getClass().getSimpleName()+" clicks="+clicks);}
    @Override public boolean dispatchTouchEvent(MotionEvent event){
        int action=event.getActionMasked();
        if(action!=MotionEvent.ACTION_MOVE)Log.i("CHOLspFixture","TOUCH "+MotionEvent.actionToString(event.getAction())+" count="+event.getPointerCount());
        return super.dispatchTouchEvent(event);
    }
}
