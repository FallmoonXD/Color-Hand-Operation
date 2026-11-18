package dev.edgeflow;
import java.io.*;
import java.util.concurrent.TimeUnit;

public final class RootAuthorizationCheck {
    static int checks;
    static void ok(boolean value,String message){checks++;if(!value)throw new AssertionError(message);}
    static final class Child extends Process {
        final InputStream output;final int exit;final boolean completes;boolean destroyed,killed;
        Child(String output,int exit,boolean completes){this.output=new ByteArrayInputStream(output.getBytes(java.nio.charset.StandardCharsets.UTF_8));this.exit=exit;this.completes=completes;}
        public OutputStream getOutputStream(){return OutputStream.nullOutputStream();}
        public InputStream getInputStream(){return output;}
        public InputStream getErrorStream(){return InputStream.nullInputStream();}
        public int waitFor(){return exit;}
        public boolean waitFor(long timeout,TimeUnit unit){ok(unit.toMillis(timeout)==123,"probe uses its bounded timeout");return completes;}
        public int exitValue(){return exit;}
        public void destroy(){destroyed=true;}
        public Process destroyForcibly(){killed=true;return this;}
    }
    public static void main(String[] args){
        for(String text:new String[]{"0\n"," 0\r\n"}){Child p=new Child(text,0,true);ok(RootAuthorization.check(()->p,123)==RootAuthorization.Result.GRANTED,"verified root UID");ok(p.destroyed,"successful child released");}
        for(String text:new String[]{"2000\n","uid=0(root)","Permission denied",""}){Child p=new Child(text,0,true);ok(RootAuthorization.check(()->p,123)==RootAuthorization.Result.DENIED,"non-root or malformed result rejected");ok(p.destroyed,"rejected child released");}
        Child failed=new Child("0\n",1,true);ok(RootAuthorization.check(()->failed,123)==RootAuthorization.Result.DENIED,"nonzero exit cannot grant authorization");
        Child pending=new Child("",0,false);ok(RootAuthorization.check(()->pending,123)==RootAuthorization.Result.TIMEOUT&&pending.killed&&pending.destroyed,"unanswered authorization is bounded and cleaned");
        ok(RootAuthorization.check(()->{throw new IOException("missing su");},123)==RootAuthorization.Result.UNAVAILABLE,"missing su is distinct from denied authorization");
        System.out.println("PASS "+checks+" Root authorization assertions");
    }
}
