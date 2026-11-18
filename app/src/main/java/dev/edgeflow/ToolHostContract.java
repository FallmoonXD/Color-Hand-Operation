package dev.edgeflow;

/** Framework types only; shared by the application host and the injected controller. */
public final class ToolHostContract {
    public static final String DESCRIPTOR="dev.edgeflow.tools.v3";
    public static final String CONTROL_DESCRIPTOR="dev.edgeflow.tools.control.v1";
    public static final String PACKAGE="dev.edgeflow",SERVICE="dev.edgeflow.LspToolHostService";
    public static final int SHOW=1,CLOSE=2,MOTION=3,GEOMETRY=4;
    public static final int GLOBAL_ACTION=1,FOREGROUND=2,TOUCH=3,CANCEL_TOUCH=4,VOLUME_OBSERVE=5;
    public static boolean supports(String action){
        return BackendActions.window(action)||BackendActions.command(action)||"edit_launcher".equals(action);
    }
    public static boolean isFolderAction(String action){return action!=null&&action.startsWith("folder:")&&action.length()>7;}
    private ToolHostContract(){}
}
