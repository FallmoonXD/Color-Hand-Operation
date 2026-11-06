package android.content;
public final class ComponentName {
 private final String pkg,name;
 public ComponentName(String p,String n){pkg=p;name=n.startsWith(".")?p+n:n;}
 public static ComponentName unflattenFromString(String s){if(s==null)return null;int n=s.indexOf('/');return n<=0||n==s.length()-1?null:new ComponentName(s.substring(0,n),s.substring(n+1));}
 public boolean equals(Object o){return o instanceof ComponentName&&pkg.equals(((ComponentName)o).pkg)&&name.equals(((ComponentName)o).name);}public int hashCode(){return 31*pkg.hashCode()+name.hashCode();}
 public String flattenToString(){return pkg+"/"+name;}public String getPackageName(){return pkg;}public String getClassName(){return name;}
}