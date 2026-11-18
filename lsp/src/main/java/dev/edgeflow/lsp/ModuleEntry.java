package dev.edgeflow.lsp;

import android.content.Context;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import io.github.libxposed.api.XposedModule;

/** API 102. SystemUI owns input; system_server owns the bounded window capability. */
public final class ModuleEntry extends XposedModule {
    private boolean installed;
    @Override public void onSystemServerStarting(SystemServerStartingParam param){
        BackendIdentity.configure(getModuleApplicationInfo());
        try {
            FrameworkWindowBroker broker=new FrameworkWindowBroker(param.getClassLoader(),getModuleApplicationInfo());
            Class<?> am=Class.forName("com.android.server.am.ActivityManagerService",false,param.getClassLoader());
            hook(am.getDeclaredMethod("onTransact",int.class,android.os.Parcel.class,android.os.Parcel.class,int.class)).intercept(chain->{
                if(((Integer)chain.getArg(0))!=WindowLeaseProtocol.TRANSACTION)return chain.proceed();
                return broker.transact((android.os.Parcel)chain.getArg(1),(android.os.Parcel)chain.getArg(2),(Integer)chain.getArg(3));
            });
            log(Log.INFO,"CHOWindowBroker","Tool window broker installed");
        }catch(ReflectiveOperationException|RuntimeException|LinkageError failure){log(Log.ERROR,"CHOWindowBroker","Tool windows unsupported",failure);}
    }
    @Override public void onPackageReady(PackageReadyParam param) {
        BackendIdentity.configure(getModuleApplicationInfo());
        if(installed || !param.isFirstPackage() || !"com.android.systemui".equals(param.getPackageName()))return;
        try{NativeVolumeState.install(this,param.getClassLoader());}
        catch(ReflectiveOperationException|RuntimeException|LinkageError failure){log(Log.WARN,"CHOLsp","Native volume lifecycle unavailable",failure);}
        try{
            Class<?> service=Class.forName("com.android.systemui.SystemUIService",false,param.getClassLoader());
            hook(service.getDeclaredMethod("dump",java.io.FileDescriptor.class,java.io.PrintWriter.class,String[].class)).intercept(chain->{
                String[] args=(String[])chain.getArg(2);
                if(args!=null&&args.length==1&&"--cho-status".equals(args[0])){PilotServer.dump((java.io.PrintWriter)chain.getArg(1));return null;}
                return chain.proceed();
            });
        }catch(ReflectiveOperationException|RuntimeException|LinkageError failure){log(Log.WARN,"CHOLsp","Runtime dump unavailable",failure);}
        // Observe scene flags only; no changes to navigation, permissions or vendor state.
        try {
            Class<?> state=Class.forName("com.android.systemui.model.SysUiStateImpl",false,param.getClassLoader());
            var flags=state.getMethod("getFlags");var display=state.getMethod("getDisplayId");
            var text=Class.forName("com.android.systemui.shared.system.QuickStepContract",false,param.getClassLoader()).getMethod("getSystemUiStateString",long.class);
            hook(state.getMethod("commitUpdate")).intercept(chain->{
                Object result=chain.proceed();
                try{if(((Number)display.invoke(chain.getThisObject())).intValue()==0)SystemScene.updateFlags((String)text.invoke(null,flags.invoke(chain.getThisObject())));}
                catch(ReflectiveOperationException | RuntimeException | LinkageError failure){SystemScene.updateFlags(null);}
                return result;
            });
        }catch(ReflectiveOperationException | RuntimeException | LinkageError failure){log(Log.WARN,"CHOLsp","Scene flags unavailable; requested scene exclusions will block input",failure);}
        try {
            String application=param.getApplicationInfo().className;
            if(application==null)application="android.app.Application";
            Class<?> type=Class.forName(application,false,param.getClassLoader());
            hook(type.getMethod("onCreate")).intercept(chain->{
                Object result=chain.proceed();
                if(chain.getThisObject() instanceof Context context) {
                    new Handler(Looper.getMainLooper()).post(()->{
                        try{PilotServer.attach(context,()->getRemotePreferences(PersistentState.GROUP));}
                        catch(RuntimeException | LinkageError failure){Log.e("CHOLsp","Control unavailable",failure);}
                    });
                }
                return result;
            });
            installed=true;log(Log.INFO,"CHOLsp","SystemUI initialization hook installed; input is inactive");
        }catch(ReflectiveOperationException | RuntimeException | LinkageError failure){log(Log.ERROR,"CHOLsp","Initialization unsupported",failure);}
    }
}
