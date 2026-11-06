package replica;
import android.content.Context;import android.graphics.Bitmap;import java.util.WeakHashMap;
/** Only an in-memory compatibility holder; production rendering is the native live pipeline. */
public final class MaterialBackdrop {
 static final WeakHashMap<Context,Bitmap> frames=new WeakHashMap<>();
 public static void release(Context c){frames.remove(c);}
 private MaterialBackdrop(){}
}
