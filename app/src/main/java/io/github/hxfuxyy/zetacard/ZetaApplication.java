package io.github.hxfuxyy.zetacard;

import android.app.Application;
import android.content.Context;
import io.github.hxfuxyy.zetacard.data.AppLanguages;
import io.github.hxfuxyy.zetacard.data.ArtSync;
import io.github.hxfuxyy.zetacard.data.LsposedConnection;
import io.github.hxfuxyy.zetacard.logging.DiagnosticsLog;
import io.github.libxposed.service.XposedService;
import io.github.libxposed.service.XposedServiceHelper;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public final class ZetaApplication extends Application implements XposedServiceHelper.OnServiceListener {
    private static final String WALLET = "com.google.android.apps.walletnfcrel";
    private static volatile XposedService service;
    private final ExecutorService syncExecutor = Executors.newSingleThreadExecutor(runnable -> {
        Thread thread = new Thread(runnable, "ZetaCard artwork sync");
        thread.setDaemon(true);
        return thread;
    });

    @Override protected void attachBaseContext(Context base) {
        super.attachBaseContext(AppLanguages.wrap(base));
    }

    @Override public void onCreate() {
        super.onCreate();
        installCrashLogger();
        DiagnosticsLog.startSession(this, BuildConfig.VERSION_NAME + " (" + BuildConfig.VERSION_CODE + ", " + BuildConfig.BUILD_TYPE + ")");
        XposedServiceHelper.registerListener(this);
    }

    private void installCrashLogger() {
        Thread.UncaughtExceptionHandler previous = Thread.getDefaultUncaughtExceptionHandler();
        Thread.setDefaultUncaughtExceptionHandler((thread, error) -> {
            try {
                DiagnosticsLog.crash(this, thread.getName(), error);
            } catch (Throwable ignored) {
            }
            if (previous != null) previous.uncaughtException(thread, error);
        });
    }

    @Override public void onServiceBind(XposedService connected) {
        service = connected;
        DiagnosticsLog.info(this, "LSPosed connected: " + frameworkSummary() + "; Wallet in scope: " + walletScopeSummary());
        LsposedConnection.setConnected(true);
        syncExecutor.execute(() -> {
            XposedService current = service;
            if (current != null) ArtSync.syncAll(current, this);
            LsposedConnection.markSynced();
        });
    }

    @Override public void onServiceDied(XposedService disconnected) {
        if (service == disconnected) {
            service = null;
            LsposedConnection.setConnected(false);
        }
        DiagnosticsLog.warning(this, "LSPosed data service disconnected");
    }

    public static boolean isConnected() {
        return service != null;
    }

    public static boolean syncArt(Application context, String id) {
        return ArtSync.sync(service, context, id);
    }

    public static String frameworkSummary() {
        XposedService current = service;
        if (current == null) return "not connected";
        try {
            return current.getFrameworkName() + " " + current.getFrameworkVersion() + " (API " + current.getApiVersion() + ")";
        } catch (Throwable error) {
            return "connected, details unavailable";
        }
    }

    public static Boolean isWalletInScope() {
        XposedService current = service;
        if (current == null) return null;
        try {
            List<String> scope = current.getScope();
            return scope == null ? null : scope.contains(WALLET);
        } catch (Throwable error) {
            return null;
        }
    }

    public static String walletScopeSummary() {
        XposedService current = service;
        if (current == null) return "unknown";
        try {
            List<String> scope = current.getScope();
            if (scope == null) return "unknown";
            return scope.contains(WALLET) ? "yes" : "no (" + scope.size() + " app(s) in scope)";
        } catch (Throwable error) {
            return "unknown";
        }
    }
}
