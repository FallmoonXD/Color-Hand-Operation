import dev.edgeflow.GestureRecognizer;
public final class GestureRecognizerCheck {
 static int checks;
 static void check(boolean ok,String message){checks++;if(!ok)throw new AssertionError(message);}
 static GestureRecognizer g(){return new GestureRecognizer(138,306,300,30,false);}
 public static void main(String[] ignored){
  GestureRecognizer s=g();s.begin(true,5,1000);s.move(142,1000,100);check(s.finish()==0,"below threshold");
  s=g();s.begin(true,5,1000);s.move(143,1000,100);check(s.finish()==1,"short threshold inclusive");check(s.finish()==0,"release cannot double fire");
  s=g();s.begin(false,1259,1000);s.move(1059,800,100);check(s.direction()==-1&&s.finish()==1,"right upward diagonal");
  s=g();s.begin(true,5,1000);s.move(400,1400,100);check(!s.shouldFireLong(299),"wait300msfromDOWN");check(s.shouldFireLong(300),"long fires at300msfromDOWN");s.fired();check(s.finish()==0,"no short after long");check(!s.shouldFireLong(500),"long cannot double fire");
  s=g();s.begin(true,5,1000);s.move(400,1000,100);s.move(200,1000,200);check(!s.shouldFireLong(600),"retract cancels pending long");check(s.finish()==1,"retract leaves valid short");
  s=g();s.begin(true,5,1000);s.move(400,1000,100);s.move(400,1500,200);check(s.shouldFireLong(400),"direction change doesnotrestartDOWNtimer");check(s.direction()==1,"down selected");
  s=g();s.begin(true,5,1000);s.move(200,2500,100);check(s.finish()==0,"vertical edge scroll excluded");
  s=g();s.begin(false,1259,1000);s.move(1300,1100,100);check(s.finish()==0,"outward drag excluded");
  s=g();s.begin(true,5,1000);s.move(500,1000,100);s.cancel();check(!s.shouldFireLong(900)&&s.finish()==0,"cancel suppresses action");
  s=g();s.begin(true,5,1000);s.move(500,1000,100);float[] a=s.anchor();a[0]=99;check(s.anchor()[0]==5,"anchor copied");
  s=g();s.begin(false,1259,1300,1000);s.move(859,1900,2100);check(s.finish()==2,"1100ms accepted right longdown");
  s=g();s.begin(true,5,1000,1000);s.move(325,1000,1100);check(!s.shouldFireLong(1100)&&s.finish()==1,"longdistance before duration falls back to short on release");
  s=g();s.begin(true,5,1000,1000);s.move(225,1220,1400);check(s.isLongDistanceReached(),"radial diagonal threshold");
  for(boolean left:new boolean[]{true,false})for(int direction=-1;direction<=1;direction++)for(boolean longer:new boolean[]{false,true}){
   float startX=left?5:1259,inward=longer?350:160;s=g();s.begin(left,startX,1300,1000);s.move(startX+(left?inward:-inward),1300+direction*inward,1400);
   check(s.direction()==direction&&s.finish()==(longer?2:1),"mirrored gesture side="+left+" direction="+direction+" long="+longer);
  }
  s=new GestureRecognizer(138,306,300,30,true);s.begin(true,5,1000,1000);s.move(400,1000,1010);check(s.pendingLongDelay(1010)==0&&s.shouldFireLong(1010),"immediate mode bypasses delayed dispatch");
  s.fired();check(s.pendingLongDelay(1010)==-1,"fired gesture cannot reschedule");
  s=g();s.begin(true,5,1000,1000);s.move(400,1000,1100);check(s.pendingLongDelay(1100)==200,"held timer uses elapsed DOWN time");s.cancel();check(s.pendingLongDelay(1200)==-1,"cancel clears pending timer");
  s=g();s.begin(false,1259,1300,1000);s.move(859,1700,1100);s.finish();check(s.pendingLongDelay(1100)==-1,"released gesture cannot reschedule");
  s=g();s.begin(true,5,1000,1000);s.move(400,1000,1400);s.fired();s.begin(false,1259,1300,2000);s.move(1099,1300,2050);check(!s.isFired()&&s.finish()==1,"next DOWN resets completed gesture state");
  for(boolean quickShort:new boolean[]{false,true})for(boolean quickLong:new boolean[]{false,true}){
   s=new GestureRecognizer(138,306,300,30,false,quickShort,quickLong);s.begin(true,5,1000,1000);s.move(165,1000,1050);
   check(s.shouldFireShort()==quickShort,"independent short quick-action option");
   if(quickShort){s.fired();s.move(405,1000,1400);check(!s.shouldFireLong(1400)&&s.pendingLongDelay(1400)==-1&&s.finish()==0,"quick short suppresses later long and UP duplicate");}
   else{s.move(405,1000,1200);check(s.pendingLongDelay(1200)==(quickLong?100:-1),"long quick-action controls held timer");check(s.shouldFireLong(1300)==quickLong,"long quick-action controls held dispatch");if(quickLong)s.fired();check(s.finish(1300)==(quickLong?0:2),"qualified long release remains available when quick-action disabled");}
  }
  s=new GestureRecognizer(138,306,300,30,true,false,false);s.begin(false,1259,1300,1000);s.move(859,1300,1010);check(s.pendingLongDelay(1010)==-1&&!s.shouldFireLong(1010)&&s.finish()==2,"long UP-only overrides legacy zero-delay preference");
  s=new GestureRecognizer(138,306,300,30,false,true,true);s.begin(false,1259,1300,1000);s.move(859,1300,1010);check(s.shouldFireShort()&&!s.shouldFireLong(1400)&&s.finish()==1,"large first sample preserves quick-short priority");
  s=new GestureRecognizer(138,306,300,30,false,true,true);s.begin(true,5,1000,1000);s.move(15,1600,1010);check(!s.shouldFireShort()&&s.finish()==0,"quick short still rejects vertical edge scrolling");
  for(boolean left:new boolean[]{true,false})for(int direction=-1;direction<=1;direction++){
   float startX=left?5:1259;s=new GestureRecognizer(138,Float.MAX_VALUE,300,30,false,false,true);s.begin(left,startX,1300,1000);s.move(startX+(left?600:-600),1300+direction*600,1500);
   check(s.isShortReady()&&!s.isLongDistanceReached()&&!s.shouldFireLong(10000)&&s.pendingLongDelay(1500)==-1,"disabled long cannot classify or schedule long side="+left+" direction="+direction);
   check(s.direction()==direction&&s.finish()==1,"disabled long retains distant short release side="+left+" direction="+direction);
  }
  s=new GestureRecognizer(138,Float.MAX_VALUE,300,30,true,false,true);s.begin(true,5,1000,1000);s.move(805,1000,1010);check(!s.shouldFireLong(1010)&&s.pendingLongDelay(1010)==-1&&s.finish()==1,"zero-delay cannot bypass disabled long");
  s=new GestureRecognizer(138,Float.MAX_VALUE,300,30,false,true,true);s.begin(true,5,1000,1000);s.move(805,1000,1010);check(s.shouldFireShort(),"disabled long preserves quick-short behavior");s.fired();check(!s.shouldFireLong(3000)&&s.finish()==0,"disabled long quick short still dispatches once");
  s=new GestureRecognizer(138,Float.MAX_VALUE,300,30,false,false,true);s.begin(true,5,1000,1000);s.move(805,1000,1500);s.cancel();check(!s.shouldFireLong(3000)&&s.pendingLongDelay(3000)==-1&&s.finish()==0,"disabled long cancellation suppresses distant short action");
  System.out.println("PASS "+checks+" gesture state assertions");
 }
}
