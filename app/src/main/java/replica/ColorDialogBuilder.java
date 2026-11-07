package replica;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Context;
import android.content.ContextWrapper;
import android.content.DialogInterface;
import android.content.res.ColorStateList;
import android.graphics.Color;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.RippleDrawable;
import android.os.Bundle;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.view.WindowManager;
import android.widget.*;
import java.util.HashMap;

/** Settings dialogs with owned content. AlertDialog remains the public lifecycle/API contract. */
public class ColorDialogBuilder extends AlertDialog.Builder {
 private CharSequence title,message;private View content,customTitle;private CharSequence[] items;
 private int choiceMode,selected=-1;private boolean[] checked;private ListAdapter adapter;
 private DialogInterface.OnClickListener itemClick;private DialogInterface.OnMultiChoiceClickListener multiClick;
 private boolean cancelable=true;private DialogInterface.OnCancelListener cancelListener;private DialogInterface.OnDismissListener dismissListener;
 private DialogInterface.OnKeyListener keyListener;
 private final HashMap<Integer,CharSequence> labels=new HashMap<>();
 private final HashMap<Integer,DialogInterface.OnClickListener> clicks=new HashMap<>();
 private final java.util.HashSet<Integer> destructive=new java.util.HashSet<>();
 private final boolean glass;
 public ColorDialogBuilder(Context c){this(c,false);}
 protected ColorDialogBuilder(Context c,boolean glass){super(c);this.glass=glass;}
 protected final boolean needsTextInput(){return content!=null&&containsTextEditor(content);}
 @Override public ColorDialogBuilder setTitle(CharSequence value){title=value;return this;}
 @Override public ColorDialogBuilder setTitle(int value){return setTitle(getContext().getText(value));}
 @Override public ColorDialogBuilder setMessage(CharSequence value){message=value;return this;}
 @Override public ColorDialogBuilder setMessage(int value){return setMessage(getContext().getText(value));}
 @Override public ColorDialogBuilder setCustomTitle(View value){customTitle=value;return this;}
 @Override public ColorDialogBuilder setView(View value){content=value;return this;}
 @Override public ColorDialogBuilder setView(int value){return setView(android.view.LayoutInflater.from(getContext()).inflate(value,null));}
 public ColorDialogBuilder setView(View value,int left,int top,int right,int bottom){value.setPadding(left,top,right,bottom);return setView(value);}
 @Override public ColorDialogBuilder setItems(CharSequence[] value,DialogInterface.OnClickListener listener){items=value;itemClick=listener;choiceMode=0;return this;}
 @Override public ColorDialogBuilder setItems(int value,DialogInterface.OnClickListener listener){return setItems(getContext().getResources().getTextArray(value),listener);}
 @Override public ColorDialogBuilder setSingleChoiceItems(CharSequence[] value,int which,DialogInterface.OnClickListener listener){items=value;selected=which;itemClick=listener;choiceMode=1;return this;}
 @Override public ColorDialogBuilder setSingleChoiceItems(int value,int which,DialogInterface.OnClickListener listener){return setSingleChoiceItems(getContext().getResources().getTextArray(value),which,listener);}
 @Override public ColorDialogBuilder setMultiChoiceItems(CharSequence[] value,boolean[] states,DialogInterface.OnMultiChoiceClickListener listener){items=value;checked=states;multiClick=listener;choiceMode=2;return this;}
 @Override public ColorDialogBuilder setMultiChoiceItems(int value,boolean[] states,DialogInterface.OnMultiChoiceClickListener listener){return setMultiChoiceItems(getContext().getResources().getTextArray(value),states,listener);}
 @Override public ColorDialogBuilder setAdapter(ListAdapter value,DialogInterface.OnClickListener listener){adapter=value;itemClick=listener;choiceMode=0;return this;}
 @Override public ColorDialogBuilder setSingleChoiceItems(ListAdapter value,int which,DialogInterface.OnClickListener listener){adapter=value;selected=which;itemClick=listener;choiceMode=1;return this;}
 @Override public ColorDialogBuilder setPositiveButton(CharSequence text,DialogInterface.OnClickListener listener){return button(DialogInterface.BUTTON_POSITIVE,text,listener);}
 @Override public ColorDialogBuilder setNegativeButton(CharSequence text,DialogInterface.OnClickListener listener){return button(DialogInterface.BUTTON_NEGATIVE,text,listener);}
 @Override public ColorDialogBuilder setNeutralButton(CharSequence text,DialogInterface.OnClickListener listener){return button(DialogInterface.BUTTON_NEUTRAL,text,listener);}
 @Override public ColorDialogBuilder setPositiveButton(int text,DialogInterface.OnClickListener listener){return setPositiveButton(getContext().getText(text),listener);}
 @Override public ColorDialogBuilder setNegativeButton(int text,DialogInterface.OnClickListener listener){return setNegativeButton(getContext().getText(text),listener);}
 @Override public ColorDialogBuilder setNeutralButton(int text,DialogInterface.OnClickListener listener){return setNeutralButton(getContext().getText(text),listener);}
 private ColorDialogBuilder button(int which,CharSequence text,DialogInterface.OnClickListener listener){labels.put(which,text);clicks.put(which,listener);return this;}
 public ColorDialogBuilder setDestructiveButton(int which,CharSequence text,DialogInterface.OnClickListener listener){destructive.add(which);return button(which,text,listener);}
 public ColorDialogBuilder setPositiveDestructive(CharSequence text,DialogInterface.OnClickListener listener){return setDestructiveButton(DialogInterface.BUTTON_POSITIVE,text,listener);}
 @Override public ColorDialogBuilder setCancelable(boolean value){cancelable=value;return this;}
 @Override public ColorDialogBuilder setOnCancelListener(DialogInterface.OnCancelListener value){cancelListener=value;return this;}
 @Override public ColorDialogBuilder setOnDismissListener(DialogInterface.OnDismissListener value){dismissListener=value;return this;}
 @Override public ColorDialogBuilder setOnKeyListener(DialogInterface.OnKeyListener value){keyListener=value;return this;}
 @Override public AlertDialog create(){ColorDialog d=new ColorDialog(getContext());d.setCancelable(cancelable);d.setCanceledOnTouchOutside(cancelable);d.setOnCancelListener(cancelListener);d.setOnDismissListener(dismissListener);d.setOnKeyListener(keyListener);return d;}
 @Override public AlertDialog show(){AlertDialog d=create();d.show();return d;}
 /** Teardown must release windows before an accessibility service loses its window token. */
 public static void dismissImmediately(android.app.Dialog dialog){if(dialog instanceof ColorDialog)((ColorDialog)dialog).finishDismiss();else dialog.dismiss();}
 /** Keeps existing dismiss listeners; navigation runs only after the old window has detached. */
 public static void dismissThen(DialogInterface dialog,Runnable after){if(dialog instanceof ColorDialog)((ColorDialog)dialog).dismissThen(after);else{dialog.dismiss();if(after!=null)new android.os.Handler(android.os.Looper.getMainLooper()).post(after);}}

 public static EditText editText(Context c,String hint){EditText edit=new SettingsEditText(c);edit.setHint(hint);styleInput(edit);return edit;}
 public static void styleInput(EditText edit){Context c=edit.getContext();edit.setTextSize(16);edit.setTextColor(ColorOsTheme.text(c));edit.setHintTextColor(ColorOsTheme.secondary(c));edit.setTypeface(ColorOsTheme.typeface(c,400));edit.setBackground(touchBackground(c,ColorOsTheme.dark(c)?0x16ffffff:0x07000000,14));edit.setPadding(dp(c,16),dp(c,13),dp(c,16),dp(c,13));edit.setMinHeight(dp(c,50));edit.setHighlightColor((ColorOsTheme.accent(c)&0xffffff)|0x40000000);GradientDrawable cursor=new GradientDrawable();cursor.setColor(ColorOsTheme.accent(c));cursor.setSize(dp(c,2),dp(c,24));cursor.setCornerRadius(dp(c,1));edit.setTextCursorDrawable(cursor);}
 public static void styleList(ListView list){list.setDivider(null);list.setDividerHeight(0);list.setCacheColorHint(Color.TRANSPARENT);list.setBackgroundColor(Color.TRANSPARENT);list.setSelector(touchBackground(list.getContext(),Color.TRANSPARENT,12));list.setVerticalScrollBarEnabled(false);list.setOverScrollMode(View.OVER_SCROLL_NEVER);}
 public static TextView listLabel(Context c,CharSequence text){TextView label=new TextView(c);label.setText(text);label.setTextSize(16);label.setTypeface(ColorOsTheme.typeface(c,500));label.setTextColor(ColorOsTheme.text(c));label.setIncludeFontPadding(false);label.setGravity(Gravity.CENTER_VERTICAL|Gravity.START);label.setPadding(dp(c,24),dp(c,14),dp(c,24),dp(c,14));label.setMinHeight(dp(c,56));return label;}
 public static Drawable touchBackground(Context c,int fill,float corner){GradientDrawable shape=new GradientDrawable();shape.setColor(fill);shape.setCornerRadius(dp(c,corner));GradientDrawable mask=new GradientDrawable();mask.setColor(Color.WHITE);mask.setCornerRadius(dp(c,corner));return new RippleDrawable(ColorStateList.valueOf(ColorOsTheme.dark(c)?0x18ffffff:0x10000000),shape,mask);}
 private static ColorStateList buttonColors(int color){return new ColorStateList(new int[][]{{-android.R.attr.state_enabled},{}},new int[]{(color&0xffffff)|0x61000000,color});}
 private static int dp(Context c,float value){return PanelStyle.dp(c,value);}
 private static Activity activity(Context c){while(c instanceof ContextWrapper){if(c instanceof Activity)return (Activity)c;Context next=((ContextWrapper)c).getBaseContext();if(next==c)break;c=next;}return null;}
 private static boolean containsTextEditor(View view){if(view.onCheckIsTextEditor())return true;if(view instanceof ViewGroup){ViewGroup group=(ViewGroup)view;for(int i=0;i<group.getChildCount();i++)if(containsTextEditor(group.getChildAt(i)))return true;}return false;}

 /** Validation stays inline, instead of opening the framework's error popup. */
 private static final class ErrorLabel extends TextView {ErrorLabel(Context c){super(c);setTextSize(13);setTypeface(ColorOsTheme.typeface(c,400));setTextColor(ColorOsTheme.error(c));setPadding(dp(c,12),dp(c,5),dp(c,12),dp(c,7));setAccessibilityLiveRegion(View.ACCESSIBILITY_LIVE_REGION_POLITE);}}
 private static final class SettingsEditText extends EditText {
  private CharSequence error;private ErrorLabel errorLabel;
  SettingsEditText(Context c){super(c);addTextChangedListener(new android.text.TextWatcher(){public void beforeTextChanged(CharSequence s,int start,int count,int after){}public void onTextChanged(CharSequence s,int start,int before,int count){if(error!=null)setError(null);}public void afterTextChanged(android.text.Editable s){}});}
  @Override public CharSequence getError(){return error;}
  @Override public void setError(CharSequence message){setError(message,null);}
  @Override public void setError(CharSequence message,Drawable icon){error=message;if(message==null){if(errorLabel!=null&&errorLabel.getParent() instanceof ViewGroup)((ViewGroup)errorLabel.getParent()).removeView(errorLabel);return;}if(errorLabel==null)errorLabel=new ErrorLabel(getContext());errorLabel.setText(message);if(errorLabel.getParent()==null&&getParent() instanceof LinearLayout){LinearLayout parent=(LinearLayout)getParent();parent.addView(errorLabel,parent.indexOfChild(this)+1,new LinearLayout.LayoutParams(-1,-2));}requestFocus();}
  @Override public void onInitializeAccessibilityNodeInfo(android.view.accessibility.AccessibilityNodeInfo info){super.onInitializeAccessibilityNodeInfo(info);info.setContentInvalid(error!=null);info.setError(error);}
 }

 private static final class ChoiceRow extends LinearLayout implements Checkable {
  private final CompoundButton control;private final boolean radio;
  ChoiceRow(Context c,CharSequence text,boolean radio,boolean checked){super(c);this.radio=radio;setBaselineAligned(false);setGravity(Gravity.CENTER_VERTICAL);setPadding(dp(c,24),dp(c,4),dp(c,24),dp(c,4));setMinimumHeight(dp(c,56));TextView label=listLabel(c,text);label.setPadding(0,dp(c,10),dp(c,16),dp(c,10));addView(label,new LinearLayout.LayoutParams(0,-2,1));control=radio?new ColorRadioButton(c):new ColorCheckBox(c);control.setClickable(false);control.setFocusable(false);control.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);addView(control,new LinearLayout.LayoutParams(dp(c,40),dp(c,48)));setContentDescription(text);label.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);setChecked(checked);}
  public boolean isChecked(){return control.isChecked();}public void setChecked(boolean value){control.setChecked(value);}public void toggle(){setChecked(!isChecked());}
  @Override public CharSequence getAccessibilityClassName(){return radio?RadioButton.class.getName():CheckBox.class.getName();}
  @Override public void onInitializeAccessibilityNodeInfo(android.view.accessibility.AccessibilityNodeInfo info){super.onInitializeAccessibilityNodeInfo(info);info.setCheckable(true);info.setChecked(isChecked());}
 }

 private final class ColorDialog extends AlertDialog {
  private final boolean sheet=items!=null||adapter!=null||content!=null;
  private LinearLayout root,header,actions;private FrameLayout viewport;private View body;private ListView list;
  private SettingsSurface surface;
  private TextView heading,description;private ImageView close;private View rule,handle;
  private final HashMap<Integer,Button> buttons=new HashMap<>();
  private final java.util.IdentityHashMap<TextView,TextStyle> contentStyles=new java.util.IdentityHashMap<>();
  private final java.util.IdentityHashMap<View,int[]> flexible=new java.util.IdentityHashMap<>();
  private ColorMotion motion;private android.animation.ValueAnimator animator;private float reveal;
  private boolean closing,finished,paused,deliveryPending,deliveryCancelled,measuring,allowCancel=true,outsideDismiss=true;
  private Runnable pendingAction;private int imeBottom,lastWidth=-1,lastHeight=-1,lastBottom=-1;
  private android.app.Application.ActivityLifecycleCallbacks lifecycle;
  private final android.os.Handler main=new android.os.Handler(android.os.Looper.getMainLooper());
  private final Runnable geometry=()->layoutWindow();
  private final android.view.ViewTreeObserver.OnGlobalLayoutListener contentLayout=()->{if(!finished){root.removeCallbacks(geometry);root.post(geometry);}};
  private final Runnable entrance=()->{if(!closing&&!finished){layoutWindow();if(glass&&sheet)NativeMaterial.whenReady(getWindow().getDecorView(),()->{if(!closing&&!finished)animatePanel(true,null);});else if(sheet)animatePanel(true,null);}};
  private android.database.DataSetObserver observer;
  ColorDialog(Context c){super(c);}
  @Override public void setCancelable(boolean value){allowCancel=value;super.setCancelable(value);}
  @Override public void setCanceledOnTouchOutside(boolean value){outsideDismiss=value;super.setCanceledOnTouchOutside(value);}
  @Override public void show(){if(finished)return;try{super.show();}catch(RuntimeException failure){finishDismiss();throw failure;}}
  @Override protected void onCreate(Bundle state){
   super.onCreate(state);Context c=getContext();
   root=new LinearLayout(c);root.setOrientation(LinearLayout.VERTICAL);root.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_YES);
   root.setAccessibilityPaneTitle(title);root.setPadding(0,0,0,dp(c,sheet?4:8));
   header=new LinearLayout(c);header.setOrientation(LinearLayout.VERTICAL);header.setPadding(dp(c,24),dp(c,sheet?8:24),dp(c,24),dp(c,12));
   root.addView(header,new LinearLayout.LayoutParams(-1,-2));
   if(sheet){FrameLayout grip=new FrameLayout(c);handle=new View(c);handle.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);grip.addView(handle,new FrameLayout.LayoutParams(dp(c,28),dp(c,4),Gravity.CENTER));header.addView(grip,new LinearLayout.LayoutParams(-1,dp(c,16)));}
   if(customTitle!=null){styleChildren(customTitle);header.addView(customTitle);}
   else if(title!=null||sheet){
    LinearLayout titleRow=new LinearLayout(c);titleRow.setGravity(Gravity.CENTER_VERTICAL);titleRow.setBaselineAligned(false);
    heading=listLabel(c,title==null?"":title);heading.setTextSize(20);heading.setTypeface(ColorOsTheme.typeface(c,600));heading.setGravity(sheet?Gravity.START|Gravity.CENTER_VERTICAL:Gravity.CENTER);heading.setPadding(0,dp(c,8),0,dp(c,8));heading.setMinHeight(0);heading.setAccessibilityHeading(true);
    titleRow.addView(heading,new LinearLayout.LayoutParams(0,-2,1));
    if(sheet&&allowCancel){close=new ImageView(c);close.setImageDrawable(SystemIcons.raw(c,"ic_close_white"));close.setScaleType(ImageView.ScaleType.CENTER_INSIDE);close.setPadding(dp(c,13),dp(c,13),dp(c,13),dp(c,13));close.setFocusable(true);close.setContentDescription("关闭"+(title==null?"面板":title));close.setOnClickListener(v->cancel());LinearLayout.LayoutParams cp=new LinearLayout.LayoutParams(dp(c,48),dp(c,48));cp.leftMargin=dp(c,8);titleRow.addView(close,cp);}
    header.addView(titleRow,new LinearLayout.LayoutParams(-1,-2));
   }
   viewport=new FrameLayout(c);root.addView(viewport,new LinearLayout.LayoutParams(-1,0,1));
   if(items!=null||adapter!=null)buildList(c);else buildBody(c);
   rule=new View(c);rule.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);LinearLayout.LayoutParams rp=new LinearLayout.LayoutParams(-1,Math.max(1,dp(c,.5f)));rp.leftMargin=dp(c,24);rp.rightMargin=dp(c,24);root.addView(rule,rp);
   actions=new LinearLayout(c);actions.setGravity(Gravity.CENTER_VERTICAL);actions.setPadding(dp(c,16),dp(c,8),dp(c,16),dp(c,12));root.addView(actions,new LinearLayout.LayoutParams(-1,-2));
   for(int which:new int[]{BUTTON_NEUTRAL,BUTTON_NEGATIVE,BUTTON_POSITIVE})if(labels.get(which)!=null){
    Button button=new Button(c);button.setText(labels.get(which));button.setAllCaps(false);button.setGravity(Gravity.CENTER);button.setTextSize(16);button.setSingleLine(false);button.setIncludeFontPadding(false);button.setTypeface(ColorOsTheme.typeface(c,500));button.setElevation(0);button.setStateListAnimator(null);button.setMinWidth(0);button.setMinimumWidth(0);button.setMinHeight(dp(c,48));button.setMinimumHeight(dp(c,48));button.setPadding(dp(c,14),dp(c,12),dp(c,14),dp(c,12));button.setContentDescription(labels.get(which));
    buttons.put(which,button);actions.addView(button);button.setOnClickListener(v->{DialogInterface.OnClickListener click=clicks.get(which);dismissThen(click==null?null:()->click.onClick(this,which));});
   }
   if(buttons.isEmpty()){actions.setPadding(0,0,0,dp(c,12));rule.setVisibility(View.GONE);}
   setContentView(root);Window window=getWindow();
   if(containsTextEditor(root))window.clearFlags(WindowManager.LayoutParams.FLAG_ALT_FOCUSABLE_IM);
   window.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE);window.setWindowAnimations(0);
   root.setOnApplyWindowInsetsListener((v,insets)->{int next=insets.getInsets(android.view.WindowInsets.Type.ime()).bottom;if(next!=imeBottom){imeBottom=next;root.removeCallbacks(geometry);root.post(geometry);}return insets;});
   root.getViewTreeObserver().addOnGlobalLayoutListener(contentLayout);
   refreshColors();ColorOsTheme.watch(root,()->{refreshColors();root.removeCallbacks(geometry);root.post(geometry);});
  }
  private void buildList(Context c){
   list=new ListView(c);styleList(list);list.setChoiceMode(choiceMode==2?ListView.CHOICE_MODE_MULTIPLE:choiceMode==1?ListView.CHOICE_MODE_SINGLE:ListView.CHOICE_MODE_NONE);
   ListAdapter actual=adapter!=null?adapter:new BaseAdapter(){
    public int getCount(){return items.length;}public Object getItem(int index){return items[index];}public long getItemId(int index){return index;}
    public View getView(int index,View reuse,ViewGroup parent){return choiceMode==0?listLabel(c,items[index]):new ChoiceRow(c,items[index],choiceMode==1,choiceMode==1?index==selected:checked!=null&&index<checked.length&&checked[index]);}
   };list.setAdapter(actual);body=list;viewport.addView(body,new FrameLayout.LayoutParams(-1,-1));
   if(choiceMode==1&&selected>=0){list.setItemChecked(selected,true);list.setSelection(selected);}if(choiceMode==2&&checked!=null)for(int i=0;i<checked.length;i++)list.setItemChecked(i,checked[i]);
   list.setOnItemClickListener((parent,view,index,id)->{
    if(closing||finished)return;
    if(choiceMode==2){boolean value=list.isItemChecked(index);if(checked!=null&&index<checked.length)checked[index]=value;if(view instanceof Checkable)((Checkable)view).setChecked(value);if(multiClick!=null)multiClick.onClick(this,index,value);}
    else if(choiceMode==1){selected=index;if(actual instanceof BaseAdapter)((BaseAdapter)actual).notifyDataSetChanged();if(itemClick!=null)itemClick.onClick(this,index);}
    else dismissThen(itemClick==null?null:()->itemClick.onClick(this,index));
   });
   observer=new android.database.DataSetObserver(){@Override public void onChanged(){if(!finished){root.removeCallbacks(geometry);root.post(geometry);}}};actual.registerDataSetObserver(observer);
  }
  private void buildBody(Context c){
   LinearLayout vertical=new LinearLayout(c);vertical.setOrientation(LinearLayout.VERTICAL);
   if(message!=null){description=listLabel(c,message);description.setGravity(sheet?Gravity.START:Gravity.CENTER);description.setTextSize(15);description.setTypeface(ColorOsTheme.typeface(c,400));description.setTextColor(ColorOsTheme.secondary(c));description.setPadding(0,dp(c,2),0,dp(c,12));description.setMinHeight(0);vertical.addView(description);}
   if(content!=null){if(content.getParent() instanceof ViewGroup)((ViewGroup)content.getParent()).removeView(content);styleChildren(content);}
   boolean direct=message==null&&content!=null&&(content instanceof ScrollView||content instanceof ListView||makeFlexible(content));
   if(direct)body=content;
   else{if(content!=null)vertical.addView(content,new LinearLayout.LayoutParams(-1,-2));ColorSettingsScrollView scroll=new ColorSettingsScrollView(c);scroll.setFillViewport(false);scroll.addView(vertical,new ScrollView.LayoutParams(-1,-2));body=scroll;}
   int left=dp(c,24),right=left;if(content!=null){left=Math.max(0,left-content.getPaddingLeft());right=Math.max(0,right-content.getPaddingRight());}
   viewport.setPadding(left,dp(c,4),right,dp(c,8));viewport.addView(body,new FrameLayout.LayoutParams(-1,-1));
  }
  private boolean makeFlexible(View view){
   if(!(view instanceof LinearLayout)||((LinearLayout)view).getOrientation()!=LinearLayout.VERTICAL)return false;
   LinearLayout column=(LinearLayout)view;for(int i=0;i<column.getChildCount();i++){View child=column.getChildAt(i);if(child instanceof ListView||child instanceof ScrollView){LinearLayout.LayoutParams p=(LinearLayout.LayoutParams)child.getLayoutParams();flexible.put(child,new int[]{p.height,Float.floatToIntBits(p.weight)});p.height=0;p.weight=1;child.setLayoutParams(p);}}
   return !flexible.isEmpty();
  }
  private int bodyHeight(int width,int limit){
   int available=Math.max(1,width-viewport.getPaddingLeft()-viewport.getPaddingRight()),total=viewport.getPaddingTop()+viewport.getPaddingBottom();
   if(list!=null){ListAdapter a=list.getAdapter();for(int i=0;i<a.getCount()&&total<limit;i++){View row=a.getView(i,null,list);row.measure(View.MeasureSpec.makeMeasureSpec(available,View.MeasureSpec.EXACTLY),View.MeasureSpec.makeMeasureSpec(0,View.MeasureSpec.UNSPECIFIED));total+=Math.max(dp(getContext(),48),row.getMeasuredHeight());}return Math.max(dp(getContext(),32),total);}
   if(!flexible.isEmpty()){
    // Do not measure/forceLayout a live ListView merely to discover its preferred height.
    // A forceLayout without a framework layout can leave its parent marked requested and
    // swallow the next adapter requestLayout before it reaches ViewRoot.
    LinearLayout column=(LinearLayout)body;total+=column.getPaddingTop()+column.getPaddingBottom();
    for(int i=0;i<column.getChildCount();i++){
     View child=column.getChildAt(i);if(child.getVisibility()==View.GONE)continue;
     LinearLayout.LayoutParams p=(LinearLayout.LayoutParams)child.getLayoutParams();int childHeight;
     int[] preferred=flexible.get(child);
     if(preferred!=null)childHeight=preferred[0]>=0?preferred[0]:Math.max(child.getMinimumHeight(),limit);
     else if(p.height>=0)childHeight=p.height;
     else{
      int childWidth=Math.max(1,available-column.getPaddingLeft()-column.getPaddingRight()-p.leftMargin-p.rightMargin);
      child.measure(View.MeasureSpec.makeMeasureSpec(childWidth,View.MeasureSpec.EXACTLY),View.MeasureSpec.makeMeasureSpec(0,View.MeasureSpec.UNSPECIFIED));childHeight=child.getMeasuredHeight();
     }
     total+=childHeight+p.topMargin+p.bottomMargin;
    }
   }else{body.measure(View.MeasureSpec.makeMeasureSpec(available,View.MeasureSpec.EXACTLY),View.MeasureSpec.makeMeasureSpec(0,View.MeasureSpec.UNSPECIFIED));total+=body.getMeasuredHeight();}
   return total;
  }
  private void arrangeButtons(int width){
   int count=buttons.size();if(count==0)return;int available=Math.max(1,width-actions.getPaddingLeft()-actions.getPaddingRight());boolean stack=false;
   for(Button button:buttons.values())if(preferredButtonWidth(button)+dp(getContext(),8)>available/count)stack=true;
   actions.setOrientation(stack?LinearLayout.VERTICAL:LinearLayout.HORIZONTAL);
   for(int i=0;i<actions.getChildCount();i++){View button=actions.getChildAt(i);LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(stack?-1:0,-2,stack?0:1);if(i>0){if(stack)p.topMargin=dp(getContext(),6);else p.leftMargin=dp(getContext(),8);}LinearLayout.LayoutParams old=(LinearLayout.LayoutParams)button.getLayoutParams();if(old.width!=p.width||old.height!=p.height||old.weight!=p.weight||old.topMargin!=p.topMargin||old.leftMargin!=p.leftMargin)button.setLayoutParams(p);}
  }
  private int preferredButtonWidth(Button button){
   CharSequence text=button.getText();
   if(button.getTransformationMethod()!=null)text=button.getTransformationMethod().getTransformation(text,button);
   // Do not probe a live Button with an UNSPECIFIED measure: TextView can keep that
   // narrow internal text layout while the unchanged footer bounds remain much wider.
   int textWidth=text==null?0:(int)Math.ceil(android.text.Layout.getDesiredWidth(text,button.getPaint()));
   return Math.max(Math.max(button.getMinimumWidth(),button.getMinWidth()),textWidth+button.getCompoundPaddingLeft()+button.getCompoundPaddingRight());
  }
  private void layoutWindow(){
   if(root==null||finished||measuring)return;measuring=true;
   try{
    Context c=getContext();android.graphics.Rect available=ColorWindowSpace.available(c,root);
    int width=Math.max(1,available.width()),height=Math.max(1,available.height());
    ColorSheetGeometry.Bounds bounds=ColorSheetGeometry.fit(width,height,0,0,0,0,0,c.getResources().getDisplayMetrics().density,Integer.MAX_VALUE);
    int panelWidth=sheet?bounds.width:Math.max(1,Math.min(dp(c,392),width-dp(c,40)));
    arrangeButtons(panelWidth);int exact=View.MeasureSpec.makeMeasureSpec(panelWidth,View.MeasureSpec.EXACTLY),free=View.MeasureSpec.makeMeasureSpec(0,View.MeasureSpec.UNSPECIFIED);header.measure(exact,free);actions.measure(exact,free);
    int chrome=header.getMeasuredHeight()+actions.getMeasuredHeight()+root.getPaddingBottom()+(rule.getVisibility()==View.GONE?0:Math.max(1,dp(c,.5f)));
    int desired=chrome+bodyHeight(panelWidth,Math.max(1,bounds.height-chrome));int panelHeight=Math.min(bounds.height,Math.max(chrome+1,desired));
    // ADJUST_RESIZE and the floating window's normal fit-insets policy place it above
    // the IME/system bars. Bounds only limits size; adding its IME bottom here would lift twice.
    int bottom=sheet?dp(c,8):0;Window window=getWindow();window.setGravity(sheet?Gravity.BOTTOM|Gravity.CENTER_HORIZONTAL:Gravity.CENTER);
    if(lastWidth!=panelWidth||lastHeight!=panelHeight||lastBottom!=bottom){lastWidth=panelWidth;lastHeight=panelHeight;lastBottom=bottom;WindowManager.LayoutParams p=window.getAttributes();p.width=panelWidth;p.height=panelHeight;p.y=sheet?bottom:0;window.setAttributes(p);}
   }finally{measuring=false;}
  }
  private void styleChildren(View view){if(view instanceof EditText)styleInput((EditText)view);else if(view instanceof CompoundButton)return;else if(view instanceof ListView){ListView list=(ListView)view;styleList(list);if(list.getAdapter() instanceof BaseAdapter)((BaseAdapter)list.getAdapter()).notifyDataSetChanged();return;}else if(view instanceof TextView){TextView text=(TextView)view;TextStyle style=contentStyles.get(text);if(style==null){style=new TextStyle(text);contentStyles.put(text,style);}style.apply(text);}if(view instanceof ViewGroup)for(int i=0;i<((ViewGroup)view).getChildCount();i++)styleChildren(((ViewGroup)view).getChildAt(i));}
  /** Preserve the owner's text hierarchy while its palette changes under an open editor. */
  private final class TextStyle {
   final int role,weight;final ColorStateList fixed;
   TextStyle(TextView text){Context c=text.getContext();int color=text.getCurrentTextColor();fixed=text.getTextColors();role=text instanceof ErrorLabel?6:color==ColorOsTheme.text(c)?1:color==ColorOsTheme.secondary(c)?2:color==ColorOsTheme.tertiary(c)?3:color==ColorOsTheme.accent(c)?4:color==ColorOsTheme.disabled(c)?5:0;int adjustment=c.getResources().getConfiguration().fontWeightAdjustment;if(adjustment==Integer.MAX_VALUE)adjustment=0;weight=Math.max(100,Math.min(900,(text.getTypeface()==null?400:text.getTypeface().getWeight())-adjustment));}
   void apply(TextView text){Context c=text.getContext();text.setTypeface(ColorOsTheme.typeface(c,weight));if(role==0)text.setTextColor(fixed);else text.setTextColor(role==1?ColorOsTheme.text(c):role==2?ColorOsTheme.secondary(c):role==3?ColorOsTheme.tertiary(c):role==4?ColorOsTheme.accent(c):role==6?ColorOsTheme.error(c):ColorOsTheme.disabled(c));}
  }

  @Override protected void onStart(){
   super.onStart();Context c=getContext();Window window=getWindow();window.setWindowAnimations(0);window.setDimAmount(glass?.12f:.24f);
   if(allowCancel&&outsideDismiss&&(window.getAttributes().flags&WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE)!=0)window.addFlags(WindowManager.LayoutParams.FLAG_WATCH_OUTSIDE_TOUCH);
   if(surface==null)surface=new SettingsSurface(root,sheet?SettingsSurface.Kind.SHEET:SettingsSurface.Kind.DIALOG,sheet?28:22,window);
   window.getDecorView().setClipToOutline(true);layoutWindow();refreshColors();observeOwner();
   if(sheet){poseSheet(0,Math.max(root.getHeight(),lastHeight));root.post(entrance);}
   else if(glass){motion=new ColorMotion(window);motion.enter();}
   else{poseCenter(0,.96f);animatePanel(true,null);}
  }
  private void observeOwner(){Activity owner=activity(getContext());if(owner==null||lifecycle!=null)return;lifecycle=new android.app.Application.ActivityLifecycleCallbacks(){
   public void onActivityCreated(Activity a,Bundle b){}public void onActivityStarted(Activity a){}public void onActivityResumed(Activity a){if(a==owner)paused=false;}
   public void onActivityPaused(Activity a){if(a==owner){paused=true;if(closing||deliveryPending)finishDismiss();}}
   public void onActivityStopped(Activity a){}public void onActivitySaveInstanceState(Activity a,Bundle b){}public void onActivityDestroyed(Activity a){if(a==owner)finishDismiss();}
  };owner.getApplication().registerActivityLifecycleCallbacks(lifecycle);}
  private boolean ownerAvailable(){Activity a=activity(getContext());return !paused&&(a==null||!a.isFinishing()&&!a.isDestroyed());}
  private boolean animations(){return android.animation.ValueAnimator.areAnimatorsEnabled()&&ColorOsTheme.animationScale(getContext())!=0;}
  private void poseSheet(float value,int distance){
   reveal=value;getWindow().getDecorView().setTranslationY((1-value)*distance);
   surface.setRevealAlpha(value<=0?0:1);
  }
  private void poseCenter(float value,float scale){
   // Keep the material root opaque. Only the content and the compositor region fade.
   reveal=value;surface.setRevealAlpha(value);
   for(int i=0;i<root.getChildCount();i++)root.getChildAt(i).setAlpha(value);
   root.setScaleX(scale);root.setScaleY(scale);
  }
  private void animatePanel(boolean enter,Runnable done){
   if(animator!=null){animator.removeAllUpdateListeners();animator.removeAllListeners();animator.cancel();animator=null;}
   float start=reveal,end=enter?1:0,startScale=root.getScaleX(),endScale=enter?1:.97f;
   int distance=Math.max(root.getHeight(),lastHeight);
   if(!animations()){if(sheet)poseSheet(end,distance);else poseCenter(end,endScale);if(done!=null)done.run();return;}
   animator=android.animation.ValueAnimator.ofFloat(0,1);animator.setDuration(sheet?250:enter?220:140);
   if(sheet)animator.setInterpolator(enter?new android.view.animation.PathInterpolator(.3f,0,.1f,1):new android.view.animation.PathInterpolator(.3f,0,1,1));
   else animator.setInterpolator(ColorMotion.spring(.3,0));
   animator.addUpdateListener(animation->{float t=(float)animation.getAnimatedValue(),value=start+(end-start)*t;
    if(sheet)poseSheet(value,distance);else poseCenter(value,startScale+(endScale-startScale)*t);
   });
   if(done!=null)animator.addListener(new android.animation.AnimatorListenerAdapter(){@Override public void onAnimationEnd(android.animation.Animator animation){done.run();}});animator.start();
  }
  private void refreshColors(){
   if(root==null)return;Context c=getContext();if(surface!=null)surface.refresh();
   if(heading!=null){heading.setTextColor(ColorOsTheme.text(c));heading.setTypeface(ColorOsTheme.typeface(c,600));}if(description!=null){description.setTextColor(ColorOsTheme.secondary(c));description.setTypeface(ColorOsTheme.typeface(c,400));}
   if(handle!=null)handle.setBackground(PanelStyle.shape(c,ColorOsTheme.tertiary(c),2));if(close!=null){close.setColorFilter(ColorOsTheme.text(c));close.setBackground(touchBackground(c,ColorOsTheme.fill(c),24));}if(rule!=null)rule.setBackgroundColor(ColorOsTheme.dark(c)?0x18ffffff:0x10000000);
   if(customTitle!=null)styleChildren(customTitle);if(content!=null)styleChildren(content);if(list!=null){styleList(list);if(list.getAdapter() instanceof BaseAdapter)((BaseAdapter)list.getAdapter()).notifyDataSetChanged();}
   for(Integer which:buttons.keySet()){Button button=buttons.get(which);button.setTextColor(buttonColors(destructive.contains(which)?ColorOsTheme.error(c):which==BUTTON_NEGATIVE?ColorOsTheme.secondary(c):ColorOsTheme.accent(c)));button.setTypeface(ColorOsTheme.typeface(c,500));button.setBackground(touchBackground(c,sheet?ColorOsTheme.fill(c):Color.TRANSPARENT,24));}
  }
  @Override public Button getButton(int which){return buttons.get(which);}
  @Override public ListView getListView(){return list;}
  @Override public boolean dispatchTouchEvent(android.view.MotionEvent event){return closing||finished||super.dispatchTouchEvent(event);}
  @Override public boolean onTouchEvent(android.view.MotionEvent event){if(event.getActionMasked()==android.view.MotionEvent.ACTION_OUTSIDE&&allowCancel&&outsideDismiss){cancel();return true;}return super.onTouchEvent(event);}
  private void dismissThen(Runnable after){if(closing||finished)return;pendingAction=after;dismiss();}
  @Override public void dismiss(){
   if(closing||finished)return;
   if(root==null||!isShowing()||!root.isAttachedToWindow()||!ownerAvailable()){finishDismiss();return;}
   closing=true;root.cancelPendingInputEvents();root.removeCallbacks(entrance);NativeMaterial.cancelEntrance(getWindow().getDecorView());
   if(!sheet&&glass&&motion!=null)motion.exit(()->finishDismiss(true));else animatePanel(false,()->finishDismiss(true));
  }
  private void finishDismiss(){deliveryCancelled=true;pendingAction=null;finishDismiss(false);}
  private void finishDismiss(boolean dispatch){
   if(finished){if(!dispatch){deliveryCancelled=true;releaseOwner();}return;}
   finished=true;closing=true;Runnable after=dispatch?pendingAction:null;pendingAction=null;deliveryPending=after!=null;
   stopMotion();if(list!=null&&observer!=null){list.getAdapter().unregisterDataSetObserver(observer);observer=null;}
   super.dismiss();
   if(after!=null)main.post(()->{try{if(!deliveryCancelled&&ownerAvailable())after.run();}finally{deliveryPending=false;releaseOwner();}});else releaseOwner();
  }
  private void stopMotion(){
   if(root!=null){root.removeCallbacks(geometry);root.removeCallbacks(entrance);root.animate().withEndAction(null).cancel();}
   if(finished&&root!=null&&root.getViewTreeObserver().isAlive())root.getViewTreeObserver().removeOnGlobalLayoutListener(contentLayout);
   if(animator!=null){animator.removeAllListeners();animator.removeAllUpdateListeners();animator.cancel();animator=null;}if(motion!=null){motion.cancel();motion=null;}
   Window w=getWindow();if(w!=null&&w.peekDecorView()!=null)NativeMaterial.cancelEntrance(w.getDecorView());
  }
  private void releaseOwner(){Activity owner=activity(getContext());if(owner!=null&&lifecycle!=null){owner.getApplication().unregisterActivityLifecycleCallbacks(lifecycle);lifecycle=null;}}
  @Override protected void onStop(){stopMotion();if(!deliveryPending)releaseOwner();super.onStop();}
 }
}
