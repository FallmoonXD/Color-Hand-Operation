package replica;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.RectF;
import android.view.View;
import android.widget.FrameLayout;

/** ItemResizeFrame's outline and the trailing lower arc drawn by IResizeFramePainter. */
final class WidgetSelectionFrame extends FrameLayout {
    private final LauncherWidgetStyle style;
    private final Paint paint=new Paint(Paint.ANTI_ALIAS_FLAG);
    private final RectF bounds=new RectF(),arc=new RectF();
    private final ColorControlMotion reveal,pressed;
    private final LauncherFramePath outline;
    private final int border,handle,thin,thick,grip,wideGrip;
    private float radius;private int spanX,spanY;
    private boolean resizable;
    WidgetSelectionFrame(Context context,LauncherWidgetStyle style){
        super(context);this.style=style;outline=new LauncherFramePath(context);setWillNotDraw(false);setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_NO);
        border=style.color("item_resize_frame_dark_color",0x99ffffff);handle=style.color("item_resize_handle_dark_color",0xffffffff);
        thin=style.dim("item_resize_frame_stroke_width_min",2);thick=style.dim("item_resize_frame_stroke_width_max",4);
        grip=style.dim("item_handle_stroke_width_min",6);wideGrip=style.dim("item_handle_stroke_width_max",8);
        reveal=new ColorControlMotion(this,0);pressed=new ColorControlMotion(this,0);
        paint.setStyle(Paint.Style.STROKE);paint.setStrokeCap(Paint.Cap.ROUND);
    }
    void select(WidgetGrid.Cell g,boolean canResize){
        resizable=canResize;shape(g);
        setVisibility(VISIBLE);reveal.animate(1,.25f,0);invalidate();
    }
    void shape(WidgetGrid.Cell g){
        if(spanX==g.w&&spanY==g.h)return;
        spanX=g.w;spanY=g.h;radius=g.w<=1||g.h<=1?style.dim("handel_radius_1xx",16):style.dim("handel_radius_2x2",18);invalidate();
    }
    void sizing(boolean active){pressed.animate(active?1:0,.25f,0);}
    void clear(){pressed.animate(0,.25f,0);reveal.animate(0,.25f,0);}
    boolean hit(float x,float y,WidgetGrid.Cell g){
        int hot=style.dim(g.w==1&&g.h==1?"item_Handle_hot_region_1X1":"item_Handle_hot_region",g.w==1&&g.h==1?32:46);
        return resizable&&y>=getHeight()-hot&&(getLayoutDirection()==View.LAYOUT_DIRECTION_RTL?x<=hot:x>=getWidth()-hot);
    }
    @Override protected void onDraw(Canvas c){
        float blend=ColorControlPhysics.clamp(pressed.value()),alpha=ColorControlPhysics.clamp(reveal.value());
        if(alpha==0)return;
        float edge=thin+(thick-thin)*blend,weight=grip+(wideGrip-grip)*blend,inset=wideGrip*.5f;
        bounds.set(inset,inset,getWidth()-inset,getHeight()-inset);
        float r=Math.max(0,Math.min(radius,Math.min(bounds.width(),bounds.height())*.5f));
        paint.setColor(border);paint.setAlpha(Math.round(android.graphics.Color.alpha(border)*alpha));paint.setStrokeWidth(edge);
        c.drawPath(outline.outline(bounds,r+edge*.5f,spanX,spanY),paint);
        if(!resizable)return;
        boolean rtl=getLayoutDirection()==View.LAYOUT_DIRECTION_RTL;
        float cx=rtl?bounds.left+r:bounds.right-r,cy=bounds.bottom-r;
        arc.set(cx-r,cy-r,cx+r,cy+r);arc.offset(rtl?-edge*.5f:edge*.5f,edge*.5f);
        paint.setColor(handle);paint.setAlpha(Math.round(android.graphics.Color.alpha(handle)*alpha));paint.setStrokeWidth(weight);
        c.drawArc(arc,rtl?90:0,90,false,paint);
    }
    @Override protected void onDetachedFromWindow(){reveal.cancel();pressed.cancel();super.onDetachedFromWindow();}
}
