package replica;

/** Runs the production failure policy without Android, Root, or device services. */
public final class NativeBlurPolicyCheck {
    private static int checks;
    private static void check(boolean result,String label){if(!result)throw new AssertionError(label);checks++;System.out.println("PASS "+label);}
    public static void main(String[] args){
        NativeBlurPolicy first=new NativeBlurPolicy();
        check(first.available(),"first use may attempt native capability discovery");
        check(!first.failed(false),"a failed initial binding does not loop");
        check(!first.available(),"initial failure keeps later panels on fallback");
        first.connected();
        check(!first.available(),"a late startup callback cannot revive the failed session");

        NativeBlurPolicy denied=new NativeBlurPolicy();
        check(!denied.failed(true),"missing service or denied Root is terminal for this process");
        check(!denied.failed(false),"repeated ensure after terminal failure cannot consume fresh retries");

        NativeBlurPolicy recovered=new NativeBlurPolicy();
        recovered.connected();
        check(recovered.failed(false),"a previously connected service may recover from one death");
        check(recovered.available(),"first recovery retains native availability");
        recovered.connected();
        check(recovered.failed(false),"a second service death still has one recovery attempt");
        recovered.connected();
        check(!recovered.failed(false),"repeated connect/death cycles are bounded after two recoveries");
        check(!recovered.available(),"exhausted recovery switches future panels to fallback");

        NativeBlurPolicy changedPermission=new NativeBlurPolicy();
        changedPermission.connected();
        check(!changedPermission.failed(true),"Root rejection during recovery stops immediately");

        NativeBlurPolicy idle=new NativeBlurPolicy();
        for(int i=0;i<100;i++)idle.connected();
        check(idle.available(),"healthy starts and idle exits do not disable the native path");
        check(idle.failed(false)&&idle.failed(false),"healthy connections do not spend the two recovery attempts");
        check(!idle.failed(false),"the same bound still applies after many healthy uses");
        System.out.println("RESULT "+checks+" native blur policy assertions passed");
    }
}
