package replica;

import android.animation.TimeInterpolator;
import android.content.Context;
import android.graphics.Matrix;
import android.view.View;
import android.view.Window;
import android.view.animation.Animation;
import android.view.animation.AnimationUtils;
import android.view.animation.Transformation;
import java.lang.reflect.Field;

/** Test-only comparison between installed APKs in the fixture's isolated process. */
public final class NativeMotionCheck {
    private static int checks;
    private static void near(String label,double expected,double actual,double tolerance){
        if(!(Math.abs(expected-actual)<=tolerance))throw new AssertionError(label+": native "+expected+", CHO "+actual);
        checks++;
    }
    private static Animation animation(Context context,String name){
        int id=context.getResources().getIdentifier(name,"anim",context.getPackageName());
        if(id==0)throw new AssertionError("Missing native animation "+name);
        Animation a=AnimationUtils.loadAnimation(context,id);
        a.initialize(200,400,200,400);a.setStartTime(0);return a;
    }
    private static float[] sample(Animation animation,long at){
        Transformation tr=new Transformation();animation.getTransformation(at,tr);
        float[] m=new float[9];tr.getMatrix().getValues(m);
        return new float[]{tr.getAlpha(),m[Matrix.MSCALE_X],m[Matrix.MTRANS_X],m[Matrix.MTRANS_Y]};
    }
    private static void windows(Context settings,Context cho)throws Exception{
        android.view.Display display=cho.getSystemService(android.hardware.display.DisplayManager.class).getDisplay(0);
        Context ui=cho.createWindowContext(display,android.view.WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,null);
        Window window=new android.app.Dialog(ui,android.R.style.Theme_Material_Light_Dialog).getWindow();
        ColorMotion panel=new ColorMotion(window);
        View decor=window.getDecorView();decor.layout(0,0,200,400);
        java.lang.reflect.Method pose=ColorMotion.class.getDeclaredMethod("pose",float.class);pose.setAccessible(true);
        Field closing=ColorMotion.class.getDeclaredField("closing");closing.setAccessible(true);
        for(boolean entering:new boolean[]{true,false}){
            closing.setBoolean(panel,!entering);
            String suffix=entering?"enter":"exit";
            Animation center=animation(settings,"coui_center_dialog_"+suffix),sheet=animation(settings,"coui_bottom_dialog_"+suffix);
            long duration=entering?ColorMotion.CENTER_IN_MS:ColorMotion.CENTER_OUT_MS;
            near("center duration",center.computeDurationHint(),duration,0);
            near("sheet duration",sheet.computeDurationHint(),ColorMotion.SHEET_MS,0);
            for(int i=0;i<=10;i++){
                long ms=duration*i/10;
                float f=(entering?ColorMotion.ENTER:ColorMotion.EXIT).getInterpolation(ms/(float)duration);
                float[] nativeCenter=sample(center,ms);
                pose.invoke(panel,entering?f:1-f);
                near("center alpha",nativeCenter[0],window.getAttributes().alpha,.001);
                near("center X scale",nativeCenter[1],decor.getScaleX(),.001);
                near("center Y scale",nativeCenter[1],decor.getScaleY(),.001);
                near("center drift",0,decor.getTranslationY(),0);
                ms=ColorMotion.SHEET_MS*i/10;
                f=(entering?ColorMotion.ENTER:ColorMotion.EXIT).getInterpolation(ms/(float)ColorMotion.SHEET_MS);
                near("sheet translation",sample(sheet,ms)[3],400*(entering?1-f:f),.1);
            }
        }
        for(String direction:new String[]{"open","close"})for(String role:new String[]{"in","out"}){
            Animation ref=animation(settings,"coui_"+direction+"_slide_"+(role.equals("in")?"enter":"exit"));
            Animation local=animation(cho,"color_settings_"+direction+"_"+role);
            near("page duration",ref.computeDurationHint(),local.computeDurationHint(),0);
            near("internal page duration",ref.computeDurationHint(),ColorPageMotion.PAGE_MS,0);
            boolean open=direction.equals("open"),incoming=role.equals("in");
            Field travel=ColorPageMotion.class.getDeclaredField((open?"OPEN":"CLOSE")+(incoming?"_IN":"_OUT"));
            Field opacity=ColorPageMotion.class.getDeclaredField("OPACITY");travel.setAccessible(true);opacity.setAccessible(true);
            TimeInterpolator translate=(TimeInterpolator)travel.get(null),fade=(TimeInterpolator)opacity.get(null);
            for(int ms=0;ms<=350;ms+=35){
                float[] a=sample(ref,ms),b=sample(local,ms);
                for(int k=0;k<a.length;k++)near("page "+direction+" "+role,a[k],b[k],.005);
                float t=ms/(float)ColorPageMotion.PAGE_MS,x=translate.getInterpolation(t),f=fade.getInterpolation(t);
                float dx=open?(incoming?200*(1-x):-60*x):(incoming?-60*(1-x):200*x);
                near("internal page position",a[2],dx,.005);
                near("internal page opacity",a[0],open?(incoming?1:1-.5f*f):(incoming?.5f+.5f*f:1),.001);
            }
        }
    }
    private static Object field(Object obj,String name)throws Exception{
        Field f=obj.getClass().getDeclaredField(name);f.setAccessible(true);return f.get(obj);
    }
    private static void choice(Context settings,boolean radio,boolean on,String channel)throws Exception{
        boolean fill=channel.equals("fill"),pulse=channel.endsWith("pulse"),tick=channel.equals("tick-pulse");
        String name=radio?(on?"coui_btn_radio_off_to_on_animation":"coui_btn_radio_on_to_off_animation"):
                (on?"coui_checkbox_unselected_to_selected":"coui_checkbox_selected_to_unselected");
        int index=radio?(pulse?1:fill?(on?9:14):16):
                (pulse?(tick?(on?15:16):(on?1:4)):fill?(on?11:1):(on?13:14));
        int id=settings.getResources().getIdentifier("$"+name+"__"+index,"drawable",settings.getPackageName());
        // These animator resources are embedded inside the installed animated vector.
        try(android.content.res.XmlResourceParser xml=settings.getResources().getXml(id)){
            String ns="http://schemas.android.com/apk/res/android";
            String property=pulse?"scaleX":fill?"fillAlpha":radio?"scaleX":"trimPathEnd";
            int compared=0;
            while(xml.next()!=org.xmlpull.v1.XmlPullParser.END_DOCUMENT){
                if(xml.getEventType()!=org.xmlpull.v1.XmlPullParser.START_TAG||!property.equals(xml.getAttributeValue(ns,"propertyName")))continue;
                int offset=xml.getAttributeIntValue(ns,"startOffset",0),duration=xml.getAttributeIntValue(ns,"duration",0);
                TimeInterpolator ease=AnimationUtils.loadInterpolator(settings,xml.getAttributeResourceValue(ns,"interpolator",0));
                float from=Float.parseFloat(xml.getAttributeValue(ns,"valueFrom")),to=Float.parseFloat(xml.getAttributeValue(ns,"valueTo"));
                for(int ms=offset;ms<=offset+duration;ms+=5){
                    float f=ColorChoiceDrawable.choiceProgress(radio,on,fill,ms);
                    float actual=pulse?ColorChoiceDrawable.pulse(radio,on,tick,ms,1):on?f:1-f;
                    near("choice "+name+" "+property,from+(to-from)*ease.getInterpolation((ms-offset)/(float)duration),actual,.002);
                    compared++;
                }
            }
            if(compared==0)throw new AssertionError("No native frames for "+name+" "+property);
        }
    }
    private static void scroll(Context settings)throws Exception{
        ClassLoader l=settings.getClassLoader();
        Class<?> util=l.loadClass("com.coui.appcompat.animation.COUIPhysicalAnimationUtil");
        for(int offset:new int[]{0,25,60,130})for(int delta:new int[]{-30,45,100})
            near("edge drag",((Number)util.getMethod("calcRealOverScrollDist",int.class,int.class,int.class).invoke(null,delta,offset,120)).intValue(),
                    ColorScrollPhysics.resisted(delta,offset,120),1);
        Class<?> rebound=l.loadClass("com.coui.appcompat.scroll.SpringOverScroller$ReboundOverScroller");
        Field friction=rebound.getDeclaredField("SPRING_BACK_FRICTION"),tension=rebound.getDeclaredField("SPRING_BACK_TENSION");
        Field multiplier=l.loadClass("com.coui.appcompat.scrollview.COUINestedScrollView").getDeclaredField("VERTICAL_SPRING_BACK_TENSION_MULTIPLE");
        friction.setAccessible(true);tension.setAccessible(true);multiplier.setAccessible(true);
        java.lang.reflect.Constructor<?> constructor=l.loadClass(rebound.getName()+"$ReboundConfig").getDeclaredConstructor(double.class,double.class);
        constructor.setAccessible(true);
        Object config=constructor.newInstance(friction.getFloat(null),(double)tension.getFloat(null)*multiplier.getFloat(null));
        near("scroll damping",((Number)field(config,"mFriction")).doubleValue(),ColorScrollPhysics.DAMPING,.001);
        near("scroll stiffness",((Number)field(config,"mTension")).doubleValue(),ColorScrollPhysics.STIFFNESS,.001);
        for(long nanos:new long[]{8_333_333,11_111_111,16_666_667}){
            java.lang.reflect.Constructor<?> c=rebound.getDeclaredConstructor();c.setAccessible(true);
            Object nativeEdge=c.newInstance();
            Field multiple=rebound.getDeclaredField("mSpringBackTensionMultiple");multiple.setAccessible(true);
            multiple.setFloat(nativeEdge,multiplier.getFloat(null));
            java.lang.reflect.Method start=rebound.getDeclaredMethod("springBack",int.class,int.class,int.class,boolean.class);
            java.lang.reflect.Method update=rebound.getDeclaredMethod("update");
            java.lang.reflect.Method position=rebound.getDeclaredMethod("getCurrentValue");
            start.setAccessible(true);update.setAccessible(true);position.setAccessible(true);
            start.invoke(nativeEdge,80,0,0,false);
            Field updated=rebound.getDeclaredField("mComputeTimeFromCallbackUpdated"),last=rebound.getDeclaredField("mLastComputeTimeFromCallback"),now=rebound.getDeclaredField("mCurrentComputeTimeFromCallback");
            updated.setAccessible(true);last.setAccessible(true);now.setAccessible(true);
            ColorScrollPhysics ours=new ColorScrollPhysics();ours.position=80;
            int stock10=0,cho10=0;
            for(int frame=1;frame<=150;frame++){
                updated.setBoolean(nativeEdge,true);last.setLong(nativeEdge,(frame-1)*nanos);now.setLong(nativeEdge,frame*nanos);
                update.invoke(nativeEdge);ours.step(nanos/1e9);
                double nativePosition=((Number)position.invoke(nativeEdge)).doubleValue();
                near("rebound position at frame "+frame,nativePosition,ours.position,.01);
                if(stock10==0&&nativePosition<=8)stock10=frame;
                if(cho10==0&&ours.position<=8)cho10=frame;
                if(stock10!=0&&cho10!=0)break;
            }
            if(stock10==0||cho10==0)throw new AssertionError("Scroll did not return");
            near("scroll 10% return at "+nanos+" ns",stock10,cho10,1);
        }
    }
    private static void spring(ClassLoader loader,ColorSpringTrack track,float start,float target,float response,float bounce)throws Exception{
        Class<?> type=loader.loadClass("com.coui.appcompat.animation.dynamicanimation.COUISpringForce");
        Object force=type.getConstructor().newInstance();
        type.getMethod("setFinalPosition",float.class).invoke(force,target);
        type.getMethod("setResponse",float.class).invoke(force,response);
        type.getMethod("setBounce",float.class).invoke(force,bounce);
        for(int ms=0;ms<track.durationMs;ms+=11){
            Object state=type.getMethod("updateValues",double.class,double.class,long.class).invoke(force,(double)start,0d,(long)ms);
            near("physical spring",((Number)field(state,"mValue")).doubleValue(),track.valueAt(ms),.001);
        }
        near("spring endpoint",target,track.valueAt(track.durationMs),0);
    }
    private static void controls(Context settings,Context systemui)throws Exception{
        ClassLoader l=settings.getClassLoader();
        Class<?> defs=l.loadClass("com.coui.appcompat.anim.param.PopupGravityMotionParamsExtKt");
        Field defaults=defs.getDeclaredField("popupGravityMotionParamsDefault");defaults.setAccessible(true);
        Object params=defaults.get(null);
        for(boolean in:new boolean[]{true,false})for(boolean scale:new boolean[]{true,false}){
            Object data=params.getClass().getMethod("get"+(in?"Open":"Close")+(scale?"Scale":"Opacity")+"Spring").invoke(params);
            float response=(Float)data.getClass().getMethod("getResponse").invoke(data);
            float bounce=(Float)data.getClass().getMethod("getBounce").invoke(data);
            float start=in?0:1,target=1-start;
            spring(l,scale?ColorSpringTrack.popupScale(start,in):ColorSpringTrack.popupOpacity(start,in),start,target,response,bounce);
        }
        Class<?> connector=systemui.getClassLoader().loadClass("com.oplusos.systemui.common.olivelink.OliveLinkConnector");
        Object edge=null;
        for(java.lang.reflect.Method m:connector.getDeclaredMethods())if(m.getName().equals("safeSpring$default"))
            edge=m.invoke(null,connector.getField("INSTANCE").get(null),"nav_bar","侧滑手势横向返回Spring参数",0f,.1f,0f,0f,0f,null,null,null,1008);
        if(edge==null)throw new AssertionError("Missing SystemUI edge spring");
        float response=(Float)edge.getClass().getMethod("response").invoke(edge),bounce=(Float)edge.getClass().getMethod("bounce").invoke(edge);
        spring(systemui.getClassLoader(),ColorSpringTrack.backArrow(80),80,0,response,bounce);
        Class<?> v=systemui.getClassLoader().loadClass("com.oplus.systemui.volume.utils.VolumeAnimUtil");
        near("volume enter duration",v.getField("DURATION_ONE_ROW_SHOW").getLong(null),ColorMotion.VOLUME_IN_MS,0);
        near("volume exit duration",v.getField("DURATION_ROW_HIDE").getLong(null),ColorMotion.VOLUME_OUT_MS,0);
        if(!ColorMotion.simpleVolume(-1,4)||ColorMotion.simpleVolume(1,4)||ColorMotion.simpleVolume(-1,1))
            throw new AssertionError("SystemUI animation level override/fallback");
        for(boolean in:new boolean[]{true,false}){
            TimeInterpolator ref=(TimeInterpolator)v.getField(in?"VOLUME_SHOW_INTERPOLATOR":"VOLUME_HIDE_INTERPOLATOR").get(null);
            for(int i=0;i<=100;i++)near("volume interpolator",ref.getInterpolation(i/100f),(in?ColorMotion.VOLUME_IN:ColorMotion.VOLUME_OUT).getInterpolation(i/100f),.0001);
        }
        Class<?> expand=l.loadClass("com.coui.appcompat.expandable.ExpandableRecyclerConnector");
        Field duration=expand.getDeclaredField("ANIMATION_DURATION");duration.setAccessible(true);
        near("expand duration",duration.getLong(null),ColorMotion.EXPAND_MS,0);
        TimeInterpolator move=(TimeInterpolator)l.loadClass("com.coui.appcompat.animation.COUIMoveEaseInterpolator").getConstructor().newInstance();
        TimeInterpolator fade=(TimeInterpolator)systemui.getClassLoader().loadClass("com.android.app.animation.Interpolators").getField("ALPHA_IN").get(null);
        String one="com.android.wm.shell.onehanded.OneHandedAnimationController";
        TimeInterpolator reach=(TimeInterpolator)systemui.getClassLoader().loadClass(one+"$OneHandedInterpolator").getDeclaredConstructor(systemui.getClassLoader().loadClass(one)).newInstance(new Object[]{null});
        int id=systemui.getResources().getIdentifier("config_one_handed_translate_animation_duration","integer",systemui.getPackageName());
        int expected=(int)Class.forName("android.os.SystemProperties").getMethod("getInt",String.class,int.class).invoke(null,"persist.debug.one_handed_translate_animation_duration",systemui.getResources().getInteger(id));
        near("screen return duration",expected,ColorMotion.oneHandedDuration(settings),0);
        for(int i=0;i<=100;i++){
            float t=i/100f;
            near("expand curve",move.getInterpolation(t),ColorMotion.ENTER.getInterpolation(t),.001);
            near("material fade",fade.getInterpolation(t),ColorMotion.MATERIAL_IN.getInterpolation(t),.001);
            near("screen return",reach.getInterpolation(t),ColorMotion.oneHandedReturn(t),.0001);
        }
        near("SystemUI CrossFadeHelper duration",210,ColorMotion.MATERIAL_MS,0);
    }
    private static void toolMotions(Context context,Context systemui)throws Exception{
        Class<?> volume=systemui.getClassLoader().loadClass("com.oplus.systemui.volume.utils.VolumeAnimUtil");
        near("expanded volume enter",volume.getField("DURATION_MULT_ROWS_SHOW").getLong(null),ColorMotion.MULTI_VOLUME_IN_MS,0);
        ClassLoader sidebar=context.createPackageContext("com.coloros.smartsidebar",Context.CONTEXT_INCLUDE_CODE|Context.CONTEXT_IGNORE_SECURITY).getClassLoader();
        Class<?> force=sidebar.loadClass("t3.g");
        for(boolean enter:new boolean[]{true,false})for(int channel=0;channel<3;channel++){
            float response=channel==0?(enter?.6f:.4f):enter?(channel==1?.4f:.5f):.45f;
            float bounce=enter?(channel==0?.3f:channel==1?.35f:.25f):.15f;
            int start=enter?12:320,end=enter?320:12;
            ColorSpringTrack ours=ColorSpringTrack.sidebar(start,end,enter,channel==0,channel==1);
            Object stock=force.getConstructor(float.class).newInstance((float)end);
            force.getMethod("l",float.class).invoke(stock,response);
            force.getMethod("i",float.class).invoke(stock,bounce);
            for(int ms=0;ms<ours.durationMs;ms+=13){
                Object state=force.getMethod("q",double.class,double.class,long.class).invoke(stock,(double)start,0d,(long)ms);
                near("sidebar spring "+channel,((Number)field(state,"a")).doubleValue(),ours.valueAt(ms),.003);
            }
        }
        View sample=new View(context);sample.layout(0,0,200,160);
        for(boolean left:new boolean[]{true,false})for(ToolAnimation.Style entry:ToolAnimation.choices(true)){
            if(entry==ToolAnimation.Style.DEFAULT)continue;
            for(ToolAnimation.Style exit:ToolAnimation.choices(false)){
                if(exit==ToolAnimation.Style.DEFAULT)continue;
                ToolMotionPlayer player=new ToolMotionPlayer(sample,left,Float.NaN,true,sample::setAlpha);
                player.prepare();player.play(entry,true,null);
                android.animation.ValueAnimator opening=(android.animation.ValueAnimator)field(player,"animator");
                if(opening!=null)opening.setCurrentPlayTime(opening.getDuration()/3);
                float x=sample.getTranslationX()+(1-sample.getScaleX())*sample.getPivotX();
                float y=sample.getTranslationY()+(1-sample.getScaleY())*sample.getPivotY();
                float alpha=sample.getAlpha();
                int[] ends={0};player.play(exit,false,()->ends[0]++);
                android.animation.ValueAnimator closing=(android.animation.ValueAnimator)field(player,"animator");
                if(closing!=null){
                    closing.setCurrentPlayTime(0);
                    near("mixed exit X",x,sample.getTranslationX()+(1-sample.getScaleX())*sample.getPivotX(),.01);
                    near("mixed exit Y",y,sample.getTranslationY()+(1-sample.getScaleY())*sample.getPivotY(),.01);
                    near("mixed exit alpha",alpha,sample.getAlpha(),.001);
                    closing.end();
                }
                near("exactly one removal",1,ends[0],0);player.cancel();
            }
        }
    }
    public static int run(Context system)throws Exception{
        int flags=Context.CONTEXT_INCLUDE_CODE|Context.CONTEXT_IGNORE_SECURITY;
        Context settings=system.createPackageContext("com.android.settings",flags);
        Context systemui=system.createPackageContext("com.android.systemui",flags);
        windows(settings,system.createPackageContext("dev.edgeflow",0));controls(settings,systemui);scroll(settings);toolMotions(system,systemui);
        for(boolean radio:new boolean[]{true,false})for(boolean on:new boolean[]{true,false})
            for(String channel:new String[]{"fill","mark","pulse","tick-pulse"})
                if(!(radio&&channel.equals("tick-pulse")))choice(settings,radio,on,channel);
        return checks;
    }
    public static void interactions(android.app.Instrumentation test,android.app.Activity host)throws Exception{
        ColorSwitch[] toggle=new ColorSwitch[1];ColorCheckBox[] check=new ColorCheckBox[1];ColorRadioButton[] radio=new ColorRadioButton[1];ColorSlider[] slider=new ColorSlider[1];
        java.util.concurrent.atomic.AtomicInteger toggles=new java.util.concurrent.atomic.AtomicInteger();
        test.runOnMainSync(()->{
            android.widget.LinearLayout box=new android.widget.LinearLayout(host);box.setOrientation(1);
            toggle[0]=new ColorSwitch(host);check[0]=new ColorCheckBox(host);radio[0]=new ColorRadioButton(host);
            slider[0]=new ColorSlider(host);
            box.addView(toggle[0]);box.addView(check[0]);box.addView(radio[0]);box.addView(slider[0]);host.setContentView(box);
            toggle[0].setOnCheckedChangeListener((v,value)->toggles.incrementAndGet());
        });
        test.waitForIdleSync();
        test.runOnMainSync(()->{toggle[0].performClick();toggle[0].setChecked(true);check[0].performClick();radio[0].performClick();});
        ClassLoader settings=host.createPackageContext("com.android.settings",Context.CONTEXT_INCLUDE_CODE|Context.CONTEXT_IGNORE_SECURITY).getClassLoader();
        Class<?> switchDefaults=settings.loadClass("com.coui.appcompat.couiswitch.olivelink.COUISwitchParamsExtKt");
        Field defaults=switchDefaults.getDeclaredField("cOUISwitchParamsDefault");defaults.setAccessible(true);
        Object params=defaults.get(null),spring=params.getClass().getMethod("getToggleSpring").invoke(params);
        verifyControl(toggle[0],"position",(Float)spring.getClass().getMethod("getResponse").invoke(spring),(Float)spring.getClass().getMethod("getBounce").invoke(spring));
        test.runOnMainSync(()->{touch(toggle[0],0);touch(slider[0],0);touch(slider[0],2);});
        verifyControl(toggle[0],"press",(Float)params.getClass().getMethod("getResponseDrag").invoke(params),(Float)params.getClass().getMethod("getBounceDrag").invoke(params));
        verifyControl(slider[0],"enlarge",.5f,0f);verifyControl(slider[0],"thumbEnlarge",.5f,.3f);
        test.runOnMainSync(()->{touch(toggle[0],3);touch(slider[0],3);});
        verifyControl(toggle[0],"position",(Float)params.getClass().getMethod("getResponseAttach").invoke(params),(Float)params.getClass().getMethod("getBounceAttach").invoke(params));
        verifyControl(slider[0],"thumbEnlarge",.5f,0f);
        android.os.SystemClock.sleep(55);
        test.runOnMainSync(()->{check[0].performClick();radio[0].setChecked(false);toggle[0].performClick();});
        android.os.SystemClock.sleep(350);
        test.runOnMainSync(()->{check[0].jumpDrawablesToCurrentState();radio[0].jumpDrawablesToCurrentState();});
        if(toggles.get()!=2||toggle[0].isChecked()||check[0].isChecked()||radio[0].isChecked())
            throw new AssertionError("Input reversal did not settle to its selected state");
        java.util.concurrent.atomic.AtomicInteger calls=new java.util.concurrent.atomic.AtomicInteger();
        java.util.concurrent.CountDownLatch removed=new java.util.concurrent.CountDownLatch(1);
        android.app.Dialog[] dialog=new android.app.Dialog[1];ColorMotion[] motion=new ColorMotion[1];
        test.runOnMainSync(()->{
            dialog[0]=new android.app.Dialog(host);dialog[0].setContentView(new android.widget.TextView(host));dialog[0].show();
            motion[0]=new ColorMotion(dialog[0].getWindow());motion[0].enter();
        });
        android.os.SystemClock.sleep(65);
        test.runOnMainSync(()->{
            motion[0].exit(()->{calls.incrementAndGet();dialog[0].dismiss();removed.countDown();});
            motion[0].exit(calls::incrementAndGet);
        });
        if(!removed.await(2,java.util.concurrent.TimeUnit.SECONDS)||calls.get()!=1||dialog[0].isShowing())
            throw new AssertionError("Interrupted entrance did not remove its window exactly once");
        // A late material frame may arrive after the fallback is visible. Its
        // animator must stop writing when the capture is cleared or detached.
        java.lang.reflect.Method fade=NativeMaterial.class.getDeclaredMethod("startMaterialFade"),clear=NativeMaterial.class.getDeclaredMethod("clearFrame");
        fade.setAccessible(true);clear.setAccessible(true);
        NativeMaterial[] material=new NativeMaterial[1];
        java.util.concurrent.atomic.AtomicReference<Throwable> failure=new java.util.concurrent.atomic.AtomicReference<>();
        test.runOnMainSync(()->{
            try{
                material[0]=(NativeMaterial)field(new ColorOsSurface(host,24,2),"nativeMaterial");
                fade.invoke(material[0]);clear.invoke(material[0]);
            }catch(Throwable error){failure.set(error);}
        });
        if(failure.get()!=null)throw new AssertionError(failure.get());
        android.os.SystemClock.sleep(ColorMotion.MATERIAL_MS+40);
        if(field(material[0],"materialFade")!=null||((Number)field(material[0],"mix")).floatValue()!=1)
            throw new AssertionError("A detached material received stale fade updates");
        glideTargets(test,host);
    }
    private static void glideTargets(android.app.Instrumentation test,android.app.Activity host)throws Exception{
        java.util.concurrent.atomic.AtomicReference<Throwable> failure=new java.util.concurrent.atomic.AtomicReference<>();
        test.runOnMainSync(()->{
            android.app.Dialog window=new android.app.Dialog(host);
            try{
                android.widget.FrameLayout grid=new android.widget.FrameLayout(host);
                View tile=new View(host);android.widget.FrameLayout.LayoutParams item=new android.widget.FrameLayout.LayoutParams(90,65);
                item.leftMargin=80;item.topMargin=45;grid.addView(tile,item);window.setContentView(grid);window.show();
                View decor=window.getWindow().getDecorView();decor.layout(0,0,320,220);grid.layout(0,0,320,220);tile.layout(80,45,170,110);
                Class<?> type=Class.forName("dev.edgeflow.QuickLauncherOverlay$TileHitTest");
                java.lang.reflect.Constructor<?> create=type.getDeclaredConstructor();create.setAccessible(true);Object hit=create.newInstance();
                java.lang.reflect.Method contains=type.getDeclaredMethod("contains",View.class,View.class,float.class,float.class);contains.setAccessible(true);
                for(boolean left:new boolean[]{true,false}){
                    decor.setPivotX(left?0:320);decor.setPivotY(100);decor.setScaleX(.5f);decor.setScaleY(.65f);decor.setTranslationX(left?-12:12);
                    int[] origin=new int[2];tile.getLocationOnScreen(origin);
                    if(!(Boolean)contains.invoke(hit,decor,tile,origin[0]+22f,origin[1]+20f)
                            ||(Boolean)contains.invoke(hit,decor,tile,origin[0]+65f,origin[1]+20f))
                        throw new AssertionError("Glide hit no longer follows the displayed tile");
                }
            }catch(Throwable error){failure.set(error);}
            finally{window.dismiss();}
        });
        if(failure.get()!=null)throw new AssertionError(failure.get());
    }
    private static void touch(View v,int action){
        long now=android.os.SystemClock.uptimeMillis();
        android.view.MotionEvent e=android.view.MotionEvent.obtain(now,now,action,v.getWidth()*(action==2?.8f:.3f),v.getHeight()/2f,0);
        try{v.dispatchTouchEvent(e);}finally{e.recycle();}
    }
    private static void verifyControl(Object view,String channel,float response,float bounce)throws Exception{
        Object actual=field(view,channel);
        near(channel+" response",response,((Number)field(actual,"response")).doubleValue(),.0001);
        near(channel+" bounce",bounce,((Number)field(actual,"bounce")).doubleValue(),.0001);
    }
}
