package dev.edgeflow;

import android.accessibilityservice.AccessibilityService;
import android.app.Dialog;
import android.content.Context;
import android.graphics.Color;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.view.Window;
import android.view.WindowManager;
import android.widget.FrameLayout;
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
  this.service=service;this.anchor=anchor.clone();
  GestureHandleConfig.Area area=HandleDisplayArea.current(service);boolean left=anchor[0]<area.displayWidth/2f;
  String[] actions=NavigationConfig.load(service);int gripGap=dp(7),gripHeight=dp(32),gutter=dp(3);
  // OHO+ soft keys center the column on the gesture DOWN and reserve navigation-bar
  // height at both ends. Respect any larger physical cutout or system-bar inset.
  int showNav=service.getResources().getIdentifier("config_showNavigationBar","bool","android");
  int navHeight=showNav!=0&&!service.getResources().getBoolean(showNav)?0:PanelGeometry.inset(service,"navigation_bar_height");
  int minY=Math.max(area.top,navHeight),maxY=Math.min(area.bottom,area.displayHeight-navHeight);
  if(maxY-minY<2*gutter+gripHeight+gripGap+actions.length){minY=area.top;maxY=area.bottom;}
  int clearance=service instanceof GestureService gestures?gestures.visibleHandleWidth(left):0;
  int inset=Math.min(clearance,Math.max(0,area.width()-1));
  int roomPerKey=(maxY-minY-2*gutter-gripHeight-gripGap)/actions.length;
  int size=Math.max(1,Math.min(dp(NavigationConfig.size(service)),Math.min(area.width()-inset,roomPerKey)));
  int height=2*gutter+gripHeight+gripGap+actions.length*size;
  final int topLimit=minY,bottomLimit=Math.max(minY,maxY-height);
  int theme=service.getResources().getIdentifier(PanelStyle.dark(service)?"ReplicaGlassDark":"ReplicaGlass","style",service.getPackageName());
  dialog=new Dialog(service,theme){@Override public boolean onTouchEvent(MotionEvent e){if(e.getActionMasked()==MotionEvent.ACTION_OUTSIDE){NavigationOverlay.this.dismiss();return true;}return super.onTouchEvent(e);}};
  dialog.setOnDismissListener(d->remove());
  Window w=dialog.getWindow();w.setType(WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY);
  Rail rail=new Rail(service);rail.setPadding(0,gutter,0,gutter);dialog.setContentView(rail);
  replica.ToolsBackdrop backdrop=PanelStyle.controlsWindow(service,w,Math.min(24,Math.round(size/(2f*service.getResources().getDisplayMetrics().density))),true);
  ImageView grip=new NavigationGlyph(service,null,"关闭虚拟导航键",true);
  grip.setTooltipText("点按关闭，拖动调整高度");grip.setOnClickListener(v->dismiss());
  rail.addKey(grip,true,size,gripHeight,0,backdrop);
  for(String action:actions){
   View button=actionButton(action);rail.addKey(button,false,size,size,rail.getChildCount()==1?gripGap:0,backdrop);
   button.setOnClickListener(v->ActionExecutor.execute(service,action,this.anchor.clone()));
   if(action.equals("home"))button.setOnLongClickListener(v->{ActionExecutor.execute(service,"assistant",this.anchor.clone());return true;});
  }
  dialog.setCanceledOnTouchOutside(false);
  w.addFlags(WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE|WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL|WindowManager.LayoutParams.FLAG_WATCH_OUTSIDE_TOUCH);
  w.getDecorView().setPadding(0,0,0,0);
  WindowManager.LayoutParams lp=w.getAttributes();lp.gravity=Gravity.TOP|Gravity.LEFT;lp.x=left?area.left+inset:area.right-size-inset;
  lp.y=Math.max(topLimit,Math.min(bottomLimit,Math.round(anchor[1]-height/2f)));lp.width=size;lp.height=height;
  lp.setTitle(service.getString(R.string.app_name)+" 虚拟导航键");PanelGeometry.screenCoordinates(lp);w.setAttributes(lp);
  grip.setOnTouchListener(new View.OnTouchListener(){
   float down;int initial;boolean moved;
   public boolean onTouch(View v,MotionEvent e){
    switch(e.getActionMasked()){
     case MotionEvent.ACTION_DOWN:down=e.getRawY();initial=w.getAttributes().y;moved=false;return true;
     case MotionEvent.ACTION_MOVE:
      float delta=e.getRawY()-down;if(Math.abs(delta)>android.view.ViewConfiguration.get(service).getScaledTouchSlop())moved=true;
      if(moved){WindowManager.LayoutParams a=w.getAttributes();a.y=Math.max(topLimit,Math.min(bottomLimit,initial+Math.round(delta)));w.setAttributes(a);}return true;
     case MotionEvent.ACTION_UP:if(!moved)v.performClick();return true;
     case MotionEvent.ACTION_CANCEL:return true;default:return false;
    }
   }
  });
  motion=ColorMotion.fromEdge(w,left,anchor[1]-lp.y);
  try{dialog.show();w.setLayout(size,height);motion.enter();}catch(RuntimeException e){android.util.Log.e("EdgeNavigation","Cannot attach",e);remove();}
 }
 /** One window blurs the page. The inset controls use the same optical layer as quick tools. */
 private static final class Rail extends LinearLayout {
  android.graphics.Bitmap shadowBitmap;
  final android.graphics.Paint shadow=new android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG);
  final int sideInset,gripInset;
  android.graphics.Rect[] shapes={};
  Rail(android.content.Context c){
   super(c);setOrientation(VERTICAL);sideInset=PanelStyle.dp(c,6);gripInset=PanelStyle.dp(c,3);
   shadow.setColor(0x44000000);shadow.setMaskFilter(new android.graphics.BlurMaskFilter(PanelStyle.dp(c,2),android.graphics.BlurMaskFilter.Blur.OUTER));
  }
  void addKey(View button,boolean grip,int slotSize,int height,int margin,replica.ToolsBackdrop backdrop){
   int y=grip?gripInset:sideInset;
   FrameLayout slot=new FrameLayout(getContext());
   View lens=new View(getContext());lens.setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_NO);
   lens.setBackground(new replica.ColorOsSurface(getContext(),80,0));
   FrameLayout.LayoutParams glass=new FrameLayout.LayoutParams(-1,-1);glass.setMargins(sideInset,y,sideInset,y);
   slot.addView(lens,glass);slot.addView(button,new FrameLayout.LayoutParams(-1,-1));
   LinearLayout.LayoutParams row=new LinearLayout.LayoutParams(-1,height);row.topMargin=margin;addView(slot,row);
   backdrop.preserve(lens,PanelStyle.dp(getContext(),80),0);
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
   super.onLayout(changed,l,t,r,b);android.graphics.Rect[] next=new android.graphics.Rect[getChildCount()];
   for(int i=0;i<next.length;i++){
    View v=getChildAt(i);android.graphics.Rect s=next[i]=new android.graphics.Rect(v.getLeft(),v.getTop(),v.getRight(),v.getBottom());
    s.inset(Math.min(sideInset,Math.max(0,(s.width()-1)/2)),Math.min(i==0?gripInset:sideInset,Math.max(0,(s.height()-1)/2)));
   }
   if(java.util.Arrays.equals(shapes,next)&&shadowBitmap!=null&&shadowBitmap.getWidth()==getWidth()&&shadowBitmap.getHeight()==getHeight())return;
   shapes=next;
   if(getWidth()==0||getHeight()==0){shadowBitmap=null;return;}
   // Cache only the soft outer shadow. A black shape punched out with clipOutPath leaves
   // dark stair steps where it intersects the partially transparent glass rim.
   shadowBitmap=android.graphics.Bitmap.createBitmap(getWidth(),getHeight(),android.graphics.Bitmap.Config.ARGB_8888);
   shadowBitmap.setDensity(android.graphics.Bitmap.DENSITY_NONE);
   android.graphics.Canvas shadows=new android.graphics.Canvas(shadowBitmap);
   shadows.translate(0,PanelStyle.dp(getContext(),1));
   for(android.graphics.Rect s:shapes){float radius=Math.min(s.width(),s.height())*.5f;
    shadows.drawRoundRect(s.left,s.top,s.right,s.bottom,radius,radius,shadow);}
  }
  @Override public void draw(android.graphics.Canvas c){
   if(shadowBitmap!=null)c.drawBitmap(shadowBitmap,0,0,null);
   super.draw(c);
  }
 }
 private TextView text(String value,int sp){
  TextView t=new TextView(service);t.setText(value);t.setTextSize(sp);t.setIncludeFontPadding(false);t.setGravity(Gravity.CENTER);
  t.setTextColor(Color.WHITE);t.setShadowLayer(dp(1),0,dp(.5f),0x70000000);
  Runnable font=()->t.setTypeface(replica.ColorOsTheme.typeface(service,500));font.run();replica.ColorOsTheme.watch(t,font);return t;
 }
 private View actionButton(String action){
  String title=ActionExecutor.title(action);
  switch(action){
   case "back":return new NavigationGlyph(service,"ic_sysbar_back",title,true);
   case "home":return new NavigationGlyph(service,"ic_sysbar_home",title,true);
   case "recents":return new NavigationGlyph(service,"ic_sysbar_recent",title,true);
  }
  String name=null;switch(action){case "volume_up":name="ic_expand_less_rounded";break;case "volume_down":name="ic_expand_more_rounded";break;case "media_previous":name="ic_oplus_media_panel_action_pre";break;case "media_play_pause":name="ic_oplus_media_panel_action_play";break;case "media_next":name="ic_oplus_media_panel_action_next";break;case "quick_tools":name="status_bar_qs_footer_more_icon_no_theme";break;case "quick_launcher":case "app_launcher":name="ic_apps_expressive";break;}
  String[] tileActions={"wifi","sound_mode","bluetooth","rotation","notifications","screen_record","screenshot","flashlight","dark_mode","screen_off","screen_timeout","close_app","mobile_data","airplane","battery_saver","location","dnd","nfc","settings","auto_brightness"};for(int i=0;i<tileActions.length;i++)if(tileActions[i].equals(action)){name=SystemIcons.tileName(i,false,2);break;}
  View button;
  if(name!=null)button=new NavigationGlyph(service,name,title,false);
  else{TextView label=text(title,12);label.setMaxLines(2);label.setEllipsize(android.text.TextUtils.TruncateAt.END);label.setAutoSizeTextTypeUniformWithConfiguration(9,13,1,android.util.TypedValue.COMPLEX_UNIT_SP);button=label;}
  button.setContentDescription(title);button.setTooltipText(title);return button;
 }
 private void dismiss(){if(removed||closing)return;closing=true;if(motion!=null)motion.exit(this::remove);else remove();}
 private void remove(){if(removed)return;removed=true;if(motion!=null)motion.cancel();try{dialog.dismiss();}catch(Exception ignored){}if(current==this)current=null;}
}
