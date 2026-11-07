package replica;

import android.appwidget.AppWidgetManager;
import android.appwidget.AppWidgetProviderInfo;
import android.content.Context;
import android.content.pm.PackageManager;
import android.graphics.drawable.Drawable;
import android.os.Handler;
import android.os.Looper;
import android.text.Editable;
import android.text.TextWatcher;
import android.text.TextUtils;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.AbsListView;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.TextView;
import java.text.Collator;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.function.Consumer;

/** Searchable real AppWidget providers, owned by one add-widget sheet. No host IDs are allocated here. */
final class WidgetProviderPicker implements AutoCloseable {
    private static final ExecutorService WORK=Executors.newSingleThreadExecutor(r->{Thread t=new Thread(r,"widget-provider-picker");t.setDaemon(true);return t;});
    private final Context owner;
    private final Handler main=new Handler(Looper.getMainLooper());
    private final LinearLayout content;
    private final EditText search;
    private final ListView list;
    private final TextView status;
    private final Rows adapter=new Rows();
    private final ArrayList<Entry> entries=new ArrayList<>(), filtered=new ArrayList<>();
    private final TextWatcher watcher;
    private Consumer<AppWidgetProviderInfo> selection;
    private Future<?> work;
    private volatile int generation;
    private volatile boolean closed;
    private boolean started, loading=true, failed;
    private int rowsVersion,gestureRowsVersion;
    private boolean blockedTouch;

    WidgetProviderPicker(Context owner,Consumer<AppWidgetProviderInfo> selection){
        this.owner=owner;this.selection=selection;
        content=new LinearLayout(owner);content.setOrientation(LinearLayout.VERTICAL);
        search=ColorDialogBuilder.editText(owner,"搜索应用或小组件");search.setSingleLine(true);
        search.setContentDescription("搜索应用或小组件");
        content.addView(search,new LinearLayout.LayoutParams(-1,-2));
        status=new TextView(owner);status.setGravity(Gravity.CENTER);status.setTextSize(14);
        status.setPadding(dp(16),dp(24),dp(16),dp(24));status.setMinHeight(dp(96));
        status.setAccessibilityLiveRegion(View.ACCESSIBILITY_LIVE_REGION_POLITE);
        list=new ListView(owner);ColorDialogBuilder.styleList(list);list.setAdapter(adapter);
        list.setContentDescription("可添加的小组件");
        LinearLayout.LayoutParams listParams=new LinearLayout.LayoutParams(-1,Math.min(dp(380),Math.max(dp(144),owner.getResources().getDisplayMetrics().heightPixels/2)));
        listParams.topMargin=dp(8);content.addView(list,listParams);
        list.addOnLayoutChangeListener((view,left,top,right,bottom,oldLeft,oldTop,oldRight,oldBottom)->{
            if(!closed&&filtered.isEmpty()&&bottom-top!=oldBottom-oldTop&&bottom>top)status.setLayoutParams(new AbsListView.LayoutParams(-1,bottom-top));
        });
        list.setOnTouchListener((view,event)->{
            int action=event.getActionMasked();
            if(action==MotionEvent.ACTION_DOWN){gestureRowsVersion=rowsVersion;blockedTouch=loading||failed||filtered.isEmpty();}
            else if(!blockedTouch&&gestureRowsVersion!=rowsVersion){
                // A loading/filter result must never reinterpret a press that started on another row.
                MotionEvent cancel=MotionEvent.obtain(event);cancel.setAction(MotionEvent.ACTION_CANCEL);list.onTouchEvent(cancel);cancel.recycle();blockedTouch=true;
            }
            boolean consume=blockedTouch;
            if(action==MotionEvent.ACTION_UP||action==MotionEvent.ACTION_CANCEL)blockedTouch=false;
            return consume;
        });
        watcher=new TextWatcher(){public void beforeTextChanged(CharSequence s,int start,int count,int after){}public void onTextChanged(CharSequence s,int start,int before,int count){filter();}public void afterTextChanged(Editable s){}};
        search.addTextChangedListener(watcher);
        list.setOnItemClickListener((parent,v,position,id)->{
            if(closed||position<0||position>=filtered.size()||selection==null||!(v instanceof Row)||v.getTag()!=filtered.get(position))return;
            // Prevent a second selection while the containing sheet is animating away.
            Consumer<AppWidgetProviderInfo> callback=this.selection;this.selection=null;
            list.setEnabled(false);search.setEnabled(false);callback.accept(filtered.get(position).info);
        });
        Runnable colors=()->{status.setTextColor(ColorOsTheme.secondary(owner));status.setTypeface(ColorOsTheme.typeface(owner,400));adapter.notifyDataSetChanged();};
        colors.run();ColorOsTheme.watch(content,colors);filter();
    }

    View view(){return content;}
    void start(){
        if(started||closed)return;started=true;
        int token=++generation;Context app=owner.getApplicationContext();
        work=WORK.submit(()->{
            List<Entry> result=new ArrayList<>();String error=null;
            try{
                PackageManager pm=app.getPackageManager();
                for(AppWidgetProviderInfo info:AppWidgetManager.getInstance(app).getInstalledProviders()){
                    if(closed||token!=generation||Thread.currentThread().isInterrupted())return;
                    if(info==null||info.provider==null)continue;
                    String label=info.provider.getShortClassName(),appLabel=info.provider.getPackageName();Drawable icon=null;
                    try{CharSequence loaded=info.loadLabel(pm);if(!TextUtils.isEmpty(loaded))label=loaded.toString();}catch(RuntimeException ignored){}
                    try{appLabel=pm.getApplicationLabel(pm.getApplicationInfo(info.provider.getPackageName(),0)).toString();}catch(Exception ignored){}
                    try{icon=info.loadIcon(app,app.getResources().getDisplayMetrics().densityDpi);}catch(RuntimeException ignored){}
                    if(icon==null)try{icon=pm.getApplicationIcon(info.provider.getPackageName());}catch(Exception ignored){}
                    result.add(new Entry(info,label,appLabel,icon));
                }
                Collator collator=Collator.getInstance(Locale.getDefault());
                result.sort((a,b)->{int order=collator.compare(a.app,b.app);if(order==0)order=collator.compare(a.label,b.label);return order==0?a.info.provider.flattenToString().compareTo(b.info.provider.flattenToString()):order;});
            }catch(Exception failure){error="无法读取小组件列表，请关闭后重试";}
            final String message=error;
            main.post(()->{
                if(closed||token!=generation)return;
                loading=false;failed=message!=null;entries.clear();entries.addAll(result);filter();
                if(message!=null)status.setText(message);
            });
        });
    }

    private void filter(){
        if(closed)return;
        rowsVersion++;filtered.clear();String query=search.getText().toString().trim().toLowerCase(Locale.ROOT);
        if(!loading&&!failed)for(Entry entry:entries)if(query.isEmpty()||entry.search.contains(query))filtered.add(entry);
        boolean empty=filtered.isEmpty();
        if(loading)status.setText("正在加载小组件…");
        else if(failed)status.setText("无法读取小组件列表，请关闭后重试");
        else if(entries.isEmpty())status.setText("当前没有可添加的小组件");
        else if(empty)status.setText("没有找到匹配的小组件");
        // The ListView always occupies the same natural region. Empty/loading/error content is
        // an unselectable row, so query results cannot move the search field or footer.
        adapter.notifyDataSetChanged();
    }

    @Override public void close(){
        if(closed)return;closed=true;generation++;selection=null;
        if(work!=null){work.cancel(true);work=null;}main.removeCallbacksAndMessages(null);
        search.removeTextChangedListener(watcher);search.setEnabled(false);list.setEnabled(false);list.setOnItemClickListener(null);list.setOnTouchListener(null);
        // Keep the final rows/icons while the sheet exits; remove all references naturally with its view tree.
    }

    private final class Rows extends BaseAdapter {
        public int getCount(){return Math.max(1,filtered.size());}public Entry getItem(int position){return filtered.isEmpty()?null:filtered.get(position);}public long getItemId(int position){return position;}
        public boolean areAllItemsEnabled(){return false;}public boolean isEnabled(int position){return !filtered.isEmpty()&&!closed;}
        public int getViewTypeCount(){return 2;}public int getItemViewType(int position){return filtered.isEmpty()?1:0;}
        public View getView(int position,View reuse,ViewGroup parent){
            if(filtered.isEmpty()){status.setLayoutParams(new AbsListView.LayoutParams(-1,list.getHeight()>0?list.getHeight():dp(380)));return status;}
            Row row=reuse instanceof Row?(Row)reuse:new Row();row.bind(getItem(position));return row;
        }
    }

    private final class Row extends LinearLayout {
        private final ImageView icon;
        private final TextView title,detail;
        Row(){
            super(owner);setGravity(Gravity.CENTER_VERTICAL);setPadding(dp(12),dp(12),dp(12),dp(12));setMinimumHeight(dp(76));
            icon=new ImageView(owner);icon.setScaleType(ImageView.ScaleType.FIT_CENTER);icon.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);
            LinearLayout.LayoutParams iconParams=new LinearLayout.LayoutParams(dp(40),dp(40));iconParams.setMarginEnd(dp(14));addView(icon,iconParams);
            LinearLayout labels=new LinearLayout(owner);labels.setOrientation(LinearLayout.VERTICAL);addView(labels,new LinearLayout.LayoutParams(0,-2,1));
            title=new TextView(owner);title.setTextSize(16);title.setMaxLines(2);title.setEllipsize(TextUtils.TruncateAt.END);labels.addView(title);
            detail=new TextView(owner);detail.setTextSize(13);detail.setMaxLines(2);detail.setEllipsize(TextUtils.TruncateAt.END);
            LinearLayout.LayoutParams detailParams=new LinearLayout.LayoutParams(-1,-2);detailParams.topMargin=dp(3);labels.addView(detail,detailParams);
            title.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);detail.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);
        }
        void bind(Entry entry){
            setTag(entry);
            title.setText(entry.label);title.setTypeface(ColorOsTheme.typeface(owner,500));title.setTextColor(ColorOsTheme.text(owner));
            detail.setText(entry.app);detail.setTypeface(ColorOsTheme.typeface(owner,400));detail.setTextColor(ColorOsTheme.secondary(owner));
            Drawable image=entry.icon;if(image!=null&&image.getConstantState()!=null)image=image.getConstantState().newDrawable(getResources()).mutate();icon.setImageDrawable(image);
            setBackground(ColorDialogBuilder.touchBackground(owner,android.graphics.Color.TRANSPARENT,14));
            setContentDescription(entry.label+"，"+entry.app);
        }
    }

    private static final class Entry {
        final AppWidgetProviderInfo info;final String label,app,search;final Drawable icon;
        Entry(AppWidgetProviderInfo info,String label,String app,Drawable icon){this.info=info;this.label=label;this.app=app;this.icon=icon;search=(label+"\n"+app+"\n"+info.provider.getPackageName()).toLowerCase(Locale.ROOT);}
    }
    private int dp(float value){return PanelStyle.dp(owner,value);}
}
