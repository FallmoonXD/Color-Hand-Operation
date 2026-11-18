package replica;

/** Runs the production failure policy without Android, Root, or device services. */
public final class NativeBlurPolicyCheck {
    private static int checks;
    private static void check(boolean result,String label){if(!result)throw new AssertionError(label);checks++;System.out.println("PASS "+label);}
    public static void main(String[] args){
        NativeBlurPolicy first=new NativeBlurPolicy();
        check(first.available(),"first use can check the native capability");
        int attempt=first.authorization();
        check(!first.startupFailed(attempt)&&!first.available(),"a failed initial helper waits for authorization");
        first.connected();
        check(!first.available(),"a late callback cannot restore a blocked helper");
        first.rootAuthorized();
        check(first.available(),"verified authorization restores startup in the same process");
        attempt=first.authorization();
        check(!first.startupFailed(attempt)&&!first.available(),"one grant cannot create an automatic restart loop");

        NativeBlurPolicy race=new NativeBlurPolicy();
        attempt=race.authorization();
        race.rootAuthorized();
        check(race.startupFailed(attempt)&&race.available(),"grant during an old attempt permits one fresh attempt");
        check(!race.startupFailed(race.authorization())&&!race.available(),"failure of the fresh attempt blocks again");

        NativeBlurPolicy missing=new NativeBlurPolicy();
        missing.unsupported();missing.rootAuthorized();missing.connected();
        check(!missing.available(),"authorization cannot create a missing system service");

        NativeBlurPolicy recovered=new NativeBlurPolicy();
        recovered.connected();
        check(recovered.serviceLost(),"a connected service can recover from one death");
        recovered.connected();recovered.rootAuthorized();
        check(recovered.serviceLost(),"a second service death still has one recovery attempt");
        recovered.connected();
        check(!recovered.serviceLost()&&!recovered.available(),"repeated connect/death cycles are bounded");
        recovered.rootAuthorized();
        check(!recovered.available(),"new authorization cannot reset the death budget");

        NativeBlurPolicy idle=new NativeBlurPolicy();
        for(int i=0;i<100;i++)idle.connected();
        check(idle.available(),"healthy starts and idle exits preserve the native path");
        check(idle.serviceLost()&&idle.serviceLost()&&!idle.serviceLost(),"idle exits do not spend recovery attempts");
        System.out.println("RESULT "+checks+" native blur policy assertions passed");
    }
}
