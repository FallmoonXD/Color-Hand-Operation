package dev.edgeflow;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.FutureTask;
import java.util.concurrent.TimeUnit;
import java.util.function.BooleanSupplier;

/** Exercises the real process/pipe lifecycle. The child writes only to its test directory. */
public final class PendingRootCommandCheck {
    public static void main(String[] args) throws Exception {
        if (args[0].equals("child")) { child(Path.of(args[1])); return; }
        Path base = Files.createTempDirectory(Path.of(args[0]), "root-command-");

        Path accepted = Files.createDirectory(base.resolve("accepted"));
        Files.createFile(accepted.resolve("grant"));
        require(command(accepted, 5000).run(() -> true) && Files.exists(accepted.resolve("executed")), "confirmed target executes");

        Path changed = Files.createDirectory(base.resolve("changed"));
        Files.createFile(changed.resolve("grant"));
        require(!command(changed, 5000).run(() -> false) && !Files.exists(changed.resolve("executed")), "changed target is refused");

        Path waiting = Files.createDirectory(base.resolve("waiting"));
        PendingRootCommand cancelled = command(waiting, 5000);
        FutureTask<Boolean> result = async(cancelled, () -> true);
        waitForFile(waiting.resolve("started"));
        cancelled.cancel();
        Files.createFile(waiting.resolve("grant"));
        require(!result.get(3, TimeUnit.SECONDS) && !Files.exists(waiting.resolve("executed")), "late authorization cannot run a cancelled request");

        Path expired = Files.createDirectory(base.resolve("timeout"));
        try {
            command(expired, 450).run(() -> true);
            throw new AssertionError("authorization timeout expected");
        } catch (IOException expected) {
            require(!Files.exists(expired.resolve("executed")), "timeout leaves the command unexecuted");
        }
        System.out.println("PASS bounded root process, live target check, cancellation and timeout");
    }

    private static PendingRootCommand command(Path dir, int timeout) {
        return new PendingRootCommand("unused", timeout, script -> new ProcessBuilder(
                Path.of(System.getProperty("java.home"), "bin", "java").toString(), "-cp", System.getProperty("java.class.path"),
                PendingRootCommandCheck.class.getName(), "child", dir.toString()).redirectErrorStream(true).start());
    }

    private static FutureTask<Boolean> async(PendingRootCommand command, BooleanSupplier gate) {
        FutureTask<Boolean> result = new FutureTask<>(() -> command.run(gate));
        new Thread(result).start();
        return result;
    }

    private static void waitForFile(Path path) throws Exception {
        long end = System.nanoTime() + TimeUnit.SECONDS.toNanos(4);
        while (!Files.exists(path) && System.nanoTime() < end) Thread.sleep(10);
        require(Files.exists(path), "child started");
    }

    private static void child(Path dir) throws Exception {
        Files.createFile(dir.resolve("started"));
        waitForFile(dir.resolve("grant"));
        System.out.println("CHO_READY");
        System.out.flush();
        if ("run".equals(new BufferedReader(new InputStreamReader(System.in)).readLine())) Files.createFile(dir.resolve("executed"));
    }

    private static void require(boolean condition, String name) { if (!condition) throw new AssertionError(name); }
}
