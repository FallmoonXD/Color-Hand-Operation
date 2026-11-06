package probe;
import android.app.UiAutomation;
import android.os.*;
import android.accessibilityservice.AccessibilityServiceInfo;
import android.view.accessibility.*;
import android.graphics.Rect;
/** Verification probe which does not suppress enabled accessibility services. */
public final class OverlayDump {
 static String esc(CharSequence s){return s==null?"":s.toString().replace("&","&amp;").replace("\"","&quot;").replace("<","&lt;").replace(">","&gt;");}
 static String range(AccessibilityNodeInfo n){AccessibilityNodeInfo.RangeInfo r=n.getRangeInfo();return r==null?"":" range-min=\""+r.getMin()+"\" range-max=\""+r.getMax()+"\" range-current=\""+r.getCurrent()+"\"";}
 static void node(AccessibilityNodeInfo n){if(n==null)return;Rect b=new Rect();n.getBoundsInScreen(b);System.out.print("<node visible-to-user=\""+n.isVisibleToUser()+"\""+range(n)+" text=\""+esc(n.getText())+"\" content-desc=\""+esc(n.getContentDescription())+"\" state-description=\""+esc(n.getStateDescription())+"\" package=\""+esc(n.getPackageName())+"\" class=\""+esc(n.getClassName())+"\" node-hash=\""+n.hashCode()+"\" window-id=\""+n.getWindowId()+"\" resource-id=\""+esc(n.getViewIdResourceName())+"\" enabled=\""+n.isEnabled()+"\" checked=\""+n.isChecked()+"\" clickable=\""+n.isClickable()+"\" scrollable=\""+n.isScrollable()+"\" bounds=\"["+b.left+","+b.top+"]["+b.right+","+b.bottom+"]\">");for(int i=0;i<n.getChildCount();i++)node(n.getChild(i));System.out.print("</node>");}
 static boolean setText(AccessibilityNodeInfo n,String description,String value){
  if(n==null)return false;
  if(n.isEditable()&&description.equals(String.valueOf(n.getContentDescription()))){Bundle b=new Bundle();b.putCharSequence(AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE,value);if(!n.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT,b))throw new IllegalStateException("Set text rejected");return true;}
  for(int i=0;i<n.getChildCount();i++)if(setText(n.getChild(i),description,value))return true;return false;
 }
 static boolean setProgress(AccessibilityNodeInfo n,String description,float value){
  if(n==null)return false;
  if(description.equals(String.valueOf(n.getContentDescription()))){Bundle b=new Bundle();b.putFloat(AccessibilityNodeInfo.ACTION_ARGUMENT_PROGRESS_VALUE,value);if(!n.performAction(AccessibilityNodeInfo.AccessibilityAction.ACTION_SET_PROGRESS.getId(),b))throw new IllegalStateException("Set progress rejected");return true;}
  for(int i=0;i<n.getChildCount();i++)if(setProgress(n.getChild(i),description,value))return true;return false;
 }
 public static void main(String[] args)throws Exception{
  Looper.prepareMainLooper();HandlerThread t=new HandlerThread("dump");t.start();Class<?> connection=Class.forName("android.app.IUiAutomationConnection");Object conn=Class.forName("android.app.UiAutomationConnection").getConstructor().newInstance();java.lang.reflect.Constructor<?> ctor=UiAutomation.class.getDeclaredConstructor(Looper.class,connection);ctor.setAccessible(true);UiAutomation ui=(UiAutomation)ctor.newInstance(t.getLooper(),conn);UiAutomation.class.getMethod("connect",int.class).invoke(ui,1);
  try{AccessibilityServiceInfo info=ui.getServiceInfo();info.flags|=AccessibilityServiceInfo.FLAG_RETRIEVE_INTERACTIVE_WINDOWS|AccessibilityServiceInfo.FLAG_REPORT_VIEW_IDS;ui.setServiceInfo(info);Thread.sleep(200);
   if(args.length==3&&args[0].equals("set-text")){boolean found=false;for(AccessibilityWindowInfo w:ui.getWindows())if(setText(w.getRoot(),args[1],args[2])){found=true;break;}if(!found)throw new IllegalArgumentException("Editable description not found");System.out.println("TEXT_SET");return;}
   if(args.length==3&&args[0].equals("set-progress")){boolean found=false;for(AccessibilityWindowInfo w:ui.getWindows())if(setProgress(w.getRoot(),args[1],Float.parseFloat(args[2]))){found=true;break;}if(!found)throw new IllegalArgumentException("Progress description not found");System.out.println("PROGRESS_SET");return;}
   System.out.print("<hierarchy>");for(AccessibilityWindowInfo w:ui.getWindows())node(w.getRoot());System.out.println("</hierarchy>");
  }finally{UiAutomation.class.getMethod("disconnect").invoke(ui);t.quitSafely();}
 }
}
