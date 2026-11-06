package replica;

import android.content.Context;
import android.graphics.*;
import android.graphics.drawable.Drawable;

/** Soft directional reflection: the edge fades instead of forming a solid ring. */
public final class LightSurface extends Drawable {
 private final Paint paint=new Paint(Paint.ANTI_ALIAS_FLAG|Paint.DITHER_FLAG);
 private final RectF box=new RectF(),opaqueBox=new RectF();private final float radius,edge;private final int kind;private boolean dark,blur,stroke,managed;
 private final Context context;private final MaterialThemeBinding theme;
 private final Path clip=new Path();private Shader fill,reflection,rim,innerLight;private int opacity=255;
 private int opaquePanel=0xff333333,opaqueControl=0x33ffffff;
 public LightSurface(Context c,float corner,int style){context=c;radius=PanelStyle.dp(c,corner);edge=c.getResources().getDisplayMetrics().density*.35f;kind=style;dark=ColorOsTheme.dark(c);blur=ColorOsTheme.materialBlur(c);stroke=ColorOsTheme.materialStroke(c);theme=new MaterialThemeBinding(this,this::refreshTheme,()->{});if(!blur)readOpaquePalette();}
 void managedByParent(){managed=true;}
 void refreshTheme(){themeChanged();if(!blur)readOpaquePalette();invalidateSelf();}
 void themeChanged(){boolean nextDark=ColorOsTheme.dark(context),nextBlur=ColorOsTheme.materialBlur(context),nextStroke=ColorOsTheme.materialStroke(context);if(dark!=nextDark||blur!=nextBlur||stroke!=nextStroke){dark=nextDark;blur=nextBlur;stroke=nextStroke;if(!blur)readOpaquePalette();onBoundsChange(getBounds());invalidateSelf();}}
 private void readOpaquePalette(){
  // VolumeBlurManager's fallback is inverse (white content) in both night modes.
  // Settings dialogs (kind 3) instead use ColorOsTheme.text/card as a matched pair.
  opaquePanel=0xff333333;opaqueControl=0x33ffffff;
  if(kind!=0&&kind!=2)return;
  try{Context system=context.createPackageContext("com.android.systemui",0).createConfigurationContext(new android.content.res.Configuration(context.getResources().getConfiguration()));android.content.res.Resources resources=system.getResources();int panel=resources.getIdentifier("oplus_volume_panel_background_default","color","com.android.systemui"),control=resources.getIdentifier("oplus_volume_settings_coui_background","color","com.android.systemui");if(panel!=0)opaquePanel=resources.getColor(panel,null);if(control!=0)opaqueControl=resources.getColor(control,null);}catch(android.content.pm.PackageManager.NameNotFoundException|RuntimeException unavailable){/* Portable defaults are the captured ColorOS resource values. */}
 }
 static int opaqueColor(int kind,int panel,int control,int accent,int card){
  if(kind==1)return accent|0xff000000;if(kind==3)return card|0xff000000;
  panel|=0xff000000;if(kind!=0)return panel;
  // Flatten the system's translucent settings-button layer onto its opaque panel.
  int a=control>>>24,r=(((control>>>16)&255)*a+((panel>>>16)&255)*(255-a)+127)/255,g=(((control>>>8)&255)*a+((panel>>>8)&255)*(255-a)+127)/255,b=((control&255)*a+(panel&255)*(255-a)+127)/255;
  return 0xff000000|(r<<16)|(g<<8)|b;
 }
 @Override protected void onBoundsChange(Rect b){
  box.set(b.left+edge/2,b.top+edge/2,b.right-edge/2,b.bottom-edge/2);opaqueBox.set(b);
  int[] colors=kind==3?(dark?new int[]{0xdf20242e,0xd9202530}:new int[]{0xdfcdd2dd,0xd9d4d9e2}):kind==1?new int[]{0xff329eff,0xff188dff,0xff087fe8}:kind==2?new int[]{0x4d595959,0x4d595959}:new int[]{0x00000000,0x00000000};
  fill=new LinearGradient(box.left,box.top,box.right,box.bottom,colors,null,Shader.TileMode.CLAMP);
  reflection=new LinearGradient(0,box.top,0,box.top+Math.max(1,box.height()*.7f),new int[]{kind==2?0x12ffffff:0x0dffffff,0x02ffffff,0x00ffffff},new float[]{0,.5f,1},Shader.TileMode.CLAMP);
  clip.reset();clip.addRoundRect(box,radius,radius,Path.Direction.CW);
  innerLight=new LinearGradient(box.left,box.top,box.right,box.bottom,new int[]{0x26ffffff,0x06ffffff,0x02ffffff,0x26ffffff},new float[]{0,.3f,.6f,1},Shader.TileMode.CLAMP);
  rim=new LinearGradient(box.left,box.top,box.right,box.bottom,new int[]{0x40ffffff,0x20ffffff,0x08ffffff,0x18ffffff,0x48ffffff},new float[]{0,.28f,.52f,.72f,1},Shader.TileMode.CLAMP);
 }
 @Override public void draw(Canvas c){
  if(!managed)theme.track();boolean changed=dark!=ColorOsTheme.dark(context)||blur!=ColorOsTheme.materialBlur(context)||stroke!=ColorOsTheme.materialStroke(context);if(changed)themeChanged();
  if(!blur){paint.setAlpha(opacity);paint.setStyle(Paint.Style.FILL);paint.setShader(null);paint.setColor(opaqueColor(kind,opaquePanel,opaqueControl,ColorOsTheme.accent(context),ColorOsTheme.card(context)));paint.setAlpha(opacity);c.drawRoundRect(opaqueBox,radius,radius,paint);if(!stroke)return;}
  else{
  paint.setAlpha(opacity);paint.setStyle(Paint.Style.FILL);paint.setShader(fill);c.drawRoundRect(box,radius,radius,paint);
  }
  if(!stroke){paint.setShader(null);return;}
  paint.setShader(reflection);c.drawRoundRect(box,radius,radius,paint);
  // ColorOS inactive material has white inner lighting, not a dark inner border.
  if(kind==0){c.save();c.clipPath(clip);paint.setStyle(Paint.Style.STROKE);paint.setShader(innerLight);float spread=Math.min(box.width(),box.height())*.08f;for(int i=12;i>=1;i--){float fraction=i/12f;paint.setAlpha(Math.round(opacity*.12f*(1-fraction)));paint.setStrokeWidth(Math.max(edge,spread*fraction));c.drawRoundRect(box,radius,radius,paint);}c.restore();}
  paint.setAlpha(opacity);paint.setStyle(Paint.Style.STROKE);paint.setStrokeWidth(edge);paint.setShader(rim);c.drawRoundRect(box,radius,radius,paint);paint.setShader(null);
 }
 @Override public void getOutline(Outline outline){outline.setRoundRect(getBounds(),radius);}
 @Override public void setAlpha(int value){opacity=value;invalidateSelf();}
 @Override public void setColorFilter(ColorFilter filter){paint.setColorFilter(filter);invalidateSelf();}
 @Override public int getOpacity(){return PixelFormat.TRANSLUCENT;}
}
