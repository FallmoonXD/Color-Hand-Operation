package replica;
import android.graphics.drawable.Drawable;
import android.view.View;
public final class MaterialBindingCheck {
    private static int checks;
    private static void check(boolean ok,String text){checks++;if(!ok)throw new AssertionError(text);}
    private static final class Wrapper extends Drawable implements Drawable.Callback {}
    public static void main(String[] args){
        View first=new View();first.attach();Drawable a=new Drawable(),b=new Drawable();Wrapper wrapper=new Wrapper();wrapper.setCallback(first);a.setCallback(first);b.setCallback(wrapper);int[] changed=new int[4],detached=new int[4];
        MaterialThemeBinding one=new MaterialThemeBinding(a,()->changed[0]++,()->detached[0]++),two=new MaterialThemeBinding(b,()->changed[1]++,()->detached[1]++);one.track();two.track();
        check(first.listeners.size()==1&&ColorOsTheme.live()==1,"nested child surfaces share one host listener and subscription");ColorOsTheme.emit();check(changed[0]==1&&changed[1]==1,"drawable callback chains receive live policy changes");one.track();two.track();ColorOsTheme.emit();check(first.listeners.size()==1&&changed[0]==2&&changed[1]==2,"repeated draws do not accumulate subscriptions");
        first.detach();check(ColorOsTheme.live()==0&&detached[0]==1&&detached[1]==1,"detach releases subscription and notifies both cache owners");first.attach();check(ColorOsTheme.live()==1&&first.listeners.size()==1&&changed[0]==3&&changed[1]==3,"reattach refreshes current policy exactly once");
        a.setCallback(null);Drawable c=new Drawable();c.setCallback(first);MaterialThemeBinding three=new MaterialThemeBinding(c,()->changed[2]++,()->detached[2]++);three.track();ColorOsTheme.emit();check(changed[0]==3&&changed[1]==4&&changed[2]==1,"replaced background is removed from current host updates");
        a.setCallback(first);one.track();ColorOsTheme.emit();check(changed[0]==4&&first.listeners.size()==1&&ColorOsTheme.live()==1,"reusing a replaced drawable can bind to the same host again");
        a.setCallback(null);b.setCallback(null);c.setCallback(null);ColorOsTheme.emit();check(first.listeners.isEmpty()&&ColorOsTheme.live()==0,"empty host registry releases listener and subscription");a.setCallback(first);one.track();check(first.listeners.size()==1&&ColorOsTheme.live()==1,"same-host reuse after complete cleanup registers again");
        View second=new View();Drawable explicit=new Drawable();MaterialThemeBinding four=new MaterialThemeBinding(explicit,()->changed[3]++,()->detached[3]++);four.attach(second);check(second.listeners.size()==1&&ColorOsTheme.live()==1,"explicit pre-attach window does not subscribe early");second.attach();check(ColorOsTheme.live()==2&&changed[3]==1,"explicit attachment observes when the host attaches");explicit.setCallback(second);four.track();explicit.setCallback(null);ColorOsTheme.emit();check(second.listeners.isEmpty()&&ColorOsTheme.live()==1,"replaced explicit surface is released once its callback was established");
        a.setCallback(second);one.track();check(first.listeners.isEmpty()&&second.listeners.size()==1&&ColorOsTheme.live()==1,"moving drawable to another host leaves no old subscription");second.detach();check(ColorOsTheme.live()==0&&detached[0]==2,"last host detach leaves no active subscription");
        System.out.println("PASS "+checks+" material host lifecycle assertions");
    }
}
