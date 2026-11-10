package io.github.hxfuxyy.zetacard.data;

import android.content.Context;
import android.content.SharedPreferences;
import io.github.hxfuxyy.zetacard.R;
import io.github.hxfuxyy.zetacard.core.CardIds;
import io.github.hxfuxyy.zetacard.core.CardLabels;
import io.github.hxfuxyy.zetacard.core.CardRegistry;
import io.github.hxfuxyy.zetacard.core.CaptionRules;
import java.io.File;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public final class ArtStore {
    private static final String PREFERENCES = "art";
    private static final String KEY_IDS = "ids";
    private static final String KEY_WELCOMED = "welcomed";
    private static final String KEY_LEGACY_MIGRATED = "legacy_migrated";
    private static final String KEY_LAST_AUTO_SCAN = "last_auto_scan";
    private static final String NAME_PREFIX = "name.";
    private static final String SYNCED_PREFIX = "synced.";
    private static final String CAPTION_PREFIX = "caption.";
    private static final String SYNCED_CAPTION_PREFIX = "synced_caption.";
    private static final String ART_DIRECTORY = "art";
    private static final String STOCK_DIRECTORY = "stock";

    private ArtStore() { }

    private static SharedPreferences prefs(Context context) {
        return context.getApplicationContext().getSharedPreferences(PREFERENCES, Context.MODE_PRIVATE);
    }

    public static File artDirectory(Context context) {
        return new File(context.getFilesDir(), ART_DIRECTORY);
    }

    public static File artFile(Context context, String id) {
        requireValid(id);
        return new File(artDirectory(context), id + ".png");
    }

    public static File stockFile(Context context, String id) {
        requireValid(id);
        return new File(new File(context.getFilesDir(), STOCK_DIRECTORY), id + ".png");
    }

    public static List<String> ids(Context context) {
        List<String> result = new ArrayList<>();
        for (String id : prefs(context).getStringSet(KEY_IDS, Collections.emptySet())) {
            if (CardIds.isValid(id)) result.add(id);
        }
        Collections.sort(result);
        return result;
    }

    public static synchronized int addCards(Context context, Collection<CardRegistry.Entry> entries) {
        SharedPreferences preferences = prefs(context);
        Set<String> known = new HashSet<>(preferences.getStringSet(KEY_IDS, Collections.emptySet()));
        SharedPreferences.Editor editor = preferences.edit();
        int added = 0;
        for (CardRegistry.Entry entry : entries) {
            if (!CardIds.isValid(entry.id)) continue;
            if (known.add(entry.id)) added++;
            if (!preferences.contains(NAME_PREFIX + entry.id)) editor.putString(NAME_PREFIX + entry.id, entry.label);
        }
        editor.putStringSet(KEY_IDS, known).apply();
        return added;
    }

    public static String name(Context context, String id) {
        String name = prefs(context).getString(NAME_PREFIX + id, null);
        return name == null || name.trim().isEmpty() ? CardLabels.DEFAULT : name;
    }

    public static void setName(Context context, String id, String name) {
        requireValid(id);
        String clean = CardLabels.forUserInput(name);
        if (clean.isEmpty()) return;
        prefs(context).edit().putString(NAME_PREFIX + id, clean).apply();
    }

    public static boolean isWelcomed(Context context) {
        return prefs(context).getBoolean(KEY_WELCOMED, false);
    }

    public static void setWelcomed(Context context) {
        prefs(context).edit().putBoolean(KEY_WELCOMED, true).apply();
    }

    public static long syncedRevision(Context context, String id) {
        return prefs(context).getLong(SYNCED_PREFIX + id, -1);
    }

    public static void setSyncedRevision(Context context, String id, long revision) {
        requireValid(id);
        SharedPreferences.Editor editor = prefs(context).edit();
        if (revision < 0) editor.remove(SYNCED_PREFIX + id);
        else editor.putLong(SYNCED_PREFIX + id, revision);
        editor.apply();
    }

    public static boolean isCaptionEnabled(Context context, String id) {
        return prefs(context).getBoolean(CAPTION_PREFIX + id, false);
    }

    public static void setCaptionEnabled(Context context, String id, boolean enabled) {
        requireValid(id);
        prefs(context).edit().putBoolean(CAPTION_PREFIX + id, enabled).apply();
    }

    public static String captionText(Context context, String id) {
        if (!isCaptionEnabled(context, id)) return null;
        String name = name(context, id);
        if (CardLabels.DEFAULT.equals(name)) name = context.getString(R.string.card_default_name);
        return CaptionRules.INSTANCE.text(name);
    }

    public static String syncedCaption(Context context, String id) {
        return prefs(context).getString(SYNCED_CAPTION_PREFIX + id, null);
    }

    public static void setSyncedCaption(Context context, String id, String caption) {
        requireValid(id);
        SharedPreferences.Editor editor = prefs(context).edit();
        if (caption == null) editor.remove(SYNCED_CAPTION_PREFIX + id);
        else editor.putString(SYNCED_CAPTION_PREFIX + id, caption);
        editor.apply();
    }

    public static void setNameFromBackup(Context context, String id, String name) {
        requireValid(id);
        String clean = CardLabels.forUserInput(name);
        if (clean.isEmpty()) return;
        String current = prefs(context).getString(NAME_PREFIX + id, null);
        if (current == null || current.trim().isEmpty() || CardLabels.DEFAULT.equals(current)) {
            prefs(context).edit().putString(NAME_PREFIX + id, clean).apply();
        }
    }

    public static long lastAutoScan(Context context) {
        return prefs(context).getLong(KEY_LAST_AUTO_SCAN, 0L);
    }

    public static void setLastAutoScan(Context context, long time) {
        prefs(context).edit().putLong(KEY_LAST_AUTO_SCAN, time).apply();
    }

    public static boolean isLegacyMigrated(Context context) {
        return prefs(context).getBoolean(KEY_LEGACY_MIGRATED, false);
    }

    public static void setLegacyMigrated(Context context) {
        prefs(context).edit().putBoolean(KEY_LEGACY_MIGRATED, true).apply();
    }

    private static void requireValid(String id) {
        if (!CardIds.isValid(id)) throw new IllegalArgumentException("Invalid card id");
    }
}
