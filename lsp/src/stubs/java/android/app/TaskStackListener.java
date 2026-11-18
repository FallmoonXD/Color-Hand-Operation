package android.app;

/** Compile-only signature; Android supplies the Binder-backed implementation. */
public abstract class TaskStackListener {
    public TaskStackListener(){}
    public void onTaskStackChanged(){}
    public void onTaskMovedToFront(ActivityManager.RunningTaskInfo task){}
}
