package dev.edgeflow.lsp;
import java.util.*;

public final class LifecycleDispatchCheck {
    static int checks;
    static void ok(boolean value,String message){checks++;if(!value)throw new AssertionError(message);}
    static final class Clock implements LifecycleDispatch.Queue {
        long now;final Map<Runnable,Long> jobs=new IdentityHashMap<>();
        public long now(){return now;}
        public void post(Runnable task,long delay){jobs.put(task,now+delay);}
        public void remove(Runnable task){jobs.remove(task);}
        void advance(long end){
            for(;;){var next=jobs.entrySet().stream().min(Map.Entry.comparingByValue()).orElse(null);if(next==null||next.getValue()>end)break;now=next.getValue();Runnable task=next.getKey();jobs.remove(task);task.run();}now=end;
        }
    }
    public static void main(String[] args){
        Clock clock=new Clock();List<Long> checksAt=new ArrayList<>();final LifecycleDispatch[] owner=new LifecycleDispatch[1];
        boolean[] unlocked={false};long[] resumed={-1};
        Clock unlock=clock;owner[0]=new LifecycleDispatch(clock,()->{checksAt.add(unlock.now);if(unlocked[0]){if(resumed[0]<0)resumed[0]=unlock.now;}else owner[0].retry();});
        owner[0].waking();
        // USER_PRESENT can precede the actual keyguard change. No later display event is needed.
        clock.advance(340);unlocked[0]=true;clock.advance(400);
        ok(resumed[0]==350,"late keyguard handoff resumes on the next bounded check");
        ok(clock.jobs.isEmpty(),"successful recovery stops polling");
        clock=new Clock();checksAt.clear();Clock burst=clock;
        LifecycleDispatch dispatch=new LifecycleDispatch(clock,()->checksAt.add(burst.now));
        for(int i=0;i<100;i++)dispatch.signal();clock.advance(0);
        ok(checksAt.equals(List.of(0L)),"display burst coalesces into one prompt check");
        for(int i=1;i<=100;i++){clock.advance(i*10L);dispatch.signal();}clock.advance(1000);
        ok(checksAt.get(1)==10&&checksAt.get(checksAt.size()-1)==1000,"continuous display changes cannot starve recovery");
        clock=new Clock();Clock waiting=clock;checksAt.clear();owner[0]=new LifecycleDispatch(clock,()->{checksAt.add(waiting.now);owner[0].retry();});
        owner[0].waking();clock.advance(10000);
        ok(checksAt.get(checksAt.size()-1)==2000&&clock.jobs.isEmpty(),"locked screen does not poll forever");
        int before=checksAt.size();owner[0].waking();clock.advance(10000);
        ok(checksAt.size()==before+1,"a later unlock signal retries after the prior window expired");
        owner[0].pause();clock.advance(20000);ok(clock.jobs.isEmpty()&&checksAt.size()==before+1,"screen-off cancels follow-up checks");
        owner[0].retry();ok(clock.jobs.isEmpty(),"screen-off also cancels the retry budget");
        clock=new Clock();Clock expedite=clock;checksAt.clear();LifecycleDispatch quick=new LifecycleDispatch(clock,()->checksAt.add(expedite.now));
        quick.arm();quick.retry();clock.advance(10);quick.signal();clock.advance(10);
        ok(checksAt.equals(List.of(10L)),"disable or unlock signals expedite an already queued retry");
        clock.advance(100);ok(checksAt.size()==1,"expedited retry leaves no stale duplicate");
        System.out.println("PASS "+checks+" lifecycle scheduling assertions");
    }
}
