package dev.edgeflow.lsp;
import android.content.pm.ApplicationInfo;
import android.net.Uri;
/** Same implementation in CHO and the isolated development APK. */
final class BackendIdentity {
    static final String APP="dev.edgeflow",PILOT="dev.edgeflow.lsp.pilot";
    private static volatile String module=APP,source="";
    private static volatile boolean debugPilot;
    static boolean isDebugPilot(ApplicationInfo info){
        return PILOT.equals(info.packageName)&&(info.flags&ApplicationInfo.FLAG_DEBUGGABLE)!=0;
    }
    static void requireDiagnosticOperation(String operation){
        if(!debugPilot&&!"status".equals(operation))throw new IllegalArgumentException("Unknown operation");
    }
    static void configure(ApplicationInfo info){
        if(!APP.equals(info.packageName)&&!PILOT.equals(info.packageName))throw new IllegalArgumentException("Unknown backend package");
        module=info.packageName;source=info.sourceDir;debugPilot=isDebugPilot(info);
    }
    static String module(){return module;}
    static String source(){return source;}
    static boolean integrated(){return APP.equals(module);}
    static Uri provider(){return Uri.parse("content://"+(integrated()?APP+".lsp":module)+".persistent");}
    private BackendIdentity(){}
}
