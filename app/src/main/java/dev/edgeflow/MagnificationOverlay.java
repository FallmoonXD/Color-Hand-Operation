package dev.edgeflow;

import android.accessibilityservice.AccessibilityService;
import android.accessibilityservice.MagnificationConfig;
import android.app.Dialog;
import android.graphics.Color;
import android.graphics.Point;
import android.view.*;
import android.widget.*;
import replica.ColorMotion;
import replica.PanelGeometry;
import replica.PanelStyle;

/** A small relative control pad for live full-display magnification, as in OHO. */
public final class MagnificationOverlay {
 private static MagnificationOverlay current;
 private final AccessibilityService service;
 private final AccessibilityService.MagnificationController controller;
 private MagnificationConfig previous;
 private final android.os.Handler main=new android.os.Handler(android.os.Looper.getMainLooper());
 private long lastRequest;
 private final Runnable checkState=()->syncExternalState();
 private final AccessibilityService.MagnificationController.OnMagnificationChangedListener listener=new AccessibilityService.MagnificationController.OnMagnificationChangedListener(){
  @Override public void onMagnificationChanged(AccessibilityService.MagnificationController c,android.graphics.Region r,float scale,float x,float y){scheduleStateCheck();}
  @Override public void onMagnificationChanged(AccessibilityService.MagnificationController c,android.graphics.Region r,MagnificationConfig config){scheduleStateCheck();}
 };
 private final Dialog dialog;
 private final Point screen;
 private ColorMotion motion;
 private TextView factor;
 private boolean removed,closing,zoomOwned,following;
 private float scale=2,centerX,centerY,lastX,lastY;
 public static void show(AccessibilityService c,float[] anchor){dismissAll();MagnificationOverlay next=new MagnificationOverlay(c,anchor);if(!next.removed)current=next;}
 public static void dismissAll(){if(current!=null)current.remove();}
 static void displayChanged(GestureHandleConfig.Area area){
  MagnificationOverlay p=current;if(p==null||p.closing)return;
  Window w=p.dialog.getWindow();WindowManager.LayoutParams lp=w.getAttributes();
  boolean left=lp.x+lp.width*.5f<p.screen.x*.5f;
  float fraction=(lp.y+lp.height*.5f)/p.screen.y;
  p.screen.set(area.displayWidth,area.displayHeight);
  p.place(lp,area,left,fraction*p.screen.y);w.setAttributes(lp);
 }
 private void place(WindowManager.LayoutParams lp,GestureHandleConfig.Area area,boolean left,float y){
  int margin=Math.min(dp(16),area.width()/4);
  lp.width=Math.min(area.width()-2*margin,dp(246));lp.height=Math.min(area.height(),dp(178));
  lp.x=left?area.left+margin:area.right-lp.width-margin;
  lp.y=Math.max(area.top,Math.min(area.bottom-lp.height,Math.round(y-lp.height*.5f)));
 }
 public static void onGestureMotion(int action,float x,float y){MagnificationOverlay p=current;if(p==null||p.closing)return;if(action==MotionEvent.ACTION_MOVE){if(p.following)p.pan(x-p.lastX,y-p.lastY);p.following=true;p.lastX=x;p.lastY=y;}else if(action==MotionEvent.ACTION_UP||action==MotionEvent.ACTION_CANCEL)p.following=false;}
 private int dp(float n){return PanelStyle.dp(service,n);}
 private MagnificationOverlay(AccessibilityService c,float[] anchor){
  service=c;screen=PanelGeometry.screen(c);controller=c.getMagnificationController();previous=controller.getMagnificationConfig();centerX=anchor[2];centerY=anchor[3];
  int theme=c.getResources().getIdentifier(PanelStyle.dark(c)?"ReplicaGlassDark":"ReplicaGlass","style",c.getPackageName());
  dialog=new Dialog(c,theme);
  dialog.setOnDismissListener(d->remove());
  LinearLayout root=new LinearLayout(c);root.setOrientation(1);root.setPadding(dp(10),dp(8),dp(10),dp(10));
  LinearLayout top=new LinearLayout(c);top.setGravity(Gravity.CENTER_VERTICAL);top.addView(text("屏幕放大镜",15),new LinearLayout.LayoutParams(0,dp(34),1));top.addView(button("关闭",this::dismiss),new LinearLayout.LayoutParams(dp(44),dp(34)));root.addView(top);
  View pad=new View(c){float x,y;@Override public boolean onTouchEvent(MotionEvent e){if(e.getActionMasked()==MotionEvent.ACTION_DOWN){x=e.getRawX();y=e.getRawY();return true;}if(e.getActionMasked()==MotionEvent.ACTION_MOVE){pan(e.getRawX()-x,e.getRawY()-y);x=e.getRawX();y=e.getRawY();return true;}return true;}};
  pad.setContentDescription("放大镜控制区：滑动移动放大视野");PanelStyle.inputSurface(pad,18);
  FrameLayout area=new FrameLayout(c);area.addView(pad,new FrameLayout.LayoutParams(-1,-1));TextView hint=text("滑动移动视野",12);hint.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);area.addView(hint,new FrameLayout.LayoutParams(-1,-1));root.addView(area,new LinearLayout.LayoutParams(-1,dp(86)));
  LinearLayout bottom=new LinearLayout(c);bottom.setGravity(Gravity.CENTER_VERTICAL);bottom.addView(iconButton("ic_remove_24dp","减小放大倍数",()->zoom(-.5f)),new LinearLayout.LayoutParams(0,dp(40),1));factor=text("2.0×",14);factor.setContentDescription("放大倍数");bottom.addView(factor,new LinearLayout.LayoutParams(0,dp(40),1));bottom.addView(iconButton("ic_add_24dp","增大放大倍数",()->zoom(.5f)),new LinearLayout.LayoutParams(0,dp(40),1));root.addView(bottom);
  dialog.setContentView(root);dialog.setCanceledOnTouchOutside(false);Window w=dialog.getWindow();w.setType(WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY);w.addFlags(WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE|WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL);PanelStyle.window(c,w,24);
  WindowManager.LayoutParams p=w.getAttributes();p.gravity=Gravity.TOP|Gravity.LEFT;place(p,HandleDisplayArea.current(c),anchor[0]<screen.x/2f,anchor[1]);p.setTitle(service.getString(dev.edgeflow.R.string.app_name)+" 屏幕放大镜");PanelGeometry.screenCoordinates(p);
  // Firmware exposes these flags; resolve by name rather than binding a bit value.
  // Only the control pad is excluded. The app being inspected remains magnified.
  try{org.lsposed.hiddenapibypass.HiddenApiBypass.addHiddenApiExemptions("Landroid/view/WindowManager$LayoutParams;");java.lang.reflect.Field flags=WindowManager.LayoutParams.class.getField("privateFlags");int exclude=WindowManager.LayoutParams.class.getField("PRIVATE_FLAG_EXCLUDE_FROM_SCREEN_MAGNIFICATION").getInt(null),unscaled=WindowManager.LayoutParams.class.getField("PRIVATE_FLAG_NOT_MAGNIFIABLE").getInt(null);flags.setInt(p,flags.getInt(p)|exclude|unscaled);}catch(Exception e){android.util.Log.w("EdgeMagnifier","Cannot exclude control pad from zoom",e);}
  w.setAttributes(p);
  try{if(!apply(true)){Toast.makeText(c,"系统未允许屏幕放大",Toast.LENGTH_SHORT).show();remove();return;}zoomOwned=true;controller.addListener(listener,main);dialog.show();w.setLayout(p.width,p.height);motion=new ColorMotion(w);motion.enter();}catch(RuntimeException e){android.util.Log.w("EdgeMagnifier","Magnification unavailable",e);remove();}
 }
 private TextView text(String value,int sp){TextView t=new TextView(service);t.setText(value);t.setTextSize(sp);t.setTextColor(Color.WHITE);t.setIncludeFontPadding(false);Runnable font=()->t.setTypeface(replica.ColorOsTheme.typeface(t.getContext(),t.getTextSize()/t.getResources().getDisplayMetrics().scaledDensity>=18?600:500));font.run();replica.ColorOsTheme.watch(t,font);t.setGravity(Gravity.CENTER);return t;}
 private TextView button(String value,Runnable action){TextView t=text(value,15);t.setContentDescription(value);t.setBackground(PanelStyle.button(service,false));t.setOnClickListener(v->action.run());return t;}
 private ImageButton iconButton(String resource,String description,Runnable action){ImageButton b=new ImageButton(service);b.setImageDrawable(replica.SystemIcons.raw(service,"com.android.settings:"+resource));b.setImageTintList(android.content.res.ColorStateList.valueOf(Color.WHITE));b.setScaleType(ImageView.ScaleType.CENTER_INSIDE);b.setPadding(dp(8),dp(8),dp(8),dp(8));b.setContentDescription(description);b.setBackground(PanelStyle.button(service,false));b.setOnClickListener(v->action.run());return b;}
 private void zoom(float delta){if(removed||closing||!ensureActive())return;float old=scale;scale=Math.max(1.5f,Math.min(5,scale+delta));if(!apply(true)){scale=old;return;}factor.setText(String.format(java.util.Locale.ROOT,"%.1f×",scale));}
 private void pan(float dx,float dy){if(removed||closing||!ensureActive())return;centerX-=dx/scale;centerY-=dy/scale;apply(false);}
 private boolean ensureActive(){MagnificationConfig value=controller.getMagnificationConfig();if(!MagnificationCompat.active(value)){zoomOwned=false;remove();return false;}return true;}
 private void scheduleStateCheck(){if(removed||closing)return;main.removeCallbacks(checkState);main.postDelayed(checkState,Math.max(80,lastRequest+350-android.os.SystemClock.uptimeMillis()));}
 private void syncExternalState(){if(removed||closing)return;MagnificationConfig value=controller.getMagnificationConfig();if(!MagnificationCompat.active(value)){zoomOwned=false;remove();return;}if(value.getMode()!=MagnificationConfig.MAGNIFICATION_MODE_FULLSCREEN){zoomOwned=false;remove();return;}if(Math.abs(value.getScale()-scale)>.02f||Math.abs(value.getCenterX()-centerX)>3||Math.abs(value.getCenterY()-centerY)>3){previous=value;scale=value.getScale();centerX=value.getCenterX();centerY=value.getCenterY();factor.setText(String.format(java.util.Locale.ROOT,"%.1f×",scale));}}
 private boolean apply(boolean animate){float halfW=screen.x/(2*scale),halfH=screen.y/(2*scale);centerX=Math.max(halfW,Math.min(screen.x-halfW,centerX));centerY=Math.max(halfH,Math.min(screen.y-halfH,centerY));lastRequest=android.os.SystemClock.uptimeMillis();return controller.setMagnificationConfig(MagnificationCompat.fullscreen(scale,centerX,centerY),animate);}
 private void dismiss(){if(removed||closing)return;closing=true;if(motion!=null)motion.exit(this::remove);else remove();}
 private void remove(){if(removed)return;removed=true;if(current==this)current=null;main.removeCallbacksAndMessages(null);controller.removeListener(listener);if(motion!=null)motion.cancel();try{dialog.dismiss();}catch(RuntimeException ignored){}if(zoomOwned)try{if(MagnificationCompat.active(previous))controller.setMagnificationConfig(previous,true);else controller.reset(true);}catch(RuntimeException e){android.util.Log.w("EdgeMagnifier","Service disconnected before reset",e);}zoomOwned=false;}
}
