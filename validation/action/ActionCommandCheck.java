package dev.edgeflow;

/** Host JVM assertions for shell result handling; uses config harness Android stubs. */
public final class ActionCommandCheck {
 private static int checks;
 private static void check(boolean actual,boolean expected,String name){if(actual!=expected)throw new AssertionError(name);checks++;}
 public static void main(String[] args){
  check(ActionExecutor.commandFailed(0,""),false,"silent command success");
  check(ActionExecutor.commandFailed(1,""),true,"nonzero exit");
  check(ActionExecutor.commandFailed(0,"Starting: Intent { x }\nError type 3\nError: Activity class does not exist."),true,"am failure with exit zero");
  check(ActionExecutor.commandFailed(0,"java.lang.SecurityException: Permission denial"),true,"service rejection");
  check(ActionExecutor.commandFailed(0,"Unknown command: invalid"),true,"unknown Android command");
  check(ActionExecutor.commandFailed(0,"/system/bin/sh: missing: not found"),true,"shell command absent");
  check(ActionExecutor.commandFailed(0,"TASK_STARTED 0\n"),false,"task launch acknowledged");
  check(ActionExecutor.commandFailed(0,"Result: null\n"),false,"sidebar provider normal null response");
  check(ActionExecutor.commandFailed(0,"EDGE_RECENTS=[{\"component\":\"com.example.exception/.MainActivity\"}]"),false,"exception word in a real package");
  System.out.println("PASS "+checks+" action command assertions");
 }
}
