package dev.edgeflow;

import java.util.List;
import org.json.JSONArray;
import org.json.JSONObject;

/** Tests the exact all-publisher protocol/grouping and helper argument validation. */
public final class ShortcutCatalogCheck {
    private static int checks;
    private static void check(boolean value,String message){checks++;if(!value)throw new AssertionError(message);}
    interface Run {void run()throws Exception;}
    private static void rejected(Run run,String message){boolean failed=false;try{run.run();}catch(Exception expected){failed=true;}check(failed,message);}
    private static JSONObject item(int user,String pkg,String id,String label,boolean enabled)throws Exception{return new JSONObject().put("key",ShortcutEntry.create(user,pkg,id)).put("label",label).put("enabled",enabled).put("dynamic",true).put("pinned",false).put("manifest",false);}
    private static String raw(JSONArray values){return "helper startup diagnostic\nEDGE_SHORTCUTS="+values+"\n";}
    private static JSONArray parsed(JSONArray values)throws Exception{return ShortcutCatalog.parse(0,raw(values),0,null);}
    public static void main(String[] args)throws Exception{
        check(ShortcutCatalog.group(parsed(new JSONArray())).isEmpty(),"empty catalog cannot manufacture launcher applications");
        JSONObject disabled=item(0,"com.beta","disabled","暂不可用",false).put("disabled","需要先登录");
        JSONArray input=new JSONArray().put(item(0,"com.beta","z","Zulu",true)).put(item(0,"com.alpha","id:你好","Exception: Debug; permission denied",true)).put(disabled).put(item(0,"com.beta","a","Alpha",true));
        JSONArray result=parsed(input);List<ShortcutCatalog.Group> groups=ShortcutCatalog.group(result);
        check(groups.size()==2&&groups.get(0).packageName.equals("com.beta")&&groups.get(1).packageName.equals("com.alpha"),"only actual publishing packages are grouped");
        ShortcutCatalog.Group beta=groups.get(0);check(beta.entries.size()==3,"disabled actual shortcuts remain present in publisher group");
        check(beta.entries.get(0).label.equals("Alpha")&&beta.entries.get(1).label.equals("Zulu"),"child labels are sorted rather than protocol order");
        ShortcutCatalog.Entry selected=beta.selected(disabled.getString("key"));check(selected!=null&&!selected.enabled&&selected.disabled.equals("需要先登录"),"selected disabled entry retains reason for summary");
        check(groups.get(1).selected(disabled.getString("key"))==null,"selection identity cannot bleed into another publisher");
        check(groups.get(1).entries.get(0).label.equals("Exception: Debug; permission denied"),"publisher labels that resemble shell errors remain ordinary data");
        rejected(()->groups.clear(),"catalog group list is immutable");rejected(()->beta.entries.clear(),"catalog child list is immutable");
        JSONArray many=new JSONArray();for(int i=0;i<300;i++)many.put(item(0,"com.publisher"+i,"same-id","Action "+i,true));check(parsed(many).length()==300&&ShortcutCatalog.group(parsed(many)).size()==300,"all-publisher result is not truncated at former per-app 100 limit");
        JSONArray duplicateId=new JSONArray().put(item(0,"com.alpha","same","A",true)).put(item(0,"com.beta","same","B",true));check(parsed(duplicateId).length()==2,"same shortcut id is valid across distinct publishers");
        rejected(()->parsed(new JSONArray().put(input.getJSONObject(0)).put(input.getJSONObject(0))),"duplicate canonical identity rejected");
        rejected(()->ShortcutCatalog.parse(0,raw(input),0,"com.beta"),"legacy package-scoped parser still rejects other publishers");
        check(ShortcutCatalog.parse(0,raw(new JSONArray().put(disabled)),0,"com.beta").length()==1,"legacy package-scoped parser still accepts its publisher");
        rejected(()->parsed(new JSONArray().put(item(10,"com.alpha","profile","Other profile",true))),"current-user catalog rejects cross-user identity");
        check(ShortcutCatalog.parse(0,raw(new JSONArray().put(item(10,"com.alpha","profile","Work",true))),10,null).length()==1,"explicit current secondary user is preserved");
        rejected(()->ShortcutCatalog.parse(1,raw(input),0,null),"nonzero helper exit cannot be accepted based on marker");
        rejected(()->ShortcutCatalog.parse(0,"no marker",0,null),"missing protocol marker rejected");
        rejected(()->ShortcutCatalog.parse(0,raw(input)+raw(input),0,null),"duplicate protocol marker rejected");
        rejected(()->ShortcutCatalog.parse(0,"EDGE_SHORTCUTS="+input+" false",0,null),"trailing JSON data rejected");
        rejected(()->ShortcutCatalog.parse(0,"EDGE_SHORTCUTS={}",0,null),"object cannot replace protocol array");
        for(String field:new String[]{"enabled","dynamic","pinned","manifest"}){JSONObject wrong=new JSONObject(disabled.toString()).put(field,"false");rejected(()->parsed(new JSONArray().put(wrong)),"state must be boolean: "+field);}
        for(Object value:new Object[]{-1,1.5,2147483648L,"123"}){JSONObject wrong=new JSONObject(disabled.toString()).put("iconRes",value);rejected(()->parsed(new JSONArray().put(wrong)),"resource id rejects wrong range/type "+value);}
        JSONObject hugeIcon=new JSONObject(disabled.toString()).put("icon","A".repeat(32769));rejected(()->parsed(new JSONArray().put(hugeIcon)),"oversize icon rejected before cache writes");
        JSONObject hugeLabel=new JSONObject(disabled.toString()).put("label","x".repeat(4097));rejected(()->parsed(new JSONArray().put(hugeLabel)),"oversize label rejected");
        JSONObject badKey=new JSONObject(disabled.toString()).put("key","app:com.alpha/com.alpha.Main");rejected(()->parsed(new JSONArray().put(badKey)),"generic launch intent cannot impersonate a system shortcut");
        for(String valid:new String[]{"0","10","21474"})check(ShortcutBridge.parseUser(valid)==Integer.parseInt(valid),"canonical helper user accepted "+valid);
        for(String invalid:new String[]{"-1","01"," 0","0 ","21475","999999","0;id","$(id)","0\n"})rejected(()->ShortcutBridge.parseUser(invalid),"noncanonical helper user rejected "+invalid.replace('\n',' '));
        System.out.println("PASS "+checks+" shortcut catalog protocol/grouping assertions");
    }
}
