package replica;
import android.content.Context;import android.graphics.*;import android.view.View;
/** Compact page indicator with optional accessible alternatives to swiping. */
public final class PageDots extends View {
 private int count,page,to;private float fraction;private final Paint paint=new Paint(3);
 private android.graphics.drawable.Drawable inactive,active;
 private LauncherWidgetStyle launcher;
 private android.animation.ValueAnimator countAnimation;private float visibleCount;
 private Runnable previous,next;
 public PageDots(Context c,int count,int page){super(c);this.count=count;this.page=page;setClickable(true);Runnable theme=()->{setBackground(PanelStyle.shape(c,ColorOsTheme.card(c),12));invalidate();};theme.run();ColorOsTheme.watch(this,theme);}
 void pages(int count,int from,int to,float fraction){
  if(this.count!=count){
   if(countAnimation!=null)countAnimation.cancel();
   if(isAttachedToWindow()&&android.animation.ValueAnimator.areAnimatorsEnabled()){
    countAnimation=android.animation.ValueAnimator.ofFloat(visibleCount,count);
    countAnimation.setDuration(launcher.integer("config_dropAnimMinDuration",100));countAnimation.setInterpolator(WidgetDeck.SCROLL);
    countAnimation.addUpdateListener(a->{visibleCount=(float)a.getAnimatedValue();invalidate();});countAnimation.start();
   }else visibleCount=count;
  }
  this.count=count;this.page=from;this.to=to;this.fraction=fraction;invalidate();
 }
 PageDots(Context c,int count,int page,LauncherWidgetStyle launcher){
  super(c);this.count=count;visibleCount=count;this.page=page;this.launcher=launcher;
  inactive=launcher.drawable("ic_launcher_page_point_normal");active=launcher.drawable("ic_launcher_page_point_focused");
  setFocusable(true);setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_YES);
 }
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
  if(launcher!=null){
   int size=active==null?launcher.dp(6):Math.max(1,active.getIntrinsicWidth());
   // PKR110 workspace uses 6 dp dots at 12 dp centers; use the themed asset's intrinsic size.
   int step=2*size;
   float visible=Math.min(visibleCount,Math.max(1,(getWidth()-size)/step+1));int n=(int)Math.ceil(visible);
   int first=Math.max(0,Math.min(page-n/2,count-n)),left=Math.round((getWidth()-size-(visible-1)*step)/2),top=(getHeight()-size)/2;
   for(int i=0;i<n;i++){
    int at=first+i,x=left+i*step;float weight=(at==page?1-fraction:0)+(at==to?fraction:0);
    float visibility=Math.max(0,Math.min(1,visible-i));
    if(inactive!=null&&active!=null){
     if(weight<1){inactive.setBounds(x,top,x+size,top+size);inactive.setAlpha(Math.round(255*(1-weight)*visibility));inactive.draw(c);}
     if(weight>0){active.setBounds(x,top,x+size,top+size);active.setAlpha(Math.round(255*weight*visibility));active.draw(c);}
    }else{paint.setColor(Color.argb(Math.round((77+178*weight)*visibility),255,255,255));c.drawCircle(x+size*.5f,top+size*.5f,size*.5f,paint);}
   }
   return;
  }
  int n=Math.min(9,count),first=Math.max(0,Math.min(page-4,count-n));
  for(int i=0;i<n;i++){boolean selected=first+i==page;paint.setColor(selected?ColorOsTheme.accent(getContext()):ColorOsTheme.tertiary(getContext()));
   float x=getWidth()/2f+(i-(n-1)/2f)*10*d,y=getHeight()/2f,r=2*d,w=selected?4*d:r;
   c.drawRoundRect(x-w,y-r,x+w,y+r,r,r,paint);
  }
 }
 @Override protected void onDetachedFromWindow(){if(countAnimation!=null)countAnimation.cancel();visibleCount=count;super.onDetachedFromWindow();}
}
