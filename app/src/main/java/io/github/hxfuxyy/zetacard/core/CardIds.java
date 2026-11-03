package io.github.hxfuxyy.zetacard.core;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.regex.Pattern;

public final class CardIds {
    private static final Pattern VALID = Pattern.compile("[0-9a-f]{64}");
    private static final char[] HEX = "0123456789abcdef".toCharArray();

    private CardIds() { }

    public static boolean isValid(String id) {
        return id != null && VALID.matcher(id).matches();
    }

    public static String fromSource(String source) {
        if (source == null) throw new IllegalArgumentException("Card source is missing");
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(source.getBytes(StandardCharsets.UTF_8));
            char[] out = new char[digest.length * 2];
            for (int i = 0; i < digest.length; i++) {
                out[i * 2] = HEX[(digest[i] >> 4) & 0x0f];
                out[i * 2 + 1] = HEX[digest[i] & 0x0f];
            }
            return new String(out);
        } catch (NoSuchAlgorithmException error) {
            throw new IllegalStateException("SHA-256 is unavailable", error);
        }
    }

    public static String shortId(String id) {
        if (id == null) return "unknown";
        return id.length() <= 8 ? id : id.substring(0, 8);
    }
}
