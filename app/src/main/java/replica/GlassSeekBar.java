package replica;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RectF;
import android.view.MotionEvent;
import android.widget.SeekBar;

/** White filled capsules. The media track uses the side volume bar's touch light. */
public final class GlassSeekBar extends SeekBar {
    private final int trackColor;
    private final Paint paint=new Paint(Paint.ANTI_ALIAS_FLAG|Paint.FILTER_BITMAP_FLAG);
    private final RectF rect=new RectF();
    private final Path path=new Path();
    private final VolumeTrackLight light;

    public GlassSeekBar(Context context){this(context,false);}
    public GlassSeekBar(Context context,boolean volume){
        super(context);
        trackColor=volume?0x50ffffff:0x28e3e9f2;
        light=volume?new VolumeTrackLight(this):null;
        setPadding(0,0,0,0);setThumb(null);setSplitTrack(false);
        setBackground(null);setForeground(null);setStateListAnimator(null);setDefaultFocusHighlightEnabled(false);
    }
    @Override public boolean onTouchEvent(MotionEvent event){
        if(light!=null)light.touch(event);
        return super.onTouchEvent(event);
    }
    @Override protected void onDetachedFromWindow(){
        if(light!=null)light.reset();
        super.onDetachedFromWindow();
    }
    @Override protected synchronized void onDraw(Canvas canvas){
        float height=Math.min(getHeight(),PanelGeometry.px(getContext(),25));
        float top=(getHeight()-height)/2f,corner=height/2;
        rect.set(0,top,getWidth(),top+height);
        paint.setColor(trackColor);canvas.drawRoundRect(rect,corner,corner,paint);

        int save=canvas.save();
        path.rewind();path.addRoundRect(rect,corner,corner,Path.Direction.CW);canvas.clipPath(path);
        paint.setColor(isEnabled()?0xffffffff:0x60dce4f0);
        canvas.drawRect(0,top,getWidth()*getProgress()/Math.max(1f,getMax()),rect.bottom,paint);
        canvas.restoreToCount(save);
        if(light!=null)light.draw(canvas,rect,corner);
    }
}
