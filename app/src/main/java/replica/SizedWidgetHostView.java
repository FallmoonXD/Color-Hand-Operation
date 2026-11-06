package replica;
import android.appwidget.*;import android.content.Context;import android.os.Bundle;import android.util.SizeF;import java.util.ArrayList;
/** Report the measured content box, after both the grid gutter and host padding. */
public final class SizedWidgetHostView extends AppWidgetHostView {
 private int reportedWidth=-1,reportedHeight=-1;private boolean queued;
 public SizedWidgetHostView(Context c){super(c);}
 @Override protected void onLayout(boolean changed,int l,int t,int r,int b){super.onLayout(changed,l,t,r,b);if(!queued){queued=true;post(this::reportSize);}}
 private void reportSize(){
  queued=false;if(!isAttachedToWindow()||getAppWidgetId()<0)return;
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
