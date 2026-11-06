package replica;

import android.content.Context;
import android.graphics.*;
import android.graphics.drawable.Drawable;
import android.view.View;
import android.view.ViewGroup;
import java.util.ArrayList;

/** Darken only the space between controls. Controls see the unchanged test19 base,
 * rather than receiving extra white to compensate for a darkened parent. */
public final class ToolsBackdrop extends Drawable implements Drawable.Callback {
    private final ColorOsSurface base;
    private final ViewGroup decor;
    private final float corner,rim;
    private final Paint shade=new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Path mask=new Path();
    private final Rect local=new Rect();
    private final RectF box=new RectF();
    private final ArrayList<Hole> holes=new ArrayList<>();
    private boolean dirty=true;
    private static final int SHADE_ALPHA=16;
    private static final class Hole {
        final View view;final float radius,height;final Rect bounds=new Rect();
        Hole(View v,float r,float h){view=v;radius=r;height=h;}
    }
    public ToolsBackdrop(Context c,android.view.Window window){
        ViewGroup host=(ViewGroup)window.getDecorView();
        decor=host;corner=PanelStyle.dp(c,24);rim=3*c.getResources().getDisplayMetrics().density/3.5f;base=new ColorOsSurface(c,24,2).attach(window);base.setCallback(this);
        shade.setColor(SHADE_ALPHA<<24);
    }
    public void preserve(View view,float radius,float trackHeight){
        holes.add(new Hole(view,radius,trackHeight));dirty=true;
        view.addOnLayoutChangeListener((v,l,t,r,b,ol,ot,or,ob)->{dirty=true;invalidateSelf();});
        invalidateSelf();
    }
    @Override protected void onBoundsChange(Rect bounds){base.setBounds(bounds);dirty=true;}
    @Override public void draw(Canvas canvas){
        base.draw(canvas);
        // Descendant coordinates stay local during entrance/exit transforms.
        for(Hole h:holes){
            local.set(0,0,h.view.getWidth(),h.view.getHeight());
            decor.offsetDescendantRectToMyCoords(h.view,local);
            if(!local.equals(h.bounds)){h.bounds.set(local);dirty=true;}
        }
        if(dirty){
            mask.reset();mask.setFillType(Path.FillType.EVEN_ODD);
            box.set(getBounds());box.inset(rim,rim);mask.addRoundRect(box,Math.max(0,corner-rim),Math.max(0,corner-rim),Path.Direction.CW);
            for(Hole h:holes){
                if(h.bounds.isEmpty())continue;
                box.set(h.bounds);
                if(h.height>0){float inset=Math.max(0,(box.height()-h.height)/2f);box.inset(0,inset);}
                float r=h.height>0?box.height()/2:Math.min(h.radius,Math.min(box.width(),box.height())/2);
                mask.addRoundRect(box,r,r,Path.Direction.CW);
            }
            dirty=false;
        }
        canvas.drawPath(mask,shade);
    }
    @Override public void getOutline(Outline o){o.setRoundRect(getBounds(),corner);}
    @Override public void setAlpha(int a){base.setAlpha(a);shade.setAlpha(Math.round(SHADE_ALPHA*a/255f));invalidateSelf();}
    @Override public void setColorFilter(ColorFilter f){base.setColorFilter(f);shade.setColorFilter(f);invalidateSelf();}
    @Override public int getOpacity(){return PixelFormat.TRANSLUCENT;}
    @Override public void invalidateDrawable(Drawable d){invalidateSelf();}
    @Override public void scheduleDrawable(Drawable d,Runnable r,long t){scheduleSelf(r,t);}
    @Override public void unscheduleDrawable(Drawable d,Runnable r){unscheduleSelf(r);}
}
