public final class SheetResizeCheck {
    static int checks;
    static void check(boolean value,String message){checks++;if(!value)throw new AssertionError(message);}
    static void drain(View view){for(int frame=0;frame<200&&view.pending!=null;frame++)view.frame();check(view.pending==null,"Motion settles without an idle callback loop");}
    public static void main(String[] args){
        View view=new View();Rect[] current={null};int[] updates={0};
        ColorSheetResizeMotion motion=new ColorSheetResizeMotion(view,rect->{current[0]=rect;updates[0]++;});
        motion.move(0,195,1208,2123,false);
        check(current[0].top==195&&current[0].bottom==2318,"Initial bounds available before entrance");
        motion.move(0,0,1208,1298,true);
        check(current[0].top==195,"IME change does not jump to final bounds");
        view.frame();check(current[0].top>0&&current[0].top<195,"IME opening has intermediate position");
        int previous=current[0].top;
        while(view.pending!=null){view.frame();check(current[0].top<=previous,"No overshoot for native zero-bounce response");previous=current[0].top;}
        check(current[0].top==0&&current[0].bottom==1298,"Open settles to visible area");
        motion.move(0,195,1208,2123,true);view.frame();view.frame();
        Rect reversal=current[0];motion.move(0,0,1208,1298,true);
        check(current[0]==reversal,"Reversal retains visible pose");drain(view);
        check(current[0].top==0&&current[0].bottom==1298,"Reversal settles to latest target");
        motion.move(0,195,1208,2123,true);view.frame();motion.cancel();int count=updates[0];
        for(int frame=0;frame<30;frame++)view.frame();
        check(updates[0]==count,"Dialog dismissal freezes keyboard geometry");
        ColorControlMotion.enabled=false;motion.move(0,195,1208,2123,true);
        check(current[0].top==195&&view.pending==null,"Disabled animation applies final bounds immediately");
        ColorControlMotion.enabled=true;motion.move(0,0,1208,1298,true);view.frame();
        motion.move(25,0,900,600,false);
        check(current[0].left==25&&current[0].right==925&&view.pending==null,"Rotation cancels stale animation bounds");
        System.out.println("PASS "+checks+" sheet resize interruption and geometry assertions");
    }
}
class Rect {final int left,top,right,bottom;Rect(int left,int top,int right,int bottom){this.left=left;this.top=top;this.right=right;this.bottom=bottom;}}
class SystemClock {static long now;static long uptimeMillis(){return now;}}
class View {
    Runnable pending;
    void postOnAnimation(Runnable callback){pending=callback;}
    void removeCallbacks(Runnable callback){if(pending==callback)pending=null;}
    boolean isAttachedToWindow(){return true;}boolean isShown(){return true;}
    void frame(){SystemClock.now+=16;Runnable callback=pending;pending=null;if(callback!=null)callback.run();}
}
class ColorControlMotion {static boolean enabled=true;static boolean canAnimate(View view){return enabled;}static float durationScale(View view){return enabled?1:0;}}
