import java.util.ArrayList;
import java.util.List;
import java.util.TreeSet;

public final class WidgetAutoAdvanceCheck {
    private static int checks;
    private static void check(boolean condition,String description){checks++;if(!condition)throw new AssertionError(description);}
    public static void main(String[] args){
        SystemClock.now=19900;
        AppWidgetHostView first=new AppWidgetHostView(1);
        WidgetAutoAdvance controller=new WidgetAutoAdvance(first);
        controller.refresh();
        check(first.posted.isEmpty(),"detached host does not schedule");
        first.attached=true;controller.refresh();
        check(first.posted.size()==1&&first.delay==100,"first advance aligns to launcher interval");
        check(first.child.announcements==1,"host takes responsibility for advance");
        controller.refresh();controller.refresh();
        check(first.posted.size()==1&&first.child.announcements==1,"layout does not duplicate callbacks");
        AppWidgetHostView second=new AppWidgetHostView(2);second.attached=true;
        WidgetAutoAdvance secondController=new WidgetAutoAdvance(second);secondController.refresh();
        check(second.delay==350,"second widget staggers by 250 ms");
        first.fire();check(first.child.advances==1&&first.posted.size()==1,"visible collection advances and reschedules");
        first.shown=false;first.fire();
        check(first.child.advances==1&&first.posted.isEmpty(),"hidden host stops");
        first.shown=true;controller.refresh();check(first.posted.size()==1,"showing resumes scheduler");
        first.visible=false;first.fire();check(first.child.advances==1,"offscreen collection does not advance");
        TestAdvanceable old=first.child;first.child=new TestAdvanceable();controller.refresh();
        check(first.child.announcements==1&&first.posted.size()==1,"replaced RemoteViews gets one scheduler");
        first.visible=true;first.fire();check(first.child.advances==1&&old.advances==1,"replacement receives advance");
        first.attached=false;controller.stop();check(first.posted.isEmpty(),"detach removes pending callback");
        first.attached=true;first.info.autoAdvanceViewId=-1;controller.refresh();check(first.posted.isEmpty(),"undeclared collection is not advanced");
        first.info.autoAdvanceViewId=7;first.windowVisibility=8;controller.refresh();check(first.posted.isEmpty(),"invisible window does not schedule");
        secondController.stop();check(second.posted.isEmpty(),"all hosts release their callbacks");
        System.out.println("PASS "+checks+" production auto-advance assertions");
    }
}
class SystemClock { static long now;static long uptimeMillis(){return now;} }
class SparseBooleanArray {
    final TreeSet<Integer> ids=new TreeSet<>();
    void put(int id,boolean value){ids.add(id);}void delete(int id){ids.remove(id);}
    int indexOfKey(int id){return new ArrayList<>(ids).indexOf(id);}
}
class Rect {}
class View { static final int VISIBLE=0; }
interface Advanceable {void advance();void fyiWillBeAdvancedByHostKThx();}
class TestAdvanceable extends View implements Advanceable {
    int advances,announcements;
    public void advance(){advances++;}public void fyiWillBeAdvancedByHostKThx(){announcements++;}
}
class AppWidgetProviderInfo {int autoAdvanceViewId=7;}
class AppWidgetHostView {
    final int id;final AppWidgetProviderInfo info=new AppWidgetProviderInfo();
    final List<Runnable> posted=new ArrayList<>();
    TestAdvanceable child=new TestAdvanceable();boolean attached,shown=true,visible=true;int windowVisibility;long delay;
    AppWidgetHostView(int id){this.id=id;}
    AppWidgetProviderInfo getAppWidgetInfo(){return info;}
    View findViewById(int id){return child;}
    boolean isAttachedToWindow(){return attached;}boolean isShown(){return shown;}
    int getWindowVisibility(){return windowVisibility;}int getAppWidgetId(){return id;}
    boolean postDelayed(Runnable action,long delay){posted.add(action);this.delay=delay;return true;}
    void removeCallbacks(Runnable action){posted.removeIf(value->value==action);}
    boolean getGlobalVisibleRect(Rect bounds){return visible;}
    void fire(){Runnable action=posted.remove(0);SystemClock.now+=delay;action.run();}
}
