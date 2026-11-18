package dev.edgeflow;

import android.os.*;
import android.view.*;
import java.util.Map;

/** The same Android call and legacy interpretation for both input backends. */
public final class GestureHapticPlayer {
    public record Result(String mode,boolean requested,boolean accepted,int duration,int amplitude){}
    public static Result perform(View surface,Map<String,?> values,boolean longer){
        if(GestureHaptics.legacyMuted(values))return new Result("muted",false,false,0,0);
        if(!GestureHaptics.custom(values))return new Result("system",true,surface.performHapticFeedback(HapticFeedbackConstants.GESTURE_END),0,0);
        boolean legacy=GestureHaptics.legacyAmplitude(values);
        int duration=legacy?(longer?18:8):GestureHaptics.level(values);
        int amplitude=legacy?GestureHaptics.amplitude(values):VibrationEffect.DEFAULT_AMPLITUDE;
        if(duration==0)return new Result("muted",false,false,0,amplitude);
        try{
            VibratorManager manager=surface.getContext().getSystemService(VibratorManager.class);
            Vibrator vibrator=manager==null?null:manager.getDefaultVibrator();
            if(vibrator!=null&&vibrator.hasVibrator()){
                vibrator.vibrate(VibrationEffect.createOneShot(duration,amplitude));return new Result("custom",true,true,duration,amplitude);
            }
        }catch(RuntimeException ignored){}
        return new Result("custom",false,false,duration,amplitude);
    }
    private GestureHapticPlayer(){}
}
