package dev.edgeflow;

/** Stable identities keep extra handles separate from the migrated primary mappings. */
public final class GestureHandleConfig {
    static final int FACTORY_SCHEMA=2;
    public static final String[] IDS={"left","right","extra_left","extra_right","extra2_left","extra2_right"};
    public static boolean isExtra(String id){checkId(id);return id.startsWith("extra");}
    public static boolean isLeft(String id){checkId(id);return id.equals("left")||id.endsWith("_left");}
    public static float defaultStart(String id){checkId(id);return id.startsWith("extra2_")?.35f:isExtra(id)?.70f:.13084841f;}
    public static float defaultEnd(String id){checkId(id);return id.startsWith("extra2_")?.55f:isExtra(id)?.90f:1f;}
    private static void checkId(String id){for(String valid:IDS)if(valid.equals(id))return;throw new IllegalArgumentException("Unknown handle: "+id);}
    public static String actionKey(String id,String direction,boolean longer){
        checkId(id);if(!direction.equals("up")&&!direction.equals("straight")&&!direction.equals("down"))throw new IllegalArgumentException("Unknown direction: "+direction);
        return "action_"+id+"_"+direction+"_"+(longer?"long":"short");
    }
    public static String defaultAction(String id,String direction,boolean longer){
        actionKey(id,direction,longer);
        if(id.startsWith("extra2_")){
            if(longer)return direction.equals("straight")?"flashlight":"none";
            return direction.equals("straight")?"task_switcher":direction.equals("up")?"quick_tools":"none";
        }
        if(isExtra(id)){
            if(longer)return direction.equals("straight")?"flashlight":"none";
            return direction.equals("straight")?"screenshot":direction.equals("down")?"screen_off":"none";
        }
        if(longer)return direction.equals("straight")?"quick_tools":direction.equals("up")?"quick_launcher":"task_switcher";
        return direction.equals("up")?"recents":"back";
    }
    /** Missing slots in backups made through 0.3.17 inherit that release's defaults. */
    public static String legacyAction(String id,String direction,boolean longer){
        actionKey(id,direction,longer);if(isExtra(id))return "none";
        if(longer)return direction.equals("up")?"widgets":direction.equals("down")?"quick_tools":isLeft(id)?"one_hand":"sidebar";
        return direction.equals("up")?"volume_up":direction.equals("down")?"volume_down":isLeft(id)?"recents":"back";
    }
    /** Immutable display-coordinate rectangle shared by input windows and previews. */
    public static final class Area {
        public final int displayWidth,displayHeight,left,top,right,bottom;
        private Area(int width,int height,int l,int t,int r,int b){displayWidth=width;displayHeight=height;left=l;top=t;right=r;bottom=b;}
        public int width(){return right-left;}public int height(){return bottom-top;}
        public boolean landscape(){return displayWidth>displayHeight;}
        public int handleEdge(boolean leftSide){
            return leftSide?(landscape()?0:left):(landscape()?displayWidth:right);
        }
        @Override public boolean equals(Object value){if(!(value instanceof Area))return false;Area a=(Area)value;return displayWidth==a.displayWidth&&displayHeight==a.displayHeight&&left==a.left&&top==a.top&&right==a.right&&bottom==a.bottom;}
        @Override public int hashCode(){return java.util.Arrays.hashCode(new int[]{displayWidth,displayHeight,left,top,right,bottom});}
        @Override public String toString(){return displayWidth+"x"+displayHeight+" ["+left+","+top+"-"+right+","+bottom+"]";}
    }
    public static Area safeArea(int width,int height,int insetLeft,int insetTop,int insetRight,int insetBottom){
        width=Math.max(2,width);height=Math.max(1,height);
        int left=Math.max(0,Math.min(width-2,insetLeft)),top=Math.max(0,Math.min(height-1,insetTop));
        return new Area(width,height,left,top,Math.max(left+2,width-Math.max(0,insetRight)),Math.max(top+1,height-Math.max(0,insetBottom)));
    }
    /** Retained full-display overload for old-coordinate migration and tests. */
    public static int[] bounds(String id,float widthDp,float start,float end,float density,int screenWidth,int screenHeight){
        return bounds(id,widthDp,start,end,density,safeArea(screenWidth,screenHeight,0,0,0,0));
    }
    /** Percentages are relative to the usable area, never the full display. */
    public static int[] bounds(String id,float widthDp,float start,float end,float density,Area area){
        boolean left=isLeft(id);
        if(!Float.isFinite(widthDp))widthDp=12.3f;if(!Float.isFinite(start))start=defaultStart(id);if(!Float.isFinite(end))end=defaultEnd(id);
        if(!Float.isFinite(density)||density<=0)density=1;
        int availableWidth=area.handleEdge(false)-area.handleEdge(true);
        int width=Math.min(Math.max(2,Math.round(widthDp*density)),Math.max(2,availableWidth/8));
        start=Math.max(0,Math.min(.95f,start));end=Math.max(start+.05f,Math.min(1,end));
        int y=area.top+Math.min(area.height()-1,Math.round(start*area.height()));
        int bottom=Math.max(y+1,Math.min(area.bottom,area.top+Math.round(end*area.height())));
        return new int[]{area.handleEdge(left)-(left?0:width),y,width,bottom-y};
    }
    /** Preserve legacy physical endpoints; a clipped tiny region expands inward only. */
    public static float[] migrateFractions(float start,float end,Area portrait){
        if(!Float.isFinite(start))start=0;if(!Float.isFinite(end))end=1;
        start=Math.max(0,Math.min(.95f,start));end=Math.max(start+.05f,Math.min(1,end));
        int oldTop=Math.round(start*portrait.displayHeight);
        int oldBottom=Math.min(portrait.displayHeight,oldTop+Math.max(1,Math.round((end-start)*portrait.displayHeight)));
        float top=Math.max(0,Math.min(1,(oldTop-portrait.top)/(float)portrait.height()));
        float bottom=Math.max(top,Math.min(1,(oldBottom-portrait.top)/(float)portrait.height()));
        if(bottom-top<.05f){float center=(top+bottom)/2;top=Math.max(0,Math.min(.95f,center-.025f));bottom=Math.min(1,top+.05f);}
        return new float[]{top,bottom};
    }
    private GestureHandleConfig(){}
}
