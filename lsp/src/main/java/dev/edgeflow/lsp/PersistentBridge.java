package dev.edgeflow.lsp;

import android.content.*;
import android.content.pm.PackageManager;
import android.database.Cursor;
import android.net.Uri;
import android.os.*;
import io.github.libxposed.service.XposedService;
import java.util.UUID;

/** Module-app writer. SystemUI can refresh or stop; it cannot supply executable settings. */
public final class PersistentBridge extends ContentProvider {
    static Uri uri(){return BackendIdentity.provider();}
    private static final Object SERVICE_LOCK=new Object(),WRITE_LOCK=new Object();
    private static XposedService service;
    static void bound(XposedService value){synchronized(SERVICE_LOCK){service=value;SERVICE_LOCK.notifyAll();}}
    static void died(XposedService value){synchronized(SERVICE_LOCK){if(service==value)service=null;}}
    @Override public boolean onCreate(){return true;}
    private XposedService service(){
        synchronized(SERVICE_LOCK){
            long until=SystemClock.uptimeMillis()+1800;
            while(service==null&&SystemClock.uptimeMillis()<until)try{SERVICE_LOCK.wait(Math.max(1,until-SystemClock.uptimeMillis()));}catch(InterruptedException error){Thread.currentThread().interrupt();throw new IllegalStateException("Interrupted",error);}
            if(service==null)throw new IllegalStateException("LSPosed service unavailable");
            return service;
        }
    }
    private void enforce(String method){
        int caller=Binder.getCallingUid();
        if(caller==android.os.Process.myUid())return;
        try{
            if((method.equals("refresh")||method.equals("disable"))&&caller==getContext().getPackageManager().getApplicationInfo("com.android.systemui",0).uid)return;
        }catch(PackageManager.NameNotFoundException error){throw new SecurityException(error);}
        getContext().enforceCallingPermission(PilotServer.PERMISSION,"Persistent control requires module signature permission");
    }
    static String sourceIdentity(Context context)throws PackageManager.NameNotFoundException{
        var info=context.getPackageManager().getPackageInfo("dev.edgeflow",PackageManager.GET_PROVIDERS);
        if(!info.applicationInfo.enabled)throw new IllegalStateException("CHO is disabled");
        boolean compatible=false;
        if(info.providers!=null)for(var provider:info.providers)if(provider.enabled&&"dev.edgeflow.gesture_config".equals(provider.authority))compatible=true;
        if(!compatible)throw new IllegalStateException("CHO configuration interface unavailable");
        return info.applicationInfo.uid+":"+info.firstInstallTime;
    }
    @Override public Bundle call(String method,String arg,Bundle extras){
        if(!method.equals("enable")&&!method.equals("disable")&&!method.equals("refresh")&&!method.equals("reset")&&!method.equals("sync")&&!method.equals("status"))throw new IllegalArgumentException("Unknown persistent operation");
        // ContentProvider also tracks the caller's attribution, beyond Binder's UID.
        enforce(method);CallingIdentity caller=clearCallingIdentity();
        try{synchronized(WRITE_LOCK){
            Context c=getContext();
            if(android.os.Process.myUid()/100000!=0)throw new IllegalStateException("Only owner user is supported");
            // The local request is authoritative even if the framework is temporarily unavailable.
            SharedPreferences local=c.createDeviceProtectedStorageContext().getSharedPreferences("persistent_controller",0);
            if(method.equals("status")){
                Bundle result=new Bundle();synchronized(SERVICE_LOCK){result.putBoolean("framework_service",service!=null);}
                result.putBoolean("requested",local.getBoolean("requested",false));return result;
            }
            if(method.equals("reset")){
                if(local.getBoolean("requested",false))throw new IllegalStateException("Disable persistent input before resetting its storage");
                if(!local.edit().clear().commit())throw new IllegalStateException("Controller reset not saved");
                service().deleteRemotePreferences(PersistentState.GROUP);
                Bundle result=new Bundle();result.putString("state",new PersistentState(false,"","",UUID.randomUUID().toString(),0).encode());return result;
            }
            if(method.equals("enable")||method.equals("disable")){
                if(!local.edit().putBoolean("requested",method.equals("enable")).commit())throw new IllegalStateException("Controller state not saved");
            }
            boolean requested=local.getBoolean("requested",false);String profile="",identity="",sourceError="";
            if(BackendIdentity.integrated()&&!dev.edgeflow.BackendChoice.lsp(c)){
                requested=false;if(!local.edit().putBoolean("requested",false).commit())throw new IllegalStateException("Backend choice not applied");
            }
            if(method.equals("sync")){
                GestureProfile.parse(arg);identity=sourceIdentity(c);
                if(requested&&!identity.equals(local.getString("source",identity)))requested=false;
                if(!local.edit().putString("profile",arg).putString("source",identity).putBoolean("requested",requested).commit())throw new IllegalStateException("Source update not saved");
            }
            if(requested){
                if(!c.getSystemService(UserManager.class).isUserUnlocked())throw new IllegalStateException("User is locked");
                try{
                    identity=sourceIdentity(c);
                    if(method.equals("refresh")&&!identity.equals(local.getString("source",identity)))throw new IllegalStateException("CHO installation changed");
                    // A retained request may predate an intervening accessibility-only build.
                    // That build can change the actual gesture preferences without publishing snapshots.
                    String installedApk=c.getApplicationInfo().sourceDir;
                    if(PersistentPolicy.needsSourceSnapshot(method.equals("enable"),BackendIdentity.integrated(),installedApk,local.getString("source_apk",null))){
                        Bundle snapshot=c.getContentResolver().call(Uri.parse("content://dev.edgeflow.gesture_config"),"snapshot",null,null);
                        if(snapshot==null)throw new IllegalStateException("CHO snapshot unavailable");
                        profile=snapshot.getString("profile");GestureProfile.parse(profile);
                        if(!local.edit().putString("source",identity).putString("profile",profile).putString("source_apk",installedApk).commit())throw new IllegalStateException("Source configuration not saved");
                    }else{profile=local.getString("profile",null);GestureProfile.parse(profile);}
                }catch(Exception unavailable){
                    sourceError=unavailable.toString();android.util.Log.w("CHOLspBridge","Source configuration unavailable",unavailable);
                    // Do not retain an enabled controller after the source was removed or replaced by an incompatible build.
                    if(!local.edit().putBoolean("requested",false).commit())throw new IllegalStateException("Source revocation not saved",unavailable);
                    requested=false;profile="";identity="";
                }
            }
            PersistentState state=new PersistentState(requested,profile,identity,UUID.randomUUID().toString(),0);
            String encoded=state.encode();
            var remote=service().getRemotePreferences(PersistentState.GROUP);
            if(!remote.edit().putString(PersistentState.KEY,encoded).commit())throw new IllegalStateException("Framework state not saved");
            if(method.equals("enable")||method.equals("disable"))c.sendBroadcast(new Intent(PilotServer.PERSISTENT_CHANGED).setPackage("com.android.systemui").putExtra("module_package",BackendIdentity.module()));
            Bundle result=new Bundle();result.putString("state",encoded);result.putString("source_error",sourceError);return result;
        }}catch(org.json.JSONException | PackageManager.NameNotFoundException error){throw new IllegalArgumentException(error);}
        finally{restoreCallingIdentity(caller);}
    }
    @Override public Cursor query(Uri uri,String[] p,String s,String[] a,String order){throw new UnsupportedOperationException();}
    @Override public String getType(Uri uri){return "application/json";}
    @Override public Uri insert(Uri uri,ContentValues v){throw new UnsupportedOperationException();}
    @Override public int delete(Uri uri,String w,String[] a){throw new UnsupportedOperationException();}
    @Override public int update(Uri uri,ContentValues v,String w,String[] a){throw new UnsupportedOperationException();}
}
