package replica;

import android.view.View;
import java.lang.reflect.Constructor;
import java.lang.reflect.Method;

/** Outer shadow of a COUI toolbar button, separate from its inner optical rim. */
final class ToolbarShadow {
    private static final Geometry GEOMETRY=Geometry.load();
    private static final Caustic CAUSTIC=Caustic.load();
    private final View view;
    private Object oplusView;
    private boolean geometryFailed,causticFailed;

    ToolbarShadow(View view){this.view=view;}

    static void allowToolbarOverflow(View icon,View target,View bar){
        // Settings expands these two ancestors by 120 physical pixels. Hit bounds stay fixed.
        try{
            Class<?> type=Class.forName("com.oplus.view.OplusView");
            Method expand=type.getMethod("setClipExpandSize",View.class,int.class,int.class,int.class,int.class);
            expand.invoke(null,target,120,120,120,120);
            expand.invoke(null,bar,120,120,120,120);
            type.getMethod("setShadowClippingEnabled",View.class,boolean.class).invoke(null,icon,true);
        }catch(ReflectiveOperationException|RuntimeException|LinkageError unavailable){/* Standard view clipping applies. */}
    }

    void update(boolean stroke,boolean dark){
        if(stroke&&CAUSTIC!=null&&!causticFailed&&geometry(-160,0,800)){
            try{
                CAUSTIC.color.invoke(null,view,dark?0x33ffffff:0x2effffff);
                CAUSTIC.layout.invoke(null,view,0,0,0,px(4.9f));
                CAUSTIC.params.invoke(null,view,(float)px(9.23f),.6f,.6f,18f,.45f,.6f);
                elevation(16,30);
                return;
            }catch(ReflectiveOperationException|RuntimeException|LinkageError unavailable){causticFailed=true;}
        }
        clearCaustic();
        // COUI uses its level 3a outer shadow when the caustic effect is off.
        boolean nativeLight=geometry(dark?-240:-200,10000,2000);
        elevation(nativeLight?100:4,dark?68:34);
    }

    void clear(){clearCaustic();view.setElevation(0);}
    private void clearCaustic(){
        if(CAUSTIC!=null)try{CAUSTIC.color.invoke(null,view,0);}
        catch(ReflectiveOperationException|RuntimeException|LinkageError unavailable){causticFailed=true;}
    }
    private int px(float dp){return PanelStyle.dp(view.getContext(),dp);}
    private void elevation(int dp,int alpha){
        view.setOutlineAmbientShadowColor(alpha<<24);
        view.setOutlineSpotShadowColor(alpha<<24);
        view.setElevation(px(dp));
    }
    private boolean geometry(int y,int z,int radius){
        if(GEOMETRY==null||geometryFailed)return false;
        try{
            if(oplusView==null)oplusView=GEOMETRY.create.newInstance(view);
            GEOMETRY.light.invoke(oplusView,-1f,(float)px(y),(float)px(z),(float)px(radius),0f);
            return true;
        }catch(ReflectiveOperationException|RuntimeException|LinkageError unavailable){geometryFailed=true;return false;}
    }
    private static final class Geometry {
        final Constructor<?> create;final Method light;
        Geometry()throws ReflectiveOperationException{
            Class<?> type=Class.forName("com.oplus.view.OplusView");
            create=type.getConstructor(View.class);
            light=type.getMethod("setOverrideLightSourceGeometry",float.class,float.class,float.class,float.class,float.class);
        }
        static Geometry load(){try{return new Geometry();}catch(ReflectiveOperationException|RuntimeException|LinkageError unavailable){return null;}}
    }
    private static final class Caustic {
        final Method color,layout,params;
        Caustic()throws ReflectiveOperationException{
            Class<?> version=Class.forName("com.oplus.os.OplusBuild$VERSION");
            int major=version.getField("SDK_VERSION").getInt(null);
            if(major<40||(major==40&&version.getField("SDK_SUB_VERSION").getInt(null)<12))
                throw new NoSuchMethodException("Caustic shadows require Oplus SDK 40.12");
            Class<?> util=Class.forName("com.oplus.view.material.OplusMaterialUtil");
            color=util.getMethod("setOutlineCausticShadowColor",View.class,int.class);
            layout=util.getMethod("setOutlineCausticShadowLayout",View.class,int.class,int.class,int.class,int.class);
            params=util.getMethod("setOutlineCausticShadowParams",View.class,float.class,float.class,float.class,float.class,float.class,float.class);
        }
        static Caustic load(){try{return new Caustic();}catch(ReflectiveOperationException|RuntimeException|LinkageError unavailable){return null;}}
    }
}
