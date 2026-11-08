package dev.edgeflow;

import android.content.SharedPreferences;
import android.content.Context;
import java.util.Map;
import java.util.HashMap;
import java.util.HashSet;

/** Geometry used by the settings controls, including old center/height preferences. */
final class HandleSettings {
    static final String VERSION_KEY="handle_geometry_version";
    static final int VERSION=2;
    static final class Geometry {
        final float width, start, end, landscapeWidth;
        Geometry(float width, float start, float end) {
            this(width,start,end,0);
        }
        Geometry(float width,float start,float end,float landscapeWidth){
            if(!Float.isFinite(width))width=12.3f;if(!Float.isFinite(start))start=0;if(!Float.isFinite(end))end=1;
            this.width = Math.max(2, Math.min(30, width));
            this.start = Math.max(0, Math.min(.95f, start));
            this.end = Math.max(this.start + .05f, Math.min(1, end));
            this.landscapeWidth=Float.isFinite(landscapeWidth)&&landscapeWidth>0?Math.max(2,Math.min(30,landscapeWidth)):0;
        }
        float effectiveLandscapeWidth(){return landscapeWidth==0?width:landscapeWidth;}
        Geometry withLandscapeWidth(float value){return new Geometry(width,start,end,value);}
        float size() { return end - start; }
        float position() { return (1 - size()) < .0001f ? .5f : start / (1 - size()); }
        Geometry resized(float size) {
            size = Math.max(.05f, Math.min(1, size));
            float top = Math.max(0, Math.min(1 - size, (start + end - size) / 2));
            return new Geometry(width, top, top + size,landscapeWidth);
        }
        Geometry moved(float position) {
            float top = Math.max(0, Math.min(1, position)) * (1 - size());
            return new Geometry(width, top, top + size(),landscapeWidth);
        }
        int sliderProgress(int property) {
            if(property==3)return Math.round(effectiveLandscapeWidth()-2);
            float fraction = property == 0 ? (width - 2) / 28 : property == 1 ? (size() - .05f) / .95f : 1 - position();
            return Math.round(Math.max(0, Math.min(1, fraction)) * 1000);
        }
        Geometry withSliderProgress(int property, int progress) {
            if(property==3)return withLandscapeWidth(2+Math.max(0,Math.min(28,progress)));
            float fraction = Math.max(0, Math.min(1000, progress)) / 1000f;
            return property == 0 ? new Geometry(2 + 28 * fraction, start, end,landscapeWidth) : property == 1 ? resized(.05f + .95f * fraction) : moved(1 - fraction);
        }
    }
    static Geometry read(SharedPreferences p, String id) {
        return read(p.getAll(),id);
    }
    private static float number(Map<String,?> p,String key,float fallback){Object value=p.get(key);return value instanceof Number&&Float.isFinite(((Number)value).floatValue())?((Number)value).floatValue():fallback;}
    static int version(Map<String,?> p){return (int)number(p,VERSION_KEY,1);}
    static Geometry read(Map<String,?> p, String id) {
        boolean extra = GestureHandleConfig.isExtra(id);
        float width = number(p,id + "_handle_width_dp", extra ? 12.3f : number(p,"handle_width_dp", 12.3f));
        float start = number(p,id + "_handle_start_fraction", extra ? GestureHandleConfig.defaultStart(id) : number(p,"handle_start_fraction", .13084841f));
        float end = number(p,id + "_handle_end_fraction", extra ? GestureHandleConfig.defaultEnd(id) : number(p,"handle_end_fraction", 1f));
        if (!extra && (p.containsKey("handle_center_fraction") || p.containsKey("handle_height_fraction"))) {
            float center = number(p,"handle_center_fraction", .5654242f), height = number(p,"handle_height_fraction", .8691516f);
            start = center - height / 2; end = center + height / 2;
        }
        return new Geometry(width, start, end,number(p,id+"_landscape_handle_width_dp",0));
    }
    static Geometry migrateLegacyGeometry(Geometry legacy,GestureHandleConfig.Area portrait){float[] range=GestureHandleConfig.migrateFractions(legacy.start,legacy.end,portrait);return new Geometry(legacy.width,range[0],range[1],legacy.landscapeWidth);}
    /** Snapshot first: the shared center/height alias must affect both sides before removal. */
    static Map<String,Object> migrateMap(Map<String,?> old,GestureHandleConfig.Area portrait){
        Map<String,Object> result=new HashMap<>(old);if(version(old)>=VERSION)return result;
        if(portrait==null)throw new IllegalStateException("Portrait display reference unavailable");
        Geometry[] effective=new Geometry[GestureHandleConfig.IDS.length];for(int i=0;i<effective.length;i++)effective[i]=read(old,GestureHandleConfig.IDS[i]);
        for(int i=0;i<effective.length;i++){String id=GestureHandleConfig.IDS[i];Geometry converted=migrateLegacyGeometry(effective[i],portrait);result.put(id+"_handle_start_fraction",converted.start);result.put(id+"_handle_end_fraction",converted.end);}
        // Six independent spans prevent old shared fallbacks from being reinterpreted.
        // Width/color/actions/enabled flags are not materialized or changed.
        for(String key:new String[]{"handle_start_fraction","handle_end_fraction","handle_center_fraction","handle_height_fraction"})result.remove(key);
        result.put(VERSION_KEY,VERSION);return result;
    }
    private static boolean rangeKey(String key){return key.equals("handle_start_fraction")||key.equals("handle_end_fraction")||key.equals("handle_center_fraction")||key.equals("handle_height_fraction")||key.matches("(extra2?_)?(left|right)_handle_(start|end)_fraction");}
    static boolean needsReference(Map<String,?> existing,Map<String,?> incoming,boolean replace){
        if(replace)return version(incoming)<VERSION;
        if(!existing.isEmpty()&&version(existing)<VERSION)return true;
        if(version(incoming)<VERSION)for(String key:incoming.keySet())if(rangeKey(key))return true;
        return false;
    }
    private static void validateRanges(Map<String,?> values,Iterable<String> ids){
        for(String id:ids){boolean extra=GestureHandleConfig.isExtra(id);float start=number(values,id+"_handle_start_fraction",extra?GestureHandleConfig.defaultStart(id):number(values,"handle_start_fraction",.13084841f));float end=number(values,id+"_handle_end_fraction",extra?GestureHandleConfig.defaultEnd(id):number(values,"handle_end_fraction",1f));
            if(!extra&&(values.containsKey("handle_center_fraction")||values.containsKey("handle_height_fraction"))){float center=number(values,"handle_center_fraction",.5654242f),height=number(values,"handle_height_fraction",.8691516f);start=center-height/2;end=center+height/2;}
            if(end-start<.05f-1e-6f)throw new IllegalArgumentException("手势区域终点必须高于起点至少 5%");
        }
    }
    /** Normalize legacy imports before the caller's one transactional preferences write. */
    static Map<String,Object> prepareImport(Map<String,?> existing,Map<String,?> incoming,boolean replace,GestureHandleConfig.Area portrait){
        if(replace){validateRanges(incoming,java.util.Arrays.asList(GestureHandleConfig.IDS));Map<String,Object> result=version(incoming)<VERSION?migrateMap(incoming,portrait):new HashMap<>(incoming);result.put(VERSION_KEY,VERSION);return result;}
        Map<String,Object> base=existing.isEmpty()?new HashMap<>():migrateMap(existing,portrait);
        base.put(VERSION_KEY,VERSION);
        if(version(incoming)>=VERSION){base.putAll(incoming);validateRanges(base,java.util.Arrays.asList(GestureHandleConfig.IDS));return base;}
        HashSet<String> affected=new HashSet<>();
        for(String key:incoming.keySet())if(rangeKey(key)){
            if(key.startsWith("handle_")){affected.add("left");affected.add("right");}
            else for(String id:GestureHandleConfig.IDS)if(key.startsWith(id+"_handle_"))affected.add(id);
        }
        if(affected.isEmpty()){for(Map.Entry<String,?> e:incoming.entrySet())if(!e.getKey().equals(VERSION_KEY))base.put(e.getKey(),e.getValue());return base;}
        if(portrait==null)throw new IllegalStateException("Portrait display reference unavailable");
        Map<String,Object> legacy=new HashMap<>(base);
        for(String id:GestureHandleConfig.IDS){Geometry g=read(base,id);legacy.put(id+"_handle_start_fraction",(portrait.top+g.start*portrait.height())/portrait.displayHeight);legacy.put(id+"_handle_end_fraction",(portrait.top+g.end*portrait.height())/portrait.displayHeight);}
        Geometry oldLeft=read(legacy,"left"),oldRight=read(legacy,"right");
        // Shared fields in an old file override target-specific modern values.
        for(String edge:new String[]{"start","end"})if(incoming.containsKey("handle_"+edge+"_fraction"))for(String id:new String[]{"left","right"})legacy.remove(id+"_handle_"+edge+"_fraction");
        legacy.putAll(incoming);
        if(incoming.containsKey("handle_center_fraction")!=incoming.containsKey("handle_height_fraction")){
            // A legacy partial edit inherits the other component per side, not
            // the retired global defaults. A complete old backup retains its
            // own original fallback semantics in the replace branch above.
            for(String id:new String[]{"left","right"}){Geometry old=id.equals("left")?oldLeft:oldRight;float center=number(incoming,"handle_center_fraction",(old.start+old.end)/2),height=number(incoming,"handle_height_fraction",old.end-old.start);legacy.put(id+"_handle_start_fraction",center-height/2);legacy.put(id+"_handle_end_fraction",center+height/2);}
            legacy.remove("handle_center_fraction");legacy.remove("handle_height_fraction");
        }
        legacy.put(VERSION_KEY,1);validateRanges(legacy,affected);Map<String,Object> converted=migrateMap(legacy,portrait);
        for(Map.Entry<String,?> e:incoming.entrySet())if(!rangeKey(e.getKey())&&!e.getKey().equals(VERSION_KEY))base.put(e.getKey(),e.getValue());
        for(String id:affected)for(String edge:new String[]{"start","end"}){String key=id+"_handle_"+edge+"_fraction";base.put(key,converted.get(key));}
        return base;
    }
    static synchronized boolean ensureSafeArea(Context context,SharedPreferences prefs){
        Map<String,?> old=prefs.getAll();if(version(old)>=VERSION)return true;
        try{return migrate(prefs,HandleDisplayArea.read(context).portrait);}catch(RuntimeException unavailable){android.util.Log.w("EdgeGesture","Safe-area migration deferred until display metrics are available",unavailable);return false;}
    }
    static synchronized boolean migrate(SharedPreferences prefs,GestureHandleConfig.Area portrait){
        Map<String,?> old=prefs.getAll();if(version(old)>=VERSION)return true;
        Map<String,Object> converted=migrateMap(old,portrait);SharedPreferences.Editor editor=prefs.edit();
        for(String key:old.keySet())if(!converted.containsKey(key))editor.remove(key);
        for(String id:GestureHandleConfig.IDS)for(String edge:new String[]{"start","end"}){String key=id+"_handle_"+edge+"_fraction";editor.putFloat(key,((Number)converted.get(key)).floatValue());}
        return editor.putInt(VERSION_KEY,VERSION).commit();
    }
    static String peer(String id) { return id.endsWith("left") ? id.substring(0, id.length() - 4) + "right" : id.substring(0, id.length() - 5) + "left"; }
    static String linkedKey(String id) { return id.startsWith("extra2_") ? "extra2_handles_linked" : id.startsWith("extra_") ? "extra_handles_linked" : "handles_linked"; }
    static boolean linked(SharedPreferences p, String id) {
        Geometry a = read(p, id), b = read(p, peer(id));
        boolean same = Math.abs(a.width - b.width) < .001f && Math.abs(a.landscapeWidth-b.landscapeWidth)<.001f && Math.abs(a.start - b.start) < .0001f && Math.abs(a.end - b.end) < .0001f;
        return p.getBoolean(linkedKey(id), same);
    }
    static boolean hideInLandscape(SharedPreferences p){
        return p.getBoolean("hide_landscape",false)||!p.getBoolean("landscape_enabled",true);
    }
    private static void put(SharedPreferences.Editor e, String id, Geometry g) {
        e.putFloat(id + "_handle_width_dp", g.width).putFloat(id + "_handle_start_fraction", g.start).putFloat(id + "_handle_end_fraction", g.end);
        putLandscapeWidth(e,id,g.landscapeWidth);
    }
    private static void putLandscapeWidth(SharedPreferences.Editor e,String id,float width){
        if(width>0)e.putFloat(id+"_landscape_handle_width_dp",width);
        else e.remove(id+"_landscape_handle_width_dp");
    }
    static void writeLandscapeWidth(SharedPreferences p,String id,Geometry geometry,boolean both){
        SharedPreferences.Editor e=p.edit();
        putLandscapeWidth(e,id,geometry.landscapeWidth);
        if(both)putLandscapeWidth(e,peer(id),geometry.landscapeWidth);
        e.apply();
    }
    static void write(SharedPreferences p, String id, Geometry g, boolean both) {
        SharedPreferences.Editor e = p.edit();
        // Materialize both old effective regions before removing their shared override.
        if (!GestureHandleConfig.isExtra(id) && (p.contains("handle_center_fraction") || p.contains("handle_height_fraction"))) {
            put(e, "left", read(p, "left")); put(e, "right", read(p, "right"));
            e.remove("handle_center_fraction").remove("handle_height_fraction");
        }
        put(e, id, g);
        if (both) put(e, peer(id), g);
        e.apply();
    }
    static int pairCount(SharedPreferences p) {
        int count = Math.max(1, Math.min(3, p.getInt("handle_pair_count", 1)));
        if (p.getBoolean("extra_left_enabled", false) || p.getBoolean("extra_right_enabled", false)) count = Math.max(2, count);
        if (p.getBoolean("extra2_left_enabled", false) || p.getBoolean("extra2_right_enabled", false)) count = 3;
        return count;
    }
    static boolean hasLongGestures(SharedPreferences prefs){
        int count=pairCount(prefs);
        for(int i=0;i<count*2;i++)if(prefs.getBoolean(GestureHandleConfig.IDS[i]+"_long_enabled",true))return true;
        return false;
    }
    static void addPair(SharedPreferences prefs){
        int count=pairCount(prefs);
        if(count>=3)return;
        SharedPreferences.Editor editor=prefs.edit().putInt("handle_pair_count",count+1);
        if(prefs.getInt("schema",1)>=GestureHandleConfig.FACTORY_SCHEMA){
            for(int i=count*2;i<count*2+2;i++){
                String key=GestureHandleConfig.IDS[i]+"_enabled";
                if(!prefs.contains(key))editor.putBoolean(key,true);
            }
        }
        editor.apply();
    }
    /** Saved page regions plus an optional uncommitted geometry draft; never writes preferences. */
    static java.util.HashMap<String,float[]> previewRegions(SharedPreferences prefs,String page,String changed,boolean both,Geometry draft){
        java.util.HashMap<String,float[]> regions=new java.util.HashMap<>();
        if(!prefs.getBoolean("enabled",true))return regions;
        if("home".equals(page)){
            int count=pairCount(prefs);
            for(int i=0;i<count*2;i++){
                String id=GestureHandleConfig.IDS[i];
                if(prefs.getBoolean(id+"_enabled",!GestureHandleConfig.isExtra(id)))putPreview(regions,id,read(prefs,id));
            }
        }else if(java.util.Arrays.asList(GestureHandleConfig.IDS).contains(page)){
            putPreview(regions,page,read(prefs,page));
            if(linked(prefs,page))putPreview(regions,peer(page),read(prefs,peer(page)));
        }
        if(changed!=null&&draft!=null){
            if(regions.containsKey(changed))putPreview(regions,changed,draft);
            if(both&&regions.containsKey(peer(changed)))putPreview(regions,peer(changed),draft);
        }
        return regions;
    }
    private static void putPreview(java.util.Map<String,float[]> regions,String id,Geometry geometry){regions.put(id,new float[]{geometry.width,geometry.start,geometry.end,geometry.effectiveLandscapeWidth()});}
    private HandleSettings() {}
}
