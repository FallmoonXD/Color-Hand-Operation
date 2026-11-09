package replica;
import android.app.Activity;import android.os.Bundle;import android.view.Window;
/** Manual activity entry; gesture actions are intercepted into ToolsOverlay. */
public class ToolsActivity extends Activity {
 private ToolsPanel panel;
 private ColorMotion motion;private boolean closing;
 @Override public void onCreate(Bundle state){
  setTheme(getResources().getIdentifier(PanelStyle.dark(this)?"ReplicaGlassDark":"ReplicaGlass","style",getPackageName()));
  super.onCreate(state);requestWindowFeature(Window.FEATURE_NO_TITLE);setFinishOnTouchOutside(true);
  float[] anchor=NativeState.intentGesture(this,state);panel=new ToolsPanel(this,getWindow(),this::finish,null,anchor);
  motion=ColorMotion.forTool(getWindow(),ToolAnimation.QUICK_TOOLS,anchor);motion.enter();overridePendingTransition(0,0);
 }
 void closeThen(Runnable after){
  if(closing)return;closing=true;
  Runnable done=()->{super.finish();overridePendingTransition(0,0);if(after!=null)after.run();};
  if(motion!=null)motion.exit(done);else done.run();
 }
 @Override public void finish(){closeThen(null);}
 @Override public void onDestroy(){if(motion!=null)motion.cancel();if(panel!=null)panel.closeResources();super.onDestroy();}
}
