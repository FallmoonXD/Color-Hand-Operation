package replica;

import android.content.Context;
import android.animation.ValueAnimator;
import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.view.Gravity;
import android.view.View;
import android.view.animation.LinearInterpolator;
import android.widget.FrameLayout;
import java.util.ArrayList;

/** A small screen and tool, drawn locally and animated by the same player as the window. */
public final class ToolAnimationPreview extends FrameLayout {
    private final View card;
    private final Paint paint=new Paint(Paint.ANTI_ALIAS_FLAG);
    private final ToolAnimation.Style entryStyle,exitStyle;
    private final ToolMotionPlayer player;
    private ToolMotionPlayer.PreviewPhase entry,exit;
    private int drawnWidth,drawnHeight;
    private ToolMotionPlayer.PreviewPhase lastPhase;
    private long lastTime;
    public ToolAnimationPreview(Context c,ToolAnimation.Style entryStyle,ToolAnimation.Style exitStyle,Loop loop){
        super(c);this.entryStyle=entryStyle;this.exitStyle=exitStyle;setWillNotDraw(false);
        setBackground(PanelStyle.shape(c,ColorOsTheme.dark(c)?0xff292a30:0xffecedf2,10));
        setClipToOutline(true);
        card=new View(c){
            @Override protected void onDraw(Canvas canvas){
                float w=getWidth(),h=getHeight();
                paint.setColor(ColorOsTheme.dark(c)?0xff595c67:0xffcdd2e0);
                canvas.drawRoundRect(0,0,w,h,h*.20f,h*.20f,paint);
                paint.setColor(ColorOsTheme.accent(c));
                canvas.drawRoundRect(w*.13f,h*.18f,w*.81f,h*.29f,h*.06f,h*.06f,paint);
                for(int i=0;i<3;i++)canvas.drawCircle(w*(.22f+i*.26f),h*.66f,h*.11f,paint);
            }
        };
        LayoutParams panel=new LayoutParams(dp(35),dp(30),Gravity.RIGHT|Gravity.CENTER_VERTICAL);
        panel.rightMargin=dp(7);addView(card,panel);
        player=new ToolMotionPlayer(card,false,Float.NaN,true,card::setAlpha);
        player.shown();loop.rows.add(this);
        setOnClickListener(v->loop.replay());setFocusable(true);
    }
    private int dp(float value){return PanelStyle.dp(getContext(),value);}
    @Override public CharSequence getAccessibilityClassName(){return "android.widget.Button";}
    @Override protected void onDraw(Canvas canvas){
        super.onDraw(canvas);
        paint.setColor(ColorOsTheme.dark(getContext())?0xff42444b:0xffd9dce5);
        for(int i=0;i<3;i++)canvas.drawRoundRect(dp(6),dp(8+i*12),dp(i%2==0?29:24),dp(11+i*12),dp(2),dp(2),paint);
        paint.setColor(ColorOsTheme.accent(getContext()));canvas.drawRoundRect(getWidth()-dp(2),getHeight()*.36f,getWidth(),getHeight()*.64f,dp(1),dp(1),paint);
    }
    private void prepareFrames(){
        if(entry!=null&&drawnWidth==card.getWidth()&&drawnHeight==card.getHeight())return;
        drawnWidth=card.getWidth();drawnHeight=card.getHeight();
        entry=player.previewPhase(entryStyle,true);exit=player.previewPhase(exitStyle,false);
        lastPhase=null;
    }
    private void drawPhase(ToolMotionPlayer.PreviewPhase phase,long time){
        time=Math.min(time,phase.duration);
        if(phase==lastPhase&&time==lastTime)return;
        phase.sample(time);lastPhase=phase;lastTime=time;
    }

    /** One page clock drives every row, including those reached by scrolling mid-cycle. */
    public static final class Loop {
        // A common stop in the open position makes the two phases distinct.
        static final int OPEN_HOLD_MS=450;
        private final ArrayList<ToolAnimationPreview> rows=new ArrayList<>();
        private final View page;
        private final Runnable begin=this::startWhenLaidOut;
        private final ValueAnimator.DurationScaleChangeListener scaleListener=scale->pageScaleChanged();
        private ValueAnimator clock;
        private boolean active,roundBoundary;
        private long exitStart,cycleDuration;
        public Loop(View page){this.page=page;}
        public void start(){
            if(active)return;
            active=true;
            ValueAnimator.registerDurationScaleChangeListener(scaleListener);
            replay();
        }
        public void stop(){
            if(!active)return;
            active=false;
            ValueAnimator.unregisterDurationScaleChangeListener(scaleListener);
            cancelClock();
        }
        private void pageScaleChanged(){if(active)page.post(begin);}
        private void cancelClock(){
            page.removeCallbacks(begin);
            if(clock!=null){clock.removeAllListeners();clock.cancel();clock=null;}
        }
        private void replay(){
            if(!active)return;
            cancelClock();page.postOnAnimation(begin);
        }
        private void startWhenLaidOut(){
            if(!active||!page.isAttachedToWindow())return;
            cancelClock();
            if(!ValueAnimator.areAnimatorsEnabled()){
                for(ToolAnimationPreview row:rows){row.player.shown();row.lastPhase=null;}
                return;
            }
            long longestEntry=0,longestExit=0;
            for(ToolAnimationPreview row:rows){
                if(row.card.getWidth()==0||row.card.getHeight()==0){page.postOnAnimation(begin);return;}
                row.prepareFrames();row.lastPhase=null;
                longestEntry=Math.max(longestEntry,row.entry.duration);
                longestExit=Math.max(longestExit,row.exit.duration);
            }
            exitStart=longestEntry+OPEN_HOLD_MS;
            cycleDuration=exitStart+longestExit;
            clock=ValueAnimator.ofInt(0,(int)cycleDuration);
            roundBoundary=false;
            clock.setDuration(cycleDuration);clock.setRepeatCount(ValueAnimator.INFINITE);
            clock.setInterpolator(new LinearInterpolator());
            clock.addListener(new AnimatorListenerAdapter(){
                @Override public void onAnimationRepeat(Animator animator){roundBoundary=true;}
            });
            clock.addUpdateListener(a->{
                // One visible endpoint for instant exits. The next frame still uses
                // the original clock, so missed vsyncs cannot lengthen each round.
                drawAt(roundBoundary?cycleDuration:(int)a.getAnimatedValue());
                roundBoundary=false;
            });
            drawAt(0);clock.start();
        }
        private void drawAt(long time){
            for(ToolAnimationPreview row:rows){
                if(time<exitStart)row.drawPhase(row.entry,time);
                else row.drawPhase(row.exit,time-exitStart);
            }
        }
    }
}
