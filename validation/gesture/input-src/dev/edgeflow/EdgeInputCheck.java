package dev.edgeflow;

import java.util.ArrayList;
import java.util.HashMap;

/** Executes production EdgeHandleView input/timer bodies with a deterministic clock. */
public final class EdgeInputCheck {
    private static int checks;
    private static void check(boolean ok,String label){checks++;if(!ok)throw new AssertionError(label);}
    private static EdgeHandleView edge(GestureService service,boolean quick){Handler.tasks.clear();SystemClock.now=1000;service.prefs.put("trigger_dp",138f);service.prefs.put("long_trigger_dp",306f);service.prefs.put("long_delay_ms",300);service.prefs.put("quick_action_long",quick);return new EdgeHandleView(service,"left");}
    private static void input(EdgeHandleView edge,int action,long down,long time,float x,float y){edge.onTouchEvent(new MotionEvent(action,down,time,x,y));}
    public static void main(String[] args){
        GestureService a=new GestureService();EdgeHandleView edge=edge(a,true);input(edge,0,1000,1000,5,1300);SystemClock.now=1200;input(edge,2,1000,1100,355,1300);
        check(Handler.next()==1300,"queued MOVE schedules original absolute deadline, not handlerTime+remaining");
        Handler.advance(1299);check(a.actions.isEmpty(),"timer cannot execute before configured duration");Handler.advance(1300);
        check(a.actions.size()==1&&a.actions.get(0).equals("straight:long"),"held timer dispatches once at deadline");
        input(edge,1,1000,1400,355,1300);Handler.advance(2000);check(a.actions.size()==1&&Handler.tasks.isEmpty(),"UP after held dispatch cannot duplicate or reschedule");

        GestureService b=new GestureService();edge=edge(b,true);input(edge,0,1000,1000,5,1300);input(edge,2,1000,1100,355,1300);
        SystemClock.now=1500;input(edge,1,1000,1200,355,1300);Handler.advance(2000);
        check(b.actions.size()==1&&b.actions.get(0).equals("straight:short"),"queued early UP uses event time instead of processing time");
        check(Handler.tasks.isEmpty(),"early UP removes pending long timer");

        for(boolean quick:new boolean[]{false,true}){
            GestureService s=new GestureService();edge=edge(s,quick);input(edge,0,1000,1000,5,1300);input(edge,2,1000,1100,355,1300);input(edge,1,1000,1299,355,1300);Handler.advance(2000);
            check(s.actions.size()==1&&s.actions.get(0).equals("straight:short"),"duration-1 UP is short quick="+quick);
            s=new GestureService();edge=edge(s,quick);input(edge,0,1000,1000,5,1300);input(edge,2,1000,1100,355,1300);input(edge,1,1000,1300,355,1300);Handler.advance(2000);
            check(s.actions.size()==1&&s.actions.get(0).equals("straight:long"),"exact deadline UP is long quick="+quick);
        }

        GestureService c=new GestureService();edge=edge(c,true);input(edge,0,1000,1000,5,1300);input(edge,2,1000,1100,355,1300);input(edge,2,1000,1200,165,1300);
        check(Handler.tasks.isEmpty(),"retraction cancels queued handler runnable");Handler.advance(1400);check(c.actions.isEmpty(),"old long timer cannot fire outside long region");
        input(edge,2,1000,1400,355,950);Handler.advance(1400);check(c.actions.size()==1&&c.actions.get(0).equals("up:long"),"late reentry uses latest direction and original deadline");

        GestureService d=new GestureService();edge=edge(d,true);input(edge,0,1000,1000,5,1300);input(edge,2,1000,1100,355,1300);input(edge,2,1000,1200,355,1650);
        check(Handler.next()==1300,"direction change does not restart deadline");Handler.advance(1300);check(d.actions.size()==1&&d.actions.get(0).equals("down:long"),"timer dispatch uses current direction");

        for(int cancel:new int[]{MotionEvent.ACTION_CANCEL,MotionEvent.ACTION_POINTER_DOWN}){
            GestureService s=new GestureService();edge=edge(s,true);input(edge,0,1000,1000,5,1300);input(edge,2,1000,1100,355,1300);input(edge,cancel,1000,1200,355,1300);Handler.advance(2000);
            check(s.actions.isEmpty()&&Handler.tasks.isEmpty(),"cancel/multipointer removes timer and prevents action type="+cancel);
        }
        GestureService e=new GestureService();edge=edge(e,true);input(edge,0,1000,1000,5,1300);input(edge,2,1000,1100,355,1300);edge.cancel();Handler.advance(1400);
        check(e.actions.isEmpty()&&Handler.tasks.isEmpty(),"service teardown/rebuild cancellation removes pending timer");
        input(edge,0,2000,2000,5,1300);input(edge,2,2000,2100,355,1300);Handler.advance(2299);check(e.actions.isEmpty(),"new DOWN gets independent deadline");Handler.advance(2300);check(e.actions.size()==1,"new DOWN can execute after cancellation");

        GestureService f=new GestureService();edge=edge(f,true);input(edge,0,1000,1000,5,1300);input(edge,2,1000,1100,355,1300);input(edge,0,1200,1200,5,1300);input(edge,2,1200,1250,355,1300);Handler.advance(1300);
        check(f.actions.isEmpty()&&Handler.next()==1500,"replacement DOWN removes old callback deadline");Handler.advance(1500);check(f.actions.size()==1,"replacement DOWN dispatches only new gesture");

        GestureService g=new GestureService();edge=edge(g,true);g.prefs.put("quick_action_short",true);input(edge,0,1000,1000,5,1300);input(edge,2,1000,1100,355,1300);input(edge,1,1000,1400,355,1300);Handler.advance(2000);
        check(g.actions.size()==1&&g.actions.get(0).equals("straight:short")&&Handler.tasks.isEmpty(),"short quick action retains priority and suppresses long timer");
        GestureService h=new GestureService();edge=edge(h,true);input(edge,0,1000,1000,5,1300);input(edge,2,1000,1100,355,1300);h.prefs.put("left_enabled",false);Handler.advance(1300);
        check(h.actions.isEmpty()&&Handler.tasks.isEmpty(),"disabling a handle blocks pending dispatch before the visibility update");
        check(!edge.onTouchEvent(new MotionEvent(0,2000,2000,5,1300)),"disabled handle refuses a new DOWN before the visibility update");
        GestureService disabled=new GestureService();edge=edge(disabled,true);disabled.prefs.put("quick_action_short",true);
        input(edge,0,1000,1000,5,1300);disabled.prefs.put("left_enabled",false);
        input(edge,2,1000,1110,200,1300);input(edge,1,1000,1200,200,1300);
        check(disabled.actions.isEmpty()&&Handler.tasks.isEmpty(),"disabling a handle during a quick short contact cancels safely");
        for(boolean quick:new boolean[]{false,true}){
            GestureService s=new GestureService();edge=edge(s,true);s.prefs.put("quick_vibration",quick);
            input(edge,0,1000,1000,5,1300);input(edge,2,1000,1050,165,1300);
            check(s.feedback.size()==(quick?1:0),"quick vibration controls initial threshold timing="+quick);
            input(edge,2,1000,1100,10,1300);input(edge,2,1000,1150,165,1300);
            check(s.feedback.size()==(quick?1:0),"retraction and reentry cannot repeat threshold vibration");
            input(edge,1,1000,1200,165,1300);check(s.feedback.size()==1&&!s.feedback.get(0),"short release gives exactly one feedback with either timing");
            s=new GestureService();edge=edge(s,true);s.prefs.put("quick_vibration",quick);input(edge,0,1000,1000,5,1300);input(edge,2,1000,1100,355,1300);Handler.advance(1300);
            check(s.feedback.size()==(quick?2:1)&&s.feedback.get(s.feedback.size()-1),"long recognition adds long feedback without duplicate release="+quick);input(edge,1,1000,1400,355,1300);check(s.feedback.size()==(quick?2:1),"UP after held action never repeats feedback");
            s=new GestureService();edge=edge(s,true);s.prefs.put("quick_vibration",quick);s.prefs.put("quick_action_short",true);input(edge,0,1000,1000,5,1300);input(edge,2,1000,1100,165,1300);
            check(s.feedback.size()==1&&s.actions.size()==1,"quick action gets action-time feedback even with quick vibration disabled");
            s=new GestureService();edge=edge(s,true);s.prefs.put("quick_vibration",quick);input(edge,0,1000,1000,5,1300);input(edge,2,1000,1100,165,1300);input(edge,3,1000,1150,165,1300);
            check(s.feedback.size()==(quick?1:0)&&s.actions.isEmpty(),"cancel never gives deferred feedback");
        }
        GestureService trialService=new GestureService();edge=edge(trialService,true);
        GestureTrial trial=new GestureTrial();GestureTrial.active=trial;
        input(edge,0,1000,1000,5,1300);input(edge,2,1000,1100,355,1300);
        Handler.advance(1300);check(trial.kind==2&&trialService.actions.isEmpty()&&trialService.feedback.isEmpty(),"held trial reports the actual long recognizer result without haptics or action");
        input(edge,1,1000,1400,355,1300);GestureTrial.active=null;
        check(trialService.actions.isEmpty(),"trial release cannot execute a second action");
        edge=edge(trialService,true);GestureTrial.active=trial;input(edge,0,1000,1000,5,1300);
        GestureTrial.active=null;input(edge,1,1000,1150,200,1300);
        check(trialService.actions.isEmpty(),"leaving a trial mid-contact cannot turn it into a live action");
        System.out.println("PASS "+checks+" production edge input/timer assertions");
    }
}

// These stubs model only the interfaces used by the extracted production methods.
class Context {static final int MODE_PRIVATE=0;}
class SharedPreferences extends HashMap<String,Object> {
    java.util.Map<String,?> getAll(){return this;}
    boolean getBoolean(String k,boolean fallback){Object v=get(k);return v==null?fallback:(Boolean)v;}
    float getFloat(String k,float fallback){Object v=get(k);return v==null?fallback:(Float)v;}
    int getInt(String k,int fallback){Object v=get(k);return v==null?fallback:(Integer)v;}
}
class Resources {static class Metrics {float density=1;}final Metrics metrics=new Metrics();Metrics getDisplayMetrics(){return metrics;}}
class GestureService {
    final SharedPreferences prefs=new SharedPreferences();final Resources resources=new Resources();final ArrayList<String> actions=new ArrayList<>();final ArrayList<Boolean> feedback=new ArrayList<>();
    boolean canStartGesture(String id){return prefs.getBoolean(id+"_enabled",!GestureHandleConfig.isExtra(id));}SharedPreferences getSharedPreferences(String name,int mode){return prefs;}
    String gesturePackage(){return "dev.test";}
    boolean canStartGesture(String id,String pkg){return canStartGesture(id);}
    void beginWave(boolean left,float y){}void updateWave(float inward,float vertical,int direction,String id,boolean longer,String pkg){}void releaseWave(){}
    void dispatchGesture(String id,String direction,boolean longer,float[] anchor,String pkg){actions.add(direction+(longer?":long":":short"));}
}
class GestureTrial {
    static GestureTrial active;String application="dev.test";int kind;
    static GestureTrial current(){return active;}
    void begin(String handle){}void cancel(){}
    void show(String handle,GestureRecognizer gesture,int result,long now){kind=result;}
}
class SystemClock {static long now;static long uptimeMillis(){return now;}}
class Handler {
    static final class Task {final Runnable action;final long when;Task(Runnable action,long when){this.action=action;this.when=when;}}
    static final ArrayList<Task> tasks=new ArrayList<>();
    void removeCallbacks(Runnable action){tasks.removeIf(t->t.action==action);}
    void postAtTime(Runnable action,long when){tasks.add(new Task(action,when));}
    void postDelayed(Runnable action,long delay){postAtTime(action,SystemClock.now+delay);}
    static long next(){long next=Long.MAX_VALUE;for(Task task:tasks)next=Math.min(next,task.when);return next;}
    static void advance(long time){while(next()<=time){long due=next();Task selected=null;for(Task task:tasks)if(task.when==due){selected=task;break;}tasks.remove(selected);SystemClock.now=Math.max(SystemClock.now,due);selected.action.run();}SystemClock.now=time;}
}
class MotionEvent {
    static final int ACTION_DOWN=0,ACTION_UP=1,ACTION_MOVE=2,ACTION_CANCEL=3,ACTION_POINTER_DOWN=5;
    final int action;final long down,time;final float x,y;
    MotionEvent(int action,long down,long time,float x,float y){this.action=action;this.down=down;this.time=time;this.x=x;this.y=y;}
    int getActionMasked(){return action;}int getPointerId(int index){return 0;}int findPointerIndex(int pointer){return pointer==0?0:-1;}
    float getRawX(){return x;}float getRawY(){return y;}long getEventTime(){return time;}long getDownTime(){return down;}
}
class QuickLauncherOverlay {static void onGestureMotion(int action,float x,float y){}}
class MagnificationOverlay {static void onGestureMotion(int action,float x,float y){}}
class ScreenMoveOverlay {static void onGestureMotion(int action,float x,float y){}}
