package replica;

import android.content.Context;
import android.content.res.ColorStateList;
import android.util.AttributeSet;
import android.widget.CheckBox;

/** A real CheckBox with the COUI 24dp rounded-square and animated check path. */
public final class ColorCheckBox extends CheckBox {
    private final ColorChoiceDrawable choice;
    public ColorCheckBox(Context c){this(c,null);}
    public ColorCheckBox(Context c,AttributeSet attrs){this(c,attrs,0);}
    public ColorCheckBox(Context c,AttributeSet attrs,int style){
        super(c,attrs,style);float d=getResources().getDisplayMetrics().density;setBackground(null);setButtonTintList(null);choice=new ColorChoiceDrawable(this,false);setButtonDrawable(choice);setMinWidth(0);setMinimumWidth(0);setMinHeight(Math.round(48*d));setPadding(Math.round(8*d),0,Math.round(8*d),0);setCompoundDrawablePadding(Math.round(8*d));
        setGravity(android.view.Gravity.CENTER_VERTICAL|android.view.Gravity.START);setTextSize(16);refreshTheme();ColorOsTheme.watch(this,this::refreshTheme);
    }
    private void refreshTheme(){setTypeface(ColorOsTheme.typeface(getContext(),400));setTextColor(new ColorStateList(new int[][]{{-android.R.attr.state_enabled},{}},new int[]{ColorOsTheme.disabled(getContext()),ColorOsTheme.text(getContext())}));invalidate();}
    @Override public int getCompoundPaddingLeft(){return super.getCompoundPaddingLeft()+(getLayoutDirection()!=LAYOUT_DIRECTION_RTL&&length()>0?Math.round(8*getResources().getDisplayMetrics().density):0);}
    @Override public int getCompoundPaddingRight(){return super.getCompoundPaddingRight()+(getLayoutDirection()==LAYOUT_DIRECTION_RTL&&length()>0?Math.round(8*getResources().getDisplayMetrics().density):0);}
    @Override public void jumpDrawablesToCurrentState(){super.jumpDrawablesToCurrentState();if(choice!=null)choice.stop();}
    @Override protected void onDetachedFromWindow(){choice.stop();super.onDetachedFromWindow();}
}
