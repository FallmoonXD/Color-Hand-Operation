package replica;

import android.content.SharedPreferences;
import dev.edgeflow.AppPrefs;

/** Stable IDs are used in gesture settings and exported configuration. */
public enum ToolAnimation {
    QUICK_TOOLS("quick_tools","快速工具",Style.VOLUME),
    WIDGETS("widgets","插件",Style.CENTER),
    LAUNCHER("quick_launcher","快速启动",Style.CENTER),
    TASKS("task_switcher","任务切换器",Style.CENTER),
    TOUCHPAD("virtual_touchpad","虚拟触控板",Style.CENTER),
    NAVIGATION("navigation_panel","虚拟导航键",Style.VOLUME),
    KEYBOARD("keyboard_shortcuts","键盘快捷方式",Style.SHEET),
    VOLUME_BUTTONS("volume_buttons","音量按钮",Style.VOLUME),
    MAGNIFIER("screen_magnifier","屏幕放大镜",Style.CENTER),
    SCREEN_MOVE("screen_free_move","屏幕自由移动",Style.SCREEN),
    TIMEOUT("screen_timeout","屏幕超时",Style.SHEET),
    OLD_LAUNCHER("app_launcher","应用启动器（兼容）",Style.SHEET);

    public enum Style {
        DEFAULT("default","工具默认"), VOLUME("volume","系统音量条"),
        EXPANDED_VOLUME("expanded_volume","展开音量面板"), SIDEBAR("sidebar","智能侧边栏"),
        ANCHOR("anchor","锚点菜单"), SLIDE("slide","侧向平移"), NONE("none","无动画"),
        CENTER("center","原地浮现"), SHEET("sheet","底部弹层"), SCREEN("screen","画面复位");
        public final String id,title;
        Style(String id,String title){this.id=id;this.title=title;}
    }
    public final String id,title;
    public final Style fallback;
    ToolAnimation(String id,String title,Style fallback){this.id=id;this.title=title;this.fallback=fallback;}
    public static ToolAnimation find(String id){
        for(ToolAnimation tool:values())if(tool.id.equals(id))return tool;
        return null;
    }
    public static Style[] choices(boolean entering){
        return entering?new Style[]{Style.DEFAULT,Style.VOLUME,Style.EXPANDED_VOLUME,Style.SIDEBAR,Style.ANCHOR,Style.SLIDE,Style.CENTER,Style.NONE}
                :new Style[]{Style.DEFAULT,Style.VOLUME,Style.SIDEBAR,Style.ANCHOR,Style.SLIDE,Style.CENTER,Style.NONE};
    }
    public String key(boolean entering){return "tool_motion/"+id+(entering?"/enter":"/exit");}
    public static boolean valid(String key,Object value){
        String[] parts=key.split("/",-1);
        if(parts.length!=3||!parts[0].equals("tool_motion")||find(parts[1])==null)return false;
        boolean enter=parts[2].equals("enter");
        if(!enter&&!parts[2].equals("exit"))return false;
        for(Style style:choices(enter))if(style.id.equals(value))return true;
        return false;
    }
    public Style selection(SharedPreferences prefs,boolean entering){
        String id=prefs.getString(key(entering),Style.DEFAULT.id);
        for(Style s:choices(entering))if(s.id.equals(id))return s;
        return Style.DEFAULT;
    }
    public Style resolve(Style selected){return selected==Style.DEFAULT?fallback:selected;}
    public Style active(android.content.Context c,boolean enter){return resolve(selection(AppPrefs.get(c),enter));}
    private void put(SharedPreferences.Editor editor,boolean enter,Style s){
        if(s==Style.DEFAULT)editor.remove(key(enter));else editor.putString(key(enter),s.id);
    }
    public void select(SharedPreferences prefs,boolean enter,Style s){
        if(!valid(key(enter),s.id))throw new IllegalArgumentException("Invalid tool animation");
        SharedPreferences.Editor edit=prefs.edit();put(edit,enter,s);edit.apply();
    }
    public void applyToAll(SharedPreferences prefs,boolean entering){
        Style style=selection(prefs,entering);
        SharedPreferences.Editor edit=prefs.edit();
        for(ToolAnimation tool:values())tool.put(edit,entering,style);
        edit.apply();
    }
}
