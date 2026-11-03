package io.github.hxfuxyy.zetacard.wallet;

import io.github.hxfuxyy.zetacard.core.LogSanitizer;
import java.io.File;
import java.io.FileWriter;
import java.nio.file.Files;
import java.text.SimpleDateFormat;
import java.util.Arrays;
import java.util.Date;
import java.util.Locale;

final class WalletEventLog {
    static final String FILE_NAME = "zetacard_events.log";
    private static final int MAX_BYTES = 64 * 1024;
    private static final int KEEP_BYTES = 32 * 1024;

    private final File file;

    WalletEventLog(File directory) {
        file = new File(directory, FILE_NAME);
    }

    synchronized void write(String level, String message) {
        try {
            if (file.length() > MAX_BYTES) trim();
            String time = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US).format(new Date());
            try (FileWriter writer = new FileWriter(file, true)) {
                writer.write(time + " " + level + " " + LogSanitizer.clean(message) + "\n");
            }
        } catch (Throwable ignored) {
        }
    }

    private void trim() throws Exception {
        byte[] all = Files.readAllBytes(file.toPath());
        int start = Math.max(0, all.length - KEEP_BYTES);
        while (start < all.length && all[start] != '\n') start++;
        Files.write(file.toPath(), Arrays.copyOfRange(all, Math.min(start + 1, all.length), all.length));
    }
}
