package replica;
import android.content.Context;
import android.app.Activity;
import android.os.Bundle;
/** Explicit standalone contracts; no reflection hooks into another app. */
public final class NativeState {
 public static final String GESTURE_EXTRA="replica.gesture";
 private static volatile float[] anchor;
 private static volatile long anchorTime;
 private static volatile String foreground="";
 public static void setGesture(float[] value){if(value!=null&&value.length==4){anchor=value.clone();anchorTime=android.os.SystemClock.uptimeMillis();}}
 public static void setForeground(String value){if(value!=null)foreground=value;}
 public static String foreground(Context c){return foreground;}
 public static float[] gesture(Context c){float[] a=anchor;return a!=null&&android.os.SystemClock.uptimeMillis()-anchorTime<2000?a.clone():defaultGesture(c);}
 public static float[] defaultGesture(Context c){android.graphics.Point s=PanelGeometry.screen(c);return new float[]{s.x,s.y*.5f,s.x*.65f,s.y*.5f};}
 public static float[] intentGesture(Activity a,Bundle b){float[] p=b==null?null:b.getFloatArray(GESTURE_EXTRA);if(p==null)p=a.getIntent().getFloatArrayExtra(GESTURE_EXTRA);if(p==null||p.length!=4)return defaultGesture(a);for(float f:p)if(!Float.isFinite(f))return defaultGesture(a);return p.clone();}
 private NativeState(){}
}
