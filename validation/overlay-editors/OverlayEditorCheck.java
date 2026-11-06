package dev.edgeflow;
import java.util.*;
import org.json.*;

/** Production picker commit and keyboard archive contracts; no Android windows are simulated. */
public final class OverlayEditorCheck {
 private static int checks;
 private static void check(boolean condition,String name){if(!condition)throw new AssertionError(name);checks++;System.out.println("PASS "+name);}
 private static JSONObject keyboard(String name,String chord){return new JSONObject().put("app:com.example",new JSONArray().put(new JSONObject().put("name",name).put("keys",chord)).toString());}
 private static void rejected(JSONObject input,String name)throws Exception{String before=input.toString();boolean failed=false;try{KeyboardShortcutsOverlay.validatePreferences(input);}catch(JSONException expected){failed=true;}check(failed&&input.toString().equals(before),name);}
 public static void main(String[] args)throws Exception{
  ArrayList<String> saved=new ArrayList<>(Arrays.asList("action:app_launcher","app:com.removed/com.removed.Main","action:back","action:home"));
  List<String> visible=Arrays.asList("action:back","action:home","action:screenshot");boolean[] draft={false,true,true};ArrayList<String> before=new ArrayList<>(saved);
  draft[0]=true;draft[1]=false;check(saved.equals(before),"changing picker checkboxes cannot mutate the saved target before confirmation");
  QuickLauncherOverlay.applyChoices(saved,visible,draft);check(saved.equals(Arrays.asList("action:app_launcher","app:com.removed/com.removed.Main","action:back","action:screenshot")),"confirmed choices only add/remove exposed IDs and retain legacy/unavailable entries");
  QuickLauncherOverlay.applyChoices(saved,visible,draft);check(new HashSet<>(saved).size()==saved.size()&&saved.get(2).equals("action:back"),"reapplying a choice neither duplicates nor reorders existing entries");
  QuickLauncherOverlay.applyChoices(saved,visible,new boolean[3]);check(saved.equals(Arrays.asList("action:app_launcher","app:com.removed/com.removed.Main")),"clearing every visible checkbox retains entries excluded from the picker");
  before=new ArrayList<>(saved);QuickLauncherOverlay.applyChoices(saved,Collections.emptyList(),new boolean[0]);check(saved.equals(before),"an empty picker cannot remove saved data");
  ArrayList<String> folder=new ArrayList<>(Arrays.asList("action:home"));QuickLauncherOverlay.applyChoices(folder,visible,new boolean[]{true,true,false});check(folder.equals(Arrays.asList("action:home","action:back"))&&saved.equals(before),"folder confirmation affects only its captured target and preserves its ordering");
  JSONObject existing=keyboard("原有名称","CTRL+C");String original=existing.toString();KeyboardShortcutsOverlay.validatePreferences(existing);check(existing.toString().equals(original),"keyboard validation leaves existing preference data unchanged");
  KeyboardShortcutsOverlay.validatePreferences(keyboard("方向键","ALT+LEFT"));check(true,"existing modifier and direction-key aliases remain supported");
  KeyboardShortcutsOverlay.validatePreferences(keyboard("组合","CTRL+ALT+SHIFT+A"));check(true,"four-key combinations remain valid");
  rejected(keyboard("","CTRL+A"),"empty names remain invalid without mutation");
  rejected(keyboard("重复","CTRL+CONTROL"),"duplicate aliases for the same key remain invalid");
  rejected(keyboard("过长","CTRL+ALT+SHIFT+META+A"),"five-key combinations remain invalid");
  rejected(keyboard("无效","CTRL+NOT_A_KEY"),"unsupported keys remain invalid");
  JSONArray entries=new JSONArray();for(int i=0;i<200;i++)entries.put(new JSONObject().put("name","快捷方式 "+i).put("keys","CTRL+A"));JSONObject maximum=new JSONObject().put("app:com.example",entries.toString());KeyboardShortcutsOverlay.validatePreferences(maximum);check(true,"the editor's 200-entry limit matches the portable format");
  entries.put(new JSONObject().put("name","超出").put("keys","CTRL+A"));rejected(new JSONObject().put("app:com.example",entries.toString()),"201 entries are rejected without mutation");
  QuickLauncherOverlay.ParentTouchGate gate=new QuickLauncherOverlay.ParentTouchGate();
  check(!gate.consume(0,10,0,false)&&!gate.consume(1,10,0,false),"ordinary parent tap remains available when no child was open");
  check(gate.consume(0,20,0,true)&&gate.consume(1,20,25,false),"tap beginning under a child stays blocked after that child closes before UP");
  check(!gate.consume(0,26,25,false)&&!gate.consume(1,26,25,false),"the next independent tap works immediately without a debounce delay");
  check(gate.consume(0,30,32,false)&&gate.consume(1,30,32,false),"zero-animation dismissal before parent DOWN cannot leak the original outside tap");
  check(!gate.consume(0,40,32,false)&&gate.consume(2,40,32,true)&&gate.consume(1,40,45,false),"a child appearing during an existing gesture blocks the rest of that gesture");
  check(gate.consume(0,50,45,true)&&gate.consume(3,50,52,false)&&!gate.consume(0,53,52,false),"CANCEL clears the blocked sequence without blocking a later gesture");
  gate.consume(3,53,52,false);
  check(gate.consume(0,60,60,false)&&!gate.consume(0,61,60,false),"equal timestamps remain part of the dismissal touch while later DOWN starts cleanly");
  System.out.println("RESULT "+checks+" overlay editor assertions passed");
 }
}
