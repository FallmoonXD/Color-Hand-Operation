package dev.edgeflow;

import java.util.Map;

/** Four independent real recognition boundaries, expressed from the inward horizontal. */
public final class GestureAngles {
    private static final String[] NAMES={"up_outer","up_inner","down_inner","down_outer"};
    public final float upOuter,upInner,downInner,downOuter;
    public GestureAngles(float upOuter,float upInner,float downInner,float downOuter){
        this.upOuter=bounded(upOuter,75,85,80);this.upInner=bounded(upInner,5,70,30);
        this.downInner=bounded(downInner,5,70,30);this.downOuter=bounded(downOuter,75,85,80);
    }
    public static String key(boolean longer,int index){return (longer?"long":"short")+"_angle_"+NAMES[index]+"_degrees";}
    public static int min(int index){return index==0||index==3?75:5;}
    public static int max(int index){return index==0||index==3?85:70;}
    public static boolean isKey(String key){return key.matches("(short|long)_angle_(up|down)_(outer|inner)_degrees");}
    public static float value(Map<String,?> p,boolean longer,int index){
        // Existing users retain the former 30°/80° default and any explicit symmetric angle.
        Object old=p.get("direction_angle_degrees");float legacy=old instanceof Number?((Number)old).floatValue():30;
        float fallback=index==0||index==3?80:bounded(legacy,10,70,30);
        Object saved=p.get(key(longer,index));return saved instanceof Number?bounded(((Number)saved).floatValue(),min(index),max(index),fallback):fallback;
    }
    public static GestureAngles read(Map<String,?> p,boolean longer){return new GestureAngles(value(p,longer,0),value(p,longer,1),value(p,longer,2),value(p,longer,3));}
    /** Downward screen coordinates are positive. Near-vertical motion remains non-actionable. */
    public boolean accepts(float angle){return angle>-upOuter&&angle<downOuter;}
    public int direction(float angle){return angle<-upInner?GestureRecognizer.UP:angle>downInner?GestureRecognizer.DOWN:GestureRecognizer.STRAIGHT;}
    private static float bounded(float v,float min,float max,float fallback){return Float.isNaN(v)||Float.isInfinite(v)?fallback:Math.max(min,Math.min(max,v));}
}
