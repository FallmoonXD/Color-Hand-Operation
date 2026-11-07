package dev.edgeflow;

import android.animation.ValueAnimator;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.drawable.Drawable;
import android.view.View;
import android.view.animation.DecelerateInterpolator;

/** Fixed, opaque OHO-style fluid curve. No preference-selected animation variants. */
public final class GestureWaveView extends View {
    private final Paint body=new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Path curve=new Path();
    private final float density,maxWidth,iconSize;
    private final int arrowPixels;
    private final int[] location=new int[2];
    private final Drawable arrow;
    private String actionId="";
    private boolean left,visible;
    private float downY,baseY,halfHeight,depth,deflection,dragY;
    private int direction;
    private ValueAnimator returning;
    public GestureWaveView(Context c){
        super(c);density=getResources().getDisplayMetrics().density;
        maxWidth=135f*density/3.5f;
        arrowPixels=c.getResources().getDimensionPixelSize(R.dimen.oho_curve_arrow_size);
        iconSize=arrowPixels;
        body.setColor(Color.BLACK);
        // OHO 9.3.13 curve_style uses ic_arrow_forward in six directions, not
        // action icons or labels (SGPFluidAnimationView.a / dispatchDraw).
        arrow=c.getDrawable(R.drawable.oho_gesture_arrow).mutate();arrow.setTint(0xffe6e6e6);
        setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_NO);setFocusable(false);
    }
    public void setActionHint(String id){
        if(id==null||id.equals(actionId))return;actionId=id;
        // Keep the service contract without drawing additional UI in the wave.
        setContentDescription(ActionExecutor.title(id));
    }
    public void begin(boolean left,float y){
        stopAnimation();this.left=left;visible=true;depth=dragY=0;direction=0;
        float height=getHeight()>0?getHeight():getResources().getDisplayMetrics().heightPixels;
        getLocationOnScreen(location);downY=y-location[1];
        // OHO curve_style uses a ten-arrow-high window, lifted half a physical inch.
        halfHeight=Math.min(5f*arrowPixels,height/2f);
        float dpi=getResources().getDisplayMetrics().xdpi;
        if(!Float.isFinite(dpi)||dpi<=0)dpi=getResources().getDisplayMetrics().densityDpi;
        float top=Math.max(0,Math.min(height-2*halfHeight,(int)(downY-halfHeight-(int)dpi/2f)));
        baseY=top+halfHeight;deflection=offsetAt(downY);invalidate();
    }
    private float offsetAt(float touchY){
        float limit=Math.max(0,halfHeight-2f*arrowPixels);
        return Math.max(-limit,Math.min(limit,touchY-3f*arrowPixels-baseY));
    }
    public void update(float inward,float vertical,int direction){
        if(!visible)return;depth=Math.min(maxWidth,Math.max(0,inward));
        dragY=vertical;deflection=offsetAt(downY+dragY);this.direction=direction;postInvalidateOnAnimation();
    }
    public void release(){
        if(!visible)return;stopAnimation();final float startDepth=depth,verticalAtRelease=dragY;
        long duration=Math.max(50,(long)(Math.min(startDepth,2*iconSize)/(2*iconSize)*300));
        returning=ValueAnimator.ofFloat(1,0);returning.setDuration(duration);returning.setInterpolator(new DecelerateInterpolator(1));
        returning.addUpdateListener(a->{float f=(Float)a.getAnimatedValue();depth=startDepth*f;deflection=offsetAt(downY+verticalAtRelease*f);if(f<=.001f)visible=false;invalidate();});returning.start();
    }
    public void clear(){stopAnimation();visible=false;depth=deflection=0;invalidate();}
    private void stopAnimation(){if(returning!=null){returning.cancel();returning=null;}}
    @Override protected void onDetachedFromWindow(){clear();super.onDetachedFromWindow();}
    @Override protected void onDraw(Canvas canvas){
        super.onDraw(canvas);if(!visible||depth<.5f)return;
        int save=canvas.save();if(!left){canvas.translate(getWidth(),0);canvas.scale(-1,1);}
        float top=baseY-halfHeight,bottom=baseY+halfHeight,tip=baseY+deflection;
        // OHO uses control-y factors5/4 and3/4, mirrored about the tip's half.
        float local=halfHeight+deflection,h=halfHeight*2;
        float lower=local>=halfHeight?1.25f*local:.75f*local+h/4;
        float upper=local>=halfHeight?.75f*local:1.25f*local-h/4;
        curve.reset();curve.moveTo(0,bottom);curve.cubicTo(0,top+lower,depth,top+lower,depth,tip);curve.cubicTo(depth,top+upper,0,top+upper,0,top);curve.close();canvas.drawPath(curve,body);
        canvas.restoreToCount(save);
        float scale=Math.min(1,depth/Math.max(1,iconSize*1.25f)),size=iconSize*scale;
        if(size>8*density){
            float centerX=left?depth*.48f:getWidth()-depth*.48f;
            int a=canvas.save();canvas.translate(centerX,tip);
            canvas.rotate(left?direction*45f:180f-direction*45f);
            int r=Math.round(size/2);arrow.setBounds(-r,-r,r,r);arrow.draw(canvas);canvas.restoreToCount(a);
        }
    }
}
