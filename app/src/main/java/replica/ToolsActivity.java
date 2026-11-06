package replica;
import android.app.Activity;import android.os.Bundle;import android.view.Window;
/** Manual activity entry; gesture actions are intercepted into ToolsOverlay. */
public class ToolsActivity extends Activity {
 private ToolsPanel panel;
 @Override public void onCreate(Bundle state){setTheme(getResources().getIdentifier(PanelStyle.dark(this)?"ReplicaGlassDark":"ReplicaGlass","style",getPackageName()));super.onCreate(state);requestWindowFeature(Window.FEATURE_NO_TITLE);setFinishOnTouchOutside(true);panel=new ToolsPanel(this,getWindow(),this::finish);}
 @Override public void onDestroy(){if(panel!=null)panel.closeResources();super.onDestroy();}
}
