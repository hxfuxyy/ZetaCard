package io.github.hxfuxyy.zetacard.core;

import java.util.regex.Pattern;

public final class LogSanitizer {
    public static final int MAX_MESSAGE_LENGTH = 240;
    private static final Pattern WHITESPACE = Pattern.compile("[\\r\\n\\t]+");
    private static final Pattern URL = Pattern.compile("(?i)\\b(?:https?|content|file)://\\S+");
    private static final Pattern LONG_DIGITS = Pattern.compile("\\d(?:[ -]?\\d){11,}");
    private static final Pattern EMAIL = Pattern.compile("[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,}");
    private static final Pattern USER_PATH = Pattern.compile("/(?:data/user/\\d+|data/data|storage/emulated/\\d+|sdcard)/\\S*");

    private LogSanitizer() { }

    public static String clean(String message) {
        if (message == null) return "";
        String value = WHITESPACE.matcher(message).replaceAll(" ").trim();
        value = URL.matcher(value).replaceAll("<url>");
        value = EMAIL.matcher(value).replaceAll("<email>");
        value = USER_PATH.matcher(value).replaceAll("<path>");
        value = LONG_DIGITS.matcher(value).replaceAll("<number>");
        if (value.length() > MAX_MESSAGE_LENGTH) value = value.substring(0, MAX_MESSAGE_LENGTH);
        return value;
    }

    public static String describe(Throwable error) {
        if (error == null) return "";
        String message = error.getMessage();
        return error.getClass().getSimpleName() + (message == null || message.isEmpty() ? "" : ": " + clean(message));
    }

    public static String stackTrace(Throwable error, int maxFrames) {
        if (error == null) return "";
        StringBuilder out = new StringBuilder();
        Throwable current = error;
        int depth = 0;
        while (current != null && depth < 4) {
            if (depth > 0) out.append("\n  caused by ");
            else out.append("  ");
            out.append(describe(current));
            StackTraceElement[] frames = current.getStackTrace();
            int count = Math.min(frames.length, depth == 0 ? maxFrames : Math.max(3, maxFrames / 3));
            for (int i = 0; i < count; i++) {
                StackTraceElement frame = frames[i];
                out.append("\n    at ").append(frame.getClassName()).append('.').append(frame.getMethodName())
                        .append('(').append(frame.getFileName() == null ? "?" : frame.getFileName())
                        .append(':').append(frame.getLineNumber()).append(')');
            }
            if (frames.length > count) out.append("\n    … ").append(frames.length - count).append(" more");
            Throwable cause = current.getCause();
            current = cause == current ? null : cause;
            depth++;
        }
        return out.toString();
    }

    public static String trimToLastLines(String entries, int maxLength) {
        if (entries == null) return "";
        if (entries.length() <= maxLength) return entries;
        int cut = entries.indexOf('\n', entries.length() - maxLength);
        return entries.substring(cut < 0 ? entries.length() - maxLength : cut + 1);
    }
}
