package replica;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.animation.ValueAnimator;
import android.app.Activity;
import android.app.Application;
import android.app.Dialog;
import android.content.res.ColorStateList;
import android.graphics.Color;
import android.graphics.Insets;
import android.graphics.drawable.ColorDrawable;
import android.os.Bundle;
import android.text.TextUtils;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.VelocityTracker;
import android.view.View;
import android.view.ViewConfiguration;
import android.view.ViewGroup;
import android.view.Window;
import android.view.WindowInsets;
import android.view.WindowInsetsController;
import android.view.WindowManager;
import android.view.accessibility.AccessibilityNodeInfo;
import android.widget.CompoundButton;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import java.util.IdentityHashMap;
import java.util.ArrayList;

/** Settings action chooser in a bottom sheet. The caller owns the list and row actions.
 * No OEM APK code or AOSP AlertController/list styling is used. Single-use, Activity-owned. */
public final class ColorActionSheet implements AutoCloseable {
    private final Activity owner;
    private CharSequence title="选择操作",negativeLabel;
    private View content;
    private Runnable negativeAction,dismissListener,pendingAction;
    private SheetDialog dialog;
    private FrameLayout embeddedHost;
    private boolean embedded,hostClippedToPadding;
    private final IdentityHashMap<View,Integer> obscuredChildren=new IdentityHashMap<>();
    private final View.OnLayoutChangeListener embeddedLayout=(v,l,t,r,b,ol,ot,or,ob)->syncEmbeddedLayout();
    private SheetRoot root;
    private View shade,handle,divider;
    private FrameLayout dragArea,header;
    private LinearLayout panel,footer;
    private SettingsSurface surface;
    private TextView heading,cancel;
    private ImageView close;
    private ValueAnimator animator;
    private ColorOsTheme.Subscription theme;
    private Application.ActivityLifecycleCallbacks lifecycle;
    private final IdentityHashMap<TextView,TextRole> roles=new IdentityHashMap<>();
    private final ArrayList<EditText> editors=new ArrayList<>();
    private Insets safe=Insets.NONE;
    private int imeBottom;
    private boolean showing,closing,closed,notified,started,suspended,skipEntryAnimation;
    private int suspendedAccessibility;
    private float reveal;
    private final Runnable updateGeometry=()->layoutSheet();
    private final Runnable enterWhenLaidOut=new Runnable(){@Override public void run(){
        if(closed||closing||suspended||!root.isAttachedToWindow())return;
        layoutSheet();FrameLayout.LayoutParams p=(FrameLayout.LayoutParams)panel.getLayoutParams();
        if(root.getWidth()==0||root.getHeight()==0||p.height<=0||panel.getHeight()!=p.height){root.postOnAnimation(this);return;}
        started=true;if(skipEntryAnimation){pose(1);panel.setVisibility(View.VISIBLE);}else animateTo(1,null);
    }};
    private final DragHandler drag=new DragHandler();

    public ColorActionSheet(Activity owner){if(owner==null)throw new IllegalArgumentException("Activity owner required");this.owner=owner;}
    public ColorActionSheet setTitle(CharSequence value){checkMutable();title=value;return this;}
    public ColorActionSheet setView(View value){checkMutable();content=value;return this;}
    public ColorActionSheet setNegativeButton(CharSequence value,Runnable action){checkMutable();negativeLabel=value;negativeAction=action;return this;}
    public ColorActionSheet setOnDismissListener(Runnable action){checkMutable();dismissListener=action;return this;}
    private void checkMutable(){if(showing||closed)throw new IllegalStateException("Configure a new sheet before showing it");}
    public boolean isShowing(){return showing&&!closed&&!suspended&&(!embedded||root!=null&&root.isAttachedToWindow()&&root.isShown());}
    public boolean isSuspended(){return suspended&&!closed;}
    /** Retains this Activity's window and view tree while its configuration page is visible. */
    public boolean suspend(){
        if(!showing||closed||suspended||closing||root==null||!ownerAvailable()||!embedded&&(dialog==null||!dialog.isShowing()))return false;
        suspended=true;pendingAction=null;stopAnimation();drag.clear();
        root.removeCallbacks(enterWhenLaidOut);root.removeCallbacks(updateGeometry);root.cancelPendingInputEvents();
        suspendedAccessibility=root.getImportantForAccessibility();root.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO_HIDE_DESCENDANTS);
        started=true;skipEntryAnimation=true;pose(1);panel.setVisibility(View.VISIBLE);if(!embedded)dialog.hide();return true;
    }
    /** Shows the retained window at its final pose; never builds another view or replays entry. */
    public void resume(){
        if(!isSuspended())return;
        if(!ownerAvailable()||root==null||!embedded&&dialog==null){dismissImmediately();return;}
        pendingAction=null;stopAnimation();drag.clear();root.removeCallbacks(enterWhenLaidOut);root.removeCallbacks(updateGeometry);
        started=true;skipEntryAnimation=true;syncEmbeddedLayout();refreshOwnerInsets();refreshColors();layoutSheet();pose(1);
        FrameLayout.LayoutParams p=(FrameLayout.LayoutParams)panel.getLayoutParams();
        // A retained embedded page keeps valid measurements while detached from pageHost.
        // It must paint on the very first return-transition frame, before reattachment callbacks.
        boolean laidOut=(embedded||root.isAttachedToWindow())&&root.getWidth()>0&&root.getHeight()>0&&p.height>0&&panel.getHeight()==p.height;
        panel.setVisibility(laidOut?View.VISIBLE:View.INVISIBLE);
        root.setImportantForAccessibility(suspendedAccessibility);
        if(!embedded)try{dialog.show();}catch(WindowManager.BadTokenException|IllegalStateException invalid){dismissImmediately();return;}
        suspended=false;root.requestApplyInsets();if(!laidOut)root.post(enterWhenLaidOut);
    }
    /** Call after asynchronously replacing/loading list rows. Does not recreate the window. */
    public void refreshContent(){
        if(closed||content==null)return;
        // The host may refresh retained rows before our own theme observer runs.
        // Resolve their previous semantic roles against the new palette first.
        if(root!=null)refreshColors();
        roles.clear();editors.clear();captureRoles(content);
        if(root!=null){refreshColors();root.removeCallbacks(updateGeometry);root.post(updateGeometry);}
    }
    public ColorActionSheet show(){return show(false);}
    /** New Activity only: recreate saved content without replaying the chooser entry animation. */
    public ColorActionSheet showRestored(){return show(true);}
    /** Chooser-only host: the real sheet stays inside its retained owner page during navigation. */
    public ColorActionSheet showEmbedded(FrameLayout host){return showEmbedded(host,false);}
    public ColorActionSheet showEmbeddedRestored(FrameLayout host){return showEmbedded(host,true);}
    private ColorActionSheet showEmbedded(FrameLayout host,boolean restored){
        checkMutable();if(content==null||host==null)throw new IllegalStateException("Action list and page host required");
        if(!ownerAvailable()){dismissImmediately();return this;}
        embedded=true;embeddedHost=host;skipEntryAnimation=restored;build();
        hostClippedToPadding=host.getClipToPadding();host.setClipToPadding(false);
        for(int i=0;i<host.getChildCount();i++){View child=host.getChildAt(i);obscuredChildren.put(child,child.getImportantForAccessibility());child.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO_HIDE_DESCENDANTS);}
        host.addView(root,new FrameLayout.LayoutParams(-1,-1));host.addOnLayoutChangeListener(embeddedLayout);
        syncEmbeddedLayout();refreshOwnerInsets();showing=true;observeOwner();
        theme=ColorOsTheme.observe(owner,()->{refreshColors();root.removeCallbacks(updateGeometry);root.post(updateGeometry);});
        root.requestApplyInsets();root.post(enterWhenLaidOut);return this;
    }
    private ColorActionSheet show(boolean restored){
        checkMutable();if(content==null)throw new IllegalStateException("Action list required");
        if(!ownerAvailable()){dismissImmediately();return this;}
        skipEntryAnimation=restored;
        dialog=new SheetDialog();dialog.setOwnerActivity(owner);dialog.setCancelable(true);
        dialog.setOnDismissListener(ignored->{if(!closed){release();notifyDismiss();}});
        try{dialog.show();}catch(WindowManager.BadTokenException|IllegalStateException invalid){dismissImmediately();return this;}
        showing=true;observeOwner();theme=ColorOsTheme.observe(owner,()->{refreshColors();root.removeCallbacks(updateGeometry);root.post(updateGeometry);});
        root.post(enterWhenLaidOut);
        return this;
    }

    /** Waits for the sheet to leave and detach, then performs the chosen navigation/action. */
    public void dismissThen(Runnable action){if(closed||closing)return;pendingAction=action;startDismiss();}
    public void dismiss(){if(closed||closing)return;pendingAction=null;startDismiss();}
    private void startDismiss(){
        if(suspended){pendingAction=null;finish(false);return;}
        if(!showing||root==null||!root.isAttachedToWindow()||!ownerAvailable()){finish(false);return;}
        closing=true;drag.clear();panel.setDescendantFocusability(ViewGroup.FOCUS_BLOCK_DESCENDANTS);
        animateTo(0,()->finish(true));
    }
    /** Owner pause/destroy cancels any deferred selection, rather than navigating in the background. */
    public void dismissImmediately(){pendingAction=null;finish(false);}
    @Override public void close(){dismissImmediately();}
    private void finish(boolean dispatch){
        if(closed)return;Runnable action=dispatch?pendingAction:null;
        release();if(dialog!=null)dialog.removeNow();unmountEmbedded();notifyDismiss();
        if(action!=null&&ownerAvailable())action.run();
    }
    private void release(){
        closed=true;closing=true;showing=false;suspended=false;pendingAction=null;drag.clear();
        stopAnimation();
        if(root!=null){root.removeCallbacks(updateGeometry);root.removeCallbacks(enterWhenLaidOut);}
        if(theme!=null){theme.close();theme=null;}
        if(lifecycle!=null){owner.getApplication().unregisterActivityLifecycleCallbacks(lifecycle);lifecycle=null;}
        roles.clear();editors.clear();
    }
    private void notifyDismiss(){if(notified)return;notified=true;Runnable callback=dismissListener;dismissListener=null;if(callback!=null)callback.run();}
    private void stopAnimation(){if(animator!=null){animator.removeAllUpdateListeners();animator.removeAllListeners();animator.cancel();animator=null;}}
    private void refreshOwnerInsets(){
        WindowInsets insets=owner.getWindow().getDecorView().getRootWindowInsets();
        if(insets!=null){safe=insets.getInsets(WindowInsets.Type.systemBars()|WindowInsets.Type.displayCutout());imeBottom=insets.getInsets(WindowInsets.Type.ime()).bottom;}
    }
    private void syncEmbeddedLayout(){
        if(embeddedHost==null||root==null||root.getParent()!=embeddedHost)return;
        FrameLayout.LayoutParams p=(FrameLayout.LayoutParams)root.getLayoutParams();
        int left=-embeddedHost.getPaddingLeft(),top=-embeddedHost.getPaddingTop(),right=-embeddedHost.getPaddingRight(),bottom=-embeddedHost.getPaddingBottom();
        if(p.leftMargin!=left||p.topMargin!=top||p.rightMargin!=right||p.bottomMargin!=bottom){p.setMargins(left,top,right,bottom);root.setLayoutParams(p);}
    }
    private void unmountEmbedded(){
        if(embeddedHost==null)return;
        embeddedHost.removeOnLayoutChangeListener(embeddedLayout);embeddedHost.removeView(root);embeddedHost.setClipToPadding(hostClippedToPadding);
        for(java.util.Map.Entry<View,Integer> item:obscuredChildren.entrySet())item.getKey().setImportantForAccessibility(item.getValue());
        obscuredChildren.clear();embeddedHost=null;
    }

    private void build(){
        root=new SheetRoot();root.setClipChildren(false);root.setClipToPadding(false);
        shade=new View(owner);shade.setBackgroundColor(0xff000000);shade.setAlpha(0);shade.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);shade.setOnClickListener(v->dismiss());root.addView(shade,new FrameLayout.LayoutParams(-1,-1));
        panel=new LinearLayout(owner){
            @Override public void onInitializeAccessibilityNodeInfo(AccessibilityNodeInfo info){super.onInitializeAccessibilityNodeInfo(info);info.setDismissable(true);info.addAction(AccessibilityNodeInfo.AccessibilityAction.ACTION_DISMISS);}
            @Override public boolean performAccessibilityAction(int action,Bundle args){if(action==AccessibilityNodeInfo.ACTION_DISMISS){dismiss();return true;}return super.performAccessibilityAction(action,args);}
        };
        panel.setOrientation(LinearLayout.VERTICAL);panel.setVisibility(View.INVISIBLE);panel.setClipToOutline(true);panel.setClickable(true);panel.setOnClickListener(v->{});panel.setAccessibilityPaneTitle(title);
        surface=new SettingsSurface(panel,embedded?SettingsSurface.Kind.EMBEDDED_SHEET:SettingsSurface.Kind.SHEET,28,null);
        root.addView(panel,new FrameLayout.LayoutParams(-1,-2,Gravity.BOTTOM|Gravity.LEFT));
        dragArea=new FrameLayout(owner);dragArea.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);dragArea.setOnTouchListener(drag);
        handle=new View(owner);FrameLayout.LayoutParams hp=new FrameLayout.LayoutParams(dp(28),dp(4),Gravity.CENTER);dragArea.addView(handle,hp);panel.addView(dragArea,new LinearLayout.LayoutParams(-1,dp(24)));
        header=new FrameLayout(owner);header.setPadding(dp(20),0,dp(12),0);header.setMinimumHeight(dp(56));header.setOnTouchListener(drag);
        heading=new TextView(owner);heading.setText(title);heading.setTextSize(20);heading.setTypeface(ColorOsTheme.typeface(owner,600));heading.setSingleLine(true);heading.setEllipsize(TextUtils.TruncateAt.END);heading.setGravity(Gravity.CENTER_VERTICAL);heading.setIncludeFontPadding(false);heading.setPadding(0,dp(10),0,dp(10));heading.setAccessibilityHeading(true);
        FrameLayout.LayoutParams tp=new FrameLayout.LayoutParams(-1,-2,Gravity.CENTER_VERTICAL|Gravity.START);tp.setMarginEnd(dp(56));header.addView(heading,tp);
        close=new ImageView(owner);close.setImageDrawable(SystemIcons.raw(owner,"ic_close_white"));close.setScaleType(ImageView.ScaleType.CENTER_INSIDE);close.setPadding(dp(13),dp(13),dp(13),dp(13));close.setContentDescription("关闭"+title);close.setFocusable(true);close.setOnClickListener(v->dismiss());header.addView(close,new FrameLayout.LayoutParams(dp(48),dp(48),Gravity.END|Gravity.CENTER_VERTICAL));
        panel.addView(header,new LinearLayout.LayoutParams(-1,-2));
        divider=new View(owner);divider.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);LinearLayout.LayoutParams dl=new LinearLayout.LayoutParams(-1,Math.max(1,dp(.5f)));dl.setMargins(dp(20),0,dp(20),dp(4));panel.addView(divider,dl);
        if(content.getParent() instanceof ViewGroup)((ViewGroup)content.getParent()).removeView(content);
        panel.addView(content,new LinearLayout.LayoutParams(-1,0,1));
        footer=new LinearLayout(owner);footer.setOrientation(LinearLayout.VERTICAL);footer.setPadding(dp(16),dp(8),dp(16),dp(12));
        if(!TextUtils.isEmpty(negativeLabel)){
            cancel=new TextView(owner);cancel.setText(negativeLabel);cancel.setTextSize(16);cancel.setGravity(Gravity.CENTER);cancel.setMinHeight(dp(48));cancel.setPadding(dp(12),dp(10),dp(12),dp(10));cancel.setFocusable(true);cancel.setContentDescription(negativeLabel);cancel.setOnClickListener(v->dismissThen(negativeAction));footer.addView(cancel,new LinearLayout.LayoutParams(-1,-2));
        }else footer.setPadding(0,0,0,dp(12));
        panel.addView(footer,new LinearLayout.LayoutParams(-1,-2));
        captureRoles(content);refreshColors();
        root.setOnApplyWindowInsetsListener((v,insets)->{syncEmbeddedLayout();safe=insets.getInsets(WindowInsets.Type.systemBars()|WindowInsets.Type.displayCutout());imeBottom=insets.getInsets(WindowInsets.Type.ime()).bottom;layoutSheet();return insets;});
    }

    private void layoutSheet(){
        if(closed||root==null||root.getWidth()==0||root.getHeight()==0)return;
        ColorSheetGeometry.Bounds b=ColorSheetGeometry.fit(root.getWidth(),root.getHeight(),safe.left,safe.top,safe.right,safe.bottom,imeBottom,density(),Integer.MAX_VALUE);
        dragArea.setVisibility(b.compact?View.GONE:View.VISIBLE);footer.setVisibility(b.compact?View.GONE:View.VISIBLE);
        int exact=View.MeasureSpec.makeMeasureSpec(b.width,View.MeasureSpec.EXACTLY),free=View.MeasureSpec.makeMeasureSpec(0,View.MeasureSpec.UNSPECIFIED);
        header.measure(exact,free);footer.measure(exact,free);content.measure(exact,free);
        int chrome=header.getMeasuredHeight()+dp(5)+(b.compact?0:dp(24)+footer.getMeasuredHeight());
        b=ColorSheetGeometry.fit(root.getWidth(),root.getHeight(),safe.left,safe.top,safe.right,safe.bottom,imeBottom,density(),chrome+Math.max(dp(64),content.getMeasuredHeight()));
        FrameLayout.LayoutParams p=(FrameLayout.LayoutParams)panel.getLayoutParams();
        if(p.width!=b.width||p.height!=b.height||p.leftMargin!=b.left||p.bottomMargin!=b.bottom){p.width=b.width;p.height=b.height;p.leftMargin=b.left;p.bottomMargin=b.bottom;panel.setLayoutParams(p);}
        pose(reveal);if(!started)panel.setVisibility(View.INVISIBLE);
    }
    private void pose(float value){
        reveal=Math.max(0,Math.min(1,value));if(panel==null)return;
        if(!embedded)surface.setRevealAlpha(reveal==0?0:1);
        FrameLayout.LayoutParams p=(FrameLayout.LayoutParams)panel.getLayoutParams();
        float distance=Math.max(1,p.height>0?p.height:panel.getHeight())+p.bottomMargin;
        panel.setTranslationY((1-reveal)*distance);shade.setAlpha(.28f*reveal);
    }
    private void animateTo(float end,Runnable done){
        if(animator!=null){animator.removeAllListeners();animator.cancel();}
        panel.setVisibility(View.VISIBLE);
        if(!ValueAnimator.areAnimatorsEnabled()||ColorOsTheme.animationScale(owner)==0){pose(end);if(done!=null)done.run();return;}
        animator=ValueAnimator.ofFloat(reveal,end);animator.setDuration(ColorMotion.SHEET_MS);animator.setInterpolator(end==1?ColorMotion.ENTER:ColorMotion.EXIT);
        animator.addUpdateListener(value->pose((float)value.getAnimatedValue()));
        if(done!=null)animator.addListener(new AnimatorListenerAdapter(){@Override public void onAnimationEnd(Animator animation){done.run();}});
        animator.start();
    }
    private void refreshColors(){
        if(panel==null)return;surface.refresh();
        heading.setTextColor(ColorOsTheme.text(owner));heading.setTypeface(ColorOsTheme.typeface(owner,600));
        handle.setBackground(PanelStyle.shape(owner,ColorOsTheme.tertiary(owner),2));divider.setBackgroundColor(ColorOsTheme.dark(owner)?0x18ffffff:0x10000000);
        close.setColorFilter(ColorOsTheme.text(owner));close.setBackground(ColorDialogBuilder.touchBackground(owner,ColorOsTheme.fill(owner),24));
        if(cancel!=null){cancel.setTextColor(ColorOsTheme.accent(owner));cancel.setTypeface(ColorOsTheme.typeface(owner,500));cancel.setBackground(ColorDialogBuilder.touchBackground(owner,ColorOsTheme.fill(owner),24));}
        for(java.util.Map.Entry<TextView,TextRole> entry:roles.entrySet())entry.getValue().apply(entry.getKey());
        for(EditText edit:editors)ColorDialogBuilder.styleInput(edit);
        Window w=dialog==null?null:dialog.getWindow();if(w!=null&&w.peekDecorView()!=null){WindowInsetsController bars=w.getInsetsController();if(bars!=null){int mask=WindowInsetsController.APPEARANCE_LIGHT_STATUS_BARS|WindowInsetsController.APPEARANCE_LIGHT_NAVIGATION_BARS;bars.setSystemBarsAppearance(ColorOsTheme.dark(owner)?0:mask,mask);}}
    }
    private void captureRoles(View v){
        if(v instanceof CompoundButton)return;
        if(v instanceof EditText){editors.add((EditText)v);ColorDialogBuilder.styleInput((EditText)v);return;}
        if(v instanceof TextView)roles.put((TextView)v,new TextRole((TextView)v));
        if(v instanceof ViewGroup)for(int i=0;i<((ViewGroup)v).getChildCount();i++)captureRoles(((ViewGroup)v).getChildAt(i));
    }
    private final class TextRole {
        final int role,weight;final ColorStateList fixed;
        TextRole(TextView v){fixed=v.getTextColors();int c=v.getCurrentTextColor();role=c==ColorOsTheme.text(owner)?1:c==ColorOsTheme.secondary(owner)?2:c==ColorOsTheme.tertiary(owner)?3:c==ColorOsTheme.accent(owner)?4:c==ColorOsTheme.error(owner)?5:0;int adjustment=owner.getResources().getConfiguration().fontWeightAdjustment;if(adjustment==Integer.MAX_VALUE)adjustment=0;weight=Math.max(100,Math.min(900,(v.getTypeface()==null?400:v.getTypeface().getWeight())-adjustment));}
        void apply(TextView v){v.setTypeface(ColorOsTheme.typeface(owner,weight));if(role==0)v.setTextColor(fixed);else v.setTextColor(role==1?ColorOsTheme.text(owner):role==2?ColorOsTheme.secondary(owner):role==3?ColorOsTheme.tertiary(owner):role==4?ColorOsTheme.accent(owner):ColorOsTheme.error(owner));}
    }
    private void observeOwner(){
        lifecycle=new Application.ActivityLifecycleCallbacks(){
            public void onActivityCreated(Activity a,Bundle b){}public void onActivityStarted(Activity a){}public void onActivityResumed(Activity a){}
            public void onActivityPaused(Activity a){if(a==owner&&(closing||pendingAction!=null||!embedded&&!suspended))dismissImmediately();}public void onActivityStopped(Activity a){if(a==owner&&(closing||pendingAction!=null||!embedded&&!suspended))dismissImmediately();}
            public void onActivitySaveInstanceState(Activity a,Bundle b){}public void onActivityDestroyed(Activity a){if(a==owner)dismissImmediately();}
        };owner.getApplication().registerActivityLifecycleCallbacks(lifecycle);
    }
    private boolean ownerAvailable(){return !owner.isFinishing()&&!owner.isDestroyed();}
    private float density(){return owner.getResources().getDisplayMetrics().density;}
    private int dp(float n){return Math.round(n*density());}
    private static boolean hasEditor(View v){if(v.onCheckIsTextEditor())return true;if(v instanceof ViewGroup)for(int i=0;i<((ViewGroup)v).getChildCount();i++)if(hasEditor(((ViewGroup)v).getChildAt(i)))return true;return false;}

    private final class SheetDialog extends Dialog {
        SheetDialog(){super(owner);requestWindowFeature(Window.FEATURE_NO_TITLE);}
        @Override protected void onCreate(Bundle state){
            super.onCreate(state);build();setContentView(root);Window w=getWindow();
            w.setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));w.getDecorView().setPadding(0,0,0,0);w.setDecorFitsSystemWindows(false);
            w.clearFlags(WindowManager.LayoutParams.FLAG_DIM_BEHIND);w.setWindowAnimations(0);
            w.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE);
            if(hasEditor(content))w.clearFlags(WindowManager.LayoutParams.FLAG_ALT_FOCUSABLE_IM);
            w.setStatusBarColor(Color.TRANSPARENT);w.setNavigationBarColor(Color.TRANSPARENT);w.setNavigationBarContrastEnforced(false);
            WindowManager.LayoutParams p=w.getAttributes();p.layoutInDisplayCutoutMode=WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES;p.gravity=Gravity.BOTTOM;w.setAttributes(p);
            refreshColors();
        }
        @Override protected void onStart(){super.onStart();getWindow().setLayout(-1,-1);root.requestApplyInsets();}
        @Override public void onBackPressed(){if(!suspended)ColorActionSheet.this.dismiss();}
        @Override public void cancel(){if(!suspended)ColorActionSheet.this.dismiss();}
        @Override public void dismiss(){ColorActionSheet.this.dismiss();}
        void removeNow(){super.dismiss();}
    }
    private final class SheetRoot extends FrameLayout {
        SheetRoot(){super(owner);}
        @Override protected void onSizeChanged(int w,int h,int oldw,int oldh){super.onSizeChanged(w,h,oldw,oldh);removeCallbacks(updateGeometry);post(updateGeometry);}
        @Override public boolean dispatchTouchEvent(MotionEvent e){if(closing||suspended)return true;return super.dispatchTouchEvent(e);}
    }
    private final class DragHandler implements View.OnTouchListener {
        VelocityTracker velocity;float startY,base;int pointer=-1;boolean dragging;
        @Override public boolean onTouch(View v,MotionEvent e){
            if(closing||closed||suspended)return true;int action=e.getActionMasked();
            if(action==MotionEvent.ACTION_DOWN){clear();if(animator!=null){animator.removeAllListeners();animator.cancel();}pointer=e.getPointerId(0);startY=e.getRawY();base=panel.getTranslationY();velocity=VelocityTracker.obtain();velocity.addMovement(e);return true;}
            if(pointer<0)return false;if(velocity!=null)velocity.addMovement(e);
            if(action==MotionEvent.ACTION_POINTER_DOWN||action==MotionEvent.ACTION_CANCEL){clear();animateTo(1,null);return true;}
            if(action==MotionEvent.ACTION_MOVE){float dy=e.getRawY()-startY;if(Math.abs(dy)>ViewConfiguration.get(owner).getScaledTouchSlop())dragging=true;if(dragging){float distance=panel.getHeight()+((FrameLayout.LayoutParams)panel.getLayoutParams()).bottomMargin;pose(1-Math.max(0,base+dy)/Math.max(1,distance));}return true;}
            if(action==MotionEvent.ACTION_UP){float speed=0;if(velocity!=null){velocity.computeCurrentVelocity(1000);speed=velocity.getYVelocity(pointer);}boolean leave=dragging&&(panel.getTranslationY()>Math.min(dp(120),panel.getHeight()*.25f)||(speed>dp(900)&&panel.getTranslationY()>ViewConfiguration.get(owner).getScaledTouchSlop()));clear();if(leave)dismiss();else animateTo(1,null);return true;}
            return true;
        }
        void clear(){if(velocity!=null){velocity.recycle();velocity=null;}pointer=-1;dragging=false;}
    }
}
