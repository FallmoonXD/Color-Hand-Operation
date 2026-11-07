package replica;

import java.util.Arrays;

/** Settings and overlay pickers must offer the same values, including older layouts. */
public final class PanelChoices {
    public static int[] columns(int current) {return current==5?new int[]{2,3,4,5}:new int[]{2,3,4};}
    public static int[] taskCounts(int current) {
        int[] normal={8,9,10,11,12,13};
        if(current>=8&&current<=13)return normal;
        int[] values=Arrays.copyOf(normal,normal.length+1);
        values[normal.length]=current;return values;
    }
    private PanelChoices(){}
}
