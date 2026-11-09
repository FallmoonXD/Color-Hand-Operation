package replica;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.animation.TimeInterpolator;
import android.animation.ValueAnimator;
import android.view.View;
import android.view.animation.LinearInterpolator;
import java.util.function.Consumer;
import static replica.ToolAnimation.Style;

/** Drives both the preview tile and the real window. A new phase starts at the visible pose. */
public final class ToolMotionPlayer {
    private final View target;
    private final Consumer<Float> opacity;
    private final boolean miniature;
    private boolean left;
    private float anchorY;
    private float alpha;
    private boolean played;
    private ValueAnimator animator;
    private Runnable pendingLayout;

    public ToolMotionPlayer(View target,boolean left,float anchorY,boolean miniature,Consumer<Float> opacity){
        this.target=target;this.left=left;this.anchorY=anchorY;this.miniature=miniature;this.opacity=opacity;
    }
    public void prepare(){cancel();played=false;setAlpha(0);}
    void reanchor(boolean left,float y){this.left=left;anchorY=y;}
    public void shown(){
        cancel();played=true;target.setScaleX(1);target.setScaleY(1);target.setTranslationX(0);target.setTranslationY(0);setAlpha(1);
    }
    public void cancel(){
        if(pendingLayout!=null){target.removeCallbacks(pendingLayout);pendingLayout=null;}
        if(animator!=null){animator.removeAllListeners();animator.removeAllUpdateListeners();animator.cancel();animator=null;}
    }
    private void setAlpha(float value){alpha=Math.max(0,Math.min(1,value));opacity.accept(alpha);}
    private void pivot(float x,float y){
        target.setTranslationX(target.getTranslationX()+(1-target.getScaleX())*(target.getPivotX()-x));
        target.setTranslationY(target.getTranslationY()+(1-target.getScaleY())*(target.getPivotY()-y));
        target.setPivotX(x);target.setPivotY(y);
    }
    public void play(Style style,boolean enter,Runnable done){
        cancel();
        if(style==Style.NONE||!ValueAnimator.areAnimatorsEnabled()||(!enter&&alpha<=0)){
            if(enter)shown();else setAlpha(0);
            if(done!=null)done.run();return;
        }
        if(enter&&(target.getWidth()==0||target.getHeight()==0)){
            pendingLayout=()->{pendingLayout=null;if(target.isAttachedToWindow())play(style,true,done);};
            target.postOnAnimation(pendingLayout);return;
        }
        animator=buildPhase(style,enter);
        if(animator==null){
            if(enter)shown();else setAlpha(0);
            if(done!=null)done.run();return;
        }
        animator.addListener(new AnimatorListenerAdapter(){@Override public void onAnimationEnd(Animator a){animator=null;if(done!=null)done.run();}});
        animator.start();
    }
    /** A thumbnail samples these same frames without starting a second clock. */
    final class PreviewPhase {
        private final ValueAnimator frames;
        private final boolean entering;
        private final float pivotX,pivotY;
        final long duration;
        private PreviewPhase(Style style,boolean enter){
            entering=enter;
            if(enter)prepare();else shown();
            frames=style==Style.NONE?null:buildPhase(style,enter);
            duration=frames==null?0:frames.getDuration();
            pivotX=target.getPivotX();pivotY=target.getPivotY();
        }
        void sample(long time){
            if(time>=duration){
                if(entering)shown();else setAlpha(0);
                return;
            }
            target.setPivotX(pivotX);target.setPivotY(pivotY);
            frames.setCurrentFraction(time/(float)duration);
        }
    }
    PreviewPhase previewPhase(Style style,boolean enter){return new PreviewPhase(style,enter);}
    private ValueAnimator buildPhase(Style style,boolean enter){
        int w=Math.max(1,target.getWidth()),h=Math.max(1,target.getHeight());
        int[] loc=new int[2];target.getLocationOnScreen(loc);
        float originX=loc[0]-target.getTranslationX()-(1-target.getScaleX())*target.getPivotX();
        float originY=loc[1]-target.getTranslationY()-(1-target.getScaleY())*target.getPivotY();
        float density=target.getResources().getDisplayMetrics().density;
        float unit=miniature?w/260f:density;
        float pixelsPerDrawnPixel=density/unit;
        float edgeGap=miniature?w*.14f:left?originX:PanelGeometry.screen(target.getContext()).x-originX-w;
        boolean edge=style==Style.VOLUME||style==Style.EXPANDED_VOLUME||style==Style.SIDEBAR||style==Style.ANCHOR||style==Style.SLIDE;
        float pivotY=edge&&Float.isFinite(anchorY)?Math.max(0,Math.min(h,anchorY-originY)):h*.5f;
        pivot(edge?(left?0:w):w*.5f,pivotY);
        float sx=1,sy=1,dx=0,dy=0,hiddenAlpha=0;
        long duration;TimeInterpolator curve;
        switch(style){
            case VOLUME:case EXPANDED_VOLUME:
                sx=sy=.5f;dx=(left?-1:1)*14*unit;
                duration=enter?(style==Style.EXPANDED_VOLUME?ColorMotion.MULTI_VOLUME_IN_MS:ColorMotion.VOLUME_IN_MS):ColorMotion.VOLUME_OUT_MS;
                curve=enter?ColorMotion.VOLUME_IN:ColorMotion.VOLUME_OUT;break;
            case SIDEBAR:
                sx=Math.min(1,4*unit/w);sy=Math.min(1,60*unit/h);dx=(left?-1:1)*Math.max(0,edgeGap);
                duration=0;curve=null;break;
            case ANCHOR:
                sx=sy=0;duration=0;curve=null;break;
            case SHEET:
                dy=h;hiddenAlpha=1;duration=ColorMotion.SHEET_MS;curve=enter?ColorMotion.ENTER:ColorMotion.EXIT;break;
            case SLIDE:
                dx=(left?-1:1)*w;hiddenAlpha=1;duration=ColorPageMotion.PAGE_MS;
                curve=enter?ColorPageMotion.OPEN_IN:ColorPageMotion.CLOSE_OUT;break;
            case SCREEN:
                // Preview of the default full-screen tool: only its captured image returns.
                dx=enter?0:w*.25f;hiddenAlpha=1;duration=enter?0:ColorMotion.oneHandedDuration(target.getContext());curve=ColorMotion::oneHandedReturn;break;
            case CENTER:
                sx=enter?ColorMotion.CENTER_START_SCALE:target.getScaleX();
                sy=enter?ColorMotion.CENTER_START_SCALE:target.getScaleY();
                duration=enter?ColorMotion.CENTER_IN_MS:ColorMotion.CENTER_OUT_MS;curve=enter?ColorMotion.ENTER:ColorMotion.EXIT;break;
            default:throw new IllegalArgumentException("Unresolved animation "+style);
        }
        if(enter&&!played){
            target.setScaleX(sx);target.setScaleY(sy);target.setTranslationX(dx);target.setTranslationY(dy);setAlpha(hiddenAlpha);
        }
        if(style==Style.SCREEN&&!enter)target.setTranslationX(dx);
        played=true;
        float startX=target.getScaleX(),startY=target.getScaleY(),startDx=target.getTranslationX(),startDy=target.getTranslationY(),startAlpha=alpha;
        boolean fadeOnly=!enter&&style==Style.CENTER;
        float endX=enter?1:sx,endY=enter?1:sy,endDx=fadeOnly?startDx:enter||style==Style.SCREEN?0:dx,endDy=fadeOnly?startDy:enter?0:dy;
        float endAlpha=enter?1:hiddenAlpha==1?startAlpha:0;
        ColorSpringTrack width,height,travel,fade;
        if(style==Style.SIDEBAR){
            // The native spring settles in physical pixels. Solve at full size
            // before projecting onto the tile, or a thumbnail settles too early.
            width=ColorSpringTrack.sidebar(startX*w*pixelsPerDrawnPixel,endX*w*pixelsPerDrawnPixel,enter,false,true);
            height=ColorSpringTrack.sidebar(startY*h*pixelsPerDrawnPixel,endY*h*pixelsPerDrawnPixel,enter,false,false);
            travel=ColorSpringTrack.sidebar(startDx*pixelsPerDrawnPixel,endDx*pixelsPerDrawnPixel,enter,true,false);
            fade=null;duration=Math.max(width.durationMs,Math.max(height.durationMs,travel.durationMs));
        }else if(style==Style.ANCHOR){
            width=ColorSpringTrack.popupScale(startX,enter);height=ColorSpringTrack.popupScale(startY,enter);
            fade=ColorSpringTrack.popupOpacity(startAlpha,enter);travel=null;
            duration=Math.max(fade.durationMs,Math.max(width.durationMs,height.durationMs));
        }else{width=height=travel=fade=null;}
        if(duration==0)return null;
        final float length=duration;
        ValueAnimator frames=ValueAnimator.ofFloat(0,length);frames.setDuration(duration);frames.setInterpolator(new LinearInterpolator());
        frames.addUpdateListener(a->{
            float t=(float)a.getAnimatedValue(),f=curve==null?t/length:curve.getInterpolation(t/length);
            float x=lerp(startX,endX,f),y=lerp(startY,endY,f),offset=lerp(startDx,endDx,f),opacityValue=lerp(startAlpha,endAlpha,f);
            if(style==Style.SIDEBAR){
                x=width.valueAt(t)/(w*pixelsPerDrawnPixel);y=height.valueAt(t)/(h*pixelsPerDrawnPixel);
                offset=travel.valueAt(t)/pixelsPerDrawnPixel;
                float widthProgress=endX==startX?f:(x-startX)/(endX-startX);
                opacityValue=lerp(startAlpha,endAlpha,Math.max(0,Math.min(1,widthProgress)));
            }else if(style==Style.ANCHOR){x=width.valueAt(t);y=height.valueAt(t);opacityValue=fade.valueAt(t);}
            target.setScaleX(Math.max(0,x));target.setScaleY(Math.max(0,y));target.setTranslationX(offset);target.setTranslationY(lerp(startDy,endDy,f));setAlpha(opacityValue);
        });
        return frames;
    }
    private static float lerp(float a,float b,float t){return a+(b-a)*t;}
}
