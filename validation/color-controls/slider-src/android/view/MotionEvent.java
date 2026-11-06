package android.view;
public final class MotionEvent {
    public static final int ACTION_DOWN=0,ACTION_UP=1,ACTION_MOVE=2,ACTION_CANCEL=3;
    private final int action;
    private MotionEvent(int action){this.action=action;}
    public static MotionEvent obtain(long down,long now,int action,float x,float y,int flags){return new MotionEvent(action);}
    public int getActionMasked(){return action;}
    public void recycle(){}
}
