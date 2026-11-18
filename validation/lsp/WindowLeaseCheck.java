package dev.edgeflow.lsp;

import java.util.*;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicReference;

public final class WindowLeaseCheck {
    static int checks;
    static void check(boolean value,String name){checks++;if(!value)throw new AssertionError(name);}
    static final class End implements WindowLeaseStore.Endpoint {
        boolean alive=true,failLink,dieOnLink;
        final List<Runnable> callbacks=new ArrayList<>();
        public synchronized void link(Runnable death)throws Exception{
            if(failLink||!alive)throw new Exception("dead");callbacks.add(death);
            if(dieOnLink)die();
        }
        public synchronized void unlink(Runnable death){callbacks.remove(death);}
        public synchronized boolean alive(){return alive;}
        void die(){List<Runnable> pending;synchronized(this){alive=false;pending=List.copyOf(callbacks);}pending.forEach(Runnable::run);}
    }
    static final class Windows implements WindowLeaseStore.Windows<Integer> {
        final Set<Integer> visible=new HashSet<>();int sequence,removes;
        boolean failAdd,failRemove;Runnable whileAdding;
        public Integer createToken(){return ++sequence;}
        public void add(Integer token)throws Exception{visible.add(token);if(whileAdding!=null)whileAdding.run();if(failAdd)throw new Exception("add");}
        public void remove(Integer token)throws Exception{if(failRemove)throw new Exception("remove");removes++;visible.remove(token);}
    }
    interface Attempt {void run()throws Exception;}
    static void rejected(Attempt action,String name)throws Exception{boolean rejected=false;try{action.run();}catch(Exception expected){rejected=true;}check(rejected,name);}
    public static void main(String[] args)throws Exception {
        Windows windows=new Windows();WindowLeaseStore<String,Integer> store=new WindowLeaseStore<>(windows,2);
        End control=new End(),host=new End();int first=store.grant("one",control,host);
        check(windows.visible.contains(first)&&store.size()==1,"real registered token");
        check(control.callbacks.size()==1&&host.callbacks.size()==1,"both deaths linked");
        check(store.revoke("one")&&windows.visible.isEmpty(),"explicit stop removes windows");
        check(control.callbacks.isEmpty()&&host.callbacks.isEmpty(),"stop unlinks both");
        check(!store.revoke("one")&&windows.removes==1,"stop idempotent");
        for(boolean killHost:new boolean[]{true,false}){
            End c=new End(),h=new End();store.grant("one",c,h);(killHost?h:c).die();
            check(store.size()==0&&windows.visible.isEmpty(),"either death removes windows");
            check(c.callbacks.isEmpty()&&h.callbacks.isEmpty(),"death unlinks both");
        }
        End oldC=new End(),oldH=new End();int old=store.grant("one",oldC,oldH);Runnable stale=oldH.callbacks.get(0);
        End newC=new End(),newH=new End();int fresh=store.grant("one",newC,newH);stale.run();
        check(!windows.visible.contains(old)&&windows.visible.contains(fresh),"replacement revokes old token");
        check(store.size()==1,"late old death preserves replacement");store.revoke("one");
        for(boolean second:new boolean[]{false,true}){
            End c=new End(),h=new End();(second?h:c).failLink=true;
            rejected(()->store.grant("one",c,h),"dead endpoint rejected");
            check(store.size()==0&&windows.visible.isEmpty()&&c.callbacks.isEmpty()&&h.callbacks.isEmpty(),"failed link has no leak");
        }
        End c=new End(),h=new End();windows.failAdd=true;
        rejected(()->store.grant("one",c,h),"partial registration fails");windows.failAdd=false;
        check(windows.visible.isEmpty()&&store.size()==0&&c.callbacks.isEmpty()&&h.callbacks.isEmpty(),"partial registration removed");
        for(boolean second:new boolean[]{false,true}){
            End a=new End(),b=new End();(second?b:a).dieOnLink=true;
            rejected(()->store.grant("one",a,b),"death during link");
            check(windows.visible.isEmpty()&&store.size()==0&&a.callbacks.isEmpty()&&b.callbacks.isEmpty(),"death during link cleaned");
        }
        End a=new End(),b=new End();windows.whileAdding=b::die;
        rejected(()->store.grant("one",a,b),"death during registration");windows.whileAdding=null;
        check(store.size()==0&&windows.visible.isEmpty(),"registration death has no window");
        store.grant("one",new End(),new End());store.grant("two",new End(),new End());
        rejected(()->store.grant("three",new End(),new End()),"bounded token count");
        check(store.size()==2&&windows.visible.size()==2,"limit preserves existing windows");
        check(!store.revoke("unknown")&&store.size()==2,"cannot remove unrelated token");
        windows.failRemove=true;rejected(()->store.revoke("one"),"cleanup failure reported");
        check(store.size()==2,"failed cleanup retained for retry");
        windows.failRemove=false;store.revoke("one");store.revoke("two");
        check(store.size()==0&&windows.visible.isEmpty(),"cleanup retry succeeds");
        // A Binder death arriving while WMS registers must remove the resulting window.
        End x=new End(),y=new End();CountDownLatch adding=new CountDownLatch(1),resume=new CountDownLatch(1);
        windows.whileAdding=()->{adding.countDown();try{resume.await();}catch(InterruptedException e){throw new RuntimeException(e);}};
        AtomicReference<Exception> failure=new AtomicReference<>();
        Thread grant=new Thread(()->{try{store.grant("race",x,y);}catch(Exception e){failure.set(e);}});grant.start();adding.await();
        Thread death=new Thread(y::die);death.start();
        while(y.alive())Thread.onSpinWait();resume.countDown();grant.join(2000);death.join(2000);
        check(!grant.isAlive()&&!death.isAlive(),"death and registration do not deadlock");
        check(failure.get()!=null,"dead host never receives a usable grant");
        check(store.size()==0&&windows.visible.isEmpty(),"concurrent death removes registered window");
        System.out.println("PASS "+checks+" window lease checks");
    }
}
