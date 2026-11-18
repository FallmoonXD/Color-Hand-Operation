package dev.edgeflow.lsp;

/** Coalesces lifecycle signals without postponing recovery; briefly follows an unlock handoff. */
final class LifecycleDispatch {
    interface Queue {long now();void post(Runnable work,long delay);void remove(Runnable work);}
    private final Queue queue;private final Runnable work,run;
    private long due=Long.MAX_VALUE,retryUntil;
    LifecycleDispatch(Queue queue,Runnable work){this.queue=queue;this.work=work;run=()->{due=Long.MAX_VALUE;this.work.run();};}
    void signal(){enqueue(0);}
    void waking(){arm();signal();}
    void arm(){retryUntil=queue.now()+2000;}
    void retry(){long remaining=retryUntil-queue.now();if(remaining>0)enqueue(Math.min(50,remaining));}
    void pause(){queue.remove(run);due=Long.MAX_VALUE;retryUntil=0;}
    private void enqueue(long delay){long next=queue.now()+delay;if(next>=due)return;queue.remove(run);due=next;queue.post(run,delay);}
}
