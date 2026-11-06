package android.util;
import java.io.*;import java.nio.file.*;
/** Local I/O double: same file-based call contract; not an Android AtomicFile implementation test. */
public class AtomicFile {
 public static boolean failNextWrite;private final File file,temp;
 public AtomicFile(File f){file=f;temp=new File(f.getPath()+".new");}
 public byte[] readFully()throws IOException{if(!file.isFile())throw new FileNotFoundException(file.toString());return Files.readAllBytes(file.toPath());}
 public FileOutputStream startWrite()throws IOException{if(failNextWrite){failNextWrite=false;throw new IOException("injected tool write failure");}file.getParentFile().mkdirs();return new FileOutputStream(temp);}
 public void finishWrite(FileOutputStream out){try{out.close();Files.move(temp.toPath(),file.toPath(),StandardCopyOption.REPLACE_EXISTING);}catch(IOException e){throw new RuntimeException(e);}}
 public void failWrite(FileOutputStream out){try{out.close();}catch(IOException ignored){}temp.delete();}
}