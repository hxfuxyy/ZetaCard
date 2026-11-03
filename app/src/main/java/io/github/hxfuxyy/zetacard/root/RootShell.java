package io.github.hxfuxyy.zetacard.root;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.InputStream;
import java.util.concurrent.FutureTask;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

public final class RootShell {
    public static final long DEFAULT_TIMEOUT_MS = 15_000L;
    public static final long GRANT_TIMEOUT_MS = 60_000L;
    public static final int MAX_OUTPUT_BYTES = 16 * 1024 * 1024;
    private static final String[] SU_PATHS = {"su", "/system/bin/su", "/system/xbin/su", "/debug_ramdisk/su"};

    private static volatile String[] shell;

    private RootShell() { }

    public static synchronized boolean probe() {
        shell = null;
        return resolve() != null;
    }

    public static boolean isAvailable() {
        return resolve() != null;
    }

    public static byte[] run(String command) {
        String[] prefix = resolve();
        return prefix == null ? null : exec(prefix, command, DEFAULT_TIMEOUT_MS);
    }

    private static synchronized String[] resolve() {
        String[] known = shell;
        if (known != null) return known;
        for (String su : SU_PATHS) {
            for (String[] candidate : new String[][]{{su, "-M", "-c"}, {su, "-c"}}) {
                byte[] output = exec(candidate, "id -u", GRANT_TIMEOUT_MS);
                if (output != null && "0".equals(new String(output).trim())) {
                    shell = candidate;
                    return candidate;
                }
            }
        }
        return null;
    }

    static byte[] exec(String[] prefix, String command, long timeoutMs) {
        String[] argv = new String[prefix.length + 1];
        System.arraycopy(prefix, 0, argv, 0, prefix.length);
        argv[prefix.length] = command;
        Process process = null;
        try {
            process = new ProcessBuilder(argv).redirectError(new File("/dev/null")).start();
            process.getOutputStream().close();
            InputStream input = process.getInputStream();
            FutureTask<byte[]> reader = new FutureTask<>(() -> readAll(input));
            Thread thread = new Thread(reader, "ZetaCard root reader");
            thread.setDaemon(true);
            thread.start();
            long deadline = System.nanoTime() + TimeUnit.MILLISECONDS.toNanos(timeoutMs);
            byte[] output;
            try {
                output = reader.get(timeoutMs, TimeUnit.MILLISECONDS);
            } catch (TimeoutException timeout) {
                return null;
            }
            long remaining = Math.max(1L, TimeUnit.NANOSECONDS.toMillis(deadline - System.nanoTime()));
            if (output == null || !process.waitFor(remaining, TimeUnit.MILLISECONDS)) return null;
            return process.exitValue() == 0 ? output : null;
        } catch (Exception ignored) {
            return null;
        } finally {
            if (process != null) {
                process.destroyForcibly();
                try { process.getInputStream().close(); } catch (Exception ignored) { }
            }
        }
    }

    private static byte[] readAll(InputStream input) throws java.io.IOException {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        byte[] buffer = new byte[8192];
        int size;
        while ((size = input.read(buffer)) != -1) {
            if (out.size() + size > MAX_OUTPUT_BYTES) return null;
            out.write(buffer, 0, size);
        }
        return out.toByteArray();
    }
}
