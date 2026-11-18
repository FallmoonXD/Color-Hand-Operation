package dev.edgeflow.lsp;

import java.util.ArrayList;
import java.util.List;

/** Bounded single-pointer strokes used by the pad; timings match its accessibility path. */
final class TouchSequence {
    static final int DOWN=0,UP=1,MOVE=2,CANCEL=3;
    record Step(long offset,int action,float x,float y){}
    static List<Step> create(float[] points,long duration,int count,int interval,int width,int height){
        if(points==null||points.length!=4||duration<1||duration>1000||count<1||count>2||interval<1||interval>32)throw new IllegalArgumentException("Invalid touch sequence");
        for(int i=0;i<4;i++)if(!Float.isFinite(points[i])||points[i]<0||points[i]>=(i%2==0?width:height))throw new IllegalArgumentException("Touch outside display");
        ArrayList<Step> steps=new ArrayList<>();
        for(int n=0;n<count;n++){
            long start=n*(duration+65);steps.add(new Step(start,DOWN,points[0],points[1]));
            if(points[0]!=points[2]||points[1]!=points[3])for(long t=interval;t<duration;t+=interval){float f=(float)t/duration;steps.add(new Step(start+t,MOVE,points[0]+(points[2]-points[0])*f,points[1]+(points[3]-points[1])*f));}
            steps.add(new Step(start+duration,UP,points[2],points[3]));
        }
        return List.copyOf(steps);
    }
    interface Sink {boolean send(int action,float x,float y);}
    static final class Delivery {
        private final Sink sink;private boolean down,ended;private float x,y;
        Delivery(Sink sink){this.sink=sink;}
        boolean send(Step step){
            if(ended)return false;
            if((step.action==DOWN)==down){cancel();return false;}
            x=step.x;y=step.y;
            if(!sink.send(step.action,x,y)){cancel();return false;}
            if(step.action==DOWN)down=true;else if(step.action==UP)down=false;
            return true;
        }
        void cancel(){if(ended)return;ended=true;if(down){down=false;sink.send(CANCEL,x,y);}}
        boolean finish(){if(ended||down){cancel();return false;}ended=true;return true;}
    }
    private TouchSequence(){}
}
