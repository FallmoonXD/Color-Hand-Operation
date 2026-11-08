package replica;

import android.animation.*;
import android.accessibilityservice.AccessibilityService;
import android.app.Dialog;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.graphics.Rect;
import android.media.AudioManager;
import android.os.Handler;
import android.os.SystemClock;
import android.view.*;
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
 private final int displayWidth,displayHeight;
 private Dialog dialog;
 private ValueAnimator motion;
 private float progress;
 private float edgeDirection;
 private boolean mounted,closing,removed;
 private int referenceDepth,referenceSide;
 private long adjustmentUntil;
 private final Rect lastCompactBounds=new Rect();
 private final long openedAt=SystemClock.uptimeMillis();
 private long entranceEndsAt;
 private boolean triedAudio,observingAudio;
 private final Runnable idleTimeout=this::onCompactIdle;
 private final BroadcastReceiver volumeChanges=new BroadcastReceiver(){
  @Override public void onReceive(Context c,Intent intent){armCompactTimeout();}
 };
 private final Runnable fallbackTimeout=()->{if(!checkSystemWindow())dismiss();};
 private final Runnable refresh=this::checkSystemWindow;
 private final Runnable confirmHidden=()->{if(!checkSystemWindow())dismiss();};

 public static void show(Context c){
  show(c,NativeState.gesture(c));
 }
 /** Avoid retaining an accessibility context after its service is disabled. */
 public static void dismissAll(){if(current!=null)current.remove();}
 public static void close(){if(current!=null)current.dismiss();}
 public static void show(Context c,float[] anchor){
  if(current!=null){
   boolean reopen=current.closing;
   if(reopen){
    current.closing=false;
    current.cancelMotion();
   }
   current.adjusted(3000);
   if(reopen)current.enter();
   return;
  }
  VolumeOverlay next=new VolumeOverlay(c,anchor);
  current=next.mounted?next:null;
 }

 private VolumeOverlay(Context c,float[] anchor){
  context=c;manager=(WindowManager)c.getSystemService(Context.WINDOW_SERVICE);systemWindow=new SystemVolumeWindow(c);
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
  float[] g=anchor;android.graphics.Point screen=PanelGeometry.screen(c);displayWidth=screen.x;displayHeight=screen.y;
  params.gravity=Gravity.TOP|Gravity.LEFT;
  edgeDirection=g[0]<screen.x/2f?-1f:1f;params.alpha=0;
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
  adjustmentUntil=SystemClock.uptimeMillis()+200;
  handler.removeCallbacks(confirmHidden);
  armFallback(timeout);armCompactTimeout();queueWindowCheck();
 }
 private void armCompactTimeout(){
  armCompactTimeout(SystemClock.uptimeMillis());
 }
 private void armCompactTimeout(long lastActivity){
  if(closing||removed||!observingAudio)return;
  android.view.accessibility.AccessibilityManager a=context.getSystemService(android.view.accessibility.AccessibilityManager.class);
  handler.removeCallbacks(idleTimeout);
  handler.postAtTime(idleTimeout,lastActivity+
    a.getRecommendedTimeoutMillis(3000,android.view.accessibility.AccessibilityManager.FLAG_CONTENT_CONTROLS));
 }
 private void onCompactIdle(){
  // Hover can extend SystemUI's clock. In that case wait for the observed retreat.
  if(context.getSystemService(android.view.accessibility.AccessibilityManager.class).isTouchExplorationEnabled())return;
  for(int id:InputDevice.getDeviceIds()){
   InputDevice device=InputDevice.getDevice(id);
   if(device!=null&&(device.supportsSource(InputDevice.SOURCE_MOUSE)||device.supportsSource(InputDevice.SOURCE_STYLUS)
       ||device.supportsSource(InputDevice.SOURCE_TOUCHPAD)))return;
  }
  dismiss();
 }
 private void compactBoundsChanged(Rect box){
  if(!systemWindow.colorOsSidePanel)return;
  if(!triedAudio){
   triedAudio=true;
   try{context.registerReceiver(volumeChanges,new IntentFilter("android.media.VOLUME_CHANGED_ACTION"),Context.RECEIVER_NOT_EXPORTED);}
   catch(SecurityException unavailable){return;}
   observingAudio=true;
   // A freshly shown ColorOS side bar rearms the compact timeout as its entrance
   // settles. A bar already on screen restarts the clock from the volume request.
   entranceEndsAt=openedAt;
   if(systemWindow.transitionTime()>=openedAt)
    entranceEndsAt+=Math.round(ColorMotion.VOLUME_IN_MS*ValueAnimator.getDurationScale());
   armCompactTimeout(entranceEndsAt);
  }
  if(!observingAudio)return;
  // Ignore sub-dp jitter. A bounded hardware key can move the native row even
  // when the stream is already at minimum/maximum and its volume does not change.
  int tolerance=PanelStyle.dp(context,1);
  boolean changed=Math.abs(box.left-lastCompactBounds.left)>=tolerance
      ||Math.abs(box.right-lastCompactBounds.right)>=tolerance||Math.abs(box.top-lastCompactBounds.top)>=tolerance
      ||Math.abs(box.bottom-lastCompactBounds.bottom)>=tolerance;
  if(changed&&!lastCompactBounds.isEmpty()&&SystemClock.uptimeMillis()>entranceEndsAt+200)
   armCompactTimeout();
  lastCompactBounds.set(box);
 }
 public static void systemWindowsChanged(AccessibilityService service){
  if(current!=null&&current.context==service)current.queueWindowCheck();
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
  handler.removeCallbacks(fallbackTimeout);handler.removeCallbacks(confirmHidden);
  // The expanded mixer can keep the compact window's ID. Its touch region grows
  // from a narrow side bar across the display, even if expansion was not a tap.
  boolean grewAcrossScreen=referenceSide!=0&&box.left<displayWidth/4&&box.right>displayWidth*3/4;
  boolean fullMixer=systemWindow.colorOsSidePanel&&box.left<=0&&box.top<=0&&box.right>=displayWidth&&box.bottom>=displayHeight;
  if(fullMixer&&referenceSide==0&&SystemClock.uptimeMillis()<openedAt+200){
   // A compact bar can initially report its full decor before publishing touch insets.
   handler.removeCallbacks(refresh);handler.postAtTime(refresh,openedAt+200);return true;
  }
  if(grewAcrossScreen||fullMixer){
   dismiss();
   return true;
  }
  int side=box.centerX()<displayWidth/2?-1:1;
  int depth=side<0?box.right:displayWidth-box.left;
  int gap=side<0?box.left:displayWidth-box.right;
  if(box.height()>box.width()&&depth>0&&depth<displayWidth/2){
   if(gap>PanelStyle.dp(context,2)){
    referenceSide=side;referenceDepth=depth;
    compactBoundsChanged(box);
   }else if(side==referenceSide&&depth<referenceDepth*.9f&&SystemClock.uptimeMillis()>=adjustmentUntil){
    dismiss();
    return true;
   }
  }
  // Window changes normally drive this path; some ROMs coalesce the final event.
  handler.removeCallbacks(refresh);handler.postDelayed(refresh,350);
  return true;
 }
 private boolean outsideTouch(MotionEvent event){
  if(event.getActionMasked()!=MotionEvent.ACTION_OUTSIDE)return false;
  dismiss();
  return true;
 }
 private void pose(float fraction){
  if(removed)return;
  progress=fraction;
  float scale=.5f+.5f*fraction;
  float offset=edgeDirection*PanelStyle.dp(context,14)*(1-fraction);
  try{
   Window w=dialog==null?null:dialog.getWindow();WindowManager.LayoutParams p=w==null?params:w.getAttributes();
   p.alpha=Math.max(0,Math.min(1,fraction));
   if(closing)p.flags|=WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE;
   else p.flags&=~WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE;
   View target=view;
   if(w!=null){w.setAttributes(p);target=w.getDecorView();}
   else manager.updateViewLayout(view,p);
   target.setPivotX(edgeDirection<0?0:target.getWidth());target.setPivotY(target.getHeight()/2f);
   target.setScaleX(scale);target.setScaleY(scale);target.setTranslationX(offset);
  }catch(RuntimeException e){remove();}
 }
 private void enter(){
  if(closing||removed)return;
  pose(progress);
  NativeMaterial.whenReady(dialog!=null?dialog.getWindow().getDecorView():view,()->{
   if(!closing&&!removed)animate(1,ColorMotion.VOLUME_IN_MS,ColorMotion.VOLUME_IN);
  });
 }
 private void dismiss(){
  if(removed||closing)return;
  closing=true;handler.removeCallbacksAndMessages(null);up.stop();down.stop();
  NativeMaterial.cancelEntrance(dialog!=null?dialog.getWindow().getDecorView():view);
  if(progress<=0){remove();return;}
  pose(progress); // Release input immediately, including touches during the exit.
  animate(0,ColorMotion.VOLUME_OUT_MS,ColorMotion.VOLUME_OUT);
 }
 private void animate(float target,long duration,TimeInterpolator spring){
  if(removed)return;
  cancelMotion();
  motion=ValueAnimator.ofFloat(progress,target);motion.setDuration(duration);motion.setInterpolator(spring);
  motion.addUpdateListener(a->pose((Float)a.getAnimatedValue()));
  if(target==0)motion.addListener(new AnimatorListenerAdapter(){
   @Override public void onAnimationEnd(Animator a){remove();}
  });
  motion.start();
 }
 private void cancelMotion(){
  if(motion==null)return;
  motion.removeAllListeners();motion.removeAllUpdateListeners();motion.cancel();motion=null;
 }
 private void remove(){
  if(removed)return;removed=true;mounted=false;up.stop();down.stop();handler.removeCallbacksAndMessages(null);
  if(observingAudio){context.unregisterReceiver(volumeChanges);observingAudio=false;}
  NativeMaterial.cancelEntrance(dialog!=null?dialog.getWindow().getDecorView():view);
  cancelMotion();
  try{if(dialog!=null)dialog.dismiss();else manager.removeView(view);}catch(Exception ignored){}
  if(current==this)current=null;
 }
}
