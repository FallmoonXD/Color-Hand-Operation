package replica;

/** A color draft. Hue/SV editing preserves the exact alpha byte; visibility is a separate setting. */
public final class ColorPaletteState {
    private final int initialColor,initialOpacity;
    private int color,opacity;
    private float hue,saturation,value;
    public ColorPaletteState(int argb,int opacityPercent){initialColor=argb;initialOpacity=clamp(opacityPercent,0,100);opacity=initialOpacity;setColor(argb);}
    public int color(){return color;}public int opacity(){return opacity;}public float hue(){return hue;}public float saturation(){return saturation;}public float value(){return value;}
    public int effectiveColor(){return effective(color,opacity);}
    public void setOpacity(int percent){opacity=clamp(percent,0,100);}
    public void setColor(int argb){color=argb;float r=((argb>>16)&255)/255f,g=((argb>>8)&255)/255f,b=(argb&255)/255f;float max=Math.max(r,Math.max(g,b)),min=Math.min(r,Math.min(g,b)),d=max-min;value=max;saturation=max==0?0:d/max;if(d>0){float h=max==r?(g-b)/d:max==g?2+(b-r)/d:4+(r-g)/d;hue=(h*60+360)%360;}}
    public void setRgb(int rgb){setColor((color&0xff000000)|(rgb&0xffffff));}
    public void setHue(float degrees){hue=normalizeHue(degrees);color=hsv(color>>>24,hue,saturation,value);}
    public void setSV(float s,float v){saturation=unit(s);value=unit(v);color=hsv(color>>>24,hue,saturation,value);}
    public void restore(){opacity=initialOpacity;hue=0;setColor(initialColor);}
    public static int effective(int argb,int opacity){return (Math.round((argb>>>24)*clamp(opacity,0,100)/100f)<<24)|(argb&0xffffff);}
    public static String hex(int argb){return String.format(java.util.Locale.ROOT,"#%08X",argb);}
    public static Integer parse(String text){if(text==null)return null;String s=text.trim();if(!s.matches("#[0-9a-fA-F]{6}([0-9a-fA-F]{2})?"))return null;long value=Long.parseLong(s.substring(1),16);return (int)(s.length()==7?value|0xff000000L:value);}
    public static int hsv(int alpha,float h,float s,float v){h=normalizeHue(h);s=unit(s);v=unit(v);float c=v*s,x=c*(1-Math.abs((h/60)%2-1)),m=v-c;float r=0,g=0,b=0;if(h<60){r=c;g=x;}else if(h<120){r=x;g=c;}else if(h<180){g=c;b=x;}else if(h<240){g=x;b=c;}else if(h<300){r=x;b=c;}else{r=c;b=x;}return (clamp(alpha,0,255)<<24)|(Math.round((r+m)*255)<<16)|(Math.round((g+m)*255)<<8)|Math.round((b+m)*255);}
    public static float hueAt(float x,float y){return normalizeHue((float)Math.toDegrees(Math.atan2(-y,x)));}
    private static float normalizeHue(float h){return Float.isFinite(h)?((h%360)+360)%360:0;}
    private static float unit(float f){return Float.isNaN(f)?0:Math.max(0,Math.min(1,f));}
    private static int clamp(int v,int min,int max){return Math.max(min,Math.min(max,v));}
}
