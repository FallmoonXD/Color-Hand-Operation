package dev.edgeflow;

import android.accessibilityservice.AccessibilityService;
import android.app.Dialog;
import android.content.Context;
import android.content.SharedPreferences;
import android.graphics.Color;
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
  this.service=service;this.anchor=anchor.clone();SharedPreferences prefs=AppPrefs.get(service);
  GestureHandleConfig.Area area=HandleDisplayArea.current(service);boolean left=anchor[0]<area.displayWidth/2f;
  String[] actions=NavigationConfig.load(service);int gap=dp(1),gripHeight=dp(32),gutter=dp(3),clearance=0;
  for(int i=0;i<HandleSettings.pairCount(prefs)*2;i++){
   String id=GestureHandleConfig.IDS[i];if(GestureHandleConfig.isLeft(id)!=left||!prefs.getBoolean(id+"_enabled",!GestureHandleConfig.isExtra(id)))continue;
   int[] bounds=GestureHandleConfig.bounds(id,HandleSettings.read(prefs,id).width,0,1,service.getResources().getDisplayMetrics().density,area);
   clearance=Math.max(clearance,bounds[2]);
  }
  int inset=Math.min(clearance+dp(4),Math.max(0,area.width()-1));
  int roomPerKey=(area.height()-2*gutter-gripHeight)/actions.length-gap;
  int size=Math.max(1,Math.min(dp(NavigationConfig.size(service)),Math.min(area.width()-inset,roomPerKey)));
  int height=2*gutter+gripHeight+actions.length*(size+gap);
  int theme=service.getResources().getIdentifier(PanelStyle.dark(service)?"ReplicaGlassDark":"ReplicaGlass","style",service.getPackageName());
  dialog=new Dialog(service,theme){@Override public boolean onTouchEvent(MotionEvent e){if(e.getActionMasked()==MotionEvent.ACTION_OUTSIDE){NavigationOverlay.this.dismiss();return true;}return super.onTouchEvent(e);}};
  dialog.setOnDismissListener(d->remove());
  Rail rail=new Rail(service);rail.setPadding(0,gutter,0,gutter);
  ImageView grip=icon("ic_close_white","关闭导航键面板");grip.setScaleType(ImageView.ScaleType.FIT_CENTER);
  grip.setTooltipText("点按关闭，拖动调整高度");grip.setOnClickListener(v->dismiss());rail.feedback(grip,true,size);
  rail.addView(grip,new LinearLayout.LayoutParams(-1,gripHeight));
  for(String action:actions){
   View button=actionButton(action);rail.feedback(button,false,size);
   LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(-1,size);lp.topMargin=gap;rail.addView(button,lp);
   button.setOnClickListener(v->ActionExecutor.execute(service,action,this.anchor.clone()));
   if(action.equals("home"))button.setOnLongClickListener(v->{ActionExecutor.execute(service,"assistant",this.anchor.clone());return true;});
  }
  dialog.setContentView(rail);dialog.setCanceledOnTouchOutside(false);Window w=dialog.getWindow();w.setType(WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY);
  w.addFlags(WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE|WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL|WindowManager.LayoutParams.FLAG_WATCH_OUTSIDE_TOUCH);
  w.clearFlags(WindowManager.LayoutParams.FLAG_DIM_BEHIND);w.setBackgroundDrawableResource(android.R.color.transparent);w.getDecorView().setPadding(0,0,0,0);
  w.setElevation(0);w.getDecorView().setClipToOutline(false);
  rail.glass.attach(w);
  WindowManager.LayoutParams lp=w.getAttributes();lp.gravity=Gravity.TOP|Gravity.LEFT;lp.x=left?area.left+inset:area.right-size-inset;
  lp.y=Math.max(area.top,Math.min(area.bottom-height,Math.round(anchor[1]-height/2f)));lp.width=size;lp.height=height;
  lp.setTitle(service.getString(R.string.app_name)+" 导航键面板");PanelGeometry.screenCoordinates(lp);w.setAttributes(lp);
  grip.setOnTouchListener(new View.OnTouchListener(){
   float down;int initial;boolean moved;
   public boolean onTouch(View v,MotionEvent e){
    switch(e.getActionMasked()){
     case MotionEvent.ACTION_DOWN:down=e.getRawY();initial=w.getAttributes().y;moved=false;return true;
     case MotionEvent.ACTION_MOVE:
      float delta=e.getRawY()-down;if(Math.abs(delta)>android.view.ViewConfiguration.get(service).getScaledTouchSlop())moved=true;
      if(moved){WindowManager.LayoutParams a=w.getAttributes();a.y=Math.max(area.top,Math.min(area.bottom-height,initial+Math.round(delta)));w.setAttributes(a);}return true;
     case MotionEvent.ACTION_UP:if(!moved)v.performClick();return true;
     case MotionEvent.ACTION_CANCEL:return true;default:return false;
    }
   }
  });
  try{dialog.show();w.setLayout(size,height);motion=new ColorMotion(w);motion.enter();}catch(RuntimeException e){android.util.Log.e("EdgeNavigation","Cannot attach",e);remove();}
 }
 /** Touch targets fill each slot. Only the inset shapes cast a shadow and sample the backdrop. */
 private static final class Rail extends LinearLayout {
  final replica.ColorOsSurface glass;
  final android.graphics.Path buttons=new android.graphics.Path();
  final android.graphics.Paint shadow=new android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG);
  final int sideInset,gripInset;
  android.graphics.Rect[] shapes={};
  Rail(android.content.Context c){
   super(c);setOrientation(VERTICAL);sideInset=PanelStyle.dp(c,6);gripInset=PanelStyle.dp(c,3);
   glass=new replica.ColorOsSurface(c,0,2);glass.setPillBounds(shapes);setBackground(glass);
   shadow.setColor(0xff000000);shadow.setShadowLayer(PanelStyle.dp(c,2),0,PanelStyle.dp(c,1),0x44000000);
  }
  void feedback(View button,boolean grip,int slotSize){
   int y=grip?gripInset:sideInset;
   android.graphics.drawable.Drawable mask=new android.graphics.drawable.InsetDrawable(PanelStyle.shape(getContext(),Color.WHITE,80),sideInset,y,sideInset,y);
   button.setBackground(new android.graphics.drawable.RippleDrawable(android.content.res.ColorStateList.valueOf(0x33808080),null,mask));
   // RippleDrawable takes padding from its mask. Size the glyph after installing it.
   if(button instanceof ImageView){
    boolean nativeSize=((ImageView)button).getScaleType()==ImageView.ScaleType.CENTER_INSIDE;
    int padding=grip||nativeSize?PanelStyle.dp(getContext(),8):Math.round(slotSize*.25f)+gripInset;
    button.setPadding(padding,padding,padding,padding);
   }else{int x=sideInset+gripInset;button.setPadding(x,sideInset,x,sideInset);}
  }
  @Override protected void onLayout(boolean changed,int l,int t,int r,int b){
   super.onLayout(changed,l,t,r,b);buttons.rewind();shapes=new android.graphics.Rect[getChildCount()];
   for(int i=0;i<shapes.length;i++){
    View v=getChildAt(i);android.graphics.Rect s=shapes[i]=new android.graphics.Rect(v.getLeft(),v.getTop(),v.getRight(),v.getBottom());
    s.inset(Math.min(sideInset,Math.max(0,(s.width()-1)/2)),Math.min(i==0?gripInset:sideInset,Math.max(0,(s.height()-1)/2)));
    float radius=Math.min(s.width(),s.height())*.5f;buttons.addRoundRect(s.left,s.top,s.right,s.bottom,radius,radius,android.graphics.Path.Direction.CW);
   }
   glass.setPillBounds(shapes);
  }
  @Override public void draw(android.graphics.Canvas c){
   int save=c.save();c.clipOutPath(buttons);
   for(android.graphics.Rect s:shapes){float r=Math.min(s.width(),s.height())*.5f;c.drawRoundRect(s.left,s.top,s.right,s.bottom,r,r,shadow);}
   c.restoreToCount(save);super.draw(c);
  }
 }
 private int foreground(){return PanelStyle.dark(service)||!replica.ColorOsTheme.materialBlur(service)?Color.WHITE:0xff22272d;}
 private TextView text(String value,int sp){
  TextView t=new TextView(service);t.setText(value);t.setTextSize(sp);t.setIncludeFontPadding(false);t.setGravity(Gravity.CENTER);
  Runnable style=()->{int fg=foreground();t.setTextColor(fg);t.setShadowLayer(dp(1),0,0,fg==Color.WHITE?0xb3000000:0xc0ffffff);t.setTypeface(replica.ColorOsTheme.typeface(service,500));};
  style.run();replica.ColorOsTheme.watch(t,style);return t;
 }
 private ImageView icon(String name,String label){
  ImageView image=new ImageView(service);image.setScaleType(ImageView.ScaleType.FIT_CENTER);image.setContentDescription(label);
  Runnable style=()->{
   android.graphics.drawable.Drawable glyph=SystemIcons.raw(service,name);if(glyph==null)return;
   int fg=foreground();glyph=glyph.mutate();android.graphics.drawable.Drawable.ConstantState state=glyph.getConstantState();glyph.setTint(fg);
   if(state==null){image.setImageDrawable(glyph);return;}
   // A thin opposing keyline keeps the symbol legible across light and dark content behind the glass.
   int stroke=Math.max(1,dp(1));int[][] offsets={{-stroke,0},{stroke,0},{0,-stroke},{0,stroke},{0,0}};
   android.graphics.drawable.Drawable[] layers=new android.graphics.drawable.Drawable[offsets.length];
   for(int i=0;i<4;i++){layers[i]=state.newDrawable(service.getResources()).mutate();layers[i].setTint(fg==Color.WHITE?0x90000000:0xb3ffffff);}layers[4]=glyph;
   android.graphics.drawable.LayerDrawable icon=new android.graphics.drawable.LayerDrawable(layers);
   icon.setPaddingMode(android.graphics.drawable.LayerDrawable.PADDING_MODE_STACK);
   for(int i=0;i<layers.length;i++){int x=offsets[i][0],y=offsets[i][1];icon.setLayerInset(i,stroke+x,stroke+y,stroke-x,stroke-y);}
   image.setImageDrawable(icon);
  };
  style.run();replica.ColorOsTheme.watch(image,style);return image;
 }
 private ImageView navigationKey(String resource,String title){
  ImageView image=new ImageView(service);
  image.setContentDescription(title);image.setTooltipText(title);
  image.setScaleType(ImageView.ScaleType.CENTER_INSIDE);
  Runnable style=()->{
   image.setImageDrawable(SystemIcons.raw(service,resource));
   image.setColorFilter(foreground()==Color.WHITE?Color.WHITE:Color.BLACK);
  };
  style.run();replica.ColorOsTheme.watch(image,style);return image;
 }
 private View actionButton(String action){
  String title=ActionExecutor.title(action);
  switch(action){
   case "back":return navigationKey("ic_sysbar_back",title);
   case "home":return navigationKey("ic_sysbar_home",title);
   case "recents":return navigationKey("ic_sysbar_recent",title);
  }
  String name=null;switch(action){case "volume_up":name="ic_expand_less_rounded";break;case "volume_down":name="ic_expand_more_rounded";break;case "media_previous":name="ic_oplus_media_panel_action_pre";break;case "media_play_pause":name="ic_oplus_media_panel_action_play";break;case "media_next":name="ic_oplus_media_panel_action_next";break;case "quick_tools":name="status_bar_qs_footer_more_icon_no_theme";break;case "quick_launcher":case "app_launcher":name="ic_apps_expressive";break;}
  String[] tileActions={"wifi","sound_mode","bluetooth","rotation","notifications","screen_record","screenshot","flashlight","dark_mode","screen_off","screen_timeout","close_app","mobile_data","airplane","battery_saver","location","dnd","nfc","settings","auto_brightness"};for(int i=0;i<tileActions.length;i++)if(tileActions[i].equals(action)){name=SystemIcons.tileName(i,false,2);break;}
  View button;
  if(name!=null)button=icon(name,title);
  else{TextView label=text(title,12);label.setMaxLines(2);label.setEllipsize(android.text.TextUtils.TruncateAt.END);label.setAutoSizeTextTypeUniformWithConfiguration(9,13,1,android.util.TypedValue.COMPLEX_UNIT_SP);button=label;}
  button.setContentDescription(title);button.setTooltipText(title);return button;
 }
 private void dismiss(){if(removed||closing)return;closing=true;if(motion!=null)motion.exit(this::remove);else remove();}
 private void remove(){if(removed)return;removed=true;if(motion!=null)motion.cancel();try{dialog.dismiss();}catch(Exception ignored){}if(current==this)current=null;}
}
