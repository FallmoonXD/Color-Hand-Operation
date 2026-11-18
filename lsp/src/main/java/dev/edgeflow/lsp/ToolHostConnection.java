package dev.edgeflow.lsp;

import android.content.*;
import android.os.*;
import dev.edgeflow.ToolHostContract;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/** One app connection and window lease per input session; tools can coexist on it. */
final class ToolHostConnection implements AutoCloseable {
    private static final java.util.concurrent.atomic.AtomicLong serial=new java.util.concurrent.atomic.AtomicLong();
    private record Request(long id,String action,float[] anchor,String focus,int rotation,ResultReceiver reply){}
    private final Context context;
    private final Handler main;
    private final java.util.function.BooleanSupplier allowed;
    private final SystemScene scene;
    private final ExecutorService worker=Executors.newSingleThreadExecutor(r->new Thread(r,"CHO-tool-host"));
    private volatile boolean closed;
    private volatile boolean geometryReady=true;
    private boolean bound,ready,watchingScreen,replied;
    private Request current;
    private volatile ToolWindowLease lease;
    private IBinder endpoint;
    private volatile ToolControls controls;
    private final BroadcastReceiver screen=new BroadcastReceiver(){public void onReceive(Context c,Intent intent){close();}};
    private final Runnable timeout=()->fail("Tool host did not attach its window");
    private final ServiceConnection connection=new ServiceConnection(){
        public void onServiceConnected(ComponentName name,IBinder binder){
            if(closed)return;
            endpoint=binder;
            worker.execute(()->{
                try{
                    if(closed)return;
                    lease=new ToolWindowLease(binder,ToolHostContract.PACKAGE);
                    int uid=context.getPackageManager().getApplicationInfo(ToolHostContract.PACKAGE,0).uid;
                    ToolControls commands=new ToolControls(context,uid,()->!closed&&geometryReady&&allowed.getAsBoolean(),lease);
                    controls=commands;
                    if(closed)commands.dispose();
                    main.post(()->{if(!closed){controls=commands;ready=true;if(current!=null)sendCurrent();}});
                }catch(Exception|LinkageError failure){main.post(()->fail(failure.toString()));}
            });
        }
        public void onServiceDisconnected(ComponentName name){fail("Tool host disconnected");}
        public void onBindingDied(ComponentName name){fail("Tool host binding ended");}
        public void onNullBinding(ComponentName name){fail("Tool host returned no endpoint");}
    };
    ToolHostConnection(Context context,Handler main,String action,float[] anchor,ResultReceiver result){
        this(context,main,action,anchor,result,()->true);
    }
    ToolHostConnection(Context context,Handler main,String action,float[] anchor,ResultReceiver result,java.util.function.BooleanSupplier allowed){
        this.context=context;this.main=main;this.allowed=allowed;scene=new SystemScene(context);if(action!=null)setRequest(action,anchor,result);
    }
    static boolean available(Context context){
        try{
            var service=context.getPackageManager().getServiceInfo(new ComponentName(ToolHostContract.PACKAGE,ToolHostContract.SERVICE),0);
            return service.enabled&&service.applicationInfo.enabled&&service.exported&&"android.permission.STATUS_BAR_SERVICE".equals(service.permission)&&ToolWindowLease.activeCount()>=0;
        }catch(ReflectiveOperationException|RemoteException|android.content.pm.PackageManager.NameNotFoundException|RuntimeException failure){return false;}
    }
    boolean isClosed(){return closed;}
    void geometryChanging(){geometryReady=false;replied=true;main.removeCallbacks(timeout);ToolControls owner=controls;if(owner!=null)owner.cancelTouch();}
    void geometryReady(){
        if(closed)return;geometryReady=true;if(!ready||lease==null)return;
        IBinder target=endpoint,token=lease.token;
        worker.execute(()->{if(closed)return;Parcel data=Parcel.obtain();try{data.writeInterfaceToken(ToolHostContract.DESCRIPTOR);data.writeStrongBinder(token);target.transact(ToolHostContract.GEOMETRY,data,null,IBinder.FLAG_ONEWAY);}catch(RemoteException failure){main.post(()->fail("Tool geometry disconnected"));}finally{data.recycle();}});
    }
    void show(String action,float[] anchor,ResultReceiver result){
        if(closed)return;setRequest(action,anchor,result);start();
    }
    private void setRequest(String action,float[] anchor,ResultReceiver result){
        replied=false;
        long id=serial.updateAndGet(previous->Math.max(previous+1,SystemClock.elapsedRealtimeNanos()));
        current=new Request(id,action,anchor.clone(),scene.focus().key(),rotation(),result);
    }
    private int rotation(){return context.getSystemService(android.hardware.display.DisplayManager.class).getDisplay(0).getRotation();}
    private boolean valid(Request request){return !closed&&allowed.getAsBoolean()&&request.rotation==rotation()&&request.focus.equals(scene.focus().key());}
    void start(){
        if(closed)return;
        if(!allowed.getAsBoolean()||current!=null&&!valid(current)){fail("Gesture target changed before binding");return;}
        main.removeCallbacks(timeout);if(current!=null)main.postDelayed(timeout,3500);
        if(ready){if(current!=null)sendCurrent();return;}
        if(bound)return;
        try{
            if(!watchingScreen){IntentFilter events=new IntentFilter(Intent.ACTION_SCREEN_OFF);events.addAction("android.intent.action.USER_SWITCHED");context.registerReceiver(screen,events,null,main,Context.RECEIVER_EXPORTED);watchingScreen=true;}
            bound=context.bindService(new Intent().setComponent(new ComponentName(ToolHostContract.PACKAGE,ToolHostContract.SERVICE)),connection,Context.BIND_AUTO_CREATE|Context.BIND_IMPORTANT);
            if(!bound)fail("Tool host service is unavailable");
        }catch(RuntimeException failure){fail(failure.toString());}
    }
    private void sendCurrent(){
        Request request=current;IBinder target=endpoint,commands=controls;
        if(!valid(request)){fail("Gesture target changed before tool display");return;}
        worker.execute(()->{
            if(closed)return;
            try{
                Parcel data=Parcel.obtain();
                try{
                    data.writeInterfaceToken(ToolHostContract.DESCRIPTOR);data.writeStrongBinder(lease.token);data.writeStrongBinder(commands);data.writeLong(request.id);data.writeInt(request.rotation);data.writeString(request.action);data.writeFloatArray(request.anchor);
                    ResultReceiver callback=new ResultReceiver(main){protected void onReceiveResult(int code,Bundle state){finish(request,state);}};
                    Parcel flatten=Parcel.obtain();
                    try{callback.writeToParcel(flatten,0);flatten.setDataPosition(0);callback=ResultReceiver.CREATOR.createFromParcel(flatten);}finally{flatten.recycle();}
                    data.writeTypedObject(callback,0);
                    if(!target.transact(ToolHostContract.SHOW,data,null,IBinder.FLAG_ONEWAY))throw new IllegalStateException("Tool host rejected show");
                }finally{data.recycle();}
            }catch(RemoteException|RuntimeException failure){main.post(()->{if(current==request)fail(failure.toString());});}
        });
    }
    void motion(int action,float x,float y){
        if(closed||!ready||current==null)return;
        Request request=current;IBinder target=endpoint;IBinder token=lease.token;
        worker.execute(()->{
            if(closed)return;Parcel data=Parcel.obtain();
            try{data.writeInterfaceToken(ToolHostContract.DESCRIPTOR);data.writeStrongBinder(token);data.writeLong(request.id);data.writeInt(action);data.writeFloat(x);data.writeFloat(y);target.transact(ToolHostContract.MOTION,data,null,IBinder.FLAG_ONEWAY);}
            catch(RemoteException failure){main.post(()->fail("Tool motion disconnected"));}
            finally{data.recycle();}
        });
    }
    private void finish(Request request,Bundle state){
        if(closed||request!=current||replied)return;replied=true;
        main.removeCallbacks(timeout);
        if(state.getBoolean("tool_shown")&&!valid(request)){state=new Bundle();state.putString("tool_error","Gesture target changed before tool display");}
        if(request.reply!=null)request.reply.send(0,state);
        if(!state.getBoolean("tool_shown")&&!state.getBoolean("action_dispatched"))close();
    }
    private void fail(String error){
        if(closed)return;Bundle state=new Bundle();state.putString("tool_error",error);if(current!=null)finish(current,state);close();
    }
    @Override public void close(){
        if(closed)return;closed=true;main.removeCallbacks(timeout);
        ToolControls owner=controls;if(owner!=null)owner.dispose();
        if(watchingScreen){watchingScreen=false;context.unregisterReceiver(screen);}
        IBinder target=endpoint;
        worker.execute(()->{
            ToolControls commands=controls;if(commands!=null)commands.dispose();
            IBinder ending=lease==null?null:lease.token;
            if(lease!=null){lease.close();lease=null;}
            if(target!=null){Parcel data=Parcel.obtain();try{data.writeInterfaceToken(ToolHostContract.DESCRIPTOR);data.writeStrongBinder(ending);target.transact(ToolHostContract.CLOSE,data,null,IBinder.FLAG_ONEWAY);}catch(RemoteException ignored){}finally{data.recycle();}}
        });worker.shutdown();
        if(bound){bound=false;try{context.unbindService(connection);}catch(IllegalArgumentException ignored){}}
    }
}
