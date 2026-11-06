package dev.edgeflow;
import android.content.Context;
/** Deterministic platform measurement; geometry and migration remain production code. */
final class HandleDisplayArea {
 static int reads;
 static GestureHandleConfig.Area reference=GestureHandleConfig.safeArea(1264,2780,0,140,0,154);
 static GestureHandleConfig.Area display=reference;
 static final class Snapshot {final GestureHandleConfig.Area current,portrait;Snapshot(GestureHandleConfig.Area a){current=display;portrait=a;}}
 static Snapshot read(Context c){reads++;return new Snapshot(reference);}
}
