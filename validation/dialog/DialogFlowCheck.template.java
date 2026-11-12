import java.util.*;
public final class DialogFlowCheck {
 static int checks;static final List<Runnable> tasks=new ArrayList<>();static final List<String> events=new ArrayList<>();
 static void check(boolean value,String label){checks++;if(!value)throw new AssertionError(label);}
 static void drain(){while(!tasks.isEmpty())tasks.remove(0).run();}
 static class Context{}static class Bundle{}
 interface Callbacks {void onActivityCreated(Activity a,Bundle b);void onActivityStarted(Activity a);void onActivityResumed(Activity a);void onActivityPaused(Activity a);void onActivityStopped(Activity a);void onActivitySaveInstanceState(Activity a,Bundle b);void onActivityDestroyed(Activity a);}
 static final class Application {Callbacks callback;void registerActivityLifecycleCallbacks(Callbacks c){callback=c;}void unregisterActivityLifecycleCallbacks(Callbacks c){if(callback==c)callback=null;}}
 static final class Activity extends Context {final Application app=new Application();boolean finishing,destroyed;boolean isFinishing(){return finishing;}boolean isDestroyed(){return destroyed;}Application getApplication(){return app;}}
 static final class Handler{void post(Runnable r){tasks.add(r);}}
 static final class Window{Object getDecorView(){return this;}}
 static final class NativeMaterial{static void cancelEntrance(Object view){}}
 static final class Prop {Prop withEndAction(Runnable r){return this;}void cancel(){}Prop alpha(float v){return this;}Prop scaleX(float v){return this;}Prop scaleY(float v){return this;}Prop setDuration(long v){return this;}void start(){}}
 static final class Root {boolean attached=true;boolean isAttachedToWindow(){return attached;}void cancelPendingInputEvents(){}void removeCallbacks(Runnable r){}Prop animate(){return new Prop();}}
 static final class Motion{void exit(Runnable done){}}
 static final class Adapter{void unregisterDataSetObserver(Object observer){}}
 static final class ListView{Adapter getAdapter(){return new Adapter();}}
 static class BaseDialog {
  final Context context;boolean shown=true;int removals;Runnable onDismiss=()->events.add("dismiss");
  BaseDialog(Context c){context=c;}Context getContext(){return context;}Window getWindow(){return new Window();}boolean isShowing(){return shown;}
  public void dismiss(){shown=false;removals++;tasks.add(onDismiss);}
 }
 static final class ColorDialog extends BaseDialog {
  final Root root=new Root();final Handler main=new Handler();final Runnable entrance=()->{};
  boolean sheet=true,glass,closing,finished,paused,deliveryPending,deliveryCancelled;Runnable pendingAction,animationDone;Callbacks lifecycle;Motion motion;ListView list;Object observer;
  ColorDialog(Context c){super(c);observeOwner();}
  static Activity activity(Context c){return c instanceof Activity?(Activity)c:null;}
  void animatePanel(boolean enter,Runnable done){animationDone=done;}void stopMotion(){}boolean animations(){return true;}void hideKeyboard(){}
  /* PRODUCTION */
 }
 static ColorDialog dialog(){return new ColorDialog(new Activity());}
 public static void main(String[] args){
  ColorDialog first=dialog();first.dismissThen(()->events.add("navigate"));check(events.isEmpty()&&first.closing,"navigation waits during exit animation");
  first.animationDone.run();check(first.removals==1&&events.isEmpty(),"old window is removed before callback dispatch");
  drain();check(events.equals(Arrays.asList("dismiss","navigate")),"original dismiss listener precedes navigation");check(((Activity)first.context).app.callback==null,"deferred dispatch releases lifecycle observer");
  events.clear();first.animationDone.run();first.dismiss();drain();check(first.removals==1&&events.isEmpty(),"duplicate completion cannot remove or dispatch twice");
  ColorDialog late=dialog();late.dismissThen(()->events.add("late"));late.animationDone.run();late.finishDismiss();drain();check(!events.contains("late"),"immediate teardown cancels already queued navigation");events.clear();
  ColorDialog paused=dialog();paused.dismissThen(()->events.add("paused"));Callbacks callback=paused.lifecycle;callback.onActivityPaused((Activity)paused.context);paused.animationDone.run();drain();check(!events.contains("paused")&&paused.removals==1,"pause during exit cancels navigation and stale end callback");events.clear();
  ColorDialog queued=dialog();queued.dismissThen(()->events.add("queued"));queued.animationDone.run();queued.lifecycle.onActivityPaused((Activity)queued.context);drain();check(!events.contains("queued"),"pause after removal but before dispatch also cancels navigation");events.clear();
  ColorDialog destroyed=dialog();destroyed.dismissThen(()->events.add("destroyed"));destroyed.lifecycle.onActivityDestroyed((Activity)destroyed.context);destroyed.animationDone.run();drain();check(!events.contains("destroyed"),"owner destruction never navigates");events.clear();
  ColorDialog repeated=dialog();repeated.dismissThen(()->events.add("first"));repeated.dismissThen(()->events.add("second"));repeated.animationDone.run();drain();check(events.equals(Arrays.asList("dismiss","first")),"repeated clicks cannot replace the first pending selection");events.clear();
  ColorDialog plain=dialog();plain.dismiss();plain.animationDone.run();drain();check(events.equals(Arrays.asList("dismiss"))&&!plain.deliveryPending,"plain cancel only dismisses");events.clear();
  ColorDialog gone=dialog();((Activity)gone.context).finishing=true;gone.dismissThen(()->events.add("invalid"));drain();check(!events.contains("invalid")&&gone.finished,"unavailable Activity closes without dispatch");events.clear();
  ColorDialog overlay=new ColorDialog(new Context());overlay.dismissThen(()->events.add("overlay"));overlay.animationDone.run();drain();check(events.equals(Arrays.asList("dismiss","overlay")),"service dialog dispatch does not require an Activity");events.clear();
  ColorDialog nested=dialog();nested.onDismiss=()->{events.add("dismiss");nested.finishDismiss();};nested.dismissThen(()->events.add("cancelled-by-owner"));nested.animationDone.run();drain();check(!events.contains("cancelled-by-owner"),"owner teardown from onDismiss can cancel queued navigation");events.clear();
  ColorDialog failure=dialog();failure.dismissThen(()->{throw new IllegalArgumentException("callback");});failure.animationDone.run();try{drain();throw new AssertionError("callback should throw");}catch(IllegalArgumentException expected){}check(((Activity)failure.context).app.callback==null,"callback failure still releases observer");
  System.out.println("PASS "+checks+" production dialog callback assertions");
 }
}
