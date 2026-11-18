package android.view;
import android.os.Looper;
/** Compile-only contract, not an implementation or a runtime dependency. */
public abstract class InputEventReceiver {
    public InputEventReceiver(InputChannel channel, Looper looper) { throw new AssertionError("compile-only"); }
    public void onInputEvent(InputEvent event) { throw new AssertionError("compile-only"); }
    public void onBatchedInputEventPending(int source) { throw new AssertionError("compile-only"); }
    public final boolean consumeBatchedInputEvents(long frameTimeNanos) { throw new AssertionError("compile-only"); }
    public final void finishInputEvent(InputEvent event, boolean handled) { throw new AssertionError("compile-only"); }
    public void dispose() { throw new AssertionError("compile-only"); }
}
