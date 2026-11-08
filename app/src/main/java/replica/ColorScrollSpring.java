package replica;

import android.animation.ValueAnimator;
import android.view.Choreographer;
import android.view.View;

/** The same COUI return motion drives a page edge, whether or not the content fills the screen. */
final class ColorScrollSpring implements Choreographer.FrameCallback {
    final ColorScrollPhysics physics=new ColorScrollPhysics();
    private final View host;
    private final Runnable apply;
    private long lastFrame;
    private boolean running;

    ColorScrollSpring(View host,Runnable apply){this.host=host;this.apply=apply;}
    boolean enabled(){return ValueAnimator.areAnimatorsEnabled()&&ColorOsTheme.animationScale(host.getContext())>0;}
    boolean running(){return running;}
    float position(){return (float)physics.position;}
    double limit(){return Math.max(1,host.getHeight());}
    void dragBy(double scrollDelta){
        ColorScrollPhysics.Pull pull=ColorScrollPhysics.pull(physics.position,scrollDelta,limit());
        physics.position=pull.remaining==0?pull.offset:ColorScrollPhysics.pull(0,pull.remaining,limit()).offset;
        apply.run();
    }
    void stop(){if(running)Choreographer.getInstance().removeFrameCallback(this);running=false;lastFrame=0;}
    void reset(){stop();physics.reset();apply.run();}
    void release(double velocity){
        stop();
        if(!enabled()||!host.isAttachedToWindow()||Math.abs(physics.position)<.25){reset();return;}
        physics.beginReturn(Math.max(-limit()*4,Math.min(limit()*4,velocity)));
        lastFrame=System.nanoTime();running=true;Choreographer.getInstance().postFrameCallback(this);
    }
    @Override public void doFrame(long nanos){
        if(!running)return;
        if(!host.isAttachedToWindow()||!host.isShown()||!enabled()){reset();return;}
        if(nanos>lastFrame){
            double seconds=(nanos-lastFrame)/1e9;
            double interval=seconds>.025?.008:Math.max(.008,seconds);
            physics.step(interval/Math.max(.01f,ColorOsTheme.animationScale(host.getContext())));
            if(Math.abs(physics.position)>=limit()){
                physics.position=Math.copySign(limit()*.999,physics.position);physics.velocity=0;
            }
            apply.run();
            lastFrame=nanos;
        }
        if(physics.position==0&&physics.velocity==0){running=false;lastFrame=0;}
        else Choreographer.getInstance().postFrameCallback(this);
    }
}
