package dev.edgeflow;

import android.accessibilityservice.AccessibilityService;
import android.accessibilityservice.GestureDescription;
import android.app.Dialog;
import android.graphics.*;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import android.view.*;
import android.widget.*;
import replica.ColorMotion;
import replica.PanelGeometry;
import replica.PanelStyle;

/** Relative pointer control; accessibility gestures are injected only after pad UP. */
public final class TouchpadOverlay {
 private static TouchpadOverlay current;
 private final AccessibilityService service;
 private final WindowManager wm;
 private final Handler main=new Handler(Looper.getMainLooper());
 private final Point screen;
 private final Dialog dialog;
 private final Cursor cursor;
 private final WindowManager.LayoutParams cursorParams;
 private ColorMotion motion;
 private TextView hint,scrollButton;
 private boolean removed,closing,injecting,scrollMode,cursorAdded;
 private float pointerX,pointerY,pointerSpeed=1.7f;
 private final Runnable restoreInput=()->restorePanelInput();
 private void restorePanelInput(){if(removed)return;injecting=false;if(closing)return;Window w=dialog.getWindow();WindowManager.LayoutParams p=w.getAttributes();p.flags&=~WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE;w.setAttributes(p);}

 public static void show(AccessibilityService c,float[] anchor){dismissAll();TouchpadOverlay next=new TouchpadOverlay(c,anchor);if(!next.removed)current=next;}
 public static void dismissAll(){if(current!=null)current.remove();}
 private int dp(float n){return PanelStyle.dp(service,n);}
 private TouchpadOverlay(AccessibilityService c,float[] anchor){
  service=c;wm=(WindowManager)c.getSystemService(android.content.Context.WINDOW_SERVICE);screen=PanelGeometry.screen(c);pointerX=screen.x*.5f;pointerY=screen.y*.35f;
  cursor=new Cursor(c);cursorParams=new WindowManager.LayoutParams(dp(30),dp(30),WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE|WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE|WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,PixelFormat.TRANSLUCENT);cursorParams.gravity=Gravity.TOP|Gravity.LEFT;cursorParams.setTitle(service.getString(dev.edgeflow.R.string.app_name)+" 触控板光标");PanelGeometry.screenCoordinates(cursorParams);
  int theme=c.getResources().getIdentifier(PanelStyle.dark(c)?"ReplicaGlassDark":"ReplicaGlass","style",c.getPackageName());
  dialog=new Dialog(c,theme){@Override public boolean onTouchEvent(MotionEvent e){if(e.getActionMasked()==MotionEvent.ACTION_OUTSIDE){if(!injecting)TouchpadOverlay.this.dismiss();return true;}return super.onTouchEvent(e);}};
  dialog.setOnDismissListener(d->remove());
  LinearLayout root=new LinearLayout(c);root.setOrientation(1);root.setPadding(dp(10),dp(8),dp(10),dp(10));
  LinearLayout title=row();TextView name=text("虚拟触控板",16);title.addView(name,new LinearLayout.LayoutParams(0,dp(36),1));title.addView(button("关闭",this::dismiss),new LinearLayout.LayoutParams(dp(48),dp(36)));root.addView(title);
  hint=text("",11);refreshPointerSpeed();root.addView(hint,new LinearLayout.LayoutParams(-1,dp(30)));
  Pad pad=new Pad(c);pad.setContentDescription("触控板：滑动移动光标，轻点点击，双指滑动滚动");pad.setBackground(PanelStyle.shape(c,0x242e3540,20));root.addView(pad,new LinearLayout.LayoutParams(-1,dp(142)));
  LinearLayout keys=row();root.addView(keys,new LinearLayout.LayoutParams(-1,dp(42)));keys.addView(button("单击",()->click(1,false)),new LinearLayout.LayoutParams(0,-1,1));keys.addView(button("双击",()->click(2,false)),new LinearLayout.LayoutParams(0,-1,1));keys.addView(button("长按",()->click(1,true)),new LinearLayout.LayoutParams(0,-1,1));
  LinearLayout options=row();root.addView(options,new LinearLayout.LayoutParams(-1,dp(40)));scrollButton=button("滚动：关",()->{scrollMode=!scrollMode;scrollButton.setText(scrollMode?"滚动：开":"滚动：关");scrollButton.setBackground(PanelStyle.toolButton(c,scrollMode));});options.addView(scrollButton,new LinearLayout.LayoutParams(0,-1,1));options.addView(button("光标居中",()->{pointerX=screen.x/2f;pointerY=screen.y*.35f;positionCursor();}),new LinearLayout.LayoutParams(0,-1,1));options.addView(button("返回",()->service.performGlobalAction(AccessibilityService.GLOBAL_ACTION_BACK)),new LinearLayout.LayoutParams(0,-1,1));
  dialog.setContentView(root);dialog.setCanceledOnTouchOutside(false);Window w=dialog.getWindow();w.setType(WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY);w.addFlags(WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE|WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL|WindowManager.LayoutParams.FLAG_WATCH_OUTSIDE_TOUCH);w.setWindowAnimations(0);PanelStyle.window(c,w,24);
  int width=Math.min(screen.x-dp(32),dp(284)),height=dp(318);WindowManager.LayoutParams p=w.getAttributes();p.gravity=Gravity.TOP|Gravity.LEFT;p.x=anchor[0]<screen.x/2f?dp(16):screen.x-width-dp(16);p.y=Math.max(PanelGeometry.inset(c,"status_bar_height"),Math.min(screen.y-height-PanelGeometry.inset(c,"navigation_bar_height")-dp(12),Math.round(Math.max(screen.y*.55f,anchor[3]))));p.width=width;p.height=height;p.setTitle(service.getString(dev.edgeflow.R.string.app_name)+" 虚拟触控板");PanelGeometry.screenCoordinates(p);w.setAttributes(p);
  try{dialog.show();w.setLayout(width,height);positionCursor();wm.addView(cursor,cursorParams);cursorAdded=true;motion=new ColorMotion(w);motion.enter();}catch(Exception e){android.util.Log.e("EdgeTouchpad","Cannot attach",e);remove();}
 }
 private LinearLayout row(){LinearLayout v=new LinearLayout(service);v.setGravity(Gravity.CENTER_VERTICAL);return v;}
 private TextView text(String value,int sp){TextView t=new TextView(service);t.setText(value);t.setTextSize(sp);t.setTextColor(Color.WHITE);t.setIncludeFontPadding(false);Runnable font=()->t.setTypeface(replica.ColorOsTheme.typeface(t.getContext(),t.getTextSize()/t.getResources().getDisplayMetrics().scaledDensity>=18?600:500));font.run();replica.ColorOsTheme.watch(t,font);t.setGravity(Gravity.CENTER);return t;}
 private TextView button(String value,Runnable action){TextView t=text(value,12);t.setContentDescription(value);t.setBackground(PanelStyle.button(service,false));t.setOnClickListener(v->{if(!injecting)action.run();});return t;}
 private void refreshPointerSpeed(){Object raw=service.getSharedPreferences("edgeflow",0).getAll().get("touchpad_speed");float speed=raw instanceof Number?((Number)raw).floatValue():1.7f;pointerSpeed=Float.isFinite(speed)?Math.max(.5f,Math.min(3f,speed)):1.7f;hint.setText(String.format(java.util.Locale.getDefault(),"光标 %.1f× · 轻点单击 · 双指滚动",pointerSpeed));}
 private void positionCursor(){
  // Stay clear of the narrow gesture handles while retaining the complete app area.
  float inset=dp(16);pointerX=Math.max(inset,Math.min(screen.x-inset,pointerX));pointerY=Math.max(dp(8),Math.min(screen.y-dp(8),pointerY));cursorParams.x=Math.round(pointerX)-dp(15);cursorParams.y=Math.round(pointerY)-dp(15);
  if(cursorAdded)try{wm.updateViewLayout(cursor,cursorParams);}catch(Exception ignored){}cursor.invalidate();
 }
 private void click(int count,boolean hold){Path p=new Path();p.moveTo(pointerX,pointerY);inject(p,hold?650:60,count);}
 private void scroll(float amount){if(Math.abs(amount)<dp(8))return;float distance=Math.max(-screen.y*.4f,Math.min(screen.y*.4f,amount*2));float start=Math.max(dp(48),Math.min(screen.y-dp(48),pointerY-distance/2)),end=Math.max(dp(48),Math.min(screen.y-dp(48),start+distance));Path p=new Path();p.moveTo(pointerX,start);p.lineTo(pointerX,end);inject(p,260,1);}
 private void inject(Path path,long duration,int count){
  if(removed||closing||injecting)return;injecting=true;Window w=dialog.getWindow();WindowManager.LayoutParams p=w.getAttributes();p.flags|=WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE;w.setAttributes(p);
  // Wait for InputDispatcher to apply the pass-through region before injecting.
  main.postDelayed(()->{if(removed||closing)return;
   try{GestureDescription.Builder b=new GestureDescription.Builder();for(int i=0;i<count;i++)b.addStroke(new GestureDescription.StrokeDescription(path,i*(duration+65),duration));
    boolean accepted=service.dispatchGesture(b.build(),new AccessibilityService.GestureResultCallback(){@Override public void onCompleted(GestureDescription g){if(removed)return;main.removeCallbacks(restoreInput);main.postDelayed(restoreInput,32);}@Override public void onCancelled(GestureDescription g){if(removed)return;main.removeCallbacks(restoreInput);main.post(restoreInput);}},main);
    if(!accepted)injectionFailed();else main.postDelayed(restoreInput,count*(duration+65)+800);
   }catch(RuntimeException e){android.util.Log.w("EdgeTouchpad","Gesture connection changed during injection",e);injectionFailed();}
  },60);
 }
 private void injectionFailed(){restoreInput.run();if(!removed&&!closing)Toast.makeText(service,"触控注入不可用，请检查无障碍服务",Toast.LENGTH_SHORT).show();}
 private void dismiss(){if(removed||closing)return;closing=true;if(motion!=null)motion.exit(this::remove);else remove();}
 private void remove(){if(removed)return;removed=true;main.removeCallbacksAndMessages(null);if(motion!=null)motion.cancel();try{dialog.dismiss();}catch(Exception ignored){}if(cursorAdded){try{wm.removeViewImmediate(cursor);}catch(Exception ignored){}cursorAdded=false;}if(current==this)current=null;}
 private final class Pad extends View {
  private float lastX,lastY,downX,downY,scrollAmount;private long downTime;private boolean multi,moved;
  Pad(android.content.Context c){super(c);setClickable(true);}
  private float x(MotionEvent e){float s=0;for(int i=0;i<e.getPointerCount();i++)s+=e.getX(i);return s/e.getPointerCount();}
  private float y(MotionEvent e){float s=0;for(int i=0;i<e.getPointerCount();i++)s+=e.getY(i);return s/e.getPointerCount();}
  @Override public boolean onTouchEvent(MotionEvent e){if(injecting)return true;int a=e.getActionMasked();
   if(a==MotionEvent.ACTION_DOWN){refreshPointerSpeed();lastX=downX=x(e);lastY=downY=y(e);downTime=SystemClock.uptimeMillis();scrollAmount=0;multi=moved=false;return true;}
   if(a==MotionEvent.ACTION_POINTER_DOWN){multi=true;lastX=x(e);lastY=y(e);return true;}
   if(a==MotionEvent.ACTION_POINTER_UP){float sx=0,sy=0;int remaining=0;for(int i=0;i<e.getPointerCount();i++)if(i!=e.getActionIndex()){sx+=e.getX(i);sy+=e.getY(i);remaining++;}if(remaining>0){lastX=sx/remaining;lastY=sy/remaining;}return true;}
   if(a==MotionEvent.ACTION_MOVE){float nx=x(e),ny=y(e),dx=nx-lastX,dy=ny-lastY;lastX=nx;lastY=ny;if(Math.hypot(nx-downX,ny-downY)>dp(6))moved=true;if(scrollMode||multi)scrollAmount+=dy;else{pointerX+=dx*pointerSpeed;pointerY+=dy*pointerSpeed;positionCursor();}return true;}
   if(a==MotionEvent.ACTION_UP){if(scrollMode||multi)scroll(scrollAmount);else if(!moved)click(1,SystemClock.uptimeMillis()-downTime>=500);performClick();return true;}
   return true;
  }
  @Override public boolean performClick(){return super.performClick();}
 }
 private final class Cursor extends View {
  final Paint p=new Paint(Paint.ANTI_ALIAS_FLAG);Cursor(android.content.Context c){super(c);setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_NO);}
  @Override protected void onDraw(Canvas c){float x=getWidth()/2f,y=getHeight()/2f;p.setStyle(Paint.Style.FILL);p.setColor(0x77188dff);c.drawCircle(x,y,dp(10),p);p.setStyle(Paint.Style.STROKE);p.setStrokeWidth(dp(1.5f));p.setColor(Color.WHITE);c.drawCircle(x,y,dp(9),p);p.setStyle(Paint.Style.FILL);c.drawCircle(x,y,dp(2),p);}
 }
}
