package replica;

import android.content.Context;
import android.graphics.BlendMode;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.Rect;
import android.graphics.RectF;
import android.graphics.RuntimeShader;
import android.view.MotionEvent;
import android.view.View;
import android.widget.LinearLayout;

/** The upper SystemUI volume track: SEEK_BAR_SIDE / TYPE_TRANSLUCENT_SMALL_2. */
final class VolumeTrackLight {
    // COUI SpotLightShader specialized to this preset: white light, noise .08, no dither.
    private static final String LIGHT = """
        uniform float2 center;
        uniform float radius;
        uniform float intensity;
        half4 main(float2 p) {
            float3 light = float3(p - center, 200.0);
            float distance = length(light);
            if (distance >= radius) return half4(0);
            float diffuse = max(normalize(light).z, 0.0);
            float attenuation = smoothstep(1.0, 0.0, distance / radius);
            float level = (0.4 + 0.4 * diffuse) * attenuation * 1.5 * intensity;
            float grain = fract(sin(dot(p * 9.259e-4, float2(127.1, 311.7))) * 43758.5453);
            float n = clamp((grain - 0.5) * 0.08 + 0.5, 0.0, 1.0);
            float soft = mix(level - (1.0 - 2.0*n)*level*(1.0-level),
                             level + (2.0*n - 1.0)*(sqrt(level)-level), step(0.5,n));
            float rgb = level + clamp(abs(clamp(soft,0.0,1.0)-level),0.0,1.0);
            return half4(rgb,rgb,rgb,clamp(level,0.0,1.0));
        }
        """;
    private final View host;
    private final ColorControlMotion glow,x,y;
    private final Paint paint=new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Path clip=new Path();
    private RuntimeShader shader;
    private int pointer=-1;
    private boolean releasing;

    VolumeTrackLight(View host){
        this.host=host;
        // Native glow is expressed in 0..10000; position uses a 0.0001 minimum visible change.
        glow=channel();x=channel();y=channel();
        paint.setBlendMode(BlendMode.OVERLAY);
    }
    private ColorControlMotion channel(){return new ColorControlMotion(host,0,.000075,.000075*62.5);}
    private boolean enabled(){return host.isEnabled()&&ColorOsTheme.materialBlur(host.getContext());}
    void touch(MotionEvent e){
        if(!enabled()||host.getWidth()==0||host.getHeight()==0){reset();return;}
        int action=e.getActionMasked();
        if(action==MotionEvent.ACTION_DOWN){
            pointer=e.getPointerId(0);move(e,0);releasing=false;glow.animate(1,.15f,0);
        }else if(pointer!=-1){
            if(action==MotionEvent.ACTION_POINTER_DOWN){
                int i=e.getActionIndex();pointer=e.getPointerId(i);move(e,i);
            }else if(action==MotionEvent.ACTION_POINTER_UP&&e.getPointerId(e.getActionIndex())==pointer){
                int i=e.getActionIndex()==0?1:0;pointer=e.getPointerId(i);move(e,i);
            }else{
                int i=e.findPointerIndex(pointer);if(i>=0)move(e,i);
                if(action==MotionEvent.ACTION_UP||action==MotionEvent.ACTION_CANCEL){
                    pointer=-1;releasing=true;glow.animate(0,.4f,0);
                }
            }
        }
    }
    private void move(MotionEvent e,int i){
        float px=e.getX(i)/host.getWidth(),py=e.getY(i)/host.getHeight();
        if(glow.value()==0){x.snap(px);y.snap(py);}
        else{x.animate(px,.35f,.15f);y.animate(py,.35f,.15f);}
    }
    void reset(){
        pointer=-1;releasing=false;
        if(glow.value()!=0||glow.running())glow.snap(0);
        x.cancel();y.cancel();
    }
    void draw(Canvas c,RectF bounds,float corner){
        if(!enabled()){reset();return;}
        drawFrame(c,bounds,corner,x.value()*host.getWidth(),y.value()*host.getHeight(),
                ColorControlPhysics.clamp(glow.value()),releasing,ColorOsTheme.dark(host.getContext()));
    }
    void drawFrame(Canvas c,RectF bounds,float corner,float px,float py,float progress,boolean release,boolean dark){
        if(progress<=0||bounds.isEmpty()||!c.isHardwareAccelerated())return;
        if(shader==null){shader=new RuntimeShader(LIGHT);paint.setShader(shader);}
        shader.setFloatUniform("center",px,py);
        // COUISpotLightEffect.setRadiusRange sets the release spread to twice the end radius.
        shader.setFloatUniform("radius",100+400*progress+(release?1000*(1-progress):0));
        shader.setFloatUniform("intensity",(dark?.2f:.4f)*progress);
        clip.rewind();clip.addRoundRect(bounds,corner,corner,Path.Direction.CW);
        int save=c.save();c.clipPath(clip);c.drawPaint(paint);c.restoreToCount(save);
    }

    /** Buttons sharing one piece of glass share its light coordinates and its outline. */
    static class Surface extends LinearLayout {
        private final VolumeTrackLight light=new VolumeTrackLight(this);
        private final RectF bounds=new RectF();
        private final Rect trackBounds=new Rect();
        private final int corner;
        private View track;
        private boolean following;
        Surface(Context context,int cornerDp){super(context);corner=PanelStyle.dp(context,cornerDp);}
        void setTrack(View track){this.track=track;}
        @Override public boolean dispatchTouchEvent(MotionEvent event){
            boolean handled=super.dispatchTouchEvent(event);
            int action=event.getActionMasked();
            if(action==MotionEvent.ACTION_DOWN){
                following=handled;
                if(track!=null){
                    track.getDrawingRect(trackBounds);offsetDescendantRectToMyCoords(track,trackBounds);
                    if(trackBounds.contains((int)event.getX(),(int)event.getY()))following=false;
                }
            }
            if(following)light.touch(event);
            if(action==MotionEvent.ACTION_UP||action==MotionEvent.ACTION_CANCEL)following=false;
            return handled;
        }
        @Override protected void dispatchDraw(Canvas canvas){
            super.dispatchDraw(canvas);bounds.set(0,0,getWidth(),getHeight());light.draw(canvas,bounds,corner);
        }
        @Override protected void onDetachedFromWindow(){following=false;light.reset();super.onDetachedFromWindow();}
    }
}
