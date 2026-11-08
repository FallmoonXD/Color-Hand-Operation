package replica;

/** COUI edge drag and the frame stepping used by Settings' rebound scroller. */
public final class ColorScrollPhysics {
    // COUI SpringOverScroller SpringConfig(12.19, 16 * 2.15).
    public static final double DAMPING=25+(12.19-8)*3;
    public static final double STIFFNESS=(16*2.15-30)*3.62+194;
    public double position,velocity;
    private double predictedPosition;
    public void reset(){position=velocity=predictedPosition=0;}
    void beginReturn(double releaseVelocity){velocity=releaseVelocity;predictedPosition=0;}
    public void step(double seconds){
        if(!Double.isFinite(seconds)||seconds<0)throw new IllegalArgumentException("Invalid frame time");
        if(seconds==0)return;
        if(seconds>2){reset();return;}
        int steps=(int)Math.ceil(seconds/.025);
        double dt=Math.max(.0001,seconds/steps);
        for(int i=0;i<steps;i++){
            if(Math.abs(position)<.25){reset();return;}
            double origin=position;
            do{
                integrate(dt);
                if(origin*position<=0||(Math.abs(position)<.05&&Math.abs(velocity)<=20)){reset();return;}
            }while(Math.abs(position-origin)<=.5);
        }
    }
    private void integrate(double dt){
        // SpringOverScroller starts the next RK step at the previous predicted
        // position. It coalesces sub-pixel movement before presenting the frame.
        double v1=velocity,a1=-STIFFNESS*predictedPosition;
        double v2=v1+a1*dt/2,a2=-STIFFNESS*(position+v1*dt/2)-DAMPING*v2;
        double v3=v1+a2*dt/2,a3=-STIFFNESS*(position+v2*dt/2)-DAMPING*v3;
        predictedPosition=position+v3*dt;
        double v4=v1+a3*dt,a4=-STIFFNESS*predictedPosition-DAMPING*v4;
        position+=.167f*dt*(v1+2*v2+2*v3+v4);
        velocity+=.167f*dt*(a1+2*a2+2*a3+a4);
    }
    public static double resisted(double delta,double offset,double limit){
        if(!Double.isFinite(delta)||!Double.isFinite(offset)||!Double.isFinite(limit)||limit<=0)throw new IllegalArgumentException("Invalid pull geometry");
        return delta*.4*(1-Math.min(Math.abs(offset)/limit,1));
    }
    public static final class Pull {
        public final double offset,remaining;
        Pull(double offset,double remaining){this.offset=offset;this.remaining=remaining;}
    }
    /** Scroll delta has the opposite sign from the translated content. */
    public static Pull pull(double offset,double scrollDelta,double limit){
        double movement=resisted(scrollDelta,offset,limit),next=offset-movement;
        if(offset!=0&&offset*next<=0){double factor=movement/scrollDelta;return new Pull(0,factor==0?scrollDelta:scrollDelta-offset/factor);}
        return new Pull(Math.max(-limit*.999,Math.min(limit*.999,next)),0);
    }
    /** Gate only the extra edge effect; native in-range touch handling remains independent. */
    public static final class Gate {
        public static final int UNDECIDED=0,VERTICAL=1,HORIZONTAL=2,BLOCKED=3;
        private int direction;private boolean slider;
        public void begin(boolean slider,int pointers){this.slider=slider;direction=pointers!=1?BLOCKED:UNDECIDED;}
        // A slider hit does not own the stream until the motion is horizontal.
        // Decide at the same slop as ScrollView/SeekBar; leaving a diagonal gap
        // would let SeekBar claim X drift before the parent recognizes a scroll.
        public void move(double dx,double dy,double slop){if(direction!=UNDECIDED)return;double x=Math.abs(dx),y=Math.abs(dy);if(y>slop&&y>=x)direction=VERTICAL;else if(x>slop&&x>y)direction=HORIZONTAL;}
        public void block(){direction=BLOCKED;}
        public boolean allowed(){return direction==VERTICAL;}
        public boolean slider(){return slider;}
        public boolean horizontal(){return direction==HORIZONTAL;}
        public void end(){slider=false;direction=BLOCKED;}
    }
}
