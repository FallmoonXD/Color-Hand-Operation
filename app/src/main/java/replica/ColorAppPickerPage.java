package replica;

import android.app.Activity;
import android.content.ComponentName;
import android.content.Intent;
import android.content.pm.ActivityInfo;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.drawable.Drawable;
import android.text.TextUtils;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.accessibility.AccessibilityNodeInfo;
import android.view.inputmethod.InputMethodManager;
import android.widget.*;
import java.text.Collator;
import java.util.*;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Future;
import java.util.function.Consumer;
import java.util.function.Predicate;

/** The three application directories share searching, recycled rows and package loading. */
public final class ColorAppPickerPage implements AutoCloseable {
    public enum Mode { ADD, SINGLE, MULTIPLE, PACKAGE }
    public final ColorSettingsScaffold scaffold;
    private final Activity owner;
    private final ExecutorService worker;
    private final Mode mode;
    private final Set<String> excluded;
    private final Predicate<ToolsConfig.Item> checked;
    private final Consumer<ToolsConfig.Item> select;
    private final EditText search;
    private final ListView list;
    private final TextView status;
    private final ArrayList<Entry> apps=new ArrayList<>(), visible=new ArrayList<>();
    private final Rows rows=new Rows();
    private int first;
    private Future<?> work;
    private boolean closed, loaded;
    private record Entry(ToolsConfig.Item item, Bitmap icon) {}

    public ColorAppPickerPage(Activity owner,ExecutorService worker,Collection<String> excluded,String query,int first,Runnable back,Consumer<ToolsConfig.Item> select){
        this(owner,worker,new ColorSettingsScaffold(owner,"添加应用快捷入口",false,back),Mode.ADD,excluded,query,first,item->false,select);
    }
    public ColorAppPickerPage(Activity owner, ExecutorService worker, ColorSettingsScaffold scaffold,
            Mode mode, Collection<String> excluded, String query, int first, Predicate<ToolsConfig.Item> checked, Consumer<ToolsConfig.Item> select) {
        this.owner=owner;this.scaffold=scaffold;this.mode=mode;this.worker=worker;
        this.excluded=new HashSet<>(excluded);this.first=first;this.checked=checked;this.select=select;
        search=scaffold.search("搜索应用",query,value->{this.first=0;if(loaded)filter();});
        scaffold.page.removeView(scaffold.scroll);
        list=new ListView(owner);ColorDialogBuilder.styleList(list);list.setAdapter(rows);
        list.setBackground(PanelStyle.shape(owner,ColorOsTheme.card(owner),20));list.setClipToOutline(true);
        LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(-1,0,1);
        lp.setMargins(dp(16),dp(6),dp(16),dp(12));scaffold.page.addView(list,lp);
        status=new TextView(owner);status.setGravity(Gravity.CENTER);status.setMinHeight(dp(80));
        status.setTextSize(15);status.setTextColor(ColorOsTheme.secondary(owner));status.setAccessibilityLiveRegion(View.ACCESSIBILITY_LIVE_REGION_POLITE);
        scaffold.page.addView(status,new LinearLayout.LayoutParams(-1,-2));
        load();
    }
    private int dp(float v){return PanelStyle.dp(owner,v);}
    public String query(){return search.getText().toString();}
    public int firstVisibleIndex(){return loaded&&!visible.isEmpty()?list.getFirstVisiblePosition():first;}
    public void hideKeyboard(){owner.getSystemService(InputMethodManager.class).hideSoftInputFromWindow(search.getWindowToken(),0);search.clearFocus();}
    private boolean alive(){return !closed&&!owner.isFinishing()&&!owner.isDestroyed();}
    private void message(String text,boolean retry){
        list.setVisibility(View.GONE);status.setVisibility(View.VISIBLE);status.setText(text);
        status.setOnClickListener(retry?v->load():null);status.setClickable(retry);
    }
    private void load(){
        message("正在读取应用…",false);loaded=false;
        work=worker.submit(()->{
            ArrayList<Entry> found=new ArrayList<>();PackageManager pm=owner.getPackageManager();
            try{
                HashSet<String> seen=new HashSet<>();
                for(ResolveInfo info:pm.queryIntentActivities(new Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER),0)){
                    if(Thread.currentThread().isInterrupted())return;
                    ActivityInfo a=info.activityInfo;if(a==null||a.applicationInfo==null||a.name==null)continue;
                    boolean packages=mode==Mode.MULTIPLE||mode==Mode.PACKAGE;
                    if(mode==Mode.MULTIPLE&&a.packageName.equals(owner.getPackageName()))continue;
                    if(!packages&&(!a.enabled||!a.exported||!a.applicationInfo.enabled))continue;
                    String key="app:"+new ComponentName(a.packageName,a.name).flattenToString();
                    if(excluded.contains(key)||!seen.add(packages?a.packageName:key))continue;
                    String label=a.packageName;Bitmap bitmap=null;
                    try{
                        CharSequence name=packages?a.applicationInfo.loadLabel(pm):info.loadLabel(pm);
                        if(name!=null&&!TextUtils.isEmpty(name.toString().trim()))label=name.toString();
                        Drawable icon=packages?a.applicationInfo.loadIcon(pm):info.loadIcon(pm);
                        if(icon!=null){
                            int size=dp(40);bitmap=Bitmap.createBitmap(size,size,Bitmap.Config.ARGB_8888);
                            icon=icon.mutate();icon.setBounds(0,0,size,size);icon.draw(new Canvas(bitmap));
                        }
                    }catch(RuntimeException missing){bitmap=null;}
                    found.add(new Entry(new ToolsConfig.Item(key,label),bitmap));
                }
                Collator order=Collator.getInstance(owner.getResources().getConfiguration().getLocales().get(0));
                found.sort((a,b)->{int n=order.compare(a.item.label,b.item.label);return n!=0?n:a.item.key.compareTo(b.item.key);});
                owner.runOnUiThread(()->{if(alive()){apps.addAll(found);loaded=true;filter();}});
            }catch(RuntimeException failure){owner.runOnUiThread(()->{if(alive())message("无法读取应用列表 · 点按重试",true);});}
        });
    }
    private void filter(){
        String q=query().trim().toLowerCase(Locale.ROOT);visible.clear();
        for(Entry entry:apps)if(entry.item.label.toLowerCase(Locale.ROOT).contains(q)||entry.item.key.toLowerCase(Locale.ROOT).contains(q))visible.add(entry);
        rows.notifyDataSetChanged();
        if(visible.isEmpty()){message(q.isEmpty()?"没有可用的应用":"没有匹配的应用",false);return;}
        status.setVisibility(View.GONE);list.setVisibility(View.VISIBLE);list.setSelection(Math.min(first,visible.size()-1));
    }
    private final class Rows extends BaseAdapter {
        public int getCount(){return visible.size();}
        public Entry getItem(int p){return visible.get(p);}
        public long getItemId(int p){return p;}
        public View getView(int p,View recycled,ViewGroup parent){
            Row row=recycled instanceof Row?(Row)recycled:new Row();row.bind(getItem(p));return row;
        }
    }
    private final class Row extends LinearLayout {
        final ImageView icon;final TextView name;final CompoundButton mark;
        Entry entry;
        Row(){
            super(owner);setGravity(Gravity.CENTER_VERTICAL);setMinimumHeight(dp(64));setPadding(dp(16),dp(6),dp(12),dp(6));
            setBackground(ColorDialogBuilder.touchBackground(owner,Color.TRANSPARENT,16));setFocusable(true);
            icon=new ImageView(owner);icon.setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_NO);addView(icon,new LayoutParams(dp(40),dp(40)));
            name=new TextView(owner);name.setTextColor(ColorOsTheme.text(owner));name.setTypeface(ColorOsTheme.typeface(owner,500));name.setTextSize(16);name.setMaxLines(2);
            name.setEllipsize(TextUtils.TruncateAt.END);name.setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_NO);
            LayoutParams label=new LayoutParams(0,-2,1);label.setMarginStart(dp(14));addView(name,label);
            mark=mode==Mode.SINGLE?new ColorRadioButton(owner):mode==Mode.MULTIPLE?new ColorCheckBox(owner):null;
            if(mark!=null){mark.setClickable(false);mark.setFocusable(false);mark.setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_NO);addView(mark);}
            setOnClickListener(v->{
                if(!alive()||entry==null)return;
                if(mode!=Mode.MULTIPLE)hideKeyboard();
                select.accept(entry.item);if(alive()&&mark!=null)mark.setChecked(checked.test(entry.item));
            });
        }
        void bind(Entry value){
            entry=value;icon.setImageBitmap(value.icon);if(value.icon==null)icon.setImageDrawable(owner.getPackageManager().getDefaultActivityIcon());
            name.setText(value.item.label);setContentDescription((mode==Mode.ADD?"添加应用 ":"")+value.item.label);
            if(mark!=null)mark.setChecked(checked.test(value.item));
        }
        @Override public void onInitializeAccessibilityNodeInfo(AccessibilityNodeInfo info){
            super.onInitializeAccessibilityNodeInfo(info);
            if(mark!=null){info.setClassName(mode==Mode.SINGLE?RadioButton.class.getName():CheckBox.class.getName());info.setCheckable(true);info.setChecked(mark.isChecked());}
        }
    }
    @Override public void close(){closed=true;if(work!=null)work.cancel(true);apps.clear();}
}
