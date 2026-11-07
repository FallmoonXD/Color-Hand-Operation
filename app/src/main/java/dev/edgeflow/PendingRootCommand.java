package dev.edgeflow;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.BooleanSupplier;

/** A single root operation. The shell waits for approval on stdin after su is granted. */
public final class PendingRootCommand {
    interface Shell { Process start(String script) throws IOException; }

    private final Shell shell;
    private static final Shell SU = script -> new ProcessBuilder("su", "-c", script).redirectErrorStream(true).start();
    private final String script;
    private final long timeoutMillis;
    private final CountDownLatch startup = new CountDownLatch(1);
    private boolean started, cancelled;
    private Process running;

    public PendingRootCommand(String command) {
        this("printf 'CHO_READY\\n'; IFS= read -r decision && [ \"$decision\" = run ] || exit 1; " + command, 15_000, SU);
    }

    /** A Java helper can wait after its own startup, immediately before its final Binder checks. */
    static PendingRootCommand forWaitingProgram(String program) { return new PendingRootCommand(program, 15_000, SU); }

    static boolean awaitApproval() throws IOException {
        System.out.println("CHO_READY");
        System.out.flush();
        return "run".equals(new BufferedReader(new InputStreamReader(System.in, StandardCharsets.UTF_8)).readLine());
    }

    PendingRootCommand(String script, long timeoutMillis, Shell shell) {
        this.script = script;
        this.timeoutMillis = timeoutMillis;
        this.shell = shell;
    }

    public void cancel() {
        Process process;
        synchronized (this) {
            cancelled = true;
            process = running;
        }
        startup.countDown();
        if (process != null) process.destroyForcibly();
    }

    public boolean run(BooleanSupplier stillValid) throws IOException, InterruptedException {
        synchronized (this) {
            if (started) throw new IllegalStateException("Root command already started");
            started = true;
            if (cancelled) return false;
        }
        long deadline = System.nanoTime() + TimeUnit.MILLISECONDS.toNanos(timeoutMillis);
        Process process = shell.start(script);
        try {
            synchronized (this) {
                running = process;
                if (cancelled) return false;
            }
            AtomicBoolean ready = new AtomicBoolean();
            AtomicReference<String> diagnostic = new AtomicReference<>("");
            Thread reader = new Thread(() -> {
                try (BufferedReader output = new BufferedReader(new InputStreamReader(process.getInputStream(), StandardCharsets.UTF_8))) {
                    String line;
                    while ((line = output.readLine()) != null) {
                        if (line.equals("CHO_READY")) {
                            ready.set(true);
                            startup.countDown();
                        } else if (!line.isEmpty()) diagnostic.set(line.substring(0, Math.min(240, line.length())));
                    }
                } catch (IOException ignored) {
                    // EOF before the marker is handled as an authorization/startup failure.
                } finally {
                    startup.countDown();
                }
            }, "edge-root-output");
            reader.setDaemon(true);
            reader.start();
            if (!startup.await(Math.max(0, deadline - System.nanoTime()), TimeUnit.NANOSECONDS))
                throw new IOException("Root 授权超时");
            synchronized (this) { if (cancelled) return false; }
            if (!ready.get()) throw new IOException("Root 命令未就绪 " + diagnostic.get());
            if (!stillValid.getAsBoolean()) return false;
            synchronized (this) {
                if (cancelled) return false;
                if (System.nanoTime() >= deadline) throw new IOException("Root 请求超时");
                try (OutputStream input = process.getOutputStream()) {
                    input.write("run\n".getBytes(StandardCharsets.UTF_8));
                    input.flush();
                }
            }
            if (!process.waitFor(Math.max(0, deadline - System.nanoTime()), TimeUnit.NANOSECONDS))
                throw new IOException("Root 命令超时");
            synchronized (this) { if (cancelled) return false; }
            if (process.exitValue() != 0) throw new IOException("Root 命令失败 " + diagnostic.get());
            return true;
        } finally {
            synchronized (this) { running = null; }
            if (process.isAlive()) process.destroyForcibly();
            try { process.getOutputStream().close(); } catch (IOException ignored) {}
        }
    }
}
