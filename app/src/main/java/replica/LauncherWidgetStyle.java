package replica;

import android.content.Context;
import android.content.pm.PackageManager;
import android.content.res.Configuration;
import android.graphics.Color;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.view.ContextThemeWrapper;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

/** Read the installed ColorOS launcher's resources without executing its classes in the widget host. */
final class LauncherWidgetStyle {
    static final String PACKAGE="com.android.launcher";
    private final Context host,launcher;
    private final LayoutInflater inflater;
    LauncherWidgetStyle(Context host){
        this.host=host;
        Context themed=null;
        try{
            Context remote=host.createPackageContext(PACKAGE,0);
            Configuration config=new Configuration();config.uiMode=Configuration.UI_MODE_NIGHT_NO;
            remote=remote.createConfigurationContext(config);
            int theme=remote.getResources().getIdentifier("LauncherTheme","style",PACKAGE);
            themed=new ContextThemeWrapper(remote,theme==0?android.R.style.Theme_Material_Light:theme);
        }catch(PackageManager.NameNotFoundException|RuntimeException unavailable){/* AOSP and other launchers. */}
        launcher=themed;
        if(launcher==null){inflater=null;return;}
        inflater=LayoutInflater.from(launcher).cloneInContext(launcher);
        inflater.setFactory((name,context,attrs)->{
            if(name.equals("com.android.launcher.layout.ItemResizeFrame"))return new WidgetSelectionFrame(host,this);
            if(name.equals("com.coui.appcompat.view.COUILinearLayout"))return new LinearLayout(context,attrs);
            return null;
        });
    }
    private int id(String type,String name){return launcher==null?0:launcher.getResources().getIdentifier(name,type,PACKAGE);}
    int dp(float v){return PanelStyle.dp(host,v);}
    int dim(String name,float fallback){
        int key=id("dimen",name);return key==0?dp(fallback):launcher.getResources().getDimensionPixelSize(key);
    }
    int color(String name,int fallback){
        int key=id("color",name);return key==0?fallback:launcher.getColor(key);
    }
    Drawable drawable(String name){
        int key=id("drawable",name);
        if(key!=0)try{return launcher.getDrawable(key).mutate();}catch(RuntimeException unavailable){}
        return null;
    }
    Drawable menuBackground(){
        Drawable nativeBackground=drawable("coui_popup_window_background");
        if(nativeBackground!=null&&!(nativeBackground instanceof GradientDrawable))return nativeBackground;
        GradientDrawable shape=nativeBackground instanceof GradientDrawable?(GradientDrawable)nativeBackground:new GradientDrawable();
        shape.setColor(color("launcher_deep_shortcut_bg_color",Color.WHITE));
        shape.setCornerRadius(dim("coui_popup_round_frame_layout_corner_radius",28));return shape;
    }
    private View inflate(String name,android.view.ViewGroup parent){
        int key=id("layout",name);
        if(key!=0)try{return inflater.inflate(key,parent,false);}catch(RuntimeException unavailable){
            android.util.Log.w("WidgetStyle","Cannot inflate launcher "+name,unavailable);
        }
        return null;
    }
    View menuRow(LinearLayout parent,String label,String iconName){
        View row=inflate("coui_popup_list_window_item",parent);
        View textView=row==null?null:row.findViewById(id("id","popup_list_window_item_title"));
        View iconView=row==null?null:row.findViewById(id("id","popup_list_window_item_icon"));
        TextView title=textView instanceof TextView t?t:null;ImageView icon=iconView instanceof ImageView i?i:null;
        if(title==null||icon==null){
            LinearLayout layout=new LinearLayout(host);layout.setGravity(Gravity.CENTER_VERTICAL);
            layout.setPadding(dp(20),dp(8),dp(20),dp(8));layout.setMinimumHeight(dp(40));
            icon=new ImageView(host);LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(dp(20),dp(20));lp.setMarginEnd(dp(12));layout.addView(icon,lp);
            title=new TextView(host);title.setTextSize(16);layout.addView(title,new LinearLayout.LayoutParams(0,-2,1));row=layout;
        }else{
            View description=row.findViewById(id("id","popup_list_window_item_description"));
            if(description!=null)description.setVisibility(View.GONE);
        }
        Drawable glyph=drawable(iconName);
        boolean launcherShortcut=iconName.startsWith("ic_system_shortcut_")||iconName.equals("area_widget_edit");
        if(glyph==null){
            glyph=host.getDrawable(iconName.contains("remove")||iconName.contains("delete")?android.R.drawable.ic_menu_delete:
                    iconName.contains("add")?android.R.drawable.ic_menu_add:android.R.drawable.ic_menu_edit).mutate();
            launcherShortcut=false;
        }
        if(!launcherShortcut)glyph.setTint(color("launcher_edit_shortcut_fg_color",0xe5000000));
        icon.setImageDrawable(glyph);if(label.equals("添加页面"))icon.setRotation(45);icon.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);
        title.setTextColor(color("coui_popup_list_window_text_color_normal_light",Color.BLACK));title.setText(label);
        title.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);
        row.setContentDescription(label);row.setFocusable(true);row.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_YES);
        row.setAccessibilityDelegate(new View.AccessibilityDelegate(){
            @Override public void onInitializeAccessibilityNodeInfo(View v,android.view.accessibility.AccessibilityNodeInfo info){
                super.onInitializeAccessibilityNodeInfo(v,info);info.setClassName(android.widget.Button.class.getName());
            }
        });
        row.setBackground(ColorDialogBuilder.touchBackground(host,Color.TRANSPARENT,12));return row;
    }
    WidgetSelectionFrame selectionFrame(FrameLayout cell){
        View nativeLayout=inflate("item_resize_frame",cell);
        return nativeLayout instanceof WidgetSelectionFrame frame?frame:new WidgetSelectionFrame(host,this);
    }
    int integer(String name,int fallback){int key=id("integer",name);return key==0?fallback:launcher.getResources().getInteger(key);}
}
