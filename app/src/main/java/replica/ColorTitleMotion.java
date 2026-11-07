package replica;

/** Scroll-driven title geometry; no clock, inertia or independent animation state. */
public final class ColorTitleMotion {
    public static float progress(float scroll,float distance){return clamp(scroll/Math.max(1,distance));}
    public static float maskOpacity(float progress){return .55f+.45f*clamp(progress);}
    public static float ease(float value){
        float x=clamp(value);if(x==0||x==1)return x;
        // Settings COUIEaseInterpolator: cubic Bezier (.33,0,.67,1).
        double low=0,high=1,t=.5;
        for(int i=0;i<20;i++){t=(low+high)*.5;double u=1-t,bx=3*u*u*t*.33+3*u*t*t*.67+t*t*t;if(bx<x)low=t;else high=t;}
        return (float)(3*(1-t)*t*t+t*t*t);
    }
    public static float lerp(float start,float end,float progress){return start+(end-start)*ease(progress);}
    public static float fittedSize(float size,float available,float measured){
        return measured>available&&measured>0?size*available/measured:size;
    }
    private static float clamp(float value){return Float.isNaN(value)?0:Math.max(0,Math.min(1,value));}
    private ColorTitleMotion(){}
}
