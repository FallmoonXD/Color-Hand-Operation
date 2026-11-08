package replica;

import android.animation.*;
import android.view.*;

/** COUI's normalized spring equation; parameters from this phone's SystemUI. */
public final class ColorMotion {
 public static final TimeInterpolator VOLUME_IN=spring(.7,.2), VOLUME_OUT=spring(.7,0);
 public static final long VOLUME_IN_MS=700,VOLUME_OUT_MS=500;
 private static final long PANEL_OUT_MS=150;
 // Same easing as the COUI sheet, with a shorter exit for a compact floating panel.
 static final TimeInterpolator PANEL_EXIT=new android.view.animation.PathInterpolator(.3f,0,1,1);
 public static TimeInterpolator spring(double response,double bounce){
  final double w=2*Math.PI/response,z=1-bounce;
  return new TimeInterpolator(){
   private double raw(double t){
    if(z==1)return 1-Math.exp(-w*t)*(1+w*t);
    double wd=w*Math.sqrt(1-z*z);
    return 1-Math.exp(-z*w*t)*(Math.cos(wd*t)+z*w/wd*Math.sin(wd*t));
   }
   public float getInterpolation(float t){return (float)(raw(Math.max(0,t))/raw(1));}
  };
 }
 private final Window window;private final int edge;private final float edgePivotY;
 private ValueAnimator animator;private boolean closing;
 private float fraction;
 public ColorMotion(Window w){this(w,0,0);}
 public static ColorMotion fromEdge(Window w,boolean left,float pivotY){return new ColorMotion(w,left?-1:1,pivotY);}
 private ColorMotion(Window w,int edge,float pivotY){window=w;this.edge=edge;edgePivotY=pivotY;w.setWindowAnimations(0);pose(0);}
 private void pose(float f){
  fraction=f;View v=window.getDecorView();
  if(edge!=0){
   v.setPivotX(edge<0?0:v.getWidth());v.setPivotY(Math.max(0,Math.min(v.getHeight(),edgePivotY)));
   v.setScaleX(.5f+.5f*f);v.setScaleY(.5f+.5f*f);v.setTranslationX(edge*PanelStyle.dp(v.getContext(),14)*(1-f));
  }else{
   v.setPivotX(v.getWidth()/2f);v.setPivotY(v.getHeight()/2f);
   v.setScaleX(.88f+.12f*f);v.setScaleY(.88f+.12f*f);v.setTranslationY(PanelStyle.dp(v.getContext(),12)*(1-f));
  }
  WindowManager.LayoutParams p=window.getAttributes();p.alpha=Math.max(0,Math.min(1,f));window.setAttributes(p);
 }
 public void enter(){window.getDecorView().post(()->{if(!closing)NativeMaterial.whenReady(window.getDecorView(),()->{if(!closing)animate(1,edge==0?350:VOLUME_IN_MS,edge==0?spring(.3,0):VOLUME_IN,null);});});}
 public void exit(Runnable done){if(closing)return;closing=true;NativeMaterial.cancelEntrance(window.getDecorView());PanelOverlayDialog.beginWindowExit(window);
  if(fraction<=0||!ValueAnimator.areAnimatorsEnabled()){done.run();return;}
  // If dismissed during entry, only animate the fraction that has appeared.
  long duration=edge==0?Math.round(PANEL_OUT_MS*Math.min(1f,fraction)):VOLUME_OUT_MS;
  animate(0,duration,edge==0?PANEL_EXIT:VOLUME_OUT,done);
 }
 private void animate(float end,long duration,TimeInterpolator curve,Runnable done){
  if(animator!=null){animator.removeAllListeners();animator.cancel();}
  animator=ValueAnimator.ofFloat(fraction,end);animator.setDuration(duration);animator.setInterpolator(curve);
  animator.addUpdateListener(a->pose((Float)a.getAnimatedValue()));
  if(done!=null)animator.addListener(new AnimatorListenerAdapter(){public void onAnimationEnd(Animator a){done.run();}});
  animator.start();
 }
 public void cancel(){closing=true;NativeMaterial.cancelEntrance(window.getDecorView());if(animator!=null){animator.removeAllListeners();animator.cancel();}}
}
