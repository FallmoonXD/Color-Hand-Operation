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
    private final String resourcePackage;
    private final LayoutInflater inflater;
    LauncherWidgetStyle(Context host){this(host,false);}
    LauncherWidgetStyle(Context host,boolean followTheme){this(host,followTheme,PACKAGE,"LauncherTheme");}
    LauncherWidgetStyle(Context host,boolean followTheme,String resourcePackage,String themeName){
        this.host=host;this.resourcePackage=resourcePackage;
        Context themed=null;
        try{
            Context remote=host.createPackageContext(resourcePackage,0);
            Configuration config=new Configuration(host.getResources().getConfiguration());
            if(!followTheme)config.uiMode=(config.uiMode&~Configuration.UI_MODE_NIGHT_MASK)|Configuration.UI_MODE_NIGHT_NO;
            remote=remote.createConfigurationContext(config);
            int theme=remote.getResources().getIdentifier(themeName,"style",resourcePackage);
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
    private int id(String type,String name){return launcher==null?0:launcher.getResources().getIdentifier(name,type,resourcePackage);}
    int dp(float v){return PanelStyle.dp(host,v);}
    int dim(String name,float fallback){
        int key=id("dimen",name);return key==0?dp(fallback):launcher.getResources().getDimensionPixelSize(key);
    }
    int color(String name,int fallback){
        int key=id("color",name);return key==0?fallback:launcher.getColor(key);
    }
    String text(String name,String fallback){
        int key=id("string",name);return key==0?fallback:launcher.getString(key);
    }
    void textAppearance(TextView view,String name,int size){
        int key=id("style",name);
        if(key!=0)view.setTextAppearance(launcher,key);else view.setTextSize(size);
        int family=id("attr","fontFamily");
        if(key!=0&&family!=0)try(android.content.res.TypedArray values=launcher.obtainStyledAttributes(key,new int[]{family})){
            String nameOfFamily=values.getString(0);
            if(nameOfFamily!=null)view.setTypeface(android.graphics.Typeface.create(nameOfFamily,android.graphics.Typeface.NORMAL));
        }
    }
    Drawable confirmationBackground(){
        Drawable nativeBackground=drawable("coui_alert_dialog_background");
        return nativeBackground!=null?nativeBackground:new android.graphics.drawable.ColorDrawable(ColorOsTheme.card(host));
    }
    int confirmationBottomMargin(){
        int normal=dim("coui_dialog_layout_margin_vertical",32);
        try{
            android.view.WindowManager manager=host.getSystemService(android.view.WindowManager.class);
            int navigation=manager.getCurrentWindowMetrics().getWindowInsets().getInsets(android.view.WindowInsets.Type.navigationBars()).bottom;
            int mode=android.provider.Settings.Secure.getInt(host.getContentResolver(),"hide_navigationbar_enable",0);
            int adjusted=navigation<=0?normal:mode==2||mode==3?normal-navigation:dim("coui_dialog_layout_margin_bottom_with_navigation_bar",60)-navigation;
            return adjusted>0?adjusted:normal;
        }catch(RuntimeException unavailable){return normal;}
    }
    void confirmationOutline(View view){
        view.setOutlineProvider(new android.view.ViewOutlineProvider(){
            java.lang.reflect.Constructor<?> constructor;
            java.lang.reflect.Method draw;
            boolean adapter;
            float radius=dim("coui_round_corner_xxxxl",32),weight=1.1f;
            {
                try{
                    int version=Class.forName("com.oplus.os.OplusBuild$VERSION").getField("SDK_VERSION").getInt(null);
                    adapter=version>=38&&Boolean.TRUE.equals(Class.forName("com.oplus.view.OplusSmoothRoundedManager").getMethod("isSmoothRadiusOn").invoke(null));
                    Class<?> type=Class.forName(adapter?"com.oplus.graphics.OplusOutlineAdapter":"com.oplus.graphics.OplusOutline");
                    constructor=adapter?type.getConstructor(android.graphics.Outline.class,int.class):type.getConstructor(android.graphics.Outline.class);
                    draw=type.getMethod("setSmoothRoundRect",int.class,int.class,int.class,int.class,float.class,float.class);
                    radius=dim(adapter?"coui_round_corner_xxxxl_radius_large_16_1":"coui_round_corner_xxxxl_radius",adapter?45:25);
                    int key=id("dimen",adapter?"weight_16_1_large":"coui_round_corner_xxxl_weight");
                    if(key!=0)weight=launcher.getResources().getFloat(key);
                }catch(ReflectiveOperationException|RuntimeException|LinkageError unavailable){constructor=null;radius=dim("coui_round_corner_xxxxl",32);}
            }
            @Override public void getOutline(View target,android.graphics.Outline outline){
                if(constructor!=null)try{
                    Object nativeOutline=adapter?constructor.newInstance(outline,1):constructor.newInstance(outline);
                    draw.invoke(nativeOutline,0,0,target.getWidth(),target.getHeight(),radius,weight);return;
                }catch(ReflectiveOperationException|RuntimeException unavailable){constructor=null;}
                outline.setRoundRect(0,0,target.getWidth(),target.getHeight(),radius);
            }
        });
        view.setClipToOutline(true);
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
    Drawable selectionBackground(){
        // WallpaperResolver persists the same flag used to select the launcher's drawable.
        boolean bright=false;
        try{bright="true".equals(android.provider.Settings.Secure.getString(
                host.getContentResolver(),"launcher_wallpaper_bright"));}
        catch(SecurityException unavailable){
            android.util.Log.w("WidgetStyle","Wallpaper brightness flag unavailable",unavailable);
        }
        Drawable background=drawable(bright?
                "launcher_widget_delete_indicator_bright":"launcher_widget_delete_indicator");
        return background!=null?background:PanelStyle.shape(host,
                bright?0x28000000:0x28ffffff,28);
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
