package replica;

import android.content.Context;
import android.graphics.Color;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.Gravity;
import android.view.inputmethod.EditorInfo;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.TextView;
import java.util.function.Consumer;

/** Search field shared by settings directories and service-owned pickers. */
public final class SearchField extends LinearLayout {
    public final EditText input;
    public SearchField(Context context,String hint,String initial,Consumer<String> changed){
        super(context);setGravity(Gravity.CENTER_VERTICAL);setFocusableInTouchMode(true);
        input=ColorDialogBuilder.editText(context,hint);
        input.setSingleLine(true);input.setContentDescription(hint);input.setImeOptions(EditorInfo.IME_ACTION_DONE);
        input.setText(initial);input.setBackgroundColor(Color.TRANSPARENT);addView(input,new LayoutParams(0,-2,1));
        TextView clear=new TextView(context);clear.setText("×");clear.setTextSize(22);clear.setGravity(Gravity.CENTER);
        clear.setFocusable(true);clear.setContentDescription("清除搜索");clear.setOnClickListener(v->input.setText(""));
        int touch=PanelStyle.dp(context,48);addView(clear,new LayoutParams(touch,touch));clear.setVisibility(initial.isEmpty()?GONE:VISIBLE);
        Runnable colors=()->{
            setBackground(PanelStyle.shape(context,ColorOsTheme.card(context),18));
            input.setTextColor(ColorOsTheme.text(context));input.setHintTextColor(ColorOsTheme.secondary(context));
            clear.setTextColor(ColorOsTheme.secondary(context));clear.setBackground(ColorDialogBuilder.touchBackground(context,Color.TRANSPARENT,20));
        };
        colors.run();ColorOsTheme.watch(this,colors);
        input.addTextChangedListener(new TextWatcher(){
            public void beforeTextChanged(CharSequence s,int start,int count,int after){}
            public void onTextChanged(CharSequence s,int start,int before,int count){clear.setVisibility(s.length()==0?GONE:VISIBLE);changed.accept(s.toString());}
            public void afterTextChanged(Editable s){}
        });
    }
}
