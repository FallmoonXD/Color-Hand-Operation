package replica;
import android.app.Dialog;import android.content.*;import android.view.*;

/** Accessibility window leaves the foreground player's activity resumed and focused. */
public final class ToolsOverlay {
 private static ToolsOverlay current;private Dialog dialog;private ToolsPanel panel;private ColorMotion motion;
 private boolean closing,removed;private final Context context;
 private final ComponentCallbacks configuration=new ComponentCallbacks(){
  public void onConfigurationChanged(android.content.res.Configuration c){if(!closing&&!removed){android.graphics.Bitmap retained=MaterialBackdrop.frames.get(panel);float[] anchor=panel.anchor();remove();ToolsOverlay next=new ToolsOverlay(context,retained,anchor);if(!next.removed)current=next;}}
  public void onLowMemory(){}
 };
 public static void show(Context c){show(c,NativeState.gesture(c));}
 /** Service teardown removes its windows immediately, including an active exit animation. */
 public static void dismissAll(){if(current!=null)current.remove();}
 public static void show(Context c,float[] anchor){if(current!=null){android.util.Log.i("ReplicaTools","show ignored closing="+current.closing+" removed="+current.removed);return;}ToolsOverlay next=new ToolsOverlay(c,null,anchor.clone());if(!next.removed)current=next;}
 private ToolsOverlay(Context c){this(c,null,null);}
 private ToolsOverlay(Context c,android.graphics.Bitmap retained,float[] anchor){context=c;
  try{
   int theme=c.getResources().getIdentifier(PanelStyle.dark(c)?"ReplicaGlassDark":"ReplicaGlass","style",c.getPackageName());
   dialog=new Dialog(c,theme){public boolean onTouchEvent(MotionEvent e){if(e.getActionMasked()==MotionEvent.ACTION_OUTSIDE){if(panel==null||!panel.hasChildDialogs())close();return true;}return super.onTouchEvent(e);}};
   dialog.setCanceledOnTouchOutside(false);dialog.setOnDismissListener(d->remove());Window w=dialog.getWindow();w.requestFeature(Window.FEATURE_NO_TITLE);
   w.setType(2032);w.addFlags(WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE|WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL|WindowManager.LayoutParams.FLAG_WATCH_OUTSIDE_TOUCH);w.clearFlags(WindowManager.LayoutParams.FLAG_DIM_BEHIND);
   panel=new ToolsPanel(c,w,this::close,retained,anchor);motion=new ColorMotion(w);WindowManager.LayoutParams attributes=w.getAttributes();attributes.setTitle(context.getString(dev.edgeflow.R.string.app_name)+" 快速工具");w.setAttributes(attributes);
   w.getDecorView().addOnAttachStateChangeListener(new View.OnAttachStateChangeListener(){public void onViewAttachedToWindow(View v){}public void onViewDetachedFromWindow(View v){remove();}});
   dialog.show();panel.position();
   c.registerComponentCallbacks(configuration);motion.enter();android.util.Log.i("ReplicaTools","accessibility overlay shown; no activity launch");
  }catch(Exception e){android.util.Log.e("ReplicaTools","Unable to show tools",e);remove();}
 }
 private void close(){if(closing||removed)return;closing=true;android.util.Log.i("ReplicaTools","close requested");if(motion!=null)motion.exit(this::remove);else remove();}
 private void remove(){if(removed)return;removed=true;android.util.Log.i("ReplicaTools","removed");context.unregisterComponentCallbacks(configuration);if(motion!=null)motion.cancel();if(panel!=null)panel.closeResources();if(dialog!=null)try{dialog.dismiss();}catch(Exception ignored){}if(current==this)current=null;}
}
