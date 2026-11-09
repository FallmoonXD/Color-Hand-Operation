package replica;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.view.View;
import android.widget.LinearLayout;

/** Equal-width segments with the COUI position spring. Labels remain in place on reversal. */
public final class ColorPhaseTabs extends LinearLayout {
    private final ColorControlMotion position;
    private final Paint paint=new Paint(Paint.ANTI_ALIAS_FLAG);
    private final float radius;

    public ColorPhaseTabs(Context context,boolean entering){
        super(context);
        setWillNotDraw(false);
        int inset=PanelStyle.dp(context,4);
        setPadding(inset,inset,inset,inset);
        radius=PanelStyle.dp(context,12);
        position=new ColorControlMotion(this,entering?0:1);
    }
    public void select(boolean entering){
        position.animate(entering?0:1,.4f,.1f);
    }
    @Override protected void onDraw(Canvas canvas){
        super.onDraw(canvas);
        if(getChildCount()!=2)return;
        View first=getChildAt(0),second=getChildAt(1);
        float x=first.getLeft()+(second.getLeft()-first.getLeft())*position.value();
        paint.setColor((ColorOsTheme.accent(getContext())&0xffffff)|0x1c000000);
        canvas.drawRoundRect(x,first.getTop(),x+first.getWidth(),first.getBottom(),radius,radius,paint);
    }
    @Override protected void onDetachedFromWindow(){
        position.cancel();super.onDetachedFromWindow();
    }
}
