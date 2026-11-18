package dev.edgeflow.lsp;

import android.os.*;

/** Framework-owned window capability, revoked when either endpoint disconnects. */
final class ToolWindowLease implements AutoCloseable {
    final IBinder token;
    private final IBinder controller=new Binder();
    private final IBinder manager;
    private boolean closed;
    ToolWindowLease(IBinder host,String hostPackage)throws ReflectiveOperationException,RemoteException {
        manager=manager();Parcel data=Parcel.obtain(),reply=Parcel.obtain();
        try{
            data.writeInterfaceToken(WindowLeaseProtocol.DESCRIPTOR);data.writeInt(WindowLeaseProtocol.GRANT);
            data.writeStrongBinder(controller);data.writeStrongBinder(host);data.writeString(hostPackage);
            transact(manager,data,reply);token=reply.readStrongBinder();
            if(token==null)throw new IllegalStateException("No tool window token");
        }finally{data.recycle();reply.recycle();}
    }
    void touch(long id,float[] points,long duration,int count,long base,String focus,int rotation,ResultReceiver callback)throws RemoteException{
        Parcel data=Parcel.obtain(),reply=Parcel.obtain();
        try{data.writeInterfaceToken(WindowLeaseProtocol.DESCRIPTOR);data.writeInt(WindowLeaseProtocol.TOUCH);data.writeStrongBinder(controller);data.writeLong(id);data.writeFloatArray(points);data.writeLong(duration);data.writeInt(count);data.writeLong(base);data.writeString(focus);data.writeInt(rotation);data.writeTypedObject(callback,0);transact(manager,data,reply);}
        finally{data.recycle();reply.recycle();}
    }
    void cancelTouch(long id)throws RemoteException{
        Parcel data=Parcel.obtain(),reply=Parcel.obtain();
        try{data.writeInterfaceToken(WindowLeaseProtocol.DESCRIPTOR);data.writeInt(WindowLeaseProtocol.CANCEL_TOUCH);data.writeStrongBinder(controller);data.writeLong(id);transact(manager,data,reply);}
        finally{data.recycle();reply.recycle();}
    }
    @Override public void close(){
        if(closed)return;
        Parcel data=Parcel.obtain(),reply=Parcel.obtain();
        try{
            data.writeInterfaceToken(WindowLeaseProtocol.DESCRIPTOR);data.writeInt(WindowLeaseProtocol.REVOKE);data.writeStrongBinder(controller);
            transact(manager,data,reply);reply.readInt();closed=true;
        }catch(RemoteException|RuntimeException error){android.util.Log.w("CHOToolWindow","Token removal failed",error);}
        finally{data.recycle();reply.recycle();}
    }
    static int activeCount()throws ReflectiveOperationException,RemoteException {
        Parcel data=Parcel.obtain(),reply=Parcel.obtain();
        try{data.writeInterfaceToken(WindowLeaseProtocol.DESCRIPTOR);data.writeInt(WindowLeaseProtocol.STATUS);transact(manager(),data,reply);return reply.readInt();}
        finally{data.recycle();reply.recycle();}
    }
    private static IBinder manager()throws ReflectiveOperationException{
        return ((IInterface)Class.forName("android.app.ActivityManager").getMethod("getService").invoke(null)).asBinder();
    }
    private static void transact(IBinder manager,Parcel data,Parcel reply)throws RemoteException {
        if(!manager.transact(WindowLeaseProtocol.TRANSACTION,data,reply,0))throw new IllegalStateException("System framework window support is unavailable");
        reply.readException();
    }
}
