package replica;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.RectF;
import android.graphics.Shader;
import android.graphics.drawable.ColorDrawable;
import android.util.AttributeSet;
import android.view.MotionEvent;
import android.widget.SeekBar;

/** COUI capsule track and textured thumb; SeekBar owns input, keys and accessibility. */
public final class ColorSlider extends SeekBar {
    private final Paint paint=new Paint(Paint.ANTI_ALIAS_FLAG);
    private final RectF track=new RectF(),fill=new RectF();
    private final float density;
    private final ColorControlMotion enlarge,thumbEnlarge,texture,cover,progress;
    private final ColorThumbMaterial material=new ColorThumbMaterial();
    private OnSeekBarChangeListener client;
    private boolean tracking,touchActive,touchCancelled;
    private LinearGradient thumbGradient;
    private float gradientTop=Float.NaN,gradientRadius;
    public ColorSlider(Context c){this(c,null);}
    public ColorSlider(Context c,AttributeSet attrs){this(c,attrs,0);}
    public ColorSlider(Context c,AttributeSet attrs,int style){
        super(c,attrs,style);density=getResources().getDisplayMetrics().density;
        setBackground(null);setProgressDrawable(new ColorDrawable(0));setThumb(new ColorDrawable(0));setSplitTrack(false);setThumbOffset(0);
        setMinHeight(Math.round(dp(36)));setMinimumHeight(Math.round(dp(48)));setPadding(Math.round(dp(14)),0,Math.round(dp(14)),0);
        enlarge=new ColorControlMotion(this,0);thumbEnlarge=new ColorControlMotion(this,0);texture=new ColorControlMotion(this,0);cover=new ColorControlMotion(this,1);progress=new ColorControlMotion(this,fraction());
        super.setOnSeekBarChangeListener(new OnSeekBarChangeListener(){
            public void onProgressChanged(SeekBar bar,int value,boolean fromUser){if(progress!=null){if(fromUser&&tracking)progress.snap(fraction());else progress.animate(fraction(),.3f,0);}if(client!=null)client.onProgressChanged(ColorSlider.this,value,fromUser);}
            public void onStartTrackingTouch(SeekBar bar){tracking=true;enlarge.animate(1,.5f,0);thumbEnlarge.animate(1,.5f,.3f);texture.animate(1,.3f,0);cover.animate(0,.5f,0);if(progress!=null)progress.snap(fraction());if(client!=null)client.onStartTrackingTouch(ColorSlider.this);}
            public void onStopTrackingTouch(SeekBar bar){tracking=false;if(client!=null)client.onStopTrackingTouch(ColorSlider.this);}
        });
        ColorOsTheme.watch(this,()->invalidate());
    }
    private float dp(float n){return n*density;}
    private float fraction(){return ColorControlPhysics.fraction(getProgress(),getMin(),getMax());}
    @Override public void setOnSeekBarChangeListener(OnSeekBarChangeListener listener){client=listener;}
    /** True during a cancelled gesture's stop callback; the next DOWN resets it. */
    public boolean isTouchCancelled(){return touchCancelled;}
    @Override public synchronized void setMin(int min){super.setMin(min);if(progress!=null)progress.snap(fraction());}
    @Override public synchronized void setMax(int max){super.setMax(max);if(progress!=null)progress.snap(fraction());}
    @Override public synchronized void setProgress(int value){super.setProgress(value);if(progress!=null&&!tracking)progress.snap(fraction());}
    @Override public void setProgress(int value,boolean animate){super.setProgress(value,false);if(progress!=null){if(animate)progress.animate(fraction(),.3f,0);else progress.snap(fraction());}}
    @Override protected synchronized void onDraw(Canvas canvas){
        float e=ColorControlPhysics.clamp(enlarge.value()),radius=dp(10+4*e),cy=(getPaddingTop()+getHeight()-getPaddingBottom())/2f;
        float start=getPaddingLeft(),end=Math.max(start,getWidth()-getPaddingRight());boolean rtl=getLayoutDirection()==LAYOUT_DIRECTION_RTL;
        float p=ColorControlPhysics.clamp(progress.value()),x=start+(end-start)*ColorControlPhysics.physicalFraction(p,rtl);
        track.set(Math.max(0,start-radius),cy-radius,Math.min(getWidth(),end+radius),cy+radius);boolean dark=ColorOsTheme.dark(getContext());int accent=ColorOsTheme.accent(getContext()),neutral=ColorOsTheme.text(getContext());
        paint.setStyle(Paint.Style.FILL);paint.setShader(null);paint.setColor(isEnabled()?ColorSwitch.alpha(neutral,dark?26:13):(dark?0xff262626:0xffebebeb));canvas.drawRoundRect(track,radius,radius,paint);
        if(p>0){paint.setColor(isEnabled()?accent:ColorOsTheme.disabled(getContext()));if(rtl)fill.set(x-radius,track.top,track.right,track.bottom);else fill.set(track.left,track.top,x+radius,track.bottom);canvas.drawRoundRect(fill,radius,radius,paint);}
        float r=dp(6+2*ColorControlPhysics.clamp(thumbEnlarge.value()));
        if(isEnabled()){
            boolean materialEnabled=ColorOsTheme.materialStroke(getContext());float t=materialEnabled?ColorControlPhysics.clamp(texture.value()):0;
            // COUI TexturedThumbRenderer: white layer C recedes on press, revealing
            // the 230 -> 140 alpha gradient and the translucent material layer.
            if(t>0){int layer=canvas.saveLayerAlpha(x-r,cy-r,x+r,cy+r,Math.round(255*t));if(thumbGradient==null||gradientTop!=cy-r||gradientRadius!=r){gradientTop=cy-r;gradientRadius=r;thumbGradient=new LinearGradient(0,cy+r,0,cy-r,new int[]{0xe6ffffff,0xe6ffffff,0x8cffffff},new float[]{0,.2f,1},Shader.TileMode.CLAMP);}paint.setShader(thumbGradient);paint.setAlpha(255);canvas.drawCircle(x,cy,r,paint);paint.setShader(null);material.draw(canvas,x,cy,r,dp(8));canvas.restoreToCount(layer);}
            paint.setColor(ColorSwitch.alpha(0xffffffff,Math.round(255*(materialEnabled?ColorControlPhysics.clamp(cover.value()):1))));canvas.drawCircle(x,cy,r,paint);
        }else{paint.setColor(dark?0xff737373:0xfffafafa);canvas.drawCircle(x,cy,r,paint);}
        if(isFocused()){paint.setStyle(Paint.Style.STROKE);paint.setStrokeWidth(dp(1.5f));paint.setColor(ColorSwitch.alpha(accent,170));fill.set(track);fill.inset(-dp(2),-dp(2));canvas.drawRoundRect(fill,radius+dp(2),radius+dp(2),paint);paint.setStyle(Paint.Style.FILL);}
    }
    @Override public boolean onTouchEvent(MotionEvent event){
        if(!isEnabled())return false;int action=event.getActionMasked();if(action==MotionEvent.ACTION_DOWN){touchCancelled=false;touchActive=true;}
        // End ownership before framework callbacks: a successful stop may rebuild
        // its page synchronously, and that detach must not synthesize another stop.
        if(action==MotionEvent.ACTION_UP||action==MotionEvent.ACTION_CANCEL)touchActive=false;
        if(action==MotionEvent.ACTION_CANCEL)touchCancelled=true;
        boolean handled=super.onTouchEvent(event);
        if(action==MotionEvent.ACTION_UP||action==MotionEvent.ACTION_CANCEL){enlarge.animate(0,.5f,0);thumbEnlarge.animate(0,.5f,0);texture.animate(0,.3f,0);cover.animate(1,.3f,0);}return handled;
    }
    @Override public void setEnabled(boolean enabled){if(!enabled&&enlarge!=null)finishTracking();super.setEnabled(enabled);invalidate();}
    private void finishTracking(){
        if(tracking||touchActive){touchCancelled=true;touchActive=false;MotionEvent cancel=MotionEvent.obtain(android.os.SystemClock.uptimeMillis(),android.os.SystemClock.uptimeMillis(),MotionEvent.ACTION_CANCEL,0,0,0);super.onTouchEvent(cancel);cancel.recycle();if(tracking){tracking=false;if(client!=null)client.onStopTrackingTouch(this);}}
        enlarge.snap(0);thumbEnlarge.snap(0);texture.snap(0);cover.snap(1);progress.snap(fraction());
    }
    @Override public void jumpDrawablesToCurrentState(){super.jumpDrawablesToCurrentState();if(enlarge!=null){enlarge.snap(0);thumbEnlarge.snap(0);texture.snap(0);cover.snap(1);progress.snap(fraction());}}
    @Override protected void onDetachedFromWindow(){finishTracking();material.clear();thumbGradient=null;super.onDetachedFromWindow();}
}
