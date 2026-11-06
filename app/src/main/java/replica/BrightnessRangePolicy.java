package replica;

/** Bounds and settings commands only; no Android or device-specific defaults. */
final class BrightnessRangePolicy {
    private BrightnessRangePolicy(){}
    static final class Range {
        final int min,max;
        Range(int min,int max){this.min=min;this.max=max;}
        boolean same(Range other){return other!=null&&min==other.min&&max==other.max;}
        int progress(int raw){return (int)Math.max(0,Math.min((long)max-min,(long)raw-min));}
        int raw(int progress){return (int)Math.max(min,Math.min((long)max,(long)min+progress));}
    }
    interface Probe {Range read() throws Exception;}
    static Range values(int min,int max){
        // Android/Oplus raw settings use integer levels; reject sentinels, reversal and
        // implausible/overflowing values instead of guessing a phone-specific range.
        return min>=0&&max>min&&max<=65535?new Range(min,max):null;
    }
    static Range parse(String value){
        if(value==null)return null;
        String[] parts=value.split(",",-1);if(parts.length!=2)return null;
        try{return values(Integer.parseInt(parts[0].trim()),Integer.parseInt(parts[1].trim()));}
        catch(NumberFormatException invalid){return null;}
    }
    static Range probe(Probe... probes){
        for(Probe probe:probes){
            try{Range result=probe.read();if(result!=null)return result;}
            catch(Exception|LinkageError unavailable){/* Try the next independent source. */}
        }
        return null;
    }
    static String command(Range range,boolean oplusMultibits,int progress){
        if(range==null)throw new IllegalStateException("Unknown brightness range");
        int raw=range.raw(progress);
        String result="settings put system screen_brightness "+raw;
        // AOSP uses an adjustment fraction here. A raw level is valid only with an
        // explicitly confirmed Oplus multibits feature, never merely max > 255.
        return oplusMultibits?result+" && settings put system screen_auto_brightness_adj "+raw+".0":result;
    }
}
