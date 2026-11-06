package replica;

import android.animation.ValueAnimator;
import android.content.Context;
import android.graphics.Rect;
import android.os.SystemClock;
import android.view.Choreographer;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewConfiguration;
import android.view.ViewGroup;
import android.widget.ScrollView;
import android.widget.SeekBar;

/** Native in-range scrolling with a COUI-style translated-content boundary spring. */
public final class ColorSettingsScrollView extends ScrollView {
    private final ColorScrollPhysics physics=new ColorScrollPhysics();
    private final ColorScrollPhysics.Gate gate=new ColorScrollPhysics.Gate();
    private final int slop;
    private boolean touch,spring,edgeTouch;
    private float downX,downY;
    private long lastFrame,lastOffsetTime,lastScrollTime;
    private double dragVelocity;
    private final Choreographer.FrameCallback frame=new Choreographer.FrameCallback(){
        @Override public void doFrame(long nanos){
            if(!spring)return;
            if(!isAttachedToWindow()||!isShown()||!animations()){resetEdge();return;}
            if(lastFrame!=0){double seconds=(nanos-lastFrame)/1_000_000_000d/Math.max(.01f,ColorOsTheme.animationScale(getContext()));physics.step(Math.max(0,seconds));if(Math.abs(physics.position)>=limit()){physics.position=Math.copySign(limit()*.999,physics.position);physics.velocity=0;}applyOffset();}
            lastFrame=nanos;
            if(physics.position==0&&physics.velocity==0){spring=false;lastFrame=0;}else Choreographer.getInstance().postFrameCallback(this);
        }
    };
    public ColorSettingsScrollView(Context context){super(context);slop=ViewConfiguration.get(context).getScaledTouchSlop();setOverScrollMode(OVER_SCROLL_NEVER);ColorOsTheme.watch(this,()->{if(!animations())resetEdge();});}
    private boolean animations(){return ValueAnimator.areAnimatorsEnabled()&&ColorOsTheme.animationScale(getContext())>0;}
    private double limit(){return Math.max(1,getHeight());}
    private void applyOffset(){if(getChildCount()>0)getChildAt(0).setTranslationY((float)physics.position);}
    private void stopSpring(){Choreographer.getInstance().removeFrameCallback(frame);spring=false;lastFrame=0;}
    private void resetEdge(){stopSpring();physics.reset();dragVelocity=0;applyOffset();}
    private void release(double velocity){
        stopSpring();if(!animations()||!isAttachedToWindow()||Math.abs(physics.position)<.25){resetEdge();return;}
        physics.velocity=Math.max(-limit()*4,Math.min(limit()*4,velocity));spring=true;Choreographer.getInstance().postFrameCallback(frame);
    }
    private boolean sliderAt(View view,int x,int y){
        if(view.getVisibility()!=VISIBLE)return false;Rect bounds=new Rect();if(!view.getGlobalVisibleRect(bounds)||!bounds.contains(x,y))return false;
        if(view instanceof SeekBar)return true;
        if(view instanceof ViewGroup){ViewGroup group=(ViewGroup)view;for(int i=group.getChildCount()-1;i>=0;i--)if(sliderAt(group.getChildAt(i),x,y))return true;}
        return false;
    }
    @Override public boolean dispatchTouchEvent(MotionEvent event){
        int action=event.getActionMasked();
        if(action==MotionEvent.ACTION_DOWN){edgeTouch=spring||physics.position!=0;stopSpring();touch=true;downX=event.getX();downY=event.getY();dragVelocity=0;lastOffsetTime=event.getEventTime();boolean slider=getChildCount()>0&&sliderAt(getChildAt(0),(int)event.getRawX(),(int)event.getRawY());gate.begin(slider,event.getPointerCount());}
        else if(action==MotionEvent.ACTION_POINTER_DOWN){gate.block();resetEdge();}
        else if(action==MotionEvent.ACTION_MOVE&&event.getPointerCount()==1)gate.move(edgeTouch?0:event.getX()-downX,event.getY()-downY,slop);
        boolean handled=super.dispatchTouchEvent(event);
        if(action==MotionEvent.ACTION_UP||action==MotionEvent.ACTION_CANCEL){touch=false;edgeTouch=false;gate.end();if(physics.position!=0&&!spring)release(action==MotionEvent.ACTION_CANCEL?0:event.getEventTime()-lastOffsetTime<80?dragVelocity:0);}
        return handled;
    }
    @Override public boolean onInterceptTouchEvent(MotionEvent event){
        // A translated-content spring is independent of ScrollView's scroller.
        // Keep its stopping touch in the parent even after stopSpring(), including
        // a later horizontal move or an UP after the offset has returned to zero.
        if(edgeTouch){if(event.getActionMasked()==MotionEvent.ACTION_DOWN)super.onInterceptTouchEvent(event);return true;}
        // Preserve ScrollView's DOWN interception while a fling is in flight:
        // touching a slider to stop a moving page must not become a slider tap.
        if(gate.horizontal())return false;
        if(event.getActionMasked()==MotionEvent.ACTION_MOVE&&touch&&!gate.allowed()&&event.getPointerCount()==1)return false;
        return super.onInterceptTouchEvent(event);
    }
    @Override public boolean onTouchEvent(MotionEvent event){
        if(event.getActionMasked()==MotionEvent.ACTION_UP&&physics.position!=0){MotionEvent cancel=MotionEvent.obtain(event);cancel.setAction(MotionEvent.ACTION_CANCEL);boolean handled=super.onTouchEvent(cancel);cancel.recycle();return handled;}
        return super.onTouchEvent(event);
    }
    @Override protected boolean overScrollBy(int dx,int dy,int x,int y,int rangeX,int rangeY,int maxX,int maxY,boolean isTouch){
        long now=SystemClock.uptimeMillis();
        if(!animations()){resetEdge();return super.overScrollBy(dx,dy,x,y,rangeX,rangeY,0,0,isTouch);}
        if(isTouch&&gate.allowed()){
            stopSpring();double delta=dy,before=physics.position;
            if(physics.position!=0){ColorScrollPhysics.Pull pull=ColorScrollPhysics.pull(physics.position,delta,limit());physics.position=pull.offset;delta=pull.remaining;}
            int proposed=y+(int)Math.round(delta),clamped=Math.max(0,Math.min(rangeY,proposed));
            if(proposed!=clamped){ColorScrollPhysics.Pull pull=ColorScrollPhysics.pull(physics.position,proposed-clamped,limit());physics.position=pull.offset;}
            super.onOverScrolled(Math.max(0,Math.min(rangeX,x+dx)),clamped,false,false);
            double seconds=Math.max(.001,(now-lastOffsetTime)/1000d);if(before!=physics.position){dragVelocity=(physics.position-before)/seconds;lastOffsetTime=now;}applyOffset();return false;
        }
        int proposed=y+dy,clamped=Math.max(0,Math.min(rangeY,proposed));
        if(!isTouch&&!touch&&!spring&&proposed!=clamped){
            super.onOverScrolled(Math.max(0,Math.min(rangeX,x+dx)),clamped,false,false);super.fling(0);
            physics.position=-ColorScrollPhysics.resisted(proposed-clamped,0,limit());double seconds=Math.max(1/120d,Math.min(.05,(now-lastScrollTime)/1000d));lastScrollTime=now;applyOffset();release(-dy*.4/seconds);return false;
        }
        lastScrollTime=now;return super.overScrollBy(dx,dy,x,y,rangeX,rangeY,0,0,isTouch);
    }
    @Override protected void onDetachedFromWindow(){resetEdge();gate.end();touch=false;edgeTouch=false;super.onDetachedFromWindow();}
    @Override protected void onWindowVisibilityChanged(int visibility){super.onWindowVisibilityChanged(visibility);if(visibility!=VISIBLE&&physics!=null){resetEdge();gate.end();touch=false;edgeTouch=false;}}
    @Override public void onWindowFocusChanged(boolean focused){super.onWindowFocusChanged(focused);if(!focused){resetEdge();gate.end();touch=false;edgeTouch=false;}}
}
