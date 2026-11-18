package dev.edgeflow;
import java.util.Set;
/** Shared capabilities for the settings chooser and both sides of the LSP action channel. */
public final class BackendActions {
    public static final String POWER_MENU="power_menu", POWER_MENU_AVAILABLE="power_menu_available";
    public static final String SPLIT_SCREEN="split_screen", SPLIT_SCREEN_AVAILABLE="split_screen_available";
    private static final Set<String> NATIVE=Set.of("none","back","home","recents","search","menu","browser_forward","media_previous","media_play_pause","media_next","volume_up","volume_down","volume_mute","notifications","quick_settings","settings");
    private static final Set<String> WINDOWS=Set.of("quick_tools","widgets","quick_launcher","task_switcher","navigation_panel","virtual_touchpad","keyboard_shortcuts","screen_timeout","app_launcher");
    private static final Set<String> COMMANDS=Set.of("previous_app","screen_off","screenshot","one_hand","sidebar","assistant","close_app","flashlight","rotation","rotate_right","rotate_left","wifi","bluetooth","mobile_data","airplane","sound_mode","dnd","nfc","dark_mode","auto_brightness","screen_record","keyboard","color_inversion","battery_saver","location","hotspot_settings","eye_comfort_settings","accessibility_settings");
    public static boolean requiresProbe(String id){return POWER_MENU.equals(id)||SPLIT_SCREEN.equals(id);}
    public record Capabilities(boolean powerMenu,boolean splitScreen){
        public static final Capabilities NONE=new Capabilities(false,false);
        public boolean nativeAction(String id){
            return id!=null&&(NATIVE.contains(id)||id.startsWith("app:")||POWER_MENU.equals(id)&&powerMenu||SPLIT_SCREEN.equals(id)&&splitScreen);
        }
        public boolean supported(String id){return nativeAction(id)||window(id)||command(id);}
    }
    public static boolean window(String id){return id!=null&&(WINDOWS.contains(id)||id.equals("volume_up")||id.equals("volume_down")||ToolHostContract.isFolderAction(id));}
    public static boolean command(String id){return id!=null&&(COMMANDS.contains(id)||id.startsWith("shortcut:")||id.startsWith("launcher_shortcut:"));}
    private BackendActions(){}
}
