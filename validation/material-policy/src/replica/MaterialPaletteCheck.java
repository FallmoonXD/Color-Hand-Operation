package replica;
import java.util.Arrays;
public final class MaterialPaletteCheck {
    private static int checks;
    private static void same(float[] actual,float[] expected,String label){checks++;if(!Arrays.equals(actual,expected))throw new AssertionError(label+" "+Arrays.toString(actual));}
    public static void main(String[] args){
        for(boolean dark:new boolean[]{false,true})for(int kind:new int[]{2,3})for(float density:new float[]{2.75f,3.5f}){
            NativeMaterialPalette on=new NativeMaterialPalette(dark,true,kind,density),off=new NativeMaterialPalette(dark,false,kind,density);float s=density/3.5f;
            float[] tint=new float[20];tint[1]=1;if(kind==3){tint[2]=tint[3]=tint[4]=dark?.12f:.84f;tint[5]=.78f;}
            float[] edge={1,1,1,1.6f*s,3*s,1.6f*s,3*s,dark?.35f:.25f,dark?.4f:.6f,.25f,.6f,.15f,.5f,0,10,.5f};
            float[] shadow={1,1,1,dark?.07f:.15f,0,0,40*s,0,36*s,1.3f,1.3f,20,.5f,6*s,20,.3f};
            same(on.values.get("u_colorArray"),tint,"accepted tint unchanged");same(on.values.get("u_edgeArray"),edge,"accepted edge unchanged");same(on.values.get("u_shadowArray"),shadow,"accepted shadow unchanged");same(on.values.get("u_opticsArray"),null,"no inset rectangle highlight");
            same(on.values.get("u_faceLight"),new float[]{kind==2?.05f:0f},"only captured side-bar glass receives the face light");
            same(off.values.get("u_faceLight"),new float[]{0f},"system stroke switch also disables face light");
            edge[7]=edge[8]=0;shadow[3]=0;
            same(off.values.get("u_colorArray"),tint,"stroke-off preserves tint");same(off.values.get("u_edgeArray"),edge,"stroke-off only masks edge alpha");same(off.values.get("u_shadowArray"),shadow,"stroke-off only masks shadow alpha");same(off.values.get("u_opticsArray"),null,"stroke-off omits inset rectangle highlight");
        }
        System.out.println("PASS "+checks+" accepted material palette assertions");
    }
}
