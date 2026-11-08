package dev.edgeflow;
import java.util.ArrayList;
import java.util.HashMap;
public final class WindowUpdateCheck {
    static int checks;
    static void check(boolean value,String label){checks++;if(!value)throw new AssertionError(label);}
    static void change(GestureService s,String key,Object value){s.prefs.put(key,value);s.onSharedPreferenceChanged(s.prefs,key);}
    public static void main(String[] args){
        GestureService s=new GestureService();s.boot();WindowManager wm=s.windows;
        check(wm.adds==8&&wm.removes==0,"connection creates two narrow wave windows and six input windows");
        for(int i=0;i<6;i++){EdgeHandleView handle=(EdgeHandleView)wm.order.get(i+2);check(handle.id.equals(GestureHandleConfig.IDS[i]),"stable primary/extra/extra2 layer order "+i);check(handle.getVisibility()==(i<2?View.VISIBLE:View.GONE),"disabled extras start GONE before add "+i);}
        EdgeHandleView left=s.left,right=s.right;GestureWaveView wave=s.leftWave;int roots=s.rootReads;wm.resetCounts();
        change(s,"left_enabled",false);s.queue();check(s.main.tasks.size()==1,"explicit reload and preference callback share one queued update");s.main.flush();
        check(s.left==left&&s.right==right&&s.leftWave==wave,"toggle retains input and wave View identities");
        check(wm.adds==0&&wm.removes==0&&wm.updates==0,"toggle does not add/remove/relayout windows");
        check(left.getVisibility()==View.GONE&&left.cancels==1,"disabling handle hides and cancels only that gesture");
        check(s.rootReads==roots,"ordinary enable toggle does not query accessibility roots");
        change(s,"enabled",false);s.main.flush();check(right.getVisibility()==View.GONE,"master disable hides enabled handles");
        check(s.panelDismissals==1&&s.handleAt(right.params.x+1,right.params.y+1)==null,"master disable clears tools and removes edge hit targets");
        change(s,"enabled",true);s.main.flush();check(right.getVisibility()==View.VISIBLE&&left.getVisibility()==View.GONE,"master reenable preserves individual disabled state");
        change(s,"extra2_right_enabled",true);s.main.flush();check(s.extra2Right.getVisibility()==View.VISIBLE&&wm.order.get(7)==s.extra2Right,"enabling extra2 preserves topmost input identity");
        check(wm.adds==0&&wm.removes==0,"all enable changes reuse existing windows");
        check(s.handleAt(s.extra2Right.params.x+1,s.extra2Right.params.y+1)==s.extra2Right,"outside swipe selects the topmost visible handle");
        check(s.handleAt(630,1200)==null,"ordinary outside click is not a handle contact");

        change(s,"left_handle_start_fraction",.2f);change(s,"left_handle_end_fraction",.5f);s.main.flush();
        check(wm.updates==1&&s.left==left&&s.leftWave==wave,"geometry updates only changed layout and keeps window identity");
        check(left.params.y==140+Math.round(.2f*2486)&&left.params.height==Math.round(.3f*2486),"geometry update uses new physical bounds");
        wm.resetCounts();change(s,"left_handle_start_fraction",.2f);s.main.flush();check(wm.updates==0,"identical geometry does not issue WindowManager update");
        int leftPaint=left.paints,rightPaint=right.paints;change(s,"left_color",0xff123456);s.main.flush();
        check(left.paints==leftPaint+1&&right.paints==rightPaint,"color refresh changes only affected appearance");
        check(wm.updates==0&&wm.adds==0&&wm.removes==0,"color changes do not replace or relayout windows");
        change(s,"long_delay_ms",800);s.queue();s.main.flush();check(s.rootReads==roots&&wm.updates==0,"recognizer parameter edit performs no root query or window mutation");
        change(s,"hide_home",true);change(s,"hide_keyboard",true);s.queue();s.main.flush();check(s.rootReads==roots+1,"visibility policy changes coalesce into one necessary state refresh");

        wm.resetCounts();s.configure();s.main.flush();check(wm.removes==8&&wm.adds==8&&s.left!=left&&s.leftWave!=wave,"real configuration change still rebuilds display-dependent windows");
        check(s.rootReads==roots+2,"configuration rebuild refreshes accessibility state once");
        View preview=new View();wm.addView(preview,new WindowManager.LayoutParams(1,1,2032,16,-3));s.handlePreviews.put("left",preview);s.handlePreviewRegions.put("left",new float[]{12.3f,.2f,.5f});
        EdgeHandleView kept=s.left;GestureWaveView keptWave=s.leftWave;int beforeRoots=s.rootReads;wm.resetCounts();
        HandleDisplayArea.value=GestureHandleConfig.safeArea(1264,2780,0,150,0,50);s.pendingGeometry=true;s.queue();s.main.flush();
        check(s.left==kept&&s.leftWave==keptWave&&wm.adds==0&&wm.removes==0,"inset changes retain all real and preview window identities");
        check(preview.params.x==s.left.params.x&&preview.params.y==s.left.params.y&&preview.params.width==s.left.params.width&&preview.params.height==s.left.params.height,"global preview equals actual touch region after safe-area change");
        check(s.rootReads==beforeRoots,"safe geometry update does not fetch accessibility roots");
        wm.resetCounts();s.pendingGeometry=true;s.queue();s.main.flush();check(wm.updates==0,"hidden bar or IME event with unchanged stable insets causes no layout jump");
        HandleDisplayArea.value=GestureHandleConfig.safeArea(2780,1264,140,0,154,0);s.pendingGeometry=true;s.queue();s.main.flush();
        check(s.screenWidth==2780&&s.screenHeight==1264&&s.left.params.x==140&&s.right.params.x+s.right.params.width==2626,"rotation uses current landscape cutout/navigation safe edges");
        check(s.handlePreviews.get("left")==preview&&preview.params.y==s.left.params.y&&preview.params.height==s.left.params.height,"rotation retains owned preview and updates it to the same new frame");
        check(s.leftWave.params.x==140&&s.rightWave.params.x+s.rightWave.params.width==2626,"wave drawing edges remain aligned with safe input edges");
        s.allowed=false;s.visibility();check(s.right.getVisibility()==View.GONE&&s.extra2Right.getVisibility()==View.GONE,"screen/lock/global policy hides active handles");
        wm.resetCounts();s.connected=false;s.queue();s.main.flush();check(wm.adds==0&&wm.removes==0&&wm.updates==0,"queued work after disconnect cannot recreate windows");
        System.out.println("PASS "+checks+" production window update assertions");
    }
}
class NavigationOverlay {static void displayChanged(GestureHandleConfig.Area area,int dpi){}}
class TouchpadOverlay {static void displayChanged(GestureHandleConfig.Area area,int dpi){}}
class MagnificationOverlay {static void displayChanged(GestureHandleConfig.Area area){}}
class SharedPreferences extends HashMap<String,Object> {
    boolean getBoolean(String key,boolean fallback){Object value=get(key);return value==null?fallback:(Boolean)value;}
    float getFloat(String key,float fallback){Object value=get(key);return value==null?fallback:(Float)value;}
    int getInt(String key,int fallback){Object value=get(key);return value==null?fallback:(Integer)value;}
    boolean contains(String key){return containsKey(key);}
}
class Looper {static final Looper main=new Looper();static Looper myLooper(){return main;}}
class Handler {final ArrayList<Runnable> tasks=new ArrayList<>();Looper getLooper(){return Looper.main;}void removeCallbacks(Runnable task){tasks.remove(task);}void post(Runnable task){tasks.add(task);}void postDelayed(Runnable task,long delay){tasks.add(task);}void flush(){ArrayList<Runnable> copy=new ArrayList<>(tasks);tasks.clear();for(Runnable task:copy)task.run();}}
class View {static final int VISIBLE=0,GONE=8;int visibility=VISIBLE;WindowManager.LayoutParams params;int getVisibility(){return visibility;}boolean isShown(){return visibility==VISIBLE&&params!=null;}void setVisibility(int value){visibility=value;}Object getLayoutParams(){return params;}}
class EdgeHandleView extends View {
    final GestureService service;final String id;int cancels,paints,lastColor;
    EdgeHandleView(GestureService service,String id){this.service=service;this.id=id;refreshAppearance();}
    void cancel(){cancels++;}void refreshAppearance(){int color=service.prefs.getInt(id+"_color",0xff188dff);if(lastColor!=color){lastColor=color;paints++;}}
}
class GestureWaveView extends View {interface Listener {Object apply(View v,Object insets);}GestureWaveView(GestureService service){}void clear(){}void setOnApplyWindowInsetsListener(Listener listener){}}
class HandleDisplayArea {static GestureHandleConfig.Area value=GestureHandleConfig.safeArea(1264,2780,0,140,0,154);static GestureHandleConfig.Area current(GestureService service){return value;}}
class Resources {static class Metrics {float density=3.5f;int densityDpi=560;}Metrics getDisplayMetrics(){return new Metrics();}}
class Point {int x,y;}
class WindowManager {
    static class LayoutParams {
        static final int TYPE_ACCESSIBILITY_OVERLAY=2032,FLAG_NOT_FOCUSABLE=8,FLAG_NOT_TOUCH_MODAL=32,FLAG_LAYOUT_IN_SCREEN=256,FLAG_NOT_TOUCHABLE=16,LAYOUT_IN_DISPLAY_CUTOUT_MODE_ALWAYS=3;
        int x,y,width,height,flags,gravity,layoutInDisplayCutoutMode,windowAnimations;float alpha;String title;
        LayoutParams(){}LayoutParams(int width,int height,int type,int flags,int format){this.width=width;this.height=height;this.flags=flags;}
        void setTitle(String title){this.title=title;}void setFitInsetsTypes(int types){}void copyFrom(LayoutParams other){x=other.x;y=other.y;width=other.width;height=other.height;title=other.title;flags=other.flags;}
    }
    static class Display {void getRealSize(Point size){size.x=1264;size.y=2780;}}
    final ArrayList<View> order=new ArrayList<>();int adds,removes,updates;
    Display getDefaultDisplay(){return new Display();}void addView(View view,LayoutParams params){adds++;order.add(view);view.params=params;}
    void updateViewLayout(View view,LayoutParams params){updates++;view.params=params;}void removeViewImmediate(View view){removes++;order.remove(view);}void resetCounts(){adds=removes=updates=0;}
}
class Gravity {static final int TOP=48,LEFT=3;}
class PixelFormat {static final int TRANSLUCENT=-3;}
class Log {static void e(String tag,String message,Exception error){throw new AssertionError(message,error);}static void w(String tag,String message,Exception error){throw new AssertionError(message,error);}}
class R {static final class string {static final int app_name=1;}}
