package dev.edgeflow;

import android.content.ComponentName;
import android.content.SharedPreferences;
import org.json.JSONException;

/** Keeps each external action's configuration without selecting that action. */
final class ActionTarget {
 static boolean isKey(String key){
  for(String prefix:new String[]{"target_app_","target_shortcut_"})if(key.startsWith(prefix))return GestureMapping.isKey(key.substring(prefix.length()));
  return false;
 }
 static boolean matches(String value,boolean shortcut){return shortcut?ShortcutEntry.valid(value):value!=null&&value.startsWith("app:")&&ComponentName.unflattenFromString(value.substring(4))!=null;}
 static String key(String gesture,boolean shortcut){return "target_"+(shortcut?"shortcut_":"app_")+gesture;}
 static String get(SharedPreferences prefs,String gesture,boolean shortcut){String active=prefs.getString(gesture,"none");if(matches(active,shortcut))return active;String saved=prefs.getString(key(gesture,shortcut),"");return matches(saved,shortcut)?saved:"";}
 static void select(SharedPreferences prefs,String gesture,String target){SharedPreferences.Editor edit=prefs.edit();String active=prefs.getString(gesture,"none");for(boolean shortcut:new boolean[]{false,true})if(matches(active,shortcut))edit.putString(key(gesture,shortcut),active);edit.putString(gesture,target).apply();}
 static void configure(SharedPreferences prefs,String gesture,boolean shortcut,String target,boolean select){
  if(!matches(target,shortcut))throw new IllegalArgumentException("Invalid external action target");
  SharedPreferences.Editor edit=prefs.edit().putString(key(gesture,shortcut),target);
  if(select){String active=prefs.getString(gesture,"none");if(matches(active,!shortcut))edit.putString(key(gesture,!shortcut),active);}
  if(select||matches(prefs.getString(gesture,"none"),shortcut))edit.putString(gesture,target);
  edit.apply();
 }
 static String validate(String key,Object value)throws JSONException{if(!(value instanceof String)||!matches((String)value,key.startsWith("target_shortcut_")))throw new JSONException("无效的应用动作配置："+key);return (String)value;}
 private ActionTarget(){}
}
