package dev.edgeflow.lsp;

import java.util.Objects;
import java.util.function.Consumer;
import java.util.function.Supplier;

/** Reads LSPosed preferences on the SystemUI looper before creating any input session. */
final class PreferenceRetry<T> implements Runnable {
    interface Queue { void post(Runnable next,long delayMillis); }
    private final Supplier<T> read;
    private final Consumer<T> ready;
    private final Queue queue;
    private final Consumer<Throwable> firstFailure;
    private int delay=250;
    private boolean delivered;

    PreferenceRetry(Supplier<T> read,Consumer<T> ready,Queue queue,Consumer<Throwable> firstFailure){
        this.read=read;this.ready=ready;this.queue=queue;this.firstFailure=firstFailure;
    }
    public void run(){
        if(delivered)return;
        T value;
        try{value=Objects.requireNonNull(read.get(),"Remote preferences unavailable");}
        catch(RuntimeException | LinkageError unavailable){
            if(delay==250)firstFailure.accept(unavailable);
            queue.post(this,delay);
            delay=Math.min(30000,delay*2);
            return;
        }
        delivered=true;
        ready.accept(value);
    }
}
