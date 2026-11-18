package replica;

/** Main-thread policy for service discovery, helper startup and bounded service-death recovery. */
final class NativeBlurPolicy {
    static final int RECOVERY_LIMIT=2;
    private enum Block { NONE, STARTUP, UNSUPPORTED, SERVICE_LOST }
    private Block blocked=Block.NONE;
    private boolean connected;
    private int recoveries,authorization;

    boolean available(){return blocked==Block.NONE;}
    int authorization(){return authorization;}
    void connected(){if(available())connected=true;}
    void unsupported(){blocked=Block.UNSUPPORTED;}

    void rootAuthorized(){
        authorization++;
        if(blocked==Block.STARTUP)blocked=Block.NONE;
    }

    boolean startupFailed(int attemptAuthorization){
        if(!available())return false;
        // A real grant may arrive while the previous su process is still closing.
        if(attemptAuthorization!=authorization)return true;
        blocked=Block.STARTUP;
        return false;
    }

    boolean serviceLost(){
        if(!available())return false;
        if(!connected||recoveries>=RECOVERY_LIMIT){blocked=Block.SERVICE_LOST;return false;}
        recoveries++;
        return true;
    }
}
