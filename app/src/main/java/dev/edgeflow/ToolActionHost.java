package dev.edgeflow;

import android.content.Context;
import android.content.ContextWrapper;

/** Capabilities supplied by a tool host, independent of its window rendering. */
public interface ToolActionHost {
    boolean globalAction(int action);
    String foregroundPackage();
    int handleClearance(boolean left);
    boolean touch(float[] points,long duration,int count,java.util.function.Consumer<Boolean> completion);
    void cancelTouch();
    static ToolActionHost find(Context context){
        for(int depth=0;context!=null&&depth<32;depth++){
            if(context instanceof ToolActionHost host)return host;
            if(!(context instanceof ContextWrapper wrapper))break;
            Context next=wrapper.getBaseContext();if(next==context)break;context=next;
        }
        return null;
    }
}
