package replica;

/** Analytic COUI response/bounce spring, independent of frame rate and Android. */
public final class ColorControlPhysics {
    public double position,velocity;
    public ColorControlPhysics(double position){this.position=position;}
    public void step(double target,double response,double bounce,double seconds){
        if(seconds<=0)return;
        if(!Double.isFinite(target)||!Double.isFinite(response)||response<=0||!Double.isFinite(seconds))throw new IllegalArgumentException("Invalid spring input");
        double w=2*Math.PI/response,z=Math.max(0,1-bounce),x=position-target,v=velocity;
        if(z<1){double wd=w*Math.sqrt(1-z*z),b=(v+z*w*x)/wd,e=Math.exp(-z*w*seconds),co=Math.cos(wd*seconds),si=Math.sin(wd*seconds);double q=e*(x*co+b*si);velocity=-z*w*q+e*wd*(-x*si+b*co);position=target+q;}
        else if(z==1){double b=v+w*x,e=Math.exp(-w*seconds),q=(x+b*seconds)*e;position=target+q;velocity=b*e-w*q;}
        else{double root=Math.sqrt(z*z-1),a=-w*(z-root),b=-w*(z+root),c=(v-b*x)/(a-b),d=x-c,ea=Math.exp(a*seconds),eb=Math.exp(b*seconds);position=target+c*ea+d*eb;velocity=a*c*ea+b*d*eb;}
    }
    public static float clamp(float value){return Math.max(0,Math.min(1,value));}
    public static float fraction(int value,int min,int max){return max<=min?0:clamp((float)(((double)value-min)/((double)max-min)));}
    public static float physicalFraction(float checkedFraction,boolean rtl){return rtl?1-checkedFraction:checkedFraction;}
    public static boolean checkedAt(float physicalFraction,boolean rtl){return rtl?physicalFraction<.5f:physicalFraction>=.5f;}
    public static float switchStretch(float travel){float p=clamp(travel),peak=133f/383f;return p<peak?1+.3f*p/peak:1.3f-.3f*(p-peak)/(1-peak);}
}
