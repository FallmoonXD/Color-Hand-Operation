package probe;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.drawable.AdaptiveIconDrawable;
import android.graphics.drawable.Drawable;
import android.os.Looper;
import java.io.FileOutputStream;

/** Render installed resources with the device's VectorDrawable implementation. */
public final class IconRender {
 public static void main(String[] args) throws Exception {
  if(args.length!=3)throw new IllegalArgumentException("package output-directory size");
  Looper.prepareMainLooper();
  Class<?> threadClass=Class.forName("android.app.ActivityThread");
  Object thread=threadClass.getMethod("systemMain").invoke(null);
  Context system=(Context)threadClass.getMethod("getSystemContext").invoke(thread);
  Context app=system.createPackageContext(args[0],0);
  Drawable launcher=app.getPackageManager().getApplicationIcon(args[0]);
  int size=Integer.parseInt(args[2]);
  write(launcher,args[1]+"/installed-launcher.png",size);
  if(!(launcher instanceof AdaptiveIconDrawable))throw new IllegalStateException("Not adaptive");
  Drawable mono=((AdaptiveIconDrawable)launcher).getMonochrome();
  if(mono==null)throw new IllegalStateException("Missing monochrome");
  write(mono,args[1]+"/installed-monochrome.png",size);
  System.out.println("Rendered installed adaptive and monochrome resources at "+size);
  System.exit(0);
 }
 private static void write(Drawable drawable,String path,int size)throws Exception {
  Bitmap bitmap=Bitmap.createBitmap(size,size,Bitmap.Config.ARGB_8888);
  drawable.setBounds(0,0,size,size);drawable.draw(new Canvas(bitmap));
  try(FileOutputStream out=new FileOutputStream(path)){bitmap.compress(Bitmap.CompressFormat.PNG,100,out);}
  bitmap.recycle();
 }
}
