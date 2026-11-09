package replica;

import android.media.session.MediaSession;
import android.media.session.PlaybackState;
import android.os.*;
import android.view.KeyEvent;

/** Read only the current media-key session's advertised single-tap command. */
final class MediaKeyTarget {
    private static final int TOGGLE=KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE;
    static int currentKey(){
        try{
            IBinder service=(IBinder)Class.forName("android.os.ServiceManager").getMethod("getService",String.class).invoke(null,"media_session");
            Object manager=Class.forName("android.media.session.ISessionManager$Stub").getMethod("asInterface",IBinder.class).invoke(null,service);
            MediaSession.Token token=(MediaSession.Token)Class.forName("android.media.session.ISessionManager")
                    .getMethod("getMediaKeyEventSession",String.class).invoke(manager,"com.android.shell");
            if(token==null)return TOGGLE;
            Object controller=MediaSession.Token.class.getMethod("getBinder").invoke(token);
            PlaybackState state=(PlaybackState)Class.forName("android.media.session.ISessionController").getMethod("getPlaybackState").invoke(controller);
            if(state==null)return TOGGLE;
            switch(state.getState()){
                case PlaybackState.STATE_PLAYING:case PlaybackState.STATE_BUFFERING:case PlaybackState.STATE_CONNECTING:
                    return (state.getActions()&PlaybackState.ACTION_PAUSE)!=0?KeyEvent.KEYCODE_MEDIA_PAUSE:TOGGLE;
                case PlaybackState.STATE_PAUSED:case PlaybackState.STATE_STOPPED:case PlaybackState.STATE_NONE:
                    return (state.getActions()&PlaybackState.ACTION_PLAY)!=0?KeyEvent.KEYCODE_MEDIA_PLAY:TOGGLE;
                default:return TOGGLE;
            }
        }catch(ReflectiveOperationException|RuntimeException|LinkageError unavailable){return TOGGLE;}
    }
}
