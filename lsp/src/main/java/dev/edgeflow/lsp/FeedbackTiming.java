package dev.edgeflow.lsp;

/** Threshold feedback is latched for one contact, even after retreat and recross. */
final class FeedbackTiming {
    private boolean ready,quick;
    void begin(boolean quick){this.quick=quick;ready=false;}
    boolean progress(boolean shortReady,boolean owned){
        if(!owned||!shortReady||ready)return false;ready=true;return quick;
    }
    boolean action(boolean longer){return !quick||longer||!ready;}
}
