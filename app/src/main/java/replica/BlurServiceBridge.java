package replica;

import android.os.*;
import android.content.*;
import java.lang.reflect.*;

/** Runs as root. Match SystemUI's action/package binding to share its service lifetime. */
public final class BlurServiceBridge {
    public static void main(String[] args)throws Exception {
        if(args.length!=2||!args[0].matches("[A-Za-z0-9_]+(?:\\.[A-Za-z0-9_]+)+\\.material(?:\\.service)?")||!args[1].matches("[0-9]+"))throw new IllegalArgumentException("Expected app material provider authority and host user");
        final int user=Integer.parseInt(args[1]);
        final String authority=args[0],pkg=authority.replaceFirst("\\.material(?:\\.service)?$","");
        Class<?> amc=Class.forName("android.app.IActivityManager");
        Object am=Class.forName("android.app.ActivityManager").getMethod("getService").invoke(null);
        Binder token=new Binder();
        Object holder=amc.getMethod("getContentProviderExternal",String.class,int.class,IBinder.class,String.class)
            .invoke(am,authority,user,token,"edgeflow-live-material");
        if(holder==null)throw new IllegalStateException("Material provider unavailable for host user "+user);
        Class<?> conn=null;
        Object connection=null;
        try {
        Object provider=holder.getClass().getField("provider").get(holder);
        Class<?> ac=Class.forName("android.content.AttributionSource");
        Object builder=Class.forName("android.content.AttributionSource$Builder").getConstructor(int.class).newInstance(0);
        builder=builder.getClass().getMethod("setPackageName",String.class).invoke(builder,"com.android.shell");
        final Object identity=builder.getClass().getMethod("build").invoke(builder);
        Method call=Class.forName("android.content.IContentProvider").getMethod("call",ac,String.class,String.class,String.class,Bundle.class);
        conn=Class.forName("android.app.IServiceConnection");
            Bundle data=(Bundle)call.invoke(provider,identity,authority,"thread",null,null);
            IBinder thread=data.getBinder("thread");
            Object caller=Class.forName("android.app.IApplicationThread$Stub").getMethod("asInterface",IBinder.class).invoke(null,thread);
            java.util.concurrent.CountDownLatch ready=new java.util.concurrent.CountDownLatch(1);
            java.util.concurrent.atomic.AtomicBoolean lost=new java.util.concurrent.atomic.AtomicBoolean();
            Binder callback=new Binder(){protected boolean onTransact(int code,Parcel data,Parcel reply,int flags)throws RemoteException{
                if(code==1){data.enforceInterface("android.app.IServiceConnection");
                    data.readTypedObject(ComponentName.CREATOR);IBinder service=data.readStrongBinder();
                    try{
                        if(service!=null){
                            if(!"com.oplus.blur.session.IBlurService".equals(service.getInterfaceDescriptor()))throw new IllegalStateException("Unexpected native blur interface");
                            service.linkToDeath(()->lost.set(true),0);
                        }else lost.set(true);
                        Bundle b=new Bundle();b.putBinder("service",service);call.invoke(provider,identity,authority,"service",null,b);if(service!=null)ready.countDown();
                    }
                    catch(Exception e){System.out.println("DELIVERY_FAILED "+e);}
                    return true;
                }return super.onTransact(code,data,reply,flags);
            }};
            connection=Class.forName("android.app.IServiceConnection$Stub").getMethod("asInterface",IBinder.class).invoke(null,callback);
            Bundle retained=new Bundle();retained.putBinder("connection",callback);
            call.invoke(provider,identity,authority,"connection",null,retained);
            Object bound=amc.getMethod("bindService",Class.forName("android.app.IApplicationThread"),IBinder.class,Intent.class,String.class,conn,long.class,String.class,int.class)
                .invoke(am,caller,null,new Intent("com.oplus.BlurService").setPackage("com.oplus.blur"),null,connection,1L,pkg,user);
            System.out.println("BOUND "+authority+" result="+bound);
            if(!ready.await(8,java.util.concurrent.TimeUnit.SECONDS))throw new IllegalStateException("Native bind timed out");
            if(lost.get())throw new IllegalStateException("Native service disconnected during startup");
            System.out.println("NATIVE_BLUR_READY");System.out.flush();
            long idle=0;
            while(thread.isBinderAlive()){
                if(lost.get())throw new IllegalStateException("Native service disconnected");
                Bundle stats=(Bundle)call.invoke(provider,identity,authority,"stats",null,null);
                if(stats.getInt("active")==0){if(idle==0)idle=SystemClock.elapsedRealtime();if(SystemClock.elapsedRealtime()-idle>15000){System.out.println("NATIVE_BLUR_IDLE");break;}}
                else idle=0;
                Thread.sleep(500);
            }
        } finally {
            if(connection!=null)try{amc.getMethod("unbindService",conn).invoke(am,connection);}catch(Exception ignored){}
            try{amc.getMethod("removeContentProviderExternal",String.class,IBinder.class).invoke(am,authority,token);}catch(Exception ignored){}
            System.out.println("UNBOUND "+authority);
        }
    }
}
