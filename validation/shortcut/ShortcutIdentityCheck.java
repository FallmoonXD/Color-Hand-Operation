package dev.edgeflow;

/** Pure-JVM checks for imported shortcut identities and shell-safe round trips. */
public final class ShortcutIdentityCheck {
 private static int checks;
 private static void require(boolean value,String name){if(!value)throw new AssertionError(name);checks++;}
 private static void invalid(String key){require(!ShortcutEntry.valid(key),"reject "+key);}
 public static void main(String[] args){
  String key=ShortcutEntry.create(0,"com.example.app","扫一扫:'\" $HOME `test` $(id)\n");ShortcutEntry entry=ShortcutEntry.parse(key);
  require(entry.id.equals("扫一扫:'\" $HOME `test` $(id)\n"),"Unicode and shell metacharacters round trip");require(entry.packageName.equals("com.example.app")&&entry.user==0,"publisher and profile");require(key.matches("[A-Za-z0-9_:\\-]+"),"serialized key is inert shell text");
  require(ShortcutEntry.parse(ShortcutEntry.create(10,"com.example.app","work-profile")).user==10,"profile identity is preserved");
  invalid(null);invalid("");invalid(key.replace(":1:0:",":2:0:"));invalid(key.replace(":1:0:",":1:00:"));invalid(key.replace(":1:0:",":1:-1:"));invalid(key+":extra");invalid("launcher_shortcut:1:0:YQ:Yg");invalid("launcher_shortcut:1:0:Y29tLmV4YW1wbGU:");invalid("launcher_shortcut:1:0:Y29tLmV4YW1wbGU:!bad");
  boolean tooLong=false;try{ShortcutEntry.create(0,"com.example",new String(new char[1025]));}catch(IllegalArgumentException e){tooLong=true;}require(tooLong,"oversize id rejected");
  System.out.println("PASS "+checks+" shortcut identity assertions");
 }
}
