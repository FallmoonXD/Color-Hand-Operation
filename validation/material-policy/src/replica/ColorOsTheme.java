package replica;
import android.content.Context;
import java.util.ArrayList;
public final class ColorOsTheme {
    private static final ArrayList<Subscription> subscriptions=new ArrayList<>();
    public static final class Subscription {private final Runnable action;Subscription(Runnable a){action=a;}public void close(){subscriptions.remove(this);}}
    public static Subscription observe(Context c,Runnable action){Subscription s=new Subscription(action);subscriptions.add(s);return s;}
    public static int live(){return subscriptions.size();}
    public static void emit(){for(Subscription s:new ArrayList<>(subscriptions))s.action.run();}
}
