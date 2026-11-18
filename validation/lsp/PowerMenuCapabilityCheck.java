package dev.edgeflow;

public final class PowerMenuCapabilityCheck {
    public static void main(String[] args){
        for(boolean power:new boolean[]{false,true})for(boolean split:new boolean[]{false,true}){
            BackendActions.Capabilities actions=new BackendActions.Capabilities(power,split);
            if(actions.nativeAction("power_menu")!=power||actions.supported("power_menu")!=power)throw new AssertionError("Power menu");
            if(actions.nativeAction("split_screen")!=split||actions.supported("split_screen")!=split)throw new AssertionError("Split screen");
            if(BackendActions.window("split_screen")||BackendActions.command("split_screen"))throw new AssertionError("Unexpected host route");
            if(!actions.supported("back")||!actions.supported("none"))throw new AssertionError("Unrelated route");
            for(String missing:new String[]{"system_magnification","screen_magnifier","screen_free_move","scroll_up","scroll_down"})
                if(actions.supported(missing))throw new AssertionError("Unavailable action offered: "+missing);
        }
        System.out.println("PASS independent power-menu and split-screen capabilities and existing routes");
    }
}
