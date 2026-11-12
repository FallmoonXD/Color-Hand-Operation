package dev.edgeflow.widgettest;

import android.app.PendingIntent;
import android.appwidget.AppWidgetManager;
import android.appwidget.AppWidgetProvider;
import android.content.Context;
import android.content.Intent;
import android.widget.RemoteViews;

/** Isolated interactive provider for the host's click/long-press/resize regression. */
public class Provider extends AppWidgetProvider {
    public static final class Configurable extends Provider {}
    public static final class Hidden extends Provider {}
    public static final class Required extends Provider {}
    public static final class Fixed extends Provider {}
    public static final class Broken extends Provider {
        @Override public void onUpdate(Context context,AppWidgetManager manager,int[] ids){
            for(int id:ids){
                RemoteViews view=new RemoteViews(context.getPackageName(),R.layout.plugin);
                view.setBoolean(R.id.content,"setChecked",true);manager.updateAppWidget(id,view);
            }
        }
    }
    public static final class Rotating extends Provider {
        @Override public void onUpdate(Context context,AppWidgetManager manager,int[] ids){
            for(int id:ids){
                RemoteViews view=new RemoteViews(context.getPackageName(),R.layout.rotating);
                RemoteViews.RemoteCollectionItems.Builder items=new RemoteViews.RemoteCollectionItems.Builder();
                items.setHasStableIds(true);items.setViewTypeCount(1);
                for(int index=0;index<3;index++){
                    RemoteViews child=new RemoteViews(context.getPackageName(),R.layout.plugin);
                    child.setTextViewText(R.id.content,"轮播内容 "+(index+1));items.addItem(index,child);
                }
                view.setRemoteAdapter(R.id.rotating,items.build());manager.updateAppWidget(id,view);
            }
        }
    }
    public static final class Settings extends android.app.Activity {
        @Override protected void onCreate(android.os.Bundle saved){
            super.onCreate(saved);int id=getIntent().getIntExtra(AppWidgetManager.EXTRA_APPWIDGET_ID,-1);
            android.widget.Button done=new android.widget.Button(this);
            done.setText(id>=0?"完成配置 "+id:"关闭提供方设置");
            done.setOnClickListener(v->{setResult(RESULT_OK,new Intent().putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID,id));finish();});
            setContentView(done);
        }
    }
    private static final String CLICK="dev.edgeflow.widgettest.CLICK";
    @Override public void onReceive(Context context,Intent intent){
        if(!CLICK.equals(intent.getAction())){super.onReceive(context,intent);return;}
        int id=intent.getIntExtra(AppWidgetManager.EXTRA_APPWIDGET_ID,-1);
        android.content.SharedPreferences prefs=context.getSharedPreferences("clicks",0);
        prefs.edit().putInt("count",prefs.getInt("count",0)+1).apply();
        update(context,AppWidgetManager.getInstance(context),id);
    }
    @Override public void onUpdate(Context c,AppWidgetManager manager,int[] ids){
        for(int id:ids)update(c,manager,id);
    }
    private void update(Context c,AppWidgetManager manager,int id){
        RemoteViews view=new RemoteViews(c.getPackageName(),this instanceof Fixed?R.layout.fixed:R.layout.plugin);
        view.setTextViewText(R.id.content,"验证插件 · "+c.getSharedPreferences("clicks",0).getInt("count",0));
        Intent click=new Intent(c,getClass()).setAction(CLICK).putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID,id);
        PendingIntent action=PendingIntent.getBroadcast(c,id,click,PendingIntent.FLAG_IMMUTABLE|PendingIntent.FLAG_UPDATE_CURRENT);
        view.setOnClickPendingIntent(R.id.content,action);
        if(this instanceof Fixed)for(int corner:new int[]{R.id.top_left,R.id.top_right,R.id.bottom_left,R.id.bottom_right})view.setOnClickPendingIntent(corner,action);
        manager.updateAppWidget(id,view);
    }
}
