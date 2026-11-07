package replica;

final class ColorHeaderFade {
    static float blur(int listOffset,float edgeTranslation,int ramp){
        return Math.max(0f,Math.min(1f,(listOffset-edgeTranslation)/Math.max(1,ramp)));
    }
    static int restingAlpha(float blur){return Math.round(255*(1-blur));}
    private ColorHeaderFade(){}
}
