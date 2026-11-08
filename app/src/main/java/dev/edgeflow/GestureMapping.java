package dev.edgeflow;

import android.content.SharedPreferences;
import java.util.TreeSet;
import java.util.regex.Pattern;

/** Absence of a scoped key means inherit; an explicit "none" is a real override. */
final class GestureMapping {
    private static final String PREFIX="app_override/";
    private static final Pattern PACKAGE=Pattern.compile("[A-Za-z0-9_]+(?:\\.[A-Za-z0-9_]+)+");
    private static final Pattern SLOT=Pattern.compile("action_(extra2?_)?(left|right)_(up|straight|down)_(short|long)");
    static boolean validPackage(String pkg){return pkg!=null&&pkg.length()<=255&&PACKAGE.matcher(pkg).matches();}
    static String scoped(String pkg,String slot){return PREFIX+pkg+"/"+slot;}
    static boolean isKey(String key){
        if(SLOT.matcher(key).matches())return true;
        return packageOf(key)!=null;
    }
    static String packageOf(String key){
        if(!key.startsWith(PREFIX))return null;
        int at=key.indexOf('/',PREFIX.length());if(at<0)return null;
        String pkg=key.substring(PREFIX.length(),at);
        return validPackage(pkg)&&SLOT.matcher(key.substring(at+1)).matches()?pkg:null;
    }
    static String resolve(SharedPreferences prefs,String pkg,String handle,String direction,boolean longer){
        String slot=GestureHandleConfig.actionKey(handle,direction,longer);
        String fallback=prefs.getInt("schema",1)>=GestureHandleConfig.FACTORY_SCHEMA?GestureHandleConfig.defaultAction(handle,direction,longer):GestureHandleConfig.legacyAction(handle,direction,longer);
        String global=prefs.getString(slot,fallback);
        return validPackage(pkg)?prefs.getString(scoped(pkg,slot),global):global;
    }
    static String global(SharedPreferences prefs,String slot){
        int last=slot.lastIndexOf('_'),direction=slot.lastIndexOf('_',last-1);
        return resolve(prefs,null,slot.substring(7,direction),slot.substring(direction+1,last),slot.endsWith("_long"));
    }
    static TreeSet<String> packages(SharedPreferences prefs){
        TreeSet<String> packages=new TreeSet<>();
        for(String key:prefs.getAll().keySet()){
            String pkg=packageOf(key);if(pkg!=null)packages.add(pkg);
        }
        return packages;
    }
    static void inherit(SharedPreferences prefs,String key){
        if(packageOf(key)==null)return;
        prefs.edit().remove(key).remove(ActionTarget.key(key,false)).remove(ActionTarget.key(key,true)).apply();
    }
    private GestureMapping(){}
}
