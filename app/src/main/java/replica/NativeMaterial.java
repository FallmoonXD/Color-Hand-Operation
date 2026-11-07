package replica;

import android.content.Context;
import android.graphics.*;
import android.graphics.drawable.Drawable;
import android.hardware.HardwareBuffer;
import android.os.*;
import android.view.*;

/** One native GPU capture per outer window; child controls keep their optical overlays. */
final class NativeMaterial implements View.OnAttachStateChangeListener, ViewTreeObserver.OnPreDrawListener {
    private static String source;
    private static int nextId=27000;
    private static final java.util.WeakHashMap<View,java.lang.ref.WeakReference<NativeMaterial>> owners=new java.util.WeakHashMap<>();
    private final int id=++nextId,kind;
    private final Context context;
    private final ColorOsSurface owner;
    private final float radius,density;
    private boolean dark,blurAllowed,strokeAllowed,attached;
    private final Paint paint=new Paint(Paint.ANTI_ALIAS_FLAG|Paint.FILTER_BITMAP_FLAG);
    private final Rect bounds=new Rect(),capture=new Rect(),displayBounds=new Rect();
    private final Matrix matrix=new Matrix();
    private final int[] xy=new int[2];
    private final Point displaySize=new Point();
    private final float[] params=new float[30];
    private View host;
    private Window window;
    private RuntimeShader shader;
    private static final class Frame {
        final Bitmap bitmap;final Rect region;final float scale;final int rotation;
        Frame(Bitmap b,Rect r,float s,int rotation){bitmap=b;region=new Rect(r);scale=s;this.rotation=rotation;}
    }
    private Frame frame,pending;
    private final Rect anchor=new Rect(),mappedRegion=new Rect();
    private float drawnScaleX=Float.NaN,drawnScaleY=Float.NaN;
    private int drawnX,drawnY;
    private BitmapShader input;
    private IBinder service;
    private Binder callback;
    private Parcelable captureSurface;
    private int initialCaptureAttempts;
    private final Runnable requestInitialFrame=new Runnable(){public void run(){
        if(!active||!requested||frame!=null||captureSurface==null)return;
        Parcel p=Parcel.obtain();try{
            p.writeInterfaceToken("com.oplus.blur.session.IBlurService");p.writeStrongBinder(callback);p.writeTypedObject(captureSurface,0);
            service.transact(10,p,null,IBinder.FLAG_ONEWAY);
        }catch(Exception ignored){}finally{p.recycle();}
        if(++initialCaptureAttempts<4)NativeBlur.main.postDelayed(this,64);
    }};
    private volatile int generation;
    private boolean active,requested,failed,blurDisabled,deliveryPosted,pillWindow;
    private int lastWindowBlur=-1;
    private int alpha=255,frameCount;
    private long firstFrame;
    private boolean fallbackVisible,crossfade;
    private Runnable entrance;
    private long waitStart;
    private final Runnable entranceTimeout=()->startEntrance("fallback-timeout");

    static void whenReady(View host,Runnable start){
        java.lang.ref.WeakReference<NativeMaterial> ref=owners.get(host);
        NativeMaterial material=ref==null?null:ref.get();
        if(material==null||material.failed||!material.blurAllowed||NativeBlur.unavailable()){start.run();return;}
        material.cancelEntrance();material.entrance=start;material.waitStart=SystemClock.uptimeMillis();
        if(material.frame!=null)material.startEntrance("native");
        else {
            // Alpha zero removes the capture anchor from SurfaceFlinger's visible layers.
            // Keep it below one 8-bit color step until the normal entrance takes ownership.
            if(material.window!=null){WindowManager.LayoutParams p=material.window.getAttributes();
                if(p.alpha==0){p.alpha=1f/1024f;material.window.setAttributes(p);material.invalidateHost();}}
            NativeBlur.main.postDelayed(material.entranceTimeout,350);
        }
    }
    static void cancelEntrance(View host){
        java.lang.ref.WeakReference<NativeMaterial> ref=owners.get(host);
        NativeMaterial material=ref==null?null:ref.get();if(material!=null)material.cancelEntrance();
    }
    private void cancelEntrance(){entrance=null;NativeBlur.main.removeCallbacks(entranceTimeout);}
    private void startEntrance(String mode){
        Runnable next=entrance;cancelEntrance();if(next==null)return;
        android.util.Log.i("ReplicaNativeMaterial","ENTER "+id+" mode="+mode+" wait="+(SystemClock.uptimeMillis()-waitStart));
        next.run();
    }

    NativeMaterial(Context c,float corner,int kind,ColorOsSurface owner){
        this.context=c;this.kind=kind;this.owner=owner;
        radius=PanelStyle.dp(c,corner);density=c.getResources().getDisplayMetrics().density;dark=ColorOsTheme.dark(c);blurAllowed=ColorOsTheme.materialBlur(c);strokeAllowed=ColorOsTheme.materialStroke(c);
        if(blurAllowed)NativeBlur.prepare(c);
        try {
            org.lsposed.hiddenapibypass.HiddenApiBypass.addHiddenApiExemptions("Landroid/view/View;","Landroid/view/ViewRootImpl;");
            if(source==null)try(java.io.InputStream in=c.getAssets().open("coloros-native-live.agsl")){
                java.io.ByteArrayOutputStream out=new java.io.ByteArrayOutputStream();byte[] b=new byte[4096];int n;
                while((n=in.read(b))!=-1)out.write(b,0,n);source=out.toString("UTF-8");
            }
            shader=new RuntimeShader(source);uniform("uMatrix",1,0,0,0,1,0,0,0,1);uniform("uMatrixParams",1,1,0,0);
            shader.setIntUniform("uEnableBlend",1);shader.setIntUniform("uEnableMetaBall",0);
            applyPalette();uniform("u_edgeReferenceAspect",60f/140f);
        }catch(Throwable e){failed=true;android.util.Log.w("ReplicaNativeMaterial","Native shader unavailable",e);}
    }
    private void uniform(String name,float... values){shader.setFloatUniform(name,values);}
    private void applyPalette(){
        float[] tint=new float[20];tint[1]=1;
        if(kind==3){tint[2]=tint[3]=tint[4]=dark?.12f:.84f;tint[5]=.78f;}
        uniform("u_colorArray",tint);float s=density/3.5f;
        // ColorOS uses 5% white for the side volume bar; apply it once to the captured glass.
        uniform("u_faceLight",kind==2&&strokeAllowed?.05f:0f);
        // Enabled values remain the accepted geometry/mixing, byte for byte.
        uniform("u_edgeArray",1,1,1,1.6f*s,3*s,1.6f*s,3*s,strokeAllowed?(dark?.35f:.25f):0,strokeAllowed?(dark?.4f:.6f):0,.25f,.6f,.15f,.5f,0,10,.5f);
        uniform("u_shadowArray",1,1,1,strokeAllowed?(dark?.07f:.15f):0,0,0,40*s,0,36*s,1.3f,1.3f,20,.5f,6*s,20,.3f);
    }
    void setPills(int count,float[] shapes){
        pillWindow=true;
        if(!failed&&shader!=null)try{shader.setFloatUniform("u_pills",shapes);shader.setIntUniform("u_pillCount",count);}
        catch(RuntimeException error){failed=true;stopNative();startEntrance("native-failed");android.util.Log.w("ReplicaNativeMaterial","Button contours unavailable",error);}
    }
    void themeChanged(){
        boolean nextDark=ColorOsTheme.dark(context),nextBlur=ColorOsTheme.materialBlur(context),nextStroke=ColorOsTheme.materialStroke(context);
        if(dark==nextDark&&blurAllowed==nextBlur&&strokeAllowed==nextStroke)return;
        boolean blurChanged=blurAllowed!=nextBlur;dark=nextDark;blurAllowed=nextBlur;strokeAllowed=nextStroke;
        if(shader!=null&&!failed)try{applyPalette();}catch(RuntimeException e){failed=true;android.util.Log.w("ReplicaNativeMaterial","Policy uniforms unavailable",e);}
        if(failed){stopNative();setBlur(false);startEntrance("native-failed");}
        else if(!blurAllowed){stopNative();setBlur(false);startEntrance("material-disabled");}
        else if(blurChanged&&attached){setBlur(false);startNative();}
        // A night/stroke change reuses the current backdrop and updates its shader
        // before the next draw; it must not restart the first-frame entrance wait.
        invalidateHost();
    }
    void attach(View view,Window w){
        if(host==view&&window==w)return;
        if(host!=null){
            release();host.removeOnAttachStateChangeListener(this);
            java.lang.ref.WeakReference<NativeMaterial> oldOwner=owners.get(host);
            if(oldOwner!=null&&oldOwner.get()==this)owners.remove(host);
        }
        window=w;lastWindowBlur=-1;host=view;if(host==null)return;
        java.lang.ref.WeakReference<NativeMaterial> old=owners.put(host,new java.lang.ref.WeakReference<>(this));
        NativeMaterial previous=old==null?null:old.get();
        if(previous!=null&&previous!=this){previous.release();host.removeOnAttachStateChangeListener(previous);}
        host.addOnAttachStateChangeListener(this);if(host.isAttachedToWindow())onViewAttachedToWindow(host);
    }
    public void onViewAttachedToWindow(View v){
        if(attached)return;attached=true;host.getViewTreeObserver().addOnPreDrawListener(this);themeChanged();setBlur(false);startNative();
    }
    private void startNative(){if(active||!attached||failed||!blurAllowed)return;active=true;NativeBlur.add(this,context);}
    private void stopNative(){removeRequest();if(active){active=false;NativeBlur.remove(this);}clearFrame();}
    public void onViewDetachedFromWindow(View v){release();}
    private void release(){
        cancelEntrance();
        if(host!=null&&host.getViewTreeObserver().isAlive())host.getViewTreeObserver().removeOnPreDrawListener(this);
        attached=false;stopNative();
    }
    public boolean onPreDraw(){
        if(!attached)return true;themeChanged();
        // Window callers may set a blur radius after attaching their background.
        // Keep enforcing the system's disabled state even without a native client.
        if(!blurAllowed){setBlur(false);return true;}
        if(failed){setBlur(false);return true;}
        if(service==null){NativeBlur.ensure();if(NativeBlur.service!=null)connect(NativeBlur.service);}
        else updateCapture();
        host.getLocationOnScreen(xy);float sx=host.getScaleX(),sy=host.getScaleY();
        if(drawnX!=xy[0]||drawnY!=xy[1]||drawnScaleX!=sx||drawnScaleY!=sy){
            drawnX=xy[0];drawnY=xy[1];drawnScaleX=sx;drawnScaleY=sy;
            // View scale/translation can reuse a cached RenderNode. Re-record the inverse mapping
            // in this traversal even when a static desktop produces no new blur buffers.
            owner.invalidateSelf();host.invalidate();
        }
        return true;
    }
    void connect(IBinder binder){
        if(!active||!blurAllowed||failed||requested||host.getWidth()==0||host.getHeight()==0)return;
        try{
            Object root=View.class.getMethod("getViewRootImpl").invoke(host);
            Parcelable surface=(Parcelable)root.getClass().getMethod("getSurfaceControl").invoke(root);
            readGeometry();service=binder;final int epoch=++generation;
            callback=new Binder(){protected boolean onTransact(int code,Parcel data,Parcel reply,int flags)throws RemoteException{
                if(code==INTERFACE_TRANSACTION){if(reply!=null)reply.writeString("com.oplus.blur.session.IBlurConnection");return true;}
                data.enforceInterface("com.oplus.blur.session.IBlurConnection");
                if(code==4){Bundle b=data.readTypedObject(Bundle.CREATOR);if(b!=null){if(frameCount<4)android.util.Log.i("ReplicaFrame","BUFFER "+id+" count="+frameCount+" region="+b.getParcelable("on_blur_ready_capture_region",Rect.class)+" scale="+b.getFloat("on_blur_ready_buffer_scale")+" rotation="+b.getInt("on_blur_ready_buffer_rotation"));accept(b.getParcelable("on_blur_ready_blur_buffer",HardwareBuffer.class),epoch,b.getParcelable("on_blur_ready_capture_region",Rect.class),b.getFloat("on_blur_ready_buffer_scale",.25f),b.getInt("on_blur_ready_buffer_rotation",0));}return true;}
                if(code==1){data.readArrayList(getClass().getClassLoader());HardwareBuffer buffer=data.readTypedObject(HardwareBuffer.CREATOR);Display display=host==null?null:host.getDisplay();accept(buffer,epoch,new Rect(capture),.25f,display==null?0:display.getRotation());return true;}
                if(code==2||code==3)return true;
                return super.onTransact(code,data,reply,flags);
            }};
            Parcel p=Parcel.obtain();try{
                p.writeInterfaceToken("com.oplus.blur.session.IBlurService");p.writeInt(id);p.writeInt(2);
                p.writeTypedObject(displayBounds,0);p.writeFloatArray(params);p.writeInt(0);p.writeTypedObject(surface,0);p.writeStrongBinder(callback);
                if(!binder.transact(1,p,null,IBinder.FLAG_ONEWAY))throw new IllegalStateException("Unsupported blur service");
            }finally{p.recycle();}
            requested=true;captureSurface=surface;initialCaptureAttempts=0;
            // A completely static scene need not produce compositor damage while the window is hidden.
            // Ask the native service for a first capture rather than waiting for the entrance to move it.
            NativeBlur.main.postDelayed(requestInitialFrame,64);
            NativeBlur.starts++;android.util.Log.i("ReplicaNativeMaterial","ADD "+id+" "+capture+" display="+displayBounds);
        }catch(Throwable e){failed=true;stopNative();startEntrance("native-unavailable");invalidateHost();android.util.Log.w("ReplicaNativeMaterial","Capture unavailable",e);}
    }
    private boolean readGeometry(){
        host.getLocationOnScreen(xy);host.getDisplay().getRealSize(displaySize);
        // Capture a stable window-sized region, with room for its entrance/exit transforms.
        // Changing the crop every animation frame made older returned buffers appear to zoom.
        int x=Math.round(xy[0]-host.getTranslationX()-host.getPivotX()*(1-host.getScaleX()));
        int y=Math.round(xy[1]-host.getTranslationY()-host.getPivotY()*(1-host.getScaleY()));
        boolean changed=anchor.isEmpty()||Math.abs(anchor.left-x)>2||Math.abs(anchor.top-y)>2
            ||anchor.width()!=host.getWidth()||anchor.height()!=host.getHeight()
            ||displayBounds.width()!=displaySize.x||displayBounds.height()!=displaySize.y;
        if(changed){
            anchor.set(x,y,x+host.getWidth(),y+host.getHeight());int pad=PanelStyle.dp(context,24);
            capture.set(Math.max(0,x-pad),Math.max(0,y-pad),Math.min(displaySize.x,anchor.right+pad),Math.min(displaySize.y,anchor.bottom+pad));
            android.util.Log.i("ReplicaFrame","CAPTURE "+id+" region="+capture+" anchor="+anchor);
        }
        displayBounds.set(0,0,displaySize.x,displaySize.y);
        params[1]=1;params[2]=230*density/3.5f;params[3]=capture.left;params[4]=capture.top;params[5]=capture.right;params[6]=capture.bottom;
        params[16]=2;params[17]=1;params[18]=-1;params[29]=1;return changed;
    }
    private void updateCapture(){
        if(!requested)return;
        int oldW=displayBounds.width(),oldH=displayBounds.height();boolean changed=readGeometry();
        if(oldW!=displayBounds.width()||oldH!=displayBounds.height()){
            IBinder old=service;removeRequest();clearFrame();connect(old);return;
        }
        if(!changed)return;
        Parcel p=Parcel.obtain();try{
            p.writeInterfaceToken("com.oplus.blur.session.IBlurService");p.writeStrongBinder(callback);p.writeInt(id);p.writeFloatArray(params);p.writeInt(0);
            service.transact(3,p,null,IBinder.FLAG_ONEWAY);
        }catch(Exception e){disconnected();}finally{p.recycle();}
    }
    private void accept(HardwareBuffer buffer,int epoch,Rect region,float scale,int rotation){
        if(buffer==null)return;
        Bitmap next=null;
        try{if(epoch!=generation)return;next=Bitmap.wrapHardwareBuffer(buffer,ColorSpace.get(ColorSpace.Named.SRGB));}
        catch(Exception e){android.util.Log.w("ReplicaNativeMaterial","Buffer import failed",e);}
        finally{buffer.close();}
        if(next==null)return;
        Rect actual=region==null||region.isEmpty()?new Rect(capture):region;
        Frame received=new Frame(next,actual,scale>0?scale:.25f,rotation);
        synchronized(this){
            if(epoch!=generation){next.recycle();return;}
            if(pending!=null)pending.bitmap.recycle();pending=received;
            if(deliveryPosted)return;deliveryPosted=true;
        }
        NativeBlur.main.post(()->{
            Frame latest;synchronized(this){latest=pending;pending=null;deliveryPosted=false;}
            if(latest==null)return;
            if(!active||!blurAllowed||!requested){latest.bitmap.recycle();return;}
            frame=latest;input=new BitmapShader(frame.bitmap,Shader.TileMode.CLAMP,Shader.TileMode.CLAMP);input.setFilterMode(BitmapShader.FILTER_MODE_LINEAR);
            if(firstFrame==0){firstFrame=SystemClock.uptimeMillis();crossfade=fallbackVisible;
                NativeBlur.main.removeCallbacks(requestInitialFrame);
                if(!crossfade)setBlur(true);
                android.util.Log.i("ReplicaNativeMaterial","READY "+id+" "+frame.bitmap.getWidth()+"x"+frame.bitmap.getHeight()+" crossfade="+crossfade);
                startEntrance("native");
            }
            frameCount++;NativeBlur.frames.incrementAndGet();invalidateHost();
        });
    }
    private void removeRequest(){
        NativeBlur.main.removeCallbacks(requestInitialFrame);captureSurface=null;
        generation++;
        if(requested&&service!=null){Parcel p=Parcel.obtain();try{
            p.writeInterfaceToken("com.oplus.blur.session.IBlurService");p.writeStrongBinder(callback);p.writeInt(id);service.transact(2,p,null,IBinder.FLAG_ONEWAY);
        }catch(Exception ignored){}finally{p.recycle();}
            NativeBlur.stops++;android.util.Log.i("ReplicaNativeMaterial","REMOVE "+id+" frames="+frameCount);
        }
        requested=false;service=null;callback=null;
    }
    private void invalidateHost(){owner.invalidateSelf();if(host!=null)host.postInvalidateOnAnimation();}
    void disconnected(){removeRequest();clearFrame();invalidateHost();}
    void nativeUnavailable(){disconnected();startEntrance("native-unavailable");}
    private void clearFrame(){
        synchronized(this){if(pending!=null)pending.bitmap.recycle();pending=null;}
        // Displayed hardware bitmaps may still be referenced by RenderThread; let it release them.
        frame=null;input=null;paint.setShader(null);firstFrame=0;fallbackVisible=false;crossfade=false;
        if(shader!=null)shader.setInputShader("uniBDFBitmap",new LinearGradient(0,0,1,1,Color.TRANSPARENT,Color.TRANSPARENT,Shader.TileMode.CLAMP));
        setBlur(false);
    }
    private void setBlur(boolean nativeReady){
        if(window==null)return;int desired=pillWindow||!blurAllowed||nativeReady||!attached?0:PanelStyle.blurRadius(context);
        if(blurAllowed&&lastWindowBlur==desired)return;
        try{window.setBackgroundBlurRadius(desired);lastWindowBlur=desired;blurDisabled=nativeReady;}catch(Exception ignored){}
    }
    void bounds(Rect r){
        bounds.set(r);if(failed)return;
        float w=r.width(),h=r.height(),corner=Math.min(radius,Math.min(w,h)/2),weight=Math.abs(w-h)<3?1:2;
        uniform("u_commonArray",w/2,h/2,w/2,h/2,w/2,h/2,corner,weight);uniform("u_size",w,h);uniform("u_corner",corner);uniform("u_weight",weight);
    }
    private void mapBackdrop(){
        // Use the metadata belonging to this buffer, not the latest requested crop or view size.
        matrix.reset();mappedRegion.set(frame.region);
        int currentRotation=host.getDisplay().getRotation();
        int turn=(currentRotation%2==frame.rotation%2)?0:(frame.rotation-currentRotation+4)%4;
        if(turn!=0){
            float w=frame.bitmap.getWidth(),h=frame.bitmap.getHeight();matrix.postRotate(turn*90f);
            if(turn==1)matrix.postTranslate(h,0);else if(turn==2)matrix.postTranslate(w,h);else matrix.postTranslate(0,w);
            int sw=displayBounds.width(),sh=displayBounds.height();
            int bw=turn%2==0?sw:sh,bh=turn%2==0?sh:sw;Rect r=frame.region;
            if(turn==1)mappedRegion.set(bh-r.bottom,r.left,bh-r.top,r.right);
            else if(turn==2)mappedRegion.set(bw-r.right,bh-r.bottom,bw-r.left,bh-r.top);
            else mappedRegion.set(r.top,bw-r.right,r.bottom,bw-r.left);
        }
        host.getLocationOnScreen(xy);
        matrix.postScale(1/frame.scale,1/frame.scale);
        matrix.postTranslate(mappedRegion.left-xy[0],mappedRegion.top-xy[1]);
        matrix.postScale(1/Math.max(.001f,host.getScaleX()),1/Math.max(.001f,host.getScaleY()));
        matrix.postTranslate(-bounds.left,-bounds.top);
    }
    void draw(Canvas canvas,Drawable fallback){
        if(!blurAllowed||frame==null||failed||!canvas.isHardwareAccelerated()){
            if(window==null||window.getAttributes().alpha>.001f)fallbackVisible=true;
            fallback.draw(canvas);return;
        }
        try{
            float mix=crossfade?Math.min(1,(SystemClock.uptimeMillis()-firstFrame)/140f):1;
            if(mix<1)fallback.draw(canvas);else setBlur(true);
            mapBackdrop();input.setLocalMatrix(matrix);
            shader.setInputShader("uniBDFBitmap",input);paint.setShader(shader);paint.setAlpha(Math.round(alpha*mix));
            int save=canvas.save();canvas.translate(bounds.left,bounds.top);canvas.drawRect(0,0,bounds.width(),bounds.height(),paint);canvas.restoreToCount(save);
            if(mix<1)invalidateHost();
        }catch(Throwable e){failed=true;stopNative();startEntrance("native-failed");fallback.draw(canvas);android.util.Log.e("ReplicaNativeMaterial","Native draw failed",e);}
    }
    void alpha(int a){alpha=a;}
    void filter(ColorFilter filter){paint.setColorFilter(filter);}
}
