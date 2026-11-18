package replica;

import android.content.Context;
import android.app.KeyguardManager;
import android.os.PowerManager;
import android.view.WindowManager;

/** The normal widget entry point shares its host ID and layout with the manual preview. */
public final class WidgetOverlay {
    private static WidgetOverlay current;
    private record SetupReturn(long id,java.lang.ref.WeakReference<Context> host,android.os.IBinder windowToken,float[] anchor){}
    private static SetupReturn pendingSetup;

    static long rememberSetup(Context host,float[] anchor){
        OverlayWindowToken owner=OverlayWindowToken.find(host);
        android.os.IBinder token=owner==null?null:owner.overlayWindowToken();
        Context base=owner instanceof Context context?context:host;
        long id=android.os.SystemClock.elapsedRealtimeNanos();
        pendingSetup=new SetupReturn(id,new java.lang.ref.WeakReference<>(base),token,anchor.clone());
        return id;
    }

    static void finishSetup(long id,boolean reopen){
        SetupReturn pending=pendingSetup;
        if(pending==null||pending.id()!=id)return;
        pendingSetup=null;
        Context host=pending.host().get();
        if(!reopen||host==null)return;
        OverlayWindowToken owner=OverlayWindowToken.find(host);
        if(owner!=null&&(pending.windowToken()==null||owner.overlayWindowToken()!=pending.windowToken()))return;
        show(host,pending.anchor());
    }
    private final PanelOverlayDialog dialog;
    private final WidgetPanel panel;
    private ColorMotion motion;
    private float[] anchor;
    private boolean closing, removed;

    private WidgetOverlay(Context service, float[] anchor) {
        this.anchor = anchor.clone();
        dialog = new PanelOverlayDialog(service, this::back, this::outside);
        WindowManager.LayoutParams lp = dialog.getWindow().getAttributes();
        lp.setTitle(service.getString(dev.edgeflow.R.string.app_name) + " 小组件面板");
        dialog.getWindow().setAttributes(lp);
        panel = new WidgetPanel(service, dialog.getWindow(), anchor, true, (provider, profile, id, bound, existing, page) -> {
            long request=rememberSetup(service,this.anchor);
            try{
                service.startActivity(WidgetActivity.setupIntent(service,provider,profile,id,bound,existing,page,this.anchor)
                    .putExtra(WidgetActivity.RETURN_REQUEST,request));
            }catch(RuntimeException failure){
                finishSetup(request,false);
                throw failure;
            }
            remove();
        },this::remove);
        dialog.setOnDismissListener(d -> remove());
    }

    public static void show(Context service, float[] anchor) {
        if (!service.getSystemService(PowerManager.class).isInteractive()
                || service.getSystemService(KeyguardManager.class).isKeyguardLocked()) return;
        if (WidgetActivity.resumeSetup(service,anchor)) return;
        if (current != null && !current.closing) {
            current.anchor = anchor.clone();
            if(current.motion!=null)current.motion.reanchor(anchor[0]<PanelGeometry.screen(service).x/2f,anchor[1]);
            current.panel.reanchor(anchor);
            return;
        }
        dismissAll();
        try {
            WidgetOverlay owner = new WidgetOverlay(service, anchor);
            current = owner;
            owner.panel.render();
            owner.motion = ColorMotion.forTool(owner.dialog.getWindow(),ToolAnimation.WIDGETS,anchor);
            owner.dialog.show();
            owner.panel.start();
            owner.motion.enter();
        } catch (RuntimeException failure) {
            dismissAll();
            android.util.Log.w("EdgeWidgets", "Cannot open widget panel", failure);
            android.widget.Toast.makeText(service, "无法打开小组件面板", android.widget.Toast.LENGTH_SHORT).show();
        }
    }

    public static boolean handleBack() {
        if (current == null) return false;
        current.back();
        return true;
    }

    public static void configurationChanged() {
        if (current != null && !current.closing) current.panel.render();
    }

    public static void dismissForDisplayChange(){if(current!=null)current.remove();}
    public static void dismissAll(){pendingSetup=null;dismissForDisplayChange();}
    public static boolean isShowing(){return current!=null&&!current.removed&&current.dialog.isShowing();}
    private void back() { if (!closing && !panel.backInEditor()) close(); }
    private void outside() { if (!panel.dismissMenuOnOutside()&&!panel.hasEditor()) close(); }
    private void close() {
        if (closing || removed) return;
        closing = true;
        if (motion == null) remove(); else motion.exit(this::remove);
    }
    private void remove() {
        if (removed) return;
        removed = true;
        if (current == this) current = null;
        if (motion != null) motion.cancel();
        try { panel.close(); } finally { dialog.dismiss(); }
    }
}
