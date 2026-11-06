package dev.edgeflow;

import android.accessibilityservice.AccessibilityService;
import android.app.Dialog;
import android.content.Context;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.graphics.Point;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.view.Window;
import android.view.WindowManager;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import replica.ColorMotion;
import replica.PanelGeometry;
import replica.PanelStyle;
import replica.SystemIcons;

/** Floating native navigation actions; non-focusable so media keeps playing. */
public final class NavigationOverlay {
 private static NavigationOverlay current;
 private final AccessibilityService service;
 private final Dialog dialog;
 private final float[] anchor;
 private ColorMotion motion;
 private boolean removed,closing;
 public static void show(AccessibilityService service,float[] anchor){dismissAll();NavigationOverlay next=new NavigationOverlay(service,anchor);if(!next.removed)current=next;}
 public static void dismissAll(){if(current!=null)current.remove();}
 private int dp(float v){return PanelStyle.dp(service,v);}
 private NavigationOverlay(AccessibilityService service,float[] anchor){
  this.service=service;this.anchor=anchor.clone();SharedPreferences prefs=service.getSharedPreferences("edgeflow",Context.MODE_PRIVATE);boolean vertical=prefs.getBoolean("navigation_vertical",false);
  String[] actions=NavigationConfig.load(service);float buttonSize=NavigationConfig.size(service);
  Point screen=PanelGeometry.screen(service);int status=PanelGeometry.inset(service,"status_bar_height"),nav=PanelGeometry.inset(service,"navigation_bar_height");
  int width=Math.min(screen.x-dp(32),dp(vertical?buttonSize+82:Math.max(140,actions.length*buttonSize+70)));
  int height=Math.min(Math.max(dp(80),screen.y-status-nav-dp(12)),dp((vertical?actions.length:1)*buttonSize+48));
  int theme=service.getResources().getIdentifier(PanelStyle.dark(service)?"ReplicaGlassDark":"ReplicaGlass","style",service.getPackageName());
  dialog=new Dialog(service,theme){@Override public boolean onTouchEvent(MotionEvent e){if(e.getActionMasked()==MotionEvent.ACTION_OUTSIDE){NavigationOverlay.this.dismiss();return true;}return super.onTouchEvent(e);}};
  dialog.setOnDismissListener(d->remove());
  LinearLayout root=new LinearLayout(service);root.setOrientation(1);root.setPadding(dp(8),dp(6),dp(8),dp(8));
  LinearLayout toolbar=new LinearLayout(service);toolbar.setGravity(Gravity.CENTER_VERTICAL);TextView drag=text("导航键",13);drag.setContentDescription("拖动移动导航键面板");toolbar.addView(drag,new LinearLayout.LayoutParams(0,dp(34),1));
  TextView orient=text(vertical?"横排":"竖排",12);orient.setContentDescription("切换导航键排列");toolbar.addView(orient,new LinearLayout.LayoutParams(dp(40),dp(34)));orient.setOnClickListener(v->{prefs.edit().putBoolean("navigation_vertical",!vertical).apply();float[] next=this.anchor.clone();remove();show(service,next);});
  ImageView close=icon("ic_close_white","关闭导航键面板");close.setPadding(dp(8),dp(8),dp(8),dp(8));toolbar.addView(close,new LinearLayout.LayoutParams(dp(34),dp(34)));close.setOnClickListener(v->dismiss());root.addView(toolbar);
  LinearLayout keys=new LinearLayout(service);keys.setOrientation(vertical?1:0);keys.setGravity(Gravity.CENTER);root.addView(keys,new LinearLayout.LayoutParams(-1,height-dp(48)));
  for(String action:actions){View button=actionButton(action,buttonSize);button.setBackground(PanelStyle.toolButton(service,false));LinearLayout.LayoutParams lp=vertical?new LinearLayout.LayoutParams(-1,0,1):new LinearLayout.LayoutParams(0,-1,1);lp.setMargins(dp(2),dp(2),dp(2),dp(2));keys.addView(button,lp);button.setOnClickListener(v->ActionExecutor.execute(service,action,this.anchor.clone()));if(action.equals("home"))button.setOnLongClickListener(v->{ActionExecutor.execute(service,"assistant",this.anchor.clone());return true;});}
  dialog.setContentView(root);dialog.setCanceledOnTouchOutside(false);Window w=dialog.getWindow();w.setType(WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY);w.addFlags(WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE|WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL|WindowManager.LayoutParams.FLAG_WATCH_OUTSIDE_TOUCH);PanelStyle.window(service,w,24);
  WindowManager.LayoutParams p=w.getAttributes();p.gravity=Gravity.TOP|Gravity.LEFT;p.x=anchor[0]<screen.x/2?dp(16):screen.x-width-dp(16);p.y=Math.max(status,Math.min(screen.y-height-nav,Math.round(anchor[1]-height/2f)));p.width=width;p.height=height;p.setTitle(service.getString(dev.edgeflow.R.string.app_name)+" 导航键面板");PanelGeometry.screenCoordinates(p);w.setAttributes(p);
  drag.setOnTouchListener(new View.OnTouchListener(){float startX,startY;int baseX,baseY;public boolean onTouch(View v,MotionEvent e){if(e.getActionMasked()==MotionEvent.ACTION_DOWN){startX=e.getRawX();startY=e.getRawY();baseX=w.getAttributes().x;baseY=w.getAttributes().y;return true;}if(e.getActionMasked()==MotionEvent.ACTION_MOVE){WindowManager.LayoutParams q=w.getAttributes();q.x=Math.max(0,Math.min(screen.x-width,baseX+Math.round(e.getRawX()-startX)));q.y=Math.max(PanelGeometry.inset(service,"status_bar_height"),Math.min(screen.y-height-PanelGeometry.inset(service,"navigation_bar_height"),baseY+Math.round(e.getRawY()-startY)));w.setAttributes(q);return true;}return true;}});
  try{dialog.show();w.setLayout(width,height);motion=new ColorMotion(w);motion.enter();}catch(Exception e){android.util.Log.e("EdgeNavigation","Cannot attach",e);remove();}
 }
 private TextView text(String value,int size){TextView t=new TextView(service);t.setText(value);t.setTextSize(size);t.setTextColor(Color.WHITE);t.setIncludeFontPadding(false);Runnable font=()->t.setTypeface(replica.ColorOsTheme.typeface(t.getContext(),t.getTextSize()/t.getResources().getDisplayMetrics().scaledDensity>=18?600:500));font.run();replica.ColorOsTheme.watch(t,font);t.setGravity(Gravity.CENTER);return t;}
 private ImageView icon(String name,String label){ImageView i=new ImageView(service);i.setImageDrawable(SystemIcons.raw(service,name));i.setColorFilter(Color.WHITE);i.setScaleType(ImageView.ScaleType.FIT_CENTER);i.setContentDescription(label);return i;}
 private View actionButton(String action,float size){
  String name=null;switch(action){case "back":name="status_bar_qs_back_arrow";break;case "home":name="ic_sysbar_home";break;case "recents":name="ic_sysbar_recent";break;case "volume_up":name="ic_expand_less_rounded";break;case "volume_down":name="ic_expand_more_rounded";break;case "media_previous":name="ic_oplus_media_panel_action_pre";break;case "media_play_pause":name="ic_oplus_media_panel_action_play";break;case "media_next":name="ic_oplus_media_panel_action_next";break;case "quick_tools":name="status_bar_qs_footer_more_icon_no_theme";break;case "quick_launcher":case "app_launcher":name="ic_apps_expressive";break;}
  String[] tileActions={"wifi","sound_mode","bluetooth","rotation","notifications","screen_record","screenshot","flashlight","dark_mode","screen_off","screen_timeout","close_app","mobile_data","airplane","battery_saver","location","dnd","nfc","settings","auto_brightness"};for(int i=0;i<tileActions.length;i++)if(tileActions[i].equals(action)){name=SystemIcons.tileName(i,false,2);break;}
  String title=ActionExecutor.title(action);View button;
  if(name!=null){button=icon(name,title);int padding=dp(size*14f/58f);button.setPadding(padding,padding,padding,padding);}
  else{TextView label=text(title,12);label.setMaxLines(2);label.setPadding(dp(4),dp(2),dp(4),dp(2));button=label;}
  button.setContentDescription(title);button.setTooltipText(title);return button;
 }
 private void dismiss(){if(removed||closing)return;closing=true;if(motion!=null)motion.exit(this::remove);else remove();}
 private void remove(){if(removed)return;removed=true;if(motion!=null)motion.cancel();try{dialog.dismiss();}catch(Exception ignored){}if(current==this)current=null;}
}
