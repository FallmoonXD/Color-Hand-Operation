package dev.edgeflow.lsp;

import android.content.Context;
import android.os.Handler;
import android.os.ResultReceiver;

/** Window lifetime follows the enabled backend, rather than one display-sized input monitor. */
final class ToolRuntime {
    private final Context context;private final Handler main;
    private final java.util.function.BooleanSupplier allowed;
    private ToolHostConnection connection;
    ToolRuntime(Context context,Handler main,java.util.function.BooleanSupplier allowed){this.context=context;this.main=main;this.allowed=allowed;}
    void prepare(){
        if(!allowed.getAsBoolean())return;
        if(connection==null||connection.isClosed()){connection=new ToolHostConnection(context,main,null,null,null,allowed);connection.start();}
    }
    void show(String action,float[] anchor,ResultReceiver completion){
        if(connection==null||connection.isClosed()){connection=new ToolHostConnection(context,main,action,anchor,completion,allowed);connection.start();}
        else connection.show(action,anchor,completion);
    }
    void motion(int action,float x,float y){if(connection!=null)connection.motion(action,x,y);}
    void geometryChanging(){if(connection!=null)connection.geometryChanging();}
    void geometryReady(){if(connection!=null&&!connection.isClosed())connection.geometryReady();}
    void close(){if(connection!=null){connection.close();connection=null;}}
}
