package replica;

import android.app.Activity;
import android.graphics.Color;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Rect;
import android.graphics.drawable.Drawable;
import android.text.TextPaint;
import android.view.Gravity;
import android.view.View;
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
    private final TextView title, largeTitle;
    private final boolean home;
    private final ColorAppBarMask mask;
    private View searchHeader, emptyResults;
    private ListView resultList;
    private int statusInset;
    private View trailing,homeMore,backIcon;
    private SettingsSurface toolbarSurface;
    private CollapsingTitle movingTitle;

    public ColorSettingsScaffold(Activity activity, String label, boolean home, Runnable onBack) {
        this.activity=activity;this.home=home;
        root=new FrameLayout(activity);page=new LinearLayout(activity);page.setOrientation(LinearLayout.VERTICAL);
        root.addView(page,new FrameLayout.LayoutParams(-1,-1));
        root.setOnApplyWindowInsetsListener((v,insets)->{applyInsets(insets);return insets;});
        // A new page is attached to an already laid-out host. Seed padding before its
        // first measure/animation frame; requesting dispatch alone can be one frame late.
        root.addOnAttachStateChangeListener(new View.OnAttachStateChangeListener(){
            private android.view.ViewTreeObserver observer;
            private final android.view.ViewTreeObserver.OnPreDrawListener beforeDraw=()->{
                if(movingTitle!=null)movingTitle.updateGeometry();
                updateHeader();return true;
            };
            public void onViewAttachedToWindow(View view){observer=view.getViewTreeObserver();observer.addOnPreDrawListener(beforeDraw);WindowInsets current=view.getRootWindowInsets();if(current==null)current=activity.getWindow().getDecorView().getRootWindowInsets();applyInsets(current);view.requestApplyInsets();}
            public void onViewDetachedFromWindow(View view){if(observer!=null&&observer.isAlive())observer.removeOnPreDrawListener(beforeDraw);observer=null;}
        });
        mask=new ColorAppBarMask(activity);root.addView(mask,new FrameLayout.LayoutParams(-1,dp(52)));
        bar=new LinearLayout(activity){@Override public boolean onTouchEvent(android.view.MotionEvent event){return true;}};
        bar.setGravity(Gravity.CENTER_VERTICAL);bar.setPadding(dp(12),0,dp(12),0);
        root.addView(bar,new FrameLayout.LayoutParams(-1,dp(52)));
        if(!home) {
            FrameLayout target=new FrameLayout(activity);
            ImageView back=new ImageView(activity);
            backIcon=back;
            back.setImageDrawable(SystemIcons.raw(activity,"status_bar_qs_back_arrow"));
            back.setColorFilter(ColorOsTheme.text(activity));
            back.setScaleType(ImageView.ScaleType.FIT_CENTER);
            back.setPadding(dp(10),dp(10),dp(10),dp(10));
            back.setDuplicateParentStateEnabled(true);
            toolbarSurface=new SettingsSurface(back,SettingsSurface.Kind.TOOLBAR,20,null);
            target.addView(back,new FrameLayout.LayoutParams(dp(40),dp(40),Gravity.CENTER));
            target.setFocusable(true);
            target.setContentDescription("返回");
            target.setOnClickListener(v->onBack.run());
            bar.addView(target,new LinearLayout.LayoutParams(dp(48),dp(48)));
        }
        title=titleLabel(label,18,550);title.setPadding(dp(home?8:6),0,dp(8),0);bar.addView(title,new LinearLayout.LayoutParams(0,-1,1));
        scroll=new ColorSettingsScrollView(activity);scroll.setFillViewport(true);scroll.setClipToPadding(false);scroll.setVerticalScrollBarEnabled(false);page.addView(scroll,new LinearLayout.LayoutParams(-1,0,1));
        scroll.setPadding(0,home?0:dp(52),0,0);
        LinearLayout body=new LinearLayout(activity);body.setOrientation(LinearLayout.VERTICAL);scroll.addView(body,new ScrollView.LayoutParams(-1,-2));
        largeTitle=titleLabel(label,32,600);
        if(home){
            largeTitle.setMinHeight(dp(64));largeTitle.setPaddingRelative(dp(24),dp(8),dp(24),0);
            body.addView(largeTitle,new LinearLayout.LayoutParams(-1,-2));
            title.setVisibility(View.INVISIBLE);title.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);
            largeTitle.setVisibility(View.INVISIBLE);largeTitle.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);
        }
        content=new LinearLayout(activity);content.setOrientation(LinearLayout.VERTICAL);content.setPadding(dp(16),dp(home?2:12),dp(16),dp(32));body.addView(content,new LinearLayout.LayoutParams(-1,-2));
        if(home){movingTitle=new CollapsingTitle(label);root.addView(movingTitle,new FrameLayout.LayoutParams(-1,-1));}
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
        }else{
            // The home body's first row already provides the title and its trailing button.
            int start=home?statusInset:bottom;
            if(scroll.getPaddingTop()!=start)scroll.setPadding(0,start,0,0);
        }
        int offset=scroll.getScrollY();
        if(resultList!=null){
            View first=resultList.getChildAt(0);
            offset=!resultList.isShown()||first==null?0:resultList.getFirstVisiblePosition()>0?dp(30):resultList.getPaddingTop()-first.getTop();
        }
        int extra;
        if(home){
            if(homeMore!=null)bottom=Math.max(bottom,Math.round(homeMore.getY())+homeMore.getHeight());
            extra=dp(14);
        }else extra=dp(searchHeader==null?24:8);
        int height=bottom+extra;
        // Only the overlay bounds follow the moving title. Avoid remeasuring the list on scroll.
        if(mask.getWidth()>0&&mask.getHeight()!=height)
            mask.layout(mask.getLeft(),mask.getTop(),mask.getRight(),mask.getTop()+height);
        mask.setFraction(ColorTitleMotion.progress(offset,dp(30)));
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
        ImageView more=new ImageView(activity);more.setImageResource(dev.edgeflow.R.drawable.cos_settings_more);more.setColorFilter(ColorOsTheme.text(activity));more.setScaleType(ImageView.ScaleType.CENTER_INSIDE);more.setPadding(dp(12),dp(12),dp(12),dp(12));more.setContentDescription(description);more.setTooltipText(description);more.setBackground(ColorDialogBuilder.touchBackground(activity,Color.TRANSPARENT,24));more.setOnClickListener(action);more.setFocusable(true);
        if(home){
            View slot=new View(activity);slot.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);setTrailing(slot);
            largeTitle.setPaddingRelative(dp(24),largeTitle.getPaddingTop(),dp(72),largeTitle.getPaddingBottom());homeMore=more;
            toolbarSurface=new SettingsSurface(more,SettingsSurface.Kind.TOOLBAR,24,null);
            root.addView(more,new FrameLayout.LayoutParams(dp(48),dp(48)));movingTitle.updateGeometry();
        }else setTrailing(more);
    }
    public void setTrailing(View view){if(trailing!=null)bar.removeView(trailing);trailing=view;bar.addView(view,new LinearLayout.LayoutParams(Math.max(dp(48),view.getMinimumWidth()),dp(48)));}
    public void refreshColors(){root.setBackgroundColor(ColorOsTheme.background(activity));page.setBackgroundColor(ColorOsTheme.background(activity));title.setTextColor(ColorOsTheme.text(activity));largeTitle.setTextColor(ColorOsTheme.text(activity));if(homeMore instanceof ImageView)((ImageView)homeMore).setColorFilter(ColorOsTheme.text(activity));if(backIcon instanceof ImageView)((ImageView)backIcon).setColorFilter(ColorOsTheme.text(activity));if(toolbarSurface!=null)toolbarSurface.refresh();bar.invalidate();if(movingTitle!=null)movingTitle.invalidate();}

    /** One title outside ScrollView clipping; placeholders keep the accepted resting geometry. */
    private final class CollapsingTitle extends View {
        private final TextPaint paint=new TextPaint(Paint.ANTI_ALIAS_FLAG|Paint.SUBPIXEL_TEXT_FLAG);
        private final Drawable colorWord;
        private final String tail;
        private final Rect bounds=new Rect();
        private float x,baseline,size,availableWidth;
        private boolean ready,rtl;
        CollapsingTitle(String text){
            super(activity);
            boolean brand="Color Hand Operation".equals(text);
            int adjustment=getResources().getConfiguration().fontWeightAdjustment;
            boolean bold=adjustment!=Integer.MAX_VALUE&&adjustment>=100;
            colorWord=brand?activity.getDrawable(bold?dev.edgeflow.R.drawable.title_color_bold:dev.edgeflow.R.drawable.title_color).mutate():null;
            tail=brand?text.substring("Color".length()):text;
            setContentDescription(text);setAccessibilityHeading(true);
            setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_YES);setClickable(false);setFocusable(false);
        }
        private float localX(View child){float p=0;View v=child;while(v!=root){p+=v.getLeft()+v.getTranslationX();android.view.ViewParent parent=v.getParent();if(!(parent instanceof View))break;v=(View)parent;p-=v.getScrollX();}return p-getLeft();}
        private float localY(View child){float p=0;View v=child;while(v!=root){p+=v.getTop()+v.getTranslationY();android.view.ViewParent parent=v.getParent();if(!(parent instanceof View))break;v=(View)parent;p-=v.getScrollY();}return p-getTop();}
        void updateGeometry(){
            if(largeTitle.getWidth()==0||title.getWidth()==0||largeTitle.getBaseline()<0||title.getBaseline()<0)return;
            boolean nextRtl=getLayoutDirection()==LAYOUT_DIRECTION_RTL;
            float expandedX=localX(largeTitle)+(nextRtl?largeTitle.getWidth()-largeTitle.getCompoundPaddingRight():largeTitle.getCompoundPaddingLeft());
            float collapsedX=localX(title)+(nextRtl?title.getWidth()-title.getCompoundPaddingRight():title.getCompoundPaddingLeft());
            // The list springs on pull-down. The app bar stays at its expanded anchor.
            float expandedY=localY(largeTitle)+largeTitle.getBaseline()+scroll.getScrollY()-scroll.getChildAt(0).getTranslationY();
            // Fit the whole wordmark once to the narrower endpoint. Scroll affects position only.
            float width=Math.max(1,Math.min(largeTitle.getWidth()-largeTitle.getCompoundPaddingLeft()-largeTitle.getCompoundPaddingRight(),title.getWidth()-title.getCompoundPaddingLeft()-title.getCompoundPaddingRight()));
            float textSize=largeTitle.getTextSize();
            boolean metricsChanged=!ready||availableWidth!=width||size!=textSize;
            if(metricsChanged){availableWidth=width;size=textSize;preparePaint();}
            float centerOffset=(paint.ascent()+paint.descent())*.5f;
            float collapsedY=localY(title)+title.getHeight()*.5f-centerOffset;
            float p=ColorTitleMotion.progress(scroll.getScrollY(),expandedY-collapsedY);
            float nextX=ColorTitleMotion.lerp(expandedX,collapsedX,p),nextY=ColorTitleMotion.lerp(expandedY,collapsedY,p);
            if(metricsChanged||x!=nextX||baseline!=nextY||rtl!=nextRtl){x=nextX;baseline=nextY;rtl=nextRtl;ready=true;invalidate();}
            if(homeMore!=null&&trailing!=null){float bx=localX(trailing)+getLeft(),by=baseline+getTop()+centerOffset-dp(24);if(homeMore.getX()!=bx||homeMore.getY()!=by){homeMore.setX(bx);homeMore.setY(by);}}
        }
        private float wordWidth(){return colorWord==null?0:paint.getTextSize()*colorWord.getIntrinsicWidth()/colorWord.getIntrinsicHeight();}
        private float textWidth(){return wordWidth()+paint.measureText(tail);}
        private void preparePaint(){paint.set(largeTitle.getPaint());paint.setTextSize(size);paint.setTextSize(ColorTitleMotion.fittedSize(size,availableWidth,textWidth()));paint.setColor(ColorOsTheme.text(activity));paint.setTextAlign(Paint.Align.LEFT);}
        @Override protected void onDraw(Canvas canvas){
            super.onDraw(canvas);if(!ready)return;preparePaint();float start=rtl?x-textWidth():x;
            if(colorWord!=null){
                float height=paint.getTextSize(),advance=wordWidth();
                colorWord.setTint(paint.getColor()|0xff000000);colorWord.setAlpha(paint.getAlpha());
                colorWord.setBounds(0,0,Math.round(advance),Math.round(height));
                // The l and r in the hero wordmark rest at y=160 in its 200-unit viewport.
                int save=canvas.save();canvas.translate(start,baseline-.8f*height);colorWord.draw(canvas);canvas.restoreToCount(save);
                start+=advance;
            }
            canvas.drawText(tail,start,baseline,paint);
        }
        @Override public void onInitializeAccessibilityNodeInfo(android.view.accessibility.AccessibilityNodeInfo info){super.onInitializeAccessibilityNodeInfo(info);info.setClassName(TextView.class.getName());if(ready){preparePaint();float width=textWidth();Paint.FontMetrics fm=paint.getFontMetrics();bounds.set((int)Math.floor(rtl?x-width:x),(int)Math.floor(baseline+fm.ascent),(int)Math.ceil(rtl?x:x+width),(int)Math.ceil(baseline+fm.descent));Rect parentBounds=new Rect(bounds);parentBounds.offset(getLeft(),getTop());info.setBoundsInParent(parentBounds);int[] location=new int[2];getLocationOnScreen(location);bounds.offset(location[0],location[1]);info.setBoundsInScreen(bounds);}}
    }
}
