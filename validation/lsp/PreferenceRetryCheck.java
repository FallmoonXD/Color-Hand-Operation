package dev.edgeflow.lsp;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.concurrent.atomic.AtomicInteger;

public final class PreferenceRetryCheck {
    public static void main(String[] args){
        ArrayDeque<Runnable> scheduled=new ArrayDeque<>();
        ArrayList<Long> delays=new ArrayList<>();
        AtomicInteger attempts=new AtomicInteger(),sessions=new AtomicInteger(),warnings=new AtomicInteger();
        PreferenceRetry<String> retry=new PreferenceRetry<>(()->{
            int attempt=attempts.incrementAndGet();
            if(attempt==1)throw new IllegalStateException("service starting");
            if(attempt<=10)return null;
            return "configuration";
        },value->{if(!value.equals("configuration"))throw new AssertionError(value);sessions.incrementAndGet();},
        (next,delay)->{scheduled.add(next);delays.add(delay);},failure->warnings.incrementAndGet());
        retry.run();
        if(sessions.get()!=0||scheduled.size()!=1)throw new AssertionError("armed before configuration");
        while(!scheduled.isEmpty()){
            if(scheduled.size()!=1)throw new AssertionError("overlapping retries");
            scheduled.remove().run();
        }
        retry.run();
        if(attempts.get()!=11||sessions.get()!=1||warnings.get()!=1||delays.get(0)!=250||delays.get(9)!=30000)
            throw new AssertionError("unbounded retry or duplicate initialization");
        AtomicInteger reads=new AtomicInteger(),started=new AtomicInteger();
        PreferenceRetry<String> transientFailure=new PreferenceRetry<>(()->{
            if(reads.incrementAndGet()==1)throw new IllegalStateException("not connected");
            return "ready";
        },value->started.incrementAndGet(),(work,delay)->scheduled.add(work),failure->{});
        transientFailure.run();
        if(started.get()!=0||scheduled.size()!=1)throw new AssertionError("must wait after first failure");
        scheduled.remove().run();
        transientFailure.run();
        if(reads.get()!=2||started.get()!=1||!scheduled.isEmpty())throw new AssertionError("duplicate session");
        System.out.println("PASS first read fails, second read initializes exactly once; extended outage backoff is bounded");
    }
}
