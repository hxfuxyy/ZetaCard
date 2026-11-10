package io.github.hxfuxyy.zetacard.logging;

import android.content.Context;
import android.content.SharedPreferences;
import android.util.Log;
import io.github.hxfuxyy.zetacard.core.LogSanitizer;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

public final class DiagnosticsLog {
    private static final String TAG = "ZetaCard";
    private static final String FILE = "diagnostics";
    private static final String KEY = "entries";
    private static final int MAX_LENGTH = 48000;
    private static final int CRASH_FRAMES = 14;

    private DiagnosticsLog() { }

    public static synchronized void clear(Context context) {
        preferences(context).edit().remove(KEY).apply();
    }

    public static void startSession(Context context, String version) {
        append(context, "----", "Session started, ZetaCard " + version, false);
    }

    public static void error(Context context, String message, Throwable error) {
        append(context, "ERR ", LogSanitizer.clean(message) + "\n" + LogSanitizer.stackTrace(error, CRASH_FRAMES), false);
    }

    public static void crash(Context context, String thread, Throwable error) {
        append(context, "CRSH", "Crash on thread " + LogSanitizer.clean(thread) + "\n" + LogSanitizer.stackTrace(error, CRASH_FRAMES), true);
    }

    public static void info(Context context, String message) {
        append(context, "INFO", LogSanitizer.clean(message), false);
    }

    public static void warning(Context context, String message) {
        append(context, "WARN", LogSanitizer.clean(message), false);
    }

    public static synchronized String read(Context context) {
        String entries = preferences(context).getString(KEY, "");
        return entries == null || entries.isEmpty() ? "No app logs yet." : entries.trim();
    }

    private static SharedPreferences preferences(Context context) {
        return context.getApplicationContext().getSharedPreferences(FILE, Context.MODE_PRIVATE);
    }

    private static synchronized void append(Context context, String level, String text, boolean immediate) {
        if ("WARN".equals(level) || "ERR ".equals(level) || "CRSH".equals(level)) Log.w(TAG, text);
        else Log.i(TAG, text);
        String time = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US).format(new Date());
        SharedPreferences preferences = preferences(context);
        String previous = preferences.getString(KEY, "");
        String entries = (previous == null ? "" : previous) + time + " " + level + " " + text + "\n";
        SharedPreferences.Editor editor = preferences.edit().putString(KEY, LogSanitizer.trimToLastLines(entries, MAX_LENGTH));
        if (immediate) editor.commit();
        else editor.apply();
    }
}
