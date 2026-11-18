package dev.edgeflow;

import java.util.Map;

/** Shared effective handle color, including old floating opacity fractions. */
public final class GestureAppearance {
    public static int opacityPercent(Object value){
        if(!(value instanceof Number))return 0;
        double amount=((Number)value).doubleValue();if(!Double.isFinite(amount))return 0;
        boolean integral=value instanceof Integer||value instanceof Long||value instanceof Short||value instanceof Byte||value instanceof java.math.BigInteger;
        if(!integral&&amount>=0&&amount<=1)amount*=100;
        return (int)Math.round(Math.max(0,Math.min(100,amount)));
    }
    public static int visibleColor(Map<String,?> values,String id){
        Object color=values.get(id+"_color");if(!(color instanceof Number))color=values.get("handle_color");
        int argb=color instanceof Number?((Number)color).intValue():0xff188dff;
        return (Math.round((argb>>>24)*opacityPercent(values.get("handle_opacity"))/100f)<<24)|(argb&0x00ffffff);
    }
    private GestureAppearance(){}
}
