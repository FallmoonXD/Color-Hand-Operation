package replica;

/** Window-relative popup geometry, independent of Android so edge cases can be checked on the JVM. */
public final class ColorPopupGeometry {
    private ColorPopupGeometry() {}

    public static final class Placement {
        public final int x, y, width, height;
        public final boolean above;
        public final float pivotX, pivotY;
        private Placement(int x, int y, int width, int height, boolean above, float px, float py) {
            this.x=x; this.y=y; this.width=width; this.height=height; this.above=above;
            pivotX=px; pivotY=py;
        }
    }

    /** Bounds and anchor use one coordinate space. Falls back to a bounded overlapping menu only
     * when neither side can contain one accessible row (e.g. a keyboard in a very short window). */
    public static Placement place(int left, int top, int right, int bottom,
            int anchorLeft, int anchorTop, int anchorRight, int anchorBottom,
            int wantedWidth, int wantedHeight, boolean alignRight, int gap, int margin, int minimumHeight) {
        return place(left,top,right,bottom,anchorLeft,anchorTop,anchorRight,anchorBottom,wantedWidth,wantedHeight,alignRight,gap,margin,minimumHeight,false);
    }
    static Placement place(int left,int top,int right,int bottom,int anchorLeft,int anchorTop,int anchorRight,int anchorBottom,
            int wantedWidth,int wantedHeight,boolean alignRight,int gap,int margin,int minimumHeight,boolean centered){
        if (right<=left || bottom<=top) throw new IllegalArgumentException("Empty popup bounds");
        int mx=Math.min(Math.max(0,margin),(right-left-1)/2);
        int my=Math.min(Math.max(0,margin),(bottom-top-1)/2);
        left+=mx; right-=mx; top+=my; bottom-=my;
        int width=Math.min(Math.max(1,wantedWidth),right-left);
        int totalHeight=bottom-top, desired=Math.min(Math.max(1,wantedHeight),totalHeight);
        int below=Math.max(0,bottom-Math.max(top,anchorBottom+Math.max(0,gap)));
        int above=Math.max(0,Math.min(bottom,anchorTop-Math.max(0,gap))-top);
        boolean useAbove=below<desired && (above>=desired || above>below);
        int side=useAbove?above:below;
        boolean overlap=side<Math.min(desired,Math.max(1,minimumHeight));
        int height=overlap?desired:Math.min(desired,side);
        int x=clamp(centered?anchorLeft+(anchorRight-anchorLeft-width)/2:alignRight?anchorRight-width:anchorLeft,left,right-width);
        int y=clamp(useAbove?anchorTop-gap-height:anchorBottom+gap,top,bottom-height);
        float pivotX=Math.max(0,Math.min(width,(anchorLeft+(anchorRight-anchorLeft)*.5f)-x));
        float pivotY=overlap?Math.max(0,Math.min(height,(anchorTop+(anchorBottom-anchorTop)*.5f)-y)):(useAbove?height:0);
        return new Placement(x,y,width,height,useAbove,pivotX,pivotY);
    }

    private static int clamp(int value,int min,int max){return Math.max(min,Math.min(max,value));}
}
