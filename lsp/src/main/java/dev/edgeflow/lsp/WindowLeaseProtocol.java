package dev.edgeflow.lsp;

/** Private, versioned operation on the existing ActivityManager Binder. */
final class WindowLeaseProtocol {
    static final int TRANSACTION=0x43484f;
    static final String DESCRIPTOR="dev.edgeflow.lsp.WindowLease.v2";
    static final int STATUS=0,GRANT=1,REVOKE=2,TOUCH=3,CANCEL_TOUCH=4;
    private WindowLeaseProtocol(){}
}
