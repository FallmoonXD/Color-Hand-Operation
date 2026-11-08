package dev.edgeflow;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Rect;
import android.graphics.drawable.Drawable;
import android.widget.ImageView;
import replica.ColorOsTheme;
import replica.SystemIcons;

/** Place the visible ink at the button center, including for asymmetric OEM navigation icons. */
final class NavigationGlyph extends ImageView {
    private final Paint paint=new Paint(Paint.ANTI_ALIAS_FLAG|Paint.FILTER_BITMAP_FLAG);
    private final boolean intrinsicSize;
    private Drawable sampled;
    private Bitmap ink;
    private float centerX,centerY;
    private int direction,sampledWidth,sampledHeight;

    NavigationGlyph(Context context,String name,String label,boolean intrinsicSize){
        super(context);this.intrinsicSize=intrinsicSize;
        setScaleType(intrinsicSize?ScaleType.CENTER_INSIDE:ScaleType.FIT_CENTER);
        setContentDescription(label);setTooltipText(label);
        float density=getResources().getDisplayMetrics().density;
        paint.setShadowLayer(density,0,.5f*density,0x70000000);
        Runnable load=()->setImageDrawable(name==null?context.getDrawable(R.drawable.navigation_close):SystemIcons.raw(context,name));
        load.run();ColorOsTheme.watch(this,load);
    }

    @Override protected void onDraw(Canvas canvas){
        Drawable glyph=getDrawable();
        int roomX=getWidth()-getPaddingLeft()-getPaddingRight(),roomY=getHeight()-getPaddingTop()-getPaddingBottom();
        if(glyph==null||roomX<=0||roomY<=0)return;
        int w=Math.max(1,glyph.getIntrinsicWidth()),h=Math.max(1,glyph.getIntrinsicHeight());
        float scale=Math.min((float)roomX/w,(float)roomY/h);
        if(intrinsicSize)scale=Math.min(1,scale);
        int width=Math.max(1,Math.round(w*scale)),height=Math.max(1,Math.round(h*scale));
        if(sampled!=glyph||direction!=getLayoutDirection()||sampledWidth!=width||sampledHeight!=height)
            sample(glyph,width,height);
        if(ink==null){super.onDraw(canvas);return;}
        canvas.drawBitmap(ink,Math.round(getPaddingLeft()+roomX*.5f-centerX),
                Math.round(getPaddingTop()+roomY*.5f-centerY),paint);
    }

    private void sample(Drawable glyph,int width,int height){
        sampled=glyph;direction=getLayoutDirection();sampledWidth=width;sampledHeight=height;
        ink=Bitmap.createBitmap(width,height,Bitmap.Config.ARGB_8888);
        ink.setDensity(Bitmap.DENSITY_NONE);
        Rect oldBounds=new Rect(glyph.getBounds());
        glyph.setLayoutDirection(direction);glyph.setBounds(0,0,width,height);
        try{glyph.draw(new Canvas(ink));}
        catch(RuntimeException hardwareOnly){
            // A vendor drawable can require a hardware canvas. Keep its original geometry.
            android.util.Log.w("EdgeNavigation","Using the drawable directly on the window canvas",hardwareOnly);
            ink=null;return;
        }finally{glyph.setBounds(oldBounds);}
        int[] pixels=new int[width*height];ink.getPixels(pixels,0,width,0,0,width,height);
        long total=0,xMoment=0,yMoment=0;int peak=0;
        for(int y=0,i=0;y<height;y++)for(int x=0;x<width;x++,i++){
            int a=Color.alpha(pixels[i]);total+=a;xMoment+=(long)x*a;yMoment+=(long)y*a;peak=Math.max(peak,a);
        }
        centerX=total==0?width*.5f:.5f+(float)xMoment/total;
        centerY=total==0?height*.5f:.5f+(float)yMoment/total;
        // SystemUI bakes 54% opacity into its three-key assets. Keep the antialiasing
        // coverage and render the same strokes in white on the glass.
        if(peak!=0){
            for(int i=0;i<pixels.length;i++)pixels[i]=Color.argb((Color.alpha(pixels[i])*255+peak/2)/peak,255,255,255);
            ink.setPixels(pixels,0,width,0,0,width,height);
        }
    }
}
