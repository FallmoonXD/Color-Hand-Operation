package replica;

/** Process-local capability decision. A healthy idle exit does not spend recovery attempts. */
final class NativeBlurPolicy {
    static final int RECOVERY_LIMIT=2;
    private boolean connected,unavailable;
    private int recoveries;
    boolean available(){return !unavailable;}
    void connected(){if(!unavailable)connected=true;}
    boolean failed(boolean unsupported){
        if(unsupported||!connected||recoveries>=RECOVERY_LIMIT){unavailable=true;return false;}
        recoveries++;return true;
    }
}
