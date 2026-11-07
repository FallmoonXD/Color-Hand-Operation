package replica;
import android.app.*;import android.content.Context;import android.content.ContextWrapper;import android.view.WindowManager;
/** A child dialog uses its own window material and only takes keyboard focus for text input. */
public final class GlassDialogBuilder extends ColorDialogBuilder {
 private final boolean overlay;
 public GlassDialogBuilder(Context c){super(c,true);overlay=overlayContext(c);}
 private static boolean overlayContext(Context c){if(c instanceof ToolsPanel&&((ToolsPanel)c).overlay())return true;while(c instanceof ContextWrapper){if(c instanceof android.accessibilityservice.AccessibilityService)return true;Context next=((ContextWrapper)c).getBaseContext();if(next==c)break;c=next;}return c instanceof android.accessibilityservice.AccessibilityService;}
 @Override public AlertDialog create(){AlertDialog d=super.create();if(overlay){d.getWindow().setType(WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY);if(!needsTextInput())d.getWindow().addFlags(WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE);}return d;}
}
