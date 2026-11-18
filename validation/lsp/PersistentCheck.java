package dev.edgeflow.lsp;
import java.util.*;

public final class PersistentCheck {
    static int checks;
    static void ok(boolean value,String why){checks++;if(!value)throw new AssertionError(why);}
    static class Phone {
        boolean requested,checked,awake=true,unlocked=true,owner=true,source=true,a11y,master=true,fault;
        PersistentPolicy.State state(){return PersistentPolicy.decide(requested,checked,awake,unlocked,owner,source,a11y,master,fault);}
        void expect(PersistentPolicy.State expected,String why){ok(state()==expected,why+": "+state());}
    }
    public static void main(String[] args)throws Exception{
        var updates=new ControllerUpdates();updates.offer("off");var inFlight=updates.take();
        ok(inFlight.profile().equals("off")&&!updates.pending(),"source snapshot is retained by its in-flight request");
        updates.offer("on");updates.failed(inFlight);ok(updates.take().profile().equals("on"),"newer source setting wins after a failed request");
        updates.offer("off");inFlight=updates.take();updates.failed(inFlight);ok(updates.take().profile().equals("off"),"failed last source setting is not discarded");
        updates.revoke();updates.offer("old");inFlight=updates.take();ok(inFlight.revoke(),"revocation takes priority over profile sync");
        updates.offer("new");updates.failed(inFlight);inFlight=updates.take();ok(inFlight.revoke()&&inFlight.profile().equals("new"),"reconnect retains revocation and newest profile");
        ok(!updates.pending()&&!updates.take().revoke(),"completed request does not repeat");
        ok(!PersistentPolicy.shouldRefresh(false,false,false,true,false,true),"USER_PRESENT before keyguard state update waits");
        ok(PersistentPolicy.shouldRefresh(false,false,false,true,true,true),"later reconciliation must retry after unlock");
        ok(!PersistentPolicy.shouldRefresh(false,true,false,true,true,true),"only one source check at a time");
        ok(!PersistentPolicy.shouldRefresh(false,false,true,true,true,true),"failed check does not enter a retry loop");
        ok(PersistentPolicy.revokesSource("android.intent.action.PACKAGE_DATA_CLEARED",false),"data clear revokes saved request");
        ok(PersistentPolicy.revokesSource("android.intent.action.PACKAGE_REMOVED",false),"uninstall revokes saved request");
        ok(!PersistentPolicy.revokesSource("android.intent.action.PACKAGE_REMOVED",true),"APK replacement preserves request");
        ok(!PersistentPolicy.revokesSource("android.intent.action.PACKAGE_REPLACED",true),"completed APK replacement can resume");
        var p=new Phone();p.expect(PersistentPolicy.State.DISABLED,"fresh install");
        p.requested=true;p.expect(PersistentPolicy.State.CHECKING,"cached enable is insufficient on a new host");
        p.checked=true;p.expect(PersistentPolicy.State.RUNNING,"fresh source confirmed");
        p.awake=false;p.unlocked=false;p.expect(PersistentPolicy.State.LOCKED,"screen off releases monitor");
        p.awake=true;p.expect(PersistentPolicy.State.LOCKED,"wake does not unlock");
        p.unlocked=true;p.expect(PersistentPolicy.State.RUNNING,"unlock resumes request");
        p.a11y=true;p.expect(PersistentPolicy.State.ACCESSIBILITY,"one input backend");p.a11y=false;p.expect(PersistentPolicy.State.RUNNING,"a11y removal resumes");
        p.master=false;p.expect(PersistentPolicy.State.MASTER_OFF,"CHO master switch suspends");p.master=true;p.expect(PersistentPolicy.State.RUNNING,"CHO master switch resumes");
        p.owner=false;p.expect(PersistentPolicy.State.OTHER_USER,"switching user cannot reuse owner settings");p.owner=true;
        p.source=false;p.expect(PersistentPolicy.State.SOURCE_UNAVAILABLE,"uninstalled source cannot run cached mappings");p.source=true;
        p.fault=true;p.expect(PersistentPolicy.State.FAULTED,"failed input does not retry");p.awake=false;p.unlocked=false;p.expect(PersistentPolicy.State.LOCKED,"fault stays suspended when locked");p.awake=true;p.unlocked=true;p.expect(PersistentPolicy.State.FAULTED,"unlock alone does not clear a fault");p.fault=false;
        p.requested=false;p.awake=false;p.unlocked=false;p.expect(PersistentPolicy.State.DISABLED,"saved disable while locked");p.awake=true;p.unlocked=true;p.expect(PersistentPolicy.State.DISABLED,"unlock cannot undo disable");p.checked=false;p.expect(PersistentPolicy.State.DISABLED,"host restart cannot undo disable");
        String profile="{\"version\":1,\"accessibility_input\":false,\"values\":{\"enabled\":true}}";
        var state=new PersistentState(true,profile,"10001:1234","sequence-1",0);var restored=PersistentState.parse(state.encode());
        ok(restored.requested&&restored.profile.equals(profile)&&restored.source.equals(state.source)&&restored.revision.equals(state.revision),"atomic snapshot roundtrip");
        ok(!PersistentState.parse(new PersistentState(false,"","","off",0).encode()).requested,"saved disable needs no source");
        for(PersistentState bad:List.of(new PersistentState(true,profile,"","r",0),new PersistentState(true,"{}","source","r",0),new PersistentState(true,profile,"source","r",10),new PersistentState(false,"","","",0))){
            boolean rejected=false;try{PersistentState.parse(bad.encode());}catch(Exception expected){rejected=true;}ok(rejected,"invalid persistent data cannot arm input");
        }
        for(String malformed:new String[]{"{}",state.encode().replace("\"version\":1","\"version\":2"),"x".repeat(150001)}){
            boolean rejected=false;try{PersistentState.parse(malformed);}catch(Exception expected){rejected=true;}ok(rejected,"unknown envelope rejected");
        }
        System.out.println("PASS "+checks+" persistent lifecycle and storage assertions");
    }
}
