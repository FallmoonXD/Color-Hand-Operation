package replica;
import android.animation.ValueAnimator;
import android.os.SystemClock;
import android.view.MotionEvent;
import android.view.View;
public final class SwitchMotionCheck {
    private static int checks;
    private static void check(boolean ok,String message){checks++;if(!ok)throw new AssertionError(message);}
    private static void settle(ColorSwitch view){for(int i=0;i<120&&!view.frames.isEmpty();i++)view.frame(SystemClock.now+16);}
    public static void main(String[] args){
        SystemClock.now=0;ColorSwitch a=new ColorSwitch();a.attached=false;a.setChecked(true);
        check(a.position.value()==1&&!a.position.running()&&a.frames.isEmpty(),"initial binding snaps without entrance animation");
        a.attached=true;a.setChecked(true);check(a.frames.isEmpty(),"identical checked state does not post a useless frame");
        a.setChecked(false);a.frame(16);float halfway=a.position.value();a.setChecked(false);
        check(a.position.value()==halfway&&a.position.running(),"same-value binding preserves ongoing movement");settle(a);check(a.position.value()==0,"unchanged binding still converges");

        SystemClock.now=0;ColorSwitch b=new ColorSwitch();final boolean[] started={false};b.setOnCheckedChangeListener((button,on)->{started[0]=b.position.running();SystemClock.now=100;});b.setChecked(true);
        check(started[0],"spring clock starts before synchronous business callback");b.frame(112);ColorControlPhysics expected=new ColorControlPhysics(0);expected.step(1,.4,.3,.112);
        check(Math.abs(b.position.value()-expected.position)<1e-6,"callback processing time is accounted for before first frame");
        b.setOnCheckedChangeListener(null);settle(b);check(b.position.value()==1&&b.frames.isEmpty(),"toggle ends without retained frame callback");

        ColorSwitch rejected=new ColorSwitch();rejected.setOnCheckedChangeListener((button,on)->{if(on)button.setChecked(false);});rejected.setChecked(true);settle(rejected);
        check(!rejected.isChecked()&&rejected.position.value()==0,"reentrant listener state wins over requested toggle");
        rejected.onTouchEvent(new MotionEvent(0,5,20));rejected.onTouchEvent(new MotionEvent(2,23,20));rejected.onTouchEvent(new MotionEvent(1,23,20));settle(rejected);
        check(!rejected.isChecked()&&rejected.position.value()==0,"drag release also honors listener-rejected state");

        ColorSwitch disabled=new ColorSwitch();disabled.setOnCheckedChangeListener((button,on)->button.setEnabled(false));disabled.setChecked(true);
        check(disabled.position.value()==1&&!disabled.position.running()&&disabled.frames.isEmpty(),"listener disabling switch stops both animations");
        disabled.setChecked(false);check(disabled.position.value()==0&&disabled.frames.isEmpty(),"disabled programmatic change snaps");
        ColorSwitch detached=new ColorSwitch();detached.setOnCheckedChangeListener((button,on)->detached.onDetachedFromWindow());detached.setChecked(true);
        check(detached.position.value()==1&&!detached.position.running()&&detached.frames.isEmpty(),"listener page removal releases animations");

        View host=new View();SystemClock.now=0;ColorControlMotion motion=new ColorControlMotion(host,0);motion.animate(0,.3f,0);check(host.frames.isEmpty(),"settled spring schedules no frame");
        motion.animate(1,.4f,.3f);host.frame(80);float before=motion.value();motion.animate(0,.4f,.3f);check(motion.value()==before,"rapid reversal has no position discontinuity");
        ValueAnimator.scale=0;host.frame(96);check(motion.value()==0&&!motion.running()&&host.frames.isEmpty(),"zero animation scale immediately settles and removes callbacks");
        ValueAnimator.scale=2;SystemClock.now=0;motion.animate(1,.4f,.3f);host.frame(160);expected=new ColorControlPhysics(0);expected.step(1,.4,.3,.08);check(Math.abs(motion.value()-expected.position)<1e-6,"framework duration scale is applied once");
        host.shown=false;host.frame(176);check(motion.value()==1&&!motion.running(),"hidden host settles without a persistent ticker");
        ValueAnimator.scale=1;host=new View();SystemClock.now=0;motion=new ColorControlMotion(host,0);motion.animate(1,.4f,.3f);
        for(int i=1;i<=100;i++){host.frame(i*50);motion.animate(i%2==0?1:0,.4f,.3f);}host.frame(5016);
        check(motion.running()&&motion.value()!=1,"continuous retargeting gets a fresh settling deadline instead of a five-second snap");
        System.out.println("PASS "+checks+" switch callback/motion assertions");
    }
}
