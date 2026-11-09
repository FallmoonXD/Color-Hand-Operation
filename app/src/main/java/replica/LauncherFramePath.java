package replica;

import android.content.Context;
import android.graphics.Path;
import android.graphics.RectF;
import java.lang.reflect.Method;

/** The OplusPath backends used by Launcher3 SmoothRoundedManager for workspace outlines. */
final class LauncherFramePath {
    final Path path=new Path();
    private final RectF last=new RectF();
    private float lastRadius=-1;
    private int lastX,lastY,radiusType;
    private Object smooth,classic;
    private Method addSmooth,addClassic;

    LauncherFramePath(Context c){
        try{
            Class<?> old=Class.forName("com.oplus.graphics.OplusPath");
            classic=old.getConstructor(Path.class).newInstance(path);
            addClassic=method(old);
            Class<?> version=Class.forName("com.oplus.os.OplusBuild$VERSION");
            if(version.getField("SDK_VERSION").getInt(null)>=38&&Boolean.TRUE.equals(
                    Class.forName("com.oplus.view.OplusSmoothRoundedManager").getMethod("isSmoothRadiusOn").invoke(null))){
                Class<?> api=Class.forName("com.oplus.graphics.OplusPathAdapter");
                smooth=api.getConstructor(Path.class,int.class).newInstance(path,1);addSmooth=method(api);
            }
        }catch(ReflectiveOperationException|RuntimeException|LinkageError unavailable){/* Public Path fallback. */}
        try{
            Object config=c.getResources().getConfiguration();
            Object extra=Class.forName("android.content.res.OplusBaseConfiguration").getField("mOplusExtraConfiguration").get(config);
            long value=extra.getClass().getField("mUxIconConfig").getLong(extra);
            int theme=(int)(value>>>4)&15,shape=(int)(value>>>12)&15,radius=(int)(value>>>48)&4095;
            if((extra.getClass().getField("mThemeChangedFlags").getLong(extra)&1)==0&&value!=-1&&shape==0&&
                    (theme==2||theme==3||theme==6||theme==7||theme==8||theme==9))
                radiusType=(radius&3840)==256?1:(radius&255)>=75?2:0;
        }catch(ReflectiveOperationException|RuntimeException|LinkageError unavailable){/* Default workspace shape. */}
    }
    private static Method method(Class<?> type)throws NoSuchMethodException{
        return type.getMethod("addSmoothRoundRect",RectF.class,float.class,float.class,float.class,Path.Direction.class);
    }
    Path outline(RectF bounds,float radius,int x,int y){
        if(last.equals(bounds)&&radius==lastRadius&&x==lastX&&y==lastY)return path;
        last.set(bounds);lastRadius=radius;lastX=x;lastY=y;path.rewind();
        boolean single=x==1&&y==1,pair=(x==2&&y==1)||(x==1&&y==2);
        boolean useSmooth=smooth!=null&&!(radiusType==1&&single);
        float weight=useSmooth?(radiusType==2&&(single||pair)?1.1f:3f):radiusType==1&&single?.99f:1.1f;
        try{
            if(useSmooth){addSmooth.invoke(smooth,bounds,radius,radius,weight,Path.Direction.CCW);return path;}
            if(classic!=null){addClassic.invoke(classic,bounds,radius,radius,weight,Path.Direction.CCW);return path;}
        }catch(ReflectiveOperationException|RuntimeException unavailable){smooth=classic=null;path.rewind();}
        path.addRoundRect(bounds,radius,radius,Path.Direction.CCW);return path;
    }
}
