import java.util.*;
public final class DialogBodyCheck {
 final IdentityHashMap<View,int[]> flexible=new IdentityHashMap<>();
 View viewport=new View(),body=new View();ListView list;
 Object getContext(){return this;}int dp(Object c,float v){return Math.round(v);}
 /* PRODUCTION */
 static int checks;static void check(boolean b,String s){checks++;if(!b)throw new AssertionError(s);}
 public static void main(String[] args){
  DialogBodyCheck c=new DialogBodyCheck();LinearLayout column=new LinearLayout();View field=new View();field.params.height=-2;View rows=new View();rows.params.height=0;rows.params.weight=1;rows.params.topMargin=8;column.children.add(field);column.children.add(rows);c.body=column;c.flexible.put(rows,new int[]{380,Float.floatToIntBits(0)});
  check(c.bodyHeight(360,240)==438,"preferred size counts search, list natural height and margin");
  check(rows.params.height==0&&rows.params.weight==1,"size probe never changes live weighted layout params");
  check(rows.measures==0&&column.measures==0,"natural size does not measure the live ListView or its parent");
  check(field.measures==1,"only normal search label requires natural measurement");
  check(!rows.forced&&!column.forced,"no artificial pending layout prevents later adapter changes propagating");
  rows.parent=column;column.parent=new View();rows.requestLayout();check(column.parent.requests==1,"next data change request reaches the framework parent");
  rows.forced=column.forced=column.parent.forced=false;
  c.bodyHeight(360,180);rows.requestLayout();check(column.parent.requests==2,"later smaller IME window does not trap subsequent request");
  check(rows.measures==0,"repeated keyboard geometry still never consumes adapter measurements");
  rows.visibility=View.GONE;check(c.bodyHeight(360,180)==50,"hidden scroll content has no preferred height");rows.visibility=0;
  c.flexible.put(rows,new int[]{-2,Float.floatToIntBits(0)});check(c.bodyHeight(360,180)==238,"unbounded scroll content uses available viewport without measuring it");
  c.flexible.put(rows,new int[]{380,Float.floatToIntBits(0)});field.fail=true;
  try{c.bodyHeight(360,240);throw new AssertionError("must fail");}catch(IllegalStateException expected){}
  check(rows.params.height==0&&rows.params.weight==1,"leaf measurement failure cannot corrupt list params");
  DialogBodyCheck shortForm=new DialogBodyCheck();check(shortForm.bodyHeight(360,1000)==50,"short form retains natural height");
  System.out.println("PASS "+checks+" production body measurement assertions");
 }
 static class LinearLayout extends View {
  final ArrayList<View> children=new ArrayList<>();int getChildCount(){return children.size();}View getChildAt(int i){return children.get(i);}
  static class LayoutParams {int height,leftMargin,rightMargin,topMargin,bottomMargin;float weight;}
 }
 static class View {
  static final int GONE=8;
  static class MeasureSpec {static final int EXACTLY=0x40000000,UNSPECIFIED=0;static int makeMeasureSpec(int size,int mode){return size|mode;}}
  LinearLayout.LayoutParams params=new LinearLayout.LayoutParams();View parent;boolean forced,fail;int measured,measures,requests,visibility;
  Object getLayoutParams(){return params;}void forceLayout(){forced=true;}int getPaddingLeft(){return 0;}int getPaddingRight(){return 0;}int getPaddingTop(){return 0;}int getPaddingBottom(){return 0;}
  int getVisibility(){return visibility;}int getMinimumHeight(){return 0;}
  void measure(int w,int h){if(fail)throw new IllegalStateException();measured=50;measures++;}
  int getMeasuredHeight(){return measured;}
  // Android requestLayout stops walking once a parent is already marked requested.
  void requestLayout(){requests++;forced=true;if(parent!=null&&!parent.forced)parent.requestLayout();}
 }
 interface ListAdapter {int getCount();View getView(int index,View old,ListView parent);}
 static class ListView extends View {ListAdapter getAdapter(){throw new AssertionError();}}
}
