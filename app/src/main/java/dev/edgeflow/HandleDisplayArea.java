package dev.edgeflow;

import android.content.Context;
import android.content.res.Configuration;
import android.content.res.Resources;
import android.graphics.Insets;
import android.graphics.Point;
import android.graphics.Rect;
import android.view.DisplayCutout;
import android.view.Surface;
import android.view.WindowInsets;
import android.view.WindowManager;
import android.view.WindowMetrics;

/** Public-SDK display geometry. IME and transient bar visibility never change its baseline. */
final class HandleDisplayArea {
    static final class Snapshot {
        final GestureHandleConfig.Area current,portrait;
        Snapshot(GestureHandleConfig.Area current,GestureHandleConfig.Area portrait){this.current=current;this.portrait=portrait;}
    }
    static Snapshot read(Context context){return read(context,true);}
    static GestureHandleConfig.Area current(Context context){return read(context,false).current;}
    private static Snapshot read(Context context,boolean portraitRequired){
        WindowManager manager=(WindowManager)context.getSystemService(Context.WINDOW_SERVICE);
        if(manager==null)throw new IllegalStateException("Display metrics unavailable");
        WindowMetrics metrics=manager.getMaximumWindowMetrics();Rect bounds=metrics.getBounds();
        int width=bounds.width(),height=bounds.height();
        if(width<2||height<2){Point size=new Point();manager.getDefaultDisplay().getRealSize(size);width=size.x;height=size.y;}
        WindowInsets insets=metrics.getWindowInsets();
        Insets safe=insets.getInsetsIgnoringVisibility(WindowInsets.Type.statusBars()|WindowInsets.Type.navigationBars()|WindowInsets.Type.displayCutout());
        GestureHandleConfig.Area current=GestureHandleConfig.safeArea(width,height,safe.left,safe.top,safe.right,safe.bottom);
        if(!portraitRequired)return new Snapshot(current,null);
        int rotation=manager.getDefaultDisplay().getRotation();
        if(width<=height&&rotation==Surface.ROTATION_0)return new Snapshot(current,current);
        // Legacy percentages were created against the upright portrait display.
        // A first launch in landscape must not convert against its shorter height.
        int portraitWidth=Math.min(width,height),portraitHeight=Math.max(width,height);
        Configuration configuration=new Configuration(context.getResources().getConfiguration());
        configuration.orientation=Configuration.ORIENTATION_PORTRAIT;
        float density=context.getResources().getDisplayMetrics().density;
        configuration.screenWidthDp=Math.round(portraitWidth/density);configuration.screenHeightDp=Math.round(portraitHeight/density);
        Resources resources=context.createConfigurationContext(configuration).getResources();
        Insets cutout=Insets.NONE;DisplayCutout dc=insets.getDisplayCutout();
        if(dc!=null)cutout=Insets.of(dc.getSafeInsetLeft(),dc.getSafeInsetTop(),dc.getSafeInsetRight(),dc.getSafeInsetBottom());
        int[] upright=unrotate(cutout.left,cutout.top,cutout.right,cutout.bottom,rotation);
        int top=Math.max(upright[1],dimension(resources,"status_bar_height"));
        Insets navigation=insets.getInsetsIgnoringVisibility(WindowInsets.Type.navigationBars());
        boolean hasNavigation=navigation.left!=0||navigation.top!=0||navigation.right!=0||navigation.bottom!=0;
        int bottom=Math.max(upright[3],hasNavigation?dimension(resources,"navigation_bar_height"):0);
        return new Snapshot(current,GestureHandleConfig.safeArea(portraitWidth,portraitHeight,upright[0],top,upright[2],bottom));
    }
    /** ROTATION_90 moves the natural top cutout to the current left edge. */
    static int[] unrotate(int left,int top,int right,int bottom,int rotation){
        switch(rotation){case Surface.ROTATION_90:return new int[]{bottom,left,top,right};case Surface.ROTATION_180:return new int[]{right,bottom,left,top};case Surface.ROTATION_270:return new int[]{top,right,bottom,left};default:return new int[]{left,top,right,bottom};}
    }
    private static int dimension(Resources resources,String name){int id=resources.getIdentifier(name,"dimen","android");return id==0?0:resources.getDimensionPixelSize(id);}
    private HandleDisplayArea(){}
}
