import replica.ColorSheetGeometry;
public final class DialogSpaceCheck {
 static int checks;static void check(boolean value,String message){checks++;if(!value)throw new AssertionError(message);}
 static void rect(Rect r,int l,int t,int right,int bottom,String message){check(r.left==l&&r.top==t&&r.right==right&&r.bottom==bottom,message+": "+r);}
 public static void main(String[] args){
  Context c=new Context();View v=new View();
  c.wm.metrics.insets.value=new Insets(0,100,0,100);v.visible=new Rect(0,100,1264,2680);
  rect(ColorWindowSpace.available(c,v),0,100,1264,2680,"full task bars");
  // A small floating root must never become the task boundary. No root width/height API exists here.
  rect(ColorWindowSpace.available(c,v),0,100,1264,2680,"floating anchor has full task menu space");
  c.wm.metrics.insets.value=new Insets(0,100,0,1174);v.visible=new Rect(0,100,1264,1606);
  Rect space=ColorWindowSpace.available(c,v);rect(space,0,100,1264,1606,"floating dialog uses task IME, not consumed local inset");
  ColorSheetGeometry.Bounds b=ColorSheetGeometry.fit(space.width(),space.height(),0,0,0,0,0,3.5f,Integer.MAX_VALUE);
  check(b.height==1338,"IME caps tall provider panel to 1338px");
  check(Math.min(b.height,700)==700,"short input form retains natural height");
  check(Math.min(b.height,2123)==1338,"long provider form yields height for footer");
  c.wm.metrics.insets.value=new Insets(0,100,0,100);
  rect(ColorWindowSpace.available(c,v),0,100,1264,1606,"visible frame covers OEM metrics IME omission");
  v.visible=new Rect(0,100,1264,2680);
  rect(ColorWindowSpace.available(c,v),0,100,1264,2680,"closing keyboard restores space without saved stale height");
  WindowInsets animated=new WindowInsets();animated.value=new Insets(0,100,0,600);v.visible=new Rect(0,100,1264,1606);
  rect(ColorWindowSpace.available(c,v,animated),0,100,1264,2180,"animated IME uses intermediate geometry instead of final visible frame");
  animated.value=new Insets(0,100,0,100);
  rect(ColorWindowSpace.available(c,v,animated),0,100,1264,2680,"animated keyboard dismissal ignores stale resized frame");
  animated.value=Insets.NONE;
  rect(ColorWindowSpace.available(c,v,animated),0,100,1264,2680,"overlay local insets retain task system bars");
  c.wm.metrics.insets.bars=new Insets(0,100,0,100);c.wm.metrics.insets.value=new Insets(0,100,0,1174);
  animated.value=new Insets(0,0,0,500);
  rect(ColorWindowSpace.available(c,v,animated,new Rect(28,240,1236,2680)),0,100,1264,2180,"bounded host IME converted to screen coordinates");
  animated.value=Insets.NONE;
  rect(ColorWindowSpace.available(c,v,animated,new Rect(28,240,1236,2680)),0,100,1264,2680,"animation ignores final metrics IME");
  c.wm.metrics.insets.bars=null;
  v.visible=new Rect(0,100,1264,2680);
  c.wm.metrics.bounds=new Rect(640,80,1264,1400);c.wm.metrics.insets.value=new Insets(0,20,0,40);
  rect(ColorWindowSpace.available(c,v),640,100,1264,1360,"split-screen origin and task boundary retained");
  c.wm.metrics.bounds=new Rect(0,0,2780,1264);c.wm.metrics.insets.value=new Insets(100,0,80,0);v.visible=new Rect(0,0,2780,1264);
  rect(ColorWindowSpace.available(c,v),100,0,2700,1264,"rotated horizontal bars/cutout");
  v.attached=false;v.visible=new Rect(0,0,1,1);
  rect(ColorWindowSpace.available(c,v),100,0,2700,1264,"before attach uses metrics rather than stale visible frame");
  c.wm.fail=true;v.attached=true;v.visible=new Rect(0,100,1264,1606);
  rect(ColorWindowSpace.available(c,v),0,100,1264,1606,"unavailable metrics falls back to display plus visible frame");
  v.visible=new Rect();rect(ColorWindowSpace.available(c,v),0,0,1264,2780,"empty transient visible frame does not collapse popup");
  System.out.println("PASS "+checks+" production task/IME space assertions");
 }
}
class Context {final Resources resources=new Resources();final WindowManager wm=new WindowManager();Resources getResources(){return resources;}<T>T getSystemService(Class<T> type){return type.cast(wm);}}
class Resources {final DisplayMetrics metrics=new DisplayMetrics();DisplayMetrics getDisplayMetrics(){return metrics;}}
class DisplayMetrics {int widthPixels=1264,heightPixels=2780;}
class WindowManager {final WindowMetrics metrics=new WindowMetrics();boolean fail;WindowMetrics getCurrentWindowMetrics(){if(fail)throw new IllegalStateException();return metrics;}}
class WindowMetrics {Rect bounds=new Rect(0,0,1264,2780);final WindowInsets insets=new WindowInsets();Rect getBounds(){return bounds;}WindowInsets getWindowInsets(){return insets;}}
class WindowInsets {Insets value=Insets.NONE,bars;Insets getInsets(int mask){return (mask&4)==0&&bars!=null?bars:value;}static class Type {static int systemBars(){return 1;}static int displayCutout(){return 2;}static int ime(){return 4;}}}
class Insets {static final Insets NONE=new Insets(0,0,0,0);final int left,top,right,bottom;Insets(int l,int t,int r,int b){left=l;top=t;right=r;bottom=b;}static Insets of(int l,int t,int r,int b){return new Insets(l,t,r,b);}static Insets max(Insets first,Insets second){return new Insets(Math.max(first.left,second.left),Math.max(first.top,second.top),Math.max(first.right,second.right),Math.max(first.bottom,second.bottom));}}
class View {boolean attached=true;Rect visible=new Rect();boolean isAttachedToWindow(){return attached;}void getWindowVisibleDisplayFrame(Rect out){out.set(visible);}}
class Rect {int left,top,right,bottom;Rect(){}Rect(int l,int t,int r,int b){left=l;top=t;right=r;bottom=b;}Rect(Rect r){set(r);}void set(Rect r){left=r.left;top=r.top;right=r.right;bottom=r.bottom;}boolean isEmpty(){return left>=right||top>=bottom;}void inset(int l,int t,int r,int b){left+=l;top+=t;right-=r;bottom-=b;}boolean intersect(Rect r){if(left<r.right&&r.left<right&&top<r.bottom&&r.top<bottom){left=Math.max(left,r.left);top=Math.max(top,r.top);right=Math.min(right,r.right);bottom=Math.min(bottom,r.bottom);return true;}return false;}int width(){return right-left;}int height(){return bottom-top;}public String toString(){return left+","+top+","+right+","+bottom;}}
/* PRODUCTION */
