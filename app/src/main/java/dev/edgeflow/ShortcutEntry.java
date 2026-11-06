package dev.edgeflow;

import java.nio.charset.StandardCharsets;
import java.util.Base64;

/** Portable identity of a system-managed shortcut. No serialized launch Intent is substituted. */
public final class ShortcutEntry {
 public static final String PREFIX="launcher_shortcut:";
 public final int user; public final String packageName,id;
 private ShortcutEntry(int u,String p,String i){user=u;packageName=p;id=i;}
 public static String create(int user,String pkg,String id){
  if(user<0||user>21474||pkg==null||!pkg.matches("[A-Za-z0-9_]+(\\.[A-Za-z0-9_]+)+")||pkg.length()>255||id==null||id.isEmpty()||id.length()>1024)throw new IllegalArgumentException("Invalid shortcut identity");
  String key=PREFIX+"1:"+user+":"+encode(pkg)+":"+encode(id);if(key.length()>2048)throw new IllegalArgumentException("Shortcut identity too long");return key;
 }
 public static ShortcutEntry parse(String key){
  if(key==null||key.length()>2048||!key.startsWith(PREFIX))throw new IllegalArgumentException("Invalid shortcut key");String[] parts=key.substring(PREFIX.length()).split(":",-1);
  if(parts.length!=4||!parts[0].equals("1"))throw new IllegalArgumentException("Unknown shortcut version");int u=Integer.parseInt(parts[1]);String p=decode(parts[2]),id=decode(parts[3]);if(!create(u,p,id).equals(key))throw new IllegalArgumentException("Noncanonical shortcut key");return new ShortcutEntry(u,p,id);
 }
 public static boolean valid(String key){try{parse(key);return true;}catch(RuntimeException e){return false;}}
 private static String encode(String s){return Base64.getUrlEncoder().withoutPadding().encodeToString(s.getBytes(StandardCharsets.UTF_8));}
 private static String decode(String s){return new String(Base64.getUrlDecoder().decode(s),StandardCharsets.UTF_8);}
 private ShortcutEntry(){throw new AssertionError();}
}
