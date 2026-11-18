package dev.edgeflow.lspfixture;

import android.content.Context;
import android.graphics.PixelFormat;
import android.os.*;
import android.view.*;
import android.widget.TextView;

final class WindowProbe {
    static final IBinder lifetime=new Binder();
    static IBinder lastToken;
    static TextView view;
    static WindowManager manager;
    static Bundle mount(Context c,IBinder token,int type){
        lastToken=token;
        Bundle result=new Bundle();
        try{
            close();manager=c.getSystemService(WindowManager.class);view=new TextView(c);view.setBackgroundColor(0xffff4400);view.setText("CHO token");view.setTextColor(0xffffffff);
            WindowManager.LayoutParams p=new WindowManager.LayoutParams(350,180,type,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE|WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE|WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,PixelFormat.TRANSLUCENT);
            p.token=token;p.gravity=Gravity.TOP|Gravity.LEFT;p.x=300;p.y=600;p.setTitle("CHO delegated token probe");p.setFitInsetsTypes(0);
            manager.addView(view,p);result.putBoolean("window_added",true);
            new Handler(Looper.getMainLooper()).postDelayed(WindowProbe::close,12000);
        }catch(RuntimeException failure){result.putString("window_error",failure.toString());close();}
        return result;
    }
    static void close(){if(view!=null){try{manager.removeViewImmediate(view);}catch(RuntimeException ignored){}view=null;}}
}
