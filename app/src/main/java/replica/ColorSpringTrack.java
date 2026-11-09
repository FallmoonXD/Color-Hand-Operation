package replica;

/** A finite COUI spring in real seconds. ValueAnimator applies the device's duration scale once. */
public final class ColorSpringTrack {
    private final ColorControlPhysics state;
    private final double start,target,response,bounce;
    public final int durationMs;

    public static ColorSpringTrack popupOpacity(float start,boolean enter){
        return new ColorSpringTrack(start,enter?1:0,enter?.27:.23,0,.001);
    }
    public static ColorSpringTrack popupScale(float start,boolean enter){
        return new ColorSpringTrack(start,enter?1:0,enter?.40:.21,enter?.2:0,.001);
    }
    public static ColorSpringTrack backArrow(float depth){
        return new ColorSpringTrack(depth,0,.1,0,1);
    }
    static ColorSpringTrack sidebar(float start,float end,boolean enter,boolean position,boolean width){
        double response=position?(enter?.6:.4):(enter?(width?.4:.5):.45);
        double bounce=enter?(position?.3:width?.35:.25):.15;
        return new ColorSpringTrack(start,end,response,bounce,1);
    }
    private ColorSpringTrack(double start,double target,double response,double bounce,double minVisibleChange){
        if(!Double.isFinite(start)||!Double.isFinite(target)||!Double.isFinite(response)||response<=0
                ||!Double.isFinite(bounce)||bounce<0||bounce>=1||!Double.isFinite(minVisibleChange)||minVisibleChange<=0)
            throw new IllegalArgumentException("Invalid spring");
        this.start=start;this.target=target;this.response=response;this.bounce=bounce;
        state=new ColorControlPhysics(start);
        // COUIDynamicAnimation and COUISpringForce use 3/4 of the minimum
        // visible change and a velocity threshold 62.5 times that distance.
        double threshold=.75*minVisibleChange;
        int time=0;
        while(Math.abs(state.position-target)>=threshold||Math.abs(state.velocity)>=threshold*62.5){
            time++;
            state.step(target,response,bounce,.001);
        }
        durationMs=time;
    }
    public float valueAt(float millis){
        if(millis>=durationMs)return (float)target;
        state.position=start;state.velocity=0;
        state.step(target,response,bounce,Math.max(0,millis)/1000d);
        return (float)state.position;
    }
}
