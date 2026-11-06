import replica.ColorControlPhysics;

public final class ControlPhysicsCheck {
    static int count;
    static void check(boolean condition,String label){count++;if(!condition)throw new AssertionError(label);}
    static void near(double a,double b,double tolerance,String label){check(Math.abs(a-b)<=tolerance,label+" ["+a+", "+b+"]");}
    static ColorControlPhysics evolve(double response,double bounce,int fps,double seconds){ColorControlPhysics s=new ColorControlPhysics(0);for(int i=0;i<(int)(fps*seconds);i++)s.step(1,response,bounce,1d/fps);return s;}
    public static void main(String[] args){
        for(double[] params:new double[][]{{.4,.3},{.4,.2},{.3,0},{.5,0},{.5,.3}}){
            ColorControlPhysics a=evolve(params[0],params[1],60,.5),b=evolve(params[0],params[1],120,.5),single=new ColorControlPhysics(0);single.step(1,params[0],params[1],.5);
            near(a.position,b.position,1e-10,"spring position independent of 60/120Hz");near(a.velocity,b.velocity,1e-9,"spring velocity independent of 60/120Hz");near(a.position,single.position,1e-10,"dropped frames preserve analytic spring time");
            ColorControlPhysics settled=evolve(params[0],params[1],60,3);near(settled.position,1,1e-9,"finite spring settles to target");near(settled.velocity,0,1e-8,"finite spring stops moving");
        }
        ColorControlPhysics critical=new ColorControlPhysics(0);double last=0;boolean monotonic=true;for(int i=0;i<180;i++){critical.step(1,.3,0,1d/60);monotonic&=critical.position>=last&&critical.position<=1;last=critical.position;}check(monotonic,"zero-bounce press never overshoots or reverses");
        ColorControlPhysics bouncing=new ColorControlPhysics(0);double max=0;for(int i=0;i<120;i++){bouncing.step(1,.4,.3,1d/120);max=Math.max(max,bouncing.position);}check(max>1&&max<1.1,"COUI toggle spring has bounded overshoot");
        ColorControlPhysics retarget=new ColorControlPhysics(0);retarget.step(1,.4,.3,.08);double before=retarget.position,velocity=retarget.velocity;retarget.step(0,.4,.3,0);near(retarget.position,before,0,"retarget has no position jump");near(retarget.velocity,velocity,0,"retarget retains current velocity");retarget.step(0,.4,.3,3);near(retarget.position,0,1e-9,"rapid reverse settles at latest target");
        near(ColorControlPhysics.fraction(15,10,20),.5,0,"nonzero slider min is honored");near(ColorControlPhysics.fraction(0,0,0),0,0,"zero slider range is finite");near(ColorControlPhysics.fraction(Integer.MAX_VALUE,Integer.MIN_VALUE,Integer.MAX_VALUE),1,0,"wide slider range avoids signed overflow");near(ColorControlPhysics.fraction(-1,0,100),0,0,"slider fraction clamps below min");near(ColorControlPhysics.fraction(101,0,100),1,0,"slider fraction clamps above max");
        for(boolean rtl:new boolean[]{false,true})for(float p:new float[]{0,.25f,.75f,1}){float physical=ColorControlPhysics.physicalFraction(p,rtl);near(ColorControlPhysics.physicalFraction(physical,rtl),p,0,"RTL coordinate transform is its own inverse");check(ColorControlPhysics.checkedAt(physical,rtl)==(p>.5f),"visual side and switch selected state agree");}
        near(ColorControlPhysics.switchStretch(0),1,1e-6,"resting off thumb is circular");near(ColorControlPhysics.switchStretch(1),1,1e-6,"resting on thumb is circular");near(ColorControlPhysics.switchStretch(133f/383f),1.3,1e-6,"COUI thumb reaches source-defined stretch peak");
        boolean invalid=false;try{new ColorControlPhysics(0).step(1,0,0,.1);}catch(IllegalArgumentException expected){invalid=true;}check(invalid,"invalid response cannot start endless animation");
        System.out.println("PASS "+count+" ColorOS control physics assertions");
    }
}
