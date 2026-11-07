package dev.edgeflow;

import java.util.ArrayList;

/** Presentation order only. Persistent action IDs and the executor registry never move. */
public final class ActionCatalog {
 public static final class Group {
  public final String title;
  public final String[] ids;
  private Group(String title,String... ids){this.title=title;this.ids=ids;}
 }
 private static final Group[] GROUPS={
  new Group("","none"),
  new Group("导航","back","home","recents","previous_app","close_app","sidebar","assistant","search","menu","browser_forward","scroll_up","scroll_down","keyboard"),
  new Group("屏幕","notifications","quick_settings","screen_off","screenshot","screen_record","one_hand","split_screen","power_menu","rotate_right","rotate_left","system_magnification","screen_magnifier","screen_free_move"),
  new Group("音量与媒体","volume_up","volume_down","volume_mute","media_previous","media_play_pause","media_next","sound_mode","dnd"),
  new Group("系统开关","flashlight","wifi","bluetooth","mobile_data","airplane","nfc","location","rotation","dark_mode","auto_brightness","screen_timeout","color_inversion","battery_saver"),
  new Group("打开系统页面","settings","accessibility_settings","hotspot_settings","eye_comfort_settings"),
  new Group("需要设置","quick_tools","widgets","quick_launcher","task_switcher","virtual_touchpad","navigation_panel","keyboard_shortcuts")
 };
 /** Each caller owns its arrays, so a view cannot reorder another view's catalogue. */
 public static Group[] groups(){Group[] result=new Group[GROUPS.length];for(int i=0;i<result.length;i++)result[i]=new Group(GROUPS[i].title,GROUPS[i].ids.clone());return result;}
 public static String[] orderedIds(){ArrayList<String> result=new ArrayList<>();for(Group group:GROUPS)for(String id:group.ids)result.add(id);return result.toArray(new String[0]);}
 public static String groupTitle(String id){for(Group group:GROUPS)for(String value:group.ids)if(value.equals(id))return group.title;return null;}
 /** Legacy execution IDs (for example app_launcher) remain valid but cannot be newly chosen. */
 public static boolean isSelectable(String id){return groupTitle(id)!=null;}
 public static boolean requiresConfiguration(String id){return destination(id)!=null;}
 public static String configuration(String id){return destination(id);}
 public static String destination(String id){
  if(id==null)return null;
  return switch(id){case "quick_tools"->"tools";case "widgets"->"widgets";case "quick_launcher"->"launcher";case "task_switcher"->"tasks";case "virtual_touchpad"->"touchpad";case "navigation_panel"->"navigation";case "keyboard_shortcuts"->"keyboard";default->null;};
 }
 private ActionCatalog(){}
}
