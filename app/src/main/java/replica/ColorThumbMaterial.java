package replica;

import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RectF;

/** Cached clipped inner-light layer from COUI TexturedThumbRenderer (physical px). */
final class ColorThumbMaterial {
    private final Paint bitmapPaint=new Paint(Paint.ANTI_ALIAS_FLAG|Paint.FILTER_BITMAP_FLAG);
    private final RectF destination=new RectF();
    private Bitmap bitmap;
    private float diameter;
    void draw(Canvas canvas,float x,float y,float radius,float maximumRadius){
        float wanted=maximumRadius*2;
        if(bitmap==null||diameter!=wanted){clear();diameter=wanted;int size=(int)Math.ceil(wanted+24);bitmap=Bitmap.createBitmap(size,size,Bitmap.Config.ARGB_8888);Canvas cache=new Canvas(bitmap);RectF rect=new RectF(12,12,12+wanted,12+wanted);Paint paint=new Paint(Paint.ANTI_ALIAS_FLAG);paint.setColor(0x77ffffff);cache.drawRoundRect(rect,maximumRadius,maximumRadius,paint);Path clip=new Path();clip.addOval(rect,Path.Direction.CCW);cache.clipPath(clip);paint.setColor(0);paint.setStyle(Paint.Style.STROKE);paint.setStrokeWidth(10);paint.setShadowLayer(5,0,.4f,0xffffffff);cache.drawOval(rect,paint);}
        float scale=radius/maximumRadius,pad=12*scale;destination.set(x-radius-pad,y-radius-pad,x+radius+pad,y+radius+pad);canvas.drawBitmap(bitmap,null,destination,bitmapPaint);
    }
    void clear(){if(bitmap!=null){bitmap.recycle();bitmap=null;}diameter=0;}
}
