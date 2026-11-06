package dev.edgeflow;

/** Presentation of the existing amplitude scale; never converts or writes stored values. */
final class SettingsDisplay {
    static int vibrationPercent(int amplitude) {
        int value=Math.max(0,Math.min(255,amplitude));
        // Zero has its own meaning. A small explicit amplitude must not be labelled
        // as the system-feedback sentinel merely because its percentage rounds down.
        return value==0?0:Math.max(1,Math.round(value*100f/255));
    }
    static int vibrationLevelPercent(float level){return Math.round(Math.max(0,Math.min(30,level))*100f/30);}
    /** Integer readout only; never rounds or rewrites the stored setting. */
    static String number(float value,String unit,boolean integer){
        boolean multiplier=unit.equals("×");
        double shown=multiplier||(unit.equals("%")&&!integer)?value*100d:value;
        return Math.round(shown)+(multiplier?"%":unit);
    }
    private SettingsDisplay() {}
}
