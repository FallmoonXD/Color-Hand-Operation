package replica;
import android.app.Activity;import android.content.Context;import android.util.DisplayMetrics;import android.view.*;
/** OHO control panel uses a 1.7-inch physical width, with a quarter-inch edge inset. */
public final class PanelGeometry {
 public static android.graphics.Point screen(Context c){android.graphics.Point p=new android.graphics.Point();((WindowManager)c.getSystemService(Context.WINDOW_SERVICE)).getDefaultDisplay().getRealSize(p);return p;}
 /** All positions below are physical screen coordinates, regardless of window type. */
 public static void screenCoordinates(WindowManager.LayoutParams p){p.setFitInsetsTypes(0);p.layoutInDisplayCutoutMode=WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_ALWAYS;}
 /** Reserve compositor pixels outside the content frame for the floating window's spot shadow. */
 static void reserveShadow(Window window){
  float elevation=window.getDecorView().getElevation();
  int padding=(int)Math.ceil(4*Math.max(elevation,PanelStyle.dp(window.getContext(),16)));
  reserveShadow(window.getAttributes(),padding);
 }
 static void reserveShadow(WindowManager.LayoutParams params,int padding){
  try{
   ShadowInsets.set(params,padding);
  }catch(ReflectiveOperationException|RuntimeException|LinkageError unavailable){
   android.util.Log.d("ReplicaPanel","Using platform shadow insets",unavailable);
  }
 }
 private static final class ShadowInsets {
  private static final java.lang.reflect.Field insets,manual;
  static {
   java.lang.reflect.Field rect=null,flag=null;
   try{
    org.lsposed.hiddenapibypass.HiddenApiBypass.addHiddenApiExemptions("Landroid/view/WindowManager$LayoutParams;");
    rect=WindowManager.LayoutParams.class.getField("surfaceInsets");
    flag=WindowManager.LayoutParams.class.getField("hasManualSurfaceInsets");
   }catch(ReflectiveOperationException|RuntimeException|LinkageError unavailable){
    android.util.Log.d("ReplicaPanel","Manual shadow insets unavailable",unavailable);
   }
   insets=rect;manual=flag;
  }
  static void set(WindowManager.LayoutParams params,int padding)throws IllegalAccessException{
   if(insets==null||manual==null)return;
   android.graphics.Rect space=(android.graphics.Rect)insets.get(params);
   if(space==null)return;
   manual.setBoolean(params,true);
   space.set(Math.max(space.left,padding),Math.max(space.top,padding),Math.max(space.right,padding),Math.max(space.bottom,padding));
  }
 }
 public static float unit(Context c){DisplayMetrics m=c.getResources().getDisplayMetrics();float dpi=m.xdpi;if(dpi<160||dpi>800)dpi=m.densityDpi;return Math.min(m.widthPixels*.67f,1.7f*dpi)/260f;}
 public static int px(Context c,float n){return Math.round(n*unit(c));}
 public static void position(Activity a,int width,int height,boolean tools,float[] gesture){
  position(a,a.getWindow(),width,height,tools,gesture);
 }
 public static void position(Context a,Window w,int width,int height,boolean tools,float[] gesture){
  DisplayMetrics m=a.getResources().getDisplayMetrics();android.graphics.Point screen=screen(a);int status=inset(a,"status_bar_height"),nav=inset(a,"navigation_bar_height");
  int x=tools?(int)(m.xdpi*.25f):(screen.x-width)/2;
  if(tools&&gesture[0]>screen.x/2f)x=screen.x-width-x;
  int top=tools?Math.round(Math.min(gesture[1],gesture[3])-status-PanelStyle.dp(a,88.5f)+(PanelStyle.dp(a,360)-height)/2f):Math.round(gesture[1]-height/2f);
  // OHO SGPWidgetPopupWindow: clamp(startY-height/2, 2*status, screenH-height-3*status).
  top=tools?Math.max(status,Math.min(screen.y-nav-height,top)):
      Math.max(status,Math.max(0,Math.min(Math.max(top,status*2),screen.y-height-status*3)));
  w.setLayout(width,height);w.setGravity(Gravity.TOP|Gravity.LEFT);
  WindowManager.LayoutParams p=w.getAttributes();screenCoordinates(p);p.x=Math.max(0,x);p.y=top;w.setAttributes(p);
  android.util.Log.d("ReplicaAnchor",(tools?"tools":"widgets")+" screen="+screen+" origin="+p.x+","+p.y+" size="+width+"x"+height+" gesture="+java.util.Arrays.toString(gesture));
 }
 public static int inset(Context c,String name){int id=c.getResources().getIdentifier(name,"dimen","android");return id==0?0:c.getResources().getDimensionPixelSize(id);}
}
