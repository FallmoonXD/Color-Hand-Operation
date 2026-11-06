package replica;
import android.content.Context;import android.graphics.*;import android.widget.SeekBar;
/** White filled capsules; media has a brighter empty track to separate it from its group surface. */
public final class GlassSeekBar extends SeekBar {
 private final int trackColor;private final Paint paint=new Paint(3);private final RectF rect=new RectF();
 public GlassSeekBar(Context c){this(c,false);}
 public GlassSeekBar(Context c,boolean volume){super(c);trackColor=volume?0x50ffffff:0x28e3e9f2;setPadding(0,0,0,0);setThumb(null);setSplitTrack(false);setBackground(null);setForeground(null);setStateListAnimator(null);setDefaultFocusHighlightEnabled(false);}
 @Override protected synchronized void onDraw(Canvas c){float h=Math.min(getHeight(),PanelGeometry.px(getContext(),25)),y=(getHeight()-h)/2f;rect.set(0,y,getWidth(),y+h);paint.setColor(trackColor);c.drawRoundRect(rect,h/2,h/2,paint);c.save();Path path=new Path();path.addRoundRect(rect,h/2,h/2,Path.Direction.CW);c.clipPath(path);paint.setColor(isEnabled()?0xffffffff:0x60dce4f0);c.drawRect(0,y,getWidth()*getProgress()/Math.max(1f,getMax()),y+h,paint);c.restore();}
}
