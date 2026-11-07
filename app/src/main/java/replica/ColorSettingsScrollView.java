package replica;

import android.content.Context;
import android.graphics.Rect;
import android.os.SystemClock;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewConfiguration;
import android.view.ViewGroup;
import android.widget.ScrollView;
import android.widget.SeekBar;

/** Native in-range scrolling with a COUI-style translated-content boundary spring. */
public final class ColorSettingsScrollView extends ScrollView {
    private final ColorScrollSpring edge=new ColorScrollSpring(this,this::applyOffset);
    private final ColorScrollPhysics.Gate gate=new ColorScrollPhysics.Gate();
    private final int slop;
    private boolean touch,edgeTouch;
    private float downX,downY;
    private long lastOffsetTime,lastScrollTime;
    private double dragVelocity;
    public ColorSettingsScrollView(Context context){super(context);slop=ViewConfiguration.get(context).getScaledTouchSlop();setOverScrollMode(OVER_SCROLL_NEVER);ColorOsTheme.watch(this,()->{if(!edge.enabled())edge.reset();});}
    private void applyOffset(){if(getChildCount()>0)getChildAt(0).setTranslationY(edge.position());}
    private boolean sliderAt(View view,int x,int y){
        if(view.getVisibility()!=VISIBLE)return false;Rect bounds=new Rect();if(!view.getGlobalVisibleRect(bounds)||!bounds.contains(x,y))return false;
        if(view instanceof SeekBar)return true;
        if(view instanceof ViewGroup){ViewGroup group=(ViewGroup)view;for(int i=group.getChildCount()-1;i>=0;i--)if(sliderAt(group.getChildAt(i),x,y))return true;}
        return false;
    }
    @Override public boolean dispatchTouchEvent(MotionEvent event){
        int action=event.getActionMasked();
        if(action==MotionEvent.ACTION_DOWN){edgeTouch=edge.running()||edge.physics.position!=0;edge.stop();touch=true;downX=event.getX();downY=event.getY();dragVelocity=0;lastOffsetTime=event.getEventTime();boolean slider=getChildCount()>0&&sliderAt(getChildAt(0),(int)event.getRawX(),(int)event.getRawY());gate.begin(slider,event.getPointerCount());}
        else if(action==MotionEvent.ACTION_POINTER_DOWN){gate.block();edge.reset();}
        else if(action==MotionEvent.ACTION_MOVE&&event.getPointerCount()==1)gate.move(edgeTouch?0:event.getX()-downX,event.getY()-downY,slop);
        boolean handled=super.dispatchTouchEvent(event);
        if(action==MotionEvent.ACTION_UP||action==MotionEvent.ACTION_CANCEL){touch=false;edgeTouch=false;gate.end();if(edge.physics.position!=0&&!edge.running())edge.release(action==MotionEvent.ACTION_CANCEL?0:event.getEventTime()-lastOffsetTime<80?dragVelocity:0);}
        return handled;
    }
    @Override public boolean onInterceptTouchEvent(MotionEvent event){
        // A translated-content spring is independent of ScrollView's scroller.
        // Keep its stopping touch in the parent even after edge.stop(), including
        // a later horizontal move or an UP after the offset has returned to zero.
        if(edgeTouch){if(event.getActionMasked()==MotionEvent.ACTION_DOWN)super.onInterceptTouchEvent(event);return true;}
        // Preserve ScrollView's DOWN interception while a fling is in flight:
        // touching a slider to stop a moving page must not become a slider tap.
        if(gate.horizontal())return false;
        if(event.getActionMasked()==MotionEvent.ACTION_MOVE&&touch&&!gate.allowed()&&event.getPointerCount()==1)return false;
        return super.onInterceptTouchEvent(event);
    }
    @Override public boolean onTouchEvent(MotionEvent event){
        if(event.getActionMasked()==MotionEvent.ACTION_UP&&edge.physics.position!=0){MotionEvent cancel=MotionEvent.obtain(event);cancel.setAction(MotionEvent.ACTION_CANCEL);boolean handled=super.onTouchEvent(cancel);cancel.recycle();return handled;}
        return super.onTouchEvent(event);
    }
    @Override protected boolean overScrollBy(int dx,int dy,int x,int y,int rangeX,int rangeY,int maxX,int maxY,boolean isTouch){
        long now=SystemClock.uptimeMillis();
        if(!edge.enabled()){edge.reset();return super.overScrollBy(dx,dy,x,y,rangeX,rangeY,0,0,isTouch);}
        if(isTouch&&gate.allowed()){
            edge.stop();double delta=dy,before=edge.physics.position;
            if(edge.physics.position!=0){ColorScrollPhysics.Pull pull=ColorScrollPhysics.pull(edge.physics.position,delta,edge.limit());edge.physics.position=pull.offset;delta=pull.remaining;}
            int proposed=y+(int)Math.round(delta),clamped=Math.max(0,Math.min(rangeY,proposed));
            if(proposed!=clamped){ColorScrollPhysics.Pull pull=ColorScrollPhysics.pull(edge.physics.position,proposed-clamped,edge.limit());edge.physics.position=pull.offset;}
            super.onOverScrolled(Math.max(0,Math.min(rangeX,x+dx)),clamped,false,false);
            double seconds=Math.max(.001,(now-lastOffsetTime)/1000d);if(before!=edge.physics.position){dragVelocity=(edge.physics.position-before)/seconds;lastOffsetTime=now;}applyOffset();return false;
        }
        int proposed=y+dy,clamped=Math.max(0,Math.min(rangeY,proposed));
        if(!isTouch&&!touch&&!edge.running()&&proposed!=clamped){
            super.onOverScrolled(Math.max(0,Math.min(rangeX,x+dx)),clamped,false,false);super.fling(0);
            edge.physics.position=-ColorScrollPhysics.resisted(proposed-clamped,0,edge.limit());double seconds=Math.max(1/120d,Math.min(.05,(now-lastScrollTime)/1000d));lastScrollTime=now;applyOffset();edge.release(-dy*.4/seconds);return false;
        }
        lastScrollTime=now;return super.overScrollBy(dx,dy,x,y,rangeX,rangeY,0,0,isTouch);
    }
    @Override protected void onDetachedFromWindow(){edge.reset();gate.end();touch=false;edgeTouch=false;super.onDetachedFromWindow();}
    @Override protected void onWindowVisibilityChanged(int visibility){super.onWindowVisibilityChanged(visibility);if(visibility!=VISIBLE&&edge!=null){edge.reset();gate.end();touch=false;edgeTouch=false;}}
    @Override public void onWindowFocusChanged(boolean focused){super.onWindowFocusChanged(focused);if(!focused){edge.reset();gate.end();touch=false;edgeTouch=false;}}
}
