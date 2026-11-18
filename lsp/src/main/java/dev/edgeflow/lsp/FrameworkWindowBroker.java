package dev.edgeflow.lsp;

import android.content.Context;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.os.*;
import android.util.Log;
import android.view.WindowManager;
import java.lang.reflect.Method;
import java.util.HashMap;
import java.util.Map;

/** Narrow system_server capability: grant and forcibly remove CHO's own tool windows. */
final class FrameworkWindowBroker {
    private final ClassLoader loader;
    private final String modulePackage;
    private final boolean diagnostics;
    private WindowLeaseStore<IBinder,IBinder> leases;
    private Context context;
    private final Map<IBinder,TouchOwner> touches=new java.util.concurrent.ConcurrentHashMap<>();
    FrameworkWindowBroker(ClassLoader loader,ApplicationInfo module){
        this.loader=loader;modulePackage=module.packageName;
        diagnostics=BackendIdentity.isDebugPilot(module);
    }
    boolean transact(Parcel data,Parcel reply,int flags){
        // No one-way operations: the caller must receive the actual result.
        if(reply==null||(flags&IBinder.FLAG_ONEWAY)!=0)return false;
        try {
            Context c=context();int caller=Binder.getCallingUid();
            int sysui=c.getPackageManager().getApplicationInfo("com.android.systemui",0).uid;
            if(caller!=sysui)throw new SecurityException("Only SystemUI can manage tool windows");
            data.enforceInterface(WindowLeaseProtocol.DESCRIPTOR);
            int operation=data.readInt();
            if(operation==WindowLeaseProtocol.STATUS){
                data.enforceNoDataAvail();long identity=Binder.clearCallingIdentity();
                try{store();reply.writeNoException();reply.writeInt(leases.size());}finally{Binder.restoreCallingIdentity(identity);}
                return true;
            }
            IBinder controller=data.readStrongBinder();
            if(controller==null)throw new IllegalArgumentException("Missing controller lifetime");
            if(operation==WindowLeaseProtocol.TOUCH||operation==WindowLeaseProtocol.CANCEL_TOUCH){
                long id=data.readLong();float[] points=null;long duration=0,base=0;int count=0,rotation=0;String focus=null;ResultReceiver callback=null;
                if(operation==WindowLeaseProtocol.TOUCH){points=data.createFloatArray();duration=data.readLong();count=data.readInt();base=data.readLong();focus=data.readString();rotation=data.readInt();callback=data.readTypedObject(ResultReceiver.CREATOR);}
                data.enforceNoDataAvail();
                long identity=Binder.clearCallingIdentity();
                try{
                    IBinder token=store().liveToken(controller);TouchOwner owner=token==null?null:touches.get(token);
                    if(owner==null)throw new SecurityException("No live tool window lease");
                    if(operation==WindowLeaseProtocol.CANCEL_TOUCH)owner.cancel(id);
                    else{
                        if(callback==null||points==null||points.length!=4||duration<1||duration>1000||count<1||count>2||focus==null||focus.isEmpty()||base<=0||Math.abs(SystemClock.uptimeMillis()-base)>1000)throw new IllegalArgumentException("Invalid touch request");
                        owner.start(c,id,points,duration,count,base,focus,rotation,callback);
                    }
                    reply.writeNoException();return true;
                }finally{Binder.restoreCallingIdentity(identity);}
            }
            IBinder host=null;String hostPackage=null;
            if(operation==WindowLeaseProtocol.GRANT){host=data.readStrongBinder();hostPackage=data.readString();}
            else if(operation!=WindowLeaseProtocol.REVOKE)throw new IllegalArgumentException("Unknown window operation");
            data.enforceNoDataAvail();
            if(operation==WindowLeaseProtocol.GRANT){
                if(host==null)throw new IllegalArgumentException("Missing host lifetime");
                if(!"dev.edgeflow".equals(hostPackage)&&!(diagnostics&&"dev.edgeflow.lspfixture".equals(hostPackage)))throw new SecurityException("Not a CHO tool host");
                PackageManager pm=c.getPackageManager();
                ApplicationInfo app=pm.getApplicationInfo(hostPackage,0);
                if(app.uid/100000!=0||pm.checkSignatures(modulePackage,hostPackage)!=PackageManager.SIGNATURE_MATCH)throw new SecurityException("Tool host identity mismatch");
            }
            long identity=Binder.clearCallingIdentity();
            try {
                WindowLeaseStore<IBinder,IBinder> store=store();
                if(operation==WindowLeaseProtocol.GRANT){
                    IBinder token=store.grant(controller,new Lifetime(controller),new Lifetime(host));
                    reply.writeNoException();reply.writeStrongBinder(token);
                }else{boolean removed=store.revoke(controller);reply.writeNoException();reply.writeInt(removed?1:0);}
            }finally{Binder.restoreCallingIdentity(identity);}
        }catch(Exception|LinkageError failure){
            Log.w("CHOWindowBroker","Window request rejected",failure);
            reply.writeException(failure instanceof SecurityException?(SecurityException)failure:new IllegalStateException("Tool window operation failed",failure));
        }
        return true;
    }
    private synchronized Context context()throws ReflectiveOperationException {
        if(context==null){Class<?> thread=Class.forName("android.app.ActivityThread",false,loader);Object current=thread.getMethod("currentActivityThread").invoke(null);context=(Context)thread.getMethod("getSystemContext").invoke(current);}
        if(context==null)throw new IllegalStateException("System context unavailable");return context;
    }
    private synchronized WindowLeaseStore<IBinder,IBinder> store()throws ReflectiveOperationException {
        if(leases!=null)return leases;
        Class<?> api=Class.forName("com.android.server.wm.WindowManagerInternal",false,loader);
        Object wm=Class.forName("com.android.server.LocalServices",false,loader).getMethod("getService",Class.class).invoke(null,api);
        if(wm==null)throw new IllegalStateException("Window service unavailable");
        Method add=api.getMethod("addWindowToken",IBinder.class,int.class,int.class,Bundle.class);
        Method remove=api.getMethod("removeWindowToken",IBinder.class,boolean.class,boolean.class,int.class);
        leases=new WindowLeaseStore<>(new WindowLeaseStore.Windows<>(){
            public IBinder createToken(){return new Binder();}
            public void add(IBinder token)throws Exception{add.invoke(wm,token,WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,0,null);touches.put(token,new TouchOwner());}
            public void remove(IBinder token)throws Exception{TouchOwner owner=touches.remove(token);if(owner!=null)owner.close();remove.invoke(wm,token,true,false,0);}
        },4);
        leases.setCleanupFailure(failure->Log.e("CHOWindowBroker","Tool window cleanup failed",failure));
        return leases;
    }
    private static final class TouchOwner {
        private final TouchRequestGate gate=new TouchRequestGate();private ToolTouchInjector injector;
        synchronized void start(Context c,long id,float[] points,long duration,int count,long base,String focus,int rotation,ResultReceiver callback)throws ReflectiveOperationException{
            if(!gate.start(id)){Bundle state=new Bundle();state.putBoolean("completed",false);callback.send(0,state);return;}
            if(injector==null)injector=new ToolTouchInjector(c,()->true);
            injector.start(points,duration,count,base,focus,rotation,()->gate.live(id),callback);
        }
        synchronized void cancel(long id){if(gate.cancel(id)&&injector!=null)injector.cancel();}
        synchronized void close(){gate.close();if(injector!=null)injector.cancel();}
    }
    private static final class Lifetime implements WindowLeaseStore.Endpoint {
        private final IBinder binder;
        private final Map<Runnable,IBinder.DeathRecipient> deaths=new HashMap<>();
        Lifetime(IBinder binder){this.binder=binder;}
        public void link(Runnable callback)throws RemoteException{
            IBinder.DeathRecipient death=callback::run;binder.linkToDeath(death,0);deaths.put(callback,death);
        }
        public void unlink(Runnable callback){IBinder.DeathRecipient death=deaths.remove(callback);if(death!=null)binder.unlinkToDeath(death,0);}
        public boolean alive(){return binder.isBinderAlive();}
    }
}
