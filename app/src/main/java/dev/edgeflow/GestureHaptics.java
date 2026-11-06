package dev.edgeflow;

import java.util.Map;

/** OHO feedback timing and level, with read-only compatibility for saved amplitudes. */
public final class GestureHaptics {
    public static boolean quick(Map<String,?> p){return bool(p,"quick_vibration",true);}
    public static boolean legacyMuted(Map<String,?> p){return !bool(p,"vibration",bool(p,"haptics",true));}
    public static boolean custom(Map<String,?> p){return bool(p,"custom_vibration",legacyMuted(p)||amplitude(p)>0);}
    public static boolean legacyAmplitude(Map<String,?> p){return custom(p)&&!p.containsKey("vibration_duration_ms")&&amplitude(p)>0;}
    public static int amplitude(Map<String,?> p){return clamp(number(p,"vibration_strength",0),0,255);}
    /** A legacy amplitude remains unchanged in runtime until a new level is committed. */
    public static int level(Map<String,?> p){
        if(legacyMuted(p))return 0;
        if(p.containsKey("vibration_duration_ms"))return clamp(number(p,"vibration_duration_ms",5),0,30);
        return legacyAmplitude(p)?Math.max(1,Math.round(amplitude(p)*30f/255f)):5;
    }
    private static boolean bool(Map<String,?> p,String key,boolean fallback){Object v=p.get(key);return v instanceof Boolean?(Boolean)v:fallback;}
    private static int number(Map<String,?> p,String key,int fallback){Object v=p.get(key);return v instanceof Number?((Number)v).intValue():fallback;}
    private static int clamp(int n,int min,int max){return Math.max(min,Math.min(max,n));}
    private GestureHaptics(){}
}
