package dev.edgeflow;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import org.json.JSONTokener;

/** Strict helper protocol and publisher grouping, independent of Android UI/lifecycle. */
final class ShortcutCatalog {
    static final int MAX_ENTRIES=4096,MAX_OUTPUT=16*1024*1024;
    static JSONArray parse(int exit,String output,int user,String packageName)throws IOException,JSONException{
        if(exit!=0)throw new IOException("系统快捷方式接口不可用，请检查 Root 授权");
        if(output==null||output.length()>MAX_OUTPUT)throw new IOException("快捷方式响应过大");
        String payload=null;for(String line:output.split("\\r?\\n"))if(line.startsWith("EDGE_SHORTCUTS=")){if(payload!=null)throw new IOException("快捷方式响应重复");payload=line.substring(15).trim();}
        if(payload==null)throw new IOException("未收到系统快捷方式数据");
        JSONTokener tokens=new JSONTokener(payload);Object parsed=tokens.nextValue();
        if(!(parsed instanceof JSONArray)||tokens.nextClean()!=0)throw new JSONException("快捷方式响应不是完整数组");
        JSONArray result=(JSONArray)parsed;if(result.length()>MAX_ENTRIES)throw new IOException("快捷方式结果超出限制");HashSet<String> seen=new HashSet<>();
        for(int i=0;i<result.length();i++){
            JSONObject item=result.getJSONObject(i);Object raw=item.get("key");
            if(!(raw instanceof String)||!ShortcutEntry.valid((String)raw)||!seen.add((String)raw))throw new JSONException("快捷方式标识无效或重复");
            ShortcutEntry entry=ShortcutEntry.parse((String)raw);if(entry.user!=user||packageName!=null&&!packageName.equals(entry.packageName))throw new IOException("快捷方式来源不匹配");
            if(!(item.get("label") instanceof String)||item.getString("label").length()>4096)throw new JSONException("快捷方式名称无效");
            for(String field:new String[]{"enabled","dynamic","pinned","manifest"})if(!(item.get(field) instanceof Boolean))throw new JSONException("快捷方式状态类型无效："+field);
            for(String field:new String[]{"disabled","icon"})if(item.has(field)&&!(item.get(field) instanceof String))throw new JSONException("快捷方式字段类型无效："+field);
            if(item.optString("disabled").length()>4096||item.optString("icon").length()>32768)throw new JSONException("快捷方式字段过大");
            if(item.has("iconRes")){Object resource=item.get("iconRes");if(!(resource instanceof Number)||((Number)resource).doubleValue()!=((Number)resource).intValue()||((Number)resource).intValue()<0)throw new JSONException("快捷方式图标资源无效");}
        }
        return result;
    }
    static List<Group> group(JSONArray values)throws JSONException{
        LinkedHashMap<String,ArrayList<Entry>> packages=new LinkedHashMap<>();
        for(int i=0;i<values.length();i++){JSONObject item=values.getJSONObject(i);Entry entry=new Entry(item);String pkg=ShortcutEntry.parse(entry.key).packageName;packages.computeIfAbsent(pkg,k->new ArrayList<>()).add(entry);}
        ArrayList<Group> groups=new ArrayList<>();for(java.util.Map.Entry<String,ArrayList<Entry>> item:packages.entrySet()){
            item.getValue().sort((a,b)->{int order=a.label.compareToIgnoreCase(b.label);return order==0?a.key.compareTo(b.key):order;});groups.add(new Group(item.getKey(),item.getValue()));
        }
        return Collections.unmodifiableList(groups);
    }
    static final class Entry {
        final String key,label,disabled;final boolean enabled;
        Entry(JSONObject item)throws JSONException{key=item.getString("key");label=item.getString("label");enabled=item.getBoolean("enabled");disabled=item.optString("disabled","此快捷方式已停用");}
    }
    static final class Group {
        final String packageName;final List<Entry> entries;
        Group(String pkg,List<Entry> values){packageName=pkg;entries=Collections.unmodifiableList(values);}
        Entry selected(String key){for(Entry entry:entries)if(entry.key.equals(key))return entry;return null;}
    }
    private ShortcutCatalog(){}
}
