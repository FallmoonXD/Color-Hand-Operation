package replica;
import android.content.Context;import android.graphics.*;import android.view.View;
/** Compact page indicator with optional accessible alternatives to swiping. */
public final class PageDots extends View {
 private final int count,page;private final Paint paint=new Paint(3);
 private Runnable previous,next;
 public PageDots(Context c,int count,int page){super(c);this.count=count;this.page=page;setClickable(true);Runnable theme=()->{setBackground(PanelStyle.shape(c,ColorOsTheme.card(c),12));invalidate();};theme.run();ColorOsTheme.watch(this,theme);}
 public void setPaging(Runnable previous,Runnable next){this.previous=previous;this.next=next;}
 @Override public void onInitializeAccessibilityNodeInfo(android.view.accessibility.AccessibilityNodeInfo node){
  super.onInitializeAccessibilityNodeInfo(node);node.setScrollable(previous!=null||next!=null);
  if(previous!=null)node.addAction(new android.view.accessibility.AccessibilityNodeInfo.AccessibilityAction(android.view.accessibility.AccessibilityNodeInfo.ACTION_SCROLL_BACKWARD,"上一页"));
  if(next!=null)node.addAction(new android.view.accessibility.AccessibilityNodeInfo.AccessibilityAction(android.view.accessibility.AccessibilityNodeInfo.ACTION_SCROLL_FORWARD,"下一页"));
 }
 @Override public boolean performAccessibilityAction(int action,android.os.Bundle args){
  if(action==android.view.accessibility.AccessibilityNodeInfo.ACTION_SCROLL_BACKWARD&&previous!=null){previous.run();return true;}
  if(action==android.view.accessibility.AccessibilityNodeInfo.ACTION_SCROLL_FORWARD&&next!=null){next.run();return true;}
  return super.performAccessibilityAction(action,args);
 }
 protected void onDraw(Canvas c){super.onDraw(c);float d=getResources().getDisplayMetrics().density;
  int n=Math.min(9,count),first=Math.max(0,Math.min(page-4,count-n));
  for(int i=0;i<n;i++){boolean selected=first+i==page;paint.setColor(selected?ColorOsTheme.accent(getContext()):ColorOsTheme.tertiary(getContext()));
   float x=getWidth()/2f+(i-(n-1)/2f)*10*d,y=getHeight()/2f,r=2*d,w=selected?4*d:r;
   c.drawRoundRect(x-w,y-r,x+w,y+r,r,r,paint);
  }
 }
}
