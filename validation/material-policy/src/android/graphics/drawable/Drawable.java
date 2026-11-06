package android.graphics.drawable;
public class Drawable {
    public interface Callback {}
    private Callback callback;
    public Callback getCallback(){return callback;}
    public void setCallback(Callback callback){this.callback=callback;}
}
