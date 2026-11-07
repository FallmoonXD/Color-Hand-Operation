package replica;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.ColorFilter;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.PixelFormat;
import android.graphics.Rect;
import android.graphics.Shader;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.LayerDrawable;

/** A neutral tint at the top of a page or panel, drawn behind its contents. */
final class TopEdgeGradient extends Drawable {
    private final Context context;
    private final float corner;
    private final int maxHeight;
    private final Paint paint=new Paint(Paint.ANTI_ALIAS_FLAG|Paint.DITHER_FLAG);
    private int topColor;

    private TopEdgeGradient(Context context,float corner,int maxHeight) {
        this.context=context;this.corner=corner;this.maxHeight=maxHeight;
        refreshTheme();
    }

    static TopEdgeGradient header(Context context) {
        return new TopEdgeGradient(context,0,Integer.MAX_VALUE);
    }

    static TopEdgeGradient panel(Context context,float cornerPx) {
        return new TopEdgeGradient(context,cornerPx,PanelStyle.dp(context,88));
    }

    Drawable over(Drawable background) {
        LayerDrawable layers=new LayerDrawable(new Drawable[]{background,this});
        layers.setPaddingMode(LayerDrawable.PADDING_MODE_STACK);
        return layers;
    }

    void refreshTheme() {
        int color=(ColorOsTheme.text(context)&0x00ffffff)|(ColorOsTheme.dark(context)?0x30000000:0x24000000);
        if(topColor==color)return;
        topColor=color;onBoundsChange(getBounds());invalidateSelf();
    }

    @Override protected void onBoundsChange(Rect bounds) {
        if(bounds.isEmpty())return;
        int mid=(topColor&0xffffff)|((topColor>>>24)/2<<24);
        paint.setShader(new LinearGradient(0,bounds.top,0,bounds.top+Math.min(bounds.height(),maxHeight),
                new int[]{topColor,mid,topColor&0xffffff},new float[]{0,.4f,1},Shader.TileMode.CLAMP));
    }

    @Override public void draw(Canvas canvas) {
        Rect b=getBounds();
        if(!b.isEmpty())canvas.drawRoundRect(b.left,b.top,b.right,b.bottom,corner,corner,paint);
    }
    @Override public void setAlpha(int alpha){paint.setAlpha(alpha);invalidateSelf();}
    @Override public int getAlpha(){return paint.getAlpha();}
    @Override public void setColorFilter(ColorFilter filter){paint.setColorFilter(filter);invalidateSelf();}
    @Override public int getOpacity(){return PixelFormat.TRANSLUCENT;}
}
