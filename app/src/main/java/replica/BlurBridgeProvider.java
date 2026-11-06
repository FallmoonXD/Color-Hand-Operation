package replica;

import android.content.*;
import android.database.Cursor;
import android.net.Uri;
import android.os.*;

/** Separate authorities keep the activity and accessibility processes isolated. */
public class BlurBridgeProvider extends ContentProvider {
    public boolean onCreate() { return true; }
    public Bundle call(String method, String arg, Bundle extras) {
        if (Binder.getCallingUid()!=0 && Binder.getCallingUid()!=android.os.Process.myUid())
            throw new SecurityException("Material bridge is root/self only");
        Bundle out=new Bundle();
        if ("stats".equals(method)) return NativeBlur.stats();
        if ("ui_theme".equals(method)) { out.putString("theme",ColorOsTheme.describe(getContext()).toString());return out; }
        if ("connection".equals(method)) {
            NativeBlur.connection=extras.getBinder("connection");
            return out;
        }
        if ("service".equals(method)) {
            IBinder binder=extras==null?null:extras.getBinder("service");
            NativeBlur.main.post(()->NativeBlur.connected(binder));
            return out;
        }
        if ("thread".equals(method)) try {
            org.lsposed.hiddenapibypass.HiddenApiBypass.addHiddenApiExemptions("Landroid/app/ActivityThread;");
            Class<?> cls=Class.forName("android.app.ActivityThread");
            Object thread=cls.getMethod("currentActivityThread").invoke(null);
            IInterface app=(IInterface)cls.getMethod("getApplicationThread").invoke(thread);
            out.putBinder("thread",app.asBinder());
            return out;
        } catch(Exception e) { throw new IllegalStateException(e); }
        throw new IllegalArgumentException("Unknown bridge operation");
    }
    public Cursor query(Uri u,String[] p,String s,String[] a,String o){return null;}
    public String getType(Uri u){return null;}
    public Uri insert(Uri u,ContentValues v){return null;}
    public int delete(Uri u,String s,String[] a){return 0;}
    public int update(Uri u,ContentValues v,String s,String[] a){return 0;}
}
