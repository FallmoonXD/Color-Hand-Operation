package replica;
import android.app.*;import android.appwidget.*;import android.content.*;import android.os.*;import android.view.*;import android.widget.*;import android.text.*;import org.json.*;import java.util.*;
/** Shared widget layout and editors, hosted either in an overlay or in the settings preview. */
final class WidgetPanel extends android.view.ContextThemeWrapper {
 interface Setup { void request(ComponentName provider,int id,boolean bound,boolean existing,int page); }
 private final Window window;
 private final boolean overlay;
 private final Setup setup;
 final AppWidgetHost host;
 private final AppWidgetManager manager;
 private final SharedPreferences prefs;
 private JSONArray items;
 private int page,pages,selected=-1,boardHeight;
 private float[] gesture;
 private boolean editing,closed,listening;
 private LinearLayout root,bar;private Page canvas;
 private ColorPopupMenu editorMenu;private AlertDialog editorDialog;private WidgetProviderPicker providerPicker;private int editorGeneration;
 private int dp(float n){return PanelStyle.dp(this,n);}
 private void toast(String message){Toast.makeText(this,message,Toast.LENGTH_SHORT).show();}
 WidgetPanel(Context base, Window window, float[] anchor, boolean overlay, Setup setup){
  super(base,base.getResources().getIdentifier(PanelStyle.dark(base)?"ReplicaGlassDark":"ReplicaGlass","style",base.getPackageName()));
  this.window=window;this.overlay=overlay;this.setup=setup;gesture=anchor.clone();
  prefs=getSharedPreferences("replica_widgets",MODE_PRIVATE);manager=AppWidgetManager.getInstance(this);
  host=new AppWidgetHost(this,WidgetMigration.HOST_ID){@Override protected AppWidgetHostView onCreateView(Context c,int id,AppWidgetProviderInfo info){return new SizedWidgetHostView(c);}};
  readLayout();
 }
 private void readLayout(){
  pages=Math.max(1,prefs.getInt("pages",1));page=Math.max(0,Math.min(pages-1,prefs.getInt("page",0)));
  int defaultHeight=prefs.contains("layout_v3")?Math.round((getResources().getDisplayMetrics().heightPixels*.474f-dp(44))/getResources().getDisplayMetrics().density):400;
  boardHeight=prefs.getInt("height_v4",defaultHeight);
  try{items=new JSONArray(prefs.getString("layout_v3","[]"));}catch(JSONException malformed){items=new JSONArray();}
  if(!prefs.contains("layout_v3")){int old=prefs.getInt("alipay_id",-1);if(old>=0&&manager.getAppWidgetInfo(old)!=null)items.put(item(old,0,0,0,4,2));save();}
 }
 void start(){if(!closed&&!listening){host.startListening();listening=true;}}
 void stop(){if(listening){listening=false;host.stopListening();}}
 void close(){closed=true;dismissEditors();stop();}
 void reanchor(float[] anchor){gesture=anchor.clone();render();}
 boolean backInEditor(){
  if(editorMenu!=null){editorMenu.dismiss();return true;}
  if(editorDialog!=null){Window w=editorDialog.getWindow();WindowInsets insets=w.getDecorView().getRootWindowInsets();
   if(insets!=null&&insets.isVisible(WindowInsets.Type.ime())&&w.getInsetsController()!=null)w.getInsetsController().hide(WindowInsets.Type.ime());else editorDialog.dismiss();return true;}
  return false;
 }
 boolean hasEditor(){return editorMenu!=null||editorDialog!=null;}
 private JSONObject item(int id,int p,int x,int y,int w,int h){JSONObject o=new JSONObject();try{o.put("id",id).put("page",p).put("x",x).put("y",y).put("w",w).put("h",h);}catch(Exception ignored){}return o;}
 private void save(){prefs.edit().putString("layout_v3",items.toString()).putInt("pages",Math.max(1,pages)).putInt("page",page).putInt("height_v4",boardHeight).apply();}
 private TextView button(String text,Runnable action){TextView t=new TextView(this);t.setText(text);t.setTextSize(14);t.setGravity(17);t.setPadding(dp(8),0,dp(8),0);t.setContentDescription(text);t.setOnClickListener(v->action.run());Runnable colors=()->{t.setTextColor(ColorOsTheme.text(this));t.setTypeface(ColorOsTheme.typeface(this,400));};colors.run();ColorOsTheme.watch(t,colors);return t;}
 private View toolbarButton(String text,String iconName,View.OnClickListener click){
  LinearLayout control=new LinearLayout(this){@Override public CharSequence getAccessibilityClassName(){return Button.class.getName();}};control.setGravity(Gravity.CENTER);control.setPadding(dp(6),0,dp(6),0);control.setContentDescription(text);control.setFocusable(true);control.setOnClickListener(click);
  ImageView icon=new ImageView(this);icon.setImageDrawable(SystemIcons.raw(this,"com.android.settings:"+iconName));icon.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);control.addView(icon,new LinearLayout.LayoutParams(dp(18),dp(18)));
  TextView label=new TextView(this);label.setText(text);label.setTextSize(14);label.setSingleLine(true);label.setEllipsize(TextUtils.TruncateAt.END);label.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(-2,-2);lp.setMarginStart(dp(5));control.addView(label,lp);
  Runnable colors=()->{icon.setColorFilter(ColorOsTheme.text(this));label.setTextColor(ColorOsTheme.text(this));label.setTypeface(ColorOsTheme.typeface(this,500));control.setBackground(ColorDialogBuilder.touchBackground(this,android.graphics.Color.TRANSPARENT,14));};colors.run();ColorOsTheme.watch(control,colors);return control;
 }
 private boolean usable(){return !closed;}
 /** Detach transient editors before changing their anchor tree or leaving the Activity. */
 void dismissEditors(){
  editorGeneration++;
  ColorPopupMenu menu=editorMenu;editorMenu=null;if(menu!=null)menu.dismissImmediately();
  WidgetProviderPicker picker=providerPicker;providerPicker=null;if(picker!=null)picker.close();
  AlertDialog dialog=editorDialog;editorDialog=null;if(dialog!=null)ColorDialogBuilder.dismissImmediately(dialog);
 }
 private void showDialog(AlertDialog dialog){
  if(!usable()){ColorDialogBuilder.dismissImmediately(dialog);return;}editorDialog=dialog;
  dialog.setOnDismissListener(d->{if(editorDialog==dialog){editorDialog=null;WidgetProviderPicker picker=providerPicker;providerPicker=null;if(picker!=null)picker.close();}});dialog.show();
 }
 private void popup(View anchor,String title,String[] labels,java.util.function.IntConsumer action,int... destructive){
  dismissEditors();if(!usable()||anchor==null||!anchor.isAttachedToWindow())return;
  int token=editorGeneration;ColorPopupMenu menu=new ColorPopupMenu(anchor).setTitle(title).setItems(labels,n->{if(usable()&&token==editorGeneration)action.accept(n);}).setDestructive(destructive);
  if(overlay)menu.setWindowLayoutType(WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY);
  editorMenu=menu;menu.setOnDismiss(()->{if(editorMenu==menu)editorMenu=null;}).show();
 }
 void render(){
  if(closed)return;
  dismissEditors();
  root=new LinearLayout(this);root.setOrientation(LinearLayout.VERTICAL);root.setPadding(dp(5),dp(5),dp(5),dp(5));window.setContentView(root);PanelStyle.window(this,window,24);
  bar=new LinearLayout(this);bar.setGravity(Gravity.CENTER_VERTICAL);
  // Reclaim the old outer gutter for widgets while preserving saved popup height.
  canvas=new Page();
  int available=PanelGeometry.screen(this).y-PanelGeometry.inset(this,"status_bar_height")-PanelGeometry.inset(this,"navigation_bar_height");
  int canvasHeight=Math.max(dp(48),Math.min(dp(boardHeight+14),available-dp(editing?150:68)));
  LinearLayout.LayoutParams cp=new LinearLayout.LayoutParams(-1,canvasHeight);cp.topMargin=editing?dp(6):0;root.addView(canvas,cp);
  for(int i=0;i<items.length();i++){JSONObject o=items.optJSONObject(i);if(o==null||o.optInt("page")!=page)continue;int id=o.optInt("id");AppWidgetProviderInfo info=manager.getAppWidgetInfo(id);if(info==null)continue;
   Cell cell=new Cell(o);cell.setContentDescription("组件 "+id);cell.setBackground(PanelStyle.shape(this,o.optInt("color",info.provider.getPackageName().equals("com.coloros.alarmclock")?0xff45484f:0x00000000),20));AppWidgetHostView w=host.createView(this,id,info);w.setAppWidget(id,info);if(editing)w.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO_HIDE_DESCENDANTS);int inset=Math.min(dp(5),Math.min(Math.min(w.getPaddingLeft(),w.getPaddingRight()),Math.min(w.getPaddingTop(),w.getPaddingBottom())));w.setPadding(inset,inset,inset,inset);cell.addView(w,new android.widget.FrameLayout.LayoutParams(-1,-1));canvas.addView(cell);
  }
  if(canvas.getChildCount()==0){TextView empty=button("点按添加小组件",this::choose);empty.setBackgroundColor(0);empty.setOnLongClickListener(v->{pageMenu(v);return true;});canvas.addView(empty,new android.widget.FrameLayout.LayoutParams(-1,-1));}
  LinearLayout nav=new LinearLayout(this);nav.setGravity(17);root.addView(nav,new LinearLayout.LayoutParams(-1,dp(48)));
  PageDots dots=new PageDots(this,pages,page);dots.setContentDescription("第 "+(page+1)+" 页，共 "+pages+" 页；页面菜单");boolean loop=prefs.getBoolean("loop",false);dots.setPaging(pages>1&&(loop||page>0)?()->turn(-1):null,pages>1&&(loop||page+1<pages)?()->turn(1):null);dots.setOnClickListener(this::pageMenu);nav.addView(dots,new LinearLayout.LayoutParams(dp(Math.max(52,Math.min(pages,9)*12+28)),-1));

  if(editing){
   bar.addView(toolbarButton("添加","ic_add_24dp",v->choose()),new LinearLayout.LayoutParams(0,-1,1));
   bar.addView(toolbarButton("页面选项","coui_toolbar_menu_icon_more_normal",this::pageMenu),new LinearLayout.LayoutParams(0,-1,1));
   bar.addView(toolbarButton("完成","menu_ic_confirm_normal",v->{editing=false;selected=-1;render();}),new LinearLayout.LayoutParams(0,-1,1));
   root.addView(bar,new LinearLayout.LayoutParams(-1,dp(44)));
  }else{
   nav.addView(toolbarButton("页面选项","coui_toolbar_menu_icon_more_normal",this::pageMenu),new LinearLayout.LayoutParams(-2,-1));
  }
  if(editing){TextView hint=new TextView(this);hint.setText("拖动选中组件移动 · 拖动四边圆点缩放 · 点按打开菜单");hint.setTextSize(11);Runnable colors=()->{hint.setTextColor(ColorOsTheme.secondary(this));hint.setTypeface(ColorOsTheme.typeface(this,400));};colors.run();ColorOsTheme.watch(hint,colors);root.addView(hint);}
  int width=Math.round(getResources().getDisplayMetrics().widthPixels*.936f);root.measure(View.MeasureSpec.makeMeasureSpec(width,View.MeasureSpec.EXACTLY),View.MeasureSpec.makeMeasureSpec(0,View.MeasureSpec.UNSPECIFIED));PanelGeometry.position(this,window,width,root.getMeasuredHeight(),false,gesture);
 }
 private void turn(int delta){int target=page+delta;if(prefs.getBoolean("loop",false))target=(target+pages)%pages;page=Math.max(0,Math.min(pages-1,target));selected=-1;save();render();}
 private void pageMenu(View anchor){popup(anchor,"小组件页面",new String[]{"添加小组件","添加页面","弹窗高度","循环翻页："+(prefs.getBoolean("loop",false)?"开":"关"),editing?"完成编辑":"编辑组件","删除当前页面"},n->{
  if(n==0)choose();
  else if(n==1){pages++;page=pages-1;save();render();}
  else if(n==2)chooseValue("弹窗高度",new String[]{"紧凑","标准","宽敞"},new int[]{250,330,400},boardHeight,value->{boardHeight=value;save();render();});
  else if(n==3)prefs.edit().putBoolean("loop",!prefs.getBoolean("loop",false)).apply();
  else if(n==4){editing=!editing;selected=-1;render();}
  else if(n==5){int token=editorGeneration;showDialog(new GlassDialogBuilder(this).setTitle("删除当前页面？").setMessage("此页面上的小组件也会被移除。").setNegativeButton("取消",null).setPositiveDestructive("删除",(d,which)->{if(usable()&&token==editorGeneration)deletePage();}).create());}
 },5);}
 private void deletePage(){JSONArray next=new JSONArray();for(int i=0;i<items.length();i++){JSONObject o=items.optJSONObject(i);if(o.optInt("page")==page)host.deleteAppWidgetId(o.optInt("id"));else{try{if(o.optInt("page")>page)o.put("page",o.optInt("page")-1);}catch(Exception ignored){}next.put(o);}}items=next;pages=Math.max(1,pages-1);page=Math.min(page,pages-1);save();render();}
 private void choose(){
  dismissEditors();if(!usable())return;int token=editorGeneration;
  final AlertDialog[] holder=new AlertDialog[1];
  WidgetProviderPicker picker=new WidgetProviderPicker(this,info->{AlertDialog dialog=holder[0];if(dialog==editorDialog&&usable())ColorDialogBuilder.dismissThen(dialog,()->{if(usable()&&token==editorGeneration)beginBind(info);});});
  providerPicker=picker;AlertDialog dialog=new GlassDialogBuilder(this).setTitle("添加小组件").setView(picker.view()).setNegativeButton("取消",null).create();holder[0]=dialog;showDialog(dialog);picker.start();
 }
 private void beginBind(AppWidgetProviderInfo info){
  int id=-1;
  try{
   id=host.allocateAppWidgetId();boolean bound=manager.bindAppWidgetIdIfAllowed(id,info.provider);
   if(bound&&info.configure==null){commitWidget(id,page);render();}
   else setup.request(info.provider,id,bound,false,page);
  }catch(RuntimeException failure){if(id>=0)host.deleteAppWidgetId(id);toast("无法绑定此小组件");}
 }
 void commitWidget(int id,int requestedPage){
  readLayout();int target=Math.max(0,Math.min(requestedPage,pages-1)),y=-1;
  for(int row=0;row<=2;row++)if(free(-1,target,0,row,4,2)){y=row;break;}
  if(y<0){target=pages++;y=0;}page=target;items.put(item(id,target,0,y,4,2));save();
 }
 private boolean free(int id,int p,int x,int y,int w,int h){WidgetGrid.Cell candidate=new WidgetGrid.Cell(x,y,w,h);if(!WidgetGrid.valid(candidate))return false;for(int i=0;i<items.length();i++){JSONObject a=items.optJSONObject(i);if(a==null||a.optInt("id")==id||a.optInt("page")!=p)continue;if(WidgetGrid.overlaps(candidate,new WidgetGrid.Cell(a.optInt("x"),a.optInt("y"),a.optInt("w"),a.optInt("h"))))return false;}return true;}
 private JSONObject findItem(int id){for(int i=0;i<items.length();i++){JSONObject item=items.optJSONObject(i);if(item!=null&&item.optInt("id")==id)return item;}return null;}
 private void menu(JSONObject o,View anchor){
  final int id=o.optInt("id");if(findItem(id)==null)return;
  AppWidgetProviderInfo info=manager.getAppWidgetInfo(id);String title="小组件";if(info!=null)try{String label=info.loadLabel(getPackageManager());if(!TextUtils.isEmpty(label))title=label;}catch(RuntimeException ignored){}
  ArrayList<String> labels=new ArrayList<>();ArrayList<Runnable> actions=new ArrayList<>();
  if(info!=null&&info.configure!=null){labels.add("组件设置");actions.add(()->{if(findItem(id)==null)return;try{setup.request(info.provider,id,true,true,page);}catch(RuntimeException failure){toast("组件设置无法打开");}});}
  if(page>0){labels.add("移到上一页");actions.add(()->moveWidget(id,-1));}
  labels.add("移到下一页");actions.add(()->moveWidget(id,1));
  labels.add("位置与尺寸");actions.add(()->adjustCell(id,anchor));
  labels.add("卡片底色");actions.add(()->chooseValue("卡片底色",new String[]{"浅色","深色","透明"},
    new int[]{0xfff5f6f8,0xff45484f,0},o.optInt("color",info!=null&&info.provider.getPackageName().equals("com.coloros.alarmclock")?0xff45484f:0),
    value->{JSONObject item=findItem(id);if(item==null)return;try{item.put("color",value);}catch(JSONException invalid){return;}save();render();}));
  labels.add("移除小组件");final String widgetTitle=title;actions.add(()->{int token=editorGeneration;showDialog(new GlassDialogBuilder(this).setTitle("移除此小组件？").setMessage(widgetTitle+"将从当前页面移除。").setNegativeButton("取消",null).setPositiveDestructive("移除",(dialog,which)->{if(usable()&&token==editorGeneration)removeWidget(id);}).create());});
  popup(anchor,title,labels.toArray(new String[0]),index->actions.get(index).run(),labels.size()-1);
 }
 private void chooseValue(String title,String[] labels,int[] values,int current,java.util.function.IntConsumer apply){
  dismissEditors();if(!usable())return;int token=editorGeneration,checked=-1;
  for(int i=0;i<values.length;i++)if(values[i]==current)checked=i;
  showDialog(new GlassDialogBuilder(this).setTitle(title).setSingleChoiceItems(labels,checked,(dialog,index)->{
   ColorDialogBuilder.dismissThen((AlertDialog)dialog,()->{if(usable()&&token==editorGeneration)apply.accept(values[index]);});
  }).setNegativeButton("取消",null).create());
 }
 private void adjustCell(int id,View anchor){
  JSONObject item=findItem(id);if(item==null)return;
  String[] names={"左移","右移","上移","下移","加宽","缩窄","增高","降低"};
  int[][] steps={{-1,0,0},{1,0,0},{0,-1,0},{0,1,0},{1,0,WidgetGrid.RIGHT},{-1,0,WidgetGrid.RIGHT},{0,1,WidgetGrid.BOTTOM},{0,-1,WidgetGrid.BOTTOM}};
  WidgetGrid.Cell start=new WidgetGrid.Cell(item.optInt("x"),item.optInt("y"),item.optInt("w"),item.optInt("h"));
  ArrayList<String> allowed=new ArrayList<>();ArrayList<WidgetGrid.Cell> positions=new ArrayList<>();
  for(int i=0;i<steps.length;i++){int[] s=steps[i];WidgetGrid.Cell candidate=WidgetGrid.propose(start,s[0],s[1],s[2]);
   if(candidate!=null&&free(id,page,candidate.x,candidate.y,candidate.w,candidate.h)){allowed.add(names[i]);positions.add(candidate);}
  }
  if(allowed.isEmpty()){toast("没有可用位置");return;}
  popup(anchor,"位置与尺寸",allowed.toArray(new String[0]),n->{JSONObject current=findItem(id);WidgetGrid.Cell c=positions.get(n);
   if(current==null||!free(id,page,c.x,c.y,c.w,c.h))return;
   try{current.put("x",c.x).put("y",c.y).put("w",c.w).put("h",c.h);}catch(JSONException invalid){return;}
   selected=id;save();render();
  });
 }
 private void moveWidget(int id,int delta){JSONObject item=findItem(id);if(item==null)return;int target=item.optInt("page")+delta;if(target<0)return;if(!free(id,target,item.optInt("x"),item.optInt("y"),item.optInt("w"),item.optInt("h"))){toast("目标位置已有组件");return;}try{item.put("page",target);}catch(JSONException ignored){}pages=Math.max(pages,target+1);page=target;save();render();}
 private void removeWidget(int id){if(findItem(id)==null)return;JSONArray next=new JSONArray();for(int i=0;i<items.length();i++){JSONObject item=items.optJSONObject(i);if(item!=null&&item.optInt("id")!=id)next.put(item);}items=next;host.deleteAppWidgetId(id);selected=-1;save();render();}
 private class Page extends android.widget.FrameLayout {float x,y;Page(){super(WidgetPanel.this);setOnLongClickListener(v->{pageMenu(v);return true;});}public boolean onInterceptTouchEvent(MotionEvent e){if(editing)return false;if(e.getActionMasked()==0){x=e.getX();y=e.getY();}return e.getActionMasked()==2&&Math.abs(e.getX()-x)>dp(36)&&Math.abs(e.getX()-x)>Math.abs(e.getY()-y)*1.5;}public boolean onTouchEvent(MotionEvent e){if(e.getActionMasked()==1&&Math.abs(e.getX()-x)>dp(50)&&Math.abs(e.getX()-x)>Math.abs(e.getY()-y)*1.5){turn(e.getX()>x?-1:1);return true;}return super.onTouchEvent(e);}
  protected void onMeasure(int widthSpec,int heightSpec){int w=MeasureSpec.getSize(widthSpec),h=MeasureSpec.getSize(heightSpec);setMeasuredDimension(w,h);for(int i=0;i<getChildCount();i++){View v=getChildAt(i);int cw=w,ch=h;if(v instanceof Cell){JSONObject o=((Cell)v).data;cw=(o.optInt("x")+o.optInt("w"))*w/4-o.optInt("x")*w/4;ch=(o.optInt("y")+o.optInt("h"))*h/4-o.optInt("y")*h/4;}v.measure(MeasureSpec.makeMeasureSpec(cw,MeasureSpec.EXACTLY),MeasureSpec.makeMeasureSpec(ch,MeasureSpec.EXACTLY));}}
  protected void onLayout(boolean changed,int l,int t,int r,int b){for(int i=0;i<getChildCount();i++){View v=getChildAt(i);int x=0,y=0;if(v instanceof Cell){JSONObject o=((Cell)v).data;x=o.optInt("x")*getWidth()/4;y=o.optInt("y")*getHeight()/4;}v.layout(x,y,x+v.getMeasuredWidth(),y+v.getMeasuredHeight());}}
 }
 private class Cell extends android.widget.FrameLayout {
  final JSONObject data;float sx,sy;int ox,oy,ow,oh,resizeEdges;boolean moved,dragActive;WidgetGrid.Edit edit;final Handler handler=new Handler();
  final Runnable longPress;
  Cell(JSONObject o){super(WidgetPanel.this);data=o;if(editing){setFocusable(true);setClickable(true);setOnClickListener(v->menu(data,v));setDescendantFocusability(FOCUS_BLOCK_DESCENDANTS);setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_YES);}longPress=()->{editing=true;selected=data.optInt("id");render();};setPadding(dp(3),dp(3),dp(3),dp(3));}
  public boolean dispatchTouchEvent(MotionEvent e){if(!editing){if(e.getActionMasked()==MotionEvent.ACTION_DOWN){sx=e.getX();sy=e.getY();handler.postDelayed(longPress,ViewConfiguration.getLongPressTimeout());}if(e.getActionMasked()==MotionEvent.ACTION_UP||e.getActionMasked()==MotionEvent.ACTION_CANCEL||(e.getActionMasked()==MotionEvent.ACTION_MOVE&&(Math.abs(e.getX()-sx)>dp(10)||Math.abs(e.getY()-sy)>dp(10))))handler.removeCallbacks(longPress);}return super.dispatchTouchEvent(e);}
  public boolean onInterceptTouchEvent(MotionEvent e){return editing;}
  public boolean onTouchEvent(MotionEvent e){
   if(!editing)return super.onTouchEvent(e);
   if(e.getActionMasked()==MotionEvent.ACTION_DOWN){
    selected=data.optInt("id");for(int i=0;i<canvas.getChildCount();i++)canvas.getChildAt(i).invalidate();
    dragActive=true;sx=e.getRawX();sy=e.getRawY();ox=data.optInt("x");oy=data.optInt("y");ow=data.optInt("w");oh=data.optInt("h");resizeEdges=0;float edge=dp(24);
    if(e.getX()<edge)resizeEdges|=WidgetGrid.LEFT;else if(e.getX()>getWidth()-edge)resizeEdges|=WidgetGrid.RIGHT;
    if(e.getY()<edge)resizeEdges|=WidgetGrid.TOP;else if(e.getY()>getHeight()-edge)resizeEdges|=WidgetGrid.BOTTOM;
    edit=new WidgetGrid.Edit(new WidgetGrid.Cell(ox,oy,ow,oh),resizeEdges);moved=false;return true;
   }
   if(e.getActionMasked()==MotionEvent.ACTION_MOVE){
    if(!dragActive||canvas.getWidth()==0||canvas.getHeight()==0)return true;
    int dx=Math.round((e.getRawX()-sx)/(canvas.getWidth()/4f)),dy=Math.round((e.getRawY()-sy)/(canvas.getHeight()/4f));
    if(Math.abs(e.getRawX()-sx)>dp(8)||Math.abs(e.getRawY()-sy)>dp(8))moved=true;
    WidgetGrid.Cell proposed=edit.move(dx,dy,c->free(data.optInt("id"),page,c.x,c.y,c.w,c.h));put(proposed);canvas.requestLayout();return true;
   }
   if(e.getActionMasked()==MotionEvent.ACTION_UP){dragActive=false;save();if(!moved)post(()->{if(isAttachedToWindow())menu(data,this);});else post(()->render());return true;}
   if(e.getActionMasked()==MotionEvent.ACTION_CANCEL){if(!dragActive)return true;dragActive=false;put(edit.cancel());post(()->render());return true;}
   return true;
  }
  private void put(WidgetGrid.Cell c){try{data.put("x",c.x).put("y",c.y).put("w",c.w).put("h",c.h);}catch(JSONException ignored){}}
  protected void dispatchDraw(android.graphics.Canvas c){super.dispatchDraw(c);if(!editing||data.optInt("id")!=selected)return;android.graphics.Paint paint=new android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG);paint.setColor(PanelStyle.accent(getContext()));paint.setStrokeWidth(dp(2));paint.setStyle(android.graphics.Paint.Style.STROKE);float inset=dp(4),left=inset,top=inset,right=getWidth()-inset,bottom=getHeight()-inset;c.drawRoundRect(left,top,right,bottom,dp(16),dp(16),paint);paint.setStyle(android.graphics.Paint.Style.FILL);float r=dp(6);c.drawCircle(left,getHeight()/2f,r,paint);c.drawCircle(right,getHeight()/2f,r,paint);c.drawCircle(getWidth()/2f,top,r,paint);c.drawCircle(getWidth()/2f,bottom,r,paint);}
  protected void onDetachedFromWindow(){handler.removeCallbacks(longPress);super.onDetachedFromWindow();}
 }

}
