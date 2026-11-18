package dev.edgeflow.lsp;

import dev.edgeflow.*;
import java.util.*;

public final class MappedGestureCheck {
    static int checks;
    static void ok(boolean value,String message){checks++;if(!value)throw new AssertionError(message);}
    static Map<String,Object> base(){return new HashMap<>(Map.of("schema",2,"trigger_dp",40,"long_trigger_dp",100,"long_delay_ms",300,"left_handle_width_dp",20,"right_handle_width_dp",20));}
    static MappedGestureSession session(Map<String,Object> values){return new MappedGestureSession(new GestureProfile(values,false),GestureHandleConfig.safeArea(1000,2000,0,50,0,50),1,8,id->!id.equals("widgets")&&!id.equals("quick_tools"));}
    static boolean begin(MappedGestureSession s,float x,float y){return s.begin(0,x,y,1000,"test.app","1:test.app/Main",true);}
    static boolean move(MappedGestureSession s,float x,float y,long now){return s.move(0,1,x,y,now,"1:test.app/Main",true);}
    static MappedGestureSession.Action end(MappedGestureSession s,float x,float y,long now){return s.end(0,x,y,now,"1:test.app/Main",true);}
    public static void main(String[] args)throws Exception{
        for(String handle:GestureHandleConfig.IDS)for(String direction:new String[]{"up","straight","down"})for(boolean longer:new boolean[]{false,true}){
            Map<String,Object> p=base();for(String id:GestureHandleConfig.IDS)p.put(id+"_enabled",id.equals(handle));
            p.put(handle+"_long_enabled",true);p.put("quick_action_long",false);
            p.put(GestureHandleConfig.actionKey(handle,direction,longer),"home");
            var area=GestureHandleConfig.safeArea(1000,2000,0,50,0,50);int[] b=GestureHandleConfig.bounds(handle,p,1,area);
            float x=b[0]+b[2]/2f,y=b[1]+b[3]/2f,delta=longer?130:50;
            float dx=GestureHandleConfig.isLeft(handle)?delta:-delta,dy=direction.equals("up")?-delta:direction.equals("down")?delta:0;
            var s=session(p);ok(begin(s,x,y),handle+" begin");ok(move(s,x+dx,y+dy,longer?1400:1100),handle+" claim");s.claim();
            var action=end(s,x+dx,y+dy,longer?1410:1110);
            ok(action!=null&&action.id().equals("home")&&action.handle().equals(handle)&&action.direction().equals(direction)&&action.longer()==longer,"6x6 slot "+handle+direction+longer);
            ok(Arrays.equals(action.anchor(),new float[]{x,y,x+dx,y+dy}),"tool anchor survives recognizer reset "+handle+direction+longer);
            ok(end(s,x+dx,y+dy,1500)==null,"single dispatch");
        }
        Map<String,Object> p=base();p.put("left_long_enabled",true);p.put("action_left_straight_short","back");p.put("action_left_straight_long","home");
        var s=session(p);begin(s,10,1000);ok(move(s,140,1000,1100),"short can claim before long deadline");s.claim();ok(s.poll(1100)==null,"long waits");ok(s.pending(1100)==200,"remaining duration");
        var a=s.poll(1300);ok(a!=null&&a.longer()&&a.id().equals("home"),"stationary long fires at deadline");ok(s.poll(1400)==null&&end(s,140,1000,1500)==null,"long fires once");
        s=session(p);begin(s,10,1000);move(s,140,1000,1100);s.claim();a=end(s,140,1000,1200);ok(!a.longer()&&a.id().equals("back"),"distance alone does not trigger long");
        p.put("long_immediate",true);s=session(p);begin(s,10,1000);move(s,140,1000,1010);s.claim();ok(s.poll(1010).longer(),"immediate long");p.remove("long_immediate");
        p.put("quick_action_short",true);s=session(p);begin(s,10,1000);move(s,140,1000,1400);s.claim();ok(!s.poll(1400).longer(),"quick short takes precedence");p.remove("quick_action_short");
        p.put("action_left_straight_short","widgets");p.put("quick_action_long",false);s=session(p);begin(s,10,1000);ok(!move(s,140,1000,1100),"unsupported short not claimed");ok(s.pending(1100)==200,"release long can await ownership");ok(s.wantsClaim(1300),"long mapping claims at deadline");s.claim();ok(s.poll(1300)==null,"release long waits for UP");ok(end(s,140,1000,1400).id().equals("home"),"supported long after unsupported short");
        p.put("action_left_straight_short","back");p.put("action_left_straight_long","widgets");s=session(p);begin(s,10,1000);move(s,60,1000,1050);s.claim();move(s,140,1000,1400);ok(end(s,140,1000,1410).id().equals("widgets"),"unsupported long never falls back to short");
        p.put("app_override/test.app/action_left_straight_short","none");s=session(p);begin(s,10,1000);move(s,60,1000,1100);s.claim();ok(end(s,60,1000,1110).id().equals("none"),"explicit none is retained");
        p.remove("app_override/test.app/action_left_straight_short");ok(new GestureProfile(p,false).action("test.app","left","straight",false).equals("back"),"absence inherits");
        for(String failure:new String[]{"retreat","multi","focus","cancel","backwards-time","nan"}){
            s=session(p);begin(s,10,1000);move(s,60,1000,1100);s.claim();
            switch(failure){
                case "retreat"->move(s,12,1000,1200);
                case "multi"->s.move(0,2,60,1000,1200,"1:test.app/Main",true);
                case "focus"->s.move(0,1,60,1000,1200,"2:test.other/Main",true);
                case "cancel"->s.cancel();
                case "backwards-time"->move(s,60,1000,1099);
                case "nan"->move(s,Float.NaN,1000,1200);
            }
            ok(end(s,failure.equals("retreat")?12:60,1000,1250)==null,"cancel "+failure);
        }
        s=session(p);begin(s,10,1000);ok(!move(s,10,1200,1100)&&!s.wantsClaim(1500),"vertical remains pass-through");
        s=session(p);ok(!begin(s,50,1000),"outside configured width");ok(!begin(s,10,60),"outside configured height");
        p.put("extra_left_enabled",true);p.put("extra2_left_enabled",true);p.put("extra_left_handle_start_fraction",.1f);p.put("extra_left_handle_end_fraction",.9f);p.put("extra2_left_handle_start_fraction",.1f);p.put("extra2_left_handle_end_fraction",.9f);p.put("action_extra2_left_straight_short","home");
        s=session(p);begin(s,5,1000);move(s,60,1000,1100);s.claim();ok(end(s,60,1000,1110).handle().equals("extra2_left"),"topmost extra handle wins overlap");
        p.put("enabled",false);ok(!begin(session(p),10,1000),"master off");p.put("enabled",true);p.put("disabled_apps",Set.of("test.app"));ok(!begin(session(p),10,1000),"application exclusion");p.remove("disabled_apps");
        p.put("hide_landscape",false);p.put("landscape_enabled",false);ok(new GestureProfile(p,false).hideLandscape(),"legacy landscape flag remains effective");
        p.put("left_landscape_handle_width_dp",30);int[] b=GestureHandleConfig.bounds("left",p,1,GestureHandleConfig.safeArea(2000,1000,60,20,60,40));ok(b[0]==0&&b[2]==30,"landscape physical edge and independent width");
        ok(!new GestureProfile(p,true).enabled(),"a11y snapshot rejects duplicate input");
        p.put("hide_notifications",true);p.put("hide_keyboard",true);var scenes=new GestureProfile(p,false);
        for(String state:Arrays.asList("awake|notif_visible","awake|notif_expanded","awake|qs_visible","awake|ime_visible",null))ok(!scenes.sceneAllowed(state),"scene excludes "+state);
        ok(scenes.sceneAllowed("allow_gesture|awake"),"normal scene remains eligible");
        GestureProfile parsed=GestureProfile.parse("{\"version\":1,\"accessibility_input\":false,\"values\":{\"disabled_apps\":[\"test.app\"],\"schema\":2}}");ok(parsed.excluded("test.app"),"serialized set retained");
        for(String raw:new String[]{"{}","{\"version\":2}","{\"version\":1,\"accessibility_input\":false,\"values\":{\"trigger_dp\":0}}","{\"version\":1,\"accessibility_input\":false,\"values\":{\"action_left_straight_short\":true}}"}){
            boolean rejected=false;try{GestureProfile.parse(raw);}catch(Exception expected){rejected=true;}ok(rejected,"invalid snapshot rejected");
        }
        trialChecks();
        earlyClaimChecks();
        System.out.println("PASS "+checks+" mapped gesture assertions");
    }
    private static void trialChecks(){
        Map<String,Object> p=base();p.put("action_left_straight_short","screen_magnifier");p.put("left_long_enabled",false);
        var area=GestureHandleConfig.safeArea(1000,2000,0,50,0,50);
        var s=new MappedGestureSession(new GestureProfile(p,false),area,1,8,id->true);
        ok(s.begin(0,10,1000,1000,"","1:dev.edgeflow/Main",true,true),"trial begins in main app");
        s.move(0,1,70,1000,1100,"1:dev.edgeflow/Main",true);s.claim();
        var r=s.endTrial(0,70,1000,1200,"1:dev.edgeflow/Main",true);
        ok(r.kind()==1&&r.action().equals("screen_magnifier")&&r.source().equals("全局"),"trial can report unsupported actions without executing");
        ok(r.distance()==60&&r.shortDistance()==40,"trial reports actual recognizer distances");
        ok(s.poll(1300)==null&&s.end(0,70,1000,1300,"1:dev.edgeflow/Main",true)==null,"trial completion leaves no action to execute");
        s.begin(0,10,1000,1000,"","1:dev.edgeflow/Main",true,true);s.move(0,1,25,1000,1100,"1:dev.edgeflow/Main",true);
        r=s.endTrial(0,25,1000,1200,"1:dev.edgeflow/Main",true);ok(r.kind()==0&&r.distance()==15,"subthreshold trial is still reported");
        p.put("disabled_apps",Set.of("test.app"));p.put("app_override/test.app/action_left_straight_short","home");
        s=new MappedGestureSession(new GestureProfile(p,false),area,1,8,id->true);
        ok(!s.begin(0,10,1000,1000,"test.app","2:test.app/Main",true),"normal excluded app stays excluded");
        ok(s.begin(0,10,1000,1000,"test.app","1:dev.edgeflow/Main",true,true),"trial may simulate an excluded application");
        s.move(0,1,70,1000,1100,"1:dev.edgeflow/Main",true);s.claim();r=s.endTrial(0,70,1000,1200,"1:dev.edgeflow/Main",true);
        ok(r.kind()==1&&r.action().equals("home")&&r.source().equals("test.app"),"trial resolves the simulated app override");
        p.put("quick_action_short",true);s=new MappedGestureSession(new GestureProfile(p,false),area,1,8,id->true);
        s.begin(0,10,1000,1000,"","1:dev.edgeflow/Main",true,true);s.move(0,1,70,1000,1100,"1:dev.edgeflow/Main",true);s.claim();
        ok(s.poll(1100)!=null,"quick trial fires a report while held");ok(s.report(1,1100).kind()==1,"quick report preserves diagnostic state");
        ok(s.endTrial(0,70,1000,1200,"1:dev.edgeflow/Main",true)==null,"quick trial is not reported twice");
    }
    private static void earlyClaimChecks(){
        Map<String,Object> p=base();p.put("left_long_enabled",false);p.put("action_left_straight_short","back");p.put("action_left_down_short","home");
        var s=session(p);begin(s,10,1000);ok(!move(s,14,1001,1010),"tap jitter remains with the app");ok(end(s,14,1001,1020)==null,"tap has no action");
        s=session(p);begin(s,10,1000);ok(move(s,20,1000,1010),"horizontal intent claims before the full action threshold");s.claim();
        ok(!s.feedback().shortReady()&&s.poll(1010)==null,"early claim has no ready pulse or action");ok(end(s,20,1000,1020)==null,"short aborted drag never executes");
        s=session(p);begin(s,10,1000);move(s,20,1000,1010);s.claim();move(s,60,1000,1050);ok(end(s,60,1000,1060).id().equals("back"),"full distance still dispatches once");
        s=session(p);begin(s,10,1000);ok(move(s,15,1010,1010),"diagonal claims before launcher search can commit");s.claim();move(s,35,1050,1050);ok(end(s,35,1050,1060).id().equals("home"),"diagonal action retains configured angle and distance");
        s=session(p);begin(s,10,1000);ok(!move(s,11,1010,1010),"vertical intent stays with the app");ok(!move(s,70,1020,1060),"vertical-to-horizontal turn never steals the app's scroll");
        p.put("action_left_straight_short","widgets");s=session(p);begin(s,10,1000);ok(!move(s,20,1000,1010),"unsupported short mapping cannot claim early");
        p.put("left_long_enabled",true);p.put("action_left_straight_long","back");s=session(p);begin(s,10,1000);ok(!move(s,20,1000,1010),"long-only mapping does not steal an unsupported short drag");move(s,140,1000,1100);ok(s.wantsClaim(1400),"supported long-only mapping still claims at its threshold");
    }
}
