package dev.edgeflow.lsp;

import android.content.*;
import android.os.*;

/** Binder requests run off SystemUI's UI thread; binding follows the saved input lifetime. */
final class ControllerConnection {
    private final Context context;
    private final Handler main;
    private final Runnable available;
    private final Object lock=new Object();
    private IBinder endpoint;
    private boolean wanted,bound;
    private String error="Controller connection timed out";
    private final Runnable bindTask=this::bind;
    private final ServiceConnection connection=new ServiceConnection(){
        @Override public void onServiceConnected(ComponentName name,IBinder service){
            synchronized(lock){endpoint=service;error="";lock.notifyAll();}
            available.run();
        }
        @Override public void onServiceDisconnected(ComponentName name){synchronized(lock){endpoint=null;lock.notifyAll();}}
        @Override public void onBindingDied(ComponentName name){
            synchronized(lock){endpoint=null;bound=false;lock.notifyAll();}
            try{context.unbindService(this);}catch(IllegalArgumentException ignored){}
            main.postDelayed(bindTask,200);
        }
        @Override public void onNullBinding(ComponentName name){synchronized(lock){endpoint=null;error="Controller returned no Binder";lock.notifyAll();}}
    };
    ControllerConnection(Context context,Handler main,Runnable available){this.context=context;this.main=main;this.available=available;}
    private void bind(){
        synchronized(lock){if(!wanted||bound)return;}
        try{
            boolean accepted=context.bindService(new Intent().setComponent(new ComponentName(BackendIdentity.module(),"dev.edgeflow.lsp.ControllerService")),connection,Context.BIND_AUTO_CREATE|Context.BIND_IMPORTANT);
            synchronized(lock){bound=accepted;if(!accepted)error="Controller service unavailable";lock.notifyAll();}
        }catch(RuntimeException failure){synchronized(lock){error=failure.toString();lock.notifyAll();}}
    }
    Bundle request(String operation,String profile){
        final IBinder service;
        synchronized(lock){
            wanted=true;main.post(bindTask);long deadline=SystemClock.uptimeMillis()+3500;
            while(endpoint==null&&SystemClock.uptimeMillis()<deadline)try{lock.wait(Math.max(1,deadline-SystemClock.uptimeMillis()));}
            catch(InterruptedException interrupted){Thread.currentThread().interrupt();throw new IllegalStateException("Controller request interrupted",interrupted);}
            service=endpoint;if(service==null)throw new IllegalStateException(error.isEmpty()?"Controller connection timed out":error);
        }
        Parcel input=Parcel.obtain(),output=Parcel.obtain();
        try{
            input.writeInterfaceToken(ControllerService.DESCRIPTOR);input.writeString(operation);input.writeString(profile);
            if(!service.transact(ControllerService.REQUEST,input,output,0))throw new IllegalStateException("Controller transaction rejected");
            output.readException();Bundle result=output.readBundle();if(result==null)throw new IllegalStateException("Empty controller reply");return result;
        }catch(RemoteException failure){synchronized(lock){if(endpoint==service)endpoint=null;}throw new IllegalStateException("Controller disconnected",failure);}
        finally{input.recycle();output.recycle();}
    }
    void release(){
        main.removeCallbacks(bindTask);boolean unbind;
        synchronized(lock){wanted=false;endpoint=null;unbind=bound;bound=false;lock.notifyAll();}
        if(unbind)try{context.unbindService(connection);}catch(IllegalArgumentException ignored){}
    }
}
