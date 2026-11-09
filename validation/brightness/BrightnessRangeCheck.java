package replica;

public final class BrightnessRangeCheck {
    private static int checks;
    private static void check(boolean value,String message){checks++;if(!value)throw new AssertionError(message);}
    public static void main(String[] args){
        String[] invalid={null,"","2","2,4095,9","two,4095","2,","2,-1","4095,2","2,2","-1,255","0,65536","0,2147483647","0,2147483648","NaN,4095","0.5,4095"};
        for(String value:invalid)check(BrightnessRangePolicy.parse(value)==null,"invalid range rejected: "+value);
        BrightnessRangePolicy.Range range=BrightnessRangePolicy.parse(" 2 , 4095 ");
        check(range!=null&&range.min==2&&range.max==4095,"explicit PKR110 range retains original units");
        check(range.raw(2726)==2728&&range.progress(2728)==2726,"verified raw value roundtrip remains 2728");
        check(range.raw(Integer.MIN_VALUE)==2&&range.raw(Integer.MAX_VALUE)==4095,"progress extremes clamp without overflow");
        check(range.progress(Integer.MIN_VALUE)==0&&range.progress(Integer.MAX_VALUE)==4093,"raw extremes clamp without overflow");
        check(BrightnessRangePolicy.parse("0,255")!=null,"reported 8-bit range accepted");
        check(BrightnessRangePolicy.parse("0,65535")!=null,"reported 16-bit upper boundary accepted");
        int[] calls={0};
        BrightnessRangePolicy.Range fallback=BrightnessRangePolicy.probe(
                ()->{calls[0]++;throw new SecurityException();},
                ()->{calls[0]++;return BrightnessRangePolicy.values(-1,-1);},
                ()->{calls[0]++;return BrightnessRangePolicy.values(4,8191);});
        check(calls[0]==3&&fallback.min==4&&fallback.max==8191,"each failed probe permits independent next source");
        check(BrightnessRangePolicy.probe(()->{throw new ClassNotFoundException();},()->{throw new NoClassDefFoundError();})==null,"all missing sources yield unknown, never 2..4095");
        calls[0]=0;BrightnessRangePolicy.Range first=BrightnessRangePolicy.probe(()->range,()->{calls[0]++;return fallback;});
        check(first==range&&calls[0]==0,"valid dynamic Settings range takes priority");
        check(BrightnessRangePolicy.command(range,2726).equals("settings put system screen_brightness 2728"),"unknown/false Oplus capability never writes raw auto adjustment");
        check(!BrightnessRangePolicy.command(BrightnessRangePolicy.values(0,65535),1000).contains("adj"),"large range alone does not prove raw adjustment semantics");
        try{BrightnessRangePolicy.command(null,100);throw new AssertionError("unknown must fail");}catch(IllegalStateException expected){checks++;}
        check(range.same(BrightnessRangePolicy.values(2,4095))&&!range.same(fallback)&&!range.same(null),"only matching bounds retain queued range identity");
        System.out.println("PASS "+checks+" production brightness range/command assertions");
    }
}
