package replica;

import android.animation.*;
import android.app.Dialog;
import android.content.Context;
import android.media.AudioManager;
import android.os.Handler;
import android.view.*;
import android.view.animation.DecelerateInterpolator;
import android.widget.*;

/** A non-focusable volume window. Outside taps continue to the foreground app. */
public final class VolumeOverlay {
 private static VolumeOverlay current;
 private final Handler handler=new Handler();
 private final Context context;
 private final WindowManager manager;
 private final LinearLayout view;
 private final WindowManager.LayoutParams params;
 private final RepeatPress up,down;
 private Dialog dialog;
 private ValueAnimator enterAnimation,exitAnimation;
 private float poseAlpha=0,poseX=.5f,poseY=.5f,poseOffset;
 private float edgeDirection;
 private boolean mounted,closing,removed;
 private final Runnable close=()->dismiss();

 public static void show(Context c){
  show(c,NativeState.gesture(c));
 }
 /** Avoid retaining an accessibility context after its service is disabled. */
 public static void dismissAll(){if(current!=null)current.remove();}
 public static void show(Context c,float[] anchor){
  if(current!=null&&!current.closing){current.arm();return;}
  if(current!=null)current.remove();
  VolumeOverlay next=new VolumeOverlay(c,anchor);
  current=next.mounted?next:null;
 }

 private VolumeOverlay(Context c,float[] anchor){
  context=c;manager=(WindowManager)c.getSystemService(Context.WINDOW_SERVICE);
  AudioManager audio=(AudioManager)c.getSystemService(Context.AUDIO_SERVICE);
  view=new LinearLayout(c);view.setOrientation(1);view.setElevation(PanelStyle.dp(c,8));
  ImageView plus=button("ic_expand_less_rounded","音量增加"),minus=button("ic_expand_more_rounded","音量减小");
  view.addView(plus,new LinearLayout.LayoutParams(-1,PanelStyle.dp(c,70)));
  view.addView(minus,new LinearLayout.LayoutParams(-1,PanelStyle.dp(c,70)));
  up=new RepeatPress(plus,()->{if(!closing){audio.adjustStreamVolume(3,1,AudioManager.FLAG_SHOW_UI);arm();}});
  down=new RepeatPress(minus,()->{if(!closing){audio.adjustStreamVolume(3,-1,AudioManager.FLAG_SHOW_UI);arm();}});
  view.addOnAttachStateChangeListener(new View.OnAttachStateChangeListener(){public void onViewAttachedToWindow(View v){}public void onViewDetachedFromWindow(View v){if(mounted)remove();}});
  int flags=WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE|WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL|WindowManager.LayoutParams.FLAG_WATCH_OUTSIDE_TOUCH;
  params=new WindowManager.LayoutParams(PanelStyle.dp(c,60),-2,c instanceof android.accessibilityservice.AccessibilityService?2032:2038,flags,android.graphics.PixelFormat.TRANSLUCENT);
  float[] g=anchor;android.graphics.Point screen=PanelGeometry.screen(c);
  params.gravity=Gravity.TOP|Gravity.LEFT;
  edgeDirection=g[0]<screen.x/2f?-1f:1f;poseOffset=edgeDirection*PanelStyle.dp(c,14);params.alpha=0;
  params.x=g[0]<screen.x/2f?PanelStyle.dp(c,60):screen.x-PanelStyle.dp(c,120);
  params.y=Math.max(PanelGeometry.inset(c,"status_bar_height"),Math.min(screen.y-PanelStyle.dp(c,140)-PanelGeometry.inset(c,"navigation_bar_height"),Math.round((g[1]+g[3])/2f)-PanelStyle.dp(c,70)));
  PanelGeometry.screenCoordinates(params);
  params.setTitle(context.getString(dev.edgeflow.R.string.app_name)+" 音量控制");
  try{
   int theme=c.getResources().getIdentifier(PanelStyle.dark(c)?"ReplicaGlassDark":"ReplicaGlass","style",c.getPackageName());
   dialog=new Dialog(c,theme){@Override public boolean onTouchEvent(MotionEvent e){if(e.getActionMasked()==MotionEvent.ACTION_OUTSIDE){VolumeOverlay.this.dismiss();return true;}return super.onTouchEvent(e);}};
   dialog.setCanceledOnTouchOutside(false);dialog.setOnDismissListener(d->{if(mounted)remove();});dialog.setContentView(view);
   Window window=dialog.getWindow();window.setType(params.type);window.setAttributes(params);
   window.setWindowAnimations(0);window.setBackgroundDrawable(new ColorOsSurface(c,22,2).attach(window));
   window.getClass().getMethod("setBackgroundBlurRadius",int.class).invoke(window,PanelStyle.blurRadius(c));
   dialog.show();window.setLayout(params.width,-2);window.getDecorView().setClipToOutline(true);mounted=true;view.post(this::enter);arm();
  }catch(Exception e){
   android.util.Log.w("ReplicaVolume","Dialog blur unavailable",e);
   if(dialog!=null){try{dialog.dismiss();}catch(Exception ignored){}dialog=null;}
   if(view.getParent() instanceof ViewGroup)((ViewGroup)view.getParent()).removeView(view);
   view.setBackground(new ColorOsSurface(c,22,2).attach(view));
   view.setOnTouchListener((v,event)->{if(event.getActionMasked()==MotionEvent.ACTION_OUTSIDE){dismiss();return true;}return false;});
   try{manager.addView(view,params);mounted=true;view.post(this::enter);arm();}catch(Exception ignored){}
  }
 }

 private ImageView button(String name,String description){ImageView t=new ImageView(context);t.setImageDrawable(SystemIcons.raw(context,name));t.setColorFilter(android.graphics.Color.WHITE);t.setScaleType(ImageView.ScaleType.FIT_CENTER);t.setPadding(PanelStyle.dp(context,14),PanelStyle.dp(context,19),PanelStyle.dp(context,14),PanelStyle.dp(context,19));t.setContentDescription(description);t.setBackgroundColor(0);return t;}

 private void arm(){if(closing)return;handler.removeCallbacks(close);android.view.accessibility.AccessibilityManager a=(android.view.accessibility.AccessibilityManager)context.getSystemService(Context.ACCESSIBILITY_SERVICE);handler.postDelayed(close,a.getRecommendedTimeoutMillis(3000,4));}
 private void pose(float alpha,float sx,float sy,float offset){
  if(removed)return;poseAlpha=alpha;poseX=sx;poseY=sy;poseOffset=offset;
  try{
   View target=view;
   if(dialog!=null){Window w=dialog.getWindow();WindowManager.LayoutParams p=w.getAttributes();p.alpha=alpha;if(closing)p.flags|=WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE;w.setAttributes(p);target=w.getDecorView();}
   else{params.alpha=alpha;if(closing)params.flags|=WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE;manager.updateViewLayout(view,params);}
   target.setPivotX(edgeDirection<0?0:target.getWidth());target.setPivotY(target.getHeight()/2f);target.setScaleX(sx);target.setScaleY(sy);target.setTranslationX(offset);
  }catch(Exception ignored){remove();}
 }
 private void enter(){
  if(closing||removed)return;
  pose(0,.5f,.5f,edgeDirection*PanelStyle.dp(context,14));
  NativeMaterial.whenReady(dialog!=null?dialog.getWindow().getDecorView():view,this::startEntrance);
 }
 private void startEntrance(){
  if(closing||removed)return;arm();
  enterAnimation=ValueAnimator.ofFloat(0,1);enterAnimation.setDuration(ColorMotion.VOLUME_IN_MS);enterAnimation.setInterpolator(new android.view.animation.LinearInterpolator());
  enterAnimation.addUpdateListener(a->{if(closing||removed)return;float t=(Float)a.getAnimatedValue();float spring=ColorMotion.VOLUME_IN.getInterpolation(t);pose(Math.max(0,Math.min(1,spring)),.5f+.5f*spring,.5f+.5f*spring,edgeDirection*PanelStyle.dp(context,14)*(1-spring));});
  enterAnimation.addListener(new AnimatorListenerAdapter(){@Override public void onAnimationEnd(Animator a){if(!closing&&!removed)pose(1,1,1,0);}});enterAnimation.start();
 }
 private void dismiss(){
  if(closing||removed)return;
  closing=true;handler.removeCallbacks(close);up.stop();down.stop();
  NativeMaterial.cancelEntrance(dialog!=null?dialog.getWindow().getDecorView():view);
  if(enterAnimation!=null)enterAnimation.cancel();
  final float startAlpha=poseAlpha,startX=poseX,startY=poseY,startOffset=poseOffset;
  exitAnimation=ValueAnimator.ofFloat(0f,1f);exitAnimation.setDuration(ColorMotion.VOLUME_OUT_MS);exitAnimation.setInterpolator(new android.view.animation.LinearInterpolator());
  exitAnimation.addUpdateListener(animation->{
   if(removed)return;float t=(Float)animation.getAnimatedValue();
   float u=ColorMotion.VOLUME_OUT.getInterpolation(t);pose(startAlpha*(1-u),startX+(.5f-startX)*u,startY+(.5f-startY)*u,startOffset+(edgeDirection*PanelStyle.dp(context,14)-startOffset)*u);
  });
  exitAnimation.addListener(new AnimatorListenerAdapter(){@Override public void onAnimationEnd(Animator animation){remove();}});
  exitAnimation.start();
 }
 private void remove(){
  if(removed)return;removed=true;mounted=false;up.stop();down.stop();handler.removeCallbacksAndMessages(null);
  NativeMaterial.cancelEntrance(dialog!=null?dialog.getWindow().getDecorView():view);
  if(exitAnimation!=null&&exitAnimation.isRunning())exitAnimation.cancel();
  if(enterAnimation!=null&&enterAnimation.isRunning())enterAnimation.cancel();
  try{if(dialog!=null)dialog.dismiss();else manager.removeView(view);}catch(Exception ignored){}
  if(current==this)current=null;
 }
}
