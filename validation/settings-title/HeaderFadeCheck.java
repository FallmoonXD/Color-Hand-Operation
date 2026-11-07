package replica;

public final class HeaderFadeCheck {
    private static void expect(int scroll,float pull,float expected,String message){
        float blur=ColorHeaderFade.blur(scroll,pull,30);
        if(blur!=expected||ColorHeaderFade.restingAlpha(blur)!=Math.round((1-expected)*255))
            throw new AssertionError(message);
    }
    public static void main(String[] args){
        expect(0,0,0,"short page at rest shows the resting gradient");
        expect(0,-15,.5f,"a held upward drag drives the blur without a scroll range");
        expect(0,-50,1,"upward edge travel saturates");
        expect(0,15,0,"a pull down retains the resting gradient");
        expect(15,0,.5f,"real list scroll uses the same distance");
        expect(0,0,0,"the resting gradient remains present after release");
        for(int i=0;i<=30;i++){
            float blur=ColorHeaderFade.blur(0,-i,30);
            if(Math.abs(ColorHeaderFade.restingAlpha(blur)+255*blur-255)>.51f)
                throw new AssertionError("both layers faded at "+i);
        }
        System.out.println("PASS header gradient at rest, during short-page pulls and on real scroll");
    }
}
