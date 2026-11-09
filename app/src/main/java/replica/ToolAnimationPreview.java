package replica;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.view.Gravity;
import android.view.View;
import android.widget.FrameLayout;

/** A small screen and tool, drawn locally and animated by the same player as the window. */
public final class ToolAnimationPreview extends FrameLayout {
    private final View card;
    private final Paint paint=new Paint(Paint.ANTI_ALIAS_FLAG);
    private final boolean entering;
    private final ToolAnimation.Style style;
    private final ToolMotionPlayer player;
    private final Runnable reset;
    public ToolAnimationPreview(Context c,ToolAnimation.Style style,boolean entering){
        super(c);this.style=style;this.entering=entering;setWillNotDraw(false);
        setBackground(PanelStyle.shape(c,ColorOsTheme.dark(c)?0xff292a30:0xffecedf2,10));
        setClipToOutline(true);
        card=new View(c){
            @Override protected void onDraw(Canvas canvas){
                float w=getWidth(),h=getHeight();
                paint.setColor(ColorOsTheme.dark(c)?0xff595c67:0xffcdd2e0);
                canvas.drawRoundRect(0,0,w,h,h*.20f,h*.20f,paint);
                paint.setColor(ColorOsTheme.accent(c));
                canvas.drawRoundRect(w*.13f,h*.18f,w*.81f,h*.29f,h*.06f,h*.06f,paint);
                for(int i=0;i<3;i++)canvas.drawCircle(w*(.22f+i*.26f),h*.66f,h*.11f,paint);
            }
        };
        LayoutParams panel=new LayoutParams(dp(35),dp(30),Gravity.RIGHT|Gravity.CENTER_VERTICAL);
        panel.rightMargin=dp(7);addView(card,panel);
        player=new ToolMotionPlayer(card,false,Float.NaN,true,card::setAlpha);
        reset=()->player.poster(style,entering);player.shown();
        setOnClickListener(v->play());setFocusable(true);
    }
    private int dp(float value){return PanelStyle.dp(getContext(),value);}
    @Override public CharSequence getAccessibilityClassName(){return "android.widget.Button";}
    @Override protected void onDraw(Canvas canvas){
        super.onDraw(canvas);
        paint.setColor(ColorOsTheme.dark(getContext())?0xff42444b:0xffd9dce5);
        for(int i=0;i<3;i++)canvas.drawRoundRect(dp(6),dp(8+i*12),dp(i%2==0?29:24),dp(11+i*12),dp(2),dp(2),paint);
        paint.setColor(ColorOsTheme.accent(getContext()));canvas.drawRoundRect(getWidth()-dp(2),getHeight()*.36f,getWidth(),getHeight()*.64f,dp(1),dp(1),paint);
    }
    public void play(){
        removeCallbacks(reset);
        if(entering)player.prepare();else player.shown();
        player.play(style,entering,()->postDelayed(reset,450));
    }
    @Override protected void onLayout(boolean changed,int l,int t,int r,int b){
        super.onLayout(changed,l,t,r,b);if(changed)reset.run();
    }
    @Override protected void onDetachedFromWindow(){removeCallbacks(reset);player.cancel();super.onDetachedFromWindow();}
}
