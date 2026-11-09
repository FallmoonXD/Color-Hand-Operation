package replica;

import android.os.Looper;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.lang.reflect.Method;
import java.util.concurrent.TimeUnit;

/** Native slider and read-only media capability lookup. EOF releases an unfinished drag. */
public final class QuickControlsBridge {
    public static void main(String[] args)throws Exception{
        int display=Integer.parseInt(args[0]);
        int user=Integer.parseInt(args[1]);
        BrightnessRangePolicy.Range range=BrightnessRangePolicy.values(Integer.parseInt(args[2]),Integer.parseInt(args[3]));
        if(display<0||user<0)throw new IllegalArgumentException("Invalid display or user");
        Looper.prepare();
        Method currentUser=android.app.ActivityManager.class.getMethod("getCurrentUser");
        BufferedReader input=new BufferedReader(new InputStreamReader(System.in));
        DisplaySlider slider=null;
        try{
            reply("READY");
            for(String line;(line=input.readLine())!=null;){
                if((Integer)currentUser.invoke(null)!=user)break;
                if(line.equals("media")){reply("KEY "+MediaKeyTarget.currentKey());continue;}
                if(line.equals("cancel")){
                    if(slider!=null)slider.cancel();
                }else{
                    String[] fields=line.split(" ");
                    if(fields.length!=2||!(fields[0].equals("drag")||fields[0].equals("save")))throw new IllegalArgumentException("Invalid brightness request");
                    int raw=Integer.parseInt(fields[1]);
                    if(range==null||raw<range.min||raw>range.max)throw new IllegalArgumentException("Brightness outside discovered range");
                    if(slider==null)slider=new DisplaySlider(display,user);
                    slider.apply(raw,fields[0].equals("save"));
                }
                reply("APPLIED");
            }
        }finally{if(slider!=null)slider.cancel();}
    }
    private static final class DisplaySlider {
        private final int display,user;
        private final Object manager;
        private final Method brightness,adjustment;
        private boolean temporary;
        DisplaySlider(int display,int user)throws Exception{
            this.display=display;this.user=user;
            Class<?> feature=Class.forName("com.oplus.content.OplusFeatureConfigManager");
            if(!Boolean.TRUE.equals(feature.getMethod("hasFeature",String.class).invoke(feature.getMethod("getInstance").invoke(null),
                    "oplus.software.display.multibits_dimming_support")))throw new IllegalStateException("No multibits brightness");
            Class<?> api=Class.forName("android.hardware.display.DisplayManagerGlobal");
            manager=api.getMethod("getInstance").invoke(null);
            brightness=api.getMethod("setTemporaryBrightness",int.class,float.class);
            adjustment=api.getMethod("setTemporaryAutoBrightnessAdjustment",float.class);
        }
        void apply(int raw,boolean save)throws Exception{
            temporary=true;
            brightness.invoke(manager,display,(float)raw);adjustment.invoke(manager,(float)raw);
            if(save){
                put(user,"screen_brightness",Integer.toString(raw));
                put(user,"screen_auto_brightness_adj",Float.toString(raw));
                temporary=false;
            }
        }
        void cancel()throws Exception{
            if(!temporary)return;
            try{brightness.invoke(manager,display,Float.NaN);}
            finally{adjustment.invoke(manager,Float.NaN);temporary=false;}
        }
    }
    private static void put(int user,String key,String value)throws Exception{
        Process process=new ProcessBuilder("/system/bin/cmd","settings","put","--user",Integer.toString(user),"system",key,value).start();
        if(!process.waitFor(2,TimeUnit.SECONDS)){process.destroyForcibly();throw new IOException("Timeout saving "+key);}
        if(process.exitValue()!=0)throw new IOException("Could not save "+key);
    }
    private static void reply(String text){System.out.println(text);System.out.flush();}
}
