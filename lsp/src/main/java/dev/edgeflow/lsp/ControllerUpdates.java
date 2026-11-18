package dev.edgeflow.lsp;

/** Keep the newest source snapshot, but never lose a revocation during reconnect. */
final class ControllerUpdates {
    record Request(boolean revoke,String profile){}
    private boolean revoke;
    private String profile;
    void offer(String value){profile=value;}
    void revoke(){revoke=true;}
    boolean pending(){return revoke||profile!=null;}
    Request take(){Request value=new Request(revoke,profile);revoke=false;profile=null;return value;}
    void failed(Request value){revoke|=value.revoke();if(profile==null)profile=value.profile();}
}
