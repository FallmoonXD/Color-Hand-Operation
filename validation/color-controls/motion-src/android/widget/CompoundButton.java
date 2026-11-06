package android.widget;
public class CompoundButton extends android.view.View {
    private boolean checked,broadcasting;private OnCheckedChangeListener listener;
    public interface OnCheckedChangeListener {void onCheckedChanged(CompoundButton button,boolean checked);}
    public boolean isChecked(){return checked;}
    public void setChecked(boolean value){if(checked==value)return;checked=value;drawableStateChanged();if(!broadcasting&&listener!=null){broadcasting=true;listener.onCheckedChanged(this,value);broadcasting=false;}}
    public void setOnCheckedChangeListener(OnCheckedChangeListener listener){this.listener=listener;}
    @Override public boolean performClick(){setChecked(!checked);return true;}
}
