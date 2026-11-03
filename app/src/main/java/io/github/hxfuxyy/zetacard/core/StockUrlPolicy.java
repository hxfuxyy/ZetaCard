package io.github.hxfuxyy.zetacard.core;

import java.net.URI;
import java.util.Locale;

public final class StockUrlPolicy {
    public static final int MAX_BYTES = 2_000_000;
    private static final String TRUSTED_DOMAIN = "googleusercontent.com";

    private StockUrlPolicy() { }

    public static boolean isAllowed(String url) {
        if (url == null || url.isEmpty() || url.length() > 4096) return false;
        try {
            URI uri = new URI(url);
            if (!"https".equalsIgnoreCase(uri.getScheme()) || uri.getUserInfo() != null) return false;
            int port = uri.getPort();
            if (port != -1 && port != 443) return false;
            String host = uri.getHost();
            if (host == null) return false;
            host = host.toLowerCase(Locale.ROOT);
            return host.equals(TRUSTED_DOMAIN) || host.endsWith("." + TRUSTED_DOMAIN);
        } catch (Exception ignored) {
            return false;
        }
    }
}
