package dev.edgeflow;

import android.animation.ValueAnimator;
import android.content.SharedPreferences;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Path;
import android.view.View;
import android.view.ViewGroup;
import android.view.animation.AccelerateInterpolator;
import replica.ColorOsTheme;

/** Settings-only distance guide. The page overlay draws above the controls without receiving touch. */
final class GestureDistancePreview extends View {
    private final ViewGroup page;
    private final SharedPreferences prefs;
    private final Paint paint=new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Path arrow=new Path();
    private final float density;
    private final Runnable fade=this::fade;
    private View slider;
    private boolean left;
    private float origin,center,tip;

    GestureDistancePreview(ViewGroup page,SharedPreferences prefs){
        super(page.getContext());this.page=page;this.prefs=prefs;
        density=getResources().getDisplayMetrics().density;
        setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_NO);
    }
    private String handle(boolean longer){
        String fallback=null;
        int count=HandleSettings.pairCount(prefs)*2;
        for(int i=0;i<count;i++){
            String id=GestureHandleConfig.IDS[i];
            if(longer&&!prefs.getBoolean(id+"_long_enabled",true))continue;
            if(prefs.getBoolean(id+"_enabled",!GestureHandleConfig.isExtra(id)))return id;
            if(fallback==null)fallback=id;
        }
        return fallback==null?"left":fallback;
    }
    void show(View source,boolean longer,float distanceDp){
        if(!page.isAttachedToWindow()||!source.isAttachedToWindow())return;
        removeCallbacks(fade);
        animate().cancel();setAlpha(1);
        if(slider!=source){
            String id=handle(longer);left=GestureHandleConfig.isLeft(id);
            GestureHandleConfig.Area area=HandleDisplayArea.current(getContext());
            HandleSettings.Geometry geometry=HandleSettings.read(prefs,id);
            int[] bounds=GestureHandleConfig.bounds(id,area.landscape()?geometry.effectiveLandscapeWidth():geometry.width,
                    geometry.start,geometry.end,density,area);
            int[] offset=new int[2];page.getLocationOnScreen(offset);
            layout(0,0,page.getWidth(),page.getHeight());
            origin=Math.max(0,Math.min(getWidth(),bounds[0]+bounds[2]*.5f-offset[0]));
            center=Math.max(12*density,Math.min(getHeight()-12*density,bounds[1]+bounds[3]*.5f-offset[1]));
            if(slider==null)page.getOverlay().add(this);
            slider=source;
        }
        float pixels=distanceDp*density;
        if(longer)pixels=GestureRecognizer.longThresholdPx(prefs.getFloat("trigger_dp",39.43f)*density,pixels);
        tip=Math.max(0,Math.min(getWidth(),origin+(left?pixels:-pixels)));invalidate();
    }
    void finish(View source,boolean cancelled){
        if(slider!=source)return;
        if(cancelled||!ValueAnimator.areAnimatorsEnabled()){clear();return;}
        removeCallbacks(fade);postDelayed(fade,100);
    }
    private void fade(){
        animate().alpha(0).setDuration(300).setInterpolator(new AccelerateInterpolator()).withEndAction(this::clear).start();
    }
    void clear(){
        removeCallbacks(fade);animate().cancel();
        if(slider!=null){slider=null;page.getOverlay().remove(this);}
        setAlpha(1);
    }
    @Override protected void onDraw(Canvas canvas){
        paint.setColor(ColorOsTheme.accent(getContext()));
        int saved=canvas.save();
        canvas.translate(tip,center);canvas.scale(left?density:-density,density);
        // OHO+ uses a 240 × 48 dp arrow image, clipped at the starting edge.
        arrow.reset();arrow.moveTo(0,0);arrow.lineTo(-12,-12);arrow.lineTo(-15,-9);
        arrow.lineTo(-8,-2);arrow.lineTo(-240,-2);arrow.lineTo(-240,2);
        arrow.lineTo(-8,2);arrow.lineTo(-15,9);arrow.lineTo(-12,12);arrow.close();
        canvas.drawPath(arrow,paint);canvas.restoreToCount(saved);
    }
}
