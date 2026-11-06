package replica;

import android.content.Context;
import android.graphics.*;
import android.os.Bundle;
import android.view.*;
import android.view.accessibility.*;
import java.util.Locale;

/** Independent Canvas implementation of the Gallery ring/SV square and 10x12 swatch layout. */
final class ColorPalettePicker extends View {
    private final ColorPaletteState state;
    private final Runnable changed;
    private final Paint paint=new Paint(Paint.ANTI_ALIAS_FLAG);
    private final RectF square=new RectF(),grid=new RectF();
    private float cx,cy,radius,stroke,unit;
    private SweepGradient ring;
    private boolean gridMode;
    private int pointer=-1,dragMode,startColor,focus=-1,hover=-1;
    private float startHue;
    private final AccessibilityNodeProvider accessibility=new Nodes();
    ColorPalettePicker(Context context,ColorPaletteState state,Runnable changed){super(context);this.state=state;this.changed=changed;setFocusable(true);setClickable(true);setForceDarkAllowed(false);setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_YES);ColorOsTheme.watch(this,this::invalidate);}
    void setGrid(boolean grid){gridMode=grid;pointer=-1;focus=-1;hover=-1;invalidate();sendAccessibilityEvent(AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED);}
    void refresh(){invalidate();}
    private float dp(float n){return n*getResources().getDisplayMetrics().density;}
    @Override protected void onMeasure(int w,int h){int width=resolveSize(Math.round(dp(328)),w);int height=Math.round(Math.max(dp(183),Math.min(dp(233),width-dp(48)))+dp(48));setMeasuredDimension(width,resolveSize(height,h));}
    private void geometry(){
        cx=getWidth()/2f;cy=getHeight()/2f;float diameter=Math.max(1,Math.min(Math.min(getWidth(),getHeight())-dp(32),dp(getWidth()<dp(300)?183:233)));
        stroke=diameter*38/233;radius=(diameter-stroke)/2;float side=diameter*90/233;
        square.set(cx-side/2,cy-side/2,cx+side/2,cy+side/2);
        unit=Math.max(1,Math.min((getWidth()-dp(8))/12f,(getHeight()-dp(8))/10f));grid.set(cx-unit*6,cy-unit*5,cx+unit*6,cy+unit*5);
        ring=new SweepGradient(cx,cy,new int[]{Color.RED,Color.MAGENTA,Color.BLUE,Color.CYAN,Color.GREEN,Color.YELLOW,Color.RED},null);
    }
    @Override protected void onSizeChanged(int w,int h,int ow,int oh){super.onSizeChanged(w,h,ow,oh);geometry();}
    @Override protected void onDraw(Canvas canvas){super.onDraw(canvas);if(ring==null)geometry();paint.setStyle(Paint.Style.FILL);paint.setShader(null);paint.setAlpha(255);
        if(gridMode){
            RectF cell=new RectF();for(int i=0;i<120;i++){cell(i,cell);paint.setColor(GalleryPaletteColors.COLORS[i]);canvas.drawRect(cell,paint);}
            paint.setStyle(Paint.Style.STROKE);paint.setStrokeWidth(dp(.5f));paint.setColor(ColorOsTheme.tertiary(getContext()));canvas.drawRect(grid,paint);
            int selected=selectedCell();if(selected>=0){cell(selected,cell);cell.inset(dp(1.5f),dp(1.5f));paint.setStrokeWidth(dp(3));paint.setColor(Color.WHITE);canvas.drawRect(cell,paint);paint.setStrokeWidth(dp(.7f));paint.setColor(0x70000000);canvas.drawRect(cell,paint);}paint.setStyle(Paint.Style.FILL);return;
        }
        paint.setShader(ring);paint.setStyle(Paint.Style.STROKE);paint.setStrokeWidth(stroke);canvas.drawCircle(cx,cy,radius,paint);paint.setStyle(Paint.Style.FILL);
        int hue=ColorPaletteState.hsv(255,state.hue(),1,1);
        paint.setShader(new LinearGradient(square.left,square.top,square.right,square.top,Color.WHITE,hue,Shader.TileMode.CLAMP));canvas.drawRect(square,paint);
        paint.setShader(new LinearGradient(square.left,square.top,square.left,square.bottom,Color.TRANSPARENT,Color.BLACK,Shader.TileMode.CLAMP));canvas.drawRect(square,paint);paint.setShader(null);
        double angle=Math.toRadians(state.hue());float hx=cx+radius*(float)Math.cos(angle),hy=cy-radius*(float)Math.sin(angle);
        marker(canvas,hx,hy,stroke*14/38,stroke*5/38,hue);
        marker(canvas,square.left+square.width()*state.saturation(),square.bottom-square.height()*state.value(),dp(6),dp(2),state.color()|0xff000000);
        if(isFocused()){paint.setStyle(Paint.Style.STROKE);paint.setStrokeWidth(dp(1));paint.setColor(ColorOsTheme.accent(getContext()));canvas.drawCircle(cx,cy,radius+stroke/2+dp(6),paint);paint.setStyle(Paint.Style.FILL);}
    }
    private void marker(Canvas c,float x,float y,float inner,float border,int color){paint.setColor(0x50000000);c.drawCircle(x,y,inner+border+dp(.7f),paint);paint.setColor(Color.WHITE);c.drawCircle(x,y,inner+border,paint);paint.setColor(color);c.drawCircle(x,y,inner,paint);}
    private void cell(int i,RectF out){int row=i/12,col=i%12;out.set(grid.left+col*unit,grid.top+row*unit,grid.left+(col+1)*unit,grid.top+(row+1)*unit);}
    private int cellAt(float x,float y){if(!grid.contains(x,y))return -1;return Math.min(9,(int)((y-grid.top)/unit))*12+Math.min(11,(int)((x-grid.left)/unit));}
    private int selectedCell(){for(int i=0;i<120;i++)if((GalleryPaletteColors.COLORS[i]&0xffffff)==(state.color()&0xffffff))return i;return -1;}
    private void update(float x,float y){if(gridMode){int i=cellAt(x,y);if(i<0)return;state.setRgb(GalleryPaletteColors.COLORS[i]);}else if(dragMode==0)state.setHue(ColorPaletteState.hueAt(x-cx,y-cy));else state.setSV((x-square.left)/square.width(),(square.bottom-y)/square.height());changed.run();invalidate();}
    @Override public boolean onTouchEvent(MotionEvent event){
        int action=event.getActionMasked();
        if(action==MotionEvent.ACTION_DOWN){if(!isEnabled())return false;float distance=(float)Math.hypot(event.getX()-cx,event.getY()-cy);if(gridMode){if(cellAt(event.getX(),event.getY())<0)return false;dragMode=2;}else if(distance>=radius-stroke/2-dp(7)&&distance<=radius+stroke/2+dp(14))dragMode=0;else if(square.contains(event.getX(),event.getY()))dragMode=1;else return false;
            pointer=event.getPointerId(0);startColor=state.color();startHue=state.hue();getParent().requestDisallowInterceptTouchEvent(true);update(event.getX(),event.getY());return true;}
        if(pointer<0)return false;int index=event.findPointerIndex(pointer);
        if(action==MotionEvent.ACTION_CANCEL||action==MotionEvent.ACTION_POINTER_DOWN||index<0){state.setHue(startHue);state.setColor(startColor);changed.run();endTouch();return true;}
        if(action==MotionEvent.ACTION_MOVE){update(event.getX(index),event.getY(index));return true;}
        if(action==MotionEvent.ACTION_UP){update(event.getX(index),event.getY(index));endTouch();performClick();return true;}return true;
    }
    private void endTouch(){pointer=-1;if(getParent()!=null)getParent().requestDisallowInterceptTouchEvent(false);invalidate();}
    @Override public boolean performClick(){return super.performClick();}
    @Override protected void onDetachedFromWindow(){if(pointer>=0){state.setHue(startHue);state.setColor(startColor);endTouch();}super.onDetachedFromWindow();}
    @Override public boolean onKeyDown(int key,KeyEvent event){if(key==KeyEvent.KEYCODE_DPAD_LEFT||key==KeyEvent.KEYCODE_DPAD_RIGHT||key==KeyEvent.KEYCODE_DPAD_UP||key==KeyEvent.KEYCODE_DPAD_DOWN){if(gridMode){int current=Math.max(0,selectedCell()),next=current+(key==KeyEvent.KEYCODE_DPAD_LEFT?-1:key==KeyEvent.KEYCODE_DPAD_RIGHT?1:key==KeyEvent.KEYCODE_DPAD_UP?-12:12);state.setRgb(GalleryPaletteColors.COLORS[Math.max(0,Math.min(119,next))]);}else if(key==KeyEvent.KEYCODE_DPAD_LEFT||key==KeyEvent.KEYCODE_DPAD_RIGHT)state.setHue(state.hue()+(key==KeyEvent.KEYCODE_DPAD_LEFT?-5:5));else state.setSV(state.saturation(),state.value()+(key==KeyEvent.KEYCODE_DPAD_UP?.05f:-.05f));changed.run();invalidate();return true;}return super.onKeyDown(key,event);}
    @Override public AccessibilityNodeProvider getAccessibilityNodeProvider(){return accessibility;}
    private void event(int id,int type){if(getParent()==null)return;AccessibilityEvent event=AccessibilityEvent.obtain(type);event.setPackageName(getContext().getPackageName());event.setClassName(gridMode?"android.widget.Button":"android.widget.SeekBar");event.setSource(this,id);event.getText().add(description(id));getParent().requestSendAccessibilityEvent(this,event);}
    private String description(int id){return gridMode?"第 "+(id/12+1)+" 行，第 "+(id%12+1)+" 列，"+String.format(Locale.ROOT,"#%06X",GalleryPaletteColors.COLORS[id]&0xffffff):id==0?"色相 "+Math.round(state.hue())+" 度":id==1?"饱和度 "+Math.round(state.saturation()*100)+"%":"明度 "+Math.round(state.value()*100)+"%";}
    @Override public boolean dispatchHoverEvent(MotionEvent e){AccessibilityManager manager=(AccessibilityManager)getContext().getSystemService(Context.ACCESSIBILITY_SERVICE);if(!manager.isTouchExplorationEnabled())return super.dispatchHoverEvent(e);int next=e.getActionMasked()==MotionEvent.ACTION_HOVER_EXIT?-1:gridMode?cellAt(e.getX(),e.getY()):square.contains(e.getX(),e.getY())?(e.getX()<cx?1:2):0;if(next!=hover){if(hover>=0)event(hover,AccessibilityEvent.TYPE_VIEW_HOVER_EXIT);hover=next;if(hover>=0)event(hover,AccessibilityEvent.TYPE_VIEW_HOVER_ENTER);}return next>=0;}
    private final class Nodes extends AccessibilityNodeProvider {
        @Override public AccessibilityNodeInfo createAccessibilityNodeInfo(int id){
            if(id==HOST_VIEW_ID){AccessibilityNodeInfo info=AccessibilityNodeInfo.obtain(ColorPalettePicker.this);ColorPalettePicker.super.onInitializeAccessibilityNodeInfo(info);for(int i=0;i<(gridMode?120:3);i++)info.addChild(ColorPalettePicker.this,i);return info;}
            if(id<0||id>=(gridMode?120:3))return null;AccessibilityNodeInfo info=AccessibilityNodeInfo.obtain();info.setSource(ColorPalettePicker.this,id);info.setParent(ColorPalettePicker.this);info.setPackageName(getContext().getPackageName());info.setClassName(gridMode?"android.widget.Button":"android.widget.SeekBar");info.setContentDescription(description(id));info.setEnabled(isEnabled());info.setFocusable(true);info.setVisibleToUser(isShown());info.setAccessibilityFocused(focus==id);info.addAction(focus==id?AccessibilityNodeInfo.ACTION_CLEAR_ACCESSIBILITY_FOCUS:AccessibilityNodeInfo.ACTION_ACCESSIBILITY_FOCUS);
            RectF rect=new RectF();if(gridMode){cell(id,rect);info.setClickable(true);info.setCheckable(true);info.setChecked(selectedCell()==id);info.addAction(AccessibilityNodeInfo.ACTION_CLICK);}else{if(id==0)rect.set(cx-radius-stroke/2,cy-radius-stroke/2,cx+radius+stroke/2,cy+radius+stroke/2);else rect.set(id==1?square.left:cx,square.top,id==1?cx:square.right,square.bottom);float value=id==0?state.hue():id==1?state.saturation()*100:state.value()*100;info.setRangeInfo(AccessibilityNodeInfo.RangeInfo.obtain(AccessibilityNodeInfo.RangeInfo.RANGE_TYPE_FLOAT,0,id==0?360:100,value));info.addAction(AccessibilityNodeInfo.AccessibilityAction.ACTION_SET_PROGRESS);info.addAction(AccessibilityNodeInfo.ACTION_SCROLL_FORWARD);info.addAction(AccessibilityNodeInfo.ACTION_SCROLL_BACKWARD);}
            Rect bounds=new Rect();rect.roundOut(bounds);info.setBoundsInParent(bounds);int[] pos=new int[2];getLocationOnScreen(pos);bounds.offset(pos[0],pos[1]);info.setBoundsInScreen(bounds);return info;
        }
        @Override public boolean performAction(int id,int action,Bundle args){if(id<0||id>=(gridMode?120:3))return false;if(action==AccessibilityNodeInfo.ACTION_ACCESSIBILITY_FOCUS){if(focus>=0)event(focus,AccessibilityEvent.TYPE_VIEW_ACCESSIBILITY_FOCUS_CLEARED);focus=id;event(id,AccessibilityEvent.TYPE_VIEW_ACCESSIBILITY_FOCUSED);invalidate();return true;}if(action==AccessibilityNodeInfo.ACTION_CLEAR_ACCESSIBILITY_FOCUS){if(focus==id)focus=-1;event(id,AccessibilityEvent.TYPE_VIEW_ACCESSIBILITY_FOCUS_CLEARED);invalidate();return true;}
            if(gridMode&&action==AccessibilityNodeInfo.ACTION_CLICK)state.setRgb(GalleryPaletteColors.COLORS[id]);else if(!gridMode){float current=id==0?state.hue():id==1?state.saturation()*100:state.value()*100;float next;if(action==AccessibilityNodeInfo.AccessibilityAction.ACTION_SET_PROGRESS.getId()&&args!=null)next=args.getFloat(AccessibilityNodeInfo.ACTION_ARGUMENT_PROGRESS_VALUE);else if(action==AccessibilityNodeInfo.ACTION_SCROLL_FORWARD||action==AccessibilityNodeInfo.ACTION_SCROLL_BACKWARD)next=current+(action==AccessibilityNodeInfo.ACTION_SCROLL_FORWARD?5:-5);else return false;if(id==0)state.setHue(next);else state.setSV(id==1?next/100:state.saturation(),id==2?next/100:state.value());}else return false;changed.run();invalidate();event(id,AccessibilityEvent.TYPE_VIEW_SELECTED);return true;
        }
        @Override public AccessibilityNodeInfo findFocus(int which){return which==AccessibilityNodeInfo.FOCUS_ACCESSIBILITY&&focus>=0?createAccessibilityNodeInfo(focus):null;}
    }
}
