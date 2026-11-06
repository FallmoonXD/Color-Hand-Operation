package replica;

/** Small command-line helper; called only for the explicitly mapped system action. */
public class OplusBridge {
    public static void main(String[] args) throws Exception {
        if(args.length>0 && args[0].equals("ringer")){
            Object binder=Class.forName("android.os.ServiceManager").getMethod("getService",String.class).invoke(null,"audio");
            Class<?> api=Class.forName("android.media.IAudioService");
            Object service=Class.forName("android.media.IAudioService$Stub").getMethod("asInterface",android.os.IBinder.class).invoke(null,binder);
            api.getMethod("setRingerModeInternal",int.class,String.class).invoke(service,Integer.parseInt(args[1]),"AlertSliderManager");
            return;
        }
        Class<?> cls=Class.forName("android.app.OplusStatusBarManager");
        if(args.length==0 || args[0].equals("inspect")) {
            for(java.lang.reflect.Method method:cls.getMethods())
                if(method.getName().toLowerCase().matches(".*(onehand|edge|sidebar).*"))System.out.println(method);
            return;
        }
        if(!args[0].equals("onehand")) throw new IllegalArgumentException("Unsupported action");
        Object manager=cls.getConstructor().newInstance();
        cls.getMethod("controlOneHandedMode",int.class,String.class)
            .invoke(manager,Integer.parseInt(args[1]),"dev.edgeflow");
        System.out.println("Requested native onehand mode="+args[1]);
    }
}
