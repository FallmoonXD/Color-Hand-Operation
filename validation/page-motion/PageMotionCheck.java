package replica;

import java.util.ArrayList;
import java.util.List;

/** Real ColorPageMotion bodies, with only Android frame/animator boundaries replaced. */
public final class PageMotionCheck {
 private static int checks;
 private static void check(boolean value,String message){if(!value)throw new AssertionError(message);checks++;}
 private static final class Scenario {
  final ColorPageMotion motion=new ColorPageMotion();final FrameLayout host=new FrameLayout(),first=new FrameLayout(),second=new FrameLayout();
  Scenario(){motion.show(host,first,0);}
  void begin(){motion.show(host,second,-1);}
  ValueAnimator start(){second.drain();return ValueAnimator.last;}
 }
 public static void main(String[] args){
  int[] calls={0};ColorPageMotion empty=new ColorPageMotion();empty.whenSettled(()->calls[0]++);check(calls[0]==1,"idle callback is immediate");empty.whenSettled(null);
  Scenario s=new Scenario();s.motion.whenSettled(()->calls[0]++);check(calls[0]==2,"initial page has no pending animation");s.begin();final Scenario first=s;
  s.motion.whenSettled(()->{check(first.host.children.size()==1&&first.host.children.get(0)==first.second,"callback runs after outgoing page removal");check(first.second.getTranslationX()==0&&first.second.getScaleX()==1&&first.second.getAlpha()==1,"callback runs after incoming page reset");calls[0]++;});
  check(calls[0]==2,"posted but not started transition still defers callback");ValueAnimator animation=s.start();check(calls[0]==2,"started transition still defers callback");animation.frame(.7f);check(calls[0]==2,"intermediate frame does not dispatch completion");animation.end();check(calls[0]==3,"normal completion dispatches once");animation.end();check(calls[0]==3,"duplicate end notification cannot duplicate callback");

  s=new Scenario();s.begin();int[] replacements={0};s.motion.whenSettled(()->replacements[0]+=100);s.motion.whenSettled(()->replacements[0]++);s.start().end();check(replacements[0]==1,"latest completion replaces the previous request");
  s=new Scenario();s.begin();s.motion.whenSettled(()->replacements[0]+=100);s.motion.whenSettled(null);s.start().end();check(replacements[0]==1,"null cancels a pending request without cancelling the page");

  s=new Scenario();s.begin();s.motion.whenSettled(()->calls[0]+=100);int created=ValueAnimator.created;s.motion.cancel();s.second.drain();check(calls[0]==3&&ValueAnimator.created==created,"cancel before posted startup invalidates callback and stale frame task");
  s=new Scenario();s.begin();s.motion.whenSettled(()->calls[0]+=100);animation=s.start();s.motion.cancel();check(calls[0]==3&&animation.cancelled,"cancel-triggered animator end cannot dispatch completion");animation.end();check(calls[0]==3,"late end after cancellation remains inert");
  s=new Scenario();s.begin();s.motion.whenSettled(()->calls[0]+=100);s.motion.clear();s.second.drain();check(calls[0]==3,"clear discards a callback while transition startup is queued");s.motion.whenSettled(()->calls[0]++);check(calls[0]==4,"clear returns the controller to settled state");
  s=new Scenario();s.begin();s.motion.whenSettled(()->calls[0]+=100);animation=s.start();s.motion.clear();animation.end();check(calls[0]==4,"clear also discards a running transition callback");

  s=new Scenario();s.begin();s.motion.whenSettled(()->calls[0]+=100);FrameLayout third=new FrameLayout();s.motion.show(s.host,third,1);s.motion.whenSettled(()->calls[0]++);created=ValueAnimator.created;s.second.drain();check(ValueAnimator.created==created,"next navigation invalidates old posted animation");third.drain();ValueAnimator.last.end();check(calls[0]==5,"only the newest navigation completion runs");
  s=new Scenario();s.begin();s.motion.whenSettled(()->calls[0]+=100);animation=s.start();s.motion.show(s.host,new FrameLayout(),0);animation.end();check(calls[0]==5,"nonanimated replacement also discards the former callback");

  ValueAnimator.enabled=false;s=new Scenario();s.begin();s.motion.whenSettled(()->calls[0]++);check(calls[0]==6&&s.host.children.size()==1,"disabled system animations settle immediately");ValueAnimator.enabled=true;
  final Scenario chained=new Scenario();final FrameLayout chainedPage=new FrameLayout();int[] chainedCalls={0};chained.begin();chained.motion.whenSettled(()->{chainedCalls[0]++;chained.motion.show(chained.host,chainedPage,1);chained.motion.whenSettled(()->chainedCalls[0]++);});animation=chained.start();animation.end();chainedPage.drain();ValueAnimator secondAnimation=ValueAnimator.last;animation.end();check(chainedCalls[0]==1,"old end cannot settle a reentrant new navigation");secondAnimation.end();check(chainedCalls[0]==2,"completion can safely schedule another transition and completion");
  Scenario retained=new Scenario();retained.first.setDescendantFocusability(262144);retained.first.setFocusable(true);View chooser=new View();retained.first.addView(chooser,new FrameLayout.LayoutParams(-1,-1));
  retained.motion.show(retained.host,retained.second,1);retained.second.drain();ValueAnimator.last.end();
  check(retained.first.children.size()==2&&retained.first.getDescendantFocusability()==ViewGroup.FOCUS_BLOCK_DESCENDANTS,"outgoing retained page keeps its chooser plus an input blocker");
  retained.motion.show(retained.host,retained.first,-1);
  check(retained.first.children.size()==1&&retained.first.children.get(0)==chooser,"return removes only the blocker and preserves the exact chooser tree");
  check(retained.first.getDescendantFocusability()==262144&&retained.first.isFocusable(),"return restores the retained page's focus policy before animation");
  check(retained.first.getImportantForAccessibility()==View.IMPORTANT_FOR_ACCESSIBILITY_AUTO,"returned page becomes accessible before the old page leaves");
  retained.first.drain();ValueAnimator.last.end();retained.motion.progress(.6f,0);retained.motion.cancel();
  check(retained.first.children.size()==1&&retained.first.getTranslationX()==0&&retained.first.getScaleX()==1,"predictive return cancellation leaves retained content interactive and reset");
  retained.motion.show(retained.host,retained.second,1);retained.motion.cancel();retained.motion.show(retained.host,retained.first,0);
  check(retained.first.children.size()==1&&retained.first.getDescendantFocusability()==262144,"rapid interrupted navigation cannot accumulate blockers on reused pages");
  System.out.println("PASS "+checks+" page-motion completion assertions");
 }
}

// Boundary models contain no navigation decisions: they expose queued frames and end/cancel events.
interface Interpolator {float getInterpolation(float value);}
class PathInterpolator implements Interpolator {PathInterpolator(float a,float b,float c,float d){}public float getInterpolation(float value){return value;}}
class Animator {}
class AnimatorListenerAdapter {public void onAnimationEnd(Animator animator){}}
class ValueAnimator extends Animator {
 interface UpdateListener {void update(ValueAnimator animator);}
 static boolean enabled=true;static int created;static ValueAnimator last;boolean cancelled;float value;
 final List<UpdateListener> updates=new ArrayList<>();final List<AnimatorListenerAdapter> listeners=new ArrayList<>();
 static boolean areAnimatorsEnabled(){return enabled;}static ValueAnimator ofFloat(float... values){created++;last=new ValueAnimator();return last;}
 void setDuration(long value){}void setInterpolator(Interpolator interpolator){}void addUpdateListener(UpdateListener listener){updates.add(listener);}void addListener(AnimatorListenerAdapter listener){listeners.add(listener);}void start(){}
 Object getAnimatedValue(){return value;}void frame(float fraction){value=fraction;for(UpdateListener listener:updates)listener.update(this);}
 void end(){for(AnimatorListenerAdapter listener:new ArrayList<>(listeners))listener.onAnimationEnd(this);}void cancel(){cancelled=true;end();}
}
class View {
 static final int LAYOUT_DIRECTION_RTL=1,IMPORTANT_FOR_ACCESSIBILITY_NO_HIDE_DESCENDANTS=4,IMPORTANT_FOR_ACCESSIBILITY_NO=2,IMPORTANT_FOR_ACCESSIBILITY_AUTO=0;
 interface InputListener {boolean onInput(View view,Object event);}
 private float x,y,scaleX=1,scaleY=1,alpha=1;private int accessibility;private boolean focusable;final List<Runnable> posted=new ArrayList<>();
 View(){}View(Object context){}Object getContext(){return null;}int getWidth(){return 1000;}int getHeight(){return 2000;}int getLayoutDirection(){return 0;}
 float getTranslationX(){return x;}void setTranslationX(float value){x=value;}void setTranslationY(float value){y=value;}float getScaleX(){return scaleX;}void setScaleX(float value){scaleX=value;}void setScaleY(float value){scaleY=value;}float getAlpha(){return alpha;}void setAlpha(float value){alpha=value;}
 void setPivotX(float value){}void setPivotY(float value){}void setImportantForAccessibility(int value){accessibility=value;}int getImportantForAccessibility(){return accessibility;}void clearFocus(){}void setFocusable(boolean value){focusable=value;}boolean isFocusable(){return focusable;}void setClickable(boolean value){}void bringToFront(){}void setOnTouchListener(InputListener listener){}void setOnGenericMotionListener(InputListener listener){}
 boolean post(Runnable task){posted.add(task);return true;}void drain(){List<Runnable> tasks=new ArrayList<>(posted);posted.clear();for(Runnable task:tasks)task.run();}ViewAnimation animate(){return new ViewAnimation();}
}
class ViewAnimation {ViewAnimation translationX(float value){return this;}ViewAnimation scaleX(float value){return this;}ViewAnimation scaleY(float value){return this;}ViewAnimation setDuration(long value){return this;}ViewAnimation setInterpolator(Interpolator value){return this;}void start(){}void cancel(){}}
class ViewGroup extends View {static final int FOCUS_BLOCK_DESCENDANTS=393216;private int focusability;void setDescendantFocusability(int value){focusability=value;}int getDescendantFocusability(){return focusability;}}
class FrameLayout extends ViewGroup {
 static class LayoutParams {LayoutParams(int width,int height){}}
 final List<View> children=new ArrayList<>();void removeAllViews(){children.clear();}void addView(View child,LayoutParams params){children.add(child);}void removeView(View child){children.remove(child);}
}
