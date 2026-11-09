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
 private PanelRoot root;private Page canvas;
 private final LauncherWidgetStyle launcher;
 private ColorPopupMenu editorMenu;private AlertDialog editorDialog;private WidgetProviderPicker providerPicker;private int editorGeneration;
 private int dp(float n){return PanelStyle.dp(this,n);}
 private void toast(String message){Toast.makeText(this,message,Toast.LENGTH_SHORT).show();}
 WidgetPanel(Context base, Window window, float[] anchor, boolean overlay, Setup setup){
  super(base,base.getResources().getIdentifier(PanelStyle.dark(base)?"ReplicaGlassDark":"ReplicaGlass","style",base.getPackageName()));
  launcher=new LauncherWidgetStyle(this);this.window=window;this.overlay=overlay;this.setup=setup;gesture=anchor.clone();
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
  if(editing){finishEdit();return true;}
  return false;
 }
 boolean hasEditor(){return editorMenu!=null||editorDialog!=null;}
 private JSONObject item(int id,int p,int x,int y,int w,int h){JSONObject o=new JSONObject();try{o.put("id",id).put("page",p).put("x",x).put("y",y).put("w",w).put("h",h);}catch(Exception ignored){}return o;}
 private void save(){prefs.edit().putString("layout_v3",items.toString()).putInt("pages",Math.max(1,pages)).putInt("page",page).putInt("height_v4",boardHeight).apply();}
 private boolean saveRemoval(JSONArray next,int count,int current){
  // Once the host releases an ID, restoring its old grid entry cannot restore the binding.
  if(!prefs.edit().putString("layout_v3",next.toString()).putInt("pages",count).putInt("page",current).commit()){
   toast("未能保存插件页面");return false;
  }
  items=next;pages=count;page=current;editing=false;selected=-1;return true;
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
 private void popup(View anchor,android.graphics.Point point,String title,String[] labels,java.util.function.IntConsumer action){
  dismissEditors();if(!usable()||anchor==null||!anchor.isAttachedToWindow())return;
  int token=editorGeneration;String[] icons=new String[labels.length];
  for(int i=0;i<labels.length;i++)icons[i]=menuIcon(labels[i]);
  ColorPopupMenu menu=new ColorPopupMenu(anchor).setTitle(title).setItems(labels,n->{if(usable()&&token==editorGeneration)action.accept(n);}).launcher(launcher,icons,point);
  if(overlay)menu.setWindowLayoutType(WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY);
  editorMenu=menu;menu.setOnDismiss(()->{if(editorMenu==menu)editorMenu=null;}).show();
 }
 private static String menuIcon(String label){return switch(label){
  case "添加插件" -> "ic_system_shortcut_card_add";case "添加页面" -> "ic_system_shortcut_add";
  case "移除插件","移除当前页面" -> "ic_system_shortcut_delete";
  case "编辑插件" -> "area_widget_edit";
  case "左移","移到上一页" -> "material_ic_keyboard_arrow_left_black_24dp";case "右移","移到下一页" -> "material_ic_keyboard_arrow_right_black_24dp";
  case "缩窄" -> "ic_widget_width_decrease";case "加宽" -> "ic_widget_width_increase";
  case "上移" -> "ic_expand_less";case "下移" -> "ic_expand_more";
  case "增高" -> "ic_widget_height_increase";case "降低" -> "ic_widget_height_decrease";case "卡片底色" -> "ic_palette";
  case "完成调整" -> "ic_system_shortcut_choose";default -> "ic_system_shortcut_app_edit";
 };}
 private void finishEdit(){editing=false;selected=-1;render();}
 void render(){
  if(closed)return;
  dismissEditors();
  root=new PanelRoot();root.setOrientation(LinearLayout.VERTICAL);root.setPadding(dp(5),dp(5),dp(5),dp(5));window.setContentView(root);PanelStyle.window(this,window,24);
  canvas=new Page();
  int available=PanelGeometry.screen(this).y-PanelGeometry.inset(this,"status_bar_height")-PanelGeometry.inset(this,"navigation_bar_height");
  int footerHeight=launcher.dim("workspace_page_indicator_height",28);
  int canvasHeight=Math.max(dp(48),Math.min(dp(boardHeight+14),available-footerHeight-dp(20)));
  root.addView(canvas,new LinearLayout.LayoutParams(-1,canvasHeight));
  for(int i=0;i<items.length();i++){JSONObject o=items.optJSONObject(i);if(o==null||o.optInt("page")!=page)continue;int id=o.optInt("id");AppWidgetProviderInfo info=manager.getAppWidgetInfo(id);if(info==null)continue;
   Cell cell=new Cell(o);cell.setContentDescription("插件 "+id);cell.setBackground(PanelStyle.shape(this,o.optInt("color",info.provider.getPackageName().equals("com.coloros.alarmclock")?0xff45484f:0x00000000),20));
   AppWidgetHostView w=host.createView(this,id,info);w.setAppWidget(id,info);
   if(cell.adjusting())w.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO_HIDE_DESCENDANTS);
   int inset=Math.min(dp(5),Math.min(Math.min(w.getPaddingLeft(),w.getPaddingRight()),Math.min(w.getPaddingTop(),w.getPaddingBottom())));w.setPadding(inset,inset,inset,inset);
   cell.addView(w,new FrameLayout.LayoutParams(-1,-1));
   if(cell.adjusting())cell.addView(launcher.resizeFrame(cell),new FrameLayout.LayoutParams(-1,-1));
   canvas.addView(cell);
  }
  if(canvas.getChildCount()==0){
   TextView empty=new TextView(this);empty.setText("长按空白处添加插件");empty.setTextSize(14);empty.setGravity(Gravity.CENTER);empty.setTextColor(ColorOsTheme.secondary(this));canvas.addView(empty);
  }
  PageDots dots=new PageDots(this,pages,page,launcher);
  dots.setContentDescription("第 "+(page+1)+" 页，共 "+pages+" 页");boolean loop=prefs.getBoolean("loop",false);
  dots.setPaging(pages>1&&(loop||page>0)?()->turn(-1):null,pages>1&&(loop||page+1<pages)?()->turn(1):null);
  root.addView(dots,new LinearLayout.LayoutParams(-1,footerHeight));
  int width=Math.round(getResources().getDisplayMetrics().widthPixels*.936f);root.measure(View.MeasureSpec.makeMeasureSpec(width,View.MeasureSpec.EXACTLY),View.MeasureSpec.makeMeasureSpec(0,View.MeasureSpec.UNSPECIFIED));PanelGeometry.position(this,window,width,root.getMeasuredHeight(),false,gesture);
 }
 private void turn(int delta){
  int target=page+delta;if(prefs.getBoolean("loop",false))target=(target+pages)%pages;
  target=Math.max(0,Math.min(pages-1,target));if(target==page)return;
  page=target;editing=false;selected=-1;save();render();
 }
 private void pageMenu(android.graphics.Point point){
  popup(root,point,"页面选项",new String[]{"添加插件","添加页面","移除当前页面"},n->{
   if(n==0)choose();
   else if(n==1){pages++;page=pages-1;editing=false;selected=-1;save();render();}
   else{
    if(pages==1&&items.length()==0){toast("已是最后一个空白页");return;}
    int token=editorGeneration;
    String message=pages==1?"此页的插件将被移除，并保留一个空白页。":"此页的插件也将被移除。";
    showDialog(new GlassDialogBuilder(this).setTitle("移除当前页面？").setMessage(message).setNegativeButton("取消",null)
     .setPositiveDestructive("移除",(d,which)->{if(usable()&&token==editorGeneration)deletePage();}).create());
   }
  });
 }
 private void deletePage(){
  JSONArray kept=new JSONArray();ArrayList<Integer> removed=new ArrayList<>();
  for(int i=0;i<items.length();i++){
   JSONObject o=items.optJSONObject(i);if(o==null)continue;
   if(o.optInt("page")==page)removed.add(o.optInt("id"));
   else{
    try{if(o.optInt("page")>page)o=new JSONObject(o.toString()).put("page",o.optInt("page")-1);}
    catch(JSONException invalid){toast("未能保存插件页面");return;}
    kept.put(o);
   }
  }
  int remaining=Math.max(1,pages-1);if(!saveRemoval(kept,remaining,Math.min(page,remaining-1)))return;
  for(int id:removed)host.deleteAppWidgetId(id);render();
 }
 private void choose(){
  dismissEditors();if(!usable())return;int token=editorGeneration;
  final AlertDialog[] holder=new AlertDialog[1];
  WidgetProviderPicker picker=new WidgetProviderPicker(this,info->{AlertDialog dialog=holder[0];if(dialog==editorDialog&&usable())ColorDialogBuilder.dismissThen(dialog,()->{if(usable()&&token==editorGeneration)beginBind(info);});});
  providerPicker=picker;AlertDialog dialog=new GlassDialogBuilder(this).setTitle("添加插件").setView(picker.view()).setNegativeButton("取消",null).create();holder[0]=dialog;showDialog(dialog);picker.start();
 }
 private void beginBind(AppWidgetProviderInfo info){
  int id=-1;
  try{
   id=host.allocateAppWidgetId();boolean bound=manager.bindAppWidgetIdIfAllowed(id,info.provider);
   if(bound&&info.configure==null){commitWidget(id,page);render();}
   else setup.request(info.provider,id,bound,false,page);
  }catch(RuntimeException failure){if(id>=0)host.deleteAppWidgetId(id);toast("无法绑定此插件");}
 }
 void commitWidget(int id,int requestedPage){
  readLayout();int target=Math.max(0,Math.min(requestedPage,pages-1)),y=-1;
  for(int row=0;row<=2;row++)if(free(-1,target,0,row,4,2)){y=row;break;}
  if(y<0){target=pages++;y=0;}page=target;editing=false;selected=-1;items.put(item(id,target,0,y,4,2));save();
 }
 private boolean free(int id,int p,int x,int y,int w,int h){WidgetGrid.Cell candidate=new WidgetGrid.Cell(x,y,w,h);if(!WidgetGrid.valid(candidate))return false;for(int i=0;i<items.length();i++){JSONObject a=items.optJSONObject(i);if(a==null||a.optInt("id")==id||a.optInt("page")!=p)continue;if(WidgetGrid.overlaps(candidate,new WidgetGrid.Cell(a.optInt("x"),a.optInt("y"),a.optInt("w"),a.optInt("h"))))return false;}return true;}
 private JSONObject findItem(int id){for(int i=0;i<items.length();i++){JSONObject item=items.optJSONObject(i);if(item!=null&&item.optInt("id")==id)return item;}return null;}
 private void menu(JSONObject o,View anchor){
  final int id=o.optInt("id");if(findItem(id)==null)return;
  AppWidgetProviderInfo info=manager.getAppWidgetInfo(id);String title="插件";if(info!=null)try{String label=info.loadLabel(getPackageManager());if(!TextUtils.isEmpty(label))title=label;}catch(RuntimeException ignored){}
  ArrayList<String> labels=new ArrayList<>();ArrayList<Runnable> actions=new ArrayList<>();
  if(editing&&selected==id){labels.add("完成调整");actions.add(this::finishEdit);labels.add("位置与尺寸");actions.add(()->adjustCell(id,anchor));}
  else{labels.add("调整大小");actions.add(()->{editing=true;selected=id;render();});}
  if(info!=null&&info.configure!=null){labels.add("编辑插件");actions.add(()->{if(findItem(id)==null)return;try{setup.request(info.provider,id,true,true,page);}catch(RuntimeException failure){toast("插件设置无法打开");}});}
  if(page>0){labels.add("移到上一页");actions.add(()->moveWidget(id,-1));}
  labels.add("移到下一页");actions.add(()->moveWidget(id,1));
  labels.add("卡片底色");actions.add(()->chooseValue("卡片底色",new String[]{"浅色","深色","透明"},
    new int[]{0xfff5f6f8,0xff45484f,0},o.optInt("color",info!=null&&info.provider.getPackageName().equals("com.coloros.alarmclock")?0xff45484f:0),
    value->{JSONObject item=findItem(id);if(item==null)return;try{item.put("color",value);}catch(JSONException invalid){return;}save();render();}));
  labels.add("移除插件");final String widgetTitle=title;actions.add(()->{int token=editorGeneration;showDialog(new GlassDialogBuilder(this).setTitle("移除此插件？").setMessage(widgetTitle+"将从当前页面移除。").setNegativeButton("取消",null).setPositiveDestructive("移除",(dialog,which)->{if(usable()&&token==editorGeneration)removeWidget(id);}).create());});
  popup(anchor,null,title,labels.toArray(new String[0]),index->actions.get(index).run());
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
  popup(anchor,null,"位置与尺寸",allowed.toArray(new String[0]),n->{JSONObject current=findItem(id);WidgetGrid.Cell c=positions.get(n);
   if(current==null||!free(id,page,c.x,c.y,c.w,c.h))return;
   try{current.put("x",c.x).put("y",c.y).put("w",c.w).put("h",c.h);}catch(JSONException invalid){return;}
   selected=id;save();render();
  });
 }
 private void moveWidget(int id,int delta){JSONObject item=findItem(id);if(item==null)return;int target=item.optInt("page")+delta;if(target<0)return;if(!free(id,target,item.optInt("x"),item.optInt("y"),item.optInt("w"),item.optInt("h"))){toast("目标位置已有插件");return;}try{item.put("page",target);}catch(JSONException ignored){}pages=Math.max(pages,target+1);page=target;save();render();}
 private void removeWidget(int id){
  if(findItem(id)==null)return;
  JSONArray next=new JSONArray();for(int i=0;i<items.length();i++){JSONObject item=items.optJSONObject(i);if(item!=null&&item.optInt("id")!=id)next.put(item);}
  if(saveRemoval(next,pages,page)){host.deleteAppWidgetId(id);render();}
 }
 /** Observe the whole panel before RemoteViews dispatch. A long press cancels the provider's click. */
 private final class PanelRoot extends LinearLayout {
  private final int slop=ViewConfiguration.get(WidgetPanel.this).getScaledTouchSlop();
  private float downX,downY;private long downTime;private Cell downCell;
  private boolean tracking,held,paging,eligibleSwipe;
  private final Runnable openMenu=this::longPress;
  PanelRoot(){super(WidgetPanel.this);}
  private Cell cellAt(float x,float y){
   if(canvas==null)return null;
   float localX=x-canvas.getLeft(),localY=y-canvas.getTop();
   for(int i=0;i<canvas.getChildCount();i++){
    View child=canvas.getChildAt(i);
    if(child instanceof Cell&&localX>=child.getLeft()&&localX<child.getRight()&&localY>=child.getTop()&&localY<child.getBottom())return (Cell)child;
   }
   return null;
  }
  private void cancelChildren(){
   MotionEvent cancel=MotionEvent.obtain(downTime,SystemClock.uptimeMillis(),MotionEvent.ACTION_CANCEL,downX,downY,0);
   try{super.dispatchTouchEvent(cancel);}finally{cancel.recycle();}
  }
  private void longPress(){
   if(!tracking||held||paging||!usable()||root!=this||!isShown()||hasEditor())return;
   held=true;cancelChildren();performHapticFeedback(HapticFeedbackConstants.LONG_PRESS);
   if(downCell!=null)menu(downCell.data,downCell);
   else pageMenu(new android.graphics.Point(Math.round(downX),Math.round(downY)));
  }
  @Override public boolean dispatchTouchEvent(MotionEvent event){
   int action=event.getActionMasked();
   if(action==MotionEvent.ACTION_DOWN){
    removeCallbacks(openMenu);downX=event.getX();downY=event.getY();downTime=event.getDownTime();downCell=cellAt(downX,downY);
    tracking=true;held=false;paging=false;eligibleSwipe=downCell==null||!downCell.adjusting();
    postDelayed(openMenu,ViewConfiguration.getLongPressTimeout());
   }else if(action==MotionEvent.ACTION_POINTER_DOWN){
    removeCallbacks(openMenu);eligibleSwipe=false;
   }else if(action==MotionEvent.ACTION_MOVE&&tracking){
    float dx=event.getX()-downX,dy=event.getY()-downY;
    if(Math.abs(dx)>slop||Math.abs(dy)>slop)removeCallbacks(openMenu);
    if(!held&&!paging&&eligibleSwipe&&pages>1&&Math.abs(dx)>dp(36)&&Math.abs(dx)>Math.abs(dy)*1.5f){
     paging=true;cancelChildren();
    }
   }
   if(action==MotionEvent.ACTION_UP||action==MotionEvent.ACTION_CANCEL){
    removeCallbacks(openMenu);tracking=false;
    if(paging||held){
     float dx=event.getX()-downX;
     if(action==MotionEvent.ACTION_UP&&paging&&eligibleSwipe&&Math.abs(dx)>dp(50))turn(dx>0?-1:1);
     return true;
    }
    boolean blankTap=action==MotionEvent.ACTION_UP&&editing&&downCell==null&&Math.hypot(event.getX()-downX,event.getY()-downY)<slop;
    super.dispatchTouchEvent(event);if(blankTap)finishEdit();return true;
   }
   if(!paging&&!held)super.dispatchTouchEvent(event);
   return true;
  }
  @Override public boolean onTouchEvent(MotionEvent e){return true;}
  @Override protected void onDetachedFromWindow(){tracking=false;removeCallbacks(openMenu);super.onDetachedFromWindow();}
 }
 private final class Page extends FrameLayout {
  Page(){super(WidgetPanel.this);setContentDescription("插件页面");setOnLongClickListener(v->{
   pageMenu(new android.graphics.Point(getLeft()+getWidth()/2,getTop()+getHeight()/2));return true;
  });}
  @Override protected void onMeasure(int widthSpec,int heightSpec){
   int w=MeasureSpec.getSize(widthSpec),h=MeasureSpec.getSize(heightSpec);setMeasuredDimension(w,h);
   for(int i=0;i<getChildCount();i++){
    View v=getChildAt(i);int cw=w,ch=h;
    if(v instanceof Cell cell){WidgetGrid.Cell g=cell.geometry();cw=(g.x+g.w)*w/4-g.x*w/4;ch=(g.y+g.h)*h/4-g.y*h/4;}
    v.measure(MeasureSpec.makeMeasureSpec(cw,MeasureSpec.EXACTLY),MeasureSpec.makeMeasureSpec(ch,MeasureSpec.EXACTLY));
   }
  }
  @Override protected void onLayout(boolean changed,int l,int t,int r,int b){
   for(int i=0;i<getChildCount();i++){
    View v=getChildAt(i);int x=0,y=0;
    if(v instanceof Cell cell){WidgetGrid.Cell g=cell.geometry();x=g.x*getWidth()/4;y=g.y*getHeight()/4;}
    v.layout(x,y,x+v.getMeasuredWidth(),y+v.getMeasuredHeight());
   }
  }
 }
 private final class Cell extends FrameLayout {
  final JSONObject data;float sx,sy;boolean moved;WidgetGrid.Edit edit;
  Cell(JSONObject data){
   super(WidgetPanel.this);this.data=data;setPadding(dp(3),dp(3),dp(3),dp(3));
   setOnLongClickListener(v->{menu(data,this);return true;});
   if(adjusting()){setFocusable(true);setOnClickListener(v->menu(data,this));setDescendantFocusability(FOCUS_BLOCK_DESCENDANTS);}
  }
  boolean adjusting(){return editing&&selected==data.optInt("id");}
  WidgetGrid.Cell geometry(){return edit!=null?edit.current():new WidgetGrid.Cell(data.optInt("x"),data.optInt("y"),data.optInt("w"),data.optInt("h"));}
  @Override public boolean onInterceptTouchEvent(MotionEvent e){return adjusting();}
  @Override public boolean onTouchEvent(MotionEvent e){
   if(!adjusting())return false;
   int action=e.getActionMasked();
   if(action==MotionEvent.ACTION_DOWN){
    sx=e.getRawX();sy=e.getRawY();float edge=dp(24);int edges=0;
    if(e.getX()<edge)edges|=WidgetGrid.LEFT;else if(e.getX()>getWidth()-edge)edges|=WidgetGrid.RIGHT;
    if(e.getY()<edge)edges|=WidgetGrid.TOP;else if(e.getY()>getHeight()-edge)edges|=WidgetGrid.BOTTOM;
    edit=new WidgetGrid.Edit(geometry(),edges);moved=false;return true;
   }
   if(action==MotionEvent.ACTION_CANCEL||action==MotionEvent.ACTION_POINTER_DOWN){edit=null;canvas.requestLayout();return true;}
   if(edit==null)return true;
   if(action==MotionEvent.ACTION_MOVE&&canvas.getWidth()>0&&canvas.getHeight()>0){
    int dx=Math.round((e.getRawX()-sx)/(canvas.getWidth()/4f)),dy=Math.round((e.getRawY()-sy)/(canvas.getHeight()/4f));
    if(Math.hypot(e.getRawX()-sx,e.getRawY()-sy)>ViewConfiguration.get(getContext()).getScaledTouchSlop())moved=true;
    edit.move(dx,dy,c->free(data.optInt("id"),page,c.x,c.y,c.w,c.h));canvas.requestLayout();
   }else if(action==MotionEvent.ACTION_UP){
    WidgetGrid.Cell result=edit.current();edit=null;
    if(moved){
     try{data.put("x",result.x).put("y",result.y).put("w",result.w).put("h",result.h);}catch(JSONException ignored){}
     save();post(()->{if(isAttachedToWindow())render();});
    }else performClick();
   }
   return true;
  }
 }
}
