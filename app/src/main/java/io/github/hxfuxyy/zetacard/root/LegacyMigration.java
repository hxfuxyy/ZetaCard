package io.github.hxfuxyy.zetacard.root;

import android.content.Context;
import android.content.pm.PackageManager;
import android.graphics.BitmapFactory;
import io.github.hxfuxyy.zetacard.core.CardIds;
import io.github.hxfuxyy.zetacard.core.CardLabels;
import io.github.hxfuxyy.zetacard.core.CardRegistry;
import io.github.hxfuxyy.zetacard.core.ImageLimits;
import io.github.hxfuxyy.zetacard.core.LegacyPrefs;
import io.github.hxfuxyy.zetacard.core.LogSanitizer;
import io.github.hxfuxyy.zetacard.data.ArtStore;
import io.github.hxfuxyy.zetacard.logging.DiagnosticsLog;
import java.io.File;
import java.io.FileOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public final class LegacyMigration {
    public static final String LEGACY_PACKAGE = "dev.zxcwsurx.zetacard";
    private static final String ABSENT = "__ZETACARD_ABSENT__";
    private static final int MAX_IMAGE_BYTES = 12 * 1024 * 1024;

    public enum State { NOT_NEEDED, NO_ROOT, INCOMPLETE, COMPLETE }

    public static final class Outcome {
        public final State state;
        public final int expected;
        public final int present;
        public final int copiedNow;

        Outcome(State state, int expected, int present, int copiedNow) {
            this.state = state;
            this.expected = expected;
            this.present = present;
            this.copiedNow = copiedNow;
        }
    }

    private enum Copy { COPIED, ALREADY_PRESENT, FAILED }

    private LegacyMigration() { }

    public static boolean isLegacyInstalled(Context context) {
        try {
            context.getPackageManager().getPackageInfo(LEGACY_PACKAGE, 0);
            return true;
        } catch (PackageManager.NameNotFoundException ignored) {
            return false;
        }
    }

    public static synchronized Outcome run(Context context) {
        boolean installed = isLegacyInstalled(context);
        if (ArtStore.isLegacyMigrated(context)) return new Outcome(installed ? State.COMPLETE : State.NOT_NEEDED, 0, 0, 0);
        if (!installed) {
            ArtStore.setLegacyMigrated(context);
            return new Outcome(State.NOT_NEEDED, 0, 0, 0);
        }
        String data = RootBridge.dataDirectory(context, LEGACY_PACKAGE);
        if (data == null) return incomplete(context, "old app data directory unavailable", 0, 0, 0);
        if (!RootShell.isAvailable()) return new Outcome(State.NO_ROOT, 0, 0, 0);
        try {
            String prefs = readOptional("cat", data + "/shared_prefs/art.xml");
            String listing = readOptional("ls", data + "/files/art");
            if (prefs == null || listing == null) return incomplete(context, "could not read the old app's data", 0, 0, 0);
            Map<String, CardRegistry.Entry> cards = new LinkedHashMap<>();
            for (CardRegistry.Entry entry : LegacyPrefs.parse(prefs)) cards.put(entry.id, entry);
            List<String> designs = parseListing(listing);
            for (String id : designs) {
                if (!cards.containsKey(id)) cards.put(id, new CardRegistry.Entry(id, CardLabels.DEFAULT, ""));
            }
            ArtStore.addCards(context, new ArrayList<>(cards.values()));
            int present = 0;
            int copied = 0;
            for (String id : designs) {
                Copy result = copy(data + "/files/art/" + id + ".png", ArtStore.artFile(context, id));
                if (result == Copy.COPIED) copied++;
                if (result != Copy.FAILED) present++;
            }
            for (String id : cards.keySet()) copy(data + "/files/stock/" + id + ".png", ArtStore.stockFile(context, id));
            if (present < designs.size()) {
                return incomplete(context, (designs.size() - present) + " design(s) could not be copied", designs.size(), present, copied);
            }
            ArtStore.setLegacyMigrated(context);
            DiagnosticsLog.info(context, "Migration complete: " + present + " of " + designs.size() + " design(s) and " +
                    cards.size() + " card(s) from " + LEGACY_PACKAGE);
            return new Outcome(State.COMPLETE, designs.size(), present, copied);
        } catch (Exception error) {
            return incomplete(context, LogSanitizer.describe(error), 0, 0, 0);
        }
    }

    private static Outcome incomplete(Context context, String reason, int expected, int present, int copied) {
        DiagnosticsLog.warning(context, "Migration not finished: " + reason + "; it will retry");
        return new Outcome(State.INCOMPLETE, expected, present, copied);
    }

    private static String readOptional(String command, String path) {
        String test = "ls".equals(command) ? "-d" : "-f";
        byte[] raw = RootShell.run("if [ " + test + " " + path + " ]; then " + command + " " + path + "; else echo " + ABSENT + "; fi");
        if (raw == null) return null;
        String value = new String(raw, StandardCharsets.UTF_8).trim();
        return ABSENT.equals(value) ? "" : value;
    }

    static List<String> parseListing(String listing) {
        List<String> ids = new ArrayList<>();
        for (String name : listing.split("\\s+")) {
            if (!name.endsWith(".png")) continue;
            String id = name.substring(0, name.length() - 4);
            if (CardIds.isValid(id) && !ids.contains(id)) ids.add(id);
        }
        return ids;
    }

    private static Copy copy(String source, File target) {
        if (target.isFile()) return Copy.ALREADY_PRESENT;
        byte[] bytes = RootShell.run("cat " + source);
        if (bytes == null || bytes.length == 0 || bytes.length > MAX_IMAGE_BYTES) return Copy.FAILED;
        BitmapFactory.Options bounds = new BitmapFactory.Options();
        bounds.inJustDecodeBounds = true;
        BitmapFactory.decodeByteArray(bytes, 0, bytes.length, bounds);
        if (!ImageLimits.isDecodable(bounds.outWidth, bounds.outHeight)) return Copy.FAILED;
        File parent = target.getParentFile();
        if (parent == null || (!parent.isDirectory() && !parent.mkdirs())) return Copy.FAILED;
        File temp = new File(parent, target.getName() + ".tmp");
        try (FileOutputStream output = new FileOutputStream(temp)) {
            output.write(bytes);
            output.getFD().sync();
        } catch (Exception error) {
            temp.delete();
            return Copy.FAILED;
        }
        if (temp.renameTo(target)) return Copy.COPIED;
        temp.delete();
        return Copy.FAILED;
    }
}
