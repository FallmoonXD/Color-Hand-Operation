package dev.edgeflow;

/** Pure geometry and a reversible drag transaction for the four OHO boundaries. */
public final class AngleEditorGeometry {
    public static final class Point {
        public final float x,y;
        Point(float x,float y){this.x=x;this.y=y;}
    }
    public static float signed(int index,float degrees){return index<2?-degrees:degrees;}
    public static Point point(float cx,float cy,float radius,boolean mirrored,int index,float degrees){
        double angle=Math.toRadians(signed(index,degrees));return new Point(cx+(mirrored?-1:1)*radius*(float)Math.cos(angle),cy+radius*(float)Math.sin(angle));
    }
    /** Select a knob first. Interior blank space and the origin are never editable. */
    public static int hit(float x,float y,float cx,float cy,float radius,boolean mirrored,float[] values,float knobSlop,float lineSlop){
        int selected=-1;float best=Float.MAX_VALUE;
        for(int i=0;i<4;i++){Point p=point(cx,cy,radius,mirrored,i,values[i]);float d=distance(x,y,p.x,p.y);if(d<=knobSlop&&d<best){selected=i;best=d;}}
        if(selected>=0)return selected;
        best=Float.MAX_VALUE;
        for(int i=0;i<4;i++){
            Point p=point(cx,cy,radius,mirrored,i,values[i]);float dx=p.x-cx,dy=p.y-cy;
            float projection=((x-cx)*dx+(y-cy)*dy)/(radius*radius);
            if(projection<.48f||projection>1f)continue;
            float d=distance(x,y,cx+projection*dx,cy+projection*dy);if(d<=lineSlop&&d<best){selected=i;best=d;}
        }
        return selected;
    }
    public static float pointerAngle(float x,float y,float cx,float cy,boolean mirrored,int index){
        float inward=(x-cx)*(mirrored?-1:1),vertical=(y-cy)*(index<2?-1:1);
        return (float)Math.toDegrees(Math.atan2(vertical,Math.max(.001f,inward)));
    }
    public static float clamp(int index,float value){return Math.max(GestureAngles.min(index),Math.min(GestureAngles.max(index),value));}
    public static float[] copy(float[] values){
        if(values==null||values.length!=4)throw new IllegalArgumentException("Four gesture angle boundaries required");
        float[] out=values.clone();for(int i=0;i<4;i++){if(Float.isNaN(out[i])||Float.isInfinite(out[i]))throw new IllegalArgumentException("Finite gesture angles required");out[i]=clamp(i,out[i]);}return out;
    }
    private static float distance(float x,float y,float a,float b){return (float)Math.hypot(x-a,y-b);}
    public static final class Drag {
        private final float[] values;
        private float original,grabAngle;
        private int active=-1;
        public Drag(float[] initial){values=copy(initial);}
        public void replace(float[] next){float[] clean=copy(next);cancel();System.arraycopy(clean,0,values,0,4);}
        public float[] values(){return values.clone();}
        public float value(int index){return values[index];}
        public int active(){return active;}
        public void begin(int index,float pointerAngle){cancel();active=index;original=values[index];grabAngle=pointerAngle;}
        public boolean update(float pointerAngle){if(active<0||Float.isNaN(pointerAngle)||Float.isInfinite(pointerAngle))return false;float next=clamp(active,original+pointerAngle-grabAngle);if(Float.compare(next,values[active])==0)return false;values[active]=next;return true;}
        public boolean changed(){return active>=0&&Float.compare(values[active],original)!=0;}
        public void commit(){active=-1;}
        public void cancel(){if(active>=0)values[active]=original;active=-1;}
        public boolean set(int index,float value){if(Float.isNaN(value)||Float.isInfinite(value))return false;float next=clamp(index,value);if(Float.compare(values[index],next)==0)return false;values[index]=next;return true;}
    }
    private AngleEditorGeometry(){}
}
