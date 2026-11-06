package replica;

public final class PaletteStateCheck {
    private static int assertions;
    private static void check(boolean value,String message){assertions++;if(!value)throw new AssertionError(message);}
    public static void main(String[] args){
        for(int color:new int[]{0,0xffffffff,0xff000000,0x80123456,0x00abcdef,0x01010203,0xfeff0100,0x7f007fff}){
            ColorPaletteState state=new ColorPaletteState(color,37);
            check(state.color()==color,"constructor preserves exact signed ARGB");
            check(ColorPaletteState.parse(ColorPaletteState.hex(color))==color,"ARGB hex round trip");
            state.setHue(211);check((state.color()>>>24)==(color>>>24),"hue retains alpha byte");
            state.setSV(.71f,.85f);check((state.color()>>>24)==(color>>>24),"SV retains alpha byte");
            state.setRgb(0xff0abcde);check(state.color()==((color&0xff000000)|0x0abcde),"grid choice retains alpha byte");
            state.setOpacity(99);check((state.color()>>>24)==(color>>>24),"visibility does not rewrite ARGB");
            state.restore();check(state.color()==color&&state.opacity()==37,"cancel restores exact original pair");
        }
        check(ColorPaletteState.parse("#FF000000")==0xff000000,"opaque black is signed and exact");
        check(ColorPaletteState.parse("#00000000")==0,"transparent black survives");
        check(ColorPaletteState.parse("#123456")==0xff123456,"six-digit RGB means opaque");
        check(ColorPaletteState.parse("  #80aBcDeF ")==0x80abcdef,"case and outer whitespace accepted");
        for(String invalid:new String[]{"","#","123456","#12345","#1234567","#123456789","#12X456","0xFF123456","#ＦＦ123456"})check(ColorPaletteState.parse(invalid)==null,"reject invalid color: "+invalid);
        check(ColorPaletteState.effective(0x80123456,100)==0x80123456,"100% leaves original alpha");
        check(ColorPaletteState.effective(0x80123456,50)==0x40123456,"alpha times visibility once");
        check(ColorPaletteState.effective(0xff123456,0)==0x00123456,"0% hides without losing RGB");
        ColorPaletteState state=new ColorPaletteState(0xffabcdef,-12);check(state.opacity()==0,"lower visibility bound");state.setOpacity(120);check(state.opacity()==100,"upper visibility bound");
        check(ColorPaletteState.hsv(255,0,1,1)==0xffff0000,"red hue");check(ColorPaletteState.hsv(255,120,1,1)==0xff00ff00,"green hue");check(ColorPaletteState.hsv(255,240,1,1)==0xff0000ff,"blue hue");check(ColorPaletteState.hsv(255,720,1,1)==0xffff0000,"hue wraps");check(ColorPaletteState.hsv(128,240,0,1)==0x80ffffff,"white saturation retains alpha");
        check(ColorPaletteState.hueAt(1,0)==0,"ring red is right");check(ColorPaletteState.hueAt(0,-1)==90,"ring hue increases counterclockwise");check(ColorPaletteState.hueAt(-1,0)==180,"ring cyan is left");check(ColorPaletteState.hueAt(0,1)==270,"ring lower hue");
        check(GalleryPaletteColors.COLORS.length==120,"source gallery grid has10x12 cells");
        for(int color:GalleryPaletteColors.COLORS){check((color>>>24)==255,"source swatch is RGB");state.setColor(color);state.setHue(state.hue());check(state.color()==color,"source swatch HSV round trip");}
        System.out.println("Color palette state: "+assertions+" assertions passed");
    }
}
