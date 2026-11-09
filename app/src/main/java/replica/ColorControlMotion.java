package replica;

import android.animation.ValueAnimator;
import android.os.SystemClock;
import android.view.View;

/** Only posts frames while settling; no global ticker or retained Activity. */
final class ColorControlMotion implements Runnable {
    private final View host;
    private final ColorControlPhysics state;
    private final double positionThreshold,velocityThreshold;
    private float target,response=.3f,bounce;
    private long last,start;
    private boolean running;
    ColorControlMotion(View host,float initial){this(host,initial,.001,.01);}
    ColorControlMotion(View host,float initial,double positionThreshold,double velocityThreshold){
        this.host=host;state=new ColorControlPhysics(initial);target=initial;
        this.positionThreshold=positionThreshold;this.velocityThreshold=velocityThreshold;
    }
    float value(){return (float)state.position;}
    boolean running(){return running;}
    static float durationScale(View host){
        if(!ValueAnimator.areAnimatorsEnabled())return 0;
        // The framework keeps this process-wide value current. No resource/theme
        // lookup or Configuration hashing is needed on every animation frame.
        return Math.max(0,ValueAnimator.getDurationScale());
    }
    static boolean canAnimate(View host){return host.isAttachedToWindow()&&host.isShown()&&durationScale(host)>0;}
    void snap(float value){cancel();state.position=value;state.velocity=0;target=value;host.invalidate();}
    void animate(float value,float response,float bounce){
        boolean retarget=target!=value||this.response!=response||this.bounce!=bounce;
        target=value;this.response=response;this.bounce=bounce;
        if(!canAnimate(host)){snap(value);return;}
        if(state.position==value&&state.velocity==0){if(running)cancel();return;}
        if(running&&retarget)start=SystemClock.uptimeMillis();
        if(!running){running=true;last=start=SystemClock.uptimeMillis();host.postOnAnimation(this);}
    }
    void cancel(){host.removeCallbacks(this);running=false;}
    @Override public void run(){
        if(!running)return;float scale=durationScale(host);
        if(!host.isAttachedToWindow()||!host.isShown()||scale<=0){snap(target);return;}
        long now=SystemClock.uptimeMillis();double dt=Math.max(0,(now-last)/1000d/scale);last=now;state.step(target,response,bounce,dt);
        if((Math.abs(state.position-target)<positionThreshold&&Math.abs(state.velocity)<velocityThreshold)||now-start>5000*scale){snap(target);return;}
        host.invalidate();host.postOnAnimation(this);
    }
}
