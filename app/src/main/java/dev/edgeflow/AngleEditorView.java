package dev.edgeflow;

import android.content.Context;
import android.content.res.Configuration;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.Rect;
import android.graphics.RectF;
import android.os.Bundle;
import android.view.KeyEvent;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewConfiguration;
import android.view.accessibility.AccessibilityEvent;
import android.view.accessibility.AccessibilityManager;
import android.view.accessibility.AccessibilityNodeInfo;
import android.view.accessibility.AccessibilityNodeProvider;
import replica.ColorOsTheme;

/** Native, themed OHO angle editor. No preferences are written until its owner commits. */
public final class AngleEditorView extends View {
    public interface Listener {
        void onCommit(int index,float degrees);
        default void onPreview(int index,float degrees){}
        default void onCancel(){}
    }
    private static final String[] NAMES={"上方限位","向上斜滑边界","向下斜滑边界","下方限位"};
    private static final String[] SHORT_NAMES={"上限","上斜","下斜","下限"};
    private final boolean longer;
    private final Listener listener;
    private final AngleEditorGeometry.Drag drag;
    private final Paint paint=new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Path path=new Path();
    private final RectF circle=new RectF();
    private final AccessibilityNodeProvider nodes=new Nodes();
    private final int touchSlop;
    private float cx,cy,radius,footerTop,rowHeight,textSize,downX,downY;
    private int footerColumns=2,pointer=-1,selected=1,accessibilityFocus=-1,hover=-1;
    private boolean mirrored,moved;

    public AngleEditorView(Context context,boolean longer,float[] values,Listener listener){
        super(context);this.longer=longer;this.listener=listener;drag=new AngleEditorGeometry.Drag(values);
        touchSlop=ViewConfiguration.get(context).getScaledTouchSlop();setFocusable(true);setClickable(true);setForceDarkAllowed(false);
        setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_YES);setContentDescription((longer?"长滑":"短滑")+"手势角度编辑器");
        ColorOsTheme.watch(this,()->{requestLayout();invalidate();});
    }
    public float[] getAngles(){return drag.values();}
    public void setAngles(float[] values){cancelDrag();drag.replace(values);invalidate();sendAccessibilityEvent(AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED);}
    public void setMirrored(boolean mirrored){if(this.mirrored==mirrored)return;cancelDrag();this.mirrored=mirrored;geometry();invalidate();sendAccessibilityEvent(AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED);}
    public void cancelDrag(){if(pointer<0&&drag.active()<0)return;int index=drag.active();drag.cancel();endTouch();if(index>=0&&listener!=null){listener.onPreview(index,drag.value(index));listener.onCancel();}invalidate();}
    private float dp(float value){return value*getResources().getDisplayMetrics().density;}
    private int alpha(int color,int value){return (color&0x00ffffff)|(value<<24);}
    private void typography(){textSize=14*getResources().getDisplayMetrics().scaledDensity;paint.setTypeface(ColorOsTheme.typeface(getContext(),500));paint.setTextSize(textSize);rowHeight=Math.max(dp(32),paint.getFontSpacing()+dp(10));}
    private float desiredRadius(int width){typography();float labels=Math.max(paint.measureText("直线"),paint.measureText("140°"))+dp(12);return Math.max(dp(28),Math.min(dp(160),width-dp(64)-labels));}
    private int columns(int width){return width>=2*(paint.measureText("下限 85°")+dp(24))?2:1;}
    @Override protected void onMeasure(int widthSpec,int heightSpec){int width=resolveSize(Math.round(dp(340)),widthSpec);float r=desiredRadius(width);int cols=columns(width);int desired=Math.round(r*2+dp(32)+rowHeight*(4/cols)+dp(8));setMeasuredDimension(width,resolveSize(desired,heightSpec));}
    private void geometry(){typography();footerColumns=columns(getWidth());float footer=rowHeight*(4/footerColumns)+dp(8);radius=Math.max(1,Math.min(desiredRadius(getWidth()),(getHeight()-footer-dp(32))/2));cx=mirrored?getWidth()-dp(28):dp(28);cy=dp(16)+radius;footerTop=cy+radius+dp(16);circle.set(cx-radius,cy-radius,cx+radius,cy+radius);}
    @Override protected void onSizeChanged(int w,int h,int ow,int oh){cancelDrag();super.onSizeChanged(w,h,ow,oh);geometry();}
    @Override protected void onConfigurationChanged(Configuration configuration){cancelDrag();super.onConfigurationChanged(configuration);requestLayout();invalidate();}
    private void sector(Canvas canvas,float from,float to,int color){paint.setColor(color);paint.setStyle(Paint.Style.FILL);float start=mirrored?180-to:from;canvas.drawArc(circle,start,to-from,true,paint);}
    private AngleEditorGeometry.Point point(int index,float degrees,float radial){return AngleEditorGeometry.point(cx,cy,radial,mirrored,index,degrees);}
    @Override protected void onDraw(Canvas canvas){
        super.onDraw(canvas);geometry();int accent=ColorOsTheme.accent(getContext()),secondary=ColorOsTheme.secondary(getContext());float[] values=drag.values();
        sector(canvas,-90,90,alpha(secondary,ColorOsTheme.dark(getContext())?22:10));
        sector(canvas,-values[0],-values[1],alpha(accent,ColorOsTheme.dark(getContext())?54:24));
        sector(canvas,-values[1],values[2],alpha(accent,ColorOsTheme.dark(getContext())?30:13));
        sector(canvas,values[2],values[3],alpha(accent,ColorOsTheme.dark(getContext())?54:24));
        paint.setStyle(Paint.Style.STROKE);paint.setStrokeWidth(dp(1));paint.setColor(alpha(secondary,65));canvas.drawArc(circle,mirrored?90:-90,180,false,paint);
        // Direction guides are anchored at fixed -45/0/+45 degrees, never sector midpoints.
        for(int direction=-1;direction<=1;direction++)drawDirection(canvas,direction,secondary,values);
        for(int index=0;index<4;index++){
            AngleEditorGeometry.Point end=point(index,values[index],radius);boolean active=index==drag.active()||index==accessibilityFocus;
            paint.setStyle(Paint.Style.STROKE);paint.setStrokeWidth(dp(active?2:1.25f));paint.setColor(alpha(accent,active?255:175));canvas.drawLine(cx,cy,end.x,end.y,paint);
            paint.setStyle(Paint.Style.FILL);if(active){paint.setColor(alpha(accent,40));canvas.drawCircle(end.x,end.y,dp(18),paint);}paint.setColor(ColorOsTheme.card(getContext()));canvas.drawCircle(end.x,end.y,dp(active?8:7),paint);paint.setStyle(Paint.Style.STROKE);paint.setStrokeWidth(dp(2));paint.setColor(accent);canvas.drawCircle(end.x,end.y,dp(active?8:7),paint);paint.setStyle(Paint.Style.FILL);paint.setColor(accent);canvas.drawCircle(end.x,end.y,dp(2.5f),paint);
        }
        paint.setColor(accent);canvas.drawCircle(cx,cy,dp(3),paint);paint.setTextAlign(Paint.Align.LEFT);paint.setStyle(Paint.Style.FILL);paint.setTextSize(textSize);
        for(int index=0;index<4;index++){
            int column=index%footerColumns,row=index/footerColumns;float left=dp(12)+column*(getWidth()-dp(24))/footerColumns;
            paint.setColor(index==selected?accent:secondary);String text=SHORT_NAMES[index]+" "+Math.round(values[index])+"°";
            canvas.drawText(text,left,footerTop+row*rowHeight+(rowHeight-paint.getFontMetrics().ascent-paint.getFontMetrics().descent)/2,paint);
        }
    }
    private void drawDirection(Canvas canvas,int direction,int color,float[] values){
        double angle=Math.toRadians(direction*45);float dx=(mirrored?-1:1)*(float)Math.cos(angle),dy=(float)Math.sin(angle);
        float x1=cx+dx*radius*.23f,y1=cy+dy*radius*.23f,x2=cx+dx*radius*.66f,y2=cy+dy*radius*.66f;
        paint.setStyle(Paint.Style.STROKE);paint.setStrokeWidth(dp(1.4f));paint.setStrokeCap(Paint.Cap.ROUND);paint.setColor(alpha(color,145));canvas.drawLine(x1,y1,x2,y2,paint);
        float arrow=dp(5);path.reset();path.moveTo(x2-dx*arrow-dy*arrow*.65f,y2-dy*arrow+dx*arrow*.65f);path.lineTo(x2,y2);path.lineTo(x2-dx*arrow+dy*arrow*.65f,y2-dy*arrow-dx*arrow*.65f);canvas.drawPath(path,paint);paint.setStrokeCap(Paint.Cap.BUTT);paint.setStyle(Paint.Style.FILL);
        float labelX=mirrored?cx-radius-dp(16):cx+radius+dp(16),labelY=cy+direction*radius*.65f;paint.setTextAlign(mirrored?Paint.Align.RIGHT:Paint.Align.LEFT);paint.setTextSize(textSize);paint.setColor(color);
        canvas.drawText(direction<0?"向上":direction>0?"向下":"直线",labelX,labelY-paint.getFontMetrics().descent,paint);
        float width=direction<0?values[0]-values[1]:direction>0?values[3]-values[2]:values[1]+values[2];paint.setColor(ColorOsTheme.tertiary(getContext()));paint.setTextSize(textSize*.85f);canvas.drawText(Math.round(width)+"°",labelX,labelY+paint.getFontSpacing()+dp(3),paint);
    }
    private int hit(float x,float y){return AngleEditorGeometry.hit(x,y,cx,cy,radius,mirrored,drag.values(),dp(24),dp(9));}
    private float angle(float x,float y,int index){return AngleEditorGeometry.pointerAngle(x,y,cx,cy,mirrored,index);}
    @Override public boolean onTouchEvent(MotionEvent event){
        int action=event.getActionMasked();
        if(action==MotionEvent.ACTION_DOWN){if(!isEnabled())return false;int index=hit(event.getX(),event.getY());if(index<0)return false;cancelDrag();selected=index;pointer=event.getPointerId(0);downX=event.getX();downY=event.getY();moved=false;drag.begin(index,angle(downX,downY,index));if(getParent()!=null)getParent().requestDisallowInterceptTouchEvent(true);invalidate();return true;}
        if(pointer<0)return false;int pointerIndex=event.findPointerIndex(pointer);
        if(action==MotionEvent.ACTION_CANCEL||action==MotionEvent.ACTION_POINTER_DOWN||pointerIndex<0){cancelDrag();return true;}
        if(action==MotionEvent.ACTION_MOVE||action==MotionEvent.ACTION_UP){float x=event.getX(pointerIndex),y=event.getY(pointerIndex);if(Math.hypot(x-downX,y-downY)>touchSlop)moved=true;if(moved&&drag.update(angle(x,y,drag.active()))){if(listener!=null)listener.onPreview(drag.active(),drag.value(drag.active()));invalidate();}
            if(action==MotionEvent.ACTION_UP){int index=drag.active();boolean changed=drag.changed();float value=drag.value(index);drag.commit();endTouch();performClick();if(changed&&listener!=null)listener.onCommit(index,value);if(changed)accessibilityEvent(index,AccessibilityEvent.TYPE_VIEW_SELECTED);}return true;}
        return true;
    }
    private void endTouch(){pointer=-1;moved=false;if(getParent()!=null)getParent().requestDisallowInterceptTouchEvent(false);invalidate();}
    @Override public boolean performClick(){return super.performClick();}
    @Override protected void onDetachedFromWindow(){cancelDrag();super.onDetachedFromWindow();}
    @Override protected void onWindowVisibilityChanged(int visibility){if(visibility!=VISIBLE&&drag!=null)cancelDrag();super.onWindowVisibilityChanged(visibility);}
    @Override public void onWindowFocusChanged(boolean focused){if(!focused&&drag!=null)cancelDrag();super.onWindowFocusChanged(focused);}
    @Override public void setEnabled(boolean enabled){if(!enabled)cancelDrag();super.setEnabled(enabled);invalidate();}
    @Override public boolean onKeyDown(int key,KeyEvent event){if(!isEnabled())return super.onKeyDown(key,event);if(key==KeyEvent.KEYCODE_DPAD_UP||key==KeyEvent.KEYCODE_DPAD_DOWN){selected=Math.max(0,Math.min(3,selected+(key==KeyEvent.KEYCODE_DPAD_UP?-1:1)));invalidate();announceForAccessibility(description(selected));return true;}if(key==KeyEvent.KEYCODE_DPAD_LEFT||key==KeyEvent.KEYCODE_DPAD_RIGHT)return adjust(selected,drag.value(selected)+(key==KeyEvent.KEYCODE_DPAD_RIGHT?1:-1));return super.onKeyDown(key,event);}
    private boolean adjust(int index,float value){if(!isEnabled()||!Float.isFinite(value))return false;cancelDrag();selected=index;if(!drag.set(index,value))return true;if(listener!=null)listener.onCommit(index,drag.value(index));invalidate();accessibilityEvent(index,AccessibilityEvent.TYPE_VIEW_SELECTED);return true;}
    private String label(int index){return (longer?"长滑":"短滑")+NAMES[index];}
    private String description(int index){return label(index)+"，"+Math.round(drag.value(index))+"度";}
    private Rect nodeBounds(int index){AngleEditorGeometry.Point p=point(index,drag.value(index),radius);return new Rect(Math.max(0,Math.round(p.x-dp(24))),Math.max(0,Math.round(p.y-dp(24))),Math.min(getWidth(),Math.round(p.x+dp(24))),Math.min(getHeight(),Math.round(p.y+dp(24))));}
    private void accessibilityEvent(int index,int type){if(getParent()==null)return;AccessibilityEvent event=AccessibilityEvent.obtain(type);event.setPackageName(getContext().getPackageName());event.setClassName("android.widget.SeekBar");event.setSource(this,index);event.getText().add(description(index));getParent().requestSendAccessibilityEvent(this,event);}
    @Override public AccessibilityNodeProvider getAccessibilityNodeProvider(){return nodes;}
    @Override public boolean dispatchHoverEvent(MotionEvent event){AccessibilityManager manager=(AccessibilityManager)getContext().getSystemService(Context.ACCESSIBILITY_SERVICE);if(manager==null||!manager.isTouchExplorationEnabled())return super.dispatchHoverEvent(event);int next=event.getActionMasked()==MotionEvent.ACTION_HOVER_EXIT?-1:hit(event.getX(),event.getY());if(next!=hover){if(hover>=0)accessibilityEvent(hover,AccessibilityEvent.TYPE_VIEW_HOVER_EXIT);hover=next;if(next>=0)accessibilityEvent(next,AccessibilityEvent.TYPE_VIEW_HOVER_ENTER);}return next>=0;}
    private final class Nodes extends AccessibilityNodeProvider {
        @Override public AccessibilityNodeInfo createAccessibilityNodeInfo(int index){
            if(index==HOST_VIEW_ID){AccessibilityNodeInfo info=AccessibilityNodeInfo.obtain(AngleEditorView.this);AngleEditorView.super.onInitializeAccessibilityNodeInfo(info);info.setClassName("android.view.ViewGroup");info.setClickable(false);for(int i=0;i<4;i++)info.addChild(AngleEditorView.this,i);return info;}
            if(index<0||index>=4)return null;AccessibilityNodeInfo info=AccessibilityNodeInfo.obtain();info.setSource(AngleEditorView.this,index);info.setParent(AngleEditorView.this);info.setPackageName(getContext().getPackageName());info.setClassName("android.widget.SeekBar");info.setContentDescription(label(index));info.setStateDescription(Math.round(drag.value(index))+"度");info.setEnabled(isEnabled());info.setFocusable(true);info.setAccessibilityFocused(accessibilityFocus==index);info.setRangeInfo(AccessibilityNodeInfo.RangeInfo.obtain(AccessibilityNodeInfo.RangeInfo.RANGE_TYPE_FLOAT,GestureAngles.min(index),GestureAngles.max(index),drag.value(index)));info.addAction(AccessibilityNodeInfo.AccessibilityAction.ACTION_SET_PROGRESS);info.addAction(AccessibilityNodeInfo.ACTION_SCROLL_FORWARD);info.addAction(AccessibilityNodeInfo.ACTION_SCROLL_BACKWARD);info.addAction(AccessibilityNodeInfo.AccessibilityAction.ACTION_SHOW_ON_SCREEN);info.addAction(accessibilityFocus==index?AccessibilityNodeInfo.ACTION_CLEAR_ACCESSIBILITY_FOCUS:AccessibilityNodeInfo.ACTION_ACCESSIBILITY_FOCUS);
            Rect bounds=nodeBounds(index),visible=new Rect();boolean clippedVisible=isShown()&&getLocalVisibleRect(visible)&&visible.intersect(bounds);info.setVisibleToUser(clippedVisible);info.setBoundsInParent(bounds);if(clippedVisible)bounds.set(visible);int[] pos=new int[2];getLocationOnScreen(pos);bounds.offset(pos[0],pos[1]);info.setBoundsInScreen(bounds);return info;
        }
        @Override public boolean performAction(int index,int action,Bundle args){if(index==HOST_VIEW_ID)return AngleEditorView.this.performAccessibilityAction(action,args);if(index<0||index>=4)return false;
            if(action==AccessibilityNodeInfo.ACTION_ACCESSIBILITY_FOCUS){if(accessibilityFocus>=0)accessibilityEvent(accessibilityFocus,AccessibilityEvent.TYPE_VIEW_ACCESSIBILITY_FOCUS_CLEARED);selected=accessibilityFocus=index;requestRectangleOnScreen(nodeBounds(index),true);accessibilityEvent(index,AccessibilityEvent.TYPE_VIEW_ACCESSIBILITY_FOCUSED);invalidate();return true;}
            if(action==AccessibilityNodeInfo.ACTION_CLEAR_ACCESSIBILITY_FOCUS){if(accessibilityFocus!=index)return false;accessibilityFocus=-1;accessibilityEvent(index,AccessibilityEvent.TYPE_VIEW_ACCESSIBILITY_FOCUS_CLEARED);invalidate();return true;}
            if(action==AccessibilityNodeInfo.AccessibilityAction.ACTION_SET_PROGRESS.getId()&&args!=null&&args.containsKey(AccessibilityNodeInfo.ACTION_ARGUMENT_PROGRESS_VALUE))return adjust(index,args.getFloat(AccessibilityNodeInfo.ACTION_ARGUMENT_PROGRESS_VALUE));
            if(action==AccessibilityNodeInfo.AccessibilityAction.ACTION_SHOW_ON_SCREEN.getId())return requestRectangleOnScreen(nodeBounds(index),true);
            if(action==AccessibilityNodeInfo.ACTION_SCROLL_FORWARD||action==AccessibilityNodeInfo.ACTION_SCROLL_BACKWARD)return adjust(index,drag.value(index)+(action==AccessibilityNodeInfo.ACTION_SCROLL_FORWARD?1:-1));return false;
        }
        @Override public AccessibilityNodeInfo findFocus(int focus){return focus==AccessibilityNodeInfo.FOCUS_ACCESSIBILITY&&accessibilityFocus>=0?createAccessibilityNodeInfo(accessibilityFocus):null;}
    }
}
