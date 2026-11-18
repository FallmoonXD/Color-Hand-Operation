package dev.edgeflow.lsp;

import android.content.Context;
import android.content.pm.PackageManager;
import android.hardware.input.InputManager;
import android.os.Binder;
import android.os.Bundle;
import android.os.SystemClock;
import android.os.Looper;
import android.view.InputChannel;
import android.view.InputDevice;
import android.view.InputEvent;
import android.view.KeyEvent;
import android.view.KeyCharacterMap;
import java.lang.reflect.Method;

/** Uses an actual service instance. Does not assume InputManager.getInstance exists. */
final class NativeInputApi {
    private final Context context;
    final NativePowerMenu powerMenu;
    final NativeSplitScreen splitScreen;
    final dev.edgeflow.BackendActions.Capabilities actions;
    private final Object manager;
    private final Method monitor, inject, channel, pilfer, dispose, eventDisplay;

    NativeInputApi(Context context) throws ReflectiveOperationException {
        this.context=context;
        powerMenu=NativePowerMenu.resolve(context);
        splitScreen=NativeSplitScreen.resolve(context);
        actions=new dev.edgeflow.BackendActions.Capabilities(powerMenu!=null,splitScreen!=null);
        manager=context.getSystemService(InputManager.class);
        if (manager==null) throw new IllegalStateException("InputManager unavailable");
        monitor=manager.getClass().getMethod("monitorGestureInput",String.class,int.class);
        inject=manager.getClass().getMethod("injectInputEvent",InputEvent.class,int.class);
        Class<?> type=Class.forName("android.view.InputMonitor");
        channel=type.getMethod("getInputChannel");
        pilfer=type.getMethod("pilferPointers");
        dispose=type.getMethod("dispose");
        eventDisplay=InputEvent.class.getMethod("getDisplayId");
        Class<?> receiver=Class.forName("android.view.InputEventReceiver");
        receiver.getDeclaredConstructor(InputChannel.class,Looper.class);
        receiver.getMethod("finishInputEvent",InputEvent.class,boolean.class);
        receiver.getMethod("consumeBatchedInputEvents",long.class);
        receiver.getMethod("dispose");
    }

    private boolean allowed(String permission) {
        return context.checkSelfPermission(permission)==PackageManager.PERMISSION_GRANTED;
    }
    Bundle capabilities() {
        Bundle b=new Bundle();
        b.putString("manager",manager.getClass().getName());
        b.putBoolean(dev.edgeflow.BackendActions.POWER_MENU_AVAILABLE,actions.powerMenu());
        b.putBoolean(dev.edgeflow.BackendActions.SPLIT_SCREEN_AVAILABLE,actions.splitScreen());
        b.putInt("uid",android.os.Process.myUid());
        b.putInt("pid",android.os.Process.myPid());
        b.putBoolean("monitor_permission",allowed("android.permission.MONITOR_INPUT"));
        b.putBoolean("inject_permission",allowed("android.permission.INJECT_EVENTS"));
        return b;
    }
    Object open(boolean capture) throws ReflectiveOperationException {
        if (!allowed("android.permission.MONITOR_INPUT")) throw new SecurityException("MONITOR_INPUT unavailable");
        if (capture && !allowed("android.permission.INJECT_EVENTS")) throw new SecurityException("INJECT_EVENTS unavailable");
        return monitor.invoke(manager,"CHO LSP pilot",0);
    }
    InputChannel channel(Object handle) throws ReflectiveOperationException {
        return (InputChannel)channel.invoke(handle);
    }
    void claim(Object handle) throws ReflectiveOperationException { pilfer.invoke(handle); }
    void close(Object handle) throws ReflectiveOperationException { dispose.invoke(handle); }
    int displayId(InputEvent event) throws ReflectiveOperationException { return (Integer)eventDisplay.invoke(event); }

    boolean back() throws ReflectiveOperationException {
        return key(KeyEvent.KEYCODE_BACK);
    }
    boolean key(int code) throws ReflectiveOperationException {
        long identity=Binder.clearCallingIdentity(), now=SystemClock.uptimeMillis();
        KeyEvent down=new KeyEvent(now,now,KeyEvent.ACTION_DOWN,code,0,0,
                KeyCharacterMap.VIRTUAL_KEYBOARD,0,KeyEvent.FLAG_FROM_SYSTEM,InputDevice.SOURCE_KEYBOARD);
        KeyEvent up=KeyEvent.changeAction(down,KeyEvent.ACTION_UP);
        try {
            boolean accepted=Boolean.TRUE.equals(inject.invoke(manager,down,0));
            // Always balance an accepted DOWN, even if a later failure is reported.
            boolean released=Boolean.TRUE.equals(inject.invoke(manager,up,0));
            return accepted && released;
        } finally { Binder.restoreCallingIdentity(identity); }
    }
}
