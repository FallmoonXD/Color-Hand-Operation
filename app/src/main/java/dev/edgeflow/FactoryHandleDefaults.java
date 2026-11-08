package dev.edgeflow;

/** OHO+ 9.3.13 phone presets expressed in CHO's dp and usable portrait area. */
final class FactoryHandleDefaults {
    final float widthDp, shortDistanceDp, longDistanceDp;
    private final GestureHandleConfig.Area portrait;
    private final boolean modern;

    FactoryHandleDefaults(GestureHandleConfig.Area portrait,float density,float xdpi,int smallestWidthDp,int androidApi){
        this.portrait=portrait;
        modern=androidApi>=36;
        if(!Float.isFinite(density)||density<=0)density=1;
        int dpi=(int)(Float.isFinite(xdpi)&&xdpi>0?xdpi:160*density);
        int widthDpClass=smallestWidthDp>0?smallestWidthDp:Math.round(portrait.displayWidth/density);
        int baseDp=widthDpClass>=411||widthDpClass<320?12:widthDpClass>=360?10:8;
        int basePx=Math.round(baseDp*density);
        int minimumPx=Math.min(portrait.displayHeight/(widthDpClass>=600?100:60),(int)(dpi*.09));
        widthDp=clamp(Math.max(minimumPx,basePx+basePx*3*40/100)/density,2,30);
        int unit=dpi/10;
        shortDistanceDp=clamp((unit+20*unit/30)/density,15,100);
        longDistanceDp=clamp((int)(dpi*.625f)/density,40,180);
    }

    HandleSettings.Geometry geometry(String id){
        boolean firstExtra=id.startsWith("extra_"),secondExtra=id.startsWith("extra2_");
        int size=firstExtra?5:secondExtra?3:modern?50:60;
        int position=firstExtra?100:secondExtra?0:modern?35:30;
        int height=portrait.displayHeight;
        int bottomInset=Math.max(portrait.top,height-portrait.bottom);
        int usable=Math.max(1,height-portrait.top-bottomInset);
        int minimum=height/10;
        int span=Math.min(usable,minimum+(height-portrait.top+bottomInset-minimum)*size/100);
        int offset=(int)((100-position)/100f*(usable-span));
        return new HandleSettings.Geometry(widthDp,offset/(float)portrait.height(),(offset+span)/(float)portrait.height());
    }
    private static float clamp(float value,int min,int max){return Math.max(min,Math.min(max,value));}
}
