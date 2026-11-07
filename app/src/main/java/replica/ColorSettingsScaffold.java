package replica;

import android.app.Activity;
import android.graphics.Color;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.text.SpannableString;
import android.text.style.ReplacementSpan;
import android.graphics.drawable.Drawable;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewConfiguration;
import android.view.WindowInsets;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.ScrollView;
import android.widget.TextView;

/** Shared Settings chrome. Dimensions follow the installed Settings COUI resources. */
public final class ColorSettingsScaffold {
    public final FrameLayout root;
    public final LinearLayout page, bar, content;
    public final ScrollView scroll;
    private final Activity activity;
    private final TextView title;
    private final ColorAppBarMask mask;
    private View searchHeader, emptyResults;
    private ListView resultList;
    private int statusInset;
    private View trailing;
    private ImageView backIcon,moreIcon;
    private SettingsSurface backSurface,moreSurface;

    public ColorSettingsScaffold(Activity activity, String label, boolean home, Runnable onBack) {
        this.activity=activity;
        root=new FrameLayout(activity);page=new PageBody();page.setOrientation(LinearLayout.VERTICAL);
        root.addView(page,new FrameLayout.LayoutParams(-1,-1));
        root.setOnApplyWindowInsetsListener((v,insets)->{applyInsets(insets);return insets;});
        // A new page is attached to an already laid-out host. Seed padding before its
        // first measure/animation frame; requesting dispatch alone can be one frame late.
        root.addOnAttachStateChangeListener(new View.OnAttachStateChangeListener(){
            private android.view.ViewTreeObserver observer;
            private final android.view.ViewTreeObserver.OnPreDrawListener beforeDraw=()->{
                updateHeader();return true;
            };
            public void onViewAttachedToWindow(View view){observer=view.getViewTreeObserver();observer.addOnPreDrawListener(beforeDraw);WindowInsets current=view.getRootWindowInsets();if(current==null)current=activity.getWindow().getDecorView().getRootWindowInsets();applyInsets(current);view.requestApplyInsets();}
            public void onViewDetachedFromWindow(View view){if(observer!=null&&observer.isAlive())observer.removeOnPreDrawListener(beforeDraw);observer=null;}
        });
        mask=new ColorAppBarMask(activity);root.addView(mask,new FrameLayout.LayoutParams(-1,dp(52)));
        bar=new LinearLayout(activity){@Override public boolean onTouchEvent(android.view.MotionEvent event){return true;}};
        bar.setGravity(Gravity.CENTER_VERTICAL);bar.setPadding(dp(12),0,dp(12),0);
        root.addView(bar,new FrameLayout.LayoutParams(-1,dp(52)));
        if(!home)bar.addView(toolbarButton(false,"返回",v->onBack.run()),new LinearLayout.LayoutParams(dp(48),dp(48)));
        title=titleLabel(label,18,550);title.setPadding(dp(home?8:6),0,dp(8),0);title.setAccessibilityHeading(true);
        if(home){
            SpannableString brand=new SpannableString(label);
            if(label.startsWith("Color"))brand.setSpan(new ColorWord(),0,5,SpannableString.SPAN_EXCLUSIVE_EXCLUSIVE);
            title.setText(brand);title.setContentDescription(label);
        }
        bar.addView(title,new LinearLayout.LayoutParams(0,-1,1));
        scroll=new ColorSettingsScrollView(activity);scroll.setFillViewport(true);scroll.setClipToPadding(false);scroll.setVerticalScrollBarEnabled(false);page.addView(scroll,new LinearLayout.LayoutParams(-1,0,1));
        scroll.setPadding(0,dp(52),0,0);
        LinearLayout body=new LinearLayout(activity);body.setOrientation(LinearLayout.VERTICAL);scroll.addView(body,new ScrollView.LayoutParams(-1,-2));
        content=new LinearLayout(activity);content.setOrientation(LinearLayout.VERTICAL);content.setPadding(dp(16),dp(12),dp(16),dp(32));body.addView(content,new LinearLayout.LayoutParams(-1,-2));
        applyInsets(activity.getWindow().getDecorView().getRootWindowInsets());
        refreshColors();
    }
    /** A pinned search field occupies the row immediately below the toolbar. */
    void setSearchHeader(View search, LinearLayout.LayoutParams params) {
        searchHeader=search;
        FrameLayout.LayoutParams lp=new FrameLayout.LayoutParams(params.width,params.height);
        lp.setMargins(params.leftMargin,statusInset+dp(52)+params.topMargin,params.rightMargin,params.bottomMargin);
        root.addView(search,lp);
        search.addOnLayoutChangeListener((v,l,t,r,b,ol,ot,or,ob)->updateHeader());
        root.requestApplyInsets();
    }
    void setResultList(ListView list,View empty) {
        resultList=list;emptyResults=empty;
        page.removeView(scroll);
        updateHeader();
    }
    /** Search stays visible while the result list scrolls. Starting a page does not open the IME. */
    public android.widget.EditText search(String hint, String initial, java.util.function.Consumer<String> changed) {
        SearchField field=new SearchField(activity,hint,initial,changed);
        LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(-1,-2);
        lp.setMargins(dp(16),dp(4),dp(16),dp(6));
        setSearchHeader(field,lp);
        return field.input;
    }
    /** ScrollView leaves a clickable child in charge when its content fits. Own only vertical
     * drags in that case; normal ViewGroup interception cancels the child's pending click. */
    private final class PageBody extends LinearLayout {
        final ColorScrollSpring edge=new ColorScrollSpring(this,this::applyPull);
        final ColorScrollPhysics.Gate gate=new ColorScrollPhysics.Gate();
        final int slop=ViewConfiguration.get(getContext()).getScaledTouchSlop();
        boolean shortContact,dragging;
        float startX,startY,lastY;
        long lastMove;
        double velocity;
        PageBody(){super(activity);}
        private void applyPull(){for(int i=0;i<getChildCount();i++)getChildAt(i).setTranslationY(edge.position());}
        private boolean fits(){
            View target=resultList!=null&&resultList.getVisibility()==VISIBLE?resultList:scroll;
            return !target.canScrollVertically(-1)&&!target.canScrollVertically(1);
        }
        private boolean takeDrag(MotionEvent event){
            if(!dragging&&shortContact&&gate.allowed()&&event.getActionMasked()==MotionEvent.ACTION_MOVE){
                dragging=true;lastY=startY+Math.copySign(slop,event.getRawY()-startY);
            }
            return dragging;
        }
        @Override public boolean dispatchTouchEvent(MotionEvent event){
            int action=event.getActionMasked();
            if(action==MotionEvent.ACTION_DOWN){
                dragging=edge.running()||edge.position()!=0;edge.stop();
                shortContact=dragging||fits();startX=event.getRawX();startY=lastY=event.getRawY();
                velocity=0;lastMove=event.getEventTime();gate.begin(false,event.getPointerCount());
            }else if(shortContact&&action==MotionEvent.ACTION_POINTER_DOWN){
                gate.block();edge.reset();
            }else if(shortContact&&action==MotionEvent.ACTION_MOVE&&event.getPointerCount()==1){
                gate.move(event.getRawX()-startX,event.getRawY()-startY,slop);
            }
            boolean handled=super.dispatchTouchEvent(event);
            if(dragging&&gate.allowed()&&action==MotionEvent.ACTION_MOVE&&edge.enabled()){
                float y=event.getRawY(),old=edge.position();
                edge.dragBy(lastY-y);
                velocity=(edge.position()-old)*1000/Math.max(1,event.getEventTime()-lastMove);
                lastY=y;lastMove=event.getEventTime();
            }
            if(action==MotionEvent.ACTION_UP||action==MotionEvent.ACTION_CANCEL){
                if(dragging)edge.release(action==MotionEvent.ACTION_UP&&event.getEventTime()-lastMove<80?velocity:0);
                dragging=shortContact=false;gate.end();
            }
            return handled;
        }
        @Override public boolean onInterceptTouchEvent(MotionEvent event){return takeDrag(event);}
        @Override public boolean onTouchEvent(MotionEvent event){
            if(shortContact){takeDrag(event);return true;}
            return super.onTouchEvent(event);
        }
        @Override public void onWindowFocusChanged(boolean focused){
            super.onWindowFocusChanged(focused);if(!focused)cancelPull();
        }
        @Override protected void onDetachedFromWindow(){cancelPull();super.onDetachedFromWindow();}
        private void cancelPull(){dragging=shortContact=false;gate.end();edge.reset();}
    }
    private void updateHeader(){
        int bottom=statusInset+dp(52);
        if(searchHeader!=null){
            FrameLayout.LayoutParams lp=(FrameLayout.LayoutParams)searchHeader.getLayoutParams();
            bottom=lp.topMargin+searchHeader.getMeasuredHeight()+lp.bottomMargin;
        }
        if(resultList!=null){
            if(resultList.getPaddingTop()!=bottom)resultList.setPadding(0,bottom,0,0);
            LinearLayout.LayoutParams lp=(LinearLayout.LayoutParams)emptyResults.getLayoutParams();
            if(lp.topMargin!=bottom){lp.topMargin=bottom;emptyResults.setLayoutParams(lp);}
        }else if(scroll.getPaddingTop()!=bottom)scroll.setPadding(0,bottom,0,0);
        int offset=scroll.getScrollY();
        if(resultList!=null){
            View first=resultList.getChildAt(0);
            offset=!resultList.isShown()||first==null?0:resultList.getFirstVisiblePosition()>0?dp(30):resultList.getPaddingTop()-first.getTop();
        }
        int extra=dp(searchHeader==null?24:8);
        int height=bottom+extra;
        // Only the overlay bounds follow the moving title. Avoid remeasuring the list on scroll.
        if(mask.getWidth()>0&&mask.getHeight()!=height)
            mask.layout(mask.getLeft(),mask.getTop(),mask.getRight(),mask.getTop()+height);
        mask.setTravel(offset,((PageBody)page).edge.position(),dp(30));
    }
    private TextView label(String value,int size,int weight){TextView t=new TextView(activity);t.setText(value);t.setTextSize(size);t.setTypeface(ColorOsTheme.typeface(activity,weight));t.setTextColor(ColorOsTheme.text(activity));t.setGravity(Gravity.CENTER_VERTICAL);t.setIncludeFontPadding(false);return t;}
    private TextView titleLabel(String value,int size,int weight){TextView t=label(value,size,weight);t.setSingleLine(true);t.setHorizontallyScrolling(false);t.setAutoSizeTextTypeUniformWithConfiguration(1,size,1,android.util.TypedValue.COMPLEX_UNIT_SP);return t;}
    private void applyInsets(WindowInsets insets){
        if(insets==null)return;
        android.graphics.Insets n=insets.getInsets(WindowInsets.Type.systemBars()|WindowInsets.Type.displayCutout());
        int bottom=searchHeader!=null?Math.max(n.bottom,insets.getInsets(WindowInsets.Type.ime()).bottom):n.bottom;
        root.setPadding(n.left,0,n.right,bottom);
        if(statusInset!=n.top){
            int delta=n.top-statusInset;statusInset=n.top;
            FrameLayout.LayoutParams lp=(FrameLayout.LayoutParams)bar.getLayoutParams();lp.topMargin=n.top;bar.setLayoutParams(lp);
            if(searchHeader!=null){lp=(FrameLayout.LayoutParams)searchHeader.getLayoutParams();lp.topMargin+=delta;searchHeader.setLayoutParams(lp);}
        }
        updateHeader();
    }
    private int dp(float v){return Math.round(v*activity.getResources().getDisplayMetrics().density);}
    public void setMore(View.OnClickListener action,String description){
        setTrailing(toolbarButton(true,description,action));
    }
    private FrameLayout toolbarButton(boolean more,String description,View.OnClickListener click){
        FrameLayout target=new FrameLayout(activity);
        target.setFocusable(true);target.setContentDescription(description);target.setOnClickListener(click);
        ImageView icon=new ImageView(activity);icon.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);
        if(more)icon.setImageResource(dev.edgeflow.R.drawable.cos_settings_more);
        else icon.setImageDrawable(SystemIcons.raw(activity,"status_bar_qs_back_arrow"));
        icon.setColorFilter(ColorOsTheme.text(activity));icon.setScaleType(ImageView.ScaleType.FIT_CENTER);
        int pad=dp(more?8:10);icon.setPadding(pad,pad,pad,pad);icon.setDuplicateParentStateEnabled(true);
        target.addView(icon,new FrameLayout.LayoutParams(dp(40),dp(40),Gravity.CENTER));
        SettingsSurface surface=new SettingsSurface(icon,SettingsSurface.Kind.TOOLBAR,20,null);
        if(more){target.setTooltipText(description);moreIcon=icon;moreSurface=surface;}
        else{backIcon=icon;backSurface=surface;}
        return target;
    }
    public void setTrailing(View view){if(trailing!=null)bar.removeView(trailing);trailing=view;bar.addView(view,new LinearLayout.LayoutParams(Math.max(dp(48),view.getMinimumWidth()),dp(48)));}
    public void refreshColors(){
        int fg=ColorOsTheme.text(activity),bg=ColorOsTheme.background(activity);
        root.setBackgroundColor(bg);page.setBackgroundColor(bg);title.setTextColor(fg);
        if(moreIcon!=null){moreIcon.setColorFilter(fg);moreSurface.refresh();}
        if(backIcon!=null){backIcon.setColorFilter(fg);backSurface.refresh();}
    }

    /** Draw the website's Color glyphs on the same baseline and with the same em as the toolbar text. */
    private final class ColorWord extends ReplacementSpan {
        private final Drawable word;
        ColorWord(){
            int weight=activity.getResources().getConfiguration().fontWeightAdjustment;
            boolean bold=weight!=Integer.MAX_VALUE&&weight>=100;
            word=activity.getDrawable(bold?dev.edgeflow.R.drawable.title_color_bold:dev.edgeflow.R.drawable.title_color).mutate();
        }
        @Override public int getSize(Paint paint,CharSequence value,int start,int end,Paint.FontMetricsInt metrics){
            if(metrics!=null)paint.getFontMetricsInt(metrics);
            return Math.round(paint.getTextSize()*word.getIntrinsicWidth()/word.getIntrinsicHeight());
        }
        @Override public void draw(Canvas canvas,CharSequence value,int start,int end,float x,int top,int y,int bottom,Paint paint){
            int width=getSize(paint,value,start,end,null);
            word.setTint(paint.getColor());word.setBounds(0,0,width,Math.round(paint.getTextSize()));
            int save=canvas.save();canvas.translate(x,y-.8f*paint.getTextSize());word.draw(canvas);canvas.restoreToCount(save);
        }
    }
}
