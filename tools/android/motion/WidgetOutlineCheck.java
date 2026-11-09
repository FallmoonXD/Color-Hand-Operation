package replica;

import android.content.Context;
import android.graphics.Path;
import android.graphics.RectF;
import java.lang.reflect.Method;

/** Compare the selected-widget contour to the installed launcher's own path builder. */
public final class WidgetOutlineCheck {
    public static String run(Context context)throws Exception{
        Context launcher=context.createPackageContext("com.android.launcher",Context.CONTEXT_INCLUDE_CODE|Context.CONTEXT_IGNORE_SECURITY);
        Class<?> manager=launcher.getClassLoader().loadClass("com.android.launcher3.util.SmoothRoundedManager");
        boolean smooth=(Boolean)manager.getMethod("useSmoothCornerV2").invoke(null);
        Method draw=manager.getMethod(smooth?"createSmoothPath":"createNormalPath",Path.class,RectF.class,
                float.class,float.class,float.class,Path.Direction.class);
        LauncherFramePath app=new LauncherFramePath(context);int cases=0;
        for(int[] span:new int[][]{{4,1},{2,2},{4,2}}){
            RectF rect=new RectF(5,5,span[0]*115-5,span[1]*122-5);
            float radius=span[1]==1?56:63;
            Path ref=new Path();draw.invoke(null,ref,rect,radius,radius,smooth?3f:1.1f,Path.Direction.CCW);
            float[] expected=ref.approximate(.25f),actual=app.outline(rect,radius,span[0],span[1]).approximate(.25f);
            if(expected.length!=actual.length)throw new AssertionError("Different launcher outline segments "+expected.length+"/"+actual.length);
            for(int i=0;i<actual.length;i++)if(Math.abs(actual[i]-expected[i])>.02f)throw new AssertionError("Different widget corner at "+i);
            cases++;
        }
        return "PASS "+cases+" launcher selection path comparisons (smooth v2="+smooth+")";
    }
}
