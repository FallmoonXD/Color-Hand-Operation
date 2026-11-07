package replica;

import android.app.Activity;
import android.graphics.Color;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.Rect;
import android.graphics.RectF;
import android.graphics.drawable.Drawable;
import android.text.TextPaint;
import android.view.Gravity;
import android.view.View;
import android.view.WindowInsets;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
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
        applyInsets(activity.getWindow().getDecorView().getRootWindowInsets());
        root.addOnAttachStateChangeListener(new View.OnAttachStateChangeListener(){
            public void onViewAttachedToWindow(View view){WindowInsets current=view.getRootWindowInsets();if(current==null)current=activity.getWindow().getDecorView().getRootWindowInsets();applyInsets(current);view.requestApplyInsets();}
            public void onViewDetachedFromWindow(View view){}
        });
        bar=new Toolbar();bar.setGravity(Gravity.CENTER_VERTICAL);bar.setPadding(dp(12),0,dp(12),0);
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
        scroll.setPadding(0,dp(52),0,0);
        LinearLayout body=new LinearLayout(activity);body.setOrientation(LinearLayout.VERTICAL);scroll.addView(body,new ScrollView.LayoutParams(-1,-2));
        largeTitle=titleLabel(label,32,600);largeTitle.setPadding(dp(24),dp(4),dp(24),dp(22));if(home){body.addView(largeTitle,new LinearLayout.LayoutParams(-1,-2));title.setVisibility(View.INVISIBLE);title.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);largeTitle.setVisibility(View.INVISIBLE);largeTitle.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);}
        content=new LinearLayout(activity);content.setOrientation(LinearLayout.VERTICAL);content.setPadding(dp(16),dp(home?2:12),dp(16),dp(32));body.addView(content,new LinearLayout.LayoutParams(-1,-2));
        if(home){movingTitle=new CollapsingTitle(label);root.addView(movingTitle,new FrameLayout.LayoutParams(-1,-1));scroll.setOnScrollChangeListener((View.OnScrollChangeListener)(v,x,y,ox,oy)->movingTitle.updateGeometry());}
        refreshColors();
    }
    /** A pinned search field occupies the row immediately below the toolbar. */
    void setSearchHeader(View search, LinearLayout.LayoutParams params) {
        scroll.setPadding(0,0,0,0);
        page.setPadding(0,dp(52),0,0);
        page.addView(search,0,params);
    }
    private final class Toolbar extends LinearLayout {
        private final Path hole=new Path();
        private final RectF bounds=new RectF();
        private final Paint fill=new Paint(Paint.ANTI_ALIAS_FLAG);
        Toolbar(){super(activity);setWillNotDraw(false);}
        @Override protected void onDraw(Canvas canvas){
            // Expose the scrolling content only inside the material button.
            int save=canvas.save();View icon=home?homeMore:backIcon;
            if(icon!=null&&icon.getWidth()>0){
                float x,y;
                if(home){x=icon.getX()-getX();y=icon.getY()-getY();}
                else{View parent=(View)icon.getParent();x=parent.getX()+icon.getX();y=parent.getY()+icon.getY();}
                bounds.set(x,y,x+icon.getWidth(),y+icon.getHeight());
                hole.rewind();hole.addOval(bounds,Path.Direction.CW);canvas.clipOutPath(hole);
            }
            fill.setColor(ColorOsTheme.background(activity));canvas.drawRect(0,0,getWidth(),getHeight(),fill);
            canvas.restoreToCount(save);super.onDraw(canvas);
        }
        @Override public boolean onTouchEvent(android.view.MotionEvent event){return true;}
    }
    private TextView label(String value,int size,int weight){TextView t=new TextView(activity);t.setText(value);t.setTextSize(size);t.setTypeface(ColorOsTheme.typeface(activity,weight));t.setTextColor(ColorOsTheme.text(activity));t.setGravity(Gravity.CENTER_VERTICAL);t.setIncludeFontPadding(false);return t;}
    private TextView titleLabel(String value,int size,int weight){TextView t=label(value,size,weight);t.setSingleLine(true);t.setHorizontallyScrolling(false);t.setAutoSizeTextTypeUniformWithConfiguration(1,size,1,android.util.TypedValue.COMPLEX_UNIT_SP);return t;}
    private void applyInsets(WindowInsets insets){if(insets==null)return;android.graphics.Insets n=insets.getInsets(WindowInsets.Type.systemBars()|WindowInsets.Type.displayCutout());root.setPadding(n.left,n.top,n.right,n.bottom);}
    private int dp(float v){return Math.round(v*activity.getResources().getDisplayMetrics().density);}
    public void setMore(View.OnClickListener action,String description){
        ImageView more=new ImageView(activity);more.setImageResource(dev.edgeflow.R.drawable.cos_settings_more);more.setColorFilter(ColorOsTheme.text(activity));more.setScaleType(ImageView.ScaleType.CENTER_INSIDE);more.setPadding(dp(12),dp(12),dp(12),dp(12));more.setContentDescription(description);more.setTooltipText(description);more.setBackground(ColorDialogBuilder.touchBackground(activity,Color.TRANSPARENT,24));more.setOnClickListener(action);more.setFocusable(true);
        if(home){
            View slot=new View(activity);slot.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);setTrailing(slot);
            largeTitle.setPaddingRelative(dp(24),dp(4),dp(72),dp(22));homeMore=more;
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
        private float x,baseline,size,availableWidth,opacity=1,progress;
        private boolean ready,rtl;
        private android.view.ViewTreeObserver observer;
        private final android.view.ViewTreeObserver.OnPreDrawListener beforeDraw=()->{updateGeometry();return true;};
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
        @Override protected void onAttachedToWindow(){super.onAttachedToWindow();observer=root.getViewTreeObserver();observer.addOnPreDrawListener(beforeDraw);}
        @Override protected void onDetachedFromWindow(){if(observer!=null&&observer.isAlive())observer.removeOnPreDrawListener(beforeDraw);observer=null;super.onDetachedFromWindow();}
        private float localX(View child){float p=0;View v=child;while(v!=root){p+=v.getLeft()+v.getTranslationX();android.view.ViewParent parent=v.getParent();if(!(parent instanceof View))break;v=(View)parent;p-=v.getScrollX();}return p-getLeft();}
        private float localY(View child){float p=0;View v=child;while(v!=root){p+=v.getTop()+v.getTranslationY();android.view.ViewParent parent=v.getParent();if(!(parent instanceof View))break;v=(View)parent;p-=v.getScrollY();}return p-getTop();}
        void updateGeometry(){
            if(largeTitle.getWidth()==0||title.getWidth()==0||largeTitle.getBaseline()<0||title.getBaseline()<0)return;
            boolean nextRtl=getLayoutDirection()==LAYOUT_DIRECTION_RTL;
            float expandedX=localX(largeTitle)+(nextRtl?largeTitle.getWidth()-largeTitle.getCompoundPaddingRight():largeTitle.getCompoundPaddingLeft());
            float collapsedX=localX(title)+(nextRtl?title.getWidth()-title.getCompoundPaddingRight():title.getCompoundPaddingLeft());
            // Recover the unscrolled anchor; retain the existing boundary translation on pull-down.
            float expandedY=localY(largeTitle)+largeTitle.getBaseline()+scroll.getScrollY();
            float collapsedY=localY(title)+title.getBaseline();
            float p=ColorTitleMotion.progress(scroll.getScrollY(),expandedY-collapsedY);
            float nextX=ColorTitleMotion.lerp(expandedX,collapsedX,p),nextY=ColorTitleMotion.lerp(expandedY,collapsedY,p),nextSize=ColorTitleMotion.lerp(largeTitle.getTextSize(),title.getTextSize(),p),nextOpacity=ColorTitleMotion.alpha(p);
            float nextWidth=ColorTitleMotion.lerp(largeTitle.getWidth()-largeTitle.getCompoundPaddingLeft()-largeTitle.getCompoundPaddingRight(),title.getWidth()-title.getCompoundPaddingLeft()-title.getCompoundPaddingRight(),p);
            if(!ready||x!=nextX||baseline!=nextY||size!=nextSize||availableWidth!=nextWidth||opacity!=nextOpacity||rtl!=nextRtl||progress!=p){x=nextX;baseline=nextY;size=nextSize;availableWidth=Math.max(1,nextWidth);opacity=nextOpacity;progress=p;rtl=nextRtl;ready=true;invalidate();}
            if(homeMore!=null&&trailing!=null){preparePaint();Paint.FontMetrics fm=paint.getFontMetrics();float bx=localX(trailing)+getLeft(),by=baseline+getTop()+(fm.ascent+fm.descent)/2f-dp(24);if(homeMore.getX()!=bx||homeMore.getY()!=by){homeMore.setX(bx);homeMore.setY(by);bar.invalidate();}}
        }
        private float wordWidth(){return colorWord==null?0:paint.getTextSize()*colorWord.getIntrinsicWidth()/colorWord.getIntrinsicHeight();}
        private float textWidth(){return wordWidth()+paint.measureText(tail);}
        private void preparePaint(){paint.set(largeTitle.getPaint());paint.setTextSize(size);paint.setTypeface(ColorOsTheme.typeface(activity,Math.round(ColorTitleMotion.lerp(600,550,progress))));float measured=textWidth();if(measured>availableWidth)paint.setTextSize(size*availableWidth/measured);paint.setColor(ColorOsTheme.text(activity));paint.setAlpha(Math.round(Color.alpha(ColorOsTheme.text(activity))*opacity));paint.setTextAlign(Paint.Align.LEFT);}
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
