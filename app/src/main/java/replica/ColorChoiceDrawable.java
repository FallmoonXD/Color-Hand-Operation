package replica;

import android.animation.ValueAnimator;
import android.graphics.Canvas;
import android.graphics.ColorFilter;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.PathMeasure;
import android.graphics.PixelFormat;
import android.graphics.RectF;
import android.graphics.drawable.Drawable;
import android.view.animation.PathInterpolator;
import android.widget.CompoundButton;

/** Geometry and 150+150ms pulse from Settings' COUI radio/check animated vectors. */
final class ColorChoiceDrawable extends Drawable {
    private final CompoundButton host;
    private final boolean radio;
    private final int size;
    private final Paint paint=new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Path tick=new Path(),visibleTick=new Path();
    private final PathMeasure tickMeasure=new PathMeasure();
    private final RectF box=new RectF();
    private static final PathInterpolator RADIO_OFF=new PathInterpolator(.33f,0,.67f,1);
    private static final PathInterpolator TICK_RETURN=new PathInterpolator(.3f,0,.633f,1);
    private final ColorControlMotion pressed;
    private ValueAnimator selection;
    private boolean checked,enabled=true,focused;
    private float fill,mark,pulse=1,markPulse=1;
    private int drawableAlpha=255;
    ColorChoiceDrawable(CompoundButton host,boolean radio){
        this.host=host;this.radio=radio;size=Math.round(24*host.getResources().getDisplayMetrics().density);checked=host.isChecked();fill=mark=checked?1:0;pressed=new ColorControlMotion(host,0);
        // Exact centerline from the animated vector's 72x72 coordinates and its
        // (36,36.764) group translation, divided by three.
        tick.moveTo((36-14.12f)/3,(36.764f-1)/3);tick.lineTo((36-3.44f)/3,(36.764f+9.69f)/3);tick.lineTo((36+16.97f)/3,(36.764f-10.91f)/3);tickMeasure.setPath(tick,false);
    }
    @Override public int getIntrinsicWidth(){return size;}
    @Override public int getIntrinsicHeight(){return size;}
    @Override public boolean isStateful(){return true;}
    private static boolean has(int[] states,int key){for(int state:states)if(state==key)return true;return false;}
    @Override protected boolean onStateChange(int[] states){
        boolean next=has(states,android.R.attr.state_checked);enabled=has(states,android.R.attr.state_enabled);focused=has(states,android.R.attr.state_focused);
        pressed.animate(enabled&&has(states,android.R.attr.state_pressed)?1:0,.3f,0);
        if(next!=checked){checked=next;animateSelection();}else if(!enabled)stop();
        invalidateSelf();return true;
    }
    static float choiceProgress(boolean radio,boolean checked,boolean filling,float ms){
        int offset=0,duration=300;
        if(radio&&!checked)duration=filling?200:267;
        if(!radio&&filling){duration=150;offset=checked?0:150;}
        return (radio&&!checked?RADIO_OFF:ColorMotion.ENTER).getInterpolation(ColorControlPhysics.clamp((ms-offset)/duration));
    }
    static float pulse(boolean radio,boolean checked,boolean tick,float ms,float from){
        android.animation.TimeInterpolator curve=radio&&!checked?RADIO_OFF:ColorMotion.ENTER;
        if(ms<150)return from+(1.08f-from)*curve.getInterpolation(ms/150);
        if(tick&&checked)curve=TICK_RETURN;
        return 1.08f-.08f*curve.getInterpolation(ColorControlPhysics.clamp((ms-150)/150));
    }
    private void animateSelection(){
        if(selection!=null){selection.cancel();selection=null;}float end=checked?1:0;
        if(!enabled||!ColorControlMotion.canAnimate(host)){fill=mark=end;pulse=markPulse=1;invalidateSelf();return;}
        final float fromFill=fill,fromMark=mark,fromPulse=pulse,fromMarkPulse=markPulse;
        selection=ValueAnimator.ofFloat(0,300);selection.setDuration(300);selection.setInterpolator(new android.view.animation.LinearInterpolator());
        selection.addUpdateListener(a->{
            float ms=(float)a.getAnimatedValue();
            fill=fromFill+(end-fromFill)*choiceProgress(radio,checked,true,ms);
            mark=fromMark+(end-fromMark)*choiceProgress(radio,checked,false,ms);
            pulse=pulse(radio,checked,false,ms,fromPulse);
            markPulse=radio?1:pulse(false,checked,true,ms,fromMarkPulse);
            invalidateSelf();
        });selection.start();
    }
    void stop(){if(selection!=null){selection.cancel();selection=null;}fill=mark=checked?1:0;pulse=markPulse=1;pressed.snap(0);invalidateSelf();}
    @Override public void jumpToCurrentState(){stop();}
    @Override public boolean setVisible(boolean visible,boolean restart){boolean changed=super.setVisible(visible,restart);if(!visible)stop();return changed;}
    @Override public void draw(Canvas canvas){
        int save=canvas.save();canvas.translate(getBounds().left,getBounds().top);canvas.scale(getBounds().width()/24f,getBounds().height()/24f);
        int accent=ColorOsTheme.accent(host.getContext()),neutral=ColorOsTheme.text(host.getContext());boolean dark=ColorOsTheme.dark(host.getContext());
        float press=ColorControlPhysics.clamp(pressed.value());paint.setStyle(Paint.Style.FILL);paint.setColor(ColorSwitch.alpha(neutral,Math.round(13*press*drawableAlpha/255f)));if(press>0)canvas.drawCircle(12,12,20,paint);
        if(focused){paint.setStyle(Paint.Style.STROKE);paint.setStrokeWidth(1.3f);paint.setColor(ColorSwitch.alpha(accent,drawableAlpha*160/255));canvas.drawCircle(12,12,14,paint);}
        int outline=canvas.save();canvas.scale(pulse,pulse,12,12);paint.setStyle(Paint.Style.STROKE);paint.setStrokeWidth(1.3f);paint.setColor(ColorSwitch.alpha(neutral,Math.round((dark?77:66)*(radio?1-mark:1)*(enabled?1:.4f)*drawableAlpha/255f)));
        if(radio)canvas.drawCircle(12,12,9.2f,paint);else{box.set(3.8f,3.8f,20.2f,20.2f);canvas.drawRoundRect(box,3.2f,3.2f,paint);}
        paint.setStyle(Paint.Style.FILL);paint.setColor(ColorSwitch.alpha(enabled?accent:neutral,Math.round(255*fill*(enabled?1:.18f)*drawableAlpha/255f)));
        if(radio)canvas.drawCircle(12,12,10,paint);
        else{box.set(3,3,21,21);canvas.drawRoundRect(box,4,4,paint);}
        canvas.restoreToCount(outline);
        if(radio){paint.setColor(ColorSwitch.alpha(0xffffffff,Math.round(255*fill*(enabled?1:.55f)*drawableAlpha/255f)));canvas.drawCircle(12,12,6*mark,paint);}
        else if(mark>0){
            canvas.scale(markPulse,markPulse,12,12);visibleTick.reset();
            tickMeasure.getSegment(0,tickMeasure.getLength()*mark,visibleTick,true);
            paint.setStyle(Paint.Style.STROKE);paint.setStrokeWidth(1.3f);paint.setStrokeJoin(Paint.Join.ROUND);paint.setStrokeCap(Paint.Cap.BUTT);
            paint.setColor(ColorSwitch.alpha(0xffffffff,Math.round(255*(enabled?1:.55f)*drawableAlpha/255f)));canvas.drawPath(visibleTick,paint);
        }
        canvas.restoreToCount(save);
    }
    @Override public void setAlpha(int alpha){drawableAlpha=alpha;invalidateSelf();}
    @Override public void setColorFilter(ColorFilter filter){paint.setColorFilter(filter);invalidateSelf();}
    @Override public int getOpacity(){return PixelFormat.TRANSLUCENT;}
}
