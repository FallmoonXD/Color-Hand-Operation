package probe;
/** Read-only signature probe for firmware-owned interfaces. */
public final class ApiDump {
 public static void main(String[] args)throws Exception {
  for(String name:args){System.out.println(name);for(java.lang.reflect.Method m:Class.forName(name).getDeclaredMethods())System.out.println(m);}
 }
}
