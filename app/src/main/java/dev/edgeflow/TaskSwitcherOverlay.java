package dev.edgeflow;
import android.accessibilityservice.AccessibilityService;import android.app.Dialog;import android.content.*;import android.graphics.Color;import android.os.Handler;import android.os.Looper;import android.view.*;import android.widget.*;import org.json.*;import java.io.*;import java.util.concurrent.*;
/** Real Android recent tasks, not a list of arbitrary installed applications. */
public final class TaskSwitcherOverlay {
 private final LinearLayout.LayoutParams viewport=new LinearLayout.LayoutParams(-1,0,1);
 private static TaskSwitcherOverlay current;private static final ExecutorService WORK=Executors.newSingleThreadExecutor();private static final Handler MAIN=new Handler(Looper.getMainLooper());
 private final AccessibilityService service;private final Context context;private final float[] anchor;private final Dialog dialog;private final LinearLayout root,list;private final int cardColumns,panelWidth,heightLimit;private final java.util.ArrayList<Dialog> children=new java.util.ArrayList<>();private replica.ColorMotion motion;private volatile boolean closed;private boolean loading;private RecentTaskList.Request recentRequest;private volatile Process previewProcess;private volatile int previewGeneration;private final java.util.LinkedHashMap<Integer,ImageView> previewViews=new java.util.LinkedHashMap<>();private final java.util.ArrayList<android.graphics.Bitmap> previewBitmaps=new java.util.ArrayList<>();
 public static void show(AccessibilityService s,float[] anchor){dismissAll();QuickLauncherOverlay.dismissAll();try{current=new TaskSwitcherOverlay(s,anchor);}catch(Exception e){android.util.Log.e("EdgeTasks","show",e);Toast.makeText(s,"任务切换器暂不可用",Toast.LENGTH_SHORT).show();}}
 public static void dismissAll(){if(current!=null)current.remove();}
 private TaskSwitcherOverlay(AccessibilityService s,float[] a){service=s;anchor=a.clone();int theme=s.getResources().getIdentifier(replica.PanelStyle.dark(s)?"ReplicaGlassDark":"ReplicaGlass","style",s.getPackageName());context=new ContextThemeWrapper(s,theme);
  dialog=new replica.PanelOverlayDialog(context,this::closeFromOutside,this::closeFromOutside);root=new LinearLayout(context){private boolean blocked;@Override public boolean dispatchTouchEvent(MotionEvent event){int action=event.getActionMasked();if(action==MotionEvent.ACTION_DOWN)blocked=!children.isEmpty();boolean consume=blocked;if(action==MotionEvent.ACTION_UP||action==MotionEvent.ACTION_CANCEL)blocked=false;return consume||super.dispatchTouchEvent(event);}};root.setOrientation(LinearLayout.VERTICAL);root.setPadding(dp(12),dp(10),dp(12),dp(12));dialog.setContentView(root);dialog.setOnDismissListener(d->{closed=true;closeChildren();clearPreviews();if(current==this)current=null;if(motion!=null)motion.cancel();});Window w=dialog.getWindow();w.setTitle(context.getString(dev.edgeflow.R.string.app_name)+" 任务切换器");replica.PanelStyle.window(context,w,24);
  LinearLayout header=new LinearLayout(context);header.setGravity(Gravity.CENTER_VERTICAL);header.setPadding(dp(10),0,dp(4),0);replica.PanelStyle.headerCard(header,16);TextView title=label("任务切换器",19);header.addView(title,new LinearLayout.LayoutParams(0,dp(46),1));TextView refresh=label("刷新",14);refresh.setPadding(dp(12),0,dp(12),0);refresh.setOnClickListener(v->{if(children.isEmpty())load();});header.addView(refresh,new LinearLayout.LayoutParams(-2,dp(46)));TextView limit=label("数量",14);limit.setPadding(dp(10),0,dp(10),0);limit.setOnClickListener(v->limit());header.addView(limit,new LinearLayout.LayoutParams(-2,dp(46)));root.addView(header);
  android.graphics.Point screen=replica.PanelGeometry.screen(context);
  cardColumns=screen.x>screen.y&&screen.x>=dp(520)?2:1;
  panelWidth=Math.min(dp(cardColumns==2?510:330),screen.x-dp(28));
  heightLimit=Math.min(dp(475),screen.y-replica.PanelGeometry.inset(context,"status_bar_height")-replica.PanelGeometry.inset(context,"navigation_bar_height")-dp(12));
  ScrollView scroll=new replica.ColorSettingsScrollView(context);list=new LinearLayout(context);list.setOrientation(LinearLayout.VERTICAL);scroll.addView(list);
  viewport.topMargin=dp(8);viewport.bottomMargin=dp(8);root.addView(scroll,viewport);
  TextView more=label("系统最近任务",14);more.setGravity(Gravity.CENTER);replica.PanelStyle.contentCard(more,16,true);more.setOnClickListener(v->{if(!children.isEmpty())return;dismiss();ActionExecutor.execute(service,"recents",anchor);});root.addView(more,new LinearLayout.LayoutParams(-1,dp(42)));dialog.show();replica.PanelGeometry.position(context,w,panelWidth,heightLimit,false,anchor);motion=replica.ColorMotion.forTool(w,replica.ToolAnimation.TASKS,anchor);motion.enter();load(false);
 }
 private int dp(float n){return replica.PanelStyle.dp(context,n);}private TextView label(String text,int size){TextView t=new TextView(context);t.setText(text);t.setTextSize(size);t.setTextColor(replica.ColorOsTheme.text(context));t.setIncludeFontPadding(false);Runnable font=()->{t.setTextColor(replica.ColorOsTheme.text(context));t.setTypeface(replica.ColorOsTheme.typeface(t.getContext(),t.getTextSize()/t.getResources().getDisplayMetrics().scaledDensity>=18?600:500));};font.run();replica.ColorOsTheme.watch(t,font);t.setGravity(Gravity.CENTER_VERTICAL);return t;}
 private String command(String args){return "CLASSPATH='"+service.getApplicationInfo().sourceDir.replace("'","'\\''")+"' app_process /system/bin dev.edgeflow.RecentTasksBridge "+args;}
 private void load(){load(true);}
 private void load(boolean refresh){
  if(closed||loading)return;loading=true;previewGeneration++;
  if(previewProcess!=null)previewProcess.destroyForcibly();clearPreviews();list.removeAllViews();
  list.addView(label("正在读取最近任务…",14),new LinearLayout.LayoutParams(-1,dp(50)));
  recentRequest=RecentTaskList.read(service,refresh,(tasks,error)->{if(!closed){loading=false;render(tasks,error);}});
 }
 private void render(JSONArray tasks,String error){
  if(closed)return;list.removeAllViews();
  if(tasks==null){
   TextView failure=label((error==null?"读取失败":error)+" · 点按重试",14);failure.setMinHeight(dp(56));
   failure.setBackground(replica.ColorDialogBuilder.touchBackground(context,replica.ColorOsTheme.card(context),14));failure.setOnClickListener(v->load());list.addView(failure);fitCards();return;
  }
  int max=service.getSharedPreferences("edgeflow_launcher",Context.MODE_PRIVATE).getInt("recent_limit",10),count=0;
  LinearLayout line=null;
  for(int i=0;i<Math.min(max,tasks.length());i++){
   JSONObject json=tasks.optJSONObject(i);if(json==null)continue;
   ComponentName component=ComponentName.unflattenFromString(json.optString("component"));
   int task=json.optInt("taskId",-1);if(task<0||component==null)continue;
   if(count%cardColumns==0){line=new LinearLayout(context);list.addView(line,new LinearLayout.LayoutParams(-1,-2));}
   LinearLayout card=new LinearLayout(context);card.setOrientation(LinearLayout.VERTICAL);card.setPadding(dp(8),dp(8),dp(8),dp(4));
   replica.PanelStyle.contentCard(card,16,true);card.setFocusable(true);
   ImageView preview=new ImageView(context);String name=component.getPackageName();
   try{preview.setImageDrawable(service.getPackageManager().getActivityIcon(component));
    name=service.getPackageManager().getActivityInfo(component,0).loadLabel(service.getPackageManager()).toString();
   }catch(android.content.pm.PackageManager.NameNotFoundException missing){preview.setImageDrawable(service.getPackageManager().getDefaultActivityIcon());}
   preview.setScaleType(ImageView.ScaleType.CENTER_INSIDE);preview.setBackground(replica.PanelStyle.shape(context,0x24202329,12));preview.setClipToOutline(true);
   preview.setContentDescription("任务图标，暂无预览");previewViews.put(task,preview);
   card.addView(preview,new LinearLayout.LayoutParams(-1,dp(cardColumns==1?220:112)));
   TextView title=label(name,15);title.setSingleLine();title.setEllipsize(android.text.TextUtils.TruncateAt.END);
   LinearLayout caption=new LinearLayout(context);caption.setGravity(Gravity.CENTER_VERTICAL);ImageView appIcon=new ImageView(context);android.graphics.drawable.Drawable source=preview.getDrawable();if(source!=null&&source.getConstantState()!=null)appIcon.setImageDrawable(source.getConstantState().newDrawable(context.getResources()));appIcon.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);caption.addView(appIcon,new LinearLayout.LayoutParams(dp(24),dp(24)));
   title.setPadding(dp(8),0,0,0);caption.addView(title,new LinearLayout.LayoutParams(0,-1,1));card.addView(caption,new LinearLayout.LayoutParams(-1,dp(40)));card.setContentDescription("切换到 "+name);
   card.setOnClickListener(v->{if(!children.isEmpty())return;dismiss();ActionExecutor.root(service,command("launch "+task),"切换任务");});
   LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(0,-2,1);lp.setMargins(dp(2),0,dp(2),dp(6));line.addView(card,lp);count++;
  }
  if(count==0){list.addView(label("没有最近任务",14),new LinearLayout.LayoutParams(-1,dp(80)));fitCards();return;}
  if(cardColumns==2&&count%2!=0)line.addView(new View(context),new LinearLayout.LayoutParams(0,0,1));
  fitCards();loadPreviews();
 }
 private void fitCards(){
  // Measure the list at its final width. Include the two margins outside the scrolling area,
  // so the first card and the footer keep their spacing even when the list is short.
  int width=panelWidth-root.getPaddingLeft()-root.getPaddingRight();
  list.measure(View.MeasureSpec.makeMeasureSpec(width,View.MeasureSpec.EXACTLY),View.MeasureSpec.UNSPECIFIED);
  int wanted=root.getPaddingTop()+root.getPaddingBottom()+dp(46)+dp(42)+viewport.topMargin+viewport.bottomMargin+list.getMeasuredHeight();
  replica.PanelGeometry.position(context,dialog.getWindow(),panelWidth,Math.min(heightLimit,wanted),false,anchor);
 }
 private void clearPreviews(){for(ImageView view:previewViews.values())view.setImageDrawable(null);previewViews.clear();for(android.graphics.Bitmap bitmap:previewBitmaps)if(!bitmap.isRecycled())bitmap.recycle();previewBitmaps.clear();}
 private void loadPreviews(){if(closed||previewViews.isEmpty())return;final int generation=previewGeneration;StringBuilder ids=new StringBuilder();for(int id:previewViews.keySet()){if(ids.length()>0)ids.append(',');ids.append(id);}String cmd="CLASSPATH='"+service.getApplicationInfo().sourceDir.replace("'","'\\''")+"' app_process /system/bin dev.edgeflow.TaskPreviewBridge "+ids;
  WORK.execute(()->{java.util.LinkedHashMap<Integer,android.graphics.Bitmap> results=new java.util.LinkedHashMap<>();Process p=null;try{if(closed||generation!=previewGeneration)return;p=new ProcessBuilder("su","-c",cmd).redirectErrorStream(true).start();previewProcess=p;if(closed||generation!=previewGeneration)throw new IOException("Preview cancelled");final Process running=p;ByteArrayOutputStream out=new ByteArrayOutputStream();Thread reader=new Thread(()->{try(InputStream input=running.getInputStream()){byte[] bytes=new byte[4096];int n;while((n=input.read(bytes))!=-1)synchronized(out){if(out.size()<4194304)out.write(bytes,0,Math.min(n,4194304-out.size()));}}catch(IOException ignored){}},"task-preview-reader");reader.start();if(!p.waitFor(15,TimeUnit.SECONDS)){p.destroyForcibly();throw new IOException("Task preview timeout");}reader.join(1000);String raw;synchronized(out){raw=out.toString("UTF-8");}int at=raw.indexOf("EDGE_TASK_PREVIEWS=");if(p.exitValue()!=0||at<0)throw new IOException("Task preview API unavailable");JSONArray previews=new JSONArray(raw.substring(at+19).trim());if(previews.length()>30)throw new IOException("Too many previews");for(int i=0;i<previews.length();i++){JSONObject preview=previews.getJSONObject(i);String encoded=preview.optString("image");if(encoded.isEmpty()||encoded.length()>131072)continue;byte[] png=java.util.Base64.getDecoder().decode(encoded);android.graphics.BitmapFactory.Options bounds=new android.graphics.BitmapFactory.Options();bounds.inJustDecodeBounds=true;android.graphics.BitmapFactory.decodeByteArray(png,0,png.length,bounds);if(bounds.outWidth<1||bounds.outHeight<1||bounds.outWidth>320||bounds.outHeight>320)continue;android.graphics.Bitmap bitmap=android.graphics.BitmapFactory.decodeByteArray(png,0,png.length);if(bitmap!=null){android.graphics.Bitmap previous=results.put(preview.getInt("taskId"),bitmap);if(previous!=null)previous.recycle();}}}
   catch(Exception e){if(!closed&&generation==previewGeneration)android.util.Log.w("EdgeTasks","Preview unavailable; using app icons",e);}finally{if(previewProcess==p)previewProcess=null;if(p!=null){p.destroy();try{p.getInputStream().close();}catch(Exception ignored){}}}MAIN.post(()->{if(closed||generation!=previewGeneration){for(android.graphics.Bitmap bitmap:results.values())bitmap.recycle();return;}for(java.util.Map.Entry<Integer,android.graphics.Bitmap> entry:results.entrySet()){ImageView view=previewViews.get(entry.getKey());if(view==null){entry.getValue().recycle();continue;}previewBitmaps.add(entry.getValue());view.setImageBitmap(entry.getValue());view.setScaleType(ImageView.ScaleType.FIT_CENTER);view.setContentDescription("系统任务预览");}});
  });
 }
 private void limit(){if(closed||!children.isEmpty())return;int value=service.getSharedPreferences("edgeflow_launcher",Context.MODE_PRIVATE).getInt("recent_limit",10);java.util.ArrayList<Integer> counts=new java.util.ArrayList<>();for(int n:replica.PanelChoices.taskCounts(value))counts.add(n);String[] names=new String[counts.size()];for(int i=0;i<names.length;i++)names[i]=counts.get(i)+" 个任务";android.app.AlertDialog d=new replica.GlassDialogBuilder(context).setTitle("显示任务数量").setSingleChoiceItems(names,counts.indexOf(value),(dialog,n)->replica.ColorDialogBuilder.dismissThen(dialog,()->{if(closed)return;service.getSharedPreferences("edgeflow_launcher",Context.MODE_PRIVATE).edit().putInt("recent_limit",counts.get(n)).apply();load();})).setNegativeButton("取消",null).create();d.getWindow().setType(WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY);d.getWindow().addFlags(WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE);d.setOnDismissListener(x->children.remove(d));children.add(d);try{d.show();}catch(RuntimeException e){children.remove(d);throw e;}}
 private void closeChildren(){for(Dialog d:new java.util.ArrayList<>(children))try{replica.ColorDialogBuilder.dismissImmediately(d);}catch(Exception ignored){}children.clear();if(recentRequest!=null){recentRequest.cancel();recentRequest=null;}Process preview=previewProcess;if(preview!=null)preview.destroyForcibly();}
 private void closeFromOutside(){if(children.isEmpty())dismiss();}
 private void dismiss(){if(closed)return;closed=true;closeChildren();if(current==this)current=null;if(motion!=null)motion.exit(()->{try{dialog.dismiss();}catch(Exception ignored){}});else dialog.dismiss();}
 private void remove(){closed=true;closeChildren();if(current==this)current=null;if(motion!=null)motion.cancel();try{dialog.dismiss();}catch(Exception ignored){}}
}
