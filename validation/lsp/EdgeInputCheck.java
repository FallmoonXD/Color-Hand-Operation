import dev.edgeflow.lsp.EdgeInputSession;
import static dev.edgeflow.lsp.EdgeInputSession.Decision.*;

public class EdgeInputCheck {
    static int checks;
    static void check(boolean value,String label){checks++;if(!value)throw new AssertionError(label);}
    static EdgeInputSession session(){return new EdgeInputSession(1264,2780,140,417,2363,84);}
    static void ready(EdgeInputSession s,boolean left){
        check(s.begin(7,left?20:1244,1390,100,true),"eligible origin");
        check(s.move(7,1,left?130:1134,1390,120,true)==CLAIM,"horizontal threshold requests ownership");
        check(!s.owned(),"request is not ownership");s.claim();check(s.owned(),"native confirmation grants ownership");
    }
    public static void main(String[] ignored){
        for(boolean left:new boolean[]{true,false}){
            float x=left?20:1244, end=left?230:1034;
            EdgeInputSession s=session();
            check(s.begin(7,x,1390,100,true),"tap starts observation");s.claim();check(!s.owned(),"cannot claim before decision");
            check(s.end(7,x,1390,101,true)==NONE,"tap never fires");
            s=session();ready(s,left);check(s.end(7,end,1390,140,true)==BACK,"claimed swipe fires");
            check(s.end(7,end,1390,150,true)==NONE,"no repeated action");
            s=session();check(s.begin(7,x,1390,100,true),"observe down");
            check(s.move(7,1,end,1390,120,true)==CLAIM,"observe candidate");
            check(s.end(7,end,1390,140,true)==NONE,"unconfirmed native ownership never fires");
            s=session();ready(s,left);check(s.end(7,x,1390,150,true)==NONE,"retreat cancels return");
            s=session();ready(s,left);s.cancel();check(s.end(7,end,1390,150,true)==NONE,"cancel after ownership");
            s=session();s.begin(7,x,1390,100,true);
            check(s.move(7,1,x,1180,120,true)==NONE,"vertical scroll not claimed");
            check(s.move(7,1,end,1390,140,true)==NONE,"vertical scroll cannot be stolen later");
            s=session();s.begin(7,x,1390,100,true);
            check(s.move(7,2,end,1390,120,true)==NONE,"multitouch not claimed");
            check(s.move(7,1,end,1390,130,true)==NONE,"lifting second finger does not resume navigation");
            s=session();ready(s,left);s.move(7,2,end,1390,130,true);
            check(s.end(7,end,1390,150,true)==NONE,"multitouch after claim cancels action");
            s=session();ready(s,left);check(s.end(8,end,1390,150,true)==NONE,"wrong pointer release");
            s=session();ready(s,left);check(s.end(7,end,1390,110,true)==NONE,"out of order release");
            s=session();ready(s,left);check(s.end(7,end,1390,150,false)==NONE,"lock or geometry change cancels action");
            s=session();ready(s,left);check(s.end(7,Float.NaN,1390,150,true)==NONE,"invalid coordinates");
            s=session();ready(s,left);s.reset();check(s.end(7,end,1390,150,true)==NONE,"stopped session cannot fire");
        }
        EdgeInputSession s=session();
        for(float[] point:new float[][]{{200,1390},{1000,1390},{20,100},{20,2600},{-1,1390},{1264,1390},{Float.NaN,1390}})
            check(!s.begin(1,point[0],point[1],0,true),"outside eligible origin");
        check(!s.begin(1,20,1390,0,false),"disabled input cannot start");
        for(int width:new int[]{320,720,1264,2780}){
            for(int start=1;start<90;start+=7){
                EdgeInputSession sample=new EdgeInputSession(width,3000,100,100,2900,80);
                sample.begin(1,start,1000,10,true);sample.move(1,1,start+40,1002,20,true);
                check(sample.end(1,start+40,1002,30,true)==NONE,"subthreshold paths preserve the app");
            }
        }
        System.out.println("PASS "+checks+" input ownership assertions");
    }
}
