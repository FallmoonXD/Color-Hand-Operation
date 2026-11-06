package probe;
/** Targeted read-only firmware constant inspection. */
public final class FieldDump {
 public static void main(String[] args)throws Exception {
  for(java.lang.reflect.Field f:Class.forName(args[0]).getDeclaredFields())
   if(java.lang.reflect.Modifier.isStatic(f.getModifiers())&&f.getName().contains(args[1])){f.setAccessible(true);System.out.println(f.getName()+"="+f.get(null));}
 }
}
