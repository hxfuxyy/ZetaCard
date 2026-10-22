package dev.zxcwsurx.zetacard;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

public final class GrantReceiver extends BroadcastReceiver {
    static final String WALLET = "com.google.android.apps.walletnfcrel";

    static void grant(Context context) {
        try {
            context.grantUriPermission(WALLET, ArtProvider.URI,
                    Intent.FLAG_GRANT_READ_URI_PERMISSION | Intent.FLAG_GRANT_PREFIX_URI_PERMISSION);
        } catch (RuntimeException ignored) { }
    }

    @Override public void onReceive(Context context, Intent intent) {
        grant(context);
    }
}
