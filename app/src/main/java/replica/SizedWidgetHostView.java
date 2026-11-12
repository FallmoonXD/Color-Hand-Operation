package replica;
import android.appwidget.*;import android.content.Context;import android.os.Bundle;import android.util.SizeF;import java.util.ArrayList;
/** Report the measured content box, after both the grid gutter and host padding. */
public final class SizedWidgetHostView extends AppWidgetHostView {
 private int reportedWidth=-1,reportedHeight=-1;private boolean queued,sizeAnimating;
 private final WidgetAutoAdvance autoAdvance=new WidgetAutoAdvance(this);
 private float contentScale=1;
 public SizedWidgetHostView(Context c){super(c);}
 void showUnavailable(){removeAllViews();addView(status("小组件暂不可用\n长按可移除"),new LayoutParams(-1,-1));}
 @Override protected android.view.View getErrorView(){return status("小组件加载失败\n请检查提供方应用");}
 private android.view.View status(String message){
  android.widget.TextView label=new android.widget.TextView(getContext());label.setText(message);label.setGravity(android.view.Gravity.CENTER);
  label.setTextSize(14);label.setTextColor(ColorOsTheme.secondary(getContext()));label.setPadding(PanelStyle.dp(getContext(),12),PanelStyle.dp(getContext(),12),PanelStyle.dp(getContext(),12),PanelStyle.dp(getContext(),12));
  label.setBackground(PanelStyle.shape(getContext(),ColorOsTheme.fill(getContext()),16));return label;
 }
 void setSizeAnimating(boolean value){sizeAnimating=value;if(!value)requestLayout();}
 @Override protected void onMeasure(int widthSpec,int heightSpec){
  super.onMeasure(widthSpec,heightSpec);contentScale=1;
  if(getChildCount()!=1||getAppWidgetInfo()==null)return;
  android.view.View child=getChildAt(0);
  int width=Math.max(1,getMeasuredWidth()-getPaddingLeft()-getPaddingRight()),height=Math.max(1,getMeasuredHeight()-getPaddingTop()-getPaddingBottom());
  for(int pass=0;pass<3;pass++){
   android.graphics.Point required=WidgetContentFit.required(child);
   float scale=WidgetContentFit.scale(width,height,required.x,required.y);
   if(scale>=contentScale-.001f)break;
   contentScale=scale;
   child.measure(MeasureSpec.makeMeasureSpec((int)Math.ceil(width/scale),MeasureSpec.EXACTLY),MeasureSpec.makeMeasureSpec((int)Math.ceil(height/scale),MeasureSpec.EXACTLY));
  }
 }
 @Override protected void onLayout(boolean changed,int l,int t,int r,int b){
  super.onLayout(changed,l,t,r,b);
  if(getChildCount()==1){
   android.view.View child=getChildAt(0);int width=getWidth()-getPaddingLeft()-getPaddingRight(),height=getHeight()-getPaddingTop()-getPaddingBottom();
   int left=getPaddingLeft()+Math.round((width-child.getMeasuredWidth()*contentScale)/2),top=getPaddingTop()+Math.round((height-child.getMeasuredHeight()*contentScale)/2);
   if(contentScale<1)child.layout(left,top,left+child.getMeasuredWidth(),top+child.getMeasuredHeight());child.setPivotX(0);child.setPivotY(0);child.setScaleX(contentScale);child.setScaleY(contentScale);
  }
  autoAdvance.refresh();if(!queued){queued=true;post(this::reportSize);}
 }
 @Override public void onVisibilityAggregated(boolean visible){super.onVisibilityAggregated(visible);if(autoAdvance!=null){if(visible)autoAdvance.refresh();else autoAdvance.stop();}}
 @Override protected void onDetachedFromWindow(){autoAdvance.stop();super.onDetachedFromWindow();}
 private void reportSize(){
  queued=false;if(sizeAnimating||!isAttachedToWindow()||getAppWidgetId()<0||getAppWidgetInfo()==null)return;
  int width=Math.max(1,getWidth()-getPaddingLeft()-getPaddingRight()),height=Math.max(1,getHeight()-getPaddingTop()-getPaddingBottom());
  if(width==reportedWidth&&height==reportedHeight)return;
  float density=getResources().getDisplayMetrics().density,w=width/density,h=height/density;
  AppWidgetManager manager=AppWidgetManager.getInstance(getContext());Bundle options=manager.getAppWidgetOptions(getAppWidgetId());
  options.putInt(AppWidgetManager.OPTION_APPWIDGET_MIN_WIDTH,(int)Math.floor(w));options.putInt(AppWidgetManager.OPTION_APPWIDGET_MAX_WIDTH,(int)Math.floor(w));
  options.putInt(AppWidgetManager.OPTION_APPWIDGET_MIN_HEIGHT,(int)Math.floor(h));options.putInt(AppWidgetManager.OPTION_APPWIDGET_MAX_HEIGHT,(int)Math.floor(h));
  ArrayList<SizeF> sizes=new ArrayList<>();sizes.add(new SizeF(w,h));options.putParcelableArrayList(AppWidgetManager.OPTION_APPWIDGET_SIZES,sizes);
  manager.updateAppWidgetOptions(getAppWidgetId(),options);reportedWidth=width;reportedHeight=height;
  android.util.Log.d("ReplicaWidgetSize","id="+getAppWidgetId()+" contentPx="+width+"x"+height+" optionsDp="+w+"x"+h);
 }
}
