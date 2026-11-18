package dev.edgeflow.lsp;
import dev.edgeflow.*;
import java.util.function.Predicate;

/** One contact owns one immutable profile and one foreground task identity. */
final class MappedGestureSession {
    record Feedback(String handle,boolean left,float startX,float startY,float inward,float vertical,int direction,boolean shortReady,boolean fired,boolean cancelled){}
    record Action(String id,String handle,String direction,boolean longer,String focus,float startX,float startY,float endX,float endY){
        float[] anchor(){return new float[]{startX,startY,endX,endY};}
    }
    record Trial(String handle,int kind,String direction,String action,String source,String reason,float distance,float shortDistance,float longDistance,long duration,long elapsed,float angle){}
    final GestureProfile profile;
    private final GestureHandleConfig.Area area;
    private final float density,claimDistance;
    private final Predicate<String> supported;
    private GestureRecognizer recognizer;
    private String handle,pkg,focus;
    private int pointer;
    private long lastTime;
    private boolean owned,cancelled;
    private String rejection="";
    MappedGestureSession(GestureProfile profile,GestureHandleConfig.Area area,float density,float claimDistance,Predicate<String> supported){
        if(!Float.isFinite(claimDistance)||claimDistance<=0)throw new IllegalArgumentException("Invalid touch slop");
        this.profile=profile;this.area=area;this.density=density;this.claimDistance=claimDistance;this.supported=supported;
    }
    boolean begin(int id,float x,float y,long now,String pkg,String focus,boolean eligible){
        return begin(id,x,y,now,pkg,focus,eligible,false);
    }
    boolean begin(int id,float x,float y,long now,String pkg,String focus,boolean eligible,boolean trial){
        reset();if(!eligible||focus==null||focus.isEmpty()||!Float.isFinite(x)||!Float.isFinite(y)||!profile.enabled()||!trial&&profile.excluded(pkg)||area.landscape()&&profile.hideLandscape())return false;
        for(int i=GestureHandleConfig.IDS.length-1;i>=0;i--){String candidate=GestureHandleConfig.IDS[i];
            if(!profile.flag(candidate+"_enabled",!GestureHandleConfig.isExtra(candidate)))continue;
            int[] b=GestureHandleConfig.bounds(candidate,profile.values,density,area);
            if(x>=b[0]&&x<b[0]+b[2]&&y>=b[1]&&y<b[1]+b[3]){handle=candidate;break;}
        }
        if(handle==null)return false;
        this.pointer=id;this.pkg=pkg;this.focus=focus;lastTime=now;
        recognizer=new GestureRecognizer(profile.number("trigger_dp",39.43f)*density,
            profile.flag(handle+"_long_enabled",true)?profile.number("long_trigger_dp",87.43f)*density:Float.MAX_VALUE,
            (long)profile.number("long_delay_ms",300),GestureAngles.read(profile.values,false),GestureAngles.read(profile.values,true),
            profile.flag("long_immediate",false),profile.flag("quick_action_short",false),profile.flag("quick_action_long",true));
        recognizer.begin(GestureHandleConfig.isLeft(handle),x,y,now);return true;
    }
    boolean move(int id,int count,float x,float y,long now,String currentFocus,boolean eligible){
        if(!tracking()||cancelled)return false;
        if(!eligible||count!=1||id!=pointer||now<lastTime||!focus.equals(currentFocus)||!Float.isFinite(x)||!Float.isFinite(y)){
            rejection=!eligible?"ineligible":count!=1?"multi-touch":id!=pointer?"pointer-changed":now<lastTime?"event-order":!focus.equals(currentFocus)?"focus-changed":"invalid-coordinate";cancel();return false;
        }
        lastTime=now;recognizer.move(x,y,now);
        if(!owned&&recognizer.distance()>=Math.min(claimDistance,recognizer.shortThreshold())&&!GestureAngles.read(profile.values,false).accepts(recognizer.angle())
                &&(!profile.flag(handle+"_long_enabled",true)||!GestureAngles.read(profile.values,true).accepts(recognizer.angle()))){rejection="vertical-intent";cancel();return false;}
        return wantsClaim(now);
    }
    boolean wantsClaim(long now){
        if(!tracking()||cancelled||owned||recognizer.isFired())return false;
        boolean longer=!profile.flag("quick_action_short",false)&&recognizer.isLongDistanceReached()&&recognizer.elapsed(now)>=recognizer.durationThreshold();
        // Claim at the system's movement slop, before the underlying app commits a swipe.
        // Recognition, haptics and action dispatch retain the user's full thresholds.
        boolean shortIntent=recognizer.inward()>0&&recognizer.distance()>=Math.min(claimDistance,recognizer.shortThreshold())&&GestureAngles.read(profile.values,false).accepts(recognizer.angle());
        return (shortIntent||longer)&&supported.test(action(longer).id());
    }
    void claim(){owned=true;}
    Action poll(long now){
        if(!tracking()||cancelled||!owned||recognizer.isFired())return null;
        if(recognizer.shouldFireShort())return fire(false);
        if(recognizer.shouldFireLong(now))return fire(true);
        return null;
    }
    Action end(int id,float x,float y,long now,String currentFocus,boolean eligible){
        Action result=null;
        if(tracking()&&!cancelled&&owned&&!recognizer.isFired()&&id==pointer&&now>=lastTime&&eligible&&focus.equals(currentFocus)&&Float.isFinite(x)&&Float.isFinite(y)){
            recognizer.move(x,y,now);int kind=recognizer.finish(now);if(kind!=0)result=fire(kind==2);
        }
        reset();return result;
    }
    private Action action(boolean longer){String direction=recognizer.directionName(longer);float[] point=recognizer.anchor();return new Action(profile.action(pkg,handle,direction,longer),handle,direction,longer,focus,point[0],point[1],point[2],point[3]);}
    private Action fire(boolean longer){Action action=action(longer);recognizer.fired();return action;}
    long pending(long now){
        if(!tracking()||cancelled||recognizer.isFired())return -1;
        long pending=recognizer.pendingLongDelay(now);
        if(pending>=0)return pending;
        return !owned&&!profile.flag("quick_action_short",false)&&recognizer.isLongDistanceReached()
            ?Math.max(0,recognizer.durationThreshold()-recognizer.elapsed(now)):-1;
    }
    boolean tracking(){return recognizer!=null;}
    boolean owned(){return owned;}
    String rejection(){return rejection;}
    String focus(){return focus;}
    Trial report(int kind,long now){
        if(recognizer==null)return null;boolean longer=kind==2;String direction=recognizer.directionName(longer);
        String key=GestureHandleConfig.actionKey(handle,direction,longer);String source=!pkg.isEmpty()&&profile.values.containsKey("app_override/"+pkg+"/"+key)?pkg:"全局";
        return new Trial(handle,kind,direction,profile.action(pkg,handle,direction,longer),source,recognizer.diagnostic(kind,now),recognizer.distance(),recognizer.shortThreshold(),recognizer.longThreshold(),recognizer.durationThreshold(),recognizer.elapsed(now),recognizer.angle());
    }
    Trial endTrial(int id,float x,float y,long now,String currentFocus,boolean eligible){
        Trial result=null;
        if(tracking()&&!recognizer.isFired()){
            int kind=0;if(!cancelled&&id==pointer&&now>=lastTime&&eligible&&focus.equals(currentFocus)&&Float.isFinite(x)&&Float.isFinite(y)){recognizer.move(x,y,now);kind=owned?recognizer.finish(now):0;}
            result=report(kind,now);
        }
        reset();return result;
    }
    Feedback feedback(){
        if(recognizer==null)return null;float[] point=recognizer.anchor();
        return new Feedback(handle,GestureHandleConfig.isLeft(handle),point[0],point[1],recognizer.inward(),recognizer.vertical(),recognizer.direction(),recognizer.isShortReady()&&supported.test(action(false).id()),recognizer.isFired(),cancelled);
    }
    void cancel(){cancelled=true;if(recognizer!=null)recognizer.cancel();}
    void reset(){recognizer=null;handle=pkg=focus=null;owned=cancelled=false;rejection="";}
}
