package dev.edgeflow;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Path;
import android.graphics.Rect;
import android.graphics.Region;
import android.graphics.RegionIterator;
import android.view.AttachedSurfaceControl;
import android.view.DisplayCutout;
import android.view.View;
import android.view.ViewTreeObserver;
import android.view.WindowInsets;
import android.view.WindowManager;
import android.view.WindowMetrics;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/** Both the input window and its settings overlay use the same visible strip. */
class HandleStripView extends View implements ViewTreeObserver.OnPreDrawListener {
    private final boolean input;
    private final int[] origin=new int[2];
    private final Rect frame=new Rect();
    private final Region region=new Region();
    private final Path outline=new Path();
    private List<Rect> cutouts=Collections.emptyList();
    private boolean dirty=true,clipped;

    HandleStripView(Context context,boolean input){super(context);this.input=input;}

    @Override protected void onAttachedToWindow(){
        super.onAttachedToWindow();dirty=true;readCutouts();getViewTreeObserver().addOnPreDrawListener(this);
    }
    @Override protected void onDetachedFromWindow(){
        getViewTreeObserver().removeOnPreDrawListener(this);super.onDetachedFromWindow();
    }
    @Override public WindowInsets onApplyWindowInsets(WindowInsets insets){
        readCutouts();return super.onApplyWindowInsets(insets);
    }
    private void readCutouts(){
        WindowMetrics m=getContext().getSystemService(WindowManager.class).getMaximumWindowMetrics();
        DisplayCutout c=m.getWindowInsets().getDisplayCutout();
        List<Rect> next=m.getBounds().width()>m.getBounds().height()&&c!=null?c.getBoundingRects():Collections.emptyList();
        if(!cutouts.equals(next)){cutouts=next;dirty=true;}
    }
    @Override public boolean onPreDraw(){
        getLocationOnScreen(origin);
        int x=origin[0],y=origin[1],w=getWidth(),h=getHeight();
        if(w==0||h==0||(!dirty&&x==frame.left&&y==frame.top&&w==frame.width()&&h==frame.height()))return true;
        AttachedSurfaceControl surface=input?getRootSurfaceControl():null;
        if(input&&surface==null){dirty=true;return true;}
        dirty=false;frame.set(x,y,x+w,y+h);region.set(0,0,w,h);clipped=false;
        for(Rect hole:cutouts)if(Rect.intersects(frame,hole)){
            region.op(hole.left-x,hole.top-y,hole.right-x,hole.bottom-y,Region.Op.DIFFERENCE);clipped=true;
        }
        if(clipped){outline.reset();region.getBoundaryPath(outline);}
        if(input){
            surface.setTouchableRegion(clipped?region:null);
            ArrayList<Rect> slices=new ArrayList<>();RegionIterator iterator=new RegionIterator(region);Rect part=new Rect();
            while(iterator.next(part))slices.add(new Rect(part));
            setSystemGestureExclusionRects(slices);
        }
        invalidate();return true;
    }
    boolean acceptsPoint(float x,float y){
        return !clipped||region.contains((int)Math.floor(x)-frame.left,(int)Math.floor(y)-frame.top);
    }
    @Override public void draw(Canvas canvas){
        if(!clipped){super.draw(canvas);return;}
        int save=canvas.save();canvas.clipPath(outline);super.draw(canvas);canvas.restoreToCount(save);
    }
}
