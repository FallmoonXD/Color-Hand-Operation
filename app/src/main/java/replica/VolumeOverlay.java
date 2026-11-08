package replica;

import android.animation.*;
import android.accessibilityservice.AccessibilityService;
import android.app.Dialog;
import android.content.Context;
import android.graphics.Rect;
import android.media.AudioManager;
import android.os.Handler;
import android.os.SystemClock;
import android.view.*;
import android.view.accessibility.AccessibilityEvent;
import android.view.accessibility.AccessibilityWindowInfo;
import android.widget.*;

/** A pair of non-focusable volume buttons that follows the system volume panel. */
public final class VolumeOverlay {
 private static VolumeOverlay current;
 private final Handler handler=new Handler();
 private final Context context;
 private final WindowManager manager;
 private final LinearLayout view;
 private final WindowManager.LayoutParams params;
 private final RepeatPress up,down;
 private final SystemVolumeWindow systemWindow;
 private final int displayWidth;
 private Dialog dialog;
 private ValueAnimator enterAnimation,exitAnimation;
 private float poseAlpha=0,poseX=.5f,poseY=.5f,poseOffset;
 private float edgeDirection;
 private boolean mounted,closing,removed;
 private int referenceDepth,referenceSide;
 private boolean followingRetreat;
 private long outsideAt=-1,appExitUntil,adjustmentUntil;
 private final Runnable fallbackTimeout=()->{if(!checkSystemWindow())dismiss();};
 private final Runnable refresh=this::checkSystemWindow;
 private final Runnable confirmHidden=()->{if(!checkSystemWindow())remove();};
 private final Runnable outsideFallback;

 public static void show(Context c){
  show(c,NativeState.gesture(c));
 }
 /** Avoid retaining an accessibility context after its service is disabled. */
 public static void dismissAll(){if(current!=null)current.remove();}
 public static void show(Context c,float[] anchor){
  if(current!=null&&!current.closing){current.adjusted(3000);return;}
  if(current!=null)current.remove();
  VolumeOverlay next=new VolumeOverlay(c,anchor);
  current=next.mounted?next:null;
 }

 private VolumeOverlay(Context c,float[] anchor){
  context=c;manager=(WindowManager)c.getSystemService(Context.WINDOW_SERVICE);systemWindow=new SystemVolumeWindow(c);
  outsideFallback=()->{if(!checkSystemWindow()&&!systemWindow.tracked())dismiss();};
  AudioManager audio=(AudioManager)c.getSystemService(Context.AUDIO_SERVICE);
  view=new LinearLayout(c){
   @Override public boolean dispatchTouchEvent(MotionEvent event){
    return closing||(dialog==null&&outsideTouch(event))||super.dispatchTouchEvent(event);
   }
  };view.setOrientation(1);view.setElevation(PanelStyle.dp(c,8));
  ImageView plus=button("ic_expand_less_rounded","音量增加"),minus=button("ic_expand_more_rounded","音量减小");
  view.addView(plus,new LinearLayout.LayoutParams(-1,PanelStyle.dp(c,70)));
  view.addView(minus,new LinearLayout.LayoutParams(-1,PanelStyle.dp(c,70)));
  up=new RepeatPress(plus,()->{if(!closing){audio.adjustStreamVolume(3,1,AudioManager.FLAG_SHOW_UI);adjusted(1500);}});
  down=new RepeatPress(minus,()->{if(!closing){audio.adjustStreamVolume(3,-1,AudioManager.FLAG_SHOW_UI);adjusted(1500);}});
  view.addOnAttachStateChangeListener(new View.OnAttachStateChangeListener(){public void onViewAttachedToWindow(View v){}public void onViewDetachedFromWindow(View v){if(mounted)remove();}});
  int flags=WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE|WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL|WindowManager.LayoutParams.FLAG_WATCH_OUTSIDE_TOUCH;
  params=new WindowManager.LayoutParams(PanelStyle.dp(c,60),-2,c instanceof android.accessibilityservice.AccessibilityService?2032:2038,flags,android.graphics.PixelFormat.TRANSLUCENT);
  float[] g=anchor;android.graphics.Point screen=PanelGeometry.screen(c);displayWidth=screen.x;
  params.gravity=Gravity.TOP|Gravity.LEFT;
  edgeDirection=g[0]<screen.x/2f?-1f:1f;poseOffset=edgeDirection*PanelStyle.dp(c,14);params.alpha=0;
  params.x=g[0]<screen.x/2f?PanelStyle.dp(c,60):screen.x-PanelStyle.dp(c,120);
  params.y=Math.max(PanelGeometry.inset(c,"status_bar_height"),Math.min(screen.y-PanelStyle.dp(c,140)-PanelGeometry.inset(c,"navigation_bar_height"),Math.round((g[1]+g[3])/2f)-PanelStyle.dp(c,70)));
  PanelGeometry.screenCoordinates(params);
  params.setTitle(context.getString(dev.edgeflow.R.string.app_name)+" 音量控制");
  try{
   int theme=c.getResources().getIdentifier(PanelStyle.dark(c)?"ReplicaGlassDark":"ReplicaGlass","style",c.getPackageName());
   dialog=new Dialog(c,theme){@Override public boolean dispatchTouchEvent(MotionEvent event){return outsideTouch(event)||super.dispatchTouchEvent(event);}};
   dialog.setCanceledOnTouchOutside(false);dialog.setOnDismissListener(d->{if(d==dialog&&mounted)remove();});dialog.setContentView(view);
   Window window=dialog.getWindow();window.setType(params.type);window.setAttributes(params);
   window.setWindowAnimations(0);window.setBackgroundDrawable(new ColorOsSurface(c,22,2).attach(window));
   window.getClass().getMethod("setBackgroundBlurRadius",int.class).invoke(window,PanelStyle.blurRadius(c));
   dialog.show();window.setLayout(params.width,-2);window.getDecorView().setClipToOutline(true);mounted=true;
  }catch(Exception e){
   android.util.Log.w("ReplicaVolume","Dialog blur unavailable",e);
   if(dialog!=null){try{dialog.dismiss();}catch(Exception ignored){}dialog=null;}
   if(view.getParent() instanceof ViewGroup)((ViewGroup)view.getParent()).removeView(view);
   view.setBackground(new ColorOsSurface(c,22,2).attach(view));
   PanelGeometry.reserveShadow(params,(int)Math.ceil(view.getElevation()*4));
   try{manager.addView(view,params);mounted=true;}catch(Exception ignored){}
  }
  if(mounted){view.post(this::enter);armFallback(3000);queueWindowCheck();}
 }

 private ImageView button(String name,String description){ImageView t=new ImageView(context);t.setImageDrawable(SystemIcons.raw(context,name));t.setColorFilter(android.graphics.Color.WHITE);t.setScaleType(ImageView.ScaleType.FIT_CENTER);t.setPadding(PanelStyle.dp(context,14),PanelStyle.dp(context,19),PanelStyle.dp(context,14),PanelStyle.dp(context,19));t.setContentDescription(description);t.setBackgroundColor(0);return t;}

 private void armFallback(int millis){
  if(closing||systemWindow.tracked())return;
  android.view.accessibility.AccessibilityManager a=context.getSystemService(android.view.accessibility.AccessibilityManager.class);
  handler.removeCallbacks(fallbackTimeout);handler.postDelayed(fallbackTimeout,a.getRecommendedTimeoutMillis(millis,android.view.accessibility.AccessibilityManager.FLAG_CONTENT_CONTROLS));
 }
 private void adjusted(int timeout){
  // A new FLAG_SHOW_UI request may replace a native window already on its way out.
  outsideAt=-1;appExitUntil=0;adjustmentUntil=SystemClock.uptimeMillis()+200;
  handler.removeCallbacks(confirmHidden);
  if(followingRetreat){followingRetreat=false;exitAnimation.cancel();edgePose(1);}
  armFallback(timeout);queueWindowCheck();
 }
 public static void systemWindowsChanged(AccessibilityService service,AccessibilityEvent event){
  VolumeOverlay owner=current;
  if(owner==null||owner.context!=service||owner.closing)return;
  // Outside notifications hide the touch coordinates for other UIDs. A new app
  // window after that contact confirms an app tap without guessing at slider taps.
  long since=event.getEventTime()-owner.outsideAt;
  if(event.getEventType()==AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED&&owner.outsideAt>=0&&since>=0&&since<350&&owner.systemWindow.tracked()){
   for(AccessibilityWindowInfo w:service.getWindows())if(w.getId()==event.getWindowId()&&w.getType()==AccessibilityWindowInfo.TYPE_APPLICATION){
    owner.outsideAt=-1;owner.appExitUntil=SystemClock.uptimeMillis()+250;
    owner.startRetreat(owner.poseAlpha);break;
   }
  }
  owner.queueWindowCheck();
 }
 private void queueWindowCheck(){
  if(!mounted||closing)return;
  handler.removeCallbacks(refresh);handler.post(refresh);
 }
 private boolean checkSystemWindow(){
  if(!mounted||closing||!(context instanceof AccessibilityService service)||view.getDisplay()==null)return false;
  Rect box=systemWindow.find(service,view.getDisplay().getDisplayId());
  if(box==null){
   // An expanded panel can replace its compact window in the next traversal.
   if(systemWindow.tracked()&&!handler.hasCallbacks(confirmHidden))
    handler.postDelayed(confirmHidden,Math.max(32,adjustmentUntil-SystemClock.uptimeMillis()));
   return false;
  }
  handler.removeCallbacks(fallbackTimeout);handler.removeCallbacks(confirmHidden);handler.removeCallbacks(outsideFallback);
  followRetreat(box);
  // Window changes normally drive this path; some ROMs coalesce the final event.
  long grace=appExitUntil-SystemClock.uptimeMillis();
  handler.removeCallbacks(refresh);handler.postDelayed(refresh,followingRetreat&&grace>0?Math.min(grace,350):350);
  return true;
 }
 private boolean outsideTouch(MotionEvent event){
  if(event.getActionMasked()!=MotionEvent.ACTION_OUTSIDE)return false;
  if(!closing){
   outsideAt=event.getEventTime();
   adjustmentUntil=0;
   if(systemWindow.tracked())queueWindowCheck();
   else{handler.removeCallbacks(outsideFallback);handler.postDelayed(outsideFallback,120);}
  }
  return true;
 }
 private void followRetreat(Rect box){
  // ColorOS publishes the visible part of the compact bar as it slides off the edge.
  // Use that movement only for a side bar; expanded dialogs still own our lifetime.
  int side=box.centerX()<displayWidth/2?-1:1;
  int gap=side<0?box.left:displayWidth-box.right,depth=side<0?box.right:displayWidth-box.left;
  boolean compact=box.height()>box.width()&&depth>0&&depth<displayWidth/2;
  if(compact&&gap>PanelStyle.dp(context,2)){referenceSide=side;referenceDepth=depth;}
  boolean leaving=compact&&referenceSide==side&&referenceDepth>0&&gap<=PanelStyle.dp(context,2)&&depth<referenceDepth*.9f;
  if(SystemClock.uptimeMillis()<adjustmentUntil||(enterAnimation!=null&&enterAnimation.isRunning())||leaving==followingRetreat)return;
  if(!leaving){
   if(SystemClock.uptimeMillis()<appExitUntil)return;
   followingRetreat=false;exitAnimation.cancel();edgePose(1);return;
  }
  // SystemUI reports clipped bounds in batches. Continue the remaining fade between
  // samples, keeping the window available if another adjustment reverses the exit.
  float fraction=Math.min(1,(float)depth/referenceDepth);
  startRetreat(fraction*fraction);
 }
 private void startRetreat(float start){
  if(followingRetreat)return;
  followingRetreat=true;
  NativeMaterial.cancelEntrance(dialog!=null?dialog.getWindow().getDecorView():view);
  if(enterAnimation!=null&&enterAnimation.isRunning()){enterAnimation.removeAllListeners();enterAnimation.cancel();}
  edgePose(start);
  exitAnimation=ValueAnimator.ofFloat(start,0);
  exitAnimation.setDuration(Math.max(1,Math.round(200*Math.sqrt(start))));
  exitAnimation.setInterpolator(ColorMotion.VOLUME_OUT);
  exitAnimation.addUpdateListener(a->edgePose((Float)a.getAnimatedValue()));
  exitAnimation.start();
 }
 private void edgePose(float f){pose(Math.max(0,Math.min(1,f)),.5f+.5f*f,.5f+.5f*f,edgeDirection*PanelStyle.dp(context,14)*(1-f));}
 private void pose(float alpha,float sx,float sy,float offset){
  if(removed)return;poseAlpha=alpha;poseX=sx;poseY=sy;poseOffset=offset;
  try{
   View target=view;
   Window w=dialog==null?null:dialog.getWindow();WindowManager.LayoutParams p=w==null?params:w.getAttributes();
   p.alpha=alpha;
   if(closing||followingRetreat)p.flags|=WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE;
   else p.flags&=~WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE;
   if(w!=null){w.setAttributes(p);target=w.getDecorView();}
   else manager.updateViewLayout(view,p);
   target.setPivotX(edgeDirection<0?0:target.getWidth());target.setPivotY(target.getHeight()/2f);target.setScaleX(sx);target.setScaleY(sy);target.setTranslationX(offset);
  }catch(Exception ignored){remove();}
 }
 private void enter(){
  if(closing||removed||followingRetreat)return;
  pose(0,.5f,.5f,edgeDirection*PanelStyle.dp(context,14));
  NativeMaterial.whenReady(dialog!=null?dialog.getWindow().getDecorView():view,this::startEntrance);
 }
 private void startEntrance(){
  if(closing||removed||followingRetreat)return;
  enterAnimation=ValueAnimator.ofFloat(0,1);enterAnimation.setDuration(ColorMotion.VOLUME_IN_MS);enterAnimation.setInterpolator(new android.view.animation.LinearInterpolator());
  enterAnimation.addUpdateListener(a->{if(closing||removed)return;float t=(Float)a.getAnimatedValue();edgePose(ColorMotion.VOLUME_IN.getInterpolation(t));});
  enterAnimation.addListener(new AnimatorListenerAdapter(){@Override public void onAnimationEnd(Animator a){if(!closing&&!removed)pose(1,1,1,0);}});enterAnimation.start();
 }
 private void dismiss(){
  if(closing||removed)return;
  closing=true;handler.removeCallbacks(fallbackTimeout);up.stop();down.stop();
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
