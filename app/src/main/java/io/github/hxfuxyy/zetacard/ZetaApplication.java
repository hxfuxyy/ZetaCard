package io.github.hxfuxyy.zetacard;

import android.app.Application;
import io.github.hxfuxyy.zetacard.data.ArtSync;
import io.github.hxfuxyy.zetacard.logging.DiagnosticsLog;
import io.github.libxposed.service.XposedService;
import io.github.libxposed.service.XposedServiceHelper;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public final class ZetaApplication extends Application implements XposedServiceHelper.OnServiceListener {
    private static volatile XposedService service;
    private final ExecutorService syncExecutor = Executors.newSingleThreadExecutor(runnable -> {
        Thread thread = new Thread(runnable, "ZetaCard artwork sync");
        thread.setDaemon(true);
        return thread;
    });

    @Override public void onCreate() {
        super.onCreate();
        DiagnosticsLog.info(this, "App service starting");
        XposedServiceHelper.registerListener(this);
    }

    @Override public void onServiceBind(XposedService connected) {
        service = connected;
        DiagnosticsLog.info(this, "LSPosed data service connected");
        syncExecutor.execute(() -> {
            XposedService current = service;
            if (current != null) ArtSync.syncAll(current, this);
        });
    }

    @Override public void onServiceDied(XposedService disconnected) {
        if (service == disconnected) service = null;
        DiagnosticsLog.warning(this, "LSPosed data service disconnected");
    }

    public static boolean isConnected() {
        return service != null;
    }

    public static boolean syncArt(Application context, String id) {
        return ArtSync.sync(service, context, id);
    }
}
