package dev.zxcwsurx.zetacard;

import android.content.Context;
import android.content.SharedPreferences;
import android.util.Log;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

public final class DiagnosticsLog {
    private static final String TAG = "ZetaCard";
    private static final String FILE = "diagnostics";
    private static final int MAX_LENGTH = 32000;

    private DiagnosticsLog() { }

    public static synchronized void clear(Context context) {
        preferences(context).edit().remove("entries").commit();
    }

    public static synchronized void info(Context context, String message) {
        append(context, "INFO", message);
        Log.i(TAG, message);
    }

    public static synchronized void warning(Context context, String message) {
        append(context, "WARN", message);
        Log.w(TAG, message);
    }

    public static synchronized String read(Context context) {
        String entries = preferences(context).getString("entries", "");
        return entries == null || entries.isEmpty() ? "No app logs yet." : entries.trim();
    }

    private static SharedPreferences preferences(Context context) {
        return context.getApplicationContext().getSharedPreferences(FILE, Context.MODE_PRIVATE);
    }

    private static void append(Context context, String level, String message) {
        String clean = message.replaceAll("[\\r\\n\\t]+", " ").trim();
        if (clean.length() > 240) clean = clean.substring(0, 240);
        String time = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US).format(new Date());
        SharedPreferences preferences = preferences(context);
        String previous = preferences.getString("entries", "");
        String entries = (previous == null ? "" : previous) + time + " " + level + " " + clean + "\n";
        if (entries.length() > MAX_LENGTH) {
            int cut = entries.indexOf('\n', entries.length() - MAX_LENGTH);
            entries = entries.substring(cut < 0 ? entries.length() - MAX_LENGTH : cut + 1);
        }
        preferences.edit().putString("entries", entries).commit();
    }
}
