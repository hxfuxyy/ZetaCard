package io.github.hxfuxyy.zetacard.core;

import java.util.regex.Pattern;

public final class CardLabels {
    public static final String DEFAULT = "Payment card";
    public static final int MAX_USER_LENGTH = 48;
    private static final int MAX_IMPORTED_LENGTH = 60;
    private static final Pattern LONG_DIGITS = Pattern.compile(".*[0-9]{8,}.*");
    private static final Pattern LONG_HEX = Pattern.compile("[0-9a-fA-F]{32,}");
    private static final Pattern CONTROL = Pattern.compile("[\\p{Cntrl}]+");

    private CardLabels() { }

    public static String stripControl(String value) {
        return value == null ? "" : CONTROL.matcher(value).replaceAll(" ").trim();
    }

    public static String forImport(String raw) {
        String label = stripControl(raw);
        if (label.isEmpty() || label.length() > MAX_IMPORTED_LENGTH || LONG_DIGITS.matcher(label).matches()) return DEFAULT;
        return label;
    }

    public static boolean isDisplayCandidate(String raw) {
        if (raw == null) return false;
        String value = raw.trim();
        return value.length() >= 3 && value.length() <= MAX_USER_LENGTH && value.indexOf('/') < 0 &&
                !LONG_DIGITS.matcher(value).matches() && !LONG_HEX.matcher(value).matches();
    }

    public static String forUserInput(String raw) {
        String label = stripControl(raw);
        if (label.length() > MAX_USER_LENGTH) label = label.substring(0, MAX_USER_LENGTH).trim();
        return label;
    }
}
