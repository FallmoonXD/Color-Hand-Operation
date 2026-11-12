package replica;

import android.graphics.Rect;
import android.os.SystemClock;
import android.view.View;
import java.util.function.Consumer;

final class ColorSheetResizeMotion implements Runnable {
    private final View view;
    private final Consumer<Rect> layout;
    private final ColorControlPhysics[] axes={new ColorControlPhysics(0),new ColorControlPhysics(0),new ColorControlPhysics(1),new ColorControlPhysics(1)};
    private final int[] target=new int[4];
    private boolean running;
    private long last,started;

    ColorSheetResizeMotion(View view,Consumer<Rect> layout){this.view=view;this.layout=layout;}

    void move(int left,int top,int width,int height,boolean animate){
        target[0]=left;target[1]=top;target[2]=width;target[3]=height;
        if(!animate||!ColorControlMotion.canAnimate(view)){finish();return;}
        started=SystemClock.uptimeMillis();
        if(!running){running=true;last=started;view.postOnAnimation(this);}
    }

    void cancel(){view.removeCallbacks(this);running=false;}

    private void publish(){
        int left=(int)Math.round(axes[0].position),top=(int)Math.round(axes[1].position);
        layout.accept(new Rect(left,top,left+Math.max(1,(int)Math.round(axes[2].position)),top+Math.max(1,(int)Math.round(axes[3].position))));
    }

    private void finish(){
        cancel();
        for(int index=0;index<axes.length;index++){axes[index].position=target[index];axes[index].velocity=0;}
        publish();
    }

    @Override public void run(){
        if(!running)return;
        float scale=ColorControlMotion.durationScale(view);
        if(!view.isAttachedToWindow()||!view.isShown()||scale<=0){finish();return;}
        long now=SystemClock.uptimeMillis();double elapsed=Math.max(0,(now-last)/(1000d*scale));last=now;
        boolean settled=true;
        for(int index=0;index<axes.length;index++){
            axes[index].step(target[index],.3,0,elapsed);
            settled&=Math.abs(axes[index].position-target[index])<.75&&Math.abs(axes[index].velocity)<46.875;
        }
        if(settled||now-started>=5000*scale){finish();return;}
        publish();view.postOnAnimation(this);
    }
}
