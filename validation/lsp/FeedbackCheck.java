package dev.edgeflow.lsp;

import dev.edgeflow.*;
import java.util.*;

public final class FeedbackCheck {
    private static int checks;
    private static void ok(boolean value,String why){checks++;if(!value)throw new AssertionError(why);}
    public static void main(String[] args){
        FeedbackTiming feedback=new FeedbackTiming();
        feedback.begin(true);ok(!feedback.progress(false,false),"tap has no pulse");
        ok(!feedback.progress(true,false),"unclaimed contact has no success pulse");
        ok(feedback.progress(true,true),"claim and short threshold pulse once");
        ok(!feedback.progress(false,true)&&!feedback.progress(true,true),"retreat and recross stay latched");
        ok(!feedback.action(false),"quick short release does not repeat");ok(feedback.action(true),"long action has its own confirmation");
        feedback.begin(false);ok(!feedback.progress(true,true),"nonquick waits for action");ok(feedback.action(false),"nonquick short action pulses");
        feedback.begin(true);ok(feedback.action(true),"long-only angle can pulse without short threshold");
        feedback.begin(true);ok(feedback.progress(true,true),"new contact resets latch");
        Map<String,Object> values=new HashMap<>();values.put("handle_color",0x80224466);values.put("handle_opacity",50);
        ok(GestureAppearance.visibleColor(values,"left")==0x40224466,"opacity multiplies existing alpha");
        values.put("left_color",0xffdd2200);ok(GestureAppearance.visibleColor(values,"left")==0x80dd2200,"independent side color");
        ok(GestureAppearance.visibleColor(values,"right")==0x40224466,"other side inherits global color");
        values.put("handle_opacity",.5f);ok(GestureAppearance.visibleColor(values,"left")==0x80dd2200,"old fraction retains alpha");
        ok(GestureAppearance.opacityPercent(1f)==100&&GestureAppearance.opacityPercent(1)==1,"old fraction and modern percentage are distinct");
        values.put("handle_opacity",0);ok(GestureAppearance.visibleColor(values,"left")==0x00dd2200,"hidden handle is fully transparent");
        for(Object value:new Object[]{Double.NaN,Float.POSITIVE_INFINITY,"50",-1})ok(GestureAppearance.opacityPercent(value)==0,"invalid opacity does not expose a handle");
        values.put("schema",2);values.put("left_enabled",true);values.put("trigger_dp",40);values.put("long_trigger_dp",100);
        MappedGestureSession session=new MappedGestureSession(new GestureProfile(values,false),GestureHandleConfig.safeArea(1000,2000,0,0,0,0),1,8,id->true);
        ok(session.begin(0,5,1000,1000,"test.app","task",true),"feedback contact begins");
        var f=session.feedback();ok(f.left()&&f.startX()==5&&f.startY()==1000&&f.inward()==0,"feedback retains physical anchor");
        session.move(0,1,60,950,1100,"task",true);f=session.feedback();ok(f.shortReady()&&f.direction()==-1&&f.inward()==55&&f.vertical()==-50,"feedback uses recognizer direction and radial readiness");
        session.cancel();ok(session.feedback().cancelled(),"cancellation is visible to feedback");session.reset();ok(session.feedback()==null,"reset removes gesture state");
        values.put("action_left_straight_short","widgets");
        session=new MappedGestureSession(new GestureProfile(values,false),GestureHandleConfig.safeArea(1000,2000,0,0,0,0),1,8,id->!id.equals("widgets"));
        session.begin(0,5,1000,1000,"test.app","task",true);session.move(0,1,60,1000,1100,"task",true);
        ok(!session.feedback().shortReady(),"unsupported short action cannot confirm with a short pulse");
        System.out.println("PASS "+checks+" feedback timing, appearance and anchor assertions");
    }
}
