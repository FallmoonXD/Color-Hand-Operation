package replica;

import android.app.Activity;
import android.content.Context;
import android.graphics.*;
import android.graphics.drawable.ColorDrawable;
import android.os.Handler;
import android.os.Looper;
import android.text.*;
import android.view.*;
import android.view.inputmethod.EditorInfo;
import android.widget.*;

/** Gallery-inspired color draft editor. Only onConfirm asks the caller to persist a value. */
public final class ColorPaletteSheet implements AutoCloseable {
    public interface Listener {
        default void onPreview(int argb,int opacityPercent){}
        void onConfirm(int argb,int opacityPercent);
        default void onCancel(){}
    }
    private static final Handler MAIN=new Handler(Looper.getMainLooper());
    private final Activity owner;
    private final ColorPaletteState state;
    private final Listener listener;
    private ColorActionSheet sheet;
    private LinearLayout body,tabs;
    private TextView gridTab,ringTab,opacityTitle,cancel,confirm;
    private EditText hex;
    private ColorPalettePicker picker;
    private OpacitySlider opacity;
    private Swatch swatch;
    private ColorOsTheme.Subscription theme;
    private Runnable onDismiss;
    private boolean updating,gridMode,settled,shown;

    public ColorPaletteSheet(Activity owner,int argb,int opacityPercent,Listener listener){if(owner==null||listener==null)throw new IllegalArgumentException("Activity and listener required");this.owner=owner;this.listener=listener;state=new ColorPaletteState(argb,opacityPercent);}
    public ColorPaletteSheet setOnDismissListener(Runnable callback){if(shown)throw new IllegalStateException("Set listener before show");onDismiss=callback;return this;}
    public boolean isShowing(){return sheet!=null&&sheet.isShowing();}
    public ColorPaletteSheet show(){
        if(shown)throw new IllegalStateException("Palette is single-use");shown=true;build();
        sheet=new ColorActionSheet(owner).setTitle("触钮颜色").setView(body).setOnDismissListener(this::sheetClosed);sheet.show();
        if(sheet.isShowing()){theme=ColorOsTheme.observe(owner,this::refreshTheme);listener.onPreview(state.color(),state.opacity());}
        return this;
    }
    public void dismiss(){if(sheet!=null)sheet.dismiss();else sheetClosed();}
    public void dismissImmediately(){if(sheet!=null)sheet.dismissImmediately();else sheetClosed();}
    @Override public void close(){dismissImmediately();}
    private void sheetClosed(){
        if(theme!=null){theme.close();theme=null;}
        // ColorActionSheet emits dismissal before its deferred chosen action. Resolve cancellation
        // on the next turn so a successful confirm is distinct from lifecycle cancellation.
        MAIN.post(()->{if(!settled){settled=true;state.restore();listener.onCancel();}Runnable callback=onDismiss;onDismiss=null;if(callback!=null)callback.run();});
    }
    private void accept(){
        if(settled||sheet==null)return;Integer parsed=ColorPaletteState.parse(hex.getText().toString());
        if(parsed==null){hex.setError("输入 #RRGGBB 或 #AARRGGBB");hex.requestFocus();return;}
        state.setColor(parsed);final int argb=state.color(),percent=state.opacity();
        sheet.dismissThen(()->{if(settled)return;settled=true;listener.onConfirm(argb,percent);});
    }
    private void build(){
        body=new LinearLayout(owner);body.setOrientation(LinearLayout.VERTICAL);
        ColorSettingsScrollView scroll=new ColorSettingsScrollView(owner);scroll.setFillViewport(false);scroll.setVerticalScrollBarEnabled(false);
        LinearLayout controls=new LinearLayout(owner);controls.setOrientation(LinearLayout.VERTICAL);controls.setPadding(dp(20),dp(4),dp(20),dp(8));scroll.addView(controls,new ScrollView.LayoutParams(-1,-2));body.addView(scroll,new LinearLayout.LayoutParams(-1,-2,1));
        tabs=new LinearLayout(owner);tabs.setGravity(Gravity.CENTER);tabs.setPadding(dp(4),dp(4),dp(4),dp(4));controls.addView(tabs,new LinearLayout.LayoutParams(-1,-2));
        gridTab=tab("网格",true);ringTab=tab("色环",false);tabs.addView(gridTab,new LinearLayout.LayoutParams(0,-2,1));LinearLayout.LayoutParams rt=new LinearLayout.LayoutParams(0,-2,1);rt.leftMargin=dp(8);tabs.addView(ringTab,rt);
        picker=new ColorPalettePicker(owner,state,()->update(true,false));controls.addView(picker,new LinearLayout.LayoutParams(-1,-2));
        opacityTitle=label("",14,400);opacityTitle.setPadding(0,dp(8),0,0);controls.addView(opacityTitle);
        opacity=new OpacitySlider(owner);opacity.setMax(100);opacity.setProgress(state.opacity());opacity.setContentDescription("透明度级别");controls.addView(opacity,new LinearLayout.LayoutParams(-1,dp(48)));
        opacity.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener(){int start;public void onStartTrackingTouch(SeekBar b){start=state.opacity();}public void onProgressChanged(SeekBar b,int value,boolean user){if(user&&!updating){state.setOpacity(value);update(true,false);}}public void onStopTrackingTouch(SeekBar b){if(opacity.cancelled){state.setOpacity(start);update(true,false);}}});
        LinearLayout current=new LinearLayout(owner);current.setGravity(Gravity.CENTER_VERTICAL);current.setPadding(0,dp(12),0,0);controls.addView(current,new LinearLayout.LayoutParams(-1,-2));
        swatch=new Swatch(owner);swatch.setContentDescription("当前颜色预览");current.addView(swatch,new LinearLayout.LayoutParams(dp(64),dp(64)));
        LinearLayout values=new LinearLayout(owner);values.setOrientation(LinearLayout.VERTICAL);LinearLayout.LayoutParams vp=new LinearLayout.LayoutParams(0,-2,1);vp.leftMargin=dp(16);current.addView(values,vp);
        TextView code=label("颜色代码",13,400);code.setTextColor(ColorOsTheme.secondary(owner));values.addView(code);
        hex=ColorDialogBuilder.editText(owner,"#AARRGGBB");hex.setSingleLine(true);hex.setInputType(android.text.InputType.TYPE_CLASS_TEXT|android.text.InputType.TYPE_TEXT_FLAG_CAP_CHARACTERS|android.text.InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS);hex.setImeOptions(EditorInfo.IME_ACTION_DONE);hex.setSelectAllOnFocus(true);hex.setContentDescription("颜色代码");values.addView(hex,new LinearLayout.LayoutParams(-1,-2));
        hex.addTextChangedListener(new TextWatcher(){public void beforeTextChanged(CharSequence s,int a,int c,int f){}public void onTextChanged(CharSequence s,int a,int before,int count){if(updating)return;Integer color=ColorPaletteState.parse(s.toString());setConfirmEnabled(color!=null);if(color!=null){state.setColor(color);update(true,true);}else hex.setError("输入 #RRGGBB 或 #AARRGGBB");}public void afterTextChanged(Editable text){}});
        hex.setOnEditorActionListener((v,id,event)->{if(id==EditorInfo.IME_ACTION_DONE){accept();return true;}return false;});
        LinearLayout actions=new LinearLayout(owner);actions.setGravity(Gravity.CENTER_VERTICAL);actions.setPadding(dp(16),dp(8),dp(16),dp(4));body.addView(actions,new LinearLayout.LayoutParams(-1,-2));
        cancel=button("取消",this::dismiss);confirm=button("确定",this::accept);actions.addView(cancel,new LinearLayout.LayoutParams(0,-2,1));LinearLayout.LayoutParams cp=new LinearLayout.LayoutParams(0,-2,1);cp.leftMargin=dp(12);actions.addView(confirm,cp);
        update(false,false);refreshTheme();
    }
    private TextView label(String text,int size,int weight){TextView label=new TextView(owner);label.setText(text);label.setTextSize(size);label.setTextColor(ColorOsTheme.text(owner));label.setTypeface(ColorOsTheme.typeface(owner,weight));label.setIncludeFontPadding(false);return label;}
    private TextView tab(String title,boolean grid){TextView tab=label(title,14,500);tab.setGravity(Gravity.CENTER);tab.setMinHeight(dp(44));tab.setPadding(dp(12),dp(10),dp(12),dp(10));tab.setFocusable(true);tab.setOnClickListener(v->{gridMode=grid;picker.setGrid(grid);refreshTheme();});return tab;}
    private TextView button(String title,Runnable action){TextView button=label(title,16,500);button.setGravity(Gravity.CENTER);button.setMinHeight(dp(48));button.setPadding(dp(12),dp(12),dp(12),dp(12));button.setFocusable(true);button.setOnClickListener(v->action.run());return button;}
    private void update(boolean notify,boolean editingHex){
        if(updating)return;updating=true;
        try{if(!editingHex)hex.setText(ColorPaletteState.hex(state.color()));setConfirmEnabled(ColorPaletteState.parse(hex.getText().toString())!=null);opacity.setProgress(state.opacity());opacity.setStateDescription(state.opacity()+"%");opacityTitle.setText("透明度级别  "+state.opacity()+"%");picker.refresh();opacity.invalidate();swatch.invalidate();swatch.setStateDescription(ColorPaletteState.hex(state.color())+"，可见度 "+state.opacity()+"%");}finally{updating=false;}
        if(notify&&!settled)listener.onPreview(state.color(),state.opacity());
    }
    private void setConfirmEnabled(boolean enabled){if(confirm!=null){confirm.setEnabled(enabled);confirm.setAlpha(enabled?1:.4f);}}
    private void refreshTheme(){
        if(body==null)return;int normal=ColorOsTheme.text(owner),accent=ColorOsTheme.accent(owner);
        tabs.setBackground(PanelStyle.shape(owner,ColorOsTheme.fill(owner),24));
        for(TextView tab:new TextView[]{gridTab,ringTab}){boolean selected=(tab==gridTab)==gridMode;tab.setSelected(selected);tab.setStateDescription(selected?"已选择":"");tab.setTextColor(selected?accent:normal);tab.setTypeface(ColorOsTheme.typeface(owner,selected?600:400));tab.setBackground(ColorDialogBuilder.touchBackground(owner,selected?ColorOsTheme.popup(owner):Color.TRANSPARENT,20));}
        opacityTitle.setTextColor(normal);opacityTitle.setTypeface(ColorOsTheme.typeface(owner,400));ColorDialogBuilder.styleInput(hex);
        cancel.setTextColor(ColorOsTheme.text(owner));cancel.setBackground(ColorDialogBuilder.touchBackground(owner,ColorOsTheme.fill(owner),24));confirm.setTextColor(Color.WHITE);confirm.setBackground(ColorDialogBuilder.touchBackground(owner,accent,24));cancel.setTypeface(ColorOsTheme.typeface(owner,500));confirm.setTypeface(ColorOsTheme.typeface(owner,500));picker.invalidate();opacity.invalidate();swatch.invalidate();
    }
    private int dp(float n){return Math.round(n*owner.getResources().getDisplayMetrics().density);}
    private void checker(Canvas canvas,RectF rect,float radius,Paint paint){int save=canvas.save();Path mask=new Path();mask.addRoundRect(rect,radius,radius,Path.Direction.CW);canvas.clipPath(mask);float tile=dp(6);for(int y=0;y<Math.ceil(rect.height()/tile);y++)for(int x=0;x<Math.ceil(rect.width()/tile);x++){paint.setShader(null);paint.setColor(((x+y)&1)==0?(ColorOsTheme.dark(owner)?0xff555555:0xffffffff):(ColorOsTheme.dark(owner)?0xff333333:0xffdddddd));canvas.drawRect(rect.left+x*tile,rect.top+y*tile,Math.min(rect.right,rect.left+(x+1)*tile),Math.min(rect.bottom,rect.top+(y+1)*tile),paint);}canvas.restoreToCount(save);}
    private final class Swatch extends View {final Paint paint=new Paint(Paint.ANTI_ALIAS_FLAG);Swatch(Context c){super(c);setForceDarkAllowed(false);}protected void onDraw(Canvas canvas){RectF bounds=new RectF(0,0,getWidth(),getHeight());checker(canvas,bounds,dp(14),paint);paint.setColor(state.effectiveColor());canvas.drawRoundRect(bounds,dp(14),dp(14),paint);paint.setColor(ColorOsTheme.dark(owner)?0x50ffffff:0x20000000);paint.setStyle(Paint.Style.STROKE);paint.setStrokeWidth(dp(.7f));bounds.inset(dp(.4f),dp(.4f));canvas.drawRoundRect(bounds,dp(14),dp(14),paint);paint.setStyle(Paint.Style.FILL);}}
    private final class OpacitySlider extends SeekBar {
        final Paint paint=new Paint(Paint.ANTI_ALIAS_FLAG);boolean cancelled;
        OpacitySlider(Context c){super(c);setProgressDrawable(new ColorDrawable(0));setThumb(new ColorDrawable(0));setBackground(null);setSplitTrack(false);setThumbOffset(0);setPadding(dp(10),0,dp(10),0);setForceDarkAllowed(false);}
        @Override protected synchronized void onDraw(Canvas canvas){float left=getPaddingLeft(),right=Math.max(left+1,getWidth()-getPaddingRight()),cy=getHeight()/2f;RectF track=new RectF(left-dp(10),cy-dp(10),right+dp(10),cy+dp(10));checker(canvas,track,dp(10),paint);boolean rtl=getLayoutDirection()==LAYOUT_DIRECTION_RTL;paint.setShader(new LinearGradient(rtl?right:left,cy,rtl?left:right,cy,state.color()&0xffffff,state.color(),Shader.TileMode.CLAMP));canvas.drawRoundRect(track,dp(10),dp(10),paint);paint.setShader(null);float p=getProgress()/100f,x=left+(right-left)*(rtl?1-p:p);paint.setColor(Color.WHITE);canvas.drawCircle(x,cy,dp(7),paint);paint.setColor(0x30000000);paint.setStyle(Paint.Style.STROKE);paint.setStrokeWidth(dp(.7f));canvas.drawCircle(x,cy,dp(7),paint);paint.setStyle(Paint.Style.FILL);if(isFocused()){paint.setColor(ColorOsTheme.accent(owner));paint.setStyle(Paint.Style.STROKE);paint.setStrokeWidth(dp(1));canvas.drawRoundRect(track,dp(10),dp(10),paint);paint.setStyle(Paint.Style.FILL);}}
        @Override public boolean onTouchEvent(MotionEvent e){if(e.getActionMasked()==MotionEvent.ACTION_DOWN)cancelled=false;if(e.getActionMasked()==MotionEvent.ACTION_CANCEL)cancelled=true;return super.onTouchEvent(e);}
    }
}
