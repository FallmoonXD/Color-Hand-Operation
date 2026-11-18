package dev.edgeflow.lsp;

import java.util.LinkedHashMap;
import java.util.Map;

/** Owns only CHO-issued tokens. Death and explicit revocation share one cleanup path. */
final class WindowLeaseStore<K,T> {
    interface Endpoint {
        void link(Runnable death) throws Exception;
        void unlink(Runnable death);
        boolean alive();
    }
    interface Windows<T> {
        T createToken();
        void add(T token) throws Exception;
        void remove(T token) throws Exception;
    }
    private final Windows<T> windows;
    private final Map<K,Lease> leases=new LinkedHashMap<>();
    private final int limit;
    WindowLeaseStore(Windows<T> windows,int limit){this.windows=windows;this.limit=limit;}
    synchronized T grant(K key,Endpoint controller,Endpoint host)throws Exception {
        // A renewed host session gets a fresh token; the previous one cannot be reused.
        revoke(key);
        if(leases.size()>=limit)throw new IllegalStateException("Too many tool hosts");
        Lease lease=new Lease(key,controller,host,windows.createToken());leases.put(key,lease);
        try {
            lease.controllerLinked=true;controller.link(lease.death);
            if(lease.closed)throw new IllegalStateException("Tool controller disconnected");
            lease.hostLinked=true;host.link(lease.death);
            if(lease.closed||!controller.alive()||!host.alive())throw new IllegalStateException("Tool host disconnected");
            // add() can throw after registering a token. Cleanup must still try removal.
            lease.addAttempted=true;windows.add(lease.token);
            if(lease.closed||!controller.alive()||!host.alive())throw new IllegalStateException("Tool host disconnected");
            return lease.token;
        }catch(Exception failure){
            try{close(lease);}catch(Exception cleanup){failure.addSuppressed(cleanup);}
            throw failure;
        }
    }
    synchronized boolean revoke(K key)throws Exception {
        Lease lease=leases.get(key);if(lease==null)return false;close(lease);return true;
    }
    synchronized int size(){return leases.size();}
    synchronized T liveToken(K key){Lease lease=leases.get(key);return lease!=null&&!lease.closed&&lease.controller.alive()&&lease.host.alive()?lease.token:null;}
    private synchronized void close(Lease lease)throws Exception {
        if(lease.closed)return;
        // Keep failed removals registered so explicit revocation can retry.
        if(lease.addAttempted)windows.remove(lease.token);
        lease.closed=true;leases.remove(lease.key,lease);
        if(lease.controllerLinked)lease.controller.unlink(lease.death);
        if(lease.hostLinked)lease.host.unlink(lease.death);
    }
    private final class Lease {
        final K key;final Endpoint controller,host;final T token;final Runnable death;
        boolean controllerLinked,hostLinked,addAttempted,closed;
        Lease(K key,Endpoint controller,Endpoint host,T token){
            this.key=key;this.controller=controller;this.host=host;this.token=token;
            death=()->{try{close(this);}catch(Exception failure){onCleanupFailure(failure);}};
        }
    }
    private java.util.function.Consumer<Exception> cleanupFailure=failure->{};
    synchronized void setCleanupFailure(java.util.function.Consumer<Exception> listener){cleanupFailure=listener;}
    private void onCleanupFailure(Exception failure){cleanupFailure.accept(failure);}
}
