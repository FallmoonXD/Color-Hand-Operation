package replica;
import java.util.*;
import java.util.concurrent.RejectedExecutionException;

/** Executes the production controller methods; only Android views/queues/process boundary are fake. */
public final class BrightnessControlCheck {
 final Context context=new Context();final Slider slider=new Slider();final Handler handler=new Handler();final Worker worker=new Worker();
 boolean dragging,dragInvalidated,started,warnedUnavailable,writing,closed,warmAttempted;State state;final ArrayDeque<Request> pending=new ArrayDeque<>();FakeRootCommand inFlight;QuickControlsSession session;
 enum Phase{DRAG,SAVE,CANCEL}
 long gesture,failedGesture=-1;
 static final class State {final BrightnessRangePolicy.Range range;final boolean multibits;State(BrightnessRangePolicy.Range r,boolean m){range=r;multibits=m;}}
 static final class Request {final State state;final int progress;final Phase phase;final long gesture;Request(State s,int p,Phase phase,long gesture){state=s;progress=p;this.phase=phase;this.gesture=gesture;}}
 void write(int progress){write(progress,Phase.DRAG);}
 void stop(){started=false;}
 /* PRODUCTION */
 static int checks;
 static void check(boolean value,String message){checks++;if(!value)throw new AssertionError(message);}
 static BrightnessControlCheck control(boolean multibits){BrightnessControlCheck c=new BrightnessControlCheck();c.state=new State(BrightnessRangePolicy.values(2,4095),multibits);return c;}
 static void reset(){FakeRootCommand.commands.clear();FakeRootCommand.exit=0;Toast.messages.clear();Settings.System.value="2728";}
 public static void main(String[] args){
  reset();BrightnessControlCheck unknown=new BrightnessControlCheck();unknown.sync();unknown.sync();
  check(!unknown.slider.enabled&&Toast.messages.size()==1,"unknown range disables UI and warns once");
  check(unknown.slider.description.contains("无法确认亮度范围"),"unavailable state is accessible");
  unknown.write(10);check(unknown.worker.queue.isEmpty()&&FakeRootCommand.commands.isEmpty(),"unknown range cannot enqueue a process");
  reset();BrightnessControlCheck normal=control(false);normal.sync();
  check(normal.slider.enabled&&normal.slider.max==4093&&normal.slider.progress==2726,"known range sync retains raw mapping");
  check(normal.slider.description.equals("屏幕亮度 67%"),"valid sync describes actual slider percent");
  normal.write(100);normal.write(2726);check(normal.worker.queue.size()==1,"drag writes coalesce into one worker");normal.worker.runAll();
  check(FakeRootCommand.commands.equals(List.of("settings put system screen_brightness 2728")),"last target only and no unconfirmed raw adjustment");
  check(!normal.writing&&normal.pending.isEmpty(),"drain releases writer state");
  reset();BrightnessControlCheck oplus=control(true);oplus.write(2726);oplus.worker.runAll();
  check(FakeRootCommand.commands.equals(List.of("drag 2728")),"confirmed multibits uses a temporary display adjustment");
  oplus.write(55,Phase.SAVE);oplus.write(75);oplus.write(85);oplus.write(85,Phase.CANCEL);oplus.worker.runAll();
  check(FakeRootCommand.commands.equals(List.of("drag 2728","save 57","cancel")),"new drag and cancel preserve preceding release");
  reset();BrightnessControlCheck changed=control(true);changed.write(2000);changed.state=new State(BrightnessRangePolicy.values(0,255),false);changed.worker.runAll();
  check(FakeRootCommand.commands.isEmpty()&&!changed.writing,"queued old-range write is discarded after range identity changes");
  reset();BrightnessControlCheck lost=control(true);lost.write(2000);lost.state=null;lost.worker.runAll();
  check(FakeRootCommand.commands.isEmpty(),"lost range invalidates queued writes");
  reset();BrightnessControlCheck ending=control(true);ending.started=true;ending.write(2000);ending.close();ending.worker.runAll();ending.handler.runAll();
  check(FakeRootCommand.commands.isEmpty()&&Toast.messages.isEmpty(),"closing before worker runs performs no settings writes or notifications");
  check(ending.worker.shutdown&&ending.slider.listenerRemoved&&!ending.started,"close releases listener and worker lifecycle");
  reset();BrightnessControlCheck invalidDrag=control(true);invalidDrag.dragInvalidated=true;invalidDrag.write(1000);
  check(invalidDrag.worker.queue.isEmpty(),"changed-range drag cannot reinterpret old slider progress");
  reset();BrightnessControlCheck fail=control(true);FakeRootCommand.exit=1;fail.write(2000);fail.worker.runAll();fail.handler.runAll();
  check(!fail.writing&&fail.pending.isEmpty()&&Toast.messages.equals(List.of("亮度调整未完成")),"failed command releases writer and uses existing error UI");
  fail.write(1200);check(fail.worker.queue.isEmpty(),"failed drag does not re-request root for each motion event");
  fail.gesture++;fail.write(1200);check(fail.worker.queue.size()==1,"next gesture can retry");
  reset();BrightnessControlCheck late=control(true);FakeRootCommand.exit=1;late.write(2000);late.worker.runAll();late.close();late.handler.runAll();
  check(Toast.messages.isEmpty(),"close cancels delayed failure UI");
  reset();BrightnessControlCheck stopped=control(true);stopped.worker.shutdown=true;stopped.write(2000);
  check(!stopped.writing&&stopped.pending.isEmpty(),"worker rejection cannot leave writing stuck");
  reset();BrightnessControlCheck unreadable=control(true);Settings.System.value="bad";unreadable.sync();
  check(!unreadable.slider.enabled&&Toast.messages.equals(List.of("无法读取当前亮度")),"unreadable current setting disables rather than inventing position");
  Settings.System.value="2728";unreadable.sync();check(unreadable.slider.enabled&&!unreadable.warnedUnavailable,"later valid current value recovers availability");
  reset();BrightnessControlCheck dead=new BrightnessControlCheck();dead.close();dead.sync();dead.write(100);
  check(Toast.messages.isEmpty()&&dead.worker.queue.isEmpty(),"closed controller cannot revive UI or writes");
  reset();BrightnessControlCheck closing=control(true);closing.write(1000);FakeRootCommand.beforeGrant=closing::close;closing.worker.runAll();
  check(FakeRootCommand.commands.isEmpty()&&!closing.writing,"closing while waiting for root cancels the active command");
  reset();BrightnessControlCheck invalidated=control(true);invalidated.write(1000);FakeRootCommand.beforeGrant=()->invalidated.updateRange(null,false);invalidated.worker.runAll();
  check(FakeRootCommand.commands.isEmpty()&&!invalidated.writing,"losing range while waiting for root cancels the active command");
  reset();BrightnessControlCheck replaced=control(false);replaced.write(100);FakeRootCommand.beforeGrant=()->replaced.write(200);replaced.worker.runAll();
  check(FakeRootCommand.commands.equals(List.of("settings put system screen_brightness 202")),"only latest drag value runs after delayed authorization");
  System.out.println("PASS "+checks+" production brightness UI/queue assertions");
 }
 static final class Context {Object getContentResolver(){return this;}}
 static final class Settings {static final class System {static String value="2728";static String getString(Object resolver,String key){return value;}}}
 static final class Slider {boolean enabled,listenerRemoved;int max,progress;String description,state;void setEnabled(boolean v){enabled=v;}void setMax(int v){max=v;}void setProgress(int v){progress=v;}int getProgress(){return progress;}void setContentDescription(String v){description=v;}void setStateDescription(String v){state=v;}void setOnTouchListener(Object ignored){}void setOnSeekBarChangeListener(Object ignored){listenerRemoved=true;}}
 static final class Handler {final ArrayDeque<Runnable> queue=new ArrayDeque<>();void post(Runnable r){queue.add(r);}void removeCallbacksAndMessages(Object token){queue.clear();}void runAll(){while(!queue.isEmpty())queue.remove().run();}}
 static final class Worker {final ArrayDeque<Runnable> queue=new ArrayDeque<>();boolean shutdown;void submit(Runnable r){if(shutdown)throw new RejectedExecutionException();queue.add(r);}void shutdown(){shutdown=true;}void runAll(){while(!queue.isEmpty())queue.remove().run();}}
 static final class Toast {static final int LENGTH_SHORT=0;static final List<String> messages=new ArrayList<>();String message;static Toast makeText(Context c,String message,int duration){Toast t=new Toast();t.message=message;return t;}void show(){messages.add(message);}}
 static final class FakeRootCommand {
  static final List<String> commands=new ArrayList<>();static int exit;static Runnable beforeGrant;
  final String command;boolean cancelled;FakeRootCommand(String command){this.command=command;}
  boolean run(java.util.function.BooleanSupplier valid)throws java.io.IOException,InterruptedException {
   if(beforeGrant!=null){Runnable action=beforeGrant;beforeGrant=null;action.run();}
   if(cancelled||!valid.getAsBoolean())return false;
   commands.add(command);if(exit!=0)throw new java.io.IOException("denied");return true;
  }
  void cancel(){cancelled=true;}
 }
 static final class QuickControlsSession {
  QuickControlsSession(Context c,BrightnessRangePolicy.Range range){}
  void apply(String request,java.util.function.BooleanSupplier valid)throws Exception{new FakeRootCommand(request).run(valid);}
  void close(){}
 }
}
