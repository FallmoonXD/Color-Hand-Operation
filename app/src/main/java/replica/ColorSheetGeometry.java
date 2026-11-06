package replica;

/** Safe-area dimensions for the settings sheet; all arguments use pixels. */
public final class ColorSheetGeometry {
    private ColorSheetGeometry(){}
    public static final class Bounds {
        public final int left,top,width,height,bottom;
        public final boolean compact;
        Bounds(int left,int top,int width,int height,int bottom,boolean compact){this.left=left;this.top=top;this.width=width;this.height=height;this.bottom=bottom;this.compact=compact;}
    }
    public static Bounds fit(int windowWidth,int windowHeight,int insetLeft,int insetTop,int insetRight,
            int insetBottom,int imeBottom,float density,int contentHeight){
        if(windowWidth<=0||windowHeight<=0||!Float.isFinite(density)||density<=0)throw new IllegalArgumentException("Invalid sheet window");
        int l=clamp(insetLeft,0,windowWidth-1),r=clamp(windowWidth-Math.max(0,insetRight),l+1,windowWidth);
        int t=clamp(insetTop,0,windowHeight-1),b=clamp(windowHeight-Math.max(Math.max(0,insetBottom),imeBottom),t+1,windowHeight);
        int edge=Math.min(Math.round(8*density),Math.min((r-l-1)/2,(b-t-1)/2));
        boolean compact=b-t<Math.round(240*density);
        int topGap=Math.min(Math.round((compact?16:40)*density),Math.max(0,(b-t)/4));
        int width=Math.max(1,Math.min(Math.round(540*density),r-l-2*edge));
        int maxHeight=Math.max(1,Math.min(Math.round(720*density),b-t-topGap-edge));
        int height=Math.min(Math.max(1,contentHeight),maxHeight);
        int left=l+(r-l-width)/2,bottom=windowHeight-b+edge;
        return new Bounds(left,windowHeight-bottom-height,width,height,bottom,compact);
    }
    private static int clamp(int v,int min,int max){return Math.max(min,Math.min(max,v));}
}
