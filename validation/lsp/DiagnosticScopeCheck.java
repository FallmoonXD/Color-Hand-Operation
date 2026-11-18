package dev.edgeflow.lsp;

import android.content.pm.ApplicationInfo;

/** Runs from a separate ART process against the packaged command gate. */
public final class DiagnosticScopeCheck {
    private static int checks;
    private static void expect(String command,boolean allowed){
        boolean accepted=true;
        try{BackendIdentity.requireDiagnosticOperation(command);}
        catch(IllegalArgumentException rejected){accepted=false;}
        if(accepted!=allowed)throw new AssertionError(command);
        checks++;
    }
    private static void checkIdentity(String pkg,boolean debug,boolean experiments){
        ApplicationInfo info=new ApplicationInfo();
        info.packageName=pkg;info.flags=debug?ApplicationInfo.FLAG_DEBUGGABLE:0;
        BackendIdentity.configure(info);
        if(BackendIdentity.isDebugPilot(info)!=experiments)throw new AssertionError(pkg);
        expect("status",true);
        for(String op:new String[]{"observe","configured","capture","stop","window_probe","window_probe_status","window_probe_stop",
                "tool_probe_widgets","tool_probe_quick_tools","tool_probe_stop"})expect(op,experiments);
    }
    public static void main(String[] args){
        checkIdentity(BackendIdentity.APP,false,false);
        checkIdentity(BackendIdentity.APP,true,false);
        checkIdentity(BackendIdentity.PILOT,false,false);
        checkIdentity(BackendIdentity.PILOT,true,true);
        System.out.println("PASS "+checks+" diagnostic scope checks across four module identities");
    }
}
