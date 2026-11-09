package replica;

import android.app.Activity;
import android.app.Instrumentation;
import android.content.Context;
import android.content.res.Configuration;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.Rect;
import android.graphics.RectF;
import android.graphics.drawable.Drawable;
import android.os.Handler;
import android.os.Looper;
import android.view.PixelCopy;
import android.view.View;
import android.widget.FrameLayout;
import java.lang.reflect.Method;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

/** Draw the installed upper side-track and CHO in the same pixels on consecutive hardware frames. */
public final class VolumeTrackLightCheck {
    private static final int W=190,H=630;
    private static final RectF BOUNDS=new RectF(0,0,W,H);
    private static final class Reference {
        final Drawable drawable;
        final Object effect;
        final Method progress,position;
        Reference(Context system,int night)throws Exception{
            Configuration cfg=new Configuration(system.getResources().getConfiguration());
            cfg.uiMode=(cfg.uiMode&~Configuration.UI_MODE_NIGHT_MASK)|night;
            Context themed=system.createConfigurationContext(cfg);
            ClassLoader loader=system.getClassLoader();
            Class<?> type=loader.loadClass("com.coui.appcompat.spotlight.COUISpotLightEffectDrawable");
            Class<?> target=loader.loadClass("com.oplus.systemui.volume.utils.spotlight.VolumeSpotLightTarget");
            Class<?> params=loader.loadClass("com.oplus.systemui.volume.utils.spotlight.OplusVolumeSpotLightEffectParams");
            drawable=(Drawable)type.getConstructor().newInstance();
            params.getMethod("apply",target,Context.class,type).invoke(null,target.getField("SEEK_BAR_SIDE").get(null),themed,drawable);
            drawable.setBounds(0,0,W,H);
            java.lang.reflect.Field field=type.getDeclaredField("mEffect");field.setAccessible(true);effect=field.get(drawable);
            progress=effect.getClass().getDeclaredMethod("setProgress",float.class);progress.setAccessible(true);
            position=type.getMethod("setLightPosition",float.class,float.class);
        }
        void sample(float x,float y,float stage,boolean releasing)throws Exception{
            progress.invoke(effect,0f);position.invoke(drawable,x/W,y/H);
            effect.getClass().getField("mIsExtinguishAnimation").setBoolean(effect,releasing);
            progress.invoke(effect,10000f*stage);
        }
    }
    private static final class Pair extends View {
        final Reference[] refs;
        final VolumeTrackLight actual;
        final Paint paint=new Paint();final Path clip=new Path();
        boolean dark,release,reference;float x,y,progress;
        Pair(Activity host,Reference[] refs){super(host);this.refs=refs;actual=new VolumeTrackLight(this);clip.addRoundRect(BOUNDS,W/2f,W/2f,Path.Direction.CW);}
        @Override protected void onDraw(Canvas c){
            int save=c.save();
            paint.setColor(dark?0xff273646:0xffc9cacb);c.drawRect(BOUNDS,paint);
            paint.setColor(dark?0xff665241:0xff559cc2);c.drawRect(0,H*.43f,W,H*.72f,paint);
            if(reference){c.clipPath(clip);refs[dark?1:0].drawable.draw(c);}
            else actual.drawFrame(c,BOUNDS,W/2f,x,y,progress,release,dark);
            c.restoreToCount(save);
        }
    }
    public static String run(Instrumentation test,Activity host)throws Exception{
        Context system=host.createPackageContext("com.android.systemui",Context.CONTEXT_INCLUDE_CODE|Context.CONTEXT_IGNORE_SECURITY);
        Reference[] refs=new Reference[2];Pair[] pair={null};
        test.runOnMainSync(()->{
            try{
                refs[0]=new Reference(system,Configuration.UI_MODE_NIGHT_NO);refs[1]=new Reference(system,Configuration.UI_MODE_NIGHT_YES);
                pair[0]=new Pair(host,refs);FrameLayout root=new FrameLayout(host);
                root.addView(pair[0],new FrameLayout.LayoutParams(W,H));host.setContentView(root);
            }catch(Exception error){throw new AssertionError(error);}
        });
        test.waitForIdleSync();
        int cases=0,maxError=0;
        for(boolean dark:new boolean[]{false,true})for(int index=0;index<5;index++){
            final int phase=index;
            test.runOnMainSync(()->{
                Pair p=pair[0];p.dark=dark;p.release=phase==3;
                p.x=phase<2?49:135;p.y=phase<2?165:362;p.progress=new float[]{0,.66f,1,.46f,0}[phase];
                try{refs[dark?1:0].sample(p.x,p.y,p.progress,p.release);}catch(Exception e){throw new AssertionError(e);}
            });
            int[] stock=capture(test,host,pair[0],true),cho=capture(test,host,pair[0],false);
            int changed=0,max=0;long total=0;
            for(int y=0;y<H;y++)for(int x=0;x<W;x++){
                int a=stock[W*y+x],b=cho[W*y+x];int error=Math.max(Math.abs(Color.red(a)-Color.red(b)),
                        Math.max(Math.abs(Color.green(a)-Color.green(b)),Math.abs(Color.blue(a)-Color.blue(b))));
                max=Math.max(max,error);total+=error;
                if(y<250&&Math.abs(Color.red(a)-(dark?0x27:0xc9))>2)changed++;
            }
            if((phase==1||phase==2)&&changed<100)throw new AssertionError("Inactive native reference");
            if(max>3||total/(float)(W*H)>.35f)throw new AssertionError("Upper-track mismatch: dark="+dark+" phase="+phase+" max="+max+" sum="+total);
            maxError=Math.max(maxError,max);cases++;
        }
        return "PASS "+cases+" upper-track GPU comparisons, maximum channel error "+maxError;
    }
    private static int[] capture(Instrumentation test,Activity host,Pair pair,boolean reference)throws Exception{
        CountDownLatch committed=new CountDownLatch(1);int[] loc=new int[2];
        test.runOnMainSync(()->{
            pair.reference=reference;pair.getLocationInWindow(loc);pair.getViewTreeObserver().registerFrameCommitCallback(committed::countDown);pair.invalidate();
        });
        if(!committed.await(3,TimeUnit.SECONDS))throw new AssertionError("No hardware frame");
        Bitmap pixels=Bitmap.createBitmap(W,H,Bitmap.Config.ARGB_8888);CountDownLatch copied=new CountDownLatch(1);int[] status={-1};
        PixelCopy.request(host.getWindow(),new Rect(loc[0],loc[1],loc[0]+W,loc[1]+H),pixels,
                r->{status[0]=r;copied.countDown();},new Handler(Looper.getMainLooper()));
        if(!copied.await(3,TimeUnit.SECONDS)||status[0]!=PixelCopy.SUCCESS)throw new AssertionError("GPU readback failed "+status[0]);
        int[] rgb=new int[W*H];pixels.getPixels(rgb,0,W,0,0,W,H);pixels.recycle();return rgb;
    }
}
