package dev.zxcwsurx.zetacard;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

public final class GrantReceiver extends BroadcastReceiver {
    @Override public void onReceive(Context context, Intent intent) {
        try {
            context.grantUriPermission("com.google.android.apps.walletnfcrel", ArtProvider.URI,
                    Intent.FLAG_GRANT_READ_URI_PERMISSION | Intent.FLAG_GRANT_PREFIX_URI_PERMISSION);
        } catch (IllegalArgumentException ignored) { }
    }
}
