package replica;
import android.content.Context;
import android.graphics.drawable.GradientDrawable;
import android.content.res.ColorStateList;
import android.graphics.drawable.RippleDrawable;
import android.graphics.drawable.Drawable;
public final class PanelStyle {
 public static int dp(Context c,float n){return Math.round(c.getResources().getDisplayMetrics().density*n);}
 public static boolean dark(Context c){return (c.getResources().getConfiguration().uiMode&48)==32;}
 public static int surface(Context c){return dark(c)?0x18283040:0x14708090;}
 public static int blurRadius(Context c){return ColorOsTheme.materialBlur(c)?dp(c,18):0;}
 public static int text(Context c){return ColorOsTheme.text(c);}
 public static int accent(Context c){return ColorOsTheme.accent(c);}
 private static final class AccentShape extends GradientDrawable {
  private final Context context;private int last;
  AccentShape(Context c,float radius){context=c;setCornerRadius(dp(c,radius));last=accent(c);setColor(last);}
  @Override public void draw(android.graphics.Canvas canvas){int color=accent(context);if(color!=last){last=color;setColor(color);}super.draw(canvas);}
 }
 public static GradientDrawable glass(Context c,int radius){GradientDrawable d=new GradientDrawable(GradientDrawable.Orientation.TL_BR,dark(c)?new int[]{0x20505a68,0x18202632}:new int[]{0x16708096,0x1258667c});d.setDither(true);d.setCornerRadius(dp(c,radius));d.setStroke(Math.max(1,dp(c,.6f)),0x30ffffff);return d;}
 public static void window(android.app.Activity a,int radius){window(a,a.getWindow(),radius);}
 public static void window(Context a,android.view.Window w,int radius){glassWindow(a,w,new ColorOsSurface(a,radius,2).attach(w));}
 private static void glassWindow(Context a,android.view.Window w,Drawable background){w.setBackgroundDrawable(background);w.getDecorView().setClipToOutline(true);try{w.getClass().getMethod("setBackgroundBlurRadius",int.class).invoke(w,blurRadius(a));}catch(Exception ignored){}w.setDimAmount(0);w.clearFlags(android.view.WindowManager.LayoutParams.FLAG_DIM_BEHIND);}
 public static GradientDrawable shape(Context c,int color,int radius){GradientDrawable d=new GradientDrawable();d.setColor(color);d.setCornerRadius(dp(c,radius));return d;}
 public static void inputSurface(android.view.View view,int radius){
  Context c=view.getContext();Runnable update=()->view.setBackground(shape(c,ColorOsTheme.dark(c)?0xff2d333b:0xff53606d,radius));
  update.run();ColorOsTheme.watch(view,update);
 }
 public static void contentCard(android.view.View view,int radius,boolean clickable){
  contentCard(view,radius,clickable,false);
 }
 public static void headerCard(android.view.View view,int radius){
  contentCard(view,radius,false,true);
 }
 private static void contentCard(android.view.View view,int radius,boolean clickable,boolean header){
  Context c=view.getContext();Runnable update=()->{
   int fill=ColorOsTheme.card(c)&0xffffff|0xed000000;
   Drawable surface=clickable?ColorDialogBuilder.touchBackground(c,fill,radius):shape(c,fill,radius);
   view.setBackground(header?TopEdgeGradient.panel(c,dp(c,radius),fill).over(surface):surface);
  };
  update.run();ColorOsTheme.watch(view,update);
 }
 public static Drawable button(Context c,boolean active){return new RippleDrawable(ColorStateList.valueOf(0x33808080),active?new AccentShape(c,18):new ColorOsSurface(c,18,0),shape(c,0xffffffff,18));}
 /** Quick-settings lighting is shared by tiles and the complete media capsule. */
 public static ToolsBackdrop controlsWindow(Context c,android.view.Window w,int radius,boolean opaqueFallback){
  ToolsBackdrop backdrop=new ToolsBackdrop(c,w,radius,opaqueFallback);glassWindow(c,w,backdrop);return backdrop;
 }
 public static Drawable mediaSurface(Context c){return new ColorOsSurface(c,22,0);}
 public static Drawable toolButton(Context c,boolean active){return new RippleDrawable(ColorStateList.valueOf(0x28ffffff),active?new AccentShape(c,22):new ColorOsSurface(c,22,0),shape(c,0xffffffff,22));}
}
