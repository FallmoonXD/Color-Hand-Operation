package replica;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.animation.ValueAnimator;
import android.view.View;
import android.view.ViewGroup;
import android.view.animation.PathInterpolator;
import android.widget.FrameLayout;

/** Installed Settings coui_{open,close}_slide curves, 350ms and 30% under-page travel. */
public final class ColorPageMotion {
    static final int PAGE_MS=350;
    static final PathInterpolator OPEN_IN=new PathInterpolator(.3f,.1f,.3f,1);
    private static final PathInterpolator OPEN_OUT=new PathInterpolator(.3f,.15f,.3f,1);
    private static final PathInterpolator CLOSE_IN=new PathInterpolator(.3f,.26f,.4f,1);
    static final PathInterpolator CLOSE_OUT=new PathInterpolator(.25f,.1f,.3f,1);
    private static final PathInterpolator OPACITY=new PathInterpolator(.33f,0,.67f,1);
    private ValueAnimator animation;
    private FrameLayout host;
    private FrameLayout current,previous;
    private long generation;
    private Runnable completion;
    private final java.util.WeakHashMap<FrameLayout,BlockedPage> blockedPages=new java.util.WeakHashMap<>();
    private static final class BlockedPage {
        final int focusability,accessibility;final boolean focusable;final java.lang.ref.WeakReference<View> blocker;
        BlockedPage(FrameLayout page,View blocker){focusability=page.getDescendantFocusability();accessibility=page.getImportantForAccessibility();focusable=page.isFocusable();this.blocker=new java.lang.ref.WeakReference<>(blocker);}
    }
    public void show(FrameLayout host,FrameLayout next,int direction){
        final float backX=direction<0&&current!=null?current.getTranslationX():0;
        final float backScale=direction<0&&current!=null?current.getScaleX():1;
        cancel();restoreInteraction(next);this.host=host;FrameLayout old=current;current=next;
        if(old==null||direction==0||!ValueAnimator.areAnimatorsEnabled()){host.removeAllViews();host.addView(next,new FrameLayout.LayoutParams(-1,-1));return;}
        previous=old;blockInteraction(old);host.addView(next,new FrameLayout.LayoutParams(-1,-1));if(direction<0)old.bringToFront();
        final long token=++generation;next.post(()->{if(token!=generation)return;float width=host.getWidth(),rtl=next.getLayoutDirection()==View.LAYOUT_DIRECTION_RTL?-1:1,closeSign=backX==0?rtl:Math.signum(backX);animation=ValueAnimator.ofFloat(0,1);animation.setDuration(PAGE_MS);animation.setInterpolator(input->input);animation.addUpdateListener(a->{float t=(float)a.getAnimatedValue();if(direction>0){next.setTranslationX(width*rtl*(1-OPEN_IN.getInterpolation(t)));old.setTranslationX(-.3f*width*rtl*OPEN_OUT.getInterpolation(t));old.setAlpha(1-.5f*OPACITY.getInterpolation(t));}else{next.setTranslationX(-.3f*width*closeSign*(1-CLOSE_IN.getInterpolation(t)));next.setAlpha(.5f+.5f*OPACITY.getInterpolation(t));old.setTranslationX(backX+(width*closeSign-backX)*CLOSE_OUT.getInterpolation(t));old.setScaleX(backScale+(1-backScale)*t);old.setScaleY(backScale+(1-backScale)*t);}});animation.addListener(new AnimatorListenerAdapter(){@Override public void onAnimationEnd(Animator a){if(token==generation){host.removeView(old);previous=null;reset(next);animation=null;Runnable action=completion;completion=null;if(action!=null)action.run();}}});animation.start();});
        float width=host.getWidth(),rtl=next.getLayoutDirection()==View.LAYOUT_DIRECTION_RTL?-1:1;next.setTranslationX(direction>0?width*rtl:-.3f*width*(backX==0?rtl:Math.signum(backX)));next.setAlpha(direction>0?1:.5f);if(direction<0){old.setTranslationX(backX);old.setScaleX(backScale);old.setScaleY(backScale);}
    }
    /** Runs after the current page transition settles; a new request replaces the old one.
     * Cancellation or subsequent navigation discards the pending action. */
    public void whenSettled(Runnable action){completion=null;if(previous==null){if(action!=null)action.run();}else completion=action;}
    public void progress(float progress,int edge){if(current==null)return;float p=Math.max(0,Math.min(1,progress));float sign=edge==0?1:-1;current.setPivotX(current.getWidth()/2f);current.setPivotY(current.getHeight()/2f);current.setScaleX(1-.035f*p);current.setScaleY(1-.035f*p);current.setTranslationX(current.getWidth()*.12f*p*sign);}
    // BackProgressAnimator has already sprung to zero before Android calls onBackCancelled.
    public void cancelBack(){if(current!=null){current.animate().cancel();current.setTranslationX(0);current.setScaleX(1);current.setScaleY(1);}}
    public void cancel(){generation++;completion=null;if(animation!=null){animation.cancel();animation=null;}if(previous!=null&&host!=null)host.removeView(previous);previous=null;if(current!=null){current.animate().cancel();reset(current);}}
    private void blockInteraction(FrameLayout page){
        restoreInteraction(page);
        View blocker=new View(page.getContext());blockedPages.put(page,new BlockedPage(page,blocker));
        // Keep enabled/checked drawable states intact while the old page is still visible.
        page.setDescendantFocusability(ViewGroup.FOCUS_BLOCK_DESCENDANTS);
        page.clearFocus();page.setFocusable(false);
        page.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO_HIDE_DESCENDANTS);
        blocker.setClickable(true);blocker.setFocusable(false);
        blocker.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);
        blocker.setOnTouchListener((view,event)->true);
        blocker.setOnGenericMotionListener((view,event)->true);
        page.addView(blocker,new FrameLayout.LayoutParams(-1,-1));
    }
    private void restoreInteraction(FrameLayout page){
        BlockedPage saved=blockedPages.remove(page);if(saved==null)return;
        View blocker=saved.blocker.get();if(blocker!=null)page.removeView(blocker);
        page.setDescendantFocusability(saved.focusability);page.setFocusable(saved.focusable);page.setImportantForAccessibility(saved.accessibility);
    }
    private static void reset(View v){v.setTranslationX(0);v.setTranslationY(0);v.setScaleX(1);v.setScaleY(1);v.setAlpha(1);v.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_AUTO);}
    public void clear(){cancel();blockedPages.clear();current=null;host=null;}
}
