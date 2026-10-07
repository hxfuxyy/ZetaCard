package dev.zxcwsurx.zetacard;

import android.app.Application;
import android.os.ParcelFileDescriptor;
import android.os.Bundle;
import android.os.SystemClock;
import io.github.libxposed.service.XposedService;
import io.github.libxposed.service.XposedServiceHelper;
import io.github.libxposed.service.HookedTarget;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;

public final class ZetaApplication extends Application implements XposedServiceHelper.OnServiceListener {
    private static volatile XposedService service;

    @Override public void onCreate() {
        super.onCreate();
        DiagnosticsLog.info(this, "App service starting");
        XposedServiceHelper.registerListener(this);
    }

    @Override public void onServiceBind(XposedService connected) {
        service = connected;
        DiagnosticsLog.info(this, "LSPosed data service connected");
        new Thread(this::syncAllArt, "ZetaCard artwork sync").start();
    }

    @Override public void onServiceDied(XposedService disconnected) {
        if (service == disconnected) service = null;
        DiagnosticsLog.warning(this, "LSPosed data service disconnected");
    }

    static boolean isConnected() { return service != null; }

    String requestHookScan() {
        XposedService connected = service;
        if (connected == null) {
            DiagnosticsLog.warning(this, "Hook scan unavailable: LSPosed service disconnected");
            return "LSPosed data service is not connected";
        }
        try {
            long scan = SystemClock.elapsedRealtime();
            if (!connected.getRemotePreferences("control").edit().putLong("scan", scan).commit()) {
                DiagnosticsLog.warning(this, "Could not request a new hook scan");
                return "Could not request a new hook scan";
            }
            int reloads = 0;
            for (HookedTarget target : connected.getRunningTargets()) {
                if (!target.getProcessName().startsWith("com.google.android.apps.walletnfcrel")) continue;
                connected.hotReloadModule(target, new Bundle(), (changed, result) ->
                        DiagnosticsLog.info(this, "Wallet hook reload: " + result.status() + " " + result.message()));
                reloads++;
            }
            DiagnosticsLog.info(this, "Hook scan requested: " + scan + ", reloading " + reloads + " Wallet processes");
            return reloads == 0 ? "Hook scan requested. Open Wallet to run it." :
                    "Hook scan requested. Open Wallet to see the result.";
        } catch (Exception error) {
            DiagnosticsLog.warning(this, "Hook scan request failed: " + error.getClass().getSimpleName() + ": " + error.getMessage());
            return "Could not start hook scan: " + error.getClass().getSimpleName();
        }
    }

    static void syncArt(Application context, String id) {
        XposedService connected = service;
        if (connected == null || !ArtProvider.valid(id)) return;
        File image = ArtProvider.file(context, id);
        try {
            if (!image.isFile()) {
                connected.deleteRemoteFile("art_" + id);
                connected.getRemotePreferences("art").edit().remove(id).commit();
                return;
            }
            try (ParcelFileDescriptor remote = connected.openRemoteFile("art_" + id);
                 FileInputStream input = new FileInputStream(image);
                 FileOutputStream output = new FileOutputStream(remote.getFileDescriptor())) {
                output.getChannel().truncate(0);
                byte[] buffer = new byte[8192];
                int length;
                while ((length = input.read(buffer)) != -1) output.write(buffer, 0, length);
                output.getFD().sync();
            }
            connected.getRemotePreferences("art").edit().putLong(id, image.lastModified()).commit();
            DiagnosticsLog.info(context, "Artwork synced to LSPosed: " + id.substring(0, 8));
        } catch (Exception error) {
            DiagnosticsLog.warning(context, "Artwork sync failed: " + error.getClass().getSimpleName() + ": " + error.getMessage());
        }
    }

    private void syncAllArt() {
        File directory = new File(getFilesDir(), "art");
        File[] files = directory.listFiles();
        if (files != null) for (File file : files) {
            String name = file.getName();
            if (name.endsWith(".png")) syncArt(this, name.substring(0, name.length() - 4));
        }
        XposedService connected = service;
        if (connected == null) return;
        try {
            for (String name : connected.listRemoteFiles()) {
                if (!name.startsWith("art_") || !ArtProvider.valid(name.substring(4))) continue;
                String id = name.substring(4);
                if (!ArtProvider.file(this, id).isFile()) syncArt(this, id);
            }
        } catch (Exception error) {
            DiagnosticsLog.warning(this, "Could not clean old LSPosed artwork: " + error.getMessage());
        }
    }
}
