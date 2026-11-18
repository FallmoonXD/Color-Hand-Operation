package dev.edgeflow.lsp;

import android.app.Application;
import io.github.libxposed.service.*;

/** The service provider may create this process before credential storage unlocks. */
public class PilotApplication extends Application {
    @Override public void onCreate(){
        super.onCreate();
        BackendIdentity.configure(getApplicationInfo());
        if(!getPackageName().equals(Application.getProcessName()))return;
        XposedServiceHelper.registerListener(new XposedServiceHelper.OnServiceListener(){
            @Override public void onServiceBind(XposedService service){PersistentBridge.bound(service);}
            @Override public void onServiceDied(XposedService service){PersistentBridge.died(service);}
        });
    }
}
