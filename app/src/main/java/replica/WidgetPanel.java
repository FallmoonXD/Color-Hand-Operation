package replica;
import android.app.*;import android.appwidget.*;import android.content.*;import android.os.*;import android.view.*;import android.widget.*;import android.text.*;import org.json.*;import java.util.*;
/** Shared widget layout and editors, hosted either in an overlay or in the settings preview. */
final class WidgetPanel extends android.view.ContextThemeWrapper {
 interface Setup { void request(ComponentName provider,int id,boolean bound,boolean existing,int page); }
 private final Window window;
 private final boolean overlay;
 private final Setup setup;
 private final Runnable leaveForProvider;
 final AppWidgetHost host;
 private final AppWidgetManager manager;
 private final SharedPreferences prefs;
 private JSONArray items;
 private int page,pages,selected=-1,boardHeight;
 private float[] gesture;
 private boolean editing,closed,listening;
 private PanelRoot root;private FrameLayout stage;private WidgetDeck deck;private PageDots dots;
 private final Map<Integer,Cell> cells=new HashMap<>();
 private Drag drag;
 private final int scrollZone,dropMin,dropMax;
 private final LauncherWidgetStyle launcher;
 private ColorPopupMenu editorMenu;private AlertDialog editorDialog;private WidgetProviderPicker providerPicker;private int editorGeneration;
 private int dp(float n){return PanelStyle.dp(this,n);}
 private void toast(String message){Toast.makeText(this,message,Toast.LENGTH_SHORT).show();}
 WidgetPanel(Context base, Window window, float[] anchor, boolean overlay, Setup setup,Runnable leaveForProvider){
  super(base,base.getResources().getIdentifier(PanelStyle.dark(base)?"ReplicaGlassDark":"ReplicaGlass","style",base.getPackageName()));
  launcher=new LauncherWidgetStyle(this);scrollZone=launcher.dim("scroll_zone",22);
  dropMin=launcher.integer("config_dropAnimMinDuration",100);dropMax=launcher.integer("config_dropAnimMaxDuration",500);
  this.window=window;this.overlay=overlay;this.setup=setup;this.leaveForProvider=leaveForProvider;gesture=anchor.clone();
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
 void close(){closed=true;if(root!=null)root.cancelInput();dismissEditors();if(deck!=null)deck.finish();stop();cells.clear();}
 void reanchor(float[] anchor){gesture=anchor.clone();render();}
 boolean backInEditor(){
  if(drag!=null){endDrag(false);return true;}
  if(editorMenu!=null){editorMenu.dismiss();return true;}
  if(editorDialog!=null){Window w=editorDialog.getWindow();WindowInsets insets=w.getDecorView().getRootWindowInsets();
   if(insets!=null&&insets.isVisible(WindowInsets.Type.ime())&&w.getInsetsController()!=null)w.getInsetsController().hide(WindowInsets.Type.ime());else editorDialog.dismiss();return true;}
  if(editing){finishEdit();return true;}
  return false;
 }
 boolean hasEditor(){return editorMenu!=null||editorDialog!=null;}
 boolean dismissMenuOnOutside(){if(editorMenu==null)return false;editorMenu.dismiss();return true;}
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
 private enum GridAction{
  WIDER("加宽",1,0,0,true),NARROWER("缩窄",-1,0,0,true),TALLER("增高",0,1,0,true),SHORTER("降低",0,-1,0,true),
  LEFT("左移",-1,0,0,false),RIGHT("右移",1,0,0,false),UP("上移",0,-1,0,false),DOWN("下移",0,1,0,false),
  PREVIOUS("移到上一页",0,0,-1,false),NEXT("移到下一页",0,0,1,false);
  final int id=View.generateViewId();final String label;final int dx,dy,dp;final boolean size;
  GridAction(String label,int dx,int dy,int dp,boolean size){this.label=label;this.dx=dx;this.dy=dy;this.dp=dp;this.size=size;}
 }
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
  boolean widget=anchor instanceof Cell;
  if(widget)menu.setFocusable(false);
  editorMenu=menu;menu.setOnDismiss(()->{
   if(editorMenu==menu){editorMenu=null;if(widget)finishEdit();}
  }).show();
 }
 private void hideMenuForTouch(){
  ColorPopupMenu menu=editorMenu;editorMenu=null;if(menu!=null)menu.dismissImmediately();
 }
 private static String menuIcon(String label){return switch(label){
  case "添加插件" -> "ic_system_shortcut_card_add";
  case "添加页面","移除插件","移除当前页面" -> "ic_system_shortcut_delete";
  case "编辑插件" -> "area_widget_edit";case "设置" -> "popup_item_setting";
  case "自定义外观" -> "ic_bottom_search_box_customize_exterior";
  default -> "ic_system_shortcut_app_edit";
 };}
 private void finishEdit(){
  hideMenuForTouch();
  Cell previous=cells.get(selected);editing=false;selected=-1;
  if(previous!=null)previous.setSelectedWidget(false);
 }
 private void select(Cell cell){
  if(selected!=cell.id())finishEdit();
  selected=cell.id();editing=true;cell.setSelectedWidget(true);
 }
 void render(){
  if(closed)return;
  if(root==null||root.getParent()==null){
   root=new PanelRoot();root.setOrientation(LinearLayout.VERTICAL);root.setPadding(dp(5),dp(5),dp(5),dp(5));
   window.setContentView(root);PanelStyle.window(this,window,24);
   stage=new FrameLayout(this);root.addView(stage,new LinearLayout.LayoutParams(-1,0));
   dots=new PageDots(this,pages,page,launcher);
   deck=new WidgetDeck(this,launcher,new WidgetDeck.Adapter(){
    public View page(int index){return new Page(index);}
    public void position(int from,int to,float progress){dots.pages(displayPages(),from,to,progress);}
   });
   stage.addView(deck,new FrameLayout.LayoutParams(-1,-1));
   root.addView(dots,new LinearLayout.LayoutParams(-1,launcher.dim("workspace_page_indicator_height",28)));
  }else{root.cancelInput();dismissEditors();finishEdit();}
  int footer=launcher.dim("workspace_page_indicator_height",28);
  int available=PanelGeometry.screen(this).y-PanelGeometry.inset(this,"status_bar_height")-PanelGeometry.inset(this,"navigation_bar_height");
  int height=Math.max(dp(48),Math.min(dp(boardHeight+14),available-footer-dp(20)));
  stage.getLayoutParams().height=height;stage.requestLayout();deck.reset(page);updateDots(page);
  int width=Math.round(getResources().getDisplayMetrics().widthPixels*.936f);
  root.measure(View.MeasureSpec.makeMeasureSpec(width,View.MeasureSpec.EXACTLY),View.MeasureSpec.makeMeasureSpec(0,View.MeasureSpec.UNSPECIFIED));
  PanelGeometry.position(this,window,width,root.getMeasuredHeight(),false,gesture);
 }
 private int displayPages(){return drag==null?pages:Math.max(pages,drag.displayPage+1);}
 private void updateDots(int index){
  int count=displayPages();dots.setContentDescription("第 "+(index+1)+" 页，共 "+count+" 页");
  dots.pages(count,index,index,0);
  boolean loop=prefs.getBoolean("loop",false);dots.setPaging(count>1&&(loop||index>0)?()->turn(-1):null,count>1&&(loop||index+1<count)?()->turn(1):null);
 }
 private int neighbor(int from,int direction){
  int target=from+direction;
  if(prefs.getBoolean("loop",false))target=(target+pages)%pages;
  return Math.max(0,Math.min(pages-1,target));
 }
 private void turn(int direction){
  if(drag!=null)return;deck.finish();int target=neighbor(page,direction);if(target==page)return;
  finishEdit();deck.preview(target,direction,0,false);commitTurn(target);
 }
 private void commitTurn(int target){
  page=target;prefs.edit().putInt("page",page).apply();updateDots(page);deck.settle(true,null);
 }
 private void addPage(){
  deck.finish();finishEdit();int next=pages++;page=next;save();updateDots(page);deck.go(page,1,false,null);
 }
 private void pageMenu(android.graphics.Point point){
  popup(root,point,"页面选项",new String[]{"添加插件","添加页面","移除当前页面"},n->{
   if(n==0)choose();
   else if(n==1)addPage();
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
  deck.finish();int sign=page<pages-1?1:-1;finishEdit();
  int remaining=Math.max(1,pages-1);if(!saveRemoval(kept,remaining,Math.min(page,remaining-1)))return;
  for(int id:removed){host.deleteAppWidgetId(id);cells.remove(id);}
  updateDots(page);deck.go(page,sign,true,null);
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
   if(bound&&(info.configure==null||(info.widgetFeatures&AppWidgetProviderInfo.WIDGET_FEATURE_CONFIGURATION_OPTIONAL)!=0)){commitWidget(id,page);showAdded(id);}
   else setup.request(info.provider,id,bound,false,page);
  }catch(RuntimeException failure){if(id>=0)host.deleteAppWidgetId(id);toast("无法绑定此插件");}
 }
 void commitWidget(int id,int requestedPage){
  readLayout();int target=Math.max(0,Math.min(requestedPage,pages-1));
  AppWidgetProviderInfo info=manager.getAppWidgetInfo(id);
  int w=info!=null&&info.targetCellWidth>0?Math.min(4,info.targetCellWidth):4;
  int h=info!=null&&info.targetCellHeight>0?Math.min(4,info.targetCellHeight):2;
  int x=0,y=0;boolean found=false;
  for(int row=0;row<=4-h&&!found;row++)for(int col=0;col<=4-w;col++)if(free(-1,target,col,row,w,h)){x=col;y=row;found=true;break;}
  if(!found)target=pages++;page=target;editing=false;selected=-1;items.put(item(id,target,x,y,w,h));save();
 }
 private void showAdded(int id){
  if(deck==null){render();return;}
  deck.finish();finishEdit();updateDots(page);
  Runnable reveal=()->{Cell c=cells.get(id);if(c!=null){c.setAlpha(0);c.setScaleX(.9f);c.setScaleY(.9f);c.animate().alpha(1).scaleX(1).scaleY(1).setDuration(dropMin).start();}};
  if(deck.index()!=page)deck.go(page,1,false,reveal);
  else{((Page)deck.current()).sync();reveal.run();}
 }
 private boolean free(int id,int p,int x,int y,int w,int h){WidgetGrid.Cell candidate=new WidgetGrid.Cell(x,y,w,h);if(!WidgetGrid.valid(candidate))return false;for(int i=0;i<items.length();i++){JSONObject a=items.optJSONObject(i);if(a==null||a.optInt("id")==id||a.optInt("page")!=p)continue;if(WidgetGrid.overlaps(candidate,new WidgetGrid.Cell(a.optInt("x"),a.optInt("y"),a.optInt("w"),a.optInt("h"))))return false;}return true;}
 private JSONObject findItem(int id){for(int i=0;i<items.length();i++){JSONObject item=items.optJSONObject(i);if(item!=null&&item.optInt("id")==id)return item;}return null;}
 private void menu(Cell cell){
  final int id=cell.id();
  if(findItem(id)==null)return;
  WidgetCapabilities caps=cell.capabilities();if(caps==null)return;
  ArrayList<String> labels=new ArrayList<>();ArrayList<Runnable> actions=new ArrayList<>();
  if(caps.settings!=null){labels.add("设置");actions.add(()->openProvider(caps.settings));}
  if(caps.appearance!=null){labels.add("自定义外观");actions.add(()->openProvider(caps.appearance));}
  if(caps.reconfigure){labels.add("编辑插件");actions.add(()->{
   if(findItem(id)==null)return;try{setup.request(cell.info.provider,id,true,true,page);}catch(RuntimeException failure){toast("插件设置无法打开");}
  });}
  labels.add("移除插件");actions.add(()->{int token=editorGeneration;
   showDialog(new GlassDialogBuilder(this).setTitle("移除此插件？").setMessage("此插件将从当前页面移除。")
        .setNegativeButton("取消",null).setPositiveDestructive("移除",(d,n)->{if(usable()&&token==editorGeneration)removeWidget(id);}).create());});
  popup(cell,null,"插件选项",labels.toArray(new String[0]),n->actions.get(n).run());
 }
 private void openProvider(Intent intent){
  try{startActivity(intent);leaveForProvider.run();}
  catch(ActivityNotFoundException|SecurityException unavailable){toast("插件设置无法打开");}
 }
 private void removeWidget(int id){
  if(findItem(id)==null)return;
  JSONArray next=new JSONArray();for(int i=0;i<items.length();i++){JSONObject item=items.optJSONObject(i);if(item!=null&&item.optInt("id")!=id)next.put(item);}
  if(saveRemoval(next,pages,page)){
   host.deleteAppWidgetId(id);Cell old=cells.remove(id);if(old!=null){
    old.setSelectedWidget(false);old.animate().alpha(0).scaleX(.9f).scaleY(.9f).setDuration(dropMin)
      .withEndAction(()->{if(!closed&&old.getParent() instanceof Page owner)owner.sync();}).start();
   }
  }
 }


 private final class Page extends FrameLayout {
  final int index;
  Page(int index){super(WidgetPanel.this);this.index=index;setContentDescription("插件页面");
   setOnLongClickListener(v->{pageMenu(new android.graphics.Point(stage.getLeft()+getWidth()/2,stage.getTop()+getHeight()/2));return true;});sync();}
  void sync(){
   Set<Integer> needed=new HashSet<>();
   for(int i=0;i<items.length();i++){
    JSONObject item=items.optJSONObject(i);if(item==null||item.optInt("page")!=index)continue;
    int id=item.optInt("id");if(drag!=null&&drag.cell.id()==id)continue;
    AppWidgetProviderInfo info=manager.getAppWidgetInfo(id);
    if(info==null){cells.remove(id);continue;}
    Cell cell=cells.get(id);
    if(cell==null||!cell.info.provider.equals(info.provider)){cell=new Cell(item,info);cells.put(id,cell);}
    needed.add(id);cell.data=item;cell.info=info;cell.caps=null;
    if(cell.getParent()!=this){
     if(cell.getParent() instanceof ViewGroup parent)parent.removeView(cell);
     addView(cell);
    }
   }
   for(int i=getChildCount()-1;i>=0;i--){View child=getChildAt(i);
    if(!(child instanceof Cell cell)||!needed.contains(cell.id()))removeViewAt(i);
   }
   if(needed.isEmpty()&&drag==null){
    TextView hint=new TextView(WidgetPanel.this);hint.setText("长按空白处添加插件");hint.setGravity(Gravity.CENTER);hint.setTextSize(14);
    hint.setTextColor(ColorOsTheme.secondary(WidgetPanel.this));addView(hint);
   }
   requestLayout();
  }
  @Override protected void onMeasure(int ws,int hs){
   int w=MeasureSpec.getSize(ws),h=MeasureSpec.getSize(hs);setMeasuredDimension(w,h);
   for(int i=0;i<getChildCount();i++){View v=getChildAt(i);int cw=w,ch=h;
    if(v instanceof Cell cell){WidgetGrid.Cell g=cell.geometry();cw=(g.x+g.w)*w/4-g.x*w/4;ch=(g.y+g.h)*h/4-g.y*h/4;}
    v.measure(MeasureSpec.makeMeasureSpec(cw,MeasureSpec.EXACTLY),MeasureSpec.makeMeasureSpec(ch,MeasureSpec.EXACTLY));
   }
  }
  @Override protected void onLayout(boolean changed,int l,int t,int r,int b){
   for(int i=0;i<getChildCount();i++){View v=getChildAt(i);int x=0,y=0;
    if(v instanceof Cell cell){WidgetGrid.Cell g=cell.geometry();x=g.x*getWidth()/4;y=g.y*getHeight()/4;}
    v.layout(x,y,x+v.getMeasuredWidth(),y+v.getMeasuredHeight());
   }
  }
 }
 private final class Cell extends FrameLayout {
  JSONObject data;AppWidgetProviderInfo info;final AppWidgetHostView widget;WidgetSelectionFrame frame;
  WidgetGrid.Edit resize;WidgetCapabilities caps;
  Cell(JSONObject data,AppWidgetProviderInfo info){
   super(WidgetPanel.this);this.data=data;this.info=info;setContentDescription("插件 "+id());setPadding(dp(3),dp(3),dp(3),dp(3));
   setBackground(PanelStyle.shape(WidgetPanel.this,data.optInt("color",info.provider.getPackageName().equals("com.coloros.alarmclock")?0xff45484f:0),20));
   widget=host.createView(WidgetPanel.this,id(),info);
   int pad=Math.min(dp(5),Math.min(Math.min(widget.getPaddingLeft(),widget.getPaddingRight()),Math.min(widget.getPaddingTop(),widget.getPaddingBottom())));
   widget.setPadding(pad,pad,pad,pad);addView(widget,new LayoutParams(-1,-1));
   setOnLongClickListener(v->{select(this);menu(this);return true;});
  }
  int id(){return data.optInt("id");}
  WidgetGrid.Cell saved(){return new WidgetGrid.Cell(data.optInt("x"),data.optInt("y"),data.optInt("w"),data.optInt("h"));}
  WidgetGrid.Cell geometry(){return resize==null?saved():resize.current();}
  WidgetCapabilities capabilities(){
   if(caps==null)caps=new WidgetCapabilities(WidgetPanel.this,info,deck.getWidth(),deck.getHeight(),saved());return caps;
  }
  void setSelectedWidget(boolean on){
   setSelected(on);
   if(!on){if(frame!=null)frame.clear();resize=null;widget.setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_AUTO);return;}
   caps=null;WidgetCapabilities available=capabilities();
   if(frame==null){frame=launcher.selectionFrame(this);addView(frame,new LayoutParams(-1,-1));}
   frame.select(saved(),available.resizable());widget.setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_NO_HIDE_DESCENDANTS);
  }
  boolean grip(float x,float y){return frame!=null&&frame.isShown()&&frame.hit(x-getPaddingLeft(),y-getPaddingTop(),saved());}
  void beginResize(){
   WidgetCapabilities available=capabilities();
   int edges=(available.horizontal?(getLayoutDirection()==LAYOUT_DIRECTION_RTL?WidgetGrid.LEFT:WidgetGrid.RIGHT):0)|(available.vertical?WidgetGrid.BOTTOM:0);
   resize=new WidgetGrid.Edit(saved(),edges);frame.sizing(true);
  }
  void moveResize(float dx,float dy){
   if(resize==null)return;
   int x=caps.horizontal?Math.round(4*dx/deck.getWidth()):0,y=caps.vertical?Math.round(4*dy/deck.getHeight()):0;
   frame.shape(resize.move(x,y,c->caps.permits(c)&&free(id(),page,c.x,c.y,c.w,c.h)));((Page)getParent()).requestLayout();
  }
  void endResize(boolean success){
   if(resize==null)return;WidgetGrid.Cell g=resize.current();resize=null;frame.sizing(false);
   if(success){putCell(data,page,g);save();}frame.shape(saved());if(getParent() instanceof Page p)p.requestLayout();
  }
  @Override public void onInitializeAccessibilityNodeInfo(android.view.accessibility.AccessibilityNodeInfo node){
   super.onInitializeAccessibilityNodeInfo(node);
   if(deck==null||deck.getWidth()==0||drag!=null||!editing||selected!=id())return;
   for(GridAction action:GridAction.values())if(accessiblePlan(action)!=null)
    node.addAction(new android.view.accessibility.AccessibilityNodeInfo.AccessibilityAction(action.id,action.label));
  }
  private List<WidgetDrop.Slot> accessiblePlan(GridAction a){
   WidgetGrid.Cell g=saved();int src=data.optInt("page"),target=src+a.dp;
   if(target<0||target>pages)return null;
   if(a.size){
    WidgetGrid.Cell c=WidgetGrid.propose(g,a.dx,a.dy,a.dy==0?WidgetGrid.RIGHT:WidgetGrid.BOTTOM);
    return c!=null&&capabilities().permits(c)&&free(id(),src,c.x,c.y,c.w,c.h)?Collections.singletonList(new WidgetDrop.Slot(id(),src,c)):null;
   }
   return WidgetDrop.plan(slots(),new WidgetDrop.Slot(id(),src,g),target,g.x+a.dx,g.y+a.dy);
  }
  @Override public boolean performAccessibilityAction(int action,Bundle args){
   for(GridAction a:GridAction.values())if(a.id==action){
    if(closed||drag!=null)return false;
    List<WidgetDrop.Slot> plan=accessiblePlan(a);if(plan==null)return false;
    int target=data.optInt("page")+a.dp;deck.finish();finishEdit();
    if(!savePlacement(plan,prefs.getString("layout_v3","[]"),target))return false;
    if(deck.index()!=page)deck.go(page,a.dp,false,null);else ((Page)deck.current()).sync();
    updateDots(page);return true;
   }
   return super.performAccessibilityAction(action,args);
  }
 }
 private static void putCell(JSONObject data,int page,WidgetGrid.Cell c){
  try{data.put("page",page).put("x",c.x).put("y",c.y).put("w",c.w).put("h",c.h);}
  catch(JSONException invalid){throw new IllegalStateException(invalid);}
 }
 private List<WidgetDrop.Slot> slots(){
  List<WidgetDrop.Slot> result=new ArrayList<>();
  for(int i=0;i<items.length();i++){JSONObject o=items.optJSONObject(i);if(o!=null)result.add(new WidgetDrop.Slot(o.optInt("id"),o.optInt("page"),
     new WidgetGrid.Cell(o.optInt("x"),o.optInt("y"),o.optInt("w"),o.optInt("h"))));}
  return result;
 }
 private final class Drag {
  final Cell cell;final WidgetDrop.Slot origin;final List<WidgetDrop.Slot> slots;
  final String layout;final int originalPages;final float grabX,grabY;
  int displayPage,targetX=-1,targetY=-1,plannedPage=-1,edge;
  List<WidgetDrop.Slot> plan;
  Drag(Cell cell,float grabX,float grabY){
   this.cell=cell;this.grabX=grabX;this.grabY=grabY;originalPages=pages;displayPage=page;
   layout=prefs.getString("layout_v3","[]");origin=new WidgetDrop.Slot(cell.id(),page,cell.saved());
   slots=WidgetPanel.this.slots();
  }
 }
 private void beginDrag(Cell cell,float x,float y){
  hideMenuForTouch();
  drag=new Drag(cell,x-cell.getLeft(),y-cell.getTop());cell.setSelectedWidget(false);
  int width=cell.getWidth(),height=cell.getHeight();((Page)cell.getParent()).removeView(cell);
  stage.addView(cell,new FrameLayout.LayoutParams(width,height));cell.layout(0,0,width,height);
  cell.setElevation(dp(8));float scale=1+launcher.dim("pre_drag_view_scale",6)/(float)Math.max(1,width);
  cell.animate().scaleX(scale).scaleY(scale).setDuration(dropMin).start();
 }
 private void updateDrag(float x,float y){
  Drag d=drag;if(d==null)return;
  float left=x-d.grabX,top=y-d.grabY;d.cell.setTranslationX(left);d.cell.setTranslationY(top);
  int gx=Math.max(0,Math.min(4-d.origin.cell.w,Math.round(4*left/deck.getWidth())));
  int gy=Math.max(0,Math.min(4-d.origin.cell.h,Math.round(4*top/deck.getHeight())));
  if(d.plannedPage!=d.displayPage||d.targetX!=gx||d.targetY!=gy){
   d.plannedPage=d.displayPage;d.targetX=gx;d.targetY=gy;d.plan=WidgetDrop.plan(d.slots,d.origin,d.displayPage,gx,gy);previewPositions(d);
  }
  int edge=y<0||y>=stage.getHeight()?0:x<=scrollZone?-1:x>=stage.getWidth()-scrollZone?1:0;
  if(d.edge!=edge){int delay=d.edge==0?400:200;d.edge=edge;root.removeCallbacks(edgeTurn);if(edge!=0)root.postDelayed(edgeTurn,delay);}
 }
 private void previewPositions(Drag d){
  for(Cell cell:cells.values()){
   if(cell==d.cell||!(cell.getParent() instanceof Page owner)||owner.index!=d.displayPage)continue;
   WidgetDrop.Slot target=null;if(d.plan!=null)for(WidgetDrop.Slot s:d.plan)if(s.id==cell.id()){target=s;break;}
   float dx=0,dy=0,alpha=1;
   if(target!=null){if(target.page!=owner.index)alpha=.25f;else{
    dx=(target.cell.x-cell.saved().x)*deck.getWidth()/4f;dy=(target.cell.y-cell.saved().y)*deck.getHeight()/4f;}}
   cell.animate().translationX(dx).translationY(dy).alpha(alpha).setDuration(dropMin).setInterpolator(WidgetDeck.SCROLL).start();
  }
 }
 private final Runnable edgeTurn=new Runnable(){public void run(){
  Drag d=drag;if(d==null||d.edge==0)return;
  int target=d.displayPage+d.edge;if(target<0||target>d.originalPages)return;
  deck.finish();d.displayPage=target;updateDots(target);
  deck.go(target,d.edge,false,()->{
   if(drag==d){updateDrag(root.lastX-stage.getLeft(),root.lastY-stage.getTop());
    if(d.edge!=0){root.removeCallbacks(this);root.postDelayed(this,400);}}
  });
  updateDrag(root.lastX-stage.getLeft(),root.lastY-stage.getTop());
 }};
 private void endDrag(boolean accept){
  Drag d=drag;if(d==null)return;
  drag=null;root.removeCallbacks(edgeTurn);deck.finish();
  float left=d.cell.getTranslationX(),top=d.cell.getTranslationY();
  boolean dropped=accept&&d.plan!=null&&d.originalPages==prefs.getInt("pages",1)&&savePlacement(d.plan,d.layout,d.displayPage);
  if(!dropped){if(accept)toast("此处无法放置插件");readLayout();}
  stage.removeView(d.cell);d.cell.animate().cancel();d.cell.setElevation(0);editing=false;selected=-1;
  for(Cell cell:cells.values()){
   cell.animate().cancel();cell.setTranslationX(0);cell.setTranslationY(0);cell.setAlpha(1);cell.setScaleX(1);cell.setScaleY(1);
  }
  if(closed)return;
  if(deck.index()!=page)deck.go(page,page>deck.index()?1:-1,false,null);
  else ((Page)deck.current()).sync();
  updateDots(page);
  if(d.cell.getParent() instanceof Page container){
   WidgetGrid.Cell end=d.cell.saved();float dx=left-end.x*deck.getWidth()/4f,dy=top-end.y*deck.getHeight()/4f;
   d.cell.setTranslationX(dx);d.cell.setTranslationY(dy);
   d.cell.animate().translationX(0).translationY(0).setDuration(Math.min(dropMax,dropMin+Math.round(Math.hypot(dx,dy)*.35f))).setInterpolator(WidgetDeck.SCROLL).start();
  }
 }
 private boolean savePlacement(List<WidgetDrop.Slot> plan,String original,int targetPage){
  if(!original.equals(prefs.getString("layout_v3","[]")))return false;
  try{
   JSONArray copy=new JSONArray(original);if(!copy.toString().equals(items.toString()))return false;
   Map<Integer,WidgetDrop.Slot> changes=new HashMap<>();
   for(WidgetDrop.Slot s:plan)changes.put(s.id,s);
   for(int i=0;i<copy.length();i++){
    JSONObject o=copy.optJSONObject(i);if(o==null)continue;
    WidgetDrop.Slot s=changes.remove(o.optInt("id"));if(s!=null)putCell(o,s.page,s.cell);
   }
   int count=Math.max(pages,targetPage+1);
   if(!changes.isEmpty()||!prefs.edit().putString("layout_v3",copy.toString()).putInt("pages",count).putInt("page",targetPage).commit())return false;
   items=copy;pages=count;page=targetPage;return true;
  }catch(JSONException invalid){return false;}
 }
 /** Own the entire pointer sequence, including the MOVE following the original long press. */
 private final class PanelRoot extends LinearLayout {
  final int slop=ViewConfiguration.get(WidgetPanel.this).getScaledTouchSlop();
  private final int minFling=launcher.dim("fling_threshold_velocity",500);
  float downX,downY,lastX,lastY,startingOffset;long downTime;Cell downCell;
  boolean tracking,held,paging,blocked,resizing,childDown,multiple;
  VelocityTracker velocity;
  final Runnable openMenu=this::longPress;
  PanelRoot(){super(WidgetPanel.this);}
  Cell cellAt(float x,float y){
   if(!(deck.current() instanceof Page p))return null;
   float localX=x-stage.getLeft(),localY=y-stage.getTop();
   for(int i=0;i<p.getChildCount();i++){View child=p.getChildAt(i);if(child instanceof Cell c&&
     localX>=c.getLeft()&&localX<c.getRight()&&localY>=c.getTop()&&localY<c.getBottom())return c;}
   return null;
  }
  void cancelChildren(){
   if(!childDown)return;childDown=false;
   MotionEvent e=MotionEvent.obtain(downTime,SystemClock.uptimeMillis(),MotionEvent.ACTION_CANCEL,lastX,lastY,0);
   try{super.dispatchTouchEvent(e);}finally{e.recycle();}
  }
  void cancelInput(){
   removeCallbacks(openMenu);removeCallbacks(edgeTurn);cancelChildren();
   if(resizing&&downCell!=null)downCell.endResize(false);
   if(drag!=null)endDrag(false);
   if(paging&&deck!=null)settleSwipe(false,0,false);
   tracking=resizing=paging=held=blocked=multiple=false;downCell=null;
   if(velocity!=null){velocity.recycle();velocity=null;}
  }
  void longPress(){
   if(!tracking||held||resizing||multiple||paging||closed||root!=this||!isShown())return;
   held=true;cancelChildren();performHapticFeedback(HapticFeedbackConstants.LONG_PRESS);
   if(downCell!=null&&downCell.isAttachedToWindow()&&findItem(downCell.id())!=null){select(downCell);menu(downCell);}
   else pageMenu(new android.graphics.Point(Math.round(downX),Math.round(downY)));
  }
  private void down(MotionEvent e){
   boolean caught=deck.catchScroll();if(!caught)deck.finish();
   downX=lastX=e.getX();downY=lastY=e.getY();downTime=e.getDownTime();
   tracking=true;held=multiple=blocked=resizing=childDown=false;paging=caught;
   startingOffset=caught?deck.progress()*deck.direction():0;downCell=caught?null:cellAt(downX,downY);
   if(velocity!=null)velocity.recycle();velocity=VelocityTracker.obtain();velocity.addMovement(e);
   if(caught)return;
   boolean wasSelected=downCell!=null&&editing&&downCell.id()==selected;
   hideMenuForTouch();
   if(wasSelected&&downCell.grip(downX-stage.getLeft()-downCell.getLeft(),downY-stage.getTop()-downCell.getTop())){
    blocked=resizing=true;downCell.beginResize();return;
   }
   if(editing){blocked=true;if(!wasSelected)finishEdit();}
   postDelayed(openMenu,ViewConfiguration.getLongPressTimeout());
   if(!blocked){childDown=true;super.dispatchTouchEvent(e);}
  }
  @Override public boolean dispatchTouchEvent(MotionEvent e){
   int action=e.getActionMasked();
   if(action==MotionEvent.ACTION_DOWN){if(tracking)cancelInput();down(e);return true;}
   if(!tracking)return true;
   lastX=e.getX();lastY=e.getY();if(velocity!=null)velocity.addMovement(e);
   if(action==MotionEvent.ACTION_POINTER_DOWN){multiple=true;cancelInput();return true;}
   float dx=lastX-downX,dy=lastY-downY;
   if(action==MotionEvent.ACTION_MOVE){
    if(Math.hypot(dx,dy)>slop){
     removeCallbacks(openMenu);
     if(!resizing&&drag==null&&downCell!=null&&(held||blocked&&editing&&selected==downCell.id())){
      cancelChildren();beginDrag(downCell,downX-stage.getLeft(),downY-stage.getTop());
     }else if(!held&&!resizing&&!paging&&drag==null&&pages>1&&Math.abs(dx)>slop&&Math.abs(dx)>Math.abs(dy)*1.2f){
      cancelChildren();paging=true;finishEdit();
     }
    }
    if(drag!=null)updateDrag(lastX-stage.getLeft(),lastY-stage.getTop());
    else if(resizing)downCell.moveResize(dx,dy);
    else if(paging){
     float offset=startingOffset-dx/Math.max(1,stage.getWidth());
     int sign=offset>=0?1:-1,target=neighbor(deck.index(),sign);
     if(target!=deck.index())deck.preview(target,sign,Math.min(1,Math.abs(offset)),false);
     else if(deck.hasNeighbor())deck.preview(deck.target(),deck.direction(),0,false);
    }else if(!held&&!blocked)super.dispatchTouchEvent(e);
    return true;
   }
   if(action==MotionEvent.ACTION_UP||action==MotionEvent.ACTION_CANCEL){
    removeCallbacks(openMenu);tracking=false;boolean up=action==MotionEvent.ACTION_UP;
    if(drag!=null){
     boolean inside=lastX>=stage.getLeft()-slop&&lastX<=stage.getRight()+slop&&lastY>=stage.getTop()-slop&&lastY<=stage.getBottom()+slop;
     if(up&&inside)updateDrag(lastX-stage.getLeft(),lastY-stage.getTop());
     endDrag(up&&inside);
    }else if(resizing){if(up)downCell.moveResize(dx,dy);downCell.endResize(up);}
    else if(paging){
     velocity.computeCurrentVelocity(1000);
     float speed=velocity.getXVelocity(),forward=-deck.direction()*speed;
     boolean advance=deck.hasNeighbor()&&deck.progress()>0&&(Math.abs(speed)>minFling&&Math.abs(dx)>slop?forward>0:deck.progress()>=.4f);
     settleSwipe(advance,speed,up);
    }else if(!blocked&&!held)super.dispatchTouchEvent(e);
    else if(blocked&&!held&&up)finishEdit();
    held=paging=resizing=blocked=childDown=false;downCell=null;
    velocity.recycle();velocity=null;return true;
   }
   if(childDown)super.dispatchTouchEvent(e);return true;
  }
  @Override public boolean onTouchEvent(MotionEvent e){return true;}
  private void settleSwipe(boolean advance,float speed,boolean commit){
   if(!deck.hasNeighbor())return;
   if(commit){page=advance?deck.target():deck.index();prefs.edit().putInt("page",page).apply();updateDots(page);}
   if(page==deck.index()||page==deck.target())deck.settleGesture(page==deck.target(),speed);
   else deck.go(page,page>deck.index()?1:-1,false,null);
  }
  @Override protected void onDetachedFromWindow(){cancelInput();super.onDetachedFromWindow();}
 }
}
