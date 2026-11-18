package dev.edgeflow.lsp;

import android.app.KeyguardManager;
import android.content.Context;
import android.os.*;
import dev.edgeflow.ToolHostContract;

/** Only the app hosting this lease may request the listed navigation actions. */
final class ToolControls extends Binder {
    private final Context context;
    private final int hostUid;
    private final java.util.function.BooleanSupplier active;
    private final SystemScene scene;
    private final NativeInputApi input;
    private final ToolWindowLease lease;
    private final java.util.concurrent.atomic.AtomicLong sequence=new java.util.concurrent.atomic.AtomicLong();
    private volatile long touching;
    private volatile boolean disposed;
    private final Runnable physical=this::cancelTouch;
    ToolControls(Context context,int hostUid,java.util.function.BooleanSupplier active,ToolWindowLease lease)throws ReflectiveOperationException{
        this.context=context;this.hostUid=hostUid;this.active=active;this.lease=lease;scene=new SystemScene(context);input=new NativeInputApi(context);TouchObservation.listen(physical);
    }
    @Override protected boolean onTransact(int code,Parcel data,Parcel reply,int flags)throws RemoteException{
        if(code!=ToolHostContract.GLOBAL_ACTION&&code!=ToolHostContract.FOREGROUND&&code!=ToolHostContract.TOUCH&&code!=ToolHostContract.CANCEL_TOUCH&&code!=ToolHostContract.VOLUME_OBSERVE)return super.onTransact(code,data,reply,flags);
        if(Binder.getCallingUid()!=hostUid)throw new SecurityException("Tool lease owner only");
        data.enforceInterface(ToolHostContract.CONTROL_DESCRIPTOR);
        if(code==ToolHostContract.VOLUME_OBSERVE){ResultReceiver callback=data.readTypedObject(ResultReceiver.CREATOR);data.enforceNoDataAvail();NativeVolumeState.watch(this,callback,()->!disposed);if(reply!=null)reply.writeNoException();return true;}
        if(code==ToolHostContract.CANCEL_TOUCH){data.enforceNoDataAvail();cancelTouch();if(reply!=null)reply.writeNoException();return true;}
        if(code==ToolHostContract.TOUCH){
            float[] points=data.createFloatArray();long duration=data.readLong();int count=data.readInt();ResultReceiver callback=data.readTypedObject(ResultReceiver.CREATOR);data.enforceNoDataAvail();
            if(callback==null)throw new IllegalArgumentException("Missing touch completion");
            long caller=Binder.clearCallingIdentity();
            try{
                if(points==null||points.length!=4||duration<1||duration>1000||count<1||count>2||!usable()||TouchObservation.touching())throw new IllegalStateException("Touch input is busy or stopped");
                long id=sequence.incrementAndGet(),base=SystemClock.uptimeMillis();touching=id;TouchObservation.remember(base,duration,count);
                ResultReceiver relay=new ResultReceiver(new Handler(Looper.getMainLooper())){protected void onReceiveResult(int code,Bundle state){if(touching==id)touching=0;callback.send(code,state);}};
                Parcel flatten=Parcel.obtain();try{relay.writeToParcel(flatten,0);flatten.setDataPosition(0);relay=ResultReceiver.CREATOR.createFromParcel(flatten);}finally{flatten.recycle();}
                int rotation=context.getSystemService(android.hardware.display.DisplayManager.class).getDisplay(0).getRotation();
                lease.touch(id,points,duration,count,base,scene.focus().key(),rotation,relay);
            }
            catch(RemoteException|RuntimeException failure){cancelTouch();Bundle result=new Bundle();result.putBoolean("completed",false);callback.send(0,result);}
            finally{Binder.restoreCallingIdentity(caller);}return true;
        }
        if(reply==null)return false;
        int action=code==ToolHostContract.GLOBAL_ACTION?data.readInt():0;data.enforceNoDataAvail();
        long identity=Binder.clearCallingIdentity();
        try {
            boolean usable=usable();
            if(code==ToolHostContract.FOREGROUND){String pkg=usable?scene.focus().pkg():"";reply.writeNoException();reply.writeString(pkg);return true;}
            boolean accepted=false;
            if(usable)accepted=switch(action){
                case 1->input.key(4);case 2->input.key(3);case 3->input.key(187);
                case 4->MappedActions.execute(context,input,"notifications");case 5->MappedActions.execute(context,input,"quick_settings");
                case android.accessibilityservice.AccessibilityService.GLOBAL_ACTION_POWER_DIALOG->MappedActions.execute(context,input,dev.edgeflow.BackendActions.POWER_MENU);
                case android.accessibilityservice.AccessibilityService.GLOBAL_ACTION_TOGGLE_SPLIT_SCREEN->input.splitScreen!=null&&input.splitScreen.enter(context,scene.focus().key())==null;
                case 8->input.key(223);case 9->input.key(120);default->false;
            };
            reply.writeNoException();reply.writeInt(accepted?1:0);return true;
        }catch(ReflectiveOperationException|RuntimeException failure){reply.writeException(new IllegalStateException("Tool control unavailable",failure));return true;}
        finally{Binder.restoreCallingIdentity(identity);}
    }
    private boolean usable(){return !disposed&&active.getAsBoolean()&&context.getSystemService(PowerManager.class).isInteractive()&&!context.getSystemService(KeyguardManager.class).isKeyguardLocked();}
    void cancelTouch(){long id=touching;if(id==0)return;touching=0;try{lease.cancelTouch(id);}catch(RemoteException|RuntimeException failure){android.util.Log.w("CHOTouch","Touch cancellation unavailable",failure);}}
    void dispose(){disposed=true;NativeVolumeState.remove(this);TouchObservation.unlisten(physical);cancelTouch();}
}
