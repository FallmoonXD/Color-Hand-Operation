package dev.edgeflow.lsp;

import dev.edgeflow.GestureRecognizer;

/** Decision state only. The caller must confirm pilferPointers before claim(). */
public final class EdgeInputSession {
    public enum Decision { NONE, CLAIM, BACK }
    private final float width, height, band, top, bottom, threshold;
    private final GestureRecognizer recognizer;
    private boolean tracking, owned, cancelled, pendingClaim;
    private int pointer;
    private long lastEvent;

    public EdgeInputSession(float width, float height, float band, float top, float bottom, float threshold) {
        if (!Float.isFinite(width) || !Float.isFinite(height) || !Float.isFinite(band)
                || !Float.isFinite(top) || !Float.isFinite(bottom) || !Float.isFinite(threshold)
                || width <= 0 || height <= 0 || band <= 0 || band >= width / 2
                || top < 0 || bottom > height || bottom <= top || threshold <= 0) {
            throw new IllegalArgumentException("Invalid input geometry");
        }
        this.width=width; this.height=height; this.band=band;
        this.top=top; this.bottom=bottom; this.threshold=threshold;
        recognizer = new GestureRecognizer(threshold, Math.max(width,height)*4, 60000, 35, false, false, false);
    }

    public boolean begin(int pointerId, float x, float y, long now, boolean eligible) {
        reset();
        if (!eligible || !Float.isFinite(x) || !Float.isFinite(y) || x<0 || x>=width
                || y<top || y>=bottom || (x>=band && x<width-band)) return false;
        pointer=pointerId; lastEvent=now; tracking=true;
        recognizer.begin(x<width/2,x,y,now);
        return true;
    }

    public Decision move(int pointerId, int count, float x, float y, long now, boolean eligible) {
        if (!tracking || cancelled) return Decision.NONE;
        if (!eligible || count!=1 || pointerId!=pointer || now<lastEvent
                || !Float.isFinite(x) || !Float.isFinite(y)) { cancel(); return Decision.NONE; }
        lastEvent=now;pendingClaim=false;
        recognizer.move(x,y,now);
        if (!owned && Math.abs(recognizer.vertical())>=threshold
                && Math.abs(recognizer.vertical())>recognizer.inward()) {
            // Once the application owns a vertical scroll, do not later steal it.
            cancel(); return Decision.NONE;
        }
        pendingClaim=!owned && recognizer.isShortReady() && recognizer.direction()==GestureRecognizer.STRAIGHT;
        return pendingClaim ? Decision.CLAIM : Decision.NONE;
    }

    public void claim() { if (tracking && !cancelled && pendingClaim) {owned=true;pendingClaim=false;} }
    public void cancel() { cancelled=true;pendingClaim=false;recognizer.cancel(); }
    public boolean owned() { return owned; }
    public boolean tracking() { return tracking; }

    public Decision end(int pointerId, float x, float y, long now, boolean eligible) {
        Decision result=Decision.NONE;
        if (tracking && owned && !cancelled && eligible && pointerId==pointer && now>=lastEvent
                && Float.isFinite(x) && Float.isFinite(y)) {
            recognizer.move(x,y,now);
            if (recognizer.direction()==GestureRecognizer.STRAIGHT && recognizer.finish(now)==1)
                result=Decision.BACK;
        }
        reset();
        return result;
    }

    public void reset() { tracking=owned=cancelled=pendingClaim=false; recognizer.cancel(); }
}
