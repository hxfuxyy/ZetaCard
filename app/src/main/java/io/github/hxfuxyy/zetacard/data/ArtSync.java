package io.github.hxfuxyy.zetacard.data;

import android.content.Context;
import android.content.SharedPreferences;
import android.os.ParcelFileDescriptor;
import io.github.hxfuxyy.zetacard.core.CardIds;
import io.github.hxfuxyy.zetacard.core.LogSanitizer;
import io.github.hxfuxyy.zetacard.logging.DiagnosticsLog;
import io.github.libxposed.service.XposedService;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;

public final class ArtSync {
    private static final String REMOTE_PREFERENCES = "art";
    private static final String REMOTE_PREFIX = "art_";
    public static final String CAPTION_PREFIX = "caption.";

    private ArtSync() { }

    public static synchronized boolean sync(XposedService service, Context context, String id) {
        if (service == null || !CardIds.isValid(id)) return false;
        File image = ArtStore.artFile(context, id);
        try {
            SharedPreferences remote = service.getRemotePreferences(REMOTE_PREFERENCES);
            if (!image.isFile()) {
                service.deleteRemoteFile(REMOTE_PREFIX + id);
                boolean removed = remote.edit().remove(id).remove(CAPTION_PREFIX + id).commit();
                if (removed) {
                    ArtStore.setSyncedRevision(context, id, -1);
                    ArtStore.setSyncedCaption(context, id, null);
                }
                return removed;
            }
            long revision = image.lastModified();
            String caption = ArtStore.captionText(context, id);
            if (remote.getLong(id, -1) != revision) copyToRemote(service, image, id);
            SharedPreferences.Editor editor = remote.edit().putLong(id, revision);
            if (caption == null) editor.remove(CAPTION_PREFIX + id);
            else editor.putString(CAPTION_PREFIX + id, caption);
            boolean saved = editor.commit();
            if (saved) {
                ArtStore.setSyncedRevision(context, id, revision);
                ArtStore.setSyncedCaption(context, id, caption);
                DiagnosticsLog.info(context, "Artwork synced to LSPosed: " + CardIds.shortId(id));
            }
            return saved;
        } catch (Exception error) {
            DiagnosticsLog.warning(context, "Artwork sync failed: " + LogSanitizer.describe(error));
            return false;
        }
    }

    private static void copyToRemote(XposedService service, File image, String id) throws java.io.IOException {
        try (ParcelFileDescriptor remote = service.openRemoteFile(REMOTE_PREFIX + id);
             FileInputStream input = new FileInputStream(image);
             FileOutputStream output = new FileOutputStream(remote.getFileDescriptor())) {
            output.getChannel().truncate(0);
            byte[] buffer = new byte[8192];
            int length;
            while ((length = input.read(buffer)) != -1) output.write(buffer, 0, length);
            output.getFD().sync();
        }
    }

    private static boolean isCurrent(XposedService service, Context context, String id, File image) {
        try {
            SharedPreferences remote = service.getRemotePreferences(REMOTE_PREFERENCES);
            String caption = ArtStore.captionText(context, id);
            String remoteCaption = remote.getString(CAPTION_PREFIX + id, null);
            return remote.getLong(id, -1) == image.lastModified() &&
                    (caption == null ? remoteCaption == null : caption.equals(remoteCaption));
        } catch (Exception ignored) {
            return false;
        }
    }

    public static void syncAll(XposedService service, Context context) {
        File[] files = ArtStore.artDirectory(context).listFiles();
        if (files != null) {
            for (File file : files) {
                String name = file.getName();
                if (!name.endsWith(".png")) continue;
                String id = name.substring(0, name.length() - 4);
                if (!CardIds.isValid(id)) continue;
                if (isCurrent(service, context, id, file)) {
                    ArtStore.setSyncedRevision(context, id, file.lastModified());
                    ArtStore.setSyncedCaption(context, id, ArtStore.captionText(context, id));
                } else {
                    sync(service, context, id);
                }
            }
        }
        try {
            String[] remote = service.listRemoteFiles();
            if (remote == null) return;
            for (String name : remote) {
                if (!name.startsWith(REMOTE_PREFIX)) continue;
                String id = name.substring(REMOTE_PREFIX.length());
                if (CardIds.isValid(id) && !ArtStore.artFile(context, id).isFile()) sync(service, context, id);
            }
        } catch (Exception error) {
            DiagnosticsLog.warning(context, "Could not clean old LSPosed artwork: " + LogSanitizer.describe(error));
        }
    }
}
