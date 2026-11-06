package dev.edgeflow;

import android.content.SharedPreferences;
import java.lang.reflect.Proxy;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.regex.*;

/** Tests the presentation contract against the real registry, not a second registry fixture. */
public final class ActionCatalogCheck {
 private static int passed;
 private static void check(boolean condition,String name){if(!condition)throw new AssertionError(name);passed++;System.out.println("PASS "+name);}
 private static List<String> registry(String source,String name){
  Matcher declaration=Pattern.compile("String\\[\\]\\s+"+name+"\\s*=\\s*\\{([^}]*)}").matcher(source);
  if(!declaration.find())throw new AssertionError("Missing production registry "+name);
  ArrayList<String> values=new ArrayList<>();Matcher item=Pattern.compile("\"([^\"]*)\"").matcher(declaration.group(1));while(item.find())values.add(item.group(1));return values;
 }
 private static void catalog(Path project)throws Exception{
  String source=Files.readString(project.resolve("app/src/main/java/dev/edgeflow/ActionExecutor.java"));
  List<String> registered=registry(source,"IDS"),titles=registry(source,"TITLES"),display=Arrays.asList(ActionCatalog.orderedIds());
  check(registered.size()==titles.size(),"actual ID and title registries have matching lengths");
  check(new HashSet<>(registered).size()==registered.size(),"actual registry has no duplicate IDs");
  check(titles.stream().noneMatch(String::isBlank),"all registered actions have display titles");
  HashSet<String> visible=new HashSet<>(registered);visible.remove("app_launcher");
  check(display.size()==visible.size()&&new HashSet<>(display).equals(visible),"catalogue covers every selectable action exactly once");
  check(registered.contains("app_launcher")&&!display.contains("app_launcher")&&!ActionCatalog.isSelectable("app_launcher"),"legacy app launcher remains registered but is no longer selectable");
  check(display.stream().allMatch(ActionCatalog::isSelectable)&&!ActionCatalog.isSelectable(null)&&!ActionCatalog.isSelectable("unregistered"),"selection policy accepts only listed action IDs");
  check(new HashSet<>(display).size()==display.size(),"no action appears in multiple groups");
  check(display.get(0).equals("none"),"none remains the first choice");
  check(!display.equals(registered),"presentation order is independent of executor indices");
  ArrayList<String> flattened=new ArrayList<>();HashSet<String> groupNames=new HashSet<>();boolean configured=false;int destinations=0;
  String main=Files.readString(project.resolve("app/src/main/java/dev/edgeflow/MainActivity.java"));
  int start=main.indexOf("switch(screen){"),end=main.indexOf("private String pageTitle()",start);
  if(start<0||end<0)throw new AssertionError("Unable to inspect Main screen dispatch");String screenDispatch=main.substring(start,end);
  for(ActionCatalog.Group group:ActionCatalog.groups()){
   check(group.ids.length>0&&groupNames.add(group.title),"nonempty unique group: "+group.title);
   for(String id:group.ids){
    flattened.add(id);if(!Objects.equals(group.title,ActionCatalog.groupTitle(id)))throw new AssertionError("Group lookup mismatch: "+id);
    String destination=ActionCatalog.destination(id);
    if(destination!=null){configured=true;destinations++;if(!screenDispatch.contains("case \""+destination+"\":"))throw new AssertionError("Unreachable configuration page: "+id+" -> "+destination);}
    else if(configured)throw new AssertionError("Ordinary action follows configurable actions: "+id);
    if(ActionCatalog.requiresConfiguration(id)!=(destination!=null)||!Objects.equals(destination,ActionCatalog.configuration(id)))throw new AssertionError("Configuration API mismatch: "+id);
   }
  }
  check(flattened.equals(display),"group iteration and flattened iteration agree");
  check(destinations==7,"all seven existing configuration destinations are reachable and form the final suffix");
  check(ActionCatalog.destination(null)==null&&ActionCatalog.destination("unregistered")==null&&ActionCatalog.groupTitle("unregistered")==null,"unknown actions do not acquire a configuration destination");
  check(display.indexOf("back")<display.indexOf("home")&&display.indexOf("home")<display.indexOf("recents"),"common navigation keeps back, home, recents order");
  check(display.indexOf("volume_up")<display.indexOf("volume_down")&&display.indexOf("media_previous")<display.indexOf("media_play_pause")&&display.indexOf("media_play_pause")<display.indexOf("media_next"),"volume and transport controls keep their natural order");
  ActionCatalog.Group[] groups=ActionCatalog.groups();groups[0].ids[0]="corrupted";groups[1]=null;String[] ordered=ActionCatalog.orderedIds();ordered[0]="corrupted";
  check(ActionCatalog.groups()[0].ids[0].equals("none")&&ActionCatalog.groups()[1]!=null&&ActionCatalog.orderedIds()[0].equals("none"),"view mutations cannot change later catalogue instances");
 }
 /** Minimal storage boundary; real ActionTarget implements all selection/configuration behavior. */
 private static final class Store {
  final Map<String,String> values=new HashMap<>();int writes;
  final SharedPreferences prefs=(SharedPreferences)Proxy.newProxyInstance(SharedPreferences.class.getClassLoader(),new Class<?>[]{SharedPreferences.class},(p,m,a)->{
   switch(m.getName()){
    case "getString":return values.getOrDefault((String)a[0],(String)a[1]);
    case "getAll":return new HashMap<>(values);
    case "contains":return values.containsKey(a[0]);
    case "edit":return editor();
    default:throw new UnsupportedOperationException(m.getName());
   }
  });
  SharedPreferences.Editor editor(){Map<String,String> pending=new HashMap<>();return (SharedPreferences.Editor)Proxy.newProxyInstance(SharedPreferences.Editor.class.getClassLoader(),new Class<?>[]{SharedPreferences.Editor.class},(p,m,a)->{
   switch(m.getName()){
    case "putString":pending.put((String)a[0],(String)a[1]);return p;
    case "apply":values.putAll(pending);writes++;return null;
    case "commit":values.putAll(pending);writes++;return true;
    default:throw new UnsupportedOperationException(m.getName());
   }
  });}
 }
 private static void selection(){
  String gesture="action_right_straight_long",other="action_left_up_short",app="app:com.example/com.example.Main",nextApp="app:com.other/com.other.Main",shortcut=ShortcutEntry.create(0,"com.example","new:聊天"),nextShortcut=ShortcutEntry.create(0,"com.example","scan");
  for(String active:new String[]{"back",app,shortcut}){
   Store store=new Store();store.values.put(gesture,active);store.values.put(other,"home");store.values.put(ActionTarget.key(gesture,false),nextApp);store.values.put(ActionTarget.key(gesture,true),nextShortcut);Map<String,String> before=new HashMap<>(store.values);
   ActionTarget.get(store.prefs,gesture,false);ActionTarget.get(store.prefs,gesture,true);
   check(store.writes==0&&store.values.equals(before),"reading choices and cancelling retains preferences for "+ActionExecutorName(active));
   ActionTarget.configure(store.prefs,gesture,false,nextApp,false);
   check(store.values.get(gesture).equals(ActionTarget.matches(active,false)?nextApp:active),"app configuration only updates an already active app action: "+ActionExecutorName(active));
   check(ActionTarget.get(store.prefs,gesture,false).equals(nextApp)&&store.values.get(other).equals("home"),"app configuration retains target and other gesture slots: "+ActionExecutorName(active));
   String beforeShortcut=store.values.get(gesture);ActionTarget.configure(store.prefs,gesture,true,nextShortcut,false);
   check(store.values.get(gesture).equals(ActionTarget.matches(beforeShortcut,true)?nextShortcut:beforeShortcut),"shortcut configuration preserves a different active action: "+ActionExecutorName(active));
  }
  Store store=new Store();store.values.put(gesture,app);ActionTarget.configure(store.prefs,gesture,true,shortcut,true);
  check(store.values.get(gesture).equals(shortcut)&&ActionTarget.get(store.prefs,gesture,false).equals(app),"first shortcut selection preserves the prior app target");
  ActionTarget.select(store.prefs,gesture,"home");check(store.values.get(gesture).equals("home")&&ActionTarget.get(store.prefs,gesture,true).equals(shortcut),"selecting a built-in action retains the saved shortcut");
  ActionTarget.select(store.prefs,gesture,app);check(store.values.get(gesture).equals(app)&&ActionTarget.get(store.prefs,gesture,true).equals(shortcut),"selecting the saved app does not discard the shortcut");
  store.values.put(ActionTarget.key(gesture,false),nextApp);check(ActionTarget.get(store.prefs,gesture,false).equals(app),"active target has priority over an older saved target");
  for(boolean shortcutType:new boolean[]{false,true}){
   Map<String,String> before=new HashMap<>(store.values);int writes=store.writes;boolean rejected=false;
   try{ActionTarget.configure(store.prefs,gesture,shortcutType,shortcutType?app:shortcut,false);}catch(IllegalArgumentException expected){rejected=true;}
   check(rejected&&store.writes==writes&&store.values.equals(before),"wrong target type is rejected before any write: "+shortcutType);
  }
 }
 private static String ActionExecutorName(String action){return action.startsWith("app:")?"app":action.startsWith(ShortcutEntry.PREFIX)?"shortcut":action;}
 public static void main(String[] args)throws Exception{catalog(Path.of(args[0]));selection();System.out.println("Action catalogue/selection assertions passed: "+passed);}
}
