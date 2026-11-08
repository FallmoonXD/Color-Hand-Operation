package replica;

import android.content.Context;
import android.content.res.Configuration;
import android.graphics.Color;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.InsetDrawable;
import android.util.Log;
import java.util.HashMap;

/** Read installed ColorOS assets without loading code from the source packages. */
public final class SystemIcons {
 private static final String PACKAGE="com.android.systemui";
 private static final HashMap<String,Context> sources=new HashMap<>();
 private static Configuration configuration;
 private static final HashMap<String,Drawable.ConstantState> cache=new HashMap<>();
 private static final String[] TILES={
  "status_bar_qs_wifi_inactive","status_bar_qs_mute_inactive","status_bar_qs_bluetooth_inactive",
  "status_bar_qs_rotation_inactive","com.coloros.floatassistant:menu_drawable_notification_center","status_bar_qs_screenrecord_full_record",
  "status_bar_qs_screenshot_inactive","status_bar_qs_flashlight_inactive","darkmode_off_status_bar",
  "status_bar_qs_lock_inactive","ic_clock_black_24dp","ic_close_white","status_bar_qs_data_inactive",
  "status_bar_qs_airplane_inactive","status_bar_qs_battery_inactive","status_bar_qs_location_inactive",
  "status_bar_qs_dnd_inactive","status_bar_qs_nfc_inactive","status_bar_qs_footer_settings_icon_no_theme",
  "status_bar_qs_brightness_icon_auto"
 };
 public static String tileName(int id,boolean active,int ringer){
  if(id<0||id>=TILES.length)return "ic_close_white";
  if(id==1)return ringer==0?"status_bar_qs_mute_active":ringer==1?"status_bar_qs_vibrate_on":TILES[1];
  if(id==8)return active?"darkmode_on_status_bar":TILES[8];
  if(active&&(id==0||id==2||id==3||id==7||(id>=12&&id<=17)))return TILES[id].replace("_inactive","_active");
  return TILES[id];
 }
 public static Drawable tile(Context c,int id,boolean active,int ringer){return padded(c,tileName(id,active,ringer));}
 /** The former OHO glyphs occupied about 26 of their 41 design units. */
 public static Drawable padded(Context c,String name){
  if(name.equals("com.coloros.floatassistant:menu_drawable_notification_center")){
   // Native path spans y=.969..20.701 in a 24-unit viewport: center=10.835.
   // Move only the glyph down by (12-10.835)/24 * .64 of the tile.
   // Opposite inset changes preserve its scale, hit target and button background.
   float offset=(12f-10.835f)/24f*.64f;
   return new InsetDrawable(raw(c,name),.18f,.18f+offset,.18f,.18f-offset);
  }
  return new InsetDrawable(raw(c,name),.18f);
 }
 public static synchronized Drawable raw(Context c,String name){
  int separator=name.indexOf(':');
  String sourcePackage=separator<0?PACKAGE:name.substring(0,separator);
  String resourceName=separator<0?name:name.substring(separator+1);
  Configuration now=c.getResources().getConfiguration();
  if(configuration==null||!configuration.equals(now)){
   cache.clear();sources.clear();configuration=new Configuration(now);
  }
  Drawable.ConstantState state=cache.get(name);
  if(state!=null){Drawable d=state.newDrawable().mutate();d.setTint(Color.WHITE);return d;}
  if(!sources.containsKey(sourcePackage)){
   Context source=null;
   try{source=c.createPackageContext(sourcePackage,0).createConfigurationContext(now);
    int theme=PACKAGE.equals(sourcePackage)?source.getResources().getIdentifier("Theme.SystemUI","style",sourcePackage):source.getApplicationInfo().theme;
    if(theme!=0)source.setTheme(theme);
   }catch(Exception e){Log.w("ReplicaIcons","Resources unavailable: "+sourcePackage,e);}
   sources.put(sourcePackage,source);
  }
  Context system=sources.get(sourcePackage);
  Drawable result=null;
  if(system!=null)try{
   int id=system.getResources().getIdentifier(resourceName,"drawable",sourcePackage);
   if(id!=0)result=system.getDrawable(id).mutate();
  }catch(Exception e){Log.w("ReplicaIcons","Cannot load "+name,e);}
  if(result==null){
   // Exact exported vector geometry from the reference firmware, for other ROMs.
   int id=c.getResources().getIdentifier("cos_"+resourceName,"drawable",c.getPackageName());
   result=c.getDrawable(id==0?dev.edgeflow.R.drawable.cos_status_bar_qs_footer_settings_icon_no_theme:id).mutate();
   Log.i("ReplicaIcons","fallback="+name);
  }else Log.i("ReplicaIcons","system="+name);
  result.setTint(Color.WHITE);state=result.getConstantState();if(state!=null)cache.put(name,state);
  return result;
 }
 public static Drawable control(Context c,String name){
  String nativeName;
  switch(name){
   case "brightness_icon":nativeName="status_bar_qs_brightness_icon_auto";break;
   case "volume_media":nativeName="status_bar_qs_icon_volume_media";break;
   case "volume_media_mute":nativeName="status_bar_qs_icon_volume_media_mute";break;
   case "player_prev":nativeName="ic_oplus_media_panel_action_pre";break;
   case "player_play":nativeName="ic_oplus_media_panel_action_play";break;
   case "player_pause":nativeName="ic_oplus_media_panel_action_pause";break;
   case "player_next":nativeName="ic_oplus_media_panel_action_next";break;
   case "ic_volume_up":nativeName="ic_expand_less_rounded";break;
   case "ic_volume_down":nativeName="ic_expand_more_rounded";break;
   default:nativeName=name;
  }
  return padded(c,nativeName);
 }
 private SystemIcons(){}
}
