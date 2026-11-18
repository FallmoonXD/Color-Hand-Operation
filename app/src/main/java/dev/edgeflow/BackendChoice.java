package dev.edgeflow;
import android.content.Context;
/** Compatibility guard for retired accessibility helpers. CHO now uses LSPosed only. */
public final class BackendChoice {
    public static boolean lsp(Context c){return true;}
    private BackendChoice(){}
}
