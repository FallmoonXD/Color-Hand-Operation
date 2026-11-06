import replica.ColorPopupGeometry;

public final class PopupGeometryCheck {
    private static int checks;
    private static void check(boolean value,String message){checks++;if(!value)throw new AssertionError(message);}
    private static void within(ColorPopupGeometry.Placement p,int l,int t,int r,int b){
        check(p.width>0&&p.height>0,"positive menu dimensions");
        check(p.x>=l&&p.y>=t&&p.x+p.width<=r&&p.y+p.height<=b,"menu stays inside safe bounds");
        check(p.pivotX>=0&&p.pivotX<=p.width&&p.pivotY>=0&&p.pivotY<=p.height,"animation pivot stays in menu");
    }
    private static ColorPopupGeometry.Placement p(int l,int t,int r,int b,int al,int at,int ar,int ab,int w,int h,boolean right){return ColorPopupGeometry.place(l,t,r,b,al,at,ar,ab,w,h,right,8,16,48);}
    public static void main(String[] args){
        ColorPopupGeometry.Placement menu=p(0,24,400,800,340,40,388,88,168,256,true);
        within(menu,16,40,384,784);check(!menu.above&&menu.y==96,"toolbar menu appears below invoking button with gap");check(menu.x+menu.width==384,"right menu respects edge margin");
        menu=p(0,24,400,800,20,700,380,756,232,176,true);
        within(menu,16,40,384,784);check(menu.above&&menu.y+menu.height==692,"bottom remove button opens above itself");check(menu.x+menu.width==380,"wide invoking button aligns to its end");
        menu=p(0,24,400,800,12,40,60,88,168,256,false);
        within(menu,16,40,384,784);check(menu.x==16,"RTL/start-side menu stays visible");
        menu=p(420,80,800,640,732,100,780,148,256,288,true);
        within(menu,436,96,784,624);check(menu.x==524&&menu.y==156,"split-window origin is preserved");
        menu=p(0,24,400,420,180,140,220,188,256,900,true);
        within(menu,16,40,384,404);check(menu.height==208&&!menu.above,"large font or long menu scrolls within larger side");
        menu=p(0,24,400,420,180,310,220,358,256,900,true);
        within(menu,16,40,384,404);check(menu.above&&menu.height==262,"keyboard-limited menu chooses usable area above");
        menu=p(0,0,120,80,12,20,108,60,256,500,true);
        within(menu,16,16,104,64);check(menu.width==88&&menu.height==48,"tiny window uses accessible overlapping fallback");
        menu=p(20,30,21,31,20,30,21,31,256,500,true);
        within(menu,20,30,21,31);check(menu.width==1&&menu.height==1,"degenerate but nonempty safe window stays bounded");
        boolean rejected=false;try{p(0,0,0,100,0,0,1,1,20,20,true);}catch(IllegalArgumentException expected){rejected=true;}
        check(rejected,"empty windows rejected before attempting popup attachment");
        for(int height:new int[]{80,200,800})for(int top:new int[]{-40,0,40,height-32,height+40})for(boolean end:new boolean[]{false,true}){
            menu=p(0,0,320,height,-20,top,340,top+48,500,1000,end);within(menu,0,0,320,height);
        }
        System.out.println("Settings popup geometry: "+checks+" assertions passed");
    }
}
