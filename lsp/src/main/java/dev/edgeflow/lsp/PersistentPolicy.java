package dev.edgeflow.lsp;

/** Pure lifecycle decision. Suspensions keep the saved request; faults require a fresh configuration. */
final class PersistentPolicy {
    enum State { DISABLED, CHECKING, LOCKED, OTHER_USER, SOURCE_UNAVAILABLE, ACCESSIBILITY, MASTER_OFF, FAULTED, RUNNING }
    static boolean shouldRefresh(boolean checked,boolean refreshing,boolean faulted,boolean awake,boolean unlocked,boolean owner){
        return !checked&&!refreshing&&!faulted&&awake&&unlocked&&owner;
    }
    static boolean revokesSource(String action,boolean replacing){
        return "android.intent.action.PACKAGE_DATA_CLEARED".equals(action)
            ||("android.intent.action.PACKAGE_REMOVED".equals(action)&&!replacing);
    }
    static State decide(boolean requested,boolean checked,boolean awake,boolean unlocked,boolean owner,
                        boolean source,boolean accessibility,boolean master,boolean faulted){
        if(!requested)return State.DISABLED;
        if(!checked)return State.CHECKING;
        if(!owner)return State.OTHER_USER;
        if(!awake||!unlocked)return State.LOCKED;
        if(!source)return State.SOURCE_UNAVAILABLE;
        if(accessibility)return State.ACCESSIBILITY;
        if(!master)return State.MASTER_OFF;
        return faulted?State.FAULTED:State.RUNNING;
    }
}
