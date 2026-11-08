package replica;

import android.animation.*;
import android.view.*;

/** COUI's normalized spring equation; parameters from this phone's SystemUI. */
public final class ColorMotion {
 // SystemUI VolumeAnimUtil selects these once, when the class is first loaded.
 private static final boolean SIMPLE_VOLUME=simpleVolume(property("debug.systemui.gaussblur_level",-1),
         property("persist.sys.oplus.anim_level",-1));
 public static final TimeInterpolator VOLUME_IN=SIMPLE_VOLUME?spring(.9,0):spring(.7,.2);
 public static final TimeInterpolator VOLUME_OUT=SIMPLE_VOLUME?spring(.9,0):spring(.7,0);
 public static final long VOLUME_IN_MS=SIMPLE_VOLUME?300:700,VOLUME_OUT_MS=SIMPLE_VOLUME?250:500;
 // Settings: coui_center_dialog_{enter,exit}, coui_bottom_dialog_{enter,exit}.
 public static final TimeInterpolator ENTER=new android.view.animation.PathInterpolator(.3f,0,.1f,1);
 public static final TimeInterpolator EXIT=new android.view.animation.PathInterpolator(.3f,0,1,1);
 static final long CENTER_IN_MS=250,CENTER_OUT_MS=150,SHEET_MS=250;
 static final float CENTER_START_SCALE=.8f;
 public static final long EXPAND_MS=400;
 static boolean simpleVolume(int debug,int level){return (debug==-1?level:debug)>3;}
 private static int property(String key,int fallback){
  try{return (int)Class.forName("android.os.SystemProperties").getMethod("getInt",String.class,int.class).invoke(null,key,fallback);}
  catch(ReflectiveOperationException|RuntimeException unavailable){return fallback;}
 }
 // SystemUI CrossFadeHelper and one-handed display-area return.
 static final int MATERIAL_MS=210;
 static final TimeInterpolator MATERIAL_IN=new android.view.animation.PathInterpolator(.4f,0,1,1);
 public static int oneHandedDuration(android.content.Context context){
  int duration=600;
  try{
   android.content.res.Resources res=context.getPackageManager().getResourcesForApplication("com.android.systemui");
   int id=res.getIdentifier("config_one_handed_translate_animation_duration","integer","com.android.systemui");
   if(id!=0)duration=res.getInteger(id);
  }catch(android.content.pm.PackageManager.NameNotFoundException|android.content.res.Resources.NotFoundException ignored){}
  return Math.max(0,property("persist.debug.one_handed_translate_animation_duration",duration));
 }
 public static float oneHandedReturn(float t){return (float)(1-Math.cos(t*Math.PI/8)*Math.pow(2,-10*t));}
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
  }else if(!closing){
   v.setPivotX(v.getWidth()/2f);v.setPivotY(v.getHeight()/2f);
   float scale=CENTER_START_SCALE+(1-CENTER_START_SCALE)*f;
   v.setScaleX(scale);v.setScaleY(scale);
  }
  WindowManager.LayoutParams p=window.getAttributes();p.alpha=Math.max(0,Math.min(1,f));window.setAttributes(p);
 }
 public void enter(){
  if(!ValueAnimator.areAnimatorsEnabled()){if(!closing)pose(1);return;}
  window.getDecorView().post(()->{if(!closing)NativeMaterial.whenReady(window.getDecorView(),()->{if(!closing)animate(1,edge==0?CENTER_IN_MS:VOLUME_IN_MS,edge==0?ENTER:VOLUME_IN,null);});});
 }
 public void exit(Runnable done){if(closing)return;closing=true;NativeMaterial.cancelEntrance(window.getDecorView());PanelOverlayDialog.beginWindowExit(window);
  if(fraction<=0||!ValueAnimator.areAnimatorsEnabled()){done.run();return;}
  animate(0,edge==0?CENTER_OUT_MS:VOLUME_OUT_MS,edge==0?EXIT:VOLUME_OUT,done);
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
