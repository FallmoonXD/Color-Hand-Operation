package replica;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.animation.ValueAnimator;
import android.app.Activity;
import android.app.Application;
import android.content.Context;
import android.content.ContextWrapper;
import android.graphics.Color;
import android.graphics.Insets;
import android.graphics.Rect;
import android.os.Bundle;
import android.text.TextUtils;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewTreeObserver;
import android.view.WindowInsets;
import android.view.WindowManager;
import android.view.accessibility.AccessibilityNodeInfo;
import android.view.animation.LinearInterpolator;
import android.widget.Button;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.PopupWindow;
import android.widget.ScrollView;
import android.widget.TextView;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.function.IntConsumer;

/** A settings menu anchored to its invoking control, without an AlertDialog title or scrim.
 * Uses this device's COUI menu dimensions and our live Settings palette, not foreign APK code.
 * A selected action runs only after the popup window has been removed. Single-use instance. */
public final class ColorPopupMenu implements AutoCloseable {
    private final View anchor;
    private final Context context;
    private final Activity owner;
    private final HashSet<Integer> separators=new HashSet<>(), destructive=new HashSet<>();
    private final ArrayList<View> rows=new ArrayList<>();
    private LauncherWidgetStyle launcher;
    private String[] icons;
    private android.graphics.Point anchorPoint;
    private final ArrayList<View> rules=new ArrayList<>();
    private String[] labels=new String[0];
    private CharSequence title, message;
    private IntConsumer selection;
    private Runnable onDismiss;
    private int rowHeightDp=48;
    private boolean alignEnd=true, focusable=true, showing, closing, closed, notifying;
    private int windowLayoutType;
    private int pending=-1;
    private MenuWindow window;
    private MenuCard card;
    private SettingsSurface surface;
    private LinearLayout content;
    private TextView heading, description;
    private ValueAnimator animator;
    private ColorOsTheme.Subscription theme;
    private Application.ActivityLifecycleCallbacks lifecycle;
    private ViewTreeObserver tree;
    private ColorPopupGeometry.Placement placement;
    private final Runnable relayout=()->layout(false);
    private final ViewTreeObserver.OnGlobalLayoutListener globalLayout=()->layout(false);
    private final ViewTreeObserver.OnScrollChangedListener scrolled=()->layout(false);
    private final View.OnAttachStateChangeListener attach=new View.OnAttachStateChangeListener(){
        @Override public void onViewAttachedToWindow(View v){}
        @Override public void onViewDetachedFromWindow(View v){dismissImmediately();}
    };

    public ColorPopupMenu(View anchor) {
        if(anchor==null)throw new IllegalArgumentException("A real invoking view is required");
        this.anchor=anchor; context=anchor.getContext(); owner=findActivity(context);
    }
    public ColorPopupMenu setItems(String[] items,IntConsumer callback){checkMutable();labels=items==null?new String[0]:items.clone();selection=callback;return this;}
    public ColorPopupMenu setTitle(CharSequence value){checkMutable();title=value;return this;}
    public ColorPopupMenu setMessage(CharSequence value){checkMutable();message=value;return this;}
    public ColorPopupMenu setAlignEnd(boolean value){checkMutable();alignEnd=value;return this;}
    public ColorPopupMenu setFocusable(boolean value){checkMutable();focusable=value;return this;}
    public ColorPopupMenu setWindowLayoutType(int value){checkMutable();windowLayoutType=value;return this;}
    public ColorPopupMenu setRowHeightDp(int value){checkMutable();rowHeightDp=Math.max(48,Math.min(72,value));return this;}
    public ColorPopupMenu setDividerBefore(int... positions){checkMutable();for(int i:positions)separators.add(i);return this;}
    public ColorPopupMenu setDestructive(int... positions){checkMutable();for(int i:positions)destructive.add(i);return this;}
    public ColorPopupMenu setOnDismiss(Runnable callback){checkMutable();onDismiss=callback;return this;}
    ColorPopupMenu launcher(LauncherWidgetStyle style,String[] drawables,android.graphics.Point point){
        checkMutable();if(drawables.length!=labels.length)throw new IllegalArgumentException("One icon per menu item");
        launcher=style;icons=drawables.clone();anchorPoint=point;rowHeightDp=40;return this;
    }
    private void checkMutable(){if(showing||closed)throw new IllegalStateException("Configure a new popup before showing it");}
    public boolean isShowing(){return showing&&!closed;}

    public ColorPopupMenu show() {
        checkMutable();
        if(labels.length==0)throw new IllegalStateException("A menu needs at least one action");
        if(!validOwner()||!anchor.isAttachedToWindow()){dismissImmediately();return this;}
        build();
        window=new MenuWindow(); window.setContentView(card); window.setFocusable(focusable);
        if(windowLayoutType!=0)window.setWindowLayoutType(windowLayoutType);
        window.setOutsideTouchable(true); window.setTouchable(true); window.setBackgroundDrawable(null);
        window.setElevation(dp(12)); window.setAnimationStyle(0);
        window.setEnterTransition(null); window.setExitTransition(null);
        window.setInputMethodMode(PopupWindow.INPUT_METHOD_NOT_NEEDED);
        window.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_NOTHING);
        // Geometry is in screen coordinates, including multi-window origins and cutout insets.
        window.setIsLaidOutInScreen(true); window.setClippingEnabled(true);
        window.setOnDismissListener(()->{if(!closed){pending=-1;release();notifyDismiss();}});
        refreshColors();
        if(!layout(true)){dismissImmediately();return this;}
        setReveal(0);card.setScaleX(0);card.setScaleY(0);
        try{showWindow();}
        catch(RuntimeException|ReflectiveOperationException failure){android.util.Log.w("ColorPopupMenu","Unable to show menu window",failure);dismissImmediately();return this;}
        showing=true;
        anchor.addOnAttachStateChangeListener(attach);
        tree=anchor.getRootView().getViewTreeObserver();tree.addOnGlobalLayoutListener(globalLayout);tree.addOnScrollChangedListener(scrolled);
        theme=ColorOsTheme.observe(context,()->{refreshColors();card.removeCallbacks(relayout);card.post(relayout);});
        observeOwner();
        card.setAccessibilityPaneTitle(title!=null?title:anchor.getContentDescription()!=null?anchor.getContentDescription():"选项");
        if(focusable&&!anchor.isInTouchMode()&&!rows.isEmpty())rows.get(0).requestFocus();
        animate(true,null);
        return this;
    }

    private void showWindow() throws ReflectiveOperationException {
        int gravity=Gravity.TOP|Gravity.LEFT;
        if(windowLayoutType!=WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY){
            window.showAtLocation(anchor,gravity,placement.x,placement.y);return;
        }
        ViewGroup.LayoutParams raw=anchor.getRootView().getLayoutParams();
        android.os.IBinder token=raw instanceof WindowManager.LayoutParams
                ?((WindowManager.LayoutParams)raw).token:null;
        if(token==null)throw new WindowManager.BadTokenException("Accessibility overlay has no registered window token");
        // View#getWindowToken is the parent window's IWindow binder. TYPE_ACCESSIBILITY_OVERLAY
        // instead needs the token registered by AccessibilityService and applied to its root LP.
        // Keep the same service WindowManager/type/NOT_FOCUSABLE policy; never fall back to an app window.
        org.lsposed.hiddenapibypass.HiddenApiBypass.invoke(PopupWindow.class,window,
                "showAtLocation",token,gravity,placement.x,placement.y);
    }

    private void build(){
        card=new MenuCard(context);card.setClipToOutline(true);
        if(launcher==null)surface=new SettingsSurface(card,SettingsSurface.Kind.POPUP,20,null);
        else card.setBackground(launcher.menuBackground());
        ScrollView scroll=new ScrollView(context);scroll.setFillViewport(false);scroll.setVerticalScrollBarEnabled(false);
        scroll.setOverScrollMode(View.OVER_SCROLL_NEVER);scroll.setBackgroundColor(Color.TRANSPARENT);
        content=new LinearLayout(context);content.setOrientation(LinearLayout.VERTICAL);content.setPadding(dp(8),dp(8),dp(8),dp(8));
        scroll.addView(content,new ScrollView.LayoutParams(-1,-2));card.addView(scroll,new FrameLayout.LayoutParams(-1,-1));
        if(launcher!=null){
            content.setPadding(0,dp(8),0,dp(8));
            for(int i=0;i<labels.length;i++){
                View row=launcher.menuRow(content,labels[i],icons[i]);final int index=i;
                row.setOnClickListener(v->{if(!closing&&!closed){pending=index;beginDismiss();}});
                rows.add(row);content.addView(row,new LinearLayout.LayoutParams(-1,-2));
            }
            return;
        }
        if(!TextUtils.isEmpty(title)){
            heading=label(title,16,500);heading.setPadding(dp(12),dp(10),dp(12),dp(6));heading.setMaxLines(3);heading.setAccessibilityHeading(true);
            content.addView(heading,new LinearLayout.LayoutParams(-1,-2));
        }
        if(!TextUtils.isEmpty(message)){
            description=label(message,14,400);description.setPadding(dp(12),0,dp(12),dp(10));description.setMaxLines(5);
            content.addView(description,new LinearLayout.LayoutParams(-1,-2));
        }
        if(heading!=null||description!=null)addRule();
        for(int i=0;i<labels.length;i++){
            if(i>0&&separators.contains(i))addRule();
            final int index=i;
            TextView row=new TextView(context){
                @Override public CharSequence getAccessibilityClassName(){return Button.class.getName();}
                @Override public void onInitializeAccessibilityNodeInfo(AccessibilityNodeInfo info){super.onInitializeAccessibilityNodeInfo(info);info.setCollectionItemInfo(AccessibilityNodeInfo.CollectionItemInfo.obtain(index,1,0,1,false));}
            };
            row.setText(labels[i]);row.setTextSize(16);row.setGravity(Gravity.CENTER_VERTICAL|Gravity.START);
            row.setIncludeFontPadding(true);row.setMaxLines(3);row.setEllipsize(TextUtils.TruncateAt.END);
            row.setPadding(dp(12),dp(10),dp(12),dp(10));row.setMinHeight(dp(rowHeightDp));
            row.setFocusable(true);row.setClickable(true);row.setContentDescription(labels[i]);
            row.setOnClickListener(v->{if(!closing&&!closed){pending=index;beginDismiss();}});
            rows.add(row);content.addView(row,new LinearLayout.LayoutParams(-1,-2));
        }
    }
    private TextView label(CharSequence text,int sp,int weight){TextView v=new TextView(context);v.setText(text);v.setTextSize(sp);v.setTypeface(ColorOsTheme.typeface(context,weight));v.setGravity(Gravity.START);v.setEllipsize(TextUtils.TruncateAt.END);return v;}
    private void addRule(){View rule=new View(context);LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(-1,Math.max(1,dp(.5f)));lp.setMargins(dp(12),dp(4),dp(12),dp(4));content.addView(rule,lp);rules.add(rule);}
    private void refreshColors(){
        if(card==null||launcher!=null)return;
        surface.refresh();
        if(heading!=null){heading.setTextColor(ColorOsTheme.text(context));heading.setTypeface(ColorOsTheme.typeface(context,500));}
        if(description!=null){description.setTextColor(ColorOsTheme.secondary(context));description.setTypeface(ColorOsTheme.typeface(context,400));}
        for(int i=0;i<rows.size();i++){TextView row=(TextView)rows.get(i);row.setTextColor(destructive.contains(i)?ColorOsTheme.error(context):ColorOsTheme.text(context));row.setTypeface(ColorOsTheme.typeface(context,400));row.setBackground(ColorDialogBuilder.touchBackground(context,Color.TRANSPARENT,12));}
        for(View rule:rules)rule.setBackgroundColor(ColorOsTheme.dark(context)?0x18ffffff:0x10000000);
    }

    private boolean layout(boolean first){
        if(closed||closing||card==null)return false;
        if(!validOwner()||!anchor.isAttachedToWindow()){if(!first)dismissImmediately();return false;}
        Rect bounds=ColorWindowSpace.available(context,anchor),a=new Rect();
        if(!anchor.getLocalVisibleRect(a)||bounds.isEmpty()){if(!first)dismissImmediately();return false;}
        if(anchorPoint!=null){
            int x=Math.max(a.left,Math.min(a.right-1,anchorPoint.x)),y=Math.max(a.top,Math.min(a.bottom-1,anchorPoint.y));
            a.set(x,y,x+1,y+1);
        }
        int[] position=new int[2];anchor.getLocationOnScreen(position);a.offset(position[0],position[1]);
        int limit=Math.max(1,bounds.width()-dp(32));
        int desired=dp(168);
        if(launcher!=null)desired=launcher.dim("popup_menu_width",216);
        else for(View item:rows){TextView row=(TextView)item;desired=Math.max(desired,(int)Math.ceil(row.getPaint().measureText(String.valueOf(row.getText())))+dp(40));}
        if(heading!=null||description!=null)desired=Math.max(desired,dp(232));
        int width=Math.min(limit,Math.min(dp(256),desired));
        content.measure(View.MeasureSpec.makeMeasureSpec(width,View.MeasureSpec.EXACTLY),View.MeasureSpec.makeMeasureSpec(0,View.MeasureSpec.UNSPECIFIED));
        boolean rtl=anchor.getLayoutDirection()==View.LAYOUT_DIRECTION_RTL;
        int gap=launcher==null?dp(8):anchorPoint==null?launcher.dim("popup_vertical_padding_for_card_and_widget",16):launcher.dim("popup_padding_top",8);
        ColorPopupGeometry.Placement next=ColorPopupGeometry.place(bounds.left,bounds.top,bounds.right,bounds.bottom,
                a.left,a.top,a.right,a.bottom,width,content.getMeasuredHeight(),alignEnd!=rtl,gap,dp(16),dp(rowHeightDp),launcher!=null);
        boolean changed=placement==null||placement.x!=next.x||placement.y!=next.y||placement.width!=next.width||placement.height!=next.height;
        placement=next;
        card.setPivotX(next.pivotX);card.setPivotY(next.pivotY);
        if(first){window.setWidth(next.width);window.setHeight(next.height);}
        else if(changed&&window.isShowing())window.update(next.x,next.y,next.width,next.height);
        return true;
    }

    public void dismiss(){pending=-1;beginDismiss();}
    private void beginDismiss(){
        if(closed||closing)return;
        if(!showing||!validOwner()||!card.isAttachedToWindow()){finishDismiss(false);return;}
        closing=true;for(View row:rows)row.setEnabled(false);
        animate(false,()->finishDismiss(true));
    }
    /** Lifecycle cancellation never dispatches a queued selection. */
    public void dismissImmediately(){pending=-1;finishDismiss(false);}
    @Override public void close(){dismissImmediately();}
    private void finishDismiss(boolean allowSelection){
        if(closed)return;
        final int selected=allowSelection?pending:-1;final IntConsumer callback=selection;
        release();
        if(window!=null&&window.isShowing())window.removeNow();
        notifyDismiss();
        if(selected>=0&&callback!=null&&validOwner()&&anchor.isAttachedToWindow())callback.accept(selected);
    }
    private void release(){
        closed=true;showing=false;closing=true;pending=-1;
        if(animator!=null){animator.removeAllUpdateListeners();animator.removeAllListeners();animator.cancel();animator=null;}
        if(card!=null)card.removeCallbacks(relayout);
        anchor.removeOnAttachStateChangeListener(attach);
        if(tree!=null&&tree.isAlive()){tree.removeOnGlobalLayoutListener(globalLayout);tree.removeOnScrollChangedListener(scrolled);}tree=null;
        if(theme!=null){theme.close();theme=null;}
        if(owner!=null&&lifecycle!=null){owner.getApplication().unregisterActivityLifecycleCallbacks(lifecycle);lifecycle=null;}
        selection=null;
    }
    private void notifyDismiss(){if(notifying)return;notifying=true;Runnable callback=onDismiss;onDismiss=null;if(callback!=null)callback.run();}
    private void setReveal(float alpha){
        // A translucent parent puts the blur drawable in an offscreen layer until the
        // last frame. Fade its compositor region and the menu contents independently.
        if(surface!=null)surface.setRevealAlpha(alpha);else card.getBackground().setAlpha(Math.round(255*alpha));
        content.setAlpha(alpha);
    }
    private void animate(boolean enter,Runnable done){
        if(animator!=null){animator.removeAllUpdateListeners();animator.removeAllListeners();animator.cancel();}
        float end=enter?1:0;
        if(!ValueAnimator.areAnimatorsEnabled()||ColorOsTheme.animationScale(context)==0){setReveal(end);card.setScaleX(end);card.setScaleY(end);if(done!=null)done.run();return;}
        // Settings PopupGravityMotionParams: opacity and scale have independent springs.
        ColorSpringTrack alpha=ColorSpringTrack.popupOpacity(content.getAlpha(),enter);
        ColorSpringTrack scale=ColorSpringTrack.popupScale(card.getScaleX(),enter);
        int duration=Math.max(alpha.durationMs,scale.durationMs);
        animator=ValueAnimator.ofFloat(0,duration);animator.setDuration(duration);
        animator.setInterpolator(new LinearInterpolator());
        animator.addUpdateListener(value->{float t=(float)value.getAnimatedValue();setReveal(alpha.valueAt(t));float s=scale.valueAt(t);card.setScaleX(s);card.setScaleY(s);});
        if(done!=null)animator.addListener(new AnimatorListenerAdapter(){@Override public void onAnimationEnd(Animator animation){done.run();}});
        animator.start();
    }
    private void observeOwner(){
        if(owner==null)return;
        lifecycle=new Application.ActivityLifecycleCallbacks(){
            public void onActivityCreated(Activity a,Bundle b){}public void onActivityStarted(Activity a){}public void onActivityResumed(Activity a){}
            public void onActivityPaused(Activity a){}public void onActivityStopped(Activity a){if(a==owner)dismissImmediately();}
            public void onActivitySaveInstanceState(Activity a,Bundle b){}public void onActivityDestroyed(Activity a){if(a==owner)dismissImmediately();}
        };owner.getApplication().registerActivityLifecycleCallbacks(lifecycle);
    }
    private boolean validOwner(){return owner==null||(!owner.isFinishing()&&!owner.isDestroyed());}
    private static Activity findActivity(Context c){while(c instanceof ContextWrapper){if(c instanceof Activity)return (Activity)c;Context next=((ContextWrapper)c).getBaseContext();if(next==c)break;c=next;}return c instanceof Activity?(Activity)c:null;}
    private int dp(float value){return Math.round(context.getResources().getDisplayMetrics().density*value);}
    private final class MenuWindow extends PopupWindow {
        @Override public void dismiss(){ColorPopupMenu.this.dismiss();}
        void removeNow(){super.dismiss();}
    }
    private final class MenuCard extends FrameLayout {
        MenuCard(Context c){super(c);setFocusable(true);setFocusableInTouchMode(true);setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_YES);}
        @Override public void onInitializeAccessibilityNodeInfo(AccessibilityNodeInfo info){super.onInitializeAccessibilityNodeInfo(info);info.setDismissable(true);info.addAction(AccessibilityNodeInfo.AccessibilityAction.ACTION_DISMISS);info.setCollectionInfo(AccessibilityNodeInfo.CollectionInfo.obtain(labels.length,1,false));}
        @Override public boolean performAccessibilityAction(int action,Bundle args){if(action==AccessibilityNodeInfo.ACTION_DISMISS){dismiss();return true;}return super.performAccessibilityAction(action,args);}
        @Override public boolean dispatchKeyEvent(KeyEvent event){if((event.getKeyCode()==KeyEvent.KEYCODE_BACK||event.getKeyCode()==KeyEvent.KEYCODE_ESCAPE)&&event.getAction()==KeyEvent.ACTION_UP){dismiss();return true;}return super.dispatchKeyEvent(event);}
    }
}
