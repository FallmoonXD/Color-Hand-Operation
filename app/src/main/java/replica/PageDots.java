package replica;
import android.content.Context;import android.graphics.*;import android.view.View;
/** Small geometric page indicator; the whole footer remains a page-menu touch target. */
public final class PageDots extends View {
 private final int count,page;private final Paint paint=new Paint(3);
 public PageDots(Context c,int count,int page){super(c);this.count=count;this.page=page;setClickable(true);setBackground(new ColorOsSurface(c,12,0));}
 protected void onDraw(Canvas c){super.onDraw(c);float d=getResources().getDisplayMetrics().density;
  int n=Math.min(9,count),first=Math.max(0,Math.min(page-4,count-n));float w=n*10*d+16*d,h=14*d,x=(getWidth()-w)/2,y=(getHeight()-h)/2;
  
  for(int i=0;i<n;i++){paint.setColor(first+i==page?0xeeffffff:0x70ffffff);float cx=getWidth()/2f+(i-(n-1)/2f)*10*d;c.drawCircle(cx,getHeight()/2f,(first+i==page?2.3f:1.8f)*d,paint);}
 }
}
