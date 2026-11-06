import replica.ColorTitleMotion;

public final class TitleMotionCheck {
    private static int count;
    private static void check(boolean ok,String name){count++;if(!ok)throw new AssertionError(name);}
    private static void close(float a,float b,float tolerance,String name){check(Math.abs(a-b)<=tolerance,name+": "+a+" vs "+b);}
    public static void main(String[] args){
        close(ColorTitleMotion.progress(-30,60),0,0,"pull-down retains expanded progress");
        close(ColorTitleMotion.progress(0,60),0,0,"expanded endpoint");
        close(ColorTitleMotion.progress(60,60),1,0,"collapsed endpoint");
        close(ColorTitleMotion.progress(600,60),1,0,"title stays pinned after collapse");
        close(ColorTitleMotion.progress(0,0),0,0,"zero range remains finite");
        close(ColorTitleMotion.progress(1,0),1,0,"zero range clamps safely");
        close(ColorTitleMotion.ease(0),0,0,"exact curve start");
        close(ColorTitleMotion.ease(1),1,0,"exact curve end");
        close(ColorTitleMotion.ease(.5f),.5f,.00001f,"source symmetric midpoint");
        close(ColorTitleMotion.lerp(32,18,0),32,0,"accepted expanded size");
        close(ColorTitleMotion.lerp(32,18,1),18,0,"accepted collapsed size");
        close(ColorTitleMotion.alpha(0),1,0,"expanded fully opaque");
        close(ColorTitleMotion.alpha(1),1,0,"collapsed fully opaque");
        close(ColorTitleMotion.alpha(.5f),.75f,.00001f,"single-run complementary crossfade");
        boolean monotonic=true,continuous=true,readable=true,reversible=true,symmetric=true;
        float previousY=100,previousSize=32,previousX=24;
        for(int i=0;i<=1000;i++){
            float p=i/1000f,y=ColorTitleMotion.lerp(100,32,p),size=ColorTitleMotion.lerp(32,18,p),x=ColorTitleMotion.lerp(24,20,p),alpha=ColorTitleMotion.alpha(p);
            monotonic&=y<=previousY+.00001f&&size<=previousSize+.00001f&&x<=previousX+.00001f;
            continuous&=Math.abs(y-previousY)<.12f&&Math.abs(size-previousSize)<.03f;
            readable&=alpha>=.75f&&alpha<=1;
            reversible&=Math.abs(y-ColorTitleMotion.lerp(32,100,1-p))<.0002f;
            symmetric&=Math.abs(alpha-ColorTitleMotion.alpha(1-p))<.00001f;
            previousY=y;previousSize=size;previousX=x;
        }
        check(monotonic,"no direction reversal during upward scroll");
        check(continuous,"no threshold jumps in geometry");
        check(readable,"no invisible interval during handoff");
        check(reversible,"reverse scrolling uses identical path without timing state");
        check(symmetric,"alpha is symmetric on reversal");
        for(float p:new float[]{0,.1f,.3f,.5f,.8f,1}){
            close(ColorTitleMotion.lerp(300,304,p),324-ColorTitleMotion.lerp(24,20,p),.0001f,"RTL mirror at "+p);
            close(ColorTitleMotion.lerp(132,64,p)-ColorTitleMotion.lerp(100,32,p),32,.0001f,"insets preserve path at "+p);
        }
        check(ColorTitleMotion.ease(.001f)<.00001f&&1-ColorTitleMotion.ease(.999f)<.00001f,"zero-slope endpoint approach");
        System.out.println(count+" title motion assertions passed");
    }
}
