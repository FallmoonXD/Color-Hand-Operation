package dev.edgeflow.lsp;

/** A cancellation may arrive before the queued start; neither may revive an older stroke. */
final class TouchRequestGate {
    private long started,cancelled;private boolean closed;
    synchronized boolean start(long id){if(closed||id<=0||id<=started||id<=cancelled)return false;started=id;return true;}
    synchronized boolean cancel(long id){cancelled=Math.max(cancelled,id);return started<=cancelled;}
    synchronized boolean live(long id){return !closed&&id==started&&id>cancelled;}
    synchronized void close(){closed=true;}
}
