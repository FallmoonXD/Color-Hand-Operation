import java.util.*;
public final class DialogFooterCheck {
 final HashMap<Integer,Button> buttons=new HashMap<>();final LinearLayout actions=new LinearLayout();
 Object getContext(){return this;}int dp(Object c,float value){return Math.round(value);}
 /* PRODUCTION */
 static int checks;static void check(boolean value,String label){checks++;if(!value)throw new AssertionError(label);}
 static class View {
  static class MeasureSpec{static final int UNSPECIFIED=0;static int makeMeasureSpec(int size,int mode){return size;}}
  LinearLayout.LayoutParams params=new LinearLayout.LayoutParams(-2,-2,0);int assignments;Object getLayoutParams(){return params;}void setLayoutParams(LinearLayout.LayoutParams p){params=p;assignments++;}
 }
 static final class Button extends View {
  int intrinsic,padLeft,padRight,minWidth;CharSequence text="OK";Transformation transformation;
  Button(int width){intrinsic=width;}void measure(int w,int h){throw new AssertionError("must not measure a live button");}
  CharSequence getText(){return text;}Transformation getTransformationMethod(){return transformation;}Button getPaint(){return this;}
  int getCompoundPaddingLeft(){return padLeft;}int getCompoundPaddingRight(){return padRight;}int getMinimumWidth(){return minWidth;}int getMinWidth(){return minWidth;}
 }
 interface Transformation{CharSequence getTransformation(CharSequence value,View view);}
 static final class Layout{static float getDesiredWidth(CharSequence text,Button paint){return paint.intrinsic*text.length()/2f;}}
 static final class LinearLayout extends View {
  static final int VERTICAL=1,HORIZONTAL=0;int orientation;List<View> children=new ArrayList<>();
  static class LayoutParams {int width,height,topMargin,leftMargin;float weight;LayoutParams(int w,int h,float weight){width=w;height=h;this.weight=weight;}}
  int getPaddingLeft(){return 16;}int getPaddingRight(){return 16;}void setOrientation(int value){orientation=value;}int getChildCount(){return children.size();}View getChildAt(int i){return children.get(i);}
 }
 static DialogFooterCheck footer(int... widths){DialogFooterCheck f=new DialogFooterCheck();for(int i=0;i<widths.length;i++){Button b=new Button(widths[i]);f.buttons.put(i,b);f.actions.children.add(b);}return f;}
 public static void main(String[] args){
  DialogFooterCheck f=footer(65,70,72);f.arrangeButtons(332);check(f.actions.orientation==0,"three short labels fit horizontally");
  check(f.actions.children.get(1).params.height==-2&&f.actions.children.get(1).params.weight==1,"horizontal footer preserves natural text height");
  int mutations=f.actions.children.stream().mapToInt(v->v.assignments).sum();f.arrangeButtons(332);check(mutations==f.actions.children.stream().mapToInt(v->v.assignments).sum(),"unchanged geometry does not request another layout loop");
  f.buttons.get(1).intrinsic=220;f.arrangeButtons(332);check(f.actions.orientation==1,"long or large-font label stacks footer buttons");
  check(f.actions.children.get(1).params.width==-1&&f.actions.children.get(1).params.topMargin==6,"stacked actions keep full width and spacing");
  check(f.actions.children.get(1).params.height==-2,"stacked actions are not clipped to a fixed 48dp height");
  f.buttons.get(1).intrinsic=70;f.arrangeButtons(332);check(f.actions.orientation==0&&f.actions.children.get(1).params.topMargin==0,"return to smaller font clears old vertical margins");
  footer().arrangeButtons(20);check(true,"empty footer handles narrow windows");
  DialogFooterCheck huge=footer(600);huge.arrangeButtons(180);check(huge.actions.orientation==1&&huge.actions.children.get(0).params.width==-1,"oversized single button remains wrapping full-width");
  Button sample=new Button(60);sample.padLeft=14;sample.padRight=14;check(f.preferredButtonWidth(sample)==88,"preferred width includes both compound paddings");
  sample.transformation=(value,view)->"LONG";check(f.preferredButtonWidth(sample)==148,"measurement honors displayed transformed text");
  sample.transformation=null;sample.minWidth=180;check(f.preferredButtonWidth(sample)==180,"minimum width participates in stacking decision");
  sample.minWidth=0;sample.text=null;check(f.preferredButtonWidth(sample)==28,"null label keeps padding without text measurement");
  check(true,"all arrangement passes complete without measuring any live button");
  System.out.println("PASS "+checks+" production dialog footer assertions");
 }
}
