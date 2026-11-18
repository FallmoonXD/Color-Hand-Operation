package dev.edgeflow.lsp;

import android.os.*;
import java.lang.reflect.Method;
import java.util.IdentityHashMap;

/** Read-only ColorOS volume lifecycle. No vendor timing, state or touch handling is changed. */
final class NativeVolumeState {
    private static final Handler main=new Handler(Looper.getMainLooper());
    private static final IdentityHashMap<Object,ResultReceiver> listeners=new IdentityHashMap<>();
    private static Method showing,dismissing,interactor;
    private static java.lang.reflect.Field expanded;
    private static Object view;
    private static boolean available;
    private static long serial;
    static void install(ModuleEntry module,ClassLoader loader)throws ReflectiveOperationException{
        Class<?> type=Class.forName("com.oplus.systemui.volume.view.OplusVolumeDialogView",false,loader);
        Class<?> impl=Class.forName("com.oplus.systemui.volume.OplusVolumeDialogImpl",false,loader);
        showing=type.getMethod("isSliderShowing");dismissing=type.getMethod("isDismissing");interactor=type.getMethod("getVolumeInteractor");expanded=impl.getField("mExpanded");
        Method setter=null;for(Method method:type.getDeclaredMethods())if(method.getName().equals("setSliderState")&&method.getParameterCount()==1){setter=method;break;}
        if(setter==null)throw new NoSuchMethodException("ColorOS volume slider state");
        module.hook(setter).intercept(chain->{Object result=chain.proceed();observe(chain.getThisObject());return result;});
        module.hook(type.getMethod("expandPanel")).intercept(chain->{Object result=chain.proceed();observe(chain.getThisObject());return result;});
        available=true;
    }
    private static void observe(Object source){main.post(()->{view=source;serial++;Bundle state=snapshot();for(ResultReceiver receiver:listeners.values())receiver.send(0,state);});}
    private static Bundle snapshot(){
        Bundle data=new Bundle();data.putBoolean("available",available);data.putLong("sequence",serial);data.putLong("time",SystemClock.uptimeMillis());
        if(view!=null)try{data.putBoolean("showing",(Boolean)showing.invoke(view));data.putBoolean("dismissing",(Boolean)dismissing.invoke(view));data.putBoolean("expanded",expanded.getBoolean(interactor.invoke(view)));}
        catch(ReflectiveOperationException|RuntimeException failure){data.putBoolean("available",false);}
        return data;
    }
    static void watch(Object owner,ResultReceiver callback,java.util.function.BooleanSupplier active){main.post(()->{if(!active.getAsBoolean())return;if(callback==null)listeners.remove(owner);else{listeners.put(owner,callback);callback.send(0,snapshot());}});}
    static void remove(Object owner){main.post(()->listeners.remove(owner));}
    private NativeVolumeState(){}
}
