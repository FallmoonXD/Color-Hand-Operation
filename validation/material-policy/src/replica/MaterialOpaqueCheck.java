package replica;

/** Readability and layer boundaries for the white-content inverse volume palette. */
public final class MaterialOpaqueCheck {
    private static int checks;
    private static void check(boolean ok,String name){checks++;if(!ok)throw new AssertionError(name);}
    private static double channel(int c){double v=c/255.0;return v<=.04045?v/12.92:Math.pow((v+.055)/1.055,2.4);}
    private static double luminance(int c){return .2126*channel((c>>>16)&255)+.7152*channel((c>>>8)&255)+.0722*channel(c&255);}
    private static double contrast(int first,int second){double a=luminance(first),b=luminance(second);return (Math.max(a,b)+.05)/(Math.min(a,b)+.05);}
    public static void main(String[] args){
        int white=0xffffffff,black=0xff000000,accent=0xff0080ff;
        int outer=MaterialOpaquePalette.opaqueColor(2,0xff333333,0x33ffffff,accent,white);
        int child=MaterialOpaquePalette.opaqueColor(0,0xff333333,0x33ffffff,accent,white);
        check(outer==0xff333333,"outer uses native volume fallback, never light Settings card");
        check(child==0xff5c5c5c,"native translucent button is flattened onto the panel");
        check(contrast(white,outer)>=4.5,"white outer labels remain readable");
        check(contrast(white,child)>=4.5,"white button labels remain readable");
        check(luminance(child)>luminance(outer),"controls remain a separate brighter layer");
        int lightDialog=MaterialOpaquePalette.opaqueColor(3,outer,child,accent,white);
        int darkDialog=MaterialOpaquePalette.opaqueColor(3,outer,child,accent,0xff1e1e1e);
        check(contrast(black,lightDialog)>=4.5,"light dialog uses its dark-text matching card");
        check(contrast(white,darkDialog)>=4.5,"dark dialog uses its light-text matching card");
        check(MaterialOpaquePalette.opaqueColor(1,outer,child,accent,white)==accent,"active control follows live accent");
        check(MaterialOpaquePalette.opaqueColor(0,0x00334455,0x00abcdef,accent,white)==0xff334455,"zero control alpha keeps fully opaque panel");
        check(MaterialOpaquePalette.opaqueColor(0,outer,0xffabcdef,accent,white)==0xffabcdef,"opaque control token remains exact");
        System.out.println("PASS "+checks+" opaque surface readability assertions");
    }
}
