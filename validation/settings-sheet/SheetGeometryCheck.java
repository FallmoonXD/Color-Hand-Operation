import replica.ColorSheetGeometry;

public final class SheetGeometryCheck {
    private static int assertions;
    private static void check(boolean value,String message){assertions++;if(!value)throw new AssertionError(message);}
    private static ColorSheetGeometry.Bounds fit(int w,int h,int l,int t,int r,int b,int ime,float density,int content){return ColorSheetGeometry.fit(w,h,l,t,r,b,ime,density,content);}
    private static void visible(ColorSheetGeometry.Bounds b,int w,int h){check(b.width>0&&b.height>0,"positive size");check(b.left>=0&&b.top>=0&&b.left+b.width<=w&&b.top+b.height<=h,"inside window");check(b.top+b.height+b.bottom==h,"bottom position consistent");}
    public static void main(String[] args){
        ColorSheetGeometry.Bounds b=fit(360,800,0,24,0,24,0,1,5000);
        visible(b,360,800);check(b.left==8&&b.width==344,"phone uses 8dp horizontal inset");check(b.height==704&&b.top==64&&b.bottom==32,"phone protects status/nav bars and 40dp top gap");check(!b.compact,"normal phone retains full header/footer");
        b=fit(1600,1200,0,30,0,30,0,1,5000);visible(b,1600,1200);check(b.width==540&&b.left==530,"tablet sheet stays centered and 540dp wide");check(b.height==720,"long list caps at720dp");
        b=fit(800,360,36,24,0,24,0,1,5000);visible(b,800,360);check(b.left>=44&&b.left+b.width<=792,"landscape avoids side cutout");check(b.height==264,"landscape content fits usable height");
        b=fit(360,800,0,24,0,24,340,1,5000);visible(b,360,800);check(b.bottom==348&&b.top+b.height==452,"IME inset replaces navigation inset, not added twice");
        b=fit(360,220,0,24,0,24,0,1,5000);visible(b,360,220);check(b.compact&&b.height==148,"short windows use compact chrome and16dp top gap");
        b=fit(360,800,0,24,0,24,0,1,160);visible(b,360,800);check(b.height==160&&b.top==608,"short content remains bottom aligned");
        b=fit(1264,2772,0,100,0,80,0,3.5f,20000);visible(b,1264,2772);check(b.width==1208&&b.left==28,"device-density phone keeps physical8dp margins");check(b.height<=2520,"device-density max height is720dp");
        b=fit(1,1,100,100,100,100,100,1,5000);visible(b,1,1);check(b.width==1&&b.height==1,"invalid transient inset values cannot invert dimensions");
        for(float density:new float[]{.75f,1,2,3.5f})for(int w:new int[]{100,320,1600})for(int h:new int[]{80,300,1800})for(int ime:new int[]{0,h/2,h+10}){b=fit(w,h,12,24,20,16,ime,density,Integer.MAX_VALUE);visible(b,w,h);}
        boolean rejected=false;try{fit(0,800,0,0,0,0,0,1,100);}catch(IllegalArgumentException e){rejected=true;}check(rejected,"zero width rejected");
        rejected=false;try{fit(100,800,0,0,0,0,0,Float.NaN,100);}catch(IllegalArgumentException e){rejected=true;}check(rejected,"invalid density rejected");
        System.out.println("Settings sheet geometry: "+assertions+" assertions passed");
    }
}
