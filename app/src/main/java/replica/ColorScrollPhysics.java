package replica;

/** COUI edge drag and spring-back model, with an analytic frame-rate-independent solver. */
public final class ColorScrollPhysics {
    // COUI SpringOverScroller SpringConfig(12.19, 16 * 2.15).
    public static final double DAMPING=25+(12.19-8)*3;
    public static final double STIFFNESS=(16*2.15-30)*3.62+194;
    private static final double ROOT=Math.sqrt(DAMPING*DAMPING-4*STIFFNESS);
    private static final double R1=(-DAMPING+ROOT)/2,R2=(-DAMPING-ROOT)/2;
    public double position,velocity;
    public void reset(){position=velocity=0;}
    public void step(double seconds){
        if(!Double.isFinite(seconds)||seconds<0)throw new IllegalArgumentException("Invalid frame time");
        if(seconds==0)return;
        double before=position,a=(velocity-R2*position)/(R1-R2),b=position-a,e1=Math.exp(R1*seconds),e2=Math.exp(R2*seconds);
        position=a*e1+b*e2;velocity=R1*a*e1+R2*b*e2;
        if(before*position<0||(Math.abs(position)<.25&&Math.abs(velocity)<20))reset();
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
