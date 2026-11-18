package dev.edgeflow;

import android.app.KeyguardManager;
import android.app.Service;
import android.content.Intent;
import android.os.*;
import replica.ToolsOverlay;
import replica.WidgetOverlay;

/** App-owned tool UI; privileged input uses a revocable typed Binder. */
public final class LspToolHostService extends Service implements replica.OverlayWindowToken,ToolActionHost,replica.NativeVolumeSource {
    private static LspToolHostService instance;
    private final Handler main=new Handler(Looper.getMainLooper());
    private final java.util.concurrent.ExecutorService actions=java.util.concurrent.Executors.newSingleThreadExecutor();
    private volatile IBinder token,control;
    private boolean bound;
    private long latest;
    private ResultReceiver pending;
    private String pendingAction;
    private java.util.function.Consumer<Bundle> volumeListener;
    private final Runnable timeout=()->finish(false,"Tool window did not attach");
    private final Binder endpoint=new Binder(){
        @Override protected boolean onTransact(int code,Parcel data,Parcel reply,int flags)throws RemoteException{
            if(code==IBinder.INTERFACE_TRANSACTION){if(reply!=null)reply.writeString(ToolHostContract.DESCRIPTOR);return true;}
            if(code!=ToolHostContract.SHOW&&code!=ToolHostContract.CLOSE&&code!=ToolHostContract.MOTION&&code!=ToolHostContract.GEOMETRY)return super.onTransact(code,data,reply,flags);
            try{if(Binder.getCallingUid()!=getPackageManager().getApplicationInfo("com.android.systemui",0).uid)throw new SecurityException("SystemUI tool controller only");}
            catch(android.content.pm.PackageManager.NameNotFoundException failure){throw new SecurityException(failure);}
            data.enforceInterface(ToolHostContract.DESCRIPTOR);
            IBinder grant=data.readStrongBinder();
            if(code==ToolHostContract.CLOSE){data.enforceNoDataAvail();main.post(()->{if(token==grant)clear();});}
            else if(code==ToolHostContract.GEOMETRY){data.enforceNoDataAvail();main.post(()->{if(token==grant)reconfigure();});}
            else if(code==ToolHostContract.MOTION){
                long request=data.readLong();int action=data.readInt();float x=data.readFloat(),y=data.readFloat();data.enforceNoDataAvail();
                if(!Float.isFinite(x)||!Float.isFinite(y))return true;
                if(action!=android.view.MotionEvent.ACTION_MOVE&&action!=android.view.MotionEvent.ACTION_UP&&action!=android.view.MotionEvent.ACTION_CANCEL)return true;
                main.post(()->{if(token==grant&&latest==request)QuickLauncherOverlay.onGestureMotion(action,x,y);});
            }else {
                IBinder commands=data.readStrongBinder();long request=data.readLong();int rotation=data.readInt();String action=data.readString();float[] anchor=data.createFloatArray();
                ResultReceiver result=data.readTypedObject(ResultReceiver.CREATOR);data.enforceNoDataAvail();
                if(grant==null||commands==null||anchor==null||anchor.length!=4||result==null||request<=0)throw new IllegalArgumentException("Incomplete tool request");
                if(!ToolHostContract.supports(action))throw new IllegalArgumentException("Tool is not connected to LSP");
                for(float coordinate:anchor)if(!Float.isFinite(coordinate))throw new IllegalArgumentException("Invalid tool anchor");
                main.post(()->show(grant,commands,request,rotation,action,anchor,result));
            }
            if(reply!=null)reply.writeNoException();return true;
        }
    };
    @Override public void onCreate(){super.onCreate();instance=this;}
    public static void prepareMaterials(){LspToolHostService service=instance;if(service!=null)replica.ColorOsSurface.keepWarm(service,service.bound&&RootAccess.previouslyGranted(service));}
    @Override public IBinder onBind(Intent intent){bound=true;prepareMaterials();return endpoint;}
    @Override public boolean onUnbind(Intent intent){bound=false;prepareMaterials();clear();return false;}
    @Override public void onDestroy(){bound=false;prepareMaterials();if(instance==this)instance=null;clear();actions.shutdownNow();super.onDestroy();}
    @Override public void onConfigurationChanged(android.content.res.Configuration config){super.onConfigurationChanged(config);if(token!=null)reconfigure();}
    private void reconfigure(){
        cancelTouch();ToolsOverlay.dismissAll();WidgetOverlay.dismissForDisplayChange();replica.VolumeOverlay.dismissAll();ActionExecutor.release(this);RecentTaskList.release(this);
        var area=HandleDisplayArea.current(this);int density=getResources().getDisplayMetrics().densityDpi;
        NavigationOverlay.displayChanged(area,density);TouchpadOverlay.displayChanged(area,density);
    }
    @Override public IBinder overlayWindowToken(){return token;}
    @Override public void overlayAttached(){main.post(()->{if(pending!=null&&showing(pendingAction))finish(true,null);});}
    private void show(IBinder grant,IBinder commands,long request,int rotation,String action,float[] anchor,ResultReceiver result){
        if(request<=latest){Bundle rejected=new Bundle();rejected.putString("tool_error","Superseded tool request");result.send(0,rejected);return;}
        if(getSystemService(android.hardware.display.DisplayManager.class).getDisplay(0).getRotation()!=rotation){Bundle rejected=new Bundle();rejected.putString("tool_error","Display rotated before tool display");result.send(0,rejected);return;}
        latest=request;main.removeCallbacks(timeout);pending=null;
        try {
            if(!bound||!getSystemService(PowerManager.class).isInteractive()||getSystemService(KeyguardManager.class).isKeyguardLocked())throw new IllegalStateException("Tool host is not available");
            if(token!=grant)clear();token=grant;control=commands;pending=result;pendingAction=action;
            replica.NativeState.setGesture(anchor);
            if("edit_launcher".equals(action))QuickLauncherOverlay.openEditor(this,anchor);else ActionExecutor.execute(this,action,anchor);
            boolean widgetSetup="widgets".equals(action)&&replica.WidgetActivity.setupInProgress();
            if(BackendActions.command(action)||widgetSetup){
                pending=null;Bundle state=new Bundle();state.putInt("tool_host_uid",android.os.Process.myUid());state.putString("action",action);state.putBoolean("widget_setup",widgetSetup);state.putBoolean("action_dispatched",true);result.send(0,state);return;
            }
            if(showing(action))finish(true,null);else main.postDelayed(timeout,2800);
        }catch(RuntimeException failure){
            if(pending==null){pending=result;pendingAction=action;}
            finish(false,failure.toString());clear();
        }
    }
    private static boolean showing(String action){
        if(action==null)return false;
        return switch(action){
            case "quick_tools"->ToolsOverlay.isShowing();case "widgets"->WidgetOverlay.isShowing();
            case "quick_launcher","edit_launcher"->QuickLauncherOverlay.isShowing();case "task_switcher"->TaskSwitcherOverlay.isShowing();
            case "navigation_panel"->NavigationOverlay.isShowing();case "keyboard_shortcuts"->KeyboardShortcutsOverlay.isShowing();
            case "virtual_touchpad"->TouchpadOverlay.isShowing();
            case "volume_up","volume_down"->replica.VolumeOverlay.isShowing();
            case "screen_timeout","app_launcher"->ActionExecutor.dialogShowing();
            default->action.startsWith("folder:")&&QuickLauncherOverlay.isShowing();
        };
    }
    private void finish(boolean shown,String error){
        main.removeCallbacks(timeout);ResultReceiver result=pending;pending=null;if(result==null)return;
        Bundle state=new Bundle();state.putInt("tool_host_uid",android.os.Process.myUid());state.putString("tool",pendingAction);state.putBoolean("tool_shown",shown);
        if(error!=null)state.putString("tool_error",error);result.send(0,state);
    }
    private void clear(){
        main.removeCallbacks(timeout);pending=null;if(token==null)return;cancelTouch();token=null;control=null;
        ToolsOverlay.dismissAll();WidgetOverlay.dismissAll();NavigationOverlay.dismissAll();TouchpadOverlay.dismissAll();replica.VolumeOverlay.dismissAll();ActionExecutor.release(this);RecentTaskList.release(this);
    }
    @Override public void observeVolume(java.util.function.Consumer<Bundle> listener){
        volumeListener=listener;IBinder owner=control;if(owner==null)return;
        ResultReceiver callback=listener==null?null:new ResultReceiver(main){protected void onReceiveResult(int code,Bundle data){if(control==owner&&volumeListener==listener)listener.accept(data);}};
        if(callback!=null){Parcel parcel=Parcel.obtain();try{callback.writeToParcel(parcel,0);parcel.setDataPosition(0);callback=ResultReceiver.CREATOR.createFromParcel(parcel);}finally{parcel.recycle();}}
        ResultReceiver target=callback;
        actions.execute(()->{Parcel data=Parcel.obtain();try{if(control==owner){data.writeInterfaceToken(ToolHostContract.CONTROL_DESCRIPTOR);data.writeTypedObject(target,0);owner.transact(ToolHostContract.VOLUME_OBSERVE,data,null,IBinder.FLAG_ONEWAY);}}catch(RemoteException failure){android.util.Log.w("CHOTools","Volume observation ended",failure);}finally{data.recycle();}});
    }
    @Override public boolean globalAction(int action){
        if(!java.util.Set.of(1,2,3,4,5,android.accessibilityservice.AccessibilityService.GLOBAL_ACTION_POWER_DIALOG,android.accessibilityservice.AccessibilityService.GLOBAL_ACTION_TOGGLE_SPLIT_SCREEN,8,9).contains(action)||control==null)return false;
        IBinder owner=control;
        actions.execute(()->{
            Parcel data=Parcel.obtain(),reply=Parcel.obtain();boolean accepted=false;
            try{
                if(control!=owner)return;
                data.writeInterfaceToken(ToolHostContract.CONTROL_DESCRIPTOR);data.writeInt(action);
                if(owner.transact(ToolHostContract.GLOBAL_ACTION,data,reply,0)){reply.readException();accepted=reply.readInt()!=0;}
            }catch(RemoteException|RuntimeException failure){android.util.Log.w("CHOTools","System action unavailable",failure);}
            finally{data.recycle();reply.recycle();}
            if(!accepted)main.post(()->android.widget.Toast.makeText(this,action==android.accessibilityservice.AccessibilityService.GLOBAL_ACTION_TOGGLE_SPLIT_SCREEN?"当前应用或系统状态不支持分屏":"系统动作未执行，请检查运行状态",android.widget.Toast.LENGTH_SHORT).show());
        });return true;
    }
    @Override public String foregroundPackage(){
        IBinder owner=control;if(owner==null)return "";Parcel data=Parcel.obtain(),reply=Parcel.obtain();
        try{data.writeInterfaceToken(ToolHostContract.CONTROL_DESCRIPTOR);if(!owner.transact(ToolHostContract.FOREGROUND,data,reply,0))return "";reply.readException();String value=reply.readString();return value==null?"":value;}
        catch(RemoteException|RuntimeException failure){return "";}finally{data.recycle();reply.recycle();}
    }
    @Override public boolean touch(float[] points,long duration,int count,java.util.function.Consumer<Boolean> completion){
        IBinder owner=control;if(owner==null)return false;
        ResultReceiver result=new ResultReceiver(main){@Override protected void onReceiveResult(int code,Bundle state){completion.accept(state!=null&&state.getBoolean("completed"));}};
        Parcel flatten=Parcel.obtain();
        try{result.writeToParcel(flatten,0);flatten.setDataPosition(0);result=ResultReceiver.CREATOR.createFromParcel(flatten);}finally{flatten.recycle();}
        ResultReceiver callback=result;float[] coordinates=points.clone();
        actions.execute(()->{
            Parcel data=Parcel.obtain();boolean sent=false;
            try{if(control==owner){data.writeInterfaceToken(ToolHostContract.CONTROL_DESCRIPTOR);data.writeFloatArray(coordinates);data.writeLong(duration);data.writeInt(count);data.writeTypedObject(callback,0);sent=owner.transact(ToolHostContract.TOUCH,data,null,IBinder.FLAG_ONEWAY);}}
            catch(RemoteException|RuntimeException failure){android.util.Log.w("CHOTools","Touch input unavailable",failure);}
            finally{data.recycle();}
            if(!sent){Bundle state=new Bundle();state.putBoolean("completed",false);callback.send(0,state);}
        });return true;
    }
    @Override public void cancelTouch(){
        IBinder owner=control;if(owner==null)return;
        actions.execute(()->{Parcel data=Parcel.obtain();try{data.writeInterfaceToken(ToolHostContract.CONTROL_DESCRIPTOR);owner.transact(ToolHostContract.CANCEL_TOUCH,data,null,IBinder.FLAG_ONEWAY);}catch(RemoteException ignored){}finally{data.recycle();}});
    }
    @Override public int handleClearance(boolean left){
        var values=getSharedPreferences("edgeflow",0).getAll();var area=HandleDisplayArea.current(this);int width=0;
        for(String id:GestureHandleConfig.IDS){
            boolean enabled=values.get(id+"_enabled") instanceof Boolean value?value:!GestureHandleConfig.isExtra(id);
            if(enabled&&GestureHandleConfig.isLeft(id)==left)width=Math.max(width,GestureHandleConfig.bounds(id,values,getResources().getDisplayMetrics().density,area)[2]);
        }
        return width;
    }
}
