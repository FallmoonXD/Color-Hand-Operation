package dev.edgeflow;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.TimeUnit;

/** A bounded authorization probe; a present su binary or a successful exit alone is insufficient. */
final class RootAuthorization {
    enum Result { GRANTED, DENIED, UNAVAILABLE, TIMEOUT }
    interface Start {Process run() throws IOException;}
    static Result check(Start start,long timeoutMillis){
        Process process=null;
        try{
            process=start.run();
            if(!process.waitFor(timeoutMillis,TimeUnit.MILLISECONDS)){process.destroyForcibly();return Result.TIMEOUT;}
            String text=new String(process.getInputStream().readNBytes(4096),StandardCharsets.UTF_8);
            return process.exitValue()==0&&java.util.Arrays.stream(text.split("\\R")).anyMatch(s->s.trim().equals("0"))?Result.GRANTED:Result.DENIED;
        }catch(IOException unavailable){return Result.UNAVAILABLE;}
        catch(InterruptedException interrupted){Thread.currentThread().interrupt();return Result.TIMEOUT;}
        finally{if(process!=null){process.destroy();try{process.getInputStream().close();}catch(IOException ignored){}}}
    }
    private RootAuthorization(){}
}
