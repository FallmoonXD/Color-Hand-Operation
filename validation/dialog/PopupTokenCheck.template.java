public final class PopupTokenCheck {
 int windowLayoutType;final View anchor=new View();final PopupWindow window=new PopupWindow();final Placement placement=new Placement();
 /* PRODUCTION */
 static int checks;static void check(boolean b,String s){checks++;if(!b)throw new AssertionError(s);}
 public static void main(String[] args)throws Exception{
  PopupTokenCheck app=new PopupTokenCheck();app.showWindow();
  check(app.window.viewCalls==1&&app.window.binderCalls==0,"Activity popup retains public View overload");
  PopupTokenCheck service=new PopupTokenCheck();service.windowLayoutType=2032;WindowManager.LayoutParams params=new WindowManager.LayoutParams();params.token=new Binder();service.anchor.params=params;
  service.showWindow();check(service.window.binder==params.token,"service popup uses registered root layout token");
  check(service.window.binder!=service.anchor.windowBinder&&service.window.viewCalls==0,"service IWindow binder never used as accessibility token");
  check(service.windowLayoutType==2032&&service.window.binderCalls==1,"service window type remains accessibility overlay without app fallback");
  check(service.window.x==34&&service.window.y==56,"screen-coordinate anchor placement preserved");
  params.token=null;try{service.showWindow();throw new AssertionError("missing token must fail");}catch(WindowManager.BadTokenException expected){}
  check(service.window.viewCalls==0&&service.window.binderCalls==1,"missing registration fails rather than showing a focusable app popup");
  service.anchor.params=new ViewGroup.LayoutParams();try{service.showWindow();throw new AssertionError("wrong root must fail");}catch(WindowManager.BadTokenException expected){}
  check(service.window.viewCalls==0,"non-window root cannot substitute anchor binder");
  System.out.println("PASS "+checks+" production popup token routing assertions");
 }
 static class Binder{}
 static class Gravity{static final int TOP=48,LEFT=3;}
 static class ViewGroup{static class LayoutParams{}}
 static class WindowManager{static class LayoutParams extends ViewGroup.LayoutParams{static final int TYPE_ACCESSIBILITY_OVERLAY=2032;Binder token;}static class BadTokenException extends RuntimeException{BadTokenException(String s){super(s);}}}
 static class View{final Binder windowBinder=new Binder();ViewGroup.LayoutParams params=new ViewGroup.LayoutParams();View getRootView(){return this;}ViewGroup.LayoutParams getLayoutParams(){return params;}}
 static class Placement{int x=34,y=56;}
 static class PopupWindow{int viewCalls,binderCalls,x,y;Binder binder;void showAtLocation(View v,int gravity,int x,int y){viewCalls++;this.x=x;this.y=y;}}
 static class HiddenApiBypass{static void invoke(Class<?> type,PopupWindow target,String method,Binder token,int gravity,int x,int y){if(type!=PopupWindow.class||!method.equals("showAtLocation"))throw new AssertionError();target.binderCalls++;target.binder=token;target.x=x;target.y=y;}}
}
