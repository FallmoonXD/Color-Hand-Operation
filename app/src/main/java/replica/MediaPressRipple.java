package replica;

import android.content.Context;
import android.content.res.ColorStateList;
import android.graphics.Rect;
import android.graphics.drawable.RippleDrawable;

/** A white press light around the finger, clipped to this control's hit area. */
final class MediaPressRipple extends RippleDrawable {
    private final int radius;
    MediaPressRipple(Context context,int radiusDp){
        super(ColorStateList.valueOf(0x55ffffff),null,PanelStyle.shape(context,0xffffffff,radiusDp));
        radius=PanelStyle.dp(context,radiusDp);setRadius(radius);
    }
    @Override public void setHotspot(float x,float y){
        int cx=Math.round(x),cy=Math.round(y);
        setHotspotBounds(cx-radius,cy-radius,cx+radius,cy+radius);super.setHotspot(x,y);
    }
    @Override protected void onBoundsChange(Rect bounds){
        super.onBoundsChange(bounds);setHotspot(bounds.exactCenterX(),bounds.exactCenterY());
    }
}
