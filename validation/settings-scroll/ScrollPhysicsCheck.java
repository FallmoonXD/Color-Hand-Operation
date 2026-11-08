import replica.ColorScrollPhysics;

public final class ScrollPhysicsCheck {
    private static int count;
    private static void check(boolean ok,String message){count++;if(!ok)throw new AssertionError(message);}
    private static void near(double a,double b,double tolerance,String message){check(Math.abs(a-b)<=tolerance,message+": "+a+" / "+b);}
    private static ColorScrollPhysics evolve(double x,double v,int fps,double seconds){ColorScrollPhysics p=new ColorScrollPhysics();p.position=x;p.velocity=v;for(int i=0;i<Math.round(fps*seconds);i++)p.step(1d/fps);return p;}
    public static void main(String[] args){
        near(ColorScrollPhysics.DAMPING,37.57,1e-8,"source friction conversion");near(ColorScrollPhysics.STIFFNESS,209.928,1e-8,"source ScrollView tension multiplier");
        near(ColorScrollPhysics.resisted(100,0,1000),40,1e-9,"first edge pull has source 0.4 resistance");near(ColorScrollPhysics.resisted(100,500,1000),20,1e-9,"resistance grows with distance");near(ColorScrollPhysics.resisted(-100,-500,1000),-20,1e-9,"top and bottom use symmetric resistance");
        ColorScrollPhysics.Pull start=ColorScrollPhysics.pull(0,-100,1000);near(start.offset,40,1e-9,"negative native scroll translates top content down");near(start.remaining,0,0,"edge pull does not scroll base position");
        ColorScrollPhysics.Pull reverse=ColorScrollPhysics.pull(40,200,1000);near(reverse.offset,0,0,"inward reversal returns content to boundary");check(reverse.remaining>0&&reverse.remaining<200,"excess inward drag returns to native scrolling");
        ColorScrollPhysics.Pull bottom=ColorScrollPhysics.pull(-40,-200,1000);near(bottom.offset,0,0,"bottom inward reversal returns to boundary");near(bottom.remaining,-reverse.remaining,1e-9,"remaining native distance is symmetric");
        check(Math.abs(ColorScrollPhysics.pull(900,-100000,1000).offset)<1000,"extreme input cannot cross viewport cap");
        // The installed COUI scroller advances again when a step moves <= .5px.
        // Its presented trajectory depends on refresh rate. Compare each rate
        // with its own native trace (see NativeMotionCheck.scroll).
        for(int fps:new int[]{60,90,120}){
            ColorScrollPhysics edge=new ColorScrollPhysics();edge.position=80;int frames=0;
            do{edge.step(1d/fps);frames++;}while(edge.position>8&&frames<100);
            check(frames<100,"edge reaches its final 10% at "+fps+" Hz");
            if(fps==120)near(frames,35,0,"measured 120 Hz native return");
            for(double x:new double[]{-400,120})for(double v:new double[]{0,800}){
                ColorScrollPhysics partial=evolve(x,v,fps,.4);
                check(Double.isFinite(partial.velocity)&&Math.abs(partial.position)<Math.abs(x),"return converges at "+fps+" Hz");
                ColorScrollPhysics settled=evolve(x,v,fps,3);near(settled.position,0,0,"settles to exact rest");near(settled.velocity,0,0,"no residual velocity");
            }
        }
        ColorScrollPhysics p=new ColorScrollPhysics();p.position=300;double energy=ColorScrollPhysics.STIFFNESS*p.position*p.position/2;boolean decays=true,monotonic=true;double previous=p.position;
        for(int i=0;i<300;i++){p.step(1d/120);double next=(p.velocity*p.velocity+ColorScrollPhysics.STIFFNESS*p.position*p.position)/2;decays&=next<=energy+1e-8;energy=next;monotonic&=p.position>=0&&p.position<=previous;previous=p.position;}
        check(decays,"spring dissipates energy instead of running forever");check(monotonic,"rest release never bounces to opposite edge");p.position=20;p.velocity=-10000;p.step(.04);near(p.position,0,0,"strong inward release stops at boundary");p.position=20;p.velocity=200;p.reset();near(p.position,0,0,"cancel/detach/zero-motion reset removes offset");near(p.velocity,0,0,"cancel reset removes velocity");
        ColorScrollPhysics.Gate gate=new ColorScrollPhysics.Gate();gate.begin(false,1);gate.move(1,5,8);check(!gate.allowed(),"touch slop does not begin edge pull");gate.move(2,20,8);check(gate.allowed(),"clear vertical drag permits edge effect");gate.end();check(!gate.allowed(),"UP or CANCEL ends edge eligibility");
        gate.begin(true,1);gate.move(0,200,8);check(gate.slider()&&gate.allowed(),"vertical drag beginning on slider belongs to page");
        gate.begin(true,1);gate.move(20,21,8);check(gate.allowed(),"diagonal vertical intent cannot leak X drift to slider");
        gate.begin(true,1);gate.move(20,20,8);check(gate.allowed(),"equal diagonal favors scrolling rather than editing");
        gate.begin(true,1);gate.move(40,5,8);check(gate.horizontal()&&!gate.allowed(),"horizontal slider drag belongs to SeekBar");gate.move(40,200,8);check(gate.horizontal(),"established slider drag does not switch owner halfway");
        gate.begin(true,1);gate.move(3,4,8);check(!gate.allowed()&&!gate.horizontal(),"stationary slider tap waits for framework UP");
        gate.begin(false,1);gate.move(40,5,8);gate.move(40,200,8);check(gate.horizontal()&&!gate.allowed(),"horizontal intent remains locked during later diagonal motion");
        gate.begin(false,1);gate.move(0,100,8);gate.block();gate.move(0,200,8);check(!gate.allowed(),"second pointer cancels edge handling until next DOWN");gate.begin(false,2);gate.move(0,200,8);check(!gate.allowed(),"multi-pointer start is excluded");gate.begin(false,1);gate.move(0,30,8);check(gate.allowed(),"next independent gesture can pull after cancellation");
        boolean rejected=false;try{ColorScrollPhysics.resisted(1,0,0);}catch(IllegalArgumentException e){rejected=true;}check(rejected,"invalid viewport rejected");rejected=false;try{p.step(Double.NaN);}catch(IllegalArgumentException e){rejected=true;}check(rejected,"invalid time cannot start endless animation");
        System.out.println("PASS "+count+" Settings edge spring assertions");
    }
}
