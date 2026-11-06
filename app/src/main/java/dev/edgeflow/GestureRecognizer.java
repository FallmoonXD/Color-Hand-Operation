package dev.edgeflow;

/** Platform-independent gesture state; one DOWN can produce at most one action. */
public final class GestureRecognizer {
    public static final int UP=-1, STRAIGHT=0, DOWN=1;
    private final float shortDistance,longDistance;
    private final GestureAngles shortAngles,longAngles;
    private final long longDelay;
    private final boolean immediateLong,quickShort,quickLong;
    private boolean running,left,fired,shortReady,longReady;
    private float sx,sy,x,y;
    private int direction,shortDirection,longDirection;
    private long downTime,lastEventTime;
    private boolean longDistanceReached;

    public GestureRecognizer(float shortDistance,float longDistance,long longDelay,float angleDegrees,boolean immediateLong){
        this(shortDistance,longDistance,longDelay,angleDegrees,immediateLong,false,true);
    }
    public GestureRecognizer(float shortDistance,float longDistance,long longDelay,float angleDegrees,boolean immediateLong,boolean quickShort,boolean quickLong){
        this(shortDistance,longDistance,longDelay,new GestureAngles(80,Math.max(10,Math.min(70,angleDegrees)),Math.max(10,Math.min(70,angleDegrees)),80),new GestureAngles(80,Math.max(10,Math.min(70,angleDegrees)),Math.max(10,Math.min(70,angleDegrees)),80),immediateLong,quickShort,quickLong);
    }
    public GestureRecognizer(float shortDistance,float longDistance,long longDelay,GestureAngles shortAngles,GestureAngles longAngles,boolean immediateLong,boolean quickShort,boolean quickLong){
        this.shortDistance=Math.max(1,shortDistance);this.longDistance=Math.max(this.shortDistance+1,longDistance);
        this.longDelay=Math.max(0,longDelay);this.shortAngles=shortAngles;this.longAngles=longAngles;this.immediateLong=immediateLong;this.quickShort=quickShort;this.quickLong=quickLong;
    }
    public void begin(boolean left,float x,float y){begin(left,x,y,0);}
    public void begin(boolean left,float x,float y,long now){this.left=left;sx=this.x=x;sy=this.y=y;running=true;fired=shortReady=longReady=longDistanceReached=false;downTime=lastEventTime=now;direction=STRAIGHT;}
    public void move(float x,float y,long now){
        if(!running||fired)return;this.x=x;this.y=y;lastEventTime=now;
        float inward=(x-sx)*(left?1:-1),vertical=y-sy;
        float angle=(float)Math.toDegrees(Math.atan2(vertical,Math.max(.001f,inward)));
        // A primarily vertical edge drag must not accidentally issue navigation.
        float distance=(float)Math.hypot(inward,vertical);
        shortReady=inward>0&&distance>=shortDistance&&shortAngles.accepts(angle);
        boolean reached=inward>0&&distance>=longDistance&&longAngles.accepts(angle);
        shortDirection=shortAngles.direction(angle);longDirection=longAngles.direction(angle);
        direction=reached&&!quickShort?longDirection:shortDirection;
        longDistanceReached=reached;
        // OHO b6/a schedules max(delay-(eventTime-downTime),0); distance is radial.
        updateLongReady(now);
    }
    private void updateLongReady(long now){longReady=longDistanceReached&&(immediateLong||Math.max(0L,now-downTime)>=longDelay);}
    public boolean shouldFireShort(){return running&&!fired&&quickShort&&shortReady;}
    public boolean shouldFireLong(long now){if(!running||fired||quickShort||!quickLong||!longDistanceReached)return false;updateLongReady(now);return longReady;}
    /** Compatibility for callers that already supplied the final event through move(). */
    public int finish(){return finish(lastEventTime);}
    /** Duration applies to release too; quickLong chooses dispatch timing, not eligibility. */
    public int finish(long eventTime){if(!running||fired){running=false;return 0;}updateLongReady(eventTime);running=false;if(quickShort&&shortReady)return 1;return longReady?2:shortReady?1:0;}
    public void fired(){fired=true;}
    public void cancel(){running=false;shortReady=longReady=longDistanceReached=false;}
    public boolean isRunning(){return running;}
    public boolean isFired(){return fired;}
    public boolean isShortReady(){return shortReady;}
    public boolean isLongReady(){return longReady;}
    public long pendingLongDelay(long now){return !running||fired||quickShort||!quickLong||!longDistanceReached?-1:immediateLong?0:Math.max(0,longDelay-Math.max(0L,now-downTime));}
    public boolean isLongDistanceReached(){return longDistanceReached;}
    public int direction(){return direction;}
    public float[] anchor(){return new float[]{sx,sy,x,y};}
    public float inward(){return Math.max(0,(x-sx)*(left?1:-1));}
    public float vertical(){return y-sy;}
    public String directionName(){return direction==UP?"up":direction==DOWN?"down":"straight";}
    public String directionName(boolean longer){int selected=longer?longDirection:shortDirection;return selected==UP?"up":selected==DOWN?"down":"straight";}
}
