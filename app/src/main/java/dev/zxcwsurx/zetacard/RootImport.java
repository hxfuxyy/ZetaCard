package dev.zxcwsurx.zetacard;

import android.content.Context;
import android.content.SharedPreferences;
import android.graphics.BitmapFactory;
import android.net.Uri;
import android.util.Base64;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.net.URL;
import java.util.Collections;
import java.util.HashSet;
import java.util.Set;
import javax.net.ssl.HttpsURLConnection;

public final class RootImport {
    private static final String WALLET_FILES = "/data/data/com.google.android.apps.walletnfcrel/files";
    private static final String REGISTRY = WALLET_FILES + "/zetacard_cards.tsv";

    private RootImport() { }

    public static synchronized int sync(Context context) {
        if (!rootAvailable()) {
            DiagnosticsLog.warning(context, "Root unavailable: grant ZetaCard root in your root manager (KernelSU/Magisk)");
            return 0;
        }
        byte[] raw = run("cat " + REGISTRY);
        if (raw == null || raw.length == 0) {
            DiagnosticsLog.info(context, "Root OK but no card list yet: open Google Wallet and view your cards once");
            return 0;
        }
        SharedPreferences prefs = ArtProvider.prefs(context);
        Set<String> ids = new HashSet<>(prefs.getStringSet("ids", Collections.emptySet()));
        SharedPreferences.Editor editor = prefs.edit();
        int added = 0;
        for (String line : new String(raw, java.nio.charset.StandardCharsets.UTF_8).split("\n")) {
            if (line.isEmpty()) continue;
            String[] parts = line.split("\t", -1);
            String id = parts[0].trim();
            if (!ArtProvider.valid(id)) continue;
            String label = parts.length > 1 ? parts[1].trim() : "";
            String stockUrl = parts.length > 2 ? parts[2].trim() : "";
            if (ids.add(id)) added++;
            if (label.isEmpty() || label.length() > 60 || label.matches(".*[0-9]{8,}.*")) label = "Payment card";
            if (!prefs.contains("name." + id)) editor.putString("name." + id, label);
            ensureStock(context, id, stockUrl);
        }
        editor.putStringSet("ids", ids).apply();
        if (added > 0) DiagnosticsLog.info(context, "Imported " + added + " card(s) from Wallet via root");
        return added;
    }

    private static void ensureStock(Context context, String id, String stockUrl) {
        File target = ArtProvider.stockFile(context, id);
        if (target.isFile()) return;
        byte[] image = null;
        if (!stockUrl.isEmpty()) image = download(stockUrl);
        if (image == null) {
            byte[] encoded = run("base64 " + WALLET_FILES + "/zetacard_stock_" + id + ".png");
            if (encoded != null && encoded.length > 0) {
                try { image = Base64.decode(encoded, Base64.DEFAULT); } catch (IllegalArgumentException ignored) { }
            }
        }
        if (image == null || image.length == 0 || image.length > 2_000_000) return;
        if (BitmapFactory.decodeByteArray(image, 0, image.length) == null) return;
        try {
            target.getParentFile().mkdirs();
            try (FileOutputStream output = new FileOutputStream(target)) { output.write(image); }
        } catch (Exception ignored) { }
    }

    private static byte[] download(String stockUrl) {
        Uri source = Uri.parse(stockUrl);
        String host = source.getHost();
        if (!"https".equals(source.getScheme()) || host == null ||
                !(host.equals("googleusercontent.com") || host.endsWith(".googleusercontent.com"))) return null;
        HttpsURLConnection connection = null;
        try {
            connection = (HttpsURLConnection) new URL(stockUrl).openConnection();
            connection.setConnectTimeout(8000);
            connection.setReadTimeout(8000);
            connection.setInstanceFollowRedirects(false);
            if (connection.getResponseCode() != 200 || connection.getContentLengthLong() > 2_000_000) return null;
            ByteArrayOutputStream bytes = new ByteArrayOutputStream();
            try (InputStream input = connection.getInputStream()) {
                byte[] buffer = new byte[8192];
                int size;
                while ((size = input.read(buffer)) != -1) {
                    if (bytes.size() + size > 2_000_000) return null;
                    bytes.write(buffer, 0, size);
                }
            }
            return bytes.toByteArray();
        } catch (Exception ignored) {
            return null;
        } finally {
            if (connection != null) connection.disconnect();
        }
    }

    public static boolean hasRoot() {
        return rootAvailable();
    }

    private static boolean rootAvailable() {
        byte[] out = run("id -u");
        if (out == null) return false;
        return new String(out, java.nio.charset.StandardCharsets.UTF_8).trim().startsWith("0");
    }

    private static final String[] SU_PATHS = {"su", "/system/bin/su", "/system/xbin/su", "/debug_ramdisk/su"};

    private static byte[] run(String command) {
        for (String su : SU_PATHS) {
            byte[] result = exec(new String[]{su, "-M", "-c", command});
            if (result != null) return result;
            result = exec(new String[]{su, "-c", command});
            if (result != null) return result;
        }
        return null;
    }

    private static byte[] exec(String[] argv) {
        Process process = null;
        try {
            process = new ProcessBuilder(argv).redirectErrorStream(false).start();
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            try (InputStream in = process.getInputStream()) {
                byte[] buffer = new byte[8192];
                int size;
                while ((size = in.read(buffer)) != -1) out.write(buffer, 0, size);
            }
            process.waitFor();
            return process.exitValue() == 0 ? out.toByteArray() : null;
        } catch (Exception ignored) {
            return null;
        } finally {
            if (process != null) process.destroy();
        }
    }
}
