package dev.edgeflow;

import android.accessibilityservice.AccessibilityService;
import android.accessibilityservice.GestureDescription;
import android.animation.ValueAnimator;
import android.content.Context;
import android.graphics.*;
import android.hardware.HardwareBuffer;
import android.os.Handler;
import android.os.Looper;
import android.view.*;
import android.view.accessibility.AccessibilityNodeInfo;
import android.widget.*;
import replica.ColorMotion;
import replica.PanelGeometry;
import replica.PanelStyle;

/** Temporary screenshot translation with a single mapped tap after the image is removed. */
public final class ScreenMoveOverlay {
 private static ScreenMoveOverlay current;
 private static int generation;
 private final int serial;
 private final AccessibilityService service;
 private final Handler main=new Handler(Looper.getMainLooper());
 private final WindowManager windows;
 private final Point screen;
 private final FrameLayout root;
 private final Picture picture;
 private final String capturedPackage;
 private final int capturedWindow;
 private Bitmap bitmap;
 private boolean removed,added,closing,following,moved;
 private float offsetX,offsetY,lastX,lastY,travel;
 private ValueAnimator exit;
 private final float[] anchor;private replica.ToolMotionPlayer toolMotion;
 private final Runnable timeout=()->dismiss(false,0,0);
 public static void show(AccessibilityService c,float[] anchor){dismissAll();ScreenMoveOverlay p=new ScreenMoveOverlay(c,anchor);current=p;p.main.postDelayed(p::capture,180);}
 public static void dismissAll(){generation++;if(current!=null)current.remove();}
 public static void onGestureMotion(int action,float x,float y){ScreenMoveOverlay p=current;if(p==null||p.removed||p.closing)return;if(action==MotionEvent.ACTION_MOVE){if(p.following&&p.bitmap!=null)p.translate(x-p.lastX,y-p.lastY);p.following=true;p.lastX=x;p.lastY=y;}else if(action==MotionEvent.ACTION_UP){if(p.bitmap!=null&&p.moved)p.finish(x,y);p.following=false;}else if(action==MotionEvent.ACTION_CANCEL){p.following=false;p.remove();}}
 private int dp(float n){return PanelStyle.dp(service,n);}
 private ScreenMoveOverlay(AccessibilityService service,float[] anchor){
  this.anchor=anchor.clone();
  this.service=service;serial=generation;windows=(WindowManager)service.getSystemService(Context.WINDOW_SERVICE);screen=PanelGeometry.screen(service);String[] owner=activeOwner();capturedPackage=owner[0];capturedWindow=Integer.parseInt(owner[1]);root=new FrameLayout(service);root.setBackgroundColor(0xff16191f);picture=new Picture(service);root.addView(picture,new FrameLayout.LayoutParams(-1,-1));
  LinearLayout controls=new LinearLayout(service);controls.setGravity(Gravity.CENTER_VERTICAL);controls.setPadding(dp(10),0,dp(10),0);controls.setBackground(new replica.ColorOsSurface(service,24,2).attach(controls));
  TextView hint=text("移动画面，松手点选",12);controls.addView(hint,new LinearLayout.LayoutParams(0,-1,1));TextView close=text("关闭",13);close.setContentDescription("关闭屏幕自由移动");close.setBackground(PanelStyle.button(service,false));close.setOnClickListener(v->dismiss(false,0,0));controls.addView(close,new LinearLayout.LayoutParams(dp(48),dp(38)));
  FrameLayout.LayoutParams p=new FrameLayout.LayoutParams(Math.min(screen.x-dp(32),dp(258)),dp(48),Gravity.BOTTOM|Gravity.CENTER_HORIZONTAL);p.bottomMargin=PanelGeometry.inset(service,"navigation_bar_height")+dp(12);root.addView(controls,p);
 }
 private TextView text(String value,int sp){TextView t=new TextView(service);t.setText(value);t.setTextSize(sp);t.setTextColor(Color.WHITE);t.setIncludeFontPadding(false);Runnable font=()->t.setTypeface(replica.ColorOsTheme.typeface(t.getContext(),t.getTextSize()/t.getResources().getDisplayMetrics().scaledDensity>=18?600:500));font.run();replica.ColorOsTheme.watch(t,font);t.setGravity(Gravity.CENTER);return t;}
 private String[] activeOwner(){for(android.view.accessibility.AccessibilityWindowInfo w:service.getWindows()){if(w.getType()!=android.view.accessibility.AccessibilityWindowInfo.TYPE_APPLICATION||!w.isFocused())continue;AccessibilityNodeInfo n=w.getRoot();try{if(n!=null&&n.getPackageName()!=null)return new String[]{n.getPackageName().toString(),Integer.toString(w.getId())};}finally{if(n!=null)n.recycle();}}return new String[]{"","-1"};}
 private boolean sameOwner(){if(capturedWindow<0||capturedPackage.isEmpty())return false;String[] owner=activeOwner();return capturedPackage.equals(owner[0])&&Integer.toString(capturedWindow).equals(owner[1]);}
 private void capture(){
  if(removed)return;if(capturedWindow<0||capturedPackage.isEmpty()){fail("当前页面无法进行屏幕自由移动");return;}
  try{service.takeScreenshot(android.view.Display.DEFAULT_DISPLAY,service.getMainExecutor(),new AccessibilityService.TakeScreenshotCallback(){
   public void onSuccess(AccessibilityService.ScreenshotResult result){
    HardwareBuffer buffer=result.getHardwareBuffer();Bitmap hardware=null;
    try{if(removed)return;hardware=Bitmap.wrapHardwareBuffer(buffer,result.getColorSpace());if(hardware==null)throw new IllegalStateException("No image");bitmap=hardware.copy(Bitmap.Config.ARGB_8888,false);if(bitmap==null)throw new IllegalStateException("No pixels");attach();}catch(RuntimeException e){fail("无法读取当前画面");}finally{if(hardware!=null)hardware.recycle();buffer.close();}
   }
   public void onFailure(int code){if(!removed)fail(code==AccessibilityService.ERROR_TAKE_SCREENSHOT_SECURE_WINDOW?"当前页面禁止屏幕捕获":"屏幕捕获暂不可用，请稍后再试");}
  });}catch(RuntimeException e){fail("屏幕捕获不可用，请检查无障碍服务");}
 }
 private void fail(String message){Toast.makeText(service,message,Toast.LENGTH_SHORT).show();remove();}
 private void attach(){
  if(removed)return;
  if(!sameOwner()){remove();return;}
  WindowManager.LayoutParams p=new WindowManager.LayoutParams(screen.x,screen.y,WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE|WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,PixelFormat.OPAQUE);p.gravity=Gravity.TOP|Gravity.LEFT;p.setTitle(service.getString(dev.edgeflow.R.string.app_name)+" 屏幕自由移动");PanelGeometry.screenCoordinates(p);
  // ColorOS rejects opaque full-display accessibility overlays. Keep this temporary
  // screenshot slightly translucent, rather than changing the system's policy.
  p.alpha=.94f;
  try{
   toolMotion=new replica.ToolMotionPlayer(root,anchor[0]<screen.x/2f,anchor[1],false,root::setAlpha);
   replica.ToolAnimation.Style style=replica.ToolAnimation.SCREEN_MOVE.active(service,true);
   if(style==replica.ToolAnimation.Style.SCREEN)toolMotion.shown();else toolMotion.prepare();
   windows.addView(root,p);added=true;picture.invalidate();main.postDelayed(timeout,30000);
   if(style!=replica.ToolAnimation.Style.SCREEN)root.post(()->{
    if(removed||closing)return;
    if(following||moved)toolMotion.shown();else toolMotion.play(style,true,null);
   });
  }catch(RuntimeException e){fail("无法显示屏幕自由移动");}
 }
 private void translate(float dx,float dy){
  if(removed||closing)return;if(toolMotion!=null)toolMotion.shown();travel+=(float)Math.hypot(dx,dy);if(travel>dp(5))moved=true;offsetX=Math.max(-screen.x/2f,Math.min(screen.x/2f,offsetX-dx*2));offsetY=Math.max(-screen.y/4f,Math.min(screen.y/2f,offsetY-dy*2));picture.invalidate();main.removeCallbacks(timeout);main.postDelayed(timeout,30000);
 }
 private void finish(float x,float y){float sourceX=x-offsetX,sourceY=y-offsetY;boolean inside=sourceX>=dp(16)&&sourceX<=screen.x-dp(16)&&sourceY>=PanelGeometry.inset(service,"status_bar_height")&&sourceY<screen.y-PanelGeometry.inset(service,"navigation_bar_height");dismiss(inside,sourceX,sourceY);}
 private void dismiss(boolean click,float x,float y){
  if(removed||closing)return;closing=true;main.removeCallbacks(timeout);
  if(!added){remove();return;}
  root.setEnabled(false);
  replica.ToolAnimation.Style style=replica.ToolAnimation.SCREEN_MOVE.active(service,false);
  if(style!=replica.ToolAnimation.Style.SCREEN){toolMotion.play(style,false,()->finishReturn(click,x,y));return;}
  toolMotion.shown();float startX=offsetX,startY=offsetY;
  if((startX==0&&startY==0)||!ValueAnimator.areAnimatorsEnabled()){finishReturn(click,x,y);return;}
  exit=ValueAnimator.ofFloat(1,0);exit.setDuration(ColorMotion.oneHandedDuration(service));exit.setInterpolator(ColorMotion::oneHandedReturn);
  exit.addUpdateListener(a->{float v=(Float)a.getAnimatedValue();offsetX=startX*v;offsetY=startY*v;picture.invalidate();});
  exit.addListener(new android.animation.AnimatorListenerAdapter(){public void onAnimationEnd(android.animation.Animator a){finishReturn(click,x,y);}});exit.start();
 }
 private void finishReturn(boolean click,float x,float y){boolean allowed=click&&!removed;remove();if(allowed)main.postDelayed(()->inject(x,y),80);}
 private void inject(float x,float y){
  // Do not apply coordinates to another window, including a new activity in the same app.
  if(serial!=generation||GestureService.instance()!=service||!sameOwner())return;
  Path p=new Path();p.moveTo(x,y);try{if(!service.dispatchGesture(new GestureDescription.Builder().addStroke(new GestureDescription.StrokeDescription(p,0,60)).build(),null,null))Toast.makeText(service,"触控注入不可用",Toast.LENGTH_SHORT).show();}catch(RuntimeException e){android.util.Log.w("EdgeScreenMove","Input connection changed",e);}
 }
 private void remove(){if(removed)return;removed=true;if(toolMotion!=null)toolMotion.cancel();if(current==this)current=null;main.removeCallbacksAndMessages(null);if(exit!=null){exit.removeAllListeners();exit.cancel();}if(added){try{windows.removeViewImmediate(root);}catch(RuntimeException ignored){}added=false;}if(bitmap!=null){bitmap.recycle();bitmap=null;}}
 private final class Picture extends View {
  final Paint paint=new Paint(Paint.ANTI_ALIAS_FLAG|Paint.FILTER_BITMAP_FLAG);float x,y;
  Picture(Context c){super(c);setContentDescription("屏幕自由移动：拖动平移画面，抬手点击映射位置");setClickable(true);}
  @Override protected void onDraw(Canvas c){if(bitmap!=null&&!bitmap.isRecycled())c.drawBitmap(bitmap,null,new RectF(offsetX,offsetY,offsetX+screen.x,offsetY+screen.y),paint);}
  @Override public boolean onTouchEvent(MotionEvent e){if(removed||closing)return true;switch(e.getActionMasked()){case MotionEvent.ACTION_DOWN:if(toolMotion!=null)toolMotion.shown();x=e.getRawX();y=e.getRawY();moved=false;travel=0;return true;case MotionEvent.ACTION_MOVE:translate(e.getRawX()-x,e.getRawY()-y);x=e.getRawX();y=e.getRawY();return true;case MotionEvent.ACTION_UP:finish(e.getRawX(),e.getRawY());performClick();return true;case MotionEvent.ACTION_POINTER_DOWN:case MotionEvent.ACTION_CANCEL:dismiss(false,0,0);return true;default:return true;}}
  @Override public boolean performClick(){return super.performClick();}
 }
}
