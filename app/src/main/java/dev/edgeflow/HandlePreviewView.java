package dev.edgeflow;

import android.content.Context;
import android.graphics.Color;
import replica.ColorOsTheme;

/** Shared group indicators for settings rows and noninteractive screen-edge previews. */
final class HandlePreviewView {
    static int groupColor(Context context,String id){
        float[] hsv=new float[3];Color.colorToHSV(ColorOsTheme.accent(context),hsv);
        if(id.startsWith("extra2_"))hsv[0]=(hsv[0]+145)%360;
        else if(id.startsWith("extra_"))hsv[0]=(hsv[0]+75)%360;
        return Color.HSVToColor(hsv);
    }
    private HandlePreviewView(){}
}
