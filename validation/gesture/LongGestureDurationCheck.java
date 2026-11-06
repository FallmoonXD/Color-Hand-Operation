import dev.edgeflow.GestureRecognizer;

/** Regression for the release path that used to bypass the configured duration. */
public final class LongGestureDurationCheck {
    private static int checks;
    private static void check(boolean ok,String message){checks++;if(!ok)throw new AssertionError(message);}
    private static GestureRecognizer begin(boolean left,int delay,boolean quick){GestureRecognizer g=new GestureRecognizer(138,306,delay,30,false,false,quick);g.begin(left,left?5:1259,1300,1000);return g;}
    private static void moveLong(GestureRecognizer g,boolean left,long time){g.move(left?355:909,1300,time);}
    public static void main(String[] args){
        for(boolean left:new boolean[]{false,true})for(boolean quick:new boolean[]{false,true})for(int delay:new int[]{1,300,1000})for(int boundary:new int[]{-1,0,1}){
            GestureRecognizer g=begin(left,delay,quick);long up=1000+delay+boundary;moveLong(g,left,up);
            check(g.isLongReady()==(boundary>=0),"distance AND duration side="+left+" quick="+quick+" delay="+delay+" boundary="+boundary);
            check(g.finish(up)==(boundary>=0?2:1),"UP boundary cannot bypass duration");
            check(g.pendingLongDelay(up)==-1&&!g.shouldFireLong(up+1000)&&g.finish(up+1000)==0,"finished gesture cannot revive its timer");
        }
        for(boolean quick:new boolean[]{false,true}){
            GestureRecognizer g=begin(true,300,quick);moveLong(g,true,1100);
            check(g.pendingLongDelay(1100)==(quick?200:-1),"only quick-long schedules held dispatch");
            check(!g.shouldFireLong(1299),"held timer cannot fire one millisecond early");
            check(g.shouldFireLong(1300)==quick,"held deadline inclusive and quick-long aware");
            check(g.finish(1300)==2,"UP evaluates its own time after a stationary hold");

            g=begin(true,300,quick);moveLong(g,true,1100);g.move(165,1300,1200);
            check(g.pendingLongDelay(1200)==-1&&!g.shouldFireLong(1500),"retraction removes long eligibility and timer");
            moveLong(g,true,1250);check(!g.isLongReady()&&g.pendingLongDelay(1250)==(quick?50:-1),"reenter before deadline keeps original DOWN deadline");
            check(g.finish(1299)==1,"reentry never bypasses remaining duration");

            g=begin(true,300,quick);moveLong(g,true,1100);g.move(165,1300,1400);moveLong(g,true,1500);
            check(g.isLongReady()&&g.finish(1500)==2,"reenter after original deadline does not restart hold time");

            g=begin(false,300,quick);g.move(909,950,1100);g.move(909,1650,1299);
            check(g.direction()==GestureRecognizer.DOWN&&!g.isLongReady(),"direction changes select current direction without bypassing time");
            check(g.finish(1300)==2,"direction change retains original duration deadline");

            g=begin(true,300,quick);moveLong(g,true,1100);g.move(15,2300,1400);
            check(!g.isShortReady()&&!g.isLongReady()&&g.finish(1400)==0,"late nearly vertical motion cannot inherit long readiness");

            g=begin(true,300,quick);moveLong(g,true,1400);g.move(-100,1300,1500);
            check(!g.shouldFireLong(2000)&&g.finish(2000)==0,"outward retraction clears prior qualified long");

            g=begin(true,300,quick);moveLong(g,true,1100);g.cancel();g.begin(false,1259,1300,2000);moveLong(g,false,2100);
            check(!g.shouldFireLong(2299)&&g.finish(2299)==1,"new DOWN does not inherit cancelled deadline");
        }
        GestureRecognizer zero=begin(true,0,true);moveLong(zero,true,1000);check(zero.shouldFireLong(1000)&&zero.finish(1000)==2,"explicit zero duration needs distance only");
        GestureRecognizer shortQuick=new GestureRecognizer(138,306,1000,30,false,true,true);shortQuick.begin(true,5,1300,1000);moveLong(shortQuick,true,1100);check(shortQuick.shouldFireShort()&&shortQuick.finish(1100)==1,"short quick-action priority does not become a premature long");
        System.out.println("PASS "+checks+" long-duration boundary assertions");
    }
}
