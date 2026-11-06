package android.view;
public class KeyEvent {
 public static final int KEYCODE_UNKNOWN=0;
 public static int keyCodeFromString(String s){switch(s){case "KEYCODE_CTRL_LEFT":return 113;case "KEYCODE_ALT_LEFT":return 57;case "KEYCODE_SHIFT_LEFT":return 59;case "KEYCODE_META_LEFT":return 117;case "KEYCODE_DPAD_LEFT":return 21;case "KEYCODE_DPAD_RIGHT":return 22;case "KEYCODE_DPAD_UP":return 19;case "KEYCODE_DPAD_DOWN":return 20;case "KEYCODE_ENTER":return 66;}if(s.matches("KEYCODE_[A-Z]"))return 29+s.charAt(8)-'A';if(s.matches("KEYCODE_[0-9]"))return 7+s.charAt(8)-'0';return 0;}
}