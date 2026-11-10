package io.github.hxfuxyy.zetacard.root;

import android.content.Context;
import android.content.pm.PackageManager;
import android.graphics.BitmapFactory;
import android.util.Base64;
import io.github.hxfuxyy.zetacard.core.CardIds;
import io.github.hxfuxyy.zetacard.core.CardRegistry;
import io.github.hxfuxyy.zetacard.core.ImageLimits;
import io.github.hxfuxyy.zetacard.core.StockUrlPolicy;
import io.github.hxfuxyy.zetacard.data.ArtStore;
import io.github.hxfuxyy.zetacard.logging.DiagnosticsLog;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.List;
import javax.net.ssl.HttpsURLConnection;

public final class RootBridge {
    public static final String WALLET_PACKAGE = "com.google.android.apps.walletnfcrel";
    private static final java.util.regex.Pattern SAFE_COMPONENT = java.util.regex.Pattern.compile("[A-Za-z0-9._$]+/[A-Za-z0-9._$]+");
    private static final long SCAN_POLL_MS = 350L;
    private static final long SCAN_SETTLE_MS = 700L;
    private static final java.util.regex.Pattern SAFE_PATH = java.util.regex.Pattern.compile("/[A-Za-z0-9._/-]+");
    private static final int NETWORK_TIMEOUT_MS = 8000;
    private static final java.util.Set<String> missingStock = java.util.concurrent.ConcurrentHashMap.newKeySet();
    private static volatile boolean loggedNoRoot;
    private static volatile boolean loggedNoCards;

    private RootBridge() { }

    public static synchronized int importCards(Context context) {
        if (!RootShell.isAvailable()) {
            if (!loggedNoRoot) DiagnosticsLog.warning(context, "Root unavailable: grant ZetaCard root in your root manager");
            loggedNoRoot = true;
            return 0;
        }
        loggedNoRoot = false;
        String files = walletFiles(context);
        if (files == null) {
            DiagnosticsLog.warning(context, "Google Wallet is not installed for this user");
            return 0;
        }
        String registry = text(RootShell.run("cat " + files + "/zetacard_cards.tsv"));
        if (registry == null) {
            if (!loggedNoCards) DiagnosticsLog.info(context, "No card list yet: open Google Wallet and view your cards once");
            loggedNoCards = true;
            return 0;
        }
        List<CardRegistry.Entry> entries = CardRegistry.parse(registry);
        int added = ArtStore.addCards(context, entries);
        for (CardRegistry.Entry entry : entries) ensureStock(context, files, entry.id, entry.stockUrl);
        if (added > 0) DiagnosticsLog.info(context, "Imported " + added + " card(s) from Wallet via root");
        return added;
    }

    public static String walletEvents(Context context) {
        String files = walletFiles(context);
        return files == null ? null : text(RootShell.run("tail -n 200 " + files + "/zetacard_events.log"));
    }

    public static String rootVersion() {
        if (!RootShell.isAvailable()) return null;
        return text(RootShell.run("su -v 2>/dev/null || echo unknown"));
    }

    public static boolean scanWallet(Context context, long timeoutMs) {
        if (!RootShell.isAvailable()) return false;
        String files = walletFiles(context);
        int uid = walletUid(context);
        android.content.Intent launch = context.getPackageManager().getLaunchIntentForPackage(WALLET_PACKAGE);
        if (files == null || uid < 0 || launch == null || launch.getComponent() == null) return false;
        String wallet = launch.getComponent().flattenToString();
        String self = new android.content.ComponentName(context, "io.github.hxfuxyy.zetacard.MainActivity").flattenToString();
        if (!SAFE_COMPONENT.matcher(wallet).matches() || !SAFE_COMPONENT.matcher(self).matches()) return false;
        long polls = Math.max(1, timeoutMs / SCAN_POLL_MS);
        String script = "probe() { cat " + files + "/zetacard_cards.tsv " + files + "/zetacard_heartbeat 2>/dev/null | md5sum; }; " +
                "before=$(probe); " + killWallet(uid) +
                "am start -n " + wallet + " -f 0x10000000 >/dev/null 2>&1 || { echo started=0; exit 0; }; " +
                "seen=0; i=0; while [ $i -lt " + polls + " ]; do sleep " + seconds(SCAN_POLL_MS) + "; " +
                "if [ \"$(probe)\" != \"$before\" ]; then seen=1; sleep " + seconds(SCAN_SETTLE_MS) + "; break; fi; i=$((i+1)); done; " +
                "am start -n " + self + " -f 0x10020000 >/dev/null 2>&1; echo seen=$seen";
        String result = text(RootShell.run(script, timeoutMs + 8000L));
        return result != null && result.contains("seen=1");
    }

    private static String seconds(long millis) {
        return String.format(java.util.Locale.US, "%.2f", millis / 1000.0);
    }

    private static int walletUid(Context context) {
        try {
            return context.getPackageManager().getApplicationInfo(WALLET_PACKAGE, 0).uid;
        } catch (PackageManager.NameNotFoundException ignored) {
            return -1;
        }
    }

    private static String killWallet(int uid) {
        return "for p in $(pidof " + WALLET_PACKAGE + "); do " +
                "if [ \"$(stat -c %u /proc/$p)\" = \"" + uid + "\" ]; then kill -9 $p; fi; done; ";
    }

    public static String walletHookCache(Context context) {
        String data = dataDirectory(context, WALLET_PACKAGE);
        if (data == null) return null;
        return text(RootShell.run("cat " + data + "/shared_prefs/zetacard_hook_cache.xml"));
    }

    public static boolean restartWallet(Context context) {
        int uid = walletUid(context);
        if (uid < 0) return false;
        String command = "killed=0; for p in $(pidof " + WALLET_PACKAGE + "); do " +
                "if [ \"$(stat -c %u /proc/$p)\" = \"" + uid + "\" ]; then kill -9 $p && killed=1; fi; done; [ $killed = 1 ]";
        return RootShell.run(command) != null;
    }

    public static String dataDirectory(Context context, String packageName) {
        try {
            String path = context.getPackageManager().getApplicationInfo(packageName, 0).dataDir;
            return path != null && SAFE_PATH.matcher(path).matches() && !path.contains("..") ? path : null;
        } catch (PackageManager.NameNotFoundException ignored) {
            return null;
        }
    }

    private static String walletFiles(Context context) {
        String data = dataDirectory(context, WALLET_PACKAGE);
        return data == null ? null : data + "/files";
    }

    public static boolean checkRoot() {
        return RootShell.probe();
    }

    public static void retryMissingStock() {
        missingStock.clear();
    }

    public static void resetLogState() {
        loggedNoRoot = false;
        loggedNoCards = false;
        missingStock.clear();
    }

    private static void ensureStock(Context context, String files, String id, String stockUrl) {
        if (!CardIds.isValid(id)) return;
        File target = ArtStore.stockFile(context, id);
        if (target.isFile() || missingStock.contains(id)) return;
        byte[] image = StockUrlPolicy.isAllowed(stockUrl) ? download(stockUrl) : null;
        if (image == null) image = readWalletStock(files, id);
        if (!isUsableImage(image)) {
            missingStock.add(id);
            return;
        }
        File parent = target.getParentFile();
        if (parent == null || (!parent.isDirectory() && !parent.mkdirs())) return;
        File temp = new File(parent, id + ".tmp");
        try {
            try (FileOutputStream output = new FileOutputStream(temp)) { output.write(image); }
            if (!temp.renameTo(target)) temp.delete();
        } catch (Exception ignored) {
            temp.delete();
        }
    }

    private static byte[] readWalletStock(String files, String id) {
        byte[] encoded = RootShell.run("base64 " + files + "/zetacard_stock_" + id + ".png");
        if (encoded == null || encoded.length == 0) return null;
        try {
            return Base64.decode(encoded, Base64.DEFAULT);
        } catch (IllegalArgumentException ignored) {
            return null;
        }
    }

    private static boolean isUsableImage(byte[] image) {
        if (image == null || image.length == 0 || image.length > StockUrlPolicy.MAX_BYTES) return false;
        BitmapFactory.Options bounds = new BitmapFactory.Options();
        bounds.inJustDecodeBounds = true;
        BitmapFactory.decodeByteArray(image, 0, image.length, bounds);
        return bounds.outWidth > 0 && bounds.outHeight > 0 &&
                bounds.outWidth <= ImageLimits.MAX_STOCK_SIDE && bounds.outHeight <= ImageLimits.MAX_STOCK_SIDE;
    }

    private static byte[] download(String stockUrl) {
        HttpsURLConnection connection = null;
        try {
            connection = (HttpsURLConnection) new URL(stockUrl).openConnection();
            connection.setConnectTimeout(NETWORK_TIMEOUT_MS);
            connection.setReadTimeout(NETWORK_TIMEOUT_MS);
            connection.setInstanceFollowRedirects(false);
            connection.setUseCaches(false);
            if (connection.getResponseCode() != 200 || connection.getContentLengthLong() > StockUrlPolicy.MAX_BYTES) return null;
            String type = connection.getContentType();
            if (type != null && !type.startsWith("image/")) return null;
            ByteArrayOutputStream bytes = new ByteArrayOutputStream();
            try (InputStream input = connection.getInputStream()) {
                byte[] buffer = new byte[8192];
                int size;
                while ((size = input.read(buffer)) != -1) {
                    if (bytes.size() + size > StockUrlPolicy.MAX_BYTES) return null;
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

    private static String text(byte[] raw) {
        if (raw == null) return null;
        String value = new String(raw, StandardCharsets.UTF_8).trim();
        return value.isEmpty() ? null : value;
    }
}
