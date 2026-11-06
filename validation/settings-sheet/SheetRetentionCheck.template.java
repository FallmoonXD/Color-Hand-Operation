import java.util.*;
import java.util.function.Consumer;

// Only production methods are inserted below. These doubles model the documented
// Dialog hide/show contract; they do not claim to test Android window focus.
public final class ColorActionSheet {
    Activity owner=new Activity(); Object content=new Object();
    boolean showing,closing,closed,notified,started,suspended,skipEntryAnimation;
    int suspendedAccessibility,builds,entries,exits,colors,layouts,insets,notifications;
    int paletteColor=10,contentColor=10;
    float reveal; Runnable pendingAction,dismissListener,animationDone;boolean deferExit;
    SheetDialog dialog; FakeView root,panel; Animator animator; Drag drag=new Drag();
    FrameLayout embeddedHost;boolean embedded,hostClippedToPadding;
    final IdentityHashMap<View,Integer> obscuredChildren=new IdentityHashMap<>();
    final View.OnLayoutChangeListener embeddedLayout=(v,l,t,r,b,ol,ot,or,ob)->syncEmbeddedLayout();
    ColorOsTheme.Subscription theme; Application.ActivityLifecycleCallbacks lifecycle;
    Map<Object,Object> roles=new HashMap<>();List<Object> editors=new ArrayList<>();
    final Runnable updateGeometry=()->layoutSheet();
    /* PRODUCTION_ENTRY */
    /* PRODUCTION_METHODS */
    void refreshColors(){colors++;if(Integer.valueOf(1).equals(roles.get(content)))contentColor=paletteColor;} void layoutSheet(){layouts++;}
    void captureRoles(Object value){roles.put(value,contentColor==paletteColor?1:0);}
    void build(){builds++;root=new FakeView();panel=new FakeView();}
    void refreshOwnerInsets(){insets++;} void pose(float value){reveal=value;}
    void animateTo(float target,Runnable done){if(target==1)entries++;else exits++;pose(target);animator=new Animator();if(done!=null){if(deferExit)animationDone=done;else done.run();}}
    static class View {
        static final int VISIBLE=0,INVISIBLE=4,IMPORTANT_FOR_ACCESSIBILITY_NO_HIDE_DESCENDANTS=4;
        interface OnLayoutChangeListener{void onLayoutChange(View v,int l,int t,int r,int b,int ol,int ot,int or,int ob);}
        int accessibility;int getImportantForAccessibility(){return accessibility;}void setImportantForAccessibility(int value){accessibility=value;}
    }
    static final class ViewGroup {static final int FOCUS_BLOCK_DESCENDANTS=1;}
    static final class FrameLayout extends FakeView {
        static final class LayoutParams {int height=400,leftMargin,topMargin,rightMargin,bottomMargin;LayoutParams(){}LayoutParams(int w,int h){height=h;}void setMargins(int l,int t,int r,int b){leftMargin=l;topMargin=t;rightMargin=r;bottomMargin=b;}}
        final List<View> children=new ArrayList<>();int paddingLeft,paddingTop,paddingRight,paddingBottom,listeners;boolean clipped=true;
        boolean getClipToPadding(){return clipped;}void setClipToPadding(boolean value){clipped=value;}
        int getPaddingLeft(){return paddingLeft;}int getPaddingTop(){return paddingTop;}int getPaddingRight(){return paddingRight;}int getPaddingBottom(){return paddingBottom;}
        int getChildCount(){return children.size();}View getChildAt(int index){return children.get(index);}
        void addView(FakeView child,LayoutParams params){children.add(child);child.parent=this;child.params=params;child.attached=attached;}
        void removeView(FakeView child){children.remove(child);child.parent=null;child.attached=false;}
        void addOnLayoutChangeListener(View.OnLayoutChangeListener listener){listeners++;}void removeOnLayoutChangeListener(View.OnLayoutChangeListener listener){listeners--;}
    }
    static final class WindowManager {static final class BadTokenException extends RuntimeException {}}
    static final class Bundle {}
    static class FakeView extends View {
        boolean attached=true;int scrollY=731,visibility,requests,inputCancels,width=400,height=400;FrameLayout parent;
        FrameLayout.LayoutParams params=new FrameLayout.LayoutParams();
        final List<Runnable> queue=new ArrayList<>();
        boolean isAttachedToWindow(){return attached;}int getWidth(){return width;}int getHeight(){return height;}
        boolean isShown(){return attached&&visibility==View.VISIBLE;}FrameLayout getParent(){return parent;}
        void setLayoutParams(FrameLayout.LayoutParams params){this.params=params;}
        Object getLayoutParams(){return params;}void setVisibility(int value){visibility=value;}
        int getImportantForAccessibility(){return accessibility;}
        void setImportantForAccessibility(int value){accessibility=value;}
        void setDescendantFocusability(int value){}void cancelPendingInputEvents(){inputCancels++;}
        void post(Runnable r){queue.add(r);}void postOnAnimation(Runnable r){queue.add(r);}
        void removeCallbacks(Runnable r){queue.removeIf(item->item==r);}
        void requestApplyInsets(){requests++;}
        void drain(){List<Runnable> pending=new ArrayList<>(queue);queue.clear();for(Runnable r:pending)r.run();}
    }
    static final class Animator {
        boolean cancelled,removedUpdates,removedListeners;
        void removeAllUpdateListeners(){removedUpdates=true;}void removeAllListeners(){removedListeners=true;}
        void cancel(){if(!removedListeners)throw new AssertionError("cancel could dispatch an old completion");cancelled=true;}
    }
    static final class Drag {int clears;void clear(){clears++;}}
    static final class Activity {
        boolean destroyed,finishing,failShow;final Application app=new Application();
        boolean isFinishing(){return finishing;}boolean isDestroyed(){return destroyed;}Application getApplication(){return app;}
    }
    static final class Application {
        ActivityLifecycleCallbacks callbacks;int unregisters;
        interface ActivityLifecycleCallbacks {
            void onActivityCreated(Activity a,Bundle b);void onActivityStarted(Activity a);void onActivityResumed(Activity a);
            void onActivityPaused(Activity a);void onActivityStopped(Activity a);void onActivitySaveInstanceState(Activity a,Bundle b);void onActivityDestroyed(Activity a);
        }
        void registerActivityLifecycleCallbacks(ActivityLifecycleCallbacks c){callbacks=c;}
        void unregisterActivityLifecycleCallbacks(ActivityLifecycleCallbacks c){if(callbacks==c)callbacks=null;unregisters++;}
    }
    static final class ColorOsTheme {
        static final class Subscription {boolean closed;void close(){closed=true;}}
        static Subscription observe(Activity owner,Runnable action){return new Subscription();}
    }
    final class SheetDialog {
        boolean created,visible,removed;int shows,hides,removes;Consumer<Object> dismiss;
        void setOwnerActivity(Activity a){}void setCancelable(boolean value){}void setOnDismissListener(Consumer<Object> cb){dismiss=cb;}
        void show(){if(owner.failShow)throw new WindowManager.BadTokenException();shows++;if(!created){created=true;builds++;root=new FakeView();panel=new FakeView();}visible=true;}
        boolean isShowing(){return visible;}void hide(){hides++;visible=false;}
        void removeNow(){if(removed)return;removed=true;removes++;visible=false;if(root!=null)root.attached=false;if(dismiss!=null)dismiss.accept(null);}
    }
    private static int checks;
    private static void check(boolean condition,String message){checks++;if(!condition)throw new AssertionError(message);}
    private static ColorActionSheet visible(){ColorActionSheet s=new ColorActionSheet();s.dismissListener=()->s.notifications++;s.show();s.root.drain();return s;}
    public static void main(String[] args){
        ColorActionSheet first=new ColorActionSheet();first.show();
        first.root.drain();check(first.entries==1&&first.started,"ordinary first presentation still animates");
        ColorActionSheet early=new ColorActionSheet();early.showRestored();
        early.root.attached=false;early.root.width=0;early.root.height=0;early.panel.height=0;
        FakeView earlyRoot=early.root;SheetDialog earlyDialog=early.dialog;
        check(early.suspend()&&early.root.queue.isEmpty(),"shown but unattached first-frame window can be retained");
        early.lifecycle.onActivityPaused(early.owner);early.lifecycle.onActivityStopped(early.owner);
        check(early.isSuspended()&&!early.closed,"first-frame pause and stop do not discard the retained window");
        early.resume();
        check(!early.closed&&early.dialog==earlyDialog&&early.root==earlyRoot&&early.builds==1,"pre-attachment resume keeps the same window instead of destroying it");
        check(early.panel.visibility==View.INVISIBLE&&early.root.queue.contains(early.enterWhenLaidOut),"pre-layout resume waits for valid measured geometry");
        check(early.suspend()&&early.root.queue.isEmpty(),"another immediate pause cancels queued restoration and keeps state");
        early.resume();early.root.attached=true;early.root.width=400;early.root.height=400;early.panel.height=400;early.root.drain();
        check(early.panel.visibility==View.VISIBLE&&early.reveal==1&&early.entries==0,"first measured restoration appears without replaying entry");
        check(early.root.scrollY==731&&early.builds==1,"rapid first-frame cycles preserve scroll and object identity");
        ColorActionSheet a=visible();FakeView original=a.root;SheetDialog originalDialog=a.dialog;Animator entry=a.animator;
        a.pendingAction=()->{throw new AssertionError("stale selection");};a.root.post(a.enterWhenLaidOut);a.root.post(a.updateGeometry);
        check(a.suspend(),"visible sheet can be suspended");
        check(!a.isShowing()&&a.isSuspended()&&!a.dialog.isShowing(),"hidden decor is not showing but remains suspended");
        check(a.root==original&&a.dialog==originalDialog&&a.root.scrollY==731,"view/window identity and scroll survive hide");
        check(a.reveal==1&&entry.cancelled&&entry.removedUpdates&&a.animator==null,"entry/drag motion stops at the final pose");
        check(a.pendingAction==null&&a.root.queue.isEmpty()&&a.root.inputCancels==1,"queued entry/geometry/input and stale selection are cancelled");
        check(a.root.accessibility==4,"hidden content cannot remain an accessibility target");
        int entries=a.entries;a.enterWhenLaidOut.run();check(a.entries==entries,"late entry runnable cannot animate a hidden sheet");
        check(!a.suspend(),"repeat suspend cannot overwrite retained state");
        Application.ActivityLifecycleCallbacks callbacks=a.lifecycle;
        callbacks.onActivityPaused(a.owner);callbacks.onActivityStopped(a.owner);callbacks.onActivityResumed(a.owner);
        check(a.isSuspended()&&a.notifications==0&&a.dialog.hides==1,"pause/stop/resume retain without automatic showing");
        a.resume();
        check(a.isShowing()&&!a.isSuspended()&&a.dialog.isShowing(),"explicit resume reveals the retained window");
        check(a.builds==1&&a.dialog.shows==2&&a.root==original&&a.root.scrollY==731,"resume neither rebuilds nor changes scrolling");
        check(a.entries==entries&&a.reveal==1&&a.insetCount()==1&&a.root.requests==1,"resume refreshes geometry/insets without entry animation");
        check(a.root.accessibility==0,"resume restores the original accessibility policy");
        a.resume();check(a.dialog.shows==2,"resume is idempotent");
        a.suspend();ColorOsTheme.Subscription subscription=a.theme;a.dismiss();
        check(a.closed&&a.dialog.removes==1&&a.notifications==1&&a.exits==0,"hidden dismiss removes its window without animating");
        check(subscription.closed&&a.owner.app.callbacks==null,"hidden close releases theme and lifecycle subscriptions");
        a.dismissImmediately();a.resume();check(a.notifications==1&&a.dialog.removes==1&&!a.isSuspended(),"closed sheet cannot revive or notify twice");
        ColorActionSheet b=visible();b.suspend();b.owner.destroyed=true;b.lifecycle.onActivityDestroyed(b.owner);
        check(b.closed&&b.dialog.removes==1&&b.notifications==1,"destroy always removes a suspended window");
        ColorActionSheet c=visible();c.lifecycle.onActivityPaused(c.owner);
        check(c.closed&&c.notifications==1,"ordinary visible pause still closes");
        ColorActionSheet d=visible();d.suspend();int[] delivered={0};d.dismissThen(()->delivered[0]++);
        check(d.closed&&delivered[0]==0&&d.exits==0,"hidden dismissThen cannot dispatch an obsolete navigation");
        ColorActionSheet e=visible();e.dismissThen(()->{check(e.dialog.removed&&e.notifications==1,"selection follows window removal and onDismiss");delivered[0]++;});
        check(delivered[0]==1&&e.exits==1,"ordinary selection still exits and dispatches once");
        ColorActionSheet f=visible();f.suspend();f.owner.finishing=true;f.resume();
        check(f.closed&&f.dialog.removed,"resume with unavailable owner cleans up instead");
        ColorActionSheet g=visible();g.suspend();g.owner.failShow=true;g.resume();
        check(g.closed&&g.dialog.removed,"failed retained-window show also cleans up");
        ColorActionSheet h=visible();h.closing=true;check(!h.suspend(),"an exit in progress is not converted into suspension");
        ColorActionSheet i=new ColorActionSheet();check(!i.suspend(),"a sheet that was never shown is not retainable");
        ColorActionSheet restored=new ColorActionSheet();restored.showRestored();restored.root.drain();
        check(restored.started&&restored.reveal==1&&restored.entries==0,"new-Activity restoration waits for layout but skips entry");
        check(restored.suspend(),"restored sheet remains retainable on later navigation");
        ColorActionSheet themed=visible();themed.roles.put(themed.content,1);themed.suspend();
        themed.paletteColor=20;themed.refreshContent();
        check(themed.contentColor==20&&Integer.valueOf(1).equals(themed.roles.get(themed.content)),"host-first retained refresh preserves a semantic theme role");
        themed.paletteColor=30;themed.refreshContent();
        check(themed.contentColor==30&&Integer.valueOf(1).equals(themed.roles.get(themed.content)),"semantic role survives repeated palette changes");
        themed.roles.put(themed.content,0);themed.contentColor=99;themed.paletteColor=40;themed.refreshContent();
        check(themed.contentColor==99&&Integer.valueOf(0).equals(themed.roles.get(themed.content)),"explicit fixed colors remain fixed while refreshing roles");
        FrameLayout host=new FrameLayout();host.paddingLeft=3;host.paddingTop=25;host.paddingRight=4;host.paddingBottom=16;
        FakeView underPage=new FakeView(),underToolbar=new FakeView();underPage.accessibility=2;underToolbar.accessibility=1;
        host.addView(underPage,new FrameLayout.LayoutParams(-1,-1));host.addView(underToolbar,new FrameLayout.LayoutParams(-1,-1));
        ColorActionSheet mounted=new ColorActionSheet();mounted.dismissListener=()->mounted.notifications++;mounted.showEmbedded(host);mounted.root.drain();
        check(mounted.dialog==null&&mounted.root.parent==host&&mounted.isShowing(),"embedded chooser uses the real page tree without a Dialog window");
        check(mounted.root.params.leftMargin==-3&&mounted.root.params.topMargin==-25&&mounted.root.params.rightMargin==-4&&mounted.root.params.bottomMargin==-16&&!host.clipped,"embedded overlay spans host padding without double insets");
        check(underPage.accessibility==4&&underToolbar.accessibility==4,"visible modal chooser hides underlying content and toolbar from accessibility");
        mounted.lifecycle.onActivityPaused(mounted.owner);mounted.lifecycle.onActivityStopped(mounted.owner);
        check(!mounted.closed&&mounted.isShowing()&&mounted.entries==1,"widget or background pause preserves an embedded visible chooser");
        mounted.suspend();check(mounted.root.visibility==View.VISIBLE&&mounted.root.parent==host&&!mounted.isShowing(),"configuration entry blocks interactions but retains the real painted chooser underneath");
        mounted.root.attached=false;mounted.resume();
        check(mounted.panel.visibility==View.VISIBLE,"measured retained embedded panel paints before its first reattachment frame");
        mounted.root.attached=true;mounted.root.drain();
        check(mounted.isShowing()&&mounted.entries==1&&mounted.root.scrollY==731,"reattaching a retained page resumes without rebuilding or replaying entry");
        host.paddingTop=31;mounted.embeddedLayout.onLayoutChange(host,0,0,0,0,0,0,0,0);
        check(mounted.root.params.topMargin==-31,"embedded bounds follow updated host inset padding");
        mounted.dismissImmediately();
        check(host.children.size()==2&&underPage.accessibility==2&&underToolbar.accessibility==1&&host.clipped,"dismissal restores underlying accessibility and clipping");
        check(host.listeners==0&&mounted.notifications==1,"embedded dismissal releases host listener and notifies once");
        ColorActionSheet destroyedEmbedded=new ColorActionSheet();destroyedEmbedded.showEmbeddedRestored(new FrameLayout());destroyedEmbedded.root.drain();
        check(destroyedEmbedded.entries==0&&destroyedEmbedded.reveal==1,"recreated embedded chooser can appear at its final pose");
        destroyedEmbedded.lifecycle.onActivityDestroyed(destroyedEmbedded.owner);
        check(destroyedEmbedded.closed&&destroyedEmbedded.root.parent==null,"Activity destruction unmounts an embedded chooser");
        ColorActionSheet leaving=new ColorActionSheet();leaving.showEmbedded(new FrameLayout());leaving.root.drain();leaving.deferExit=true;int[] staleSelection={0};
        leaving.dismissThen(()->staleSelection[0]++);Runnable lateEnd=leaving.animationDone;
        check(leaving.closing&&leaving.pendingAction!=null,"embedded selection remains pending during its exit animation");
        leaving.lifecycle.onActivityPaused(leaving.owner);lateEnd.run();
        check(leaving.closed&&leaving.root.parent==null&&staleSelection[0]==0,"pausing during embedded exit cancels selection and unmounts without a late commit");
        System.out.println("PASS "+checks+" production sheet retention assertions");
    }
    private int insetCount(){return insets;}
}
