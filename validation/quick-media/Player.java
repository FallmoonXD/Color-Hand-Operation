package dev.edgeflow.mediatest;

import android.app.Activity;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.media.AudioAttributes;
import android.media.AudioFormat;
import android.media.AudioTrack;
import android.media.session.MediaSession;
import android.media.session.PlaybackState;
import android.os.Bundle;
import android.util.Log;
import android.view.KeyEvent;
import android.widget.TextView;

/** A real local media session backed by looping silence. */
public final class Player extends Activity {
    private AudioTrack track;
    private MediaSession session;
    private TextView label;
    private boolean playing;
    private final BroadcastReceiver externalControl=new BroadcastReceiver(){
        @Override public void onReceive(Context c,Intent i){setPlaying(i.getBooleanExtra("play",false));}
    };

    @Override public void onCreate(Bundle state) {
        super.onCreate(state);
        label=new TextView(this); label.setTextSize(20); setContentView(label);
        int samples=44100;
        AudioFormat format=new AudioFormat.Builder().setSampleRate(samples)
                .setChannelMask(AudioFormat.CHANNEL_OUT_MONO).setEncoding(AudioFormat.ENCODING_PCM_16BIT).build();
        track=new AudioTrack.Builder().setAudioAttributes(new AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_MEDIA)
                .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC).build()).setAudioFormat(format)
                .setTransferMode(AudioTrack.MODE_STATIC).setBufferSizeInBytes(samples*2).build();
        track.write(new short[samples],0,samples); track.setLoopPoints(0,samples,-1);
        session=new MediaSession(this,"CHO fixture");
        session.setCallback(new MediaSession.Callback(){
            @Override public void onPlay(){setPlaying(true);}
            @Override public void onPause(){setPlaying(false);}
            @Override public void onSkipToNext(){Log.i("ChoMediaTest","NEXT");}
            @Override public void onSkipToPrevious(){Log.i("ChoMediaTest","PREVIOUS");}
            @Override public boolean onMediaButtonEvent(Intent i){
                KeyEvent event=i.getParcelableExtra(Intent.EXTRA_KEY_EVENT,KeyEvent.class);
                if(event!=null)Log.i("ChoMediaTest","KEY "+event.getKeyCode()+" "+event.getAction());
                if(!getIntent().getBooleanExtra("standard",false)&&event!=null&&event.getKeyCode()==KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE){
                    if(event.getAction()==KeyEvent.ACTION_DOWN)setPlaying(!playing);
                    return true;
                }
                return super.onMediaButtonEvent(i);
            }
        });
        session.setActive(true); setPlaying(true);
        registerReceiver(externalControl,new IntentFilter("dev.edgeflow.mediatest.STATE"),Context.RECEIVER_EXPORTED);
    }
    private void setPlaying(boolean value){
        playing=value;
        if(value)track.play();else track.pause();
        long actions=PlaybackState.ACTION_PLAY_PAUSE|PlaybackState.ACTION_SKIP_TO_NEXT|PlaybackState.ACTION_SKIP_TO_PREVIOUS;
        if(!getIntent().getBooleanExtra("toggleOnly",false))actions|=PlaybackState.ACTION_PLAY|PlaybackState.ACTION_PAUSE;
        session.setPlaybackState(new PlaybackState.Builder().setActions(actions).setState(
                value?PlaybackState.STATE_PLAYING:PlaybackState.STATE_PAUSED,0,value?1:0).build());
        String text=value?"PLAYING":"PAUSED"; label.setText(text); Log.i("ChoMediaTest",text);
    }
    @Override protected void onDestroy(){
        unregisterReceiver(externalControl);session.release();track.release();super.onDestroy();
    }
}
