import dev.edgeflow.GestureHandleConfig;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;

public final class GestureHandleConfigCheck {
    private static int checks;
    private static void check(boolean value,String message){checks++;if(!value)throw new AssertionError(message);}
    private static void bounds(String id,int width,int height,int[] expected){
        check(Arrays.equals(expected,GestureHandleConfig.bounds(id,12.3f,GestureHandleConfig.defaultStart(id),GestureHandleConfig.defaultEnd(id),3.5f,width,height)),"physical bounds "+id+" "+width+"x"+height);
    }
    public static void main(String[] ignored){
        String[] directions={"up","straight","down"};
        String[][] factory={{"recents","back","back","quick_launcher","quick_tools","task_switcher"},
                            {"none","screenshot","screen_off","none","flashlight","none"},
                            {"quick_tools","task_switcher","none","none","flashlight","none"}};
        HashSet<String> keys=new HashSet<>();
        for(String id:GestureHandleConfig.IDS)for(boolean longer:new boolean[]{false,true})for(int direction=0;direction<3;direction++){
            String key=GestureHandleConfig.actionKey(id,directions[direction],longer);keys.add(key);
            String expected=factory[id.startsWith("extra2_")?2:GestureHandleConfig.isExtra(id)?1:0][(longer?3:0)+direction];
            check(expected.equals(GestureHandleConfig.defaultAction(id,directions[direction],longer)),"default mapping "+key);
        }
        check(keys.size()==36,"all six six-direction profiles have distinct preference keys");
        HashMap<String,String> preferences=new HashMap<>();
        preferences.put(GestureHandleConfig.actionKey("left","up",false),"screenshot");
        preferences.put(GestureHandleConfig.actionKey("extra_left","up",false),"home");
        check("screenshot".equals(preferences.get("action_left_up_short"))&&"home".equals(preferences.get("action_extra_left_up_short")),"extra action assignment does not overwrite migrated primary action");
        bounds("left",1264,2780,new int[]{0,364,43,2416});
        bounds("right",1264,2780,new int[]{1221,364,43,2416});
        bounds("extra_left",1264,2780,new int[]{0,1946,43,556});
        bounds("extra_right",1264,2780,new int[]{1221,1946,43,556});
        bounds("extra2_left",1264,2780,new int[]{0,973,43,556});
        bounds("extra2_right",1264,2780,new int[]{1221,973,43,556});
        bounds("extra_left",2780,1264,new int[]{0,885,43,253});
        bounds("extra_right",2780,1264,new int[]{2737,885,43,253});
        bounds("extra2_left",2780,1264,new int[]{0,442,43,253});
        bounds("extra2_right",2780,1264,new int[]{2737,442,43,253});
        check(Arrays.equals(new int[]{0,2641,158,139},GestureHandleConfig.bounds("extra_left",999,2,-1,3.5f,1264,2780)),"out-of-range geometry remains a bounded narrow edge surface");
        check(Arrays.equals(new int[]{1221,1946,43,556},GestureHandleConfig.bounds("extra_right",Float.NaN,Float.NaN,Float.NaN,3.5f,1264,2780)),"invalid floating-point configuration uses extra defaults");
        boolean rejected=false;try{GestureHandleConfig.actionKey("extra_top","up",false);}catch(IllegalArgumentException expected){rejected=true;}check(rejected,"unknown profiles cannot form untracked preference keys");
        System.out.println("PASS "+checks+" handle configuration assertions");
    }
}
