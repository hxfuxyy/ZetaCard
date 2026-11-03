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

    public static String trimToLastLines(String entries, int maxLength) {
        if (entries == null) return "";
        if (entries.length() <= maxLength) return entries;
        int cut = entries.indexOf('\n', entries.length() - maxLength);
        return entries.substring(cut < 0 ? entries.length() - maxLength : cut + 1);
    }
}
