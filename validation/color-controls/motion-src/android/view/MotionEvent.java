package android.view;
public final class MotionEvent {
    public static final int ACTION_DOWN=0,ACTION_UP=1,ACTION_MOVE=2,ACTION_CANCEL=3,ACTION_POINTER_DOWN=5;
    private final int action;private final float x,y;
    public MotionEvent(int action,float x,float y){this.action=action;this.x=x;this.y=y;}
    public int getActionMasked(){return action;}public int getPointerId(int index){return 0;}public int findPointerIndex(int pointer){return pointer==0?0:-1;}
    public float getX(){return x;}public float getY(){return y;}public float getX(int index){return x;}public float getY(int index){return y;}
}
