package replica;

/** Optional native volume lifecycle supplied by the LSP tool session. */
public interface NativeVolumeSource {
    void observeVolume(java.util.function.Consumer<android.os.Bundle> listener);
}
