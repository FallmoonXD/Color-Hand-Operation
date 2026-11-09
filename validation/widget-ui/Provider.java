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
        RemoteViews view=new RemoteViews(c.getPackageName(),R.layout.plugin);
        view.setTextViewText(R.id.content,"验证插件 · "+c.getSharedPreferences("clicks",0).getInt("count",0));
        Intent click=new Intent(c,getClass()).setAction(CLICK).putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID,id);
        view.setOnClickPendingIntent(R.id.content,PendingIntent.getBroadcast(c,id,click,PendingIntent.FLAG_IMMUTABLE|PendingIntent.FLAG_UPDATE_CURRENT));
        manager.updateAppWidget(id,view);
    }
}
