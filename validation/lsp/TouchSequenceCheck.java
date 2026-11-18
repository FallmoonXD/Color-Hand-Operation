package dev.edgeflow.lsp;
import java.util.*;
public final class TouchSequenceCheck {
    static int checks;static void ok(boolean condition,String name){checks++;if(!condition)throw new AssertionError(name);}
    public static void main(String[] args){
        for(int count:new int[]{1,2})for(long duration:new long[]{60,260,650}){
            var steps=TouchSequence.create(new float[]{10,20,10,20},duration,count,8,100,200);
            ok(steps.size()==count*2,"stationary strokes contain only down/up");
            for(int i=0;i<count;i++){ok(steps.get(i*2).offset()==i*(duration+65),"inter-tap gap");ok(steps.get(i*2+1).offset()==i*(duration+65)+duration,"existing press duration");}
            ArrayList<Integer> events=new ArrayList<>();var delivery=new TouchSequence.Delivery((a,x,y)->{events.add(a);return true;});
            for(var step:steps)ok(delivery.send(step),"valid sequence accepted");
            ok(delivery.finish()&&events.size()==count*2,"balanced completion");delivery.cancel();ok(events.size()==count*2,"completed stroke not cancelled twice");
        }
        var scroll=TouchSequence.create(new float[]{20,150,20,30},260,1,8,100,200);
        ok(scroll.get(0).action()==0&&scroll.get(scroll.size()-1).action()==1,"scroll boundaries");
        long time=-1;float previous=151;
        for(var step:scroll){ok(step.offset()>time&&step.x()==20&&step.y()<=previous,"ordered linear scroll");time=step.offset();previous=step.y();}
        ok(scroll.get(scroll.size()-1).y()==30,"exact scroll endpoint");
        for(int failure:new int[]{0,1,scroll.size()-1}){
            ArrayList<Integer> events=new ArrayList<>();int[] index={0};
            var delivery=new TouchSequence.Delivery((a,x,y)->{events.add(a);return a==3||index[0]++!=failure;});
            for(var step:scroll)if(!delivery.send(step))break;
            ok(!delivery.finish(),"failed sequence cannot complete");
            ok(failure==0?!events.contains(3):events.get(events.size()-1)==3,"accepted down is balanced after failure");
        }
        ArrayList<Integer> events=new ArrayList<>();var delivery=new TouchSequence.Delivery((a,x,y)->{events.add(a);return true;});
        delivery.send(scroll.get(0));delivery.cancel();delivery.cancel();ok(events.equals(List.of(0,3)),"stop sends exactly one cancel");ok(!delivery.send(scroll.get(1)),"stopped sequence cannot resume");
        var incomplete=new TouchSequence.Delivery((a,x,y)->true);incomplete.send(scroll.get(0));ok(!incomplete.finish(),"unbalanced completion rejected");
        for(float[] points:new float[][]{null,new float[]{1},new float[]{Float.NaN,1,2,3},new float[]{-1,1,2,3},new float[]{1,1,100,3},new float[]{1,1,2,200}}){boolean rejected=false;try{TouchSequence.create(points,60,1,8,100,200);}catch(IllegalArgumentException e){rejected=true;}ok(rejected,"invalid coordinates rejected");}
        for(long duration:new long[]{0,-1,1001}){boolean rejected=false;try{TouchSequence.create(new float[]{1,1,2,2},duration,1,8,100,200);}catch(IllegalArgumentException e){rejected=true;}ok(rejected,"unbounded duration rejected");}
        for(int count:new int[]{0,3}){boolean rejected=false;try{TouchSequence.create(new float[]{1,1,2,2},60,count,8,100,200);}catch(IllegalArgumentException e){rejected=true;}ok(rejected,"unbounded repeat rejected");}
        TouchRequestGate gate=new TouchRequestGate();
        ok(!gate.live(1),"no active request by default");gate.cancel(2);ok(!gate.start(2),"cancel before start blocks delayed delivery");
        ok(gate.start(3)&&gate.live(3),"new request survives earlier cancellation");
        ok(!gate.cancel(2)&&gate.live(3),"late old cancellation cannot stop a newer request");
        ok(!gate.start(3)&&!gate.start(1),"duplicate and stale starts rejected");
        ok(gate.cancel(3)&&!gate.live(3),"current request cancels");
        ok(gate.start(4),"next explicit request accepted");gate.close();ok(!gate.live(4)&&!gate.start(5),"dead endpoint cannot revive a stroke");
        for(boolean hostDies:new boolean[]{false,true}){
            class End implements WindowLeaseStore.Endpoint {boolean alive=true;Runnable death;public void link(Runnable r){death=r;}public void unlink(Runnable r){death=null;}public boolean alive(){return alive;}void die(){alive=false;if(death!=null)death.run();}}
            End control=new End(),host=new End();TouchRequestGate owner=new TouchRequestGate();ArrayList<Integer> sent=new ArrayList<>();
            var stroke=new TouchSequence.Delivery((action,x,y)->{sent.add(action);return true;});
            var leases=new WindowLeaseStore<String,Integer>(new WindowLeaseStore.Windows<Integer>(){public Integer createToken(){return 1;}public void add(Integer token){}public void remove(Integer token){owner.close();stroke.cancel();}},1);
            try{leases.grant("owner",control,host);}catch(Exception failure){throw new AssertionError(failure);}
            owner.start(1);stroke.send(new TouchSequence.Step(0,0,20,30));(hostDies?host:control).die();
            ok(sent.equals(List.of(0,3)),"endpoint death balances its active pointer");
            ok(!owner.live(1)&&!owner.start(2),"endpoint death revokes queued input");
            ok(leases.liveToken("owner")==null,"dead endpoint cannot acquire its old token");
        }
        System.out.println("PASS "+checks+" touch sequence assertions");
    }
}
