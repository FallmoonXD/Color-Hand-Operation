import dev.edgeflow.GestureAngles;
import dev.edgeflow.GestureHaptics;
import dev.edgeflow.GestureRecognizer;
import java.util.HashMap;
import java.util.Map;

public final class AngleHapticCheck {
    private static int checks;
    private static void check(boolean ok,String label){checks++;if(!ok)throw new AssertionError(label);}
    private static GestureRecognizer at(boolean left,float angle,float distance,long time,GestureAngles shortAngles,GestureAngles longAngles){
        GestureRecognizer g=new GestureRecognizer(40,100,300,shortAngles,longAngles,false,false,true);
        g.begin(left,500,500,1000);double radians=Math.toRadians(angle);g.move(500+(left?1:-1)*(float)(Math.cos(radians)*distance),500+(float)(Math.sin(radians)*distance),time);return g;
    }
    public static void main(String[] args){
        GestureAngles narrow=new GestureAngles(75,25,45,75),wide=new GestureAngles(85,20,30,85);
        for(boolean left:new boolean[]{false,true})for(int sign:new int[]{-1,1}){
            GestureRecognizer g=at(left,sign*80,200,1400,narrow,wide);
            check(!g.isShortReady()&&g.isLongReady()&&g.pendingLongDelay(1400)==0,"long-only outer region can schedule side="+left);
            check(g.finish(1400)==2&&g.directionName(true).equals(sign<0?"up":"down"),"long-only outer region dispatches independently");
            g=at(left,sign*80,200,1400,wide,narrow);
            check(g.isShortReady()&&!g.isLongDistanceReached()&&g.pendingLongDelay(1400)==-1,"short-only outer region cannot schedule long");
            check(g.finish(1400)==1&&g.directionName(false).equals(sign<0?"up":"down"),"short-only outer region releases short");
            g=at(left,sign*80,200,1100,narrow,wide);check(g.pendingLongDelay(1100)==200&&g.finish(1200)==0,"long-only region before duration does not invent short");
        }
        for(boolean left:new boolean[]{false,true}){
            GestureRecognizer g=at(left,-22,200,1100,narrow,wide);
            check(g.direction()==GestureRecognizer.UP&&g.directionName(false).equals("straight")&&g.directionName(true).equals("up"),"wave uses long area but both dispatch directions remain independent");
            check(g.finish(1200)==1&&g.directionName(false).equals("straight"),"early release uses short boundary despite long-distance wave");
            g=at(left,35,200,1400,narrow,wide);check(g.finish(1400)==2&&g.directionName(true).equals("down")&&g.directionName(false).equals("straight"),"down inner boundaries independent");
            g=new GestureRecognizer(40,Float.MAX_VALUE,300,narrow,wide,false,false,true);g.begin(left,500,500,1000);g.move(left?650:350,500,1400);check(g.pendingLongDelay(1400)==-1&&!g.isLongDistanceReached()&&g.finish(1400)==1,"long switch off excludes configured long boundaries");
        }
        Map<String,Object> p=new HashMap<>();GestureAngles old=GestureAngles.read(p,false);
        check(old.upInner==30&&old.downInner==30&&old.upOuter==80&&old.downOuter==80,"missing new keys retain former default");
        check(old.accepts(79.999f)&&!old.accepts(80)&&old.accepts(-79.999f)&&!old.accepts(-80),"legacy 80 degree boundary stays excluded");
        p.put("direction_angle_degrees",47f);for(boolean longer:new boolean[]{false,true}){GestureAngles a=GestureAngles.read(p,longer);check(a.upInner==47&&a.downInner==47,"explicit legacy symmetric angle preserved");}
        p.put(GestureAngles.key(false,1),12f);check(GestureAngles.read(p,false).upInner==12&&GestureAngles.read(p,false).downInner==47&&GestureAngles.read(p,true).upInner==47,"one new boundary does not overwrite other legacy boundaries");
        p.clear();check(GestureHaptics.quick(p)&&!GestureHaptics.custom(p)&&GestureHaptics.level(p)==5,"new model defaults to quick and system feedback");
        p.put("vibration_strength",192);check(GestureHaptics.custom(p)&&GestureHaptics.legacyAmplitude(p)&&GestureHaptics.amplitude(p)==192&&GestureHaptics.level(p)==23,"old nonzero amplitude retained and shown as custom");
        p.put("custom_vibration",false);check(!GestureHaptics.custom(p)&&!GestureHaptics.legacyAmplitude(p),"explicit system mode overrides stored legacy amplitude");
        for(int amplitude=1;amplitude<=4;amplitude++){p.put("custom_vibration",true);p.put("vibration_strength",amplitude);check(GestureHaptics.level(p)==1&&GestureHaptics.amplitude(p)==amplitude,"nonzero legacy amplitude cannot be displayed as mute "+amplitude);}
        p.put("custom_vibration",true);p.put("vibration_duration_ms",9);check(GestureHaptics.level(p)==9&&!GestureHaptics.legacyAmplitude(p),"new level switches runtime from old amplitude without deleting it");
        p.clear();p.put("vibration",false);check(GestureHaptics.legacyMuted(p)&&GestureHaptics.custom(p)&&GestureHaptics.level(p)==0,"legacy muted profile stays visibly custom zero");
        p.put("custom_vibration",true);p.put("vibration_duration_ms",0);p.put("vibration",true);check(!GestureHaptics.legacyMuted(p)&&GestureHaptics.level(p)==0,"explicit migration can remove hidden master while retaining visible zero");
        p.put("custom_vibration",false);check(!GestureHaptics.custom(p)&&!GestureHaptics.legacyMuted(p),"switching custom off restores system mode");
        p.put("quick_vibration",false);check(!GestureHaptics.quick(p)&&!GestureHaptics.custom(p),"quick controls timing without disabling system feedback");
        System.out.println("PASS "+checks+" independent angle / haptic compatibility assertions");
    }
}
