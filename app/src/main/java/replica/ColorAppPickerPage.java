package replica;

import android.app.Activity;
import android.content.*;
import android.content.pm.*;
import android.graphics.*;
import android.graphics.drawable.*;
import android.text.*;
import android.view.*;
import android.widget.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.function.Consumer;

/** Settings application page: fixed search field, real icons and a cancellable worker query. */
public final class ColorAppPickerPage implements AutoCloseable {
    public final ColorSettingsScaffold scaffold;
    private final Activity owner;
    private final HashSet<String> excluded;
    private final Consumer<ToolsConfig.Item> selected;
    private final LinearLayout rows;
    private final EditText search;
    private final ArrayList<Entry> apps=new ArrayList<>();
    private Future<?> work;
    private boolean closed,loading=true;
    private int initialScroll;
    private static final class Entry {
        final ToolsConfig.Item item;final Drawable icon;
        Entry(ToolsConfig.Item item,Drawable icon){this.item=item;this.icon=icon;}
    }
    public ColorAppPickerPage(Activity owner,ExecutorService worker,Collection<String> excluded,String query,int y,Runnable back,Consumer<ToolsConfig.Item> selected){
        this.owner=owner;this.excluded=new HashSet<>(excluded);this.selected=selected;initialScroll=y;
        scaffold=new ColorSettingsScaffold(owner,"添加应用快捷入口",false,back);
        search=ColorDialogBuilder.editText(owner,"搜索应用");search.setContentDescription("搜索应用");search.setSingleLine(true);search.setText(query);search.setImeOptions(android.view.inputmethod.EditorInfo.IME_ACTION_DONE);
        LinearLayout.LayoutParams sp=new LinearLayout.LayoutParams(-1,-2);sp.setMargins(dp(16),dp(8),dp(16),dp(4));scaffold.setSearchHeader(search,sp);
        scaffold.root.setOnApplyWindowInsetsListener((view,insets)->{android.graphics.Insets bars=insets.getInsets(WindowInsets.Type.systemBars()|WindowInsets.Type.displayCutout());int bottom=Math.max(bars.bottom,insets.getInsets(WindowInsets.Type.ime()).bottom);view.setPadding(bars.left,bars.top,bars.right,bottom);return insets;});
        rows=new LinearLayout(owner);rows.setOrientation(LinearLayout.VERTICAL);rows.setPadding(dp(4),dp(4),dp(4),dp(4));rows.setBackground(PanelStyle.shape(owner,ColorOsTheme.card(owner),20));scaffold.content.addView(rows,new LinearLayout.LayoutParams(-1,-2));
        message("正在读取应用…");search.addTextChangedListener(new TextWatcher(){public void beforeTextChanged(CharSequence s,int st,int c,int a){}public void onTextChanged(CharSequence s,int st,int before,int count){initialScroll=0;if(!loading)filter();}public void afterTextChanged(Editable e){}});
        work=worker.submit(()->load());
    }
    private int dp(float value){return PanelStyle.dp(owner,value);}
    private boolean alive(){return !closed&&!owner.isFinishing()&&!owner.isDestroyed();}
    public String query(){return search.getText().toString();}
    public int scrollY(){return scaffold.scroll.getScrollY();}
    public void hideKeyboard(){android.view.inputmethod.InputMethodManager ime=owner.getSystemService(android.view.inputmethod.InputMethodManager.class);if(ime!=null)ime.hideSoftInputFromWindow(search.getWindowToken(),0);search.clearFocus();}
    private void load(){
        ArrayList<Entry> result=new ArrayList<>();String error=null;
        try{
            PackageManager pm=owner.getPackageManager();HashSet<String> seen=new HashSet<>();int size=dp(40);
            for(ResolveInfo resolve:pm.queryIntentActivities(new Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER),0)){
                if(Thread.currentThread().isInterrupted())return;ActivityInfo info=resolve.activityInfo;if(info==null||!info.enabled||!info.exported||info.applicationInfo==null||!info.applicationInfo.enabled)continue;
                ComponentName component=new ComponentName(info.packageName,info.name);String key="app:"+component.flattenToString();if(excluded.contains(key)||!seen.add(key))continue;
                String title=resolve.loadLabel(pm).toString();Drawable raw;try{raw=pm.getActivityIcon(component);}catch(Exception missing){raw=pm.getDefaultActivityIcon();}
                Bitmap bitmap=Bitmap.createBitmap(size,size,Bitmap.Config.ARGB_8888);raw=raw.mutate();raw.setBounds(0,0,size,size);raw.draw(new Canvas(bitmap));result.add(new Entry(new ToolsConfig.Item(key,title),new BitmapDrawable(owner.getResources(),bitmap)));
            }
            java.text.Collator collator=java.text.Collator.getInstance(Locale.CHINA);result.sort((a,b)->collator.compare(a.item.label,b.item.label));
        }catch(RuntimeException failure){error="无法读取应用列表";}
        String failure=error;owner.runOnUiThread(()->{if(!alive())return;loading=false;if(failure!=null){message(failure);return;}apps.addAll(result);filter();});
    }
    private TextView label(String value,int size,int weight,int color){TextView v=new TextView(owner);v.setText(value);v.setTextSize(size);v.setTypeface(ColorOsTheme.typeface(owner,weight));v.setTextColor(color);v.setIncludeFontPadding(false);v.setGravity(Gravity.CENTER_VERTICAL);return v;}
    private void message(String message){rows.removeAllViews();TextView label=label(message,15,400,ColorOsTheme.secondary(owner));label.setPadding(dp(16),dp(20),dp(16),dp(20));label.setAccessibilityLiveRegion(View.ACCESSIBILITY_LIVE_REGION_POLITE);rows.addView(label);}
    private void filter(){
        if(!alive())return;rows.removeAllViews();String q=query().trim().toLowerCase(Locale.ROOT);int count=0;
        for(Entry entry:apps){ToolsConfig.Item item=entry.item;if(!q.isEmpty()&&!item.label.toLowerCase(Locale.ROOT).contains(q)&&!item.key.toLowerCase(Locale.ROOT).contains(q))continue;
            if(count++>0){View divider=new View(owner);divider.setBackgroundColor((ColorOsTheme.text(owner)&0xffffff)|0x10000000);LinearLayout.LayoutParams dp=new LinearLayout.LayoutParams(-1,Math.max(1,dp(.5f)));dp.setMargins(dp(16),0,dp(16),0);rows.addView(divider,dp);}
            LinearLayout row=new LinearLayout(owner);row.setGravity(Gravity.CENTER_VERTICAL);row.setMinimumHeight(dp(72));row.setPadding(dp(16),dp(12),dp(16),dp(12));row.setBackground(ColorDialogBuilder.touchBackground(owner,Color.TRANSPARENT,16));row.setFocusable(true);row.setContentDescription("添加应用 "+item.label);
            ImageView icon=new ImageView(owner);icon.setImageDrawable(entry.icon);icon.setScaleType(ImageView.ScaleType.FIT_CENTER);icon.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);LinearLayout.LayoutParams ip=new LinearLayout.LayoutParams(dp(40),dp(40));ip.setMarginEnd(dp(16));row.addView(icon,ip);
            TextView name=label(item.label,16,500,ColorOsTheme.text(owner));name.setMaxLines(2);name.setEllipsize(TextUtils.TruncateAt.END);row.addView(name,new LinearLayout.LayoutParams(0,-2,1));row.setOnClickListener(v->{if(alive()){hideKeyboard();selected.accept(item);}});rows.addView(row);
        }
        if(count==0)message(q.isEmpty()?"没有可添加的应用":"没有匹配的应用");int y=initialScroll;initialScroll=0;scaffold.scroll.post(()->{if(alive())scaffold.scroll.scrollTo(0,y);});
    }
    @Override public void close(){if(closed)return;closed=true;if(work!=null)work.cancel(true);apps.clear();}
}
