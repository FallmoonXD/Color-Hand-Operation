package dev.edgeflow;
import android.content.*;import android.appwidget.*;import org.json.*;import java.io.*;import java.nio.file.*;import java.util.*;
public final class ConfigArchiveHarness {
 static int passed;static Path dir;
 static class Memory extends ContextWrapper {
  final Map<String,Prefs> all=new HashMap<>();final File files;
  Memory(String name)throws Exception{super(null);files=Files.createDirectory(dir.resolve(name)).toFile();}
  public SharedPreferences getSharedPreferences(String n,int mode){return all.computeIfAbsent(n,k->new Prefs());}
  public File getFilesDir(){return files;}public Context getApplicationContext(){return this;}
  Prefs prefs(String n){return (Prefs)getSharedPreferences(n,0);}
 }
 static class Prefs implements SharedPreferences {
  Map<String,Object> values=new HashMap<>();boolean failNext;int commits;
  public Map<String,?> getAll(){return new HashMap<>(values);}public boolean contains(String k){return values.containsKey(k);}
  public String getString(String k,String d){return values.containsKey(k)?(String)values.get(k):d;}
  @SuppressWarnings("unchecked") public Set<String> getStringSet(String k,Set<String>d){return values.containsKey(k)?(Set<String>)values.get(k):d;}
  public int getInt(String k,int d){return values.containsKey(k)?(Integer)values.get(k):d;}public long getLong(String k,long d){return values.containsKey(k)?(Long)values.get(k):d;}
  public float getFloat(String k,float d){return values.containsKey(k)?(Float)values.get(k):d;}public boolean getBoolean(String k,boolean d){return values.containsKey(k)?(Boolean)values.get(k):d;}
  public void registerOnSharedPreferenceChangeListener(OnSharedPreferenceChangeListener l){}public void unregisterOnSharedPreferenceChangeListener(OnSharedPreferenceChangeListener l){}
  public Editor edit(){return new Editor(){Map<String,Object> updates=new HashMap<>();Set<String> remove=new HashSet<>();boolean clear;
   public Editor putString(String k,String v){updates.put(k,v);return this;}public Editor putStringSet(String k,Set<String>v){updates.put(k,new HashSet<>(v));return this;}public Editor putInt(String k,int v){updates.put(k,v);return this;}public Editor putLong(String k,long v){updates.put(k,v);return this;}public Editor putFloat(String k,float v){updates.put(k,v);return this;}public Editor putBoolean(String k,boolean v){updates.put(k,v);return this;}public Editor remove(String k){remove.add(k);return this;}public Editor clear(){clear=true;return this;}public boolean commit(){commits++;if(clear)values.clear();for(String k:remove)values.remove(k);values.putAll(updates);if(failNext){failNext=false;return false;}return true;}public void apply(){commit();}
  };}
 }
 interface Task{void run()throws Exception;}
 static void check(boolean ok,String name){if(!ok)throw new AssertionError(name);passed++;System.out.println("PASS "+name);}
 static String state(Memory c)throws Exception{return canonical(ConfigArchive.export(c));}
 static String canonical(Object o){if(o instanceof JSONObject){JSONObject j=(JSONObject)o;List<String> keys=new ArrayList<>(j.keySet());Collections.sort(keys);StringBuilder s=new StringBuilder("{");for(String k:keys)s.append(k).append(':').append(canonical(j.get(k))).append(',');return s.append('}').toString();}if(o instanceof JSONArray){StringBuilder s=new StringBuilder("[");for(Object v:(JSONArray)o)s.append(canonical(v)).append(',');return s.append(']').toString();}return String.valueOf(o);}
 static void rejectUnchanged(Memory c,JSONObject archive,String name)throws Exception{String before=state(c);boolean failed=false;try{ConfigArchive.restore(c,archive);}catch(Exception expected){failed=true;}check(failed&&before.equals(state(c)),name);}
 static JSONObject copy(JSONObject o){return new JSONObject(o.toString());}
 static JSONObject layout(){return new JSONObject().put("version",2).put("recent_page",true).put("items",new JSONArray().put("folder:folder-1").put("action:back")).put("folders",new JSONObject().put("folder-1",new JSONObject().put("name","常用").put("items",new JSONArray().put("app:com.example/com.example.Main"))));}
 static JSONObject archive(int version){JSONObject o=new JSONObject().put("version",version).put("preferences",new JSONObject().put("schema",1).put("handle_geometry_version",2).put("enabled",false).put("trigger_dp",45.5).put("long_trigger_dp",90).put("navigation_vertical",true).put("disabled_apps",new JSONArray().put("com.example.hidden"))).put("tools",new JSONObject().put("version",1).put("items",new JSONArray().put(new JSONObject().put("key","0")).put(new JSONObject().put("key","app:com.example/com.example.Main").put("label","Example"))));if(version==2)o.put("launcher",new JSONObject().put("layout_v2",layout().toString()).put("columns",5).put("page",1).put("recent_limit",20)).put("shortcuts",new JSONObject().put("app:com.example",new JSONArray().put(new JSONObject().put("name","Copy").put("keys","CTRL+C")).toString()));if(version==2)o.getJSONObject("preferences").put("hide_home",true).put("hide_notifications",true).put("quick_action_short",true).put("quick_action_long",false).put("vibration_strength",192).put("long_immediate",false);return o;}
 static Memory seeded(String name)throws Exception{Memory c=new Memory(name);AppPrefs.defaults(c);c.prefs("edgeflow_launcher").edit().putInt("columns",3).putInt("recent_limit",5).apply();c.prefs("edgeflow_shortcuts").edit().putString("app:com.old","[]").apply();c.prefs("replica_widgets").edit().putInt("pages",2).putInt("page",1).putInt("height_v4",400).putString("layout_v3","[{\"id\":93,\"page\":1,\"x\":0,\"y\":0,\"w\":4,\"h\":2,\"color\":0}]").apply();replica.ToolsConfig.save(c,replica.ToolsConfig.defaults());return c;}
 static void legacyOpacityAndShortcutNames() throws Exception {
  Memory old = seeded("legacy-opacity"), restored = seeded("restored-opacity");
  SharedPreferences prefs = AppPrefs.get(old);
  prefs.edit().putFloat("handle_opacity", .5f).commit();
  check(AppPrefs.handleOpacity(prefs) == 50 && (AppPrefs.visibleHandleColor(prefs, "left") >>> 24) == 128, "legacy picker and handle agree");
  ConfigArchive.restore(restored, new JSONObject(ConfigArchive.export(old).toString()));
  check(AppPrefs.get(restored).getInt("handle_opacity", -1) == 50, "fractional opacity survives full export/restore");
  AppPrefs.importPrefs(restored, new JSONObject("{\"handle_opacity\":0.25}"));
  check(AppPrefs.handleOpacity(AppPrefs.get(restored)) == 25, "legacy fraction from JSON text imports as percent");
  prefs.edit().putFloat("handle_opacity", 1f).commit();
  check(AppPrefs.export(old).getInt("handle_opacity") == 100, "legacy floating one exports as fully visible");
  prefs.edit().putInt("handle_opacity", 1).commit();
  check(AppPrefs.export(old).getInt("handle_opacity") == 1, "modern integer one remains one percent");
  String oldName = "长".repeat(257);
  check(KeyboardShortcutsOverlay.nameError("字".repeat(256)) == null && KeyboardShortcutsOverlay.nameError(oldName) != null, "shortcut editor name boundary");
  String saved = new JSONArray().put(new JSONObject().put("name",oldName).put("keys","CTRL+A")).toString();
  old.prefs("edgeflow_shortcuts").edit().putString("app:com.example",saved).commit();
  ConfigArchive.restore(restored, new JSONObject(ConfigArchive.export(old).toString()));
  check(saved.equals(restored.prefs("edgeflow_shortcuts").getString("app:com.example",null)), "legacy shortcut name roundtrips in full without preventing backup");
 }
 static void quickToolActionAccent() throws Exception {
  String key=AppPrefs.QUICK_TOOLS_ACTION_ACCENT;
  Memory original=seeded("tool-accent"),restored=seeded("tool-accent-restore");
  check(!AppPrefs.get(original).getBoolean(key,false),"existing settings default to glass commands");
  for(boolean enabled:new boolean[]{true,false}){
   AppPrefs.get(original).edit().putBoolean(key,enabled).apply();
   ConfigArchive.restore(restored,ConfigArchive.export(original));
   check(AppPrefs.get(restored).contains(key)&&AppPrefs.get(restored).getBoolean(key,!enabled)==enabled,
     "quick tool action accent round trips: "+enabled);
  }
  AppPrefs.get(restored).edit().putBoolean(key,true).apply();
  ConfigArchive.restore(restored,archive(2));
  check(!AppPrefs.get(restored).getBoolean(key,false),"restoring a full older backup restores default glass");
  JSONObject invalid=archive(2);invalid.getJSONObject("preferences").put(key,"true");
  rejectUnchanged(restored,invalid,"invalid quick tool action accent cannot partially import");
  for(int i=0;i<replica.ToolsConfig.TITLES.length;i++){
   boolean expected=Arrays.asList(4,5,6,9,10,11,18).contains(i);
   check(replica.ToolsConfig.builtin(i).isOneShot()==expected,"one-shot appearance eligibility: "+i);
  }
  check(!new replica.ToolsConfig.Item("app:com.example/.Main","Example").isOneShot(),"application tiles keep their own icons and glass");
 }
 static void appMappings()throws Exception{
  Memory c=seeded("app-mapping");SharedPreferences p=c.prefs("edgeflow");
  String slot="action_left_straight_short",key=GestureMapping.scoped("com.sample.reader",slot);
  p.edit().putString(slot,"back").apply();
  check(GestureMapping.resolve(p,"com.sample.reader","left","straight",false).equals("back"),"missing override inherits global action");
  ActionTarget.select(p,key,"none");
  check(GestureMapping.resolve(p,"com.sample.reader","left","straight",false).equals("none")&&GestureMapping.resolve(p,"com.other.app","left","straight",false).equals("back"),"explicit none is scoped to one package and slot");
  ActionTarget.configure(p,key,false,"app:com.example/com.example.Reader",false);
  ActionTarget.select(p,key,"folder:folder-1");
  Memory target=seeded("app-mapping-restored");ConfigArchive.restore(target,ConfigArchive.export(c));
  SharedPreferences restored=target.prefs("edgeflow");
  check(restored.getString(key,"").equals("folder:folder-1")&&ActionTarget.get(restored,key,false).startsWith("app:"),"archive preserves a scoped folder and configured external target");
  GestureMapping.inherit(restored,key);
  check(!restored.contains(key)&&!restored.contains(ActionTarget.key(key,false))&&GestureMapping.resolve(restored,"com.sample.reader","left","straight",false).equals("back"),"inherit removes only that scoped slot and its remembered target");
  String importedFolder="folder:旧版/常用";
  check(importedFolder.equals(AppPrefs.validate(new JSONObject().put(key,importedFolder)).getString(key)),"opaque folder IDs from an existing layout remain assignable");
  boolean rejected=false;try{AppPrefs.validate(new JSONObject().put(key,"folder:"));}catch(JSONException invalid){rejected=true;}
  check(rejected,"malformed folder target rejected before import");
  check(!GestureMapping.isKey("app_override/com.sample.reader/action_left_bad_long")&&!GestureMapping.isKey("app_override//"+slot),"invalid package and direction rejected");
  JSONArray tasks=new JSONArray().put(new JSONObject().put("taskId",9).put("component","com.test.reader/.First"))
   .put(new JSONObject().put("taskId",7).put("component","com.test.reader/.Second"))
   .put(new JSONObject().put("taskId",3).put("component","com.test.map/.Main"));
  java.util.List<String> strip=RecentTasksBridge.launcherEntries(tasks,6);
  check(strip.size()==2&&strip.get(0).startsWith("recent:9:")&&strip.get(1).startsWith("recent:3:")&&tasks.length()==3,
   "recent applications deduplicate in task order and preserve the full task list");
 }
 public static void main(String[] args)throws Exception{
  dir=Files.createTempDirectory("edgeflow-config-test-");AppWidgetProviderInfo provider=new AppWidgetProviderInfo();provider.provider=new ComponentName("com.example.widget","com.example.widget.Provider");AppWidgetManager.INSTANCE.providers.put(93,provider);appMappings();
  legacyOpacityAndShortcutNames();
  quickToolActionAccent();
  factorySettings();
  toolAnimations();
  releaseUpdateChecks();
  widgetGridArchives();
  Memory profileArchive=seeded("profile-export");provider.profile=new android.os.UserHandle(10);
  check(ConfigArchive.export(profileArchive).getJSONObject("widgets").getJSONArray("items").getJSONObject(0).getBoolean("requiresProfileSelection"),"work profile widget export requires explicit destination profile selection");
  provider.profile=new android.os.UserHandle(0);
  check(!ConfigArchive.export(profileArchive).getJSONObject("widgets").getJSONArray("items").getJSONObject(0).has("requiresProfileSelection"),"personal profile widget export preserves legacy migration contract");
  Memory v1=seeded("v1");Map<String,?> launcherBefore=v1.prefs("edgeflow_launcher").getAll(),shortcutsBefore=v1.prefs("edgeflow_shortcuts").getAll();ConfigArchive.restore(v1,archive(1));check(!AppPrefs.get(v1).getBoolean("enabled",true)&&AppPrefs.get(v1).getFloat("trigger_dp",0)==45.5f,"v1 imports typed preferences");check(launcherBefore.equals(v1.prefs("edgeflow_launcher").getAll())&&shortcutsBefore.equals(v1.prefs("edgeflow_shortcuts").getAll()),"v1 preserves newer panel settings");check(new File(v1.files,"tools-layout-v1.json").isFile()&&replica.ToolsConfig.load(v1).size()==2,"tools use actual tools-layout-v1.json path");
  Memory v2=seeded("v2");Map<String,?> widgetsBefore=v2.prefs("replica_widgets").getAll();ConfigArchive.restore(v2,archive(2));JSONObject exported=ConfigArchive.export(v2);check(v2.prefs("edgeflow_launcher").getInt("columns",0)==5&&v2.prefs("edgeflow_launcher").getInt("recent_limit",0)==20,"v2 restores launcher and task switcher settings");check(v2.prefs("edgeflow_shortcuts").contains("app:com.example")&&!v2.prefs("edgeflow_shortcuts").contains("app:com.old"),"v2 replaces shortcuts namespace");JSONObject widget=exported.getJSONObject("widgets").getJSONArray("items").getJSONObject(0);check(widget.getString("component").equals("com.example.widget/com.example.widget.Provider")&&widget.getInt("sourceId")==93&&!widget.has("id"),"widget export maps provider and excludes live ID field");check(exported.getJSONObject("widgets").getInt("height")==400&&exported.getJSONObject("widgets").getInt("pages")==2,"widget export reads replica_widgets/layout_v3 and height_v4");
  Memory target=seeded("target");target.prefs("edgeflow").edit().putFloat("left_handle_start_fraction",.8f).putFloat("left_handle_end_fraction",.9f).apply();ConfigArchive.restore(target,new JSONObject(exported.toString()));check(!target.prefs("edgeflow").contains("left_handle_start_fraction"),"v2 clears target-only gesture overrides");check(state(v2).equals(state(target)),"v2 serialize-import-export roundtrip");check(widgetsBefore.equals(target.prefs("replica_widgets").getAll()),"portable import never changes installed widget IDs");
  for(String store:new String[]{"edgeflow","edgeflow_launcher","edgeflow_shortcuts"}){Memory failure=seeded("failure-"+store);String before=state(failure);failure.prefs(store).failNext=true;boolean failed=false;try{ConfigArchive.restore(failure,archive(2));}catch(Exception e){failed=true;}check(failed&&before.equals(state(failure)),"rollback after "+store+" commit failure (memory already changed)");}
  Memory toolFailure=seeded("failure-tools");String before=state(toolFailure);android.util.AtomicFile.failNextWrite=true;boolean failed=false;try{ConfigArchive.restore(toolFailure,archive(2));}catch(Exception e){failed=true;}check(failed&&before.equals(state(toolFailure)),"rollback after tools file write failure");
  Memory invalid=seeded("invalid");JSONObject x=archive(2);x.getJSONObject("preferences").put("enabled","yes");rejectUnchanged(invalid,x,"reject wrong preference type without mutation");x=archive(2);x.getJSONObject("launcher").put("columns",1);rejectUnchanged(invalid,x,"reject launcher range without mutation");x=archive(2);JSONObject badLayout=layout();badLayout.getJSONObject("folders").remove("folder-1");x.getJSONObject("launcher").put("layout_v2",badLayout.toString());rejectUnchanged(invalid,x,"reject dangling folder reference");x=archive(2);x.getJSONObject("shortcuts").put("app:com.example","[{\"name\":\"Bad\",\"keys\":\"CTRL+CTRL\"}]");rejectUnchanged(invalid,x,"reject duplicate shortcut keys");x=archive(2);x.getJSONObject("tools").getJSONArray("items").put(new JSONObject().put("key","0"));rejectUnchanged(invalid,x,"reject duplicate quick tools");x=archive(2);x.getJSONObject("preferences").put("trigger_dp",90).put("long_trigger_dp",45);rejectUnchanged(invalid,x,"reject inconsistent gesture distances");x=archive(2).put("unknown",true);rejectUnchanged(invalid,x,"reject unknown archive section");x=archive(2);x.getJSONObject("preferences").put("excluded_packages","x".repeat(1024*1024));rejectUnchanged(invalid,x,"reject archive above 1 MiB");x=archive(2);x.getJSONObject("launcher").put("columns",5.0);ConfigArchive.restore(invalid,x);check(invalid.prefs("edgeflow_launcher").getInt("columns",0)==5,"integral JSON double normalized to int storage");
  AppWidgetManager.INSTANCE.providers.clear();check(ConfigArchive.export(v2).getJSONObject("widgets").getJSONArray("items").getJSONObject(0).getBoolean("unavailable"),"missing widget provider exported explicitly");
  settingsTests();
  hapticAngleSettingsTests();
  externalActionTargetTests();
  legacyLauncherActionTests();
  extendedSettingsTests();
  shortcutRegressionTests();
  handleSettingsTests();
  safeAreaMigrationTests();
  handlePreviewTests();
  handlePreviewOwnerTests();
  handleColorTests();
  widgetEditTests();
  migrationTests(provider);
  System.out.println("RESULT "+passed+" passed; data "+dir);
 }
 static void safeAreaMigrationTests()throws Exception{
  GestureHandleConfig.Area area=GestureHandleConfig.safeArea(1264,2780,0,140,0,154);
  Memory c=seeded("safe-area-defaults");Prefs p=c.prefs("edgeflow");check(p.getAll().get(HandleSettings.VERSION_KEY) instanceof Integer&&p.getInt(HandleSettings.VERSION_KEY,0)==2,"fresh defaults explicitly use safe-area version2 integer");
  p.edit().remove(HandleSettings.VERSION_KEY).putBoolean("extra_left_enabled",false).putFloat("left_handle_width_dp",20.284f).putFloat("right_handle_width_dp",20.284f).putFloat("left_handle_start_fraction",.04987502f).putFloat("left_handle_end_fraction",.950125f).putFloat("right_handle_start_fraction",.04987502f).putFloat("right_handle_end_fraction",.950125f).putString("action_left_up_short","screenshot").putInt("left_color",0xff123456).apply();
  int commits=p.commits;check(HandleSettings.migrate(p,area)&&p.commits==commits+1,"legacy geometry migrates in one preferences commit");
  HandleSettings.Geometry left=HandleSettings.read(p,"left"),right=HandleSettings.read(p,"right");check(left.start==0&&left.end==1&&right.start==0&&right.end==1&&left.width==20.284f,"actual old139..2642 region becomes exact safe140..2626 at same width");
  check(p.getString("action_left_up_short","").equals("screenshot")&&p.getInt("left_color",0)==0xff123456&&!p.getBoolean("extra_left_enabled",true),"migration preserves actions colors and disabled extras");
  check(!p.contains("handle_start_fraction")&&!p.contains("handle_end_fraction")&&p.getInt(HandleSettings.VERSION_KEY,0)==2,"obsolete shared coordinate fallbacks removed after six effective spans captured");
  Memory source=seeded("landscape-width");AppPrefs.importPrefs(source,new JSONObject().put("left_landscape_handle_width_dp",8).put("extra2_right_landscape_handle_width_dp",12));
  JSONObject backup=ConfigArchive.export(source);Memory dest=seeded("landscape-width-roundtrip");ConfigArchive.restore(dest,backup);
  check(HandleSettings.read(dest.prefs("edgeflow"),"left").landscapeWidth==8&&HandleSettings.read(dest.prefs("edgeflow"),"extra2_right").landscapeWidth==12,"landscape overrides survive full backup/restore");
  backup.getJSONObject("preferences").remove("left_landscape_handle_width_dp");ConfigArchive.restore(dest,backup);
  check(!dest.prefs("edgeflow").contains("left_landscape_handle_width_dp"),"older full backup restores inheritance");
  backup.getJSONObject("preferences").put("left_landscape_handle_width_dp",31);rejectUnchanged(dest,backup,"invalid landscape width rejected atomically");
  Map<String,?> migrated=p.getAll();commits=p.commits;check(HandleSettings.migrate(p,area)&&p.commits==commits&&migrated.equals(p.getAll()),"repeated migration is an exact no-op");
  Prefs aliases=new Prefs();aliases.edit().putFloat("handle_center_fraction",.5f).putFloat("handle_height_fraction",.6f).putFloat("left_handle_start_fraction",.8f).putFloat("left_handle_end_fraction",.95f).putFloat("right_handle_start_fraction",0f).putFloat("right_handle_end_fraction",1f).apply();HandleSettings.migrate(aliases,area);
  left=HandleSettings.read(aliases,"left");right=HandleSettings.read(aliases,"right");float[] expected=GestureHandleConfig.migrateFractions(.2f,.8f,area);check(Math.abs(left.start-expected[0])<.000001&&Math.abs(right.end-expected[1])<.000001&&left.start==right.start&&left.end==right.end,"old center/height override is materialized for both sides before removal");
  check(!aliases.contains("handle_center_fraction")&&!aliases.contains("handle_height_fraction"),"legacy aliases cannot reapply after migration");
  Prefs concurrent=new Prefs();ArrayList<Thread> threads=new ArrayList<>();for(int i=0;i<6;i++){Thread t=new Thread(()->HandleSettings.migrate(concurrent,area));threads.add(t);t.start();}for(Thread t:threads)t.join();check(concurrent.commits==1&&concurrent.getInt(HandleSettings.VERSION_KEY,0)==2,"concurrent initializers commit migration once");
  Memory landscape=new Memory("safe-area-landscape");landscape.prefs("edgeflow").edit().putInt("schema",1).putFloat("handle_start_fraction",.2f).putFloat("handle_end_fraction",.8f).apply();HandleDisplayArea.display=GestureHandleConfig.safeArea(2780,1264,140,0,154,0);AppPrefs.defaults(landscape);HandleDisplayArea.display=area;left=HandleSettings.read(landscape.prefs("edgeflow"),"left");check(Math.abs(left.start-expected[0])<.000001&&Math.abs(left.end-expected[1])<.000001,"first landscape initialization uses portrait reference instead of short display height");
  JSONObject legacy=archive(2);legacy.getJSONObject("preferences").remove(HandleSettings.VERSION_KEY);legacy.getJSONObject("preferences").put("handle_start_fraction",.04987502).put("handle_end_fraction",.950125).put("handle_width_dp",20.284);Memory restored=seeded("safe-area-legacy-restore");ConfigArchive.restore(restored,legacy);check(HandleSettings.read(restored.prefs("edgeflow"),"left").start==0&&HandleSettings.read(restored.prefs("edgeflow"),"right").end==1,"unversioned old full backup converts on restore");
  JSONObject explicit=copy(legacy);explicit.getJSONObject("preferences").put(HandleSettings.VERSION_KEY,1);ConfigArchive.restore(restored,explicit);check(restored.prefs("edgeflow").getInt(HandleSettings.VERSION_KEY,0)==2,"explicit version1 backup also normalizes to version2");
  Prefs target=restored.prefs("edgeflow");HandleSettings.Geometry unchanged=HandleSettings.read(target,"right");int reads=HandleDisplayArea.reads;
  AppPrefs.importPrefs(restored,new JSONObject().put("action_left_straight_short","home"));check(HandleDisplayArea.reads==reads&&HandleSettings.read(target,"right").start==unchanged.start&&HandleSettings.read(target,"right").end==unchanged.end&&target.getInt(HandleSettings.VERSION_KEY,0)==2,"unversioned action-only partial import never reconverts current geometry");
  AppPrefs.importPrefs(restored,new JSONObject().put("left_handle_start_fraction",.2).put("left_handle_end_fraction",.4));left=HandleSettings.read(target,"left");float[] partial=GestureHandleConfig.migrateFractions(.2f,.4f,area);check(Math.abs(left.start-partial[0])<.000001&&Math.abs(left.end-partial[1])<.000001&&HandleSettings.read(target,"right").start==unchanged.start&&HandleSettings.read(target,"right").end==unchanged.end,"old partial geometry converts only the affected handle");
  reads=HandleDisplayArea.reads;AppPrefs.importPrefs(restored,new JSONObject().put(HandleSettings.VERSION_KEY,2).put("left_handle_start_fraction",.17).put("left_handle_end_fraction",.53));left=HandleSettings.read(target,"left");check(left.start==.17f&&left.end==.53f&&HandleDisplayArea.reads==reads,"current version2 partial geometry preserves exact fractions without measurement");
  JSONObject exported=ConfigArchive.export(restored);Memory roundtrip=seeded("safe-area-roundtrip");ConfigArchive.restore(roundtrip,exported);check(HandleSettings.read(roundtrip.prefs("edgeflow"),"left").start==.17f&&roundtrip.prefs("edgeflow").getAll().get(HandleSettings.VERSION_KEY) instanceof Integer,"version2 geometry and integer marker survive export restore");
  for(Object invalid:new Object[]{0,3,1.5,"2"}){JSONObject bad=copy(exported);bad.getJSONObject("preferences").put(HandleSettings.VERSION_KEY,invalid);rejectUnchanged(restored,bad,"reject invalid coordinate-space version "+invalid);}
  Memory fail=seeded("safe-area-rollback");String before=state(fail);fail.prefs("edgeflow").failNext=true;boolean failed=false;try{ConfigArchive.restore(fail,legacy);}catch(Exception error){failed=true;}check(failed&&state(fail).equals(before),"old-backup conversion rolls back alongside all archive stores on commit failure");
  before=state(restored);target.failNext=true;failed=false;try{AppPrefs.importPrefs(restored,new JSONObject().put("left_handle_start_fraction",.2).put("left_handle_end_fraction",.4));}catch(Exception error){failed=true;}check(failed&&state(restored).equals(before),"partial geometry import rolls back on failed atomic write");
  JSONObject tiny=copy(legacy);tiny.getJSONObject("preferences").put("handle_start_fraction",0).put("handle_end_fraction",.05);ConfigArchive.restore(restored,tiny);left=HandleSettings.read(target,"left");int[] b=GestureHandleConfig.bounds("left",left.width,left.start,left.end,3.5f,area);check(left.start==0&&left.end>=.05f-1e-6f&&b[1]==140&&b[1]+b[3]<=2626,"bar-only legacy span expands to minimum region inside safe bounds");
  JSONObject bad=copy(legacy);bad.getJSONObject("preferences").put("handle_start_fraction",.6).put("handle_end_fraction",.61);rejectUnchanged(restored,bad,"invalid old import span is rejected before migration can clamp it");
  Memory single=seeded("safe-area-single-field");Prefs singlePrefs=single.prefs("edgeflow");singlePrefs.edit().remove(HandleSettings.VERSION_KEY).putFloat("left_handle_start_fraction",.65f).putFloat("left_handle_end_fraction",.85f).putFloat("right_handle_start_fraction",.2f).putFloat("right_handle_end_fraction",.5f).apply();HandleSettings.migrate(singlePrefs,area);
  AppPrefs.importPrefs(single,new JSONObject().put("handle_height_fraction",.3));left=HandleSettings.read(singlePrefs,"left");right=HandleSettings.read(singlePrefs,"right");float[] expectedLeft=GestureHandleConfig.migrateFractions(.6f,.9f,area),expectedRight=GestureHandleConfig.migrateFractions(.2f,.5f,area);check(Math.abs(left.start-expectedLeft[0])<.001&&Math.abs(left.end-expectedLeft[1])<.001&&Math.abs(right.start-expectedRight[0])<.001&&Math.abs(right.end-expectedRight[1])<.001,"height-only legacy partial import retains each side's effective center");
  singlePrefs.edit().remove(HandleSettings.VERSION_KEY).putFloat("left_handle_start_fraction",.65f).putFloat("left_handle_end_fraction",.85f).putFloat("right_handle_start_fraction",.2f).putFloat("right_handle_end_fraction",.5f).apply();HandleSettings.migrate(singlePrefs,area);AppPrefs.importPrefs(single,new JSONObject().put("handle_center_fraction",.5));left=HandleSettings.read(singlePrefs,"left");right=HandleSettings.read(singlePrefs,"right");expectedLeft=GestureHandleConfig.migrateFractions(.4f,.6f,area);expectedRight=GestureHandleConfig.migrateFractions(.35f,.65f,area);check(Math.abs(left.start-expectedLeft[0])<.001&&Math.abs(left.end-expectedLeft[1])<.001&&Math.abs(right.start-expectedRight[0])<.001&&Math.abs(right.end-expectedRight[1])<.001,"center-only legacy partial import retains each side's effective height");
  check(!singlePrefs.contains("handle_center_fraction")&&!singlePrefs.contains("handle_height_fraction"),"partial alias edits finish as independent version2 spans");
 }
 static void handlePreviewOwnerTests(){
  HandlePreviewOwner ownership=new HandlePreviewOwner();Object oldActivity=new Object(),newActivity=new Object();
  check(!ownership.release(oldActivity)&&!ownership.release(null),"preview cleanup before first show is a no-op");
  ownership.replace(oldActivity);ownership.replace(newActivity);
  check(!ownership.release(oldActivity),"old Activity stop cannot clear its successor preview");
  check(ownership.release(newActivity)&&!ownership.release(newActivity),"current Activity cleanup succeeds exactly once");
  ownership.replace(oldActivity);ArrayDeque<Runnable> main=new ArrayDeque<>();final int[] removed={0};
  main.add(()->ownership.replace(newActivity));main.add(()->{if(ownership.release(oldActivity))removed[0]++;});while(!main.isEmpty())main.remove().run();
  check(removed[0]==0&&ownership.release(newActivity),"queued old cleanup checks ownership at execution after a new resume");
  ownership.replace(oldActivity);main.add(()->{if(ownership.release(oldActivity))removed[0]++;});main.add(()->ownership.replace(newActivity));while(!main.isEmpty())main.remove().run();
  check(removed[0]==1&&ownership.release(newActivity),"old cleanup before new resume permits normal replacement");
  Object equalA=new String("same"),equalB=new String("same");ownership.replace(equalA);
  check(!ownership.release(equalB)&&ownership.release(equalA),"preview ownership uses instance identity rather than token equality");
  ownership.replace(newActivity);ownership.clear();
  check(!ownership.release(newActivity),"screen-off and disconnect forced cleanup reset ownership");
  ownership.replace(newActivity);ownership.replace(newActivity);
  check(ownership.release(newActivity),"draft refresh by the same Activity retains its ownership");
  ownership.replace(HandlePreviewOwner.TEMPORARY);
  check(!ownership.release(oldActivity)&&ownership.release(HandlePreviewOwner.TEMPORARY),"legacy temporary previews retain their own end or timeout cleanup");
  ownership.replace(HandlePreviewOwner.TEMPORARY);ownership.replace(newActivity);
  check(!ownership.release(HandlePreviewOwner.TEMPORARY)&&ownership.release(newActivity),"a stale temporary timeout cannot delete later settings previews");
  ownership.replace(oldActivity);ownership.replace(HandlePreviewOwner.TEMPORARY);
  check(!ownership.release(oldActivity)&&ownership.release(HandlePreviewOwner.TEMPORARY),"a later temporary preview is not removed by stale settings cleanup");
 }
 static void handlePreviewTests(){
  Prefs p=new Prefs();Map<String,float[]> regions=HandleSettings.previewRegions(p,"home",null,false,null);
  check(regions.keySet().equals(new HashSet<>(Arrays.asList("left","right"))),"home preview defaults to the enabled primary pair without preference writes");
  check(p.commits==0&&p.values.isEmpty(),"opening preview never materializes settings or changes opacity");
  p.edit().putBoolean("right_enabled",false).putInt("handle_pair_count",3).apply();
  regions=HandleSettings.previewRegions(p,"home",null,false,null);
  check(regions.keySet().equals(Collections.singleton("left")),"home omits disabled primary and unused extra handles");
  p.edit().putBoolean("extra2_right_enabled",true).putInt("handle_pair_count",1).apply();
  regions=HandleSettings.previewRegions(p,"home",null,false,null);
  check(regions.keySet().equals(new HashSet<>(Arrays.asList("left","extra2_right"))),"enabled extra handles remain visible despite an older pair-count preference");
  p.edit().putBoolean("handles_linked",true).apply();
  regions=HandleSettings.previewRegions(p,"left",null,false,null);
  check(regions.keySet().equals(new HashSet<>(Arrays.asList("left","right"))),"aligned side page previews both editable regions");
  p.edit().putBoolean("handles_linked",false).apply();
  check(HandleSettings.previewRegions(p,"left",null,false,null).keySet().equals(Collections.singleton("left")),"unaligned side page previews only its own region");
  p.edit().putBoolean("handles_linked",true).apply();Map<String,?> saved=p.getAll();int commits=p.commits;
  HandleSettings.Geometry draft=new HandleSettings.Geometry(21.14f,.28f,.61f);
  regions=HandleSettings.previewRegions(p,"left","left",true,draft);
  check(Arrays.equals(regions.get("left"),new float[]{21.14f,.28f,.61f,21.14f})&&Arrays.equals(regions.get("left"),regions.get("right")),"linked uncommitted drag previews exact draft geometry on both edges");
  check(p.commits==commits&&saved.equals(p.getAll()),"live preview preserves all stored preferences and raw precision");
  regions=HandleSettings.previewRegions(p,"left",null,false,null);HandleSettings.Geometry stored=HandleSettings.read(p,"left");
  check(Arrays.equals(regions.get("left"),new float[]{stored.width,stored.start,stored.end,stored.width}),"cancel restores saved geometry rather than leaving the last draft");
  HandleSettings.write(p,"left",draft,true);regions=HandleSettings.previewRegions(p,"left",null,false,null);
  check(Arrays.equals(regions.get("left"),new float[]{21.14f,.28f,.61f,21.14f})&&Arrays.equals(regions.get("left"),regions.get("right")),"committed draft remains visible at the saved geometry");
  Prefs sparse=new Prefs();sparse.edit().putFloat("handle_width_dp",12.3f).apply();Map<String,?> inherited=sparse.getAll();
  HandleSettings.writeLandscapeWidth(sparse,"left",HandleSettings.read(sparse,"left").withLandscapeWidth(8),true);
  check(sparse.getAll().size()==inherited.size()+2&&!sparse.contains("left_handle_width_dp"),"landscape edits do not materialize inherited portrait geometry");
  HandleSettings.writeLandscapeWidth(sparse,"left",HandleSettings.read(sparse,"left").withLandscapeWidth(0),true);
  check(sparse.getAll().equals(inherited),"restoring inheritance preserves even sparse legacy preferences");
  HandleSettings.Geometry wide=draft.withLandscapeWidth(8);HandleSettings.write(p,"left",wide,true);
  regions=HandleSettings.previewRegions(p,"home",null,false,null);
  check(HandleSettings.read(p,"right").landscapeWidth==8&&regions.get("left")[3]==8&&regions.get("left")[0]==draft.width,"linked landscape width is independent and available to the preview");
  HandleSettings.write(p,"left",wide.withSliderProgress(0,500).resized(.3f).moved(.4f),false);
  check(HandleSettings.read(p,"left").landscapeWidth==8,"normal geometry edits retain the landscape override");
  HandleSettings.write(p,"left",wide.withLandscapeWidth(0),false);
  check(!p.contains("left_landscape_handle_width_dp")&&p.contains("right_landscape_handle_width_dp"),"disabling independent width restores live inheritance only on the edited side");
  boolean empty=true;for(String page:new String[]{"gestures","angle","advanced","permissions","tools","widgets","color"})empty&=HandleSettings.previewRegions(p,page,null,false,null).isEmpty();
  check(empty,"leaving handle settings supplies an empty preview set");
  p.edit().putBoolean("enabled",false).apply();
  check(HandleSettings.previewRegions(p,"home","left",true,draft).isEmpty()&&HandleSettings.previewRegions(p,"left",null,false,null).isEmpty(),"master disable clears previews even when a draft was pending");
  p.edit().putBoolean("enabled",true).apply();regions=HandleSettings.previewRegions(p,"left",null,false,null);regions.get("left")[0]=99;
  check(HandleSettings.previewRegions(p,"left",null,false,null).get("left")[0]==21.14f&&HandleSettings.read(p,"left").width==21.14f,"preview arrays are detached from saved values and later refreshes");
 }
 static void handleColorTests()throws Exception{
  Memory c=seeded("handle-colors");SharedPreferences p=AppPrefs.get(c);Map<String,?> original=p.getAll();
  check(AppPrefs.handleColor(p,"left")==0xffffffff&&(AppPrefs.visibleHandleColor(p,"left")>>>24)==0&&original.equals(p.getAll()),"handle color uses transparent white stock preset without migration writes");
  p.edit().putInt("handle_color",0xffabcdef).putInt("handle_opacity",50).apply();
  check(AppPrefs.handleColor(p,"right")==0xffabcdef&&AppPrefs.visibleHandleColor(p,"right")==0x80abcdef,"global handle color fallback uses existing percent opacity");
  int index=0;for(String id:GestureHandleConfig.IDS)p.edit().putInt(id+"_color",0x80112233+index++).apply();
  check(AppPrefs.visibleHandleColor(p,"left")==0x40112233&&AppPrefs.handleColor(p,"right")==0x80112234,"per-handle ARGB alpha multiplies opacity and leaves peer colors independent");
  JSONObject saved=ConfigArchive.export(c);Memory target=seeded("handle-colors-target");ConfigArchive.restore(target,new JSONObject(saved.toString()));SharedPreferences q=AppPrefs.get(target);index=0;
  for(String id:GestureHandleConfig.IDS)check(q.getInt(id+"_color",0)==0x80112233+index++&&q.getAll().get(id+"_color") instanceof Integer,"signed ARGB int survives archive roundtrip for "+id);
  check(q.getInt("handle_color",0)==0xffabcdef&&q.getInt("handle_opacity",0)==50,"global color and opacity retain integer storage");
  JSONObject bounds=copy(saved);bounds.getJSONObject("preferences").put("left_color",Integer.MIN_VALUE).put("right_color",Integer.MAX_VALUE).put("extra_left_color",0);ConfigArchive.restore(target,bounds);
  check(q.getInt("left_color",0)==Integer.MIN_VALUE&&q.getInt("right_color",0)==Integer.MAX_VALUE&&AppPrefs.visibleHandleColor(q,"extra_left")==0,"full signed ARGB range including transparent color imports without loss");
  for(Object value:new Object[]{2147483648L,-2147483649L,0.5,"#FF112233",true}){JSONObject bad=copy(saved);bad.getJSONObject("preferences").put("left_color",value);rejectUnchanged(target,bad,"reject invalid ARGB value without mutation: "+value);}
 }
 static void handleSettingsTests()throws Exception{
  Memory c=seeded("handle-settings");SharedPreferences p=AppPrefs.get(c);Map<String,?> original=p.getAll();
  HandleSettings.Geometry left=HandleSettings.read(p,"left");
  check(HandleSettings.pairCount(p)==1&&HandleSettings.linked(p,"left")&&original.equals(p.getAll()),"menu reads one aligned pair without mutating old preferences");
  Map<String,Object> actions=new HashMap<>();for(Map.Entry<String,?> entry:p.getAll().entrySet())if(entry.getKey().startsWith("action_"))actions.put(entry.getKey(),entry.getValue());
  p.edit().remove("handles_linked").putFloat("left_handle_width_dp",9).putFloat("right_handle_width_dp",15).putFloat("handle_center_fraction",.6f).putFloat("handle_height_fraction",.4f).apply();
  check(!HandleSettings.linked(p,"left")&&Math.abs(HandleSettings.read(p,"left").start-.4f)<.0001,"legacy effective geometry and independent width are displayed faithfully");
  HandleSettings.write(p,"left",HandleSettings.read(p,"left").moved(0),false);
  check(!p.contains("handle_center_fraction")&&!p.contains("handle_height_fraction")&&Math.abs(HandleSettings.read(p,"right").start-.4f)<.0001&&Math.abs(HandleSettings.read(p,"right").end-.8f)<.0001&&p.getFloat("right_handle_width_dp",0)==15,"editing one handle migrates legacy override and preserves peer geometry");
  HandleSettings.write(p,"left",new HandleSettings.Geometry(18,.1f,.5f),true);
  check(p.getFloat("left_handle_width_dp",0)==18&&p.getFloat("right_handle_width_dp",0)==18&&p.getFloat("right_handle_start_fraction",0)==.1f,"aligned controller writes effective per-handle values on both sides");
  HandleSettings.Geometry moved=left.moved(1),resized=left.resized(.05f),full=left.resized(1);
  check(Math.abs(moved.end-1)<.0001&&Math.abs(moved.size()-left.size())<.0001&&Math.abs(resized.size()-.05f)<.0001&&full.start==0&&full.end==1,"size and position sliders stay inside screen and preserve intended extent");
  HandleSettings.Geometry nearBottom=new HandleSettings.Geometry(12,.7f,.9f),bigger=nearBottom.withSliderProgress(1,600);
  check(nearBottom.sliderProgress(2)==125&&bigger.sliderProgress(1)==600&&bigger.sliderProgress(2)==0&&Math.abs(bigger.end-1)<.0001,"size draft synchronizes position progress when resized region reaches bottom");
  HandleSettings.Geometry top=bigger.withSliderProgress(2,1000),wider=top.withSliderProgress(0,750);
  check(top.start==0&&top.sliderProgress(1)==600&&top.sliderProgress(2)==1000&&Math.abs(top.size()-bigger.size())<.0001,"subsequent position edit keeps latest resized height");
  check(wider.width==23&&wider.start==top.start&&wider.end==top.end&&wider.sliderProgress(0)==750,"width edit and all slider refresh values preserve current vertical geometry");
  HandleSettings.Geometry all=nearBottom.withSliderProgress(1,1000);
  check(all.sliderProgress(2)==500&&all.withSliderProgress(2,0).sliderProgress(2)==500&&all.sliderProgress(1)==1000,"full-height region keeps position control at canonical center after commit");
  Map<String,?> beforeDraft=p.getAll();int beforeCommits=((Prefs)p).commits;HandleSettings.Geometry discarded=HandleSettings.read(p,"left").withSliderProgress(1,950);for(int property=0;property<3;property++)discarded.sliderProgress(property);
  check(beforeDraft.equals(p.getAll())&&((Prefs)p).commits==beforeCommits,"draft geometry and in-place slider synchronization do not write preferences");
  HandleSettings.write(p,"left",wider,true);HandleSettings.Geometry committed=HandleSettings.read(p,"left"),peer=HandleSettings.read(p,"right");
  check(((Prefs)p).commits==beforeCommits+1&&committed.sliderProgress(0)==peer.sliderProgress(0)&&committed.sliderProgress(1)==peer.sliderProgress(1)&&committed.sliderProgress(2)==peer.sliderProgress(2),"aligned geometry commits once and both sides yield identical displayed progress");
  HandleSettings.write(p,"left",new HandleSettings.Geometry(18,.1f,.5f),true);
  HandleSettings.write(p,"extra2_left",new HandleSettings.Geometry(12,.2f,.4f),true);
  check(p.getFloat("left_handle_start_fraction",0)==.1f&&p.getFloat("extra2_right_handle_start_fraction",0)==.2f,"extra pair alignment never changes primary pair geometry");
  JSONObject ranges=ConfigArchive.export(c);ranges.getJSONObject("launcher").put("columns",2).put("recent_limit",13);Memory supported=seeded("oho-panel-ranges");ConfigArchive.restore(supported,ranges);check(supported.prefs("edgeflow_launcher").getInt("columns",0)==2&&supported.prefs("edgeflow_launcher").getInt("recent_limit",0)==13,"OHO launcher and task count endpoints use typed portable storage");
  ranges.getJSONObject("launcher").put("columns",5).put("recent_limit",30);ConfigArchive.restore(supported,ranges);check(supported.prefs("edgeflow_launcher").getInt("columns",0)==5&&supported.prefs("edgeflow_launcher").getInt("recent_limit",0)==30,"old five-column and thirty-task settings remain valid without coercion");
  boolean same=true;for(Map.Entry<String,Object> action:actions.entrySet())same&=action.getValue().equals(p.getAll().get(action.getKey()));check(same,"all existing action mappings survive geometry edits");
  p.edit().putInt("handle_pair_count",2).putBoolean("handles_linked",true).putBoolean("extra_handles_linked",false).putBoolean("extra2_handles_linked",true).putBoolean("right_long_enabled",false).apply();
  Memory target=seeded("handle-settings-target");ConfigArchive.restore(target,ConfigArchive.export(c));SharedPreferences q=AppPrefs.get(target);
  check(q.getInt("handle_pair_count",0)==2&&q.getBoolean("handles_linked",false)&&!q.getBoolean("extra_handles_linked",true)&&q.getBoolean("extra2_handles_linked",false)&&!q.getBoolean("right_long_enabled",true),"menu pair count, alignment and long-slide state roundtrip with proper types");
  check(AppPrefs.action(target,"right","up",true).equals(actions.get("action_right_up_long")),"disabling long slide preserves its configured actions through backup");
  q.edit().putBoolean("extra2_right_enabled",true).apply();check(HandleSettings.pairCount(q)==3,"already enabled extra handle remains visible even with older menu count");
  for(Object value:new Object[]{0,4,1.5,"2"}){JSONObject bad=ConfigArchive.export(c);bad.getJSONObject("preferences").put("handle_pair_count",value);rejectUnchanged(target,bad,"reject invalid handle pair count "+value);}
  JSONObject bad=ConfigArchive.export(c);bad.getJSONObject("preferences").put("extra2_right_long_enabled","false");rejectUnchanged(target,bad,"reject non-boolean long-slide switch");
 }
 static void widgetGridArchives()throws Exception{
  AppWidgetProviderInfo available=AppWidgetManager.INSTANCE.providers.get(93);
  AppWidgetManager.INSTANCE.installed.add(available);
  Memory source=seeded("widget-grid-export");
  source.prefs("replica_widgets").edit().putInt("columns",6).putInt("rows",5).apply();
  JSONObject widgets=ConfigArchive.export(source).getJSONObject("widgets");
  check(widgets.getInt("version")==2&&widgets.getInt("columns")==6&&widgets.getInt("rows")==5,"widget archive carries custom grid");
  JSONObject custom=migrationInput().put("version",2).put("columns",6).put("rows",5);
  custom.getJSONArray("items").getJSONObject(0).put("x",5).put("y",4).put("w",1).put("h",1);
  Memory target=new Memory("widget-grid-import");
  JSONObject result=replica.WidgetMigration.migrate(target,custom);
  check(result.getInt("importedCount")==1&&target.prefs("replica_widgets").getInt("columns",0)==6&&target.prefs("replica_widgets").getInt("rows",0)==5,"custom grid migration accepts last cell");
  Memory legacy=new Memory("widget-grid-legacy");
  legacy.prefs("replica_widgets").edit().putInt("columns",6).putInt("rows",6).apply();
  replica.WidgetMigration.migrate(legacy,migrationInput());
  check(replica.WidgetLayout.columns(legacy.prefs("replica_widgets"))==4&&replica.WidgetLayout.rows(legacy.prefs("replica_widgets"))==4,"legacy widget migration restores four by four");
  for(double bad:new double[]{1,7,4.5}){
   Memory invalid=new Memory("widget-grid-invalid-"+bad);int count=AppWidgetHost.live.size();
   result=replica.WidgetMigration.migrate(invalid,new JSONObject(custom.toString()).put("columns",bad));
   check(result.getString("status").equals("invalid_layout")&&AppWidgetHost.live.size()==count&&invalid.prefs("replica_widgets").getAll().isEmpty(),"invalid grid rejected before allocation "+bad);
  }
  AppWidgetManager.INSTANCE.installed.remove(available);
 }
 static JSONObject migrationInput(){return new JSONObject().put("version",1).put("pages",1).put("page",0).put("height",330).put("loop",true).put("items",new JSONArray().put(new JSONObject().put("component","com.example.widget/com.example.widget.Provider").put("sourceId",93).put("page",0).put("x",0).put("y",0).put("w",4).put("h",2)));}
 static void migrationTests(AppWidgetProviderInfo provider)throws Exception{
  Memory crossProfile=new Memory("migration-profile");JSONObject crossInput=migrationInput();crossInput.getJSONArray("items").getJSONObject(0).put("requiresProfileSelection",true);
  int allocatedBefore=AppWidgetHost.allocations;JSONObject crossResult=replica.WidgetMigration.migrate(crossProfile,crossInput);
  check(crossResult.getJSONArray("imported").length()==0&&crossResult.getJSONArray("failed").getJSONObject(0).getString("reason").equals("profile_selection_required")&&AppWidgetHost.allocations==allocatedBefore,"cross-profile migration never binds the same package in the personal profile");
  AppWidgetManager.INSTANCE.installed.add(provider);provider.configure=new ComponentName("com.example.widget","com.example.widget.Configure");
  Memory m=new Memory("migration");JSONObject input=migrationInput();JSONObject report=replica.WidgetMigration.migrate(m,input);int id=report.getJSONArray("imported").getJSONObject(0).getInt("appWidgetId");check(report.getString("status").equals("imported")&&id!=93&&AppWidgetHost.live.contains(id),"widget migration allocates new host ID");check(report.getInt("configurationCount")==1&&report.getJSONArray("needsConfiguration").length()==1,"widget migration reports required provider configuration");int allocations=AppWidgetHost.allocations;JSONObject repeated=replica.WidgetMigration.migrate(m,input);check(repeated.getBoolean("repeatedRequest")&&AppWidgetHost.allocations==allocations,"identical widget migration is idempotent");
  JSONObject different=migrationInput().put("height",400);String saved=m.prefs("replica_widgets").getString("layout_v3",null);report=replica.WidgetMigration.migrate(m,different);check(report.getString("status").equals("destination_not_empty")&&saved.equals(m.prefs("replica_widgets").getString("layout_v3",null)),"widget migration refuses nonempty destination");
  Memory partial=new Memory("migration-partial");JSONObject overlap=migrationInput();overlap.getJSONArray("items").put(copy(overlap.getJSONArray("items").getJSONObject(0)));report=replica.WidgetMigration.migrate(partial,overlap);check(report.getString("status").equals("partial")&&report.getInt("importedCount")==1&&report.getInt("failedCount")==1,"widget overlap yields explicit partial report");
  Memory denied=new Memory("migration-denied");Set<Integer> before=new HashSet<>(AppWidgetHost.live);AppWidgetManager.INSTANCE.allowBinding=false;report=replica.WidgetMigration.migrate(denied,migrationInput());AppWidgetManager.INSTANCE.allowBinding=true;check(report.getString("status").equals("nothing_imported")&&before.equals(AppWidgetHost.live)&&denied.prefs("replica_widgets").getAll().isEmpty(),"denied widget bind deletes new ID and leaves prefs untouched");
  Memory failing=new Memory("migration-write-fail");failing.prefs("replica_widgets").edit().putInt("pages",2).putInt("page",1).putInt("height_v4",400).putBoolean("loop",false).putString("layout_v3","[]").putString("unrelated","keep").apply();Map<String,?> old=failing.prefs("replica_widgets").getAll();before=new HashSet<>(AppWidgetHost.live);failing.prefs("replica_widgets").failNext=true;report=replica.WidgetMigration.migrate(failing,migrationInput());check(report.getString("status").equals("persistence_failed")&&old.equals(failing.prefs("replica_widgets").getAll())&&before.equals(AppWidgetHost.live),"widget persistence failure restores all values and releases new IDs");check(report.getInt("importedCount")==0&&report.getInt("configurationCount")==0&&report.getJSONArray("needsConfiguration").length()==0,"rolled-back migration report never offers deleted widget IDs");
 }

 static void settingsTests()throws Exception{
  Memory defaults=seeded("new-settings-defaults");SharedPreferences p=AppPrefs.get(defaults);check(!p.getBoolean("hide_keyboard",true)&&!p.getBoolean("hide_home",true)&&!p.getBoolean("hide_notifications",true)&&!p.getBoolean("quick_action_short",true)&&p.getBoolean("quick_action_long",false)&&p.getInt("vibration_strength",-1)==0,"new OHO settings preserve requested defaults");
  ConfigArchive.restore(defaults,archive(2));JSONObject exported=ConfigArchive.export(defaults);Memory roundtrip=new Memory("new-settings-roundtrip");ConfigArchive.restore(roundtrip,new JSONObject(exported.toString()));p=AppPrefs.get(roundtrip);check(p.getBoolean("hide_home",false)&&p.getBoolean("hide_notifications",false)&&p.getBoolean("quick_action_short",false)&&!p.getBoolean("quick_action_long",true)&&p.getInt("vibration_strength",0)==192&&!p.getBoolean("long_immediate",true),"new OHO settings retain typed values through v2 roundtrip");
  JSONObject old=archive(1);old.getJSONObject("preferences").put("long_immediate",true);ConfigArchive.restore(roundtrip,old);p=AppPrefs.get(roundtrip);check(p.getBoolean("long_immediate",false)&&!p.getBoolean("quick_action_long",true),"legacy long_immediate stays independent from quick-action mode");
  for(double value:new double[]{-1,256,12.5}){JSONObject bad=archive(2);bad.getJSONObject("preferences").put("vibration_strength",value);rejectUnchanged(roundtrip,bad,"reject invalid vibration strength "+value);}
  JSONObject bad=archive(2);bad.getJSONObject("preferences").put("quick_action_short","true");rejectUnchanged(roundtrip,bad,"reject wrong quick-action boolean type");
  for(int value:new int[]{0,255}){JSONObject good=archive(2);good.getJSONObject("preferences").put("vibration_strength",value);ConfigArchive.restore(roundtrip,good);check(AppPrefs.get(roundtrip).getInt("vibration_strength",-1)==value,"vibration endpoint "+value+" uses integer storage");}
 }

 static void hapticAngleSettingsTests()throws Exception{
  Memory c=seeded("haptic-angle-settings");JSONObject a=archive(2);JSONObject prefs=a.getJSONObject("preferences");
  prefs.put("quick_vibration",false).put("custom_vibration",true).put("vibration_duration_ms",17).put("direction_angle_degrees",37.5);
  for(boolean longer:new boolean[]{false,true})for(int i=0;i<4;i++)prefs.put(GestureAngles.key(longer,i),GestureAngles.min(i)+.25);
  ConfigArchive.restore(c,a);Memory restored=new Memory("haptic-angle-roundtrip");ConfigArchive.restore(restored,new JSONObject(ConfigArchive.export(c).toString()));SharedPreferences p=AppPrefs.get(restored);
  check(!p.getBoolean("quick_vibration",true)&&p.getBoolean("custom_vibration",false)&&p.getInt("vibration_duration_ms",0)==17,"independent haptic timing/mode/duration typed archive roundtrip");
  check(p.getInt("vibration_strength",0)==192&&!GestureHaptics.legacyAmplitude(p.getAll())&&p.getFloat("direction_angle_degrees",0)==37.5f,"new settings preserve stored legacy amplitude and angle without using them over explicit keys");
  for(boolean longer:new boolean[]{false,true})for(int i=0;i<4;i++){String key=GestureAngles.key(longer,i);check(p.getFloat(key,0)==GestureAngles.min(i)+.25f,"independent angle float retained: "+key);for(float invalid:new float[]{GestureAngles.min(i)-1,GestureAngles.max(i)+1}){JSONObject bad=copy(a);bad.getJSONObject("preferences").put(key,invalid);rejectUnchanged(restored,bad,"reject out-of-range angle "+key+"="+invalid);}}
  for(int duration:new int[]{0,30}){JSONObject good=copy(a);good.getJSONObject("preferences").put("vibration_duration_ms",duration);ConfigArchive.restore(c,good);check(AppPrefs.get(c).getInt("vibration_duration_ms",-1)==duration,"duration endpoint is integer "+duration);}
  for(double invalid:new double[]{-1,31,1.5}){JSONObject bad=copy(a);bad.getJSONObject("preferences").put("vibration_duration_ms",invalid);rejectUnchanged(restored,bad,"reject invalid duration "+invalid);}
  for(String key:new String[]{"quick_vibration","custom_vibration"}){JSONObject bad=copy(a);bad.getJSONObject("preferences").put(key,1);rejectUnchanged(restored,bad,"reject nonboolean "+key);}
 }

 static void legacyLauncherActionTests()throws Exception{
  check(Arrays.asList(ActionExecutor.IDS).contains("app_launcher")&&ActionExecutor.title("app_launcher").equals("应用启动器")&&!ActionCatalog.isSelectable("app_launcher"),"retired launcher action keeps its execution ID and title without a new selection entry");
  check(!Arrays.asList(NavigationConfig.IDS).contains("app_launcher")&&NavigationConfig.IDS.length==NavigationConfig.TITLES.length,"navigation picker excludes retired launcher without misaligning titles");
  String navigation="[\"back\",\"app_launcher\",\"home\"]";check(NavigationConfig.validate(navigation).equals(navigation),"legacy launcher navigation assignment remains valid");
  Memory source=seeded("legacy-launcher-action"),restored=new Memory("legacy-launcher-action-restored");
  AppPrefs.importPrefs(source,new JSONObject().put("action_left_up_long","app_launcher").put("navigation_actions",navigation));
  LauncherLayout layout=LauncherLayout.load(source.prefs("edgeflow_launcher"));layout.items.add("action:app_launcher");String folder=layout.createFolder("旧入口");layout.folders.get(folder).items.add("action:app_launcher");layout.save(source.prefs("edgeflow_launcher"));
  check(AppPrefs.action(source,"left","up",true).equals("app_launcher")&&Arrays.equals(NavigationConfig.load(source),new String[]{"back","app_launcher","home"}),"legacy gesture and navigation values load without migration or fallback");
  ConfigArchive.restore(restored,ConfigArchive.export(source));LauncherLayout imported=LauncherLayout.load(restored.prefs("edgeflow_launcher"));
  check(AppPrefs.action(restored,"left","up",true).equals("app_launcher")&&Arrays.equals(NavigationConfig.load(restored),new String[]{"back","app_launcher","home"}),"legacy gesture and navigation survive export and restore unchanged");
  check(imported.items.contains("action:app_launcher")&&imported.folders.get(folder).items.contains("action:app_launcher"),"existing root and folder launcher entries survive export and restore");
 }
 static void externalActionTargetTests()throws Exception{
  String gesture="action_right_up_short",app="app:com.example/com.example.Main",shortcut=ShortcutEntry.create(0,"com.example","conversation:你好");
  String appKey=ActionTarget.key(gesture,false),shortcutKey=ActionTarget.key(gesture,true);
  check(ActionTarget.isKey(appKey)&&ActionTarget.isKey(shortcutKey)&&!ActionTarget.isKey("target_app_action_unknown"),"external target keys require a valid gesture slot");
  JSONObject valid=AppPrefs.validate(new JSONObject().put(appKey,app).put(shortcutKey,shortcut));check(valid.getString(appKey).equals(app)&&valid.getString(shortcutKey).equals(shortcut),"app and shortcut target types validate independently");
  for(String[] pair:new String[][]{{appKey,shortcut},{shortcutKey,app}}){boolean rejected=false;try{AppPrefs.validate(new JSONObject().put(pair[0],pair[1]));}catch(JSONException expected){rejected=true;}check(rejected,"external action target rejects cross-type "+pair[0]);}
  Prefs p=new Prefs();p.edit().putString(gesture,"back").apply();int before=p.commits;check(ActionTarget.get(p,gesture,false).isEmpty()&&p.commits==before,"target getter never mutates active action or preferences");
  ActionTarget.configure(p,gesture,false,app,false);check(p.getString(gesture,"").equals("back")&&ActionTarget.get(p,gesture,false).equals(app),"gear configuration retains different active action");
  ActionTarget.select(p,gesture,app);ActionTarget.configure(p,gesture,false,"app:com.other/com.other.Main",false);check(p.getString(gesture,"").equals("app:com.other/com.other.Main"),"gear updates an already selected same-type action");
  ActionTarget.configure(p,gesture,true,shortcut,true);check(p.getString(gesture,"").equals(shortcut)&&ActionTarget.get(p,gesture,false).equals("app:com.other/com.other.Main"),"choosing shortcut retains previously selected app target");
  ActionTarget.select(p,gesture,"home");check(p.getString(gesture,"").equals("home")&&ActionTarget.get(p,gesture,true).equals(shortcut),"choosing ordinary action retains external shortcut configuration");
  Memory c=seeded("action-target-archive");AppPrefs.importPrefs(c,new JSONObject().put(appKey,app).put(shortcutKey,shortcut));Memory destination=new Memory("action-target-restored");ConfigArchive.restore(destination,ConfigArchive.export(c));check(ActionTarget.get(AppPrefs.get(destination),gesture,false).equals(app)&&ActionTarget.get(AppPrefs.get(destination),gesture,true).equals(shortcut),"external targets survive portable archive roundtrip");
 }

 static String toolKeys(Memory c){
  return replica.ToolsConfig.load(c).stream().map(item->item.key).collect(java.util.stream.Collectors.joining(","));
 }
 static void releaseUpdateChecks()throws Exception{
  JSONObject asset=new JSONObject().put("name","ColorHandOperation-0.3.18.apk").put("state","uploaded");
  JSONObject release=new JSONObject().put("tag_name","v0.3.18").put("draft",false).put("prerelease",false).put("assets",new JSONArray().put(asset));
  ReleaseUpdate update=ReleaseUpdate.parse("0.3.9",release);
  check(update.comparedToInstalled>0&&update.apkUrl.equals(ReleaseUpdate.HOME+"/releases/download/v0.3.18/ColorHandOperation-0.3.18.apk"),"release comparison is numeric and the APK URL matches the published asset");
  check(ReleaseUpdate.parse("0.3.18",release).comparedToInstalled==0&&ReleaseUpdate.parse("0.4.0",release).comparedToInstalled<0,"same or newer local versions never trigger a download");
  asset.put("state","new");
  check(ReleaseUpdate.parse("0.3.9",release).apkUrl==null,"unfinished APK uploads are ignored");
  asset.put("state","uploaded").put("name","ColorHandOperation-0.3.18-source.zip");
  update=ReleaseUpdate.parse("0.3.9",release);
  check(update.apkUrl==null&&update.notesUrl.equals(ReleaseUpdate.HOME+"/releases/tag/v0.3.18"),"release notes remain available when the APK is missing");
  for(String flag:new String[]{"draft","prerelease"}){
   boolean rejected=false;try{ReleaseUpdate.parse("0.3.9",copy(release).put(flag,true));}catch(JSONException expected){rejected=true;}
   check(rejected,"ignore "+flag+" releases");
  }
  for(String tag:new String[]{"v0.3.19-rc1","v0.3","v0.3.18/download"}){
   boolean rejected=false;try{ReleaseUpdate.parse("0.3.9",copy(release).put("tag_name",tag));}catch(JSONException expected){rejected=true;}
   check(rejected,"reject invalid release tag "+tag);
  }
 }
 static void toolAnimations()throws Exception{
  Memory source=new Memory("tool-animations");AppPrefs.defaults(source);
  Prefs prefs=source.prefs("edgeflow");
  replica.ToolAnimation quick=replica.ToolAnimation.QUICK_TOOLS,widgets=replica.ToolAnimation.WIDGETS;
  check(quick.active(source,true)==replica.ToolAnimation.Style.VOLUME&&widgets.active(source,false)==replica.ToolAnimation.Style.CENTER,"missing tool animations inherit each tool's native style");
  quick.select(prefs,true,replica.ToolAnimation.Style.SIDEBAR);quick.select(prefs,false,replica.ToolAnimation.Style.SLIDE);
  check(widgets.selection(prefs,true)==replica.ToolAnimation.Style.DEFAULT,"individual selection does not touch another tool");
  widgets.select(prefs,false,replica.ToolAnimation.Style.ANCHOR);
  int commits=prefs.commits;quick.applyToAll(prefs,true);
  check(prefs.commits==commits+1,"bulk entry is one write");
  check(widgets.selection(prefs,false)==replica.ToolAnimation.Style.ANCHOR,"bulk entry preserves another tool's exit");
  widgets.select(prefs,true,replica.ToolAnimation.Style.NONE);
  commits=prefs.commits;quick.applyToAll(prefs,false);
  check(prefs.commits==commits+1,"bulk exit is one write");
  for(replica.ToolAnimation tool:replica.ToolAnimation.values())
   check(tool.selection(prefs,true)==(tool==widgets?replica.ToolAnimation.Style.NONE:replica.ToolAnimation.Style.SIDEBAR)&&tool.selection(prefs,false)==replica.ToolAnimation.Style.SLIDE,"phase-only copy "+tool.id);
  widgets.select(prefs,false,replica.ToolAnimation.Style.NONE);
  Memory dest=new Memory("tool-motion-restore");ConfigArchive.restore(dest,ConfigArchive.export(source));
  check(widgets.active(dest,false)==replica.ToolAnimation.Style.NONE&&quick.active(dest,false)==replica.ToolAnimation.Style.SLIDE,"per-tool overrides survive backup independently");
  AppPrefs.resetGestures(dest);check(widgets.active(dest,false)==replica.ToolAnimation.Style.NONE,"reset gestures preserves tool choices");
  AppPrefs.importPrefs(dest,new JSONObject().put(widgets.key(false),"default"));
  check(!AppPrefs.get(dest).contains(widgets.key(false))&&widgets.active(dest,false)==replica.ToolAnimation.Style.CENTER,"explicit default in a partial import restores inheritance");
  JSONObject backup=ConfigArchive.export(source);
  for(String bad:new String[]{"tool_motion/missing/enter","tool_motion/widgets/both","tool_motion/widgets/exit"}){
   JSONObject broken=copy(backup);broken.getJSONObject("preferences").put(bad,"expanded_volume");
   rejectUnchanged(dest,broken,"invalid tool animation rejected atomically: "+bad);
  }
  JSONObject old=copy(backup);
  for(replica.ToolAnimation tool:replica.ToolAnimation.values())for(boolean enter:new boolean[]{true,false})old.getJSONObject("preferences").remove(tool.key(enter));
  ConfigArchive.restore(dest,old);check(widgets.active(dest,false)==replica.ToolAnimation.Style.CENTER,"old full archive resets omitted animations to tool defaults");
 }
 static void factorySettings()throws Exception{
  Memory fresh=new Memory("factory");AppPrefs.defaults(fresh);Prefs p=fresh.prefs("edgeflow");
  check(!p.getBoolean("enabled",true)&&HandleSettings.pairCount(p)==1&&p.getBoolean("left_enabled",false)&&p.getBoolean("right_enabled",false),"first launch: master off, one configured pair");
  for(String id:GestureHandleConfig.IDS){
   check(!p.getBoolean(id+"_long_enabled",true)&&HandleSettings.linked(p,id),"long disabled and geometry linked: "+id);
   for(String direction:new String[]{"straight","up","down"})for(boolean longer:new boolean[]{false,true})
    check(GestureHandleConfig.defaultAction(id,direction,longer).equals(AppPrefs.action(fresh,id,direction,longer)),"seeded slot "+GestureHandleConfig.actionKey(id,direction,longer));
  }
  check(p.getFloat("trigger_dp",0)==81/3.5f&&p.getFloat("long_trigger_dp",0)==306/3.5f
        &&p.getFloat("left_handle_width_dp",0)==22,"pixel thresholds and touch width converted using actual display density");
  GestureHandleConfig.Area portrait=HandleDisplayArea.reference;
  int[] modern=GestureHandleConfig.bounds("left",22,HandleSettings.read(p,"left").start,HandleSettings.read(p,"left").end,3.5f,portrait);
  check(Arrays.equals(modern,new int[]{0,757,77,1536}),"API 36+ primary span uses 50/35 in stable portrait insets");
  HandleSettings.Geometry oldAndroid=new FactoryHandleDefaults(portrait,3.5f,490,361,35).geometry("right");
  check(Arrays.equals(GestureHandleConfig.bounds("right",oldAndroid.width,oldAndroid.start,oldAndroid.end,3.5f,portrait),new int[]{1187,629,77,1787}),"pre-36 primary span uses 60/30");
  check(HandleSettings.read(p,"extra_left").start==0&&HandleSettings.read(p,"extra2_right").end==1,"additional pairs occupy opposite ends");
  check(GestureAngles.read(p.getAll(),false).upInner==25&&GestureAngles.read(p.getAll(),false).downInner==45&&GestureAngles.read(p.getAll(),true).upInner==20,"distinct short and long angles");
  check(toolKeys(fresh).equals("0,1,2,3,4,6,9,7,8,5,10,11"),"fresh quick tools follow phone preset");
  HandleSettings.addPair(p);check(HandleSettings.pairCount(p)==2&&p.getBoolean("extra_right_enabled",false)&&p.getBoolean("extra_left_enabled",false),"explicit add activates both new handles");
  p.edit().putBoolean("extra_left_long_enabled",true).apply();
  check(HandleSettings.hasLongGestures(p),"extra pair can independently expose long settings");
  String saved=state(fresh);AppPrefs.defaults(fresh);check(state(fresh).equals(saved),"subsequent launch preserves all choices");
  Memory dest=new Memory("factory-roundtrip");ConfigArchive.restore(dest,ConfigArchive.export(fresh));AppPrefs.defaults(dest);check(state(dest).equals(saved),"schema 2 backup survives restore and restart");
  Memory legacy=new Memory("factory-legacy");Prefs old=legacy.prefs("edgeflow");
  old.edit().putInt("schema",1).putInt(HandleSettings.VERSION_KEY,2).putBoolean("enabled",true).putFloat("trigger_dp",39.43f)
     .putString("action_right_down_long","screen_off").putString("action_left_straight_long","one_hand").apply();
  Map<String,?> before=old.getAll();AppPrefs.defaults(legacy);
  check(old.getAll().equals(before)&&AppPrefs.action(legacy,"right","down",true).equals("screen_off")&&AppPrefs.action(legacy,"left","straight",false).equals("recents")&&HandleSettings.hasLongGestures(old),"old install: custom values and missing-slot semantics unchanged");
  String legacyOrder=toolKeys(legacy);
  check(legacyOrder.equals("0,1,2,3,4,5,6,7,8,9,10,11"),"old install without a layout file keeps its tile order");
  JSONObject backup=ConfigArchive.export(legacy);backup.getJSONObject("preferences").remove("schema");
  ConfigArchive.restore(dest,backup);AppPrefs.defaults(dest);
  check(AppPrefs.action(dest,"left","straight",false).equals("recents")&&AppPrefs.action(dest,"right","down",true).equals("screen_off")&&toolKeys(dest).equals(legacyOrder),"unversioned full backup survives restart with legacy fallback");
  AppPrefs.resetGestures(legacy);
  check(AppPrefs.action(legacy,"left","straight",false).equals("back")&&!HandleSettings.hasLongGestures(old)&&toolKeys(legacy).equals(legacyOrder),"explicit reset switches gestures but preserves unsaved old tile order");
  replica.ToolsConfig.save(fresh,Arrays.asList(replica.ToolsConfig.builtin(9),replica.ToolsConfig.builtin(2)));
  AppPrefs.resetGestures(fresh);check(toolKeys(fresh).equals("9,2"),"gesture reset preserves a custom quick tools file");
 }
 static void extendedSettingsTests()throws Exception{
  Memory c=seeded("extended-settings");SharedPreferences p=AppPrefs.get(c);check(p.getFloat("touchpad_speed",0)==1.7f&&p.getFloat("navigation_button_size_dp",0)==58f&&Arrays.equals(NavigationConfig.load(c),new String[]{"back","home","recents"}),"touchpad and navigation accepted defaults remain unchanged");
  String shortcut=ShortcutEntry.create(0,"com.example","conversation:你好");JSONObject a=archive(2);a.getJSONObject("preferences").put("extra_left_enabled",true).put("extra_left_handle_start_fraction",.1).put("extra_left_handle_end_fraction",.4).put("extra_left_handle_width_dp",10).put("action_extra_left_up_long",shortcut).put("extra2_right_enabled",true).put("extra2_right_handle_start_fraction",.35).put("extra2_right_handle_end_fraction",.55).put("extra2_right_handle_width_dp",14).put("action_extra2_right_down_short","screenshot").put("touchpad_speed",2.35).put("navigation_button_size_dp",68).put("navigation_actions","[\"home\",\"back\",\"quick_tools\",\"virtual_touchpad\",\"screenshot\"]");JSONObject layout=layout();layout.getJSONArray("items").put(shortcut);a.getJSONObject("launcher").put("layout_v2",layout.toString()).put("shortcut_labels",new JSONObject().put(shortcut,"会话").toString());ConfigArchive.restore(c,a);JSONObject exported=ConfigArchive.export(c);Memory target=new Memory("extended-target");ConfigArchive.restore(target,new JSONObject(exported.toString()));p=AppPrefs.get(target);check(p.getBoolean("extra_left_enabled",false)&&p.getFloat("extra_left_handle_width_dp",0)==10f&&AppPrefs.action(target,"extra_left","up",true).equals(shortcut),"extra-handle geometry and system shortcut action roundtrip");check(p.getBoolean("extra2_right_enabled",false)&&p.getFloat("extra2_right_handle_width_dp",0)==14f&&AppPrefs.action(target,"extra2_right","down",false).equals("screenshot"),"second extra pair action and geometry roundtrip");check(Math.abs(p.getFloat("touchpad_speed",0)-2.35f)<.0001&&p.getFloat("navigation_button_size_dp",0)==68f&&Arrays.equals(NavigationConfig.load(target),new String[]{"home","back","quick_tools","virtual_touchpad","screenshot"}),"touchpad speed and five-key navigation order roundtrip");check(new JSONObject(target.prefs("edgeflow_launcher").getString("shortcut_labels","{}")).getString(shortcut).equals("会话")&&LauncherLayout.load(target.prefs("edgeflow_launcher")).items.contains(shortcut),"portable launcher shortcut identity and display label roundtrip");
  JSONObject bad=copy(a);bad.getJSONObject("preferences").put("extra_left_handle_start_fraction",.6).put("extra_left_handle_end_fraction",.61);rejectUnchanged(target,bad,"reject too-small extra handle span");bad=copy(a);bad.getJSONObject("preferences").put("action_extra_right_down_short","not_an_action");rejectUnchanged(target,bad,"reject unknown extra handle action");
  for(String key:new String[]{"extra_left_handle_width_dp","touchpad_speed","navigation_button_size_dp"}){bad=copy(a);bad.getJSONObject("preferences").put(key,key.equals("extra_left_handle_width_dp")?31:key.equals("touchpad_speed")?.49:77);rejectUnchanged(target,bad,"reject out-of-range "+key);}
  for(String value:new String[]{"[]","[\"back\",\"home\",\"recents\",\"screenshot\",\"quick_tools\",\"widgets\"]","[\"home\",\"home\"]","[\"none\"]","[\"navigation_panel\"]"}){bad=copy(a);bad.getJSONObject("preferences").put("navigation_actions",value);rejectUnchanged(target,bad,"reject invalid navigation list "+value);}
  bad=copy(a);bad.getJSONObject("preferences").put("navigation_actions",new JSONArray().put("back"));rejectUnchanged(target,bad,"navigation_actions must retain string storage");bad=copy(a);bad.getJSONObject("preferences").put("action_extra_left_up_long","launcher_shortcut:1:0:invalid");rejectUnchanged(target,bad,"reject malformed system shortcut identity");bad=copy(a);bad.getJSONObject("launcher").put("shortcut_labels",new JSONObject().put(shortcut,42).toString());rejectUnchanged(target,bad,"reject non-string shortcut label cache");
  JSONObject minimum=copy(a);minimum.getJSONObject("preferences").put("touchpad_speed",.5).put("navigation_button_size_dp",40).put("navigation_actions","[\"back\"]").put("extra_left_handle_start_fraction",.90).put("extra_left_handle_end_fraction",.95);ConfigArchive.restore(target,minimum);check(NavigationConfig.load(target).length==1&&p.getFloat("touchpad_speed",0)==.5f&&p.getFloat("navigation_button_size_dp",0)==40f,"minimum supported settings and 5-percent extra handle span import");
 }
 static void cell(replica.WidgetGrid.Cell c,int x,int y,int w,int h,String name){check(c!=null&&c.x==x&&c.y==y&&c.w==w&&c.h==h,name);}
 static void widgetEditTests(){
  replica.WidgetGrid.Cell start=new replica.WidgetGrid.Cell(1,1,2,2);
  cell(replica.WidgetGrid.propose(start,-1,0,replica.WidgetGrid.LEFT),0,1,3,2,"left resize anchors right edge");cell(replica.WidgetGrid.propose(start,0,-1,replica.WidgetGrid.TOP),1,0,2,3,"top resize anchors bottom edge");cell(replica.WidgetGrid.propose(start,1,0,replica.WidgetGrid.RIGHT),1,1,3,2,"right resize preserves origin");cell(replica.WidgetGrid.propose(start,0,1,replica.WidgetGrid.BOTTOM),1,1,2,3,"bottom resize preserves origin");check(replica.WidgetGrid.propose(start,-2,0,replica.WidgetGrid.LEFT)==null&&replica.WidgetGrid.propose(start,2,0,replica.WidgetGrid.RIGHT)==null,"resize cannot cross page bounds");check(replica.WidgetGrid.propose(start,2,0,replica.WidgetGrid.LEFT)==null,"resize cannot create zero-width widget");
  replica.WidgetGrid.Edit edit=new replica.WidgetGrid.Edit(start,0);cell(edit.move(-1,-1,c->true),0,0,2,2,"move uses original pointer origin");cell(edit.move(5,0,c->true),0,0,2,2,"invalid drag preserves last valid geometry");cell(edit.cancel(),1,1,2,2,"cancelled drag restores original geometry");edit=new replica.WidgetGrid.Edit(start,replica.WidgetGrid.LEFT|replica.WidgetGrid.TOP);cell(edit.move(-1,-1,c->true),0,0,3,3,"corner resize handles both axes");cell(edit.cancel(),1,1,2,2,"cancelled resize restores original size and origin");
  replica.WidgetGrid.Cell occupied=new replica.WidgetGrid.Cell(0,0,1,4);edit=new replica.WidgetGrid.Edit(start,0);cell(edit.move(-1,0,c->!replica.WidgetGrid.overlaps(c,occupied)),1,1,2,2,"occupied cells reject drag without mutation");check(!replica.WidgetGrid.overlaps(start,occupied),"touching cell edges do not overlap");check(replica.WidgetGrid.propose(start,1,1,replica.WidgetGrid.LEFT|replica.WidgetGrid.RIGHT)==null,"contradictory resize edges rejected");
 }

 static void shortcutRegressionTests()throws Exception{
  Memory source=seeded("folder-regression"),destination=seeded("folder-regression-target");String oldName="N".repeat(257);JSONObject saved=layout();saved.getJSONObject("folders").getJSONObject("folder-1").put("name",oldName);source.prefs("edgeflow_launcher").edit().putString("layout_v2",saved.toString()).apply();LauncherLayout loaded=LauncherLayout.load(source.prefs("edgeflow_launcher"));boolean preserved=loaded.folders.get("folder-1").name.equals(oldName);loaded.save(source.prefs("edgeflow_launcher"));String before=source.prefs("edgeflow_launcher").getString("layout_v2",null);boolean exportBlocked=false,inputBlocked=false,writeBlocked=false;try{ConfigArchive.export(source);}catch(JSONException expected){exportBlocked=expected.getMessage().contains("256");}int count=loaded.folders.size();try{loaded.createFolder(oldName);}catch(IllegalArgumentException expected){inputBlocked=loaded.folders.size()==count;}loaded.folders.get("folder-1").name=oldName+"x";try{loaded.save(source.prefs("edgeflow_launcher"));}catch(IllegalArgumentException expected){writeBlocked=before.equals(source.prefs("edgeflow_launcher").getString("layout_v2",null));}loaded.folders.get("folder-1").name="N".repeat(256);loaded.save(source.prefs("edgeflow_launcher"));ConfigArchive.restore(destination,ConfigArchive.export(source));boolean roundtrip=LauncherLayout.load(destination.prefs("edgeflow_launcher")).folders.get("folder-1").name.length()==256;check(preserved&&exportBlocked&&inputBlocked&&writeBlocked&&roundtrip,"folder regression: preserve legacy names, reject new overflow, export only restorable layout");
  String key=ShortcutEntry.create(0,"com.example","debug");JSONObject item=new JSONObject().put("key",key).put("label","Exception: Debug; permission denied").put("enabled",true).put("dynamic",true).put("pinned",false).put("manifest",false);String raw="helper startup log\nEDGE_SHORTCUTS="+new JSONArray().put(item)+"\n";JSONArray parsed=ShortcutPicker.parseResult(0,raw,0,"com.example");boolean schemaRejected=false,exitRejected=false,markerRejected=false;try{ShortcutPicker.parseResult(0,"EDGE_SHORTCUTS="+new JSONArray().put(copy(item).put("enabled","true")),0,"com.example");}catch(JSONException expected){schemaRejected=true;}try{ShortcutPicker.parseResult(1,raw,0,"com.example");}catch(IOException expected){exitRejected=true;}try{ShortcutPicker.parseResult(0,"no protocol marker",0,"com.example");}catch(IOException expected){markerRejected=true;}check(parsed.length()==1&&parsed.getJSONObject(0).getString("label").equals(item.getString("label"))&&schemaRejected&&exitRejected&&markerRejected,"shortcut protocol regression: payload error words are data; exit, marker and schema remain enforced");
 }

}
