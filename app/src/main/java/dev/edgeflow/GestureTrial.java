package dev.edgeflow;

import android.content.Context;
import android.content.SharedPreferences;
import android.widget.TextView;
import java.util.Locale;

/** Observes the real handle's recognizer. A contact captured here never reaches the executor. */
final class GestureTrial {
    private static GestureTrial active;
    private final Context context;
    private final TextView result,details,toggle;
    private float startX,startY;
    final String application;
    private boolean expanded,hit;
    private long generation;
    private final android.os.IBinder owner=new android.os.Binder();
    GestureTrial(Context context,String application,TextView result,TextView details,TextView toggle){
        this.context=context;this.application=application;this.result=result;this.details=details;this.toggle=toggle;
        toggle.setVisibility(android.view.View.GONE);details.setVisibility(android.view.View.GONE);
        toggle.setOnClickListener(v->{expanded=!expanded;showDetails();});
    }
    private void showDetails(){details.setVisibility(expanded?android.view.View.VISIBLE:android.view.View.GONE);toggle.setText(expanded?"收起判定数据":"判定数据");}
    static GestureTrial current(){return active;}
    void activate(){if(active!=this){active=this;sync(RuntimeBackend.status());}}
    void sync(RuntimeBackend.Status backend){
        if(active!=this)return;
        if(!backend.active()||!backend.ready()){
            generation=0;status("等待手势后端就绪");return;
        }
        if(generation!=0&&generation==backend.raw().getLong("trial_generation"))return;
        generation=0;status("正在连接试划");
        RuntimeBackend.trial(context,owner,application,true,this::remote);
    }
    void deactivate(){if(active==this){RuntimeBackend.trial(context,owner,application,false,b->{});active=null;}}
    private void remote(android.os.Bundle data){
        if(active!=this)return;String event=data.getString("trial_event","");
        if(event.equals("ready")){
            long acknowledged=data.getLong("trial_generation");
            if(generation!=acknowledged){generation=acknowledged;status("等待手势");}
            return;
        }
        if(event.equals("begin")){begin(data.getString("handle"));return;}if(event.equals("cancel")){cancel();return;}
        if(!event.equals("result")){status("试划尚未连接，请检查运行状态");return;}
        int kind=data.getInt("kind");boolean longer=kind==2;String direction=data.getString("direction","straight");
        String name=switch(direction){case "up"->"上斜";case "down"->"下斜";default->"直滑";};String reason=data.getString("reason","");
        result.setText(handleName(data.getString("handle","right"))+" · "+name+"\n"+(kind==0?"未触发":(longer?"长滑":"短滑")+" · "+ActionExecutor.title(context,data.getString("action","none")))+(kind==0?"":"\n配置来源　"+data.getString("source","全局"))+(reason.isEmpty()?"":"\n"+reason));
        String distances=String.format(Locale.getDefault(),"滑动距离　%.0f dp\n短滑阈值　%.0f dp",data.getFloat("distance"),data.getFloat("short_distance"));
        if(data.getFloat("long_distance")==Float.MAX_VALUE)distances+="\n长滑　已关闭";
        else distances+=String.format(Locale.getDefault(),"\n长滑阈值　%.0f dp / %d ms",data.getFloat("long_distance"),data.getLong("duration"));
        details.setText(distances+String.format(Locale.getDefault(),"\n按住时间　%d ms\n角度　%.0f°",data.getLong("elapsed"),data.getFloat("angle")));toggle.setVisibility(android.view.View.VISIBLE);showDetails();
    }
    private void status(String text){result.setText(text);toggle.setVisibility(android.view.View.GONE);details.setVisibility(android.view.View.GONE);}
    void begin(String handle){if(active==this){hit=true;status(handleName(handle)+" · 待判定");}}
    void cancel(){if(active==this)status("已取消");}
    void applicationTouch(android.view.MotionEvent e){
        if(active!=this)return;
        if(e.getActionMasked()==android.view.MotionEvent.ACTION_DOWN){startX=e.getRawX();startY=e.getRawY();hit=false;}
        if(e.getActionMasked()==android.view.MotionEvent.ACTION_UP){
            float dx=Math.abs(e.getRawX()-startX),dy=Math.abs(e.getRawY()-startY);
            if(generation!=0&&!hit&&dx>dy&&dx>android.view.ViewConfiguration.get(context).getScaledTouchSlop()){
                status("起点未命中触钮");
            }
        }
    }
    void show(String handle,GestureRecognizer gesture,int kind,long now){
        if(active!=this)return;
        boolean longer=kind==2;
        String direction=gesture.directionName(longer);
        String key=GestureHandleConfig.actionKey(handle,direction,longer);
        SharedPreferences prefs=AppPrefs.get(context);
        String action=GestureMapping.resolve(prefs,application,handle,direction,longer);
        String source=application!=null&&prefs.contains(GestureMapping.scoped(application,key))?application:"全局";
        String name=switch(direction){case "up"->"上斜";case "down"->"下斜";default->"直滑";};
        String reason=gesture.diagnostic(kind,now);
        result.setText(handleName(handle)+" · "+name+"\n"+(kind==0?"未触发":(longer?"长滑":"短滑")+" · "+ActionExecutor.title(context,action))
            +(kind==0?"":"\n配置来源　"+source)+(reason.isEmpty()?"":"\n"+reason));
        float density=context.getResources().getDisplayMetrics().density;
        String distances=String.format(Locale.getDefault(),"滑动距离　%.0f dp\n短滑阈值　%.0f dp",gesture.distance()/density,gesture.shortThreshold()/density);
        if(gesture.longThreshold()!=Float.MAX_VALUE)distances+=String.format(Locale.getDefault(),"\n长滑阈值　%.0f dp / %d ms",gesture.longThreshold()/density,gesture.durationThreshold());
        else distances+="\n长滑　已关闭";
        details.setText(distances+String.format(Locale.getDefault(),"\n按住时间　%d ms\n角度　%.0f°",gesture.elapsed(now),gesture.angle()));
        toggle.setVisibility(android.view.View.VISIBLE);showDetails();
    }
    private static String handleName(String id){return (GestureHandleConfig.isLeft(id)?"左侧触钮":"右侧触钮")+(id.startsWith("extra2_")?" 3":id.startsWith("extra_")?" 2":"");}
}
