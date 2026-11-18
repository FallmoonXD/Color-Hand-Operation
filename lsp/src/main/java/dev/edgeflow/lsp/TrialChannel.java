package dev.edgeflow.lsp;
import android.os.*;

/** Activity-owned observation mode. Leaving its task or losing its process ends the trial. */
final class TrialChannel {
    private final Handler main;private IBinder owner;private IBinder.DeathRecipient death;private ResultReceiver reply;private String focus,application;private long generation;
    TrialChannel(Handler main){this.main=main;}
    void start(IBinder token,String app,ResultReceiver receiver,SystemScene.Focus current)throws RemoteException{
        if(token==null||receiver==null||!current.trialPage())throw new IllegalArgumentException("Trial requires CHO settings");
        if(app!=null&&!app.matches("[A-Za-z0-9_]+(\\.[A-Za-z0-9_]+)+"))throw new IllegalArgumentException("Invalid trial application");
        if(token!=owner||!current.key().equals(focus)){
            clear();
            IBinder.DeathRecipient recipient=()->main.post(()->stop(token));
            token.linkToDeath(recipient,0);
            owner=token;application=app;focus=current.key();death=recipient;
            generation=SystemClock.elapsedRealtimeNanos();
        }
        reply=receiver;
        Bundle ready=new Bundle();ready.putLong("trial_generation",generation);ready.putString("trial_event","ready");send(token,ready);
    }
    boolean active(SystemScene.Focus current){if(owner!=null&&!current.key().equals(focus))clear();return owner!=null;}
    long generation(){return owner==null?0:generation;}
    IBinder owner(){return owner;}
    String application(){return application==null?"":application;}
    void stop(IBinder token){if(token==owner)clear();}
    void clear(){if(owner!=null&&death!=null)owner.unlinkToDeath(death,0);owner=null;reply=null;death=null;focus=null;application=null;}
    void send(IBinder token,Bundle data){if(token!=null&&token==owner&&reply!=null)reply.send(0,data);}
    void event(IBinder token,String event,String handle){Bundle data=new Bundle();data.putString("trial_event",event);data.putString("handle",handle);send(token,data);}
}
