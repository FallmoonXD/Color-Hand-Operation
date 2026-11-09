package replica;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.animation.ValueAnimator;
import android.content.Context;
import android.view.View;
import android.view.animation.Interpolator;
import android.widget.FrameLayout;

/** Two page layers inside the original glass window. Only the pages translate. */
final class WidgetDeck extends FrameLayout {
    interface Adapter {View page(int index);void position(int from,int to,float fraction);}
    static final Interpolator SCROLL=t->1-(float)Math.pow(1-t,4); // Launcher3 Interpolators.SCROLL
    private final Adapter adapter;
    private final int snapMs,minFling,minSnap;
    private View front,next;
    private int index,nextIndex,direction;
    private float progress;
    private ValueAnimator motion;
    private boolean canCatch;
    WidgetDeck(Context c,LauncherWidgetStyle style,Adapter adapter){
        super(c);this.adapter=adapter;snapMs=style.integer("config_pageSnapAnimationDuration",750);
        minFling=style.dim("min_fling_velocity",250);minSnap=style.dim("min_page_snap_velocity",1500);
        setClipChildren(true);setClipToPadding(true);
    }
    View current(){return front;}
    int index(){return index;}
    float progress(){return progress;}
    int direction(){return direction;}
    boolean hasNeighbor(){return next!=null;}
    int target(){return next==null?index:nextIndex;}
    void finish(){if(motion!=null)motion.end();}
    boolean catchScroll(){
        if(motion==null||!canCatch)return false;
        motion.removeAllListeners();motion.removeAllUpdateListeners();motion.cancel();motion=null;canCatch=false;return true;
    }
    void reset(int page){
        finish();removeAllViews();next=null;progress=0;index=page;front=adapter.page(page);
        addView(front,new LayoutParams(-1,-1));adapter.position(page,page,0);
    }
    private void discard(){
        if(next!=null)removeView(next);
        next=null;progress=0;if(front!=null)front.setTranslationX(0);adapter.position(index,index,0);
    }
    void preview(int dest,int sign,float p,boolean replace){
        finish();
        if(next==null||nextIndex!=dest||direction!=sign||replace){
            discard();nextIndex=dest;direction=sign;next=adapter.page(dest);addView(next,new LayoutParams(-1,-1));
        }
        pose(p);
    }
    private void pose(float value){
        progress=value;
        front.setTranslationX(-direction*getWidth()*value);
        next.setTranslationX(direction*getWidth()*(1-value));adapter.position(index,nextIndex,value);
    }
    void settle(boolean advance,Runnable done){settle(advance,0,false,done);}
    void settleGesture(boolean advance,float velocity){settle(advance,velocity,true,null);}
    private void settle(boolean advance,float speed,boolean interactive,Runnable done){
        if(next==null){if(done!=null)done.run();return;}
        float end=advance?1:0;
        Runnable complete=()->{
            motion=null;canCatch=false;
            if(advance){removeView(front);front=next;index=nextIndex;next=null;}
            discard();if(done!=null)done.run();
        };
        float remaining=Math.abs(end-progress);
        int duration=snapMs;
        if(Math.abs(speed)>=minFling){
            float half=getWidth()/2f,influence=(float)Math.sin((remaining-.5f)*.4712389167638204);
            duration=4*Math.round(1000*half*(1+influence)/Math.max(minSnap,Math.abs(speed)));
        }
        if(!isAttachedToWindow()||!ValueAnimator.areAnimatorsEnabled()||remaining==0||duration<1){complete.run();return;}
        canCatch=interactive;
        motion=ValueAnimator.ofFloat(progress,end);motion.setDuration(duration);motion.setInterpolator(SCROLL);
        motion.addUpdateListener(a->pose((float)a.getAnimatedValue()));
        motion.addListener(new AnimatorListenerAdapter(){@Override public void onAnimationEnd(Animator a){complete.run();}});
        motion.start();
    }
    void go(int dest,int sign,boolean replace,Runnable done){
        finish();preview(dest,sign,0,replace);settle(true,done);
    }
    @Override protected void onDetachedFromWindow(){finish();super.onDetachedFromWindow();}
}
