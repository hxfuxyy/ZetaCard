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
    private static final int MAX_LENGTH = 32000;

    private DiagnosticsLog() { }

    public static synchronized void clear(Context context) {
        preferences(context).edit().remove(KEY).apply();
    }

    public static void info(Context context, String message) {
        append(context, "INFO", message);
    }

    public static void warning(Context context, String message) {
        append(context, "WARN", message);
    }

    public static synchronized String read(Context context) {
        String entries = preferences(context).getString(KEY, "");
        return entries == null || entries.isEmpty() ? "No app logs yet." : entries.trim();
    }

    private static SharedPreferences preferences(Context context) {
        return context.getApplicationContext().getSharedPreferences(FILE, Context.MODE_PRIVATE);
    }

    private static synchronized void append(Context context, String level, String message) {
        String clean = LogSanitizer.clean(message);
        if ("WARN".equals(level)) Log.w(TAG, clean);
        else Log.i(TAG, clean);
        String time = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US).format(new Date());
        SharedPreferences preferences = preferences(context);
        String previous = preferences.getString(KEY, "");
        String entries = (previous == null ? "" : previous) + time + " " + level + " " + clean + "\n";
        preferences.edit().putString(KEY, LogSanitizer.trimToLastLines(entries, MAX_LENGTH)).apply();
    }
}
