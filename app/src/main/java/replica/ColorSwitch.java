package replica;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.RectF;
import android.util.AttributeSet;
import android.view.MotionEvent;
import android.view.ViewConfiguration;
import android.view.accessibility.AccessibilityEvent;
import android.view.accessibility.AccessibilityNodeInfo;
import android.widget.CompoundButton;

/** COUI 44 x 24 track, 18dp thumb, 3dp inset, with real drag and switch semantics. */
public final class ColorSwitch extends CompoundButton {
    private final Paint paint=new Paint(Paint.ANTI_ALIAS_FLAG);
    private final RectF track=new RectF(),thumb=new RectF(),focus=new RectF();
    private final float density;
    private final int slop;
    private final ColorControlMotion position,press;
    private int pointer=-1;
    private float downX,downY,downPosition;
    private boolean dragging,aborted;
    private boolean dark;
    private int accentColor,textColor,disabledColor;
    public ColorSwitch(Context c){this(c,null);}
    public ColorSwitch(Context c,AttributeSet attrs){this(c,attrs,0);}
    public ColorSwitch(Context c,AttributeSet attrs,int style){
        super(c,attrs,style);density=getResources().getDisplayMetrics().density;slop=ViewConfiguration.get(c).getScaledTouchSlop();
        setButtonDrawable((android.graphics.drawable.Drawable)null);setBackground(null);setMinWidth(0);setMinimumWidth(0);setMinHeight(0);setMinimumHeight(0);
        setGravity(android.view.Gravity.CENTER_VERTICAL|android.view.Gravity.START);refreshTheme();setFocusable(true);setClickable(true);
        position=new ColorControlMotion(this,isChecked()?1:0);press=new ColorControlMotion(this,0);
        ColorOsTheme.watch(this,this::refreshTheme);
    }
    private void refreshTheme(){dark=ColorOsTheme.dark(getContext());accentColor=ColorOsTheme.accent(getContext());textColor=ColorOsTheme.text(getContext());disabledColor=ColorOsTheme.disabled(getContext());setTextColor(textColor);setTypeface(ColorOsTheme.typeface(getContext(),400));invalidate();}
    @Override protected void onAttachedToWindow(){super.onAttachedToWindow();refreshTheme();}
    private float dp(float n){return n*density;}
    private boolean rtl(){return getLayoutDirection()==LAYOUT_DIRECTION_RTL;}
    @Override public int getCompoundPaddingRight(){return super.getCompoundPaddingRight()+(!rtl()&&length()>0?Math.round(dp(52)):0);}
    @Override public int getCompoundPaddingLeft(){return super.getCompoundPaddingLeft()+(rtl()&&length()>0?Math.round(dp(52)):0);}
    @Override protected void onMeasure(int w,int h){
        super.onMeasure(w,h);int wantedW=length()>0?getMeasuredWidth():Math.round(dp(48))+getPaddingLeft()+getPaddingRight();
        setMeasuredDimension(resolveSize(wantedW,w),resolveSize(Math.max(Math.round(dp(44))+getPaddingTop()+getPaddingBottom(),length()>0?getMeasuredHeight():0),h));
    }
    private void geometry(){float cx=length()>0?(rtl()?getPaddingLeft()+dp(22):getWidth()-getPaddingRight()-dp(22)):(getPaddingLeft()+getWidth()-getPaddingRight())/2f;float cy=(getPaddingTop()+getHeight()-getPaddingBottom())/2f;track.set(cx-dp(22),cy-dp(12),cx+dp(22),cy+dp(12));}
    @Override protected void onDraw(Canvas canvas){
        super.onDraw(canvas);geometry();float p=ColorControlPhysics.clamp(position.value());
        int off=dark?0xff757575:0xffe5e5e5,accent=accentColor;int color=blend(off,accent,p);
        if(!isEnabled())color=isChecked()?disabledColor:(dark?0xff393939:0xfff2f2f2);
        paint.setStyle(Paint.Style.FILL);paint.setColor(color);canvas.drawRoundRect(track,dp(12),dp(12),paint);
        if(press.value()>0){paint.setColor(alpha(textColor,Math.round(13*ColorControlPhysics.clamp(press.value()))));canvas.drawRoundRect(track,dp(12),dp(12),paint);}
        float physical=ColorControlPhysics.physicalFraction(p,rtl());float center=track.left+dp(12)+dp(20)*physical;
        float stretch=position.running()&&!dragging?ColorControlPhysics.switchStretch(p):1;float half=dp(9);
        thumb.set(center-half*stretch,track.centerY()-half,center+half*stretch,track.centerY()+half);
        paint.setColor(isEnabled()?0xffffffff:(dark?0xff737373:0xfffafafa));canvas.drawRoundRect(thumb,half,half,paint);
        if(isFocused()){paint.setStyle(Paint.Style.STROKE);paint.setStrokeWidth(dp(1.5f));paint.setColor(alpha(accent,180));focus.set(track);focus.inset(-dp(3),-dp(3));canvas.drawRoundRect(focus,dp(15),dp(15),paint);paint.setStyle(Paint.Style.FILL);}
    }
    @Override public void setChecked(boolean checked){
        // Start the clock before CompoundButton invokes the synchronous client.
        // Rebinding an unchanged value must not restart an in-flight animation.
        if(position!=null&&isChecked()!=checked)moveChecked(checked,.3f);
        super.setChecked(checked);
        // A client may reject/rewrite the change or disable/remove the control.
        if(position!=null&&isChecked()!=checked)moveChecked(isChecked(),.3f);
    }
    private void moveChecked(boolean checked,float bounce){if(isEnabled())position.animate(checked?1:0,.4f,bounce);else position.snap(checked?1:0);}
    @Override public void setEnabled(boolean enabled){super.setEnabled(enabled);if(!enabled&&position!=null){finishTouch(false);position.snap(isChecked()?1:0);press.snap(0);}invalidate();}
    @Override public boolean onTouchEvent(MotionEvent e){
        if(!isEnabled()||!isClickable())return false;int action=e.getActionMasked();
        if(action==MotionEvent.ACTION_DOWN){pointer=e.getPointerId(0);downX=e.getX();downY=e.getY();downPosition=position.value();dragging=aborted=false;position.cancel();setPressed(true);press.animate(1,.3f,0);return true;}
        if(pointer<0)return false;int index=e.findPointerIndex(pointer);
        if(action==MotionEvent.ACTION_CANCEL||action==MotionEvent.ACTION_POINTER_DOWN||index<0){finishTouch(false);return true;}
        float dx=e.getX(index)-downX,dy=e.getY(index)-downY;
        if(action==MotionEvent.ACTION_MOVE){
            if(!dragging&&Math.abs(dy)>slop&&Math.abs(dy)>Math.abs(dx))aborted=true;
            if(!aborted&&!dragging&&Math.abs(dx)>slop){dragging=true;if(getParent()!=null)getParent().requestDisallowInterceptTouchEvent(true);}
            if(dragging)position.snap(ColorControlPhysics.clamp(downPosition+(rtl()?-dx:dx)/dp(20)));return true;
        }
        if(action==MotionEvent.ACTION_UP){boolean committed=false;if(!aborted){if(dragging){boolean checked=position.value()>=.5f;setChecked(checked);moveChecked(isChecked(),.2f);committed=true;}else if(e.getX()>=0&&e.getX()<=getWidth()&&e.getY()>=0&&e.getY()<=getHeight()){performClick();committed=true;}}finishTouch(committed);return true;}
        return true;
    }
    private void finishTouch(boolean committed){pointer=-1;dragging=false;setPressed(false);if(getParent()!=null)getParent().requestDisallowInterceptTouchEvent(false);if(position!=null&&!committed)position.animate(isChecked()?1:0,.4f,.2f);if(press!=null)press.animate(0,.3f,0);}
    @Override public boolean performClick(){return super.performClick();}
    @Override protected void drawableStateChanged(){super.drawableStateChanged();if(press!=null)press.animate(isEnabled()&&isPressed()?1:0,.3f,0);}
    @Override public CharSequence getAccessibilityClassName(){return android.widget.Switch.class.getName();}
    @Override public void onInitializeAccessibilityNodeInfo(AccessibilityNodeInfo info){super.onInitializeAccessibilityNodeInfo(info);info.setClassName(android.widget.Switch.class.getName());info.setCheckable(true);info.setChecked(isChecked());}
    @Override public void onInitializeAccessibilityEvent(AccessibilityEvent event){super.onInitializeAccessibilityEvent(event);event.setClassName(android.widget.Switch.class.getName());event.setChecked(isChecked());}
    @Override public void jumpDrawablesToCurrentState(){super.jumpDrawablesToCurrentState();if(position!=null)position.snap(isChecked()?1:0);if(press!=null)press.snap(0);}
    @Override protected void onDetachedFromWindow(){finishTouch(false);position.snap(isChecked()?1:0);press.snap(0);super.onDetachedFromWindow();}
    static int alpha(int color,int alpha){return (color&0x00ffffff)|(Math.max(0,Math.min(255,alpha))<<24);}
    static int blend(int a,int b,float f){f=ColorControlPhysics.clamp(f);return (Math.round((a>>>24)+((b>>>24)-(a>>>24))*f)<<24)|(Math.round(((a>>16)&255)+(((b>>16)&255)-((a>>16)&255))*f)<<16)|(Math.round(((a>>8)&255)+(((b>>8)&255)-((a>>8)&255))*f)<<8)|Math.round((a&255)+((b&255)-(a&255))*f);}
}
