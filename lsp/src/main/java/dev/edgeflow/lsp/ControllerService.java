package dev.edgeflow.lsp;

import android.app.Service;
import android.content.Intent;
import android.os.*;

/** A platform-permission-protected bound control channel; never renders or reads input. */
public final class ControllerService extends Service {
    static final String DESCRIPTOR="dev.edgeflow.lsp.controller.v1";
    static final int REQUEST=IBinder.FIRST_CALL_TRANSACTION;
    private final Binder binder=new Binder(){
        @Override protected boolean onTransact(int code,Parcel data,Parcel reply,int flags)throws RemoteException{
            if(code==IBinder.INTERFACE_TRANSACTION){reply.writeString(DESCRIPTOR);return true;}
            if(code!=REQUEST)return super.onTransact(code,data,reply,flags);
            data.enforceInterface(DESCRIPTOR);
            int caller=Binder.getCallingUid();
            try{
                if(caller!=android.os.Process.myUid()&&caller!=getPackageManager().getApplicationInfo("com.android.systemui",0).uid)throw new SecurityException("SystemUI controller only");
            }catch(android.content.pm.PackageManager.NameNotFoundException error){throw new SecurityException(error);}
            String operation=data.readString(),profile=data.readString();data.enforceNoDataAvail();
            if(!"refresh".equals(operation)&&!"sync".equals(operation)&&!"disable".equals(operation))throw new SecurityException("Unsupported controller operation");
            long identity=Binder.clearCallingIdentity();
            try{
                Bundle result=getContentResolver().call(PersistentBridge.uri(),operation,profile,null);
                if(result==null)throw new IllegalStateException("No controller state");
                reply.writeNoException();reply.writeBundle(result);return true;
            }finally{Binder.restoreCallingIdentity(identity);}
        }
    };
    @Override public IBinder onBind(Intent intent){return binder;}
}
