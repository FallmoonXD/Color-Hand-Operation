package replica;
import android.content.Context;import android.graphics.*;import android.graphics.drawable.Drawable;import android.view.View;
import java.lang.ref.WeakReference;import java.util.ArrayList;import java.util.WeakHashMap;

/** Native backdrop and original optical constants, with live system material policy. */
public final class ColorOsSurface extends Drawable implements Drawable.Callback {
 private final OriginalMaterial optics;private final float corner;private final NativeMaterial nativeMaterial;
 private final MaterialThemeBinding theme;
 private Rect[] pills;
 public ColorOsSurface(Context c,float radius,int style){
  corner=PanelStyle.dp(c,radius);
  optics=new OriginalMaterial(c,radius,style,true);
  optics.managedByParent();optics.setCallback(this);
  boolean panel=style==2||style==3;
  nativeMaterial=panel&&android.os.Build.VERSION.SDK_INT>=33?new NativeMaterial(c,radius,style,this):null;
  theme=new MaterialThemeBinding(this,this::themeChanged,optics::releaseSample);
 }
 public ColorOsSurface attach(View host){theme.attach(host);if(nativeMaterial!=null)nativeMaterial.attach(host,null);return this;}
 public ColorOsSurface attach(android.view.Window window){
  if(pills==null)PanelGeometry.reserveShadow(window);
  theme.attach(window.getDecorView());if(nativeMaterial!=null)nativeMaterial.attach(window.getDecorView(),window);return this;
 }
 /** Each button has its own SDF and rim, while the entire window samples one frame. */
 public void setPillBounds(Rect[] shapes){
  if(shapes.length>6)throw new IllegalArgumentException("A rail supports one grip and five actions");
  if(java.util.Arrays.equals(pills,shapes))return;
  pills=new Rect[shapes.length];
  float[] data=new float[6*4];
  for(int i=0;i<shapes.length;i++){
   Rect r=pills[i]=new Rect(shapes[i]);
   int at=4*i;data[at]=r.exactCenterX();data[at+1]=r.exactCenterY();data[at+2]=r.width()*.5f;data[at+3]=r.height()*.5f;
  }
  optics.setPills(pills,data);
  if(nativeMaterial!=null)nativeMaterial.setPills(pills.length,data);
  invalidateSelf();
 }
 private void themeChanged(){
  optics.refreshTheme();
  if(nativeMaterial!=null)nativeMaterial.themeChanged();
  invalidateSelf();
 }
 @Override public void draw(Canvas canvas){
  if(pills!=null&&pills.length==0)return;
  theme.track();optics.themeChanged();
  if(nativeMaterial!=null){nativeMaterial.themeChanged();nativeMaterial.draw(canvas,optics);}
  else optics.draw(canvas);
 }
 @Override protected void onBoundsChange(Rect bounds){
  optics.setBounds(bounds);
  if(nativeMaterial!=null)nativeMaterial.bounds(bounds);
  if(theme!=null)theme.track();
 }
 @Override public void setAlpha(int alpha){
  optics.setAlpha(alpha);
  if(nativeMaterial!=null)nativeMaterial.alpha(alpha);
  invalidateSelf();
 }
 @Override public void setColorFilter(ColorFilter filter){
  optics.setColorFilter(filter);
  if(nativeMaterial!=null)nativeMaterial.filter(filter);
  invalidateSelf();
 }
 public int getOpacity(){return PixelFormat.TRANSLUCENT;}
 public void getOutline(Outline o){o.setRoundRect(getBounds(),corner);}
 public void invalidateDrawable(Drawable d){invalidateSelf();}
 public void scheduleDrawable(Drawable d,Runnable r,long t){scheduleSelf(r,t);}
 public void unscheduleDrawable(Drawable d,Runnable r){unscheduleSelf(r);}
}

/** One attach listener/theme subscription per host; child drawables are weak entries. */
final class MaterialThemeBinding {
 private static final WeakHashMap<View,Host> hosts=new WeakHashMap<>();
 private final Drawable owner;private final Runnable changed,detached;
 private WeakReference<View> explicit,tracked;private boolean seenCallback;
 MaterialThemeBinding(Drawable owner,Runnable changed,Runnable detached){this.owner=owner;this.changed=changed;this.detached=detached;}
 void attach(View host){explicit=new WeakReference<>(host);track();}
 private View callbackHost(){Drawable.Callback callback=owner.getCallback();for(int depth=0;depth<20&&callback instanceof Drawable;depth++)callback=((Drawable)callback).getCallback();return callback instanceof View?(View)callback:null;}
 private View current(){View callback=callbackHost();if(callback!=null){seenCallback=true;return callback;}return explicit!=null&&!seenCallback?explicit.get():null;}
 void track(){View next=current(),old=tracked==null?null:tracked.get();if(next==old)return;if(old!=null){Host host=hosts.get(old);if(host!=null)host.remove(this);}tracked=next==null?null:new WeakReference<>(next);if(next!=null){Host host=hosts.get(next);if(host==null){host=new Host(next);hosts.put(next,host);}host.add(this);}}
 private static final class Host implements View.OnAttachStateChangeListener {
  private final WeakReference<View> view;private final ArrayList<WeakReference<MaterialThemeBinding>> bindings=new ArrayList<>();private ColorOsTheme.Subscription subscription;
  Host(View v){view=new WeakReference<>(v);v.addOnAttachStateChangeListener(this);}
  void add(MaterialThemeBinding binding){compact();bindings.add(new WeakReference<>(binding));View v=view.get();if(v!=null&&v.isAttachedToWindow())subscribe(v);}
  void remove(MaterialThemeBinding binding){bindings.removeIf(ref->ref.get()==null||ref.get()==binding);cleanIfEmpty();}
  private void compact(){View v=view.get();bindings.removeIf(ref->{MaterialThemeBinding binding=ref.get();if(binding==null)return true;if(binding.current()==v)return false;if(binding.tracked!=null&&binding.tracked.get()==v)binding.tracked=null;return true;});}
  private void subscribe(View v){if(subscription==null)subscription=ColorOsTheme.observe(v.getContext(),this::dispatch);}
  private void dispatch(){compact();for(WeakReference<MaterialThemeBinding> ref:new ArrayList<>(bindings)){MaterialThemeBinding binding=ref.get();if(binding!=null)binding.changed.run();}cleanIfEmpty();}
  private void cleanIfEmpty(){if(!bindings.isEmpty())return;if(subscription!=null){subscription.close();subscription=null;}View v=view.get();if(v!=null){v.removeOnAttachStateChangeListener(this);if(hosts.get(v)==this)hosts.remove(v);}}
  public void onViewAttachedToWindow(View v){compact();if(!bindings.isEmpty()){subscribe(v);dispatch();}else cleanIfEmpty();}
  public void onViewDetachedFromWindow(View v){if(subscription!=null){subscription.close();subscription=null;}for(WeakReference<MaterialThemeBinding> ref:new ArrayList<>(bindings)){MaterialThemeBinding binding=ref.get();if(binding!=null)binding.detached.run();}compact();cleanIfEmpty();}
 }
}
