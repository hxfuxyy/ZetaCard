package dev.zxcwsurx.zetacard;

import android.content.ContentProvider;
import android.content.ContentValues;
import android.content.SharedPreferences;
import android.database.Cursor;
import android.net.Uri;
import android.os.Binder;
import android.os.Bundle;
import android.os.ParcelFileDescriptor;
import java.io.File;
import java.io.FileNotFoundException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.net.URL;
import javax.net.ssl.HttpsURLConnection;

public final class ArtProvider extends ContentProvider {
    public static final String AUTHORITY = "dev.zxcwsurx.zetacard.art";
    public static final Uri URI = Uri.parse("content://" + AUTHORITY);
    private static final String WALLET = "com.google.android.apps.walletnfcrel";
    private static final ExecutorService stockDownloads = Executors.newSingleThreadExecutor();
    private static final Set<String> pendingStock = Collections.synchronizedSet(new HashSet<>());

    static SharedPreferences prefs(android.content.Context context) {
        return context.getSharedPreferences("art", 0);
    }

    static boolean valid(String id) {
        return id != null && id.matches("[0-9a-f]{64}");
    }

    static File file(android.content.Context context, String id) {
        return new File(new File(context.getFilesDir(), "art"), id + ".png");
    }

    static File stockFile(android.content.Context context, String id) {
        return new File(new File(context.getFilesDir(), "stock"), id + ".png");
    }

    static ArrayList<String> ids(android.content.Context context) {
        ArrayList<String> result = new ArrayList<>(prefs(context).getStringSet("ids", Collections.emptySet()));
        Collections.sort(result);
        return result;
    }

    private void checkCaller() {
        int uid = Binder.getCallingUid();
        if (uid == android.os.Process.myUid()) return;
        String[] packages = getContext().getPackageManager().getPackagesForUid(uid);
        if (packages != null) for (String name : packages) if (WALLET.equals(name)) return;
        throw new SecurityException("ZetaCard only accepts Google Wallet");
    }

    @Override public boolean onCreate() { return true; }

    @Override public Bundle call(String method, String arg, Bundle extras) {
        checkCaller();
        String id = extras == null ? null : extras.getString("id");
        if (!valid(id)) return Bundle.EMPTY;
        if ("discover".equals(method)) {
            SharedPreferences p = prefs(getContext());
            Set<String> ids = new HashSet<>(p.getStringSet("ids", Collections.emptySet()));
            boolean firstSeen = ids.add(id);
            String label = extras.getString("label", "Payment card");
            label = label.replaceAll("[\\p{Cntrl}]", "").trim();
            if (label.isEmpty() || label.length() > 60 || label.matches(".*[0-9]{8,}.*")) label = "Payment card";
            SharedPreferences.Editor editor = p.edit().putStringSet("ids", ids);
            if (!p.contains("name." + id)) editor.putString("name." + id, label);
            editor.apply();
            if (firstSeen) DiagnosticsLog.info(getContext(), "Card discovered: " + id.substring(0, 8));
            String stockUrl = extras.getString("stockUrl");
            Uri source = stockUrl == null ? null : Uri.parse(stockUrl);
            String host = source == null ? null : source.getHost();
            if (source != null && "https".equals(source.getScheme()) && host != null &&
                    (host.equals("googleusercontent.com") || host.endsWith(".googleusercontent.com")) &&
                    !stockFile(getContext(), id).isFile() && pendingStock.add(id)) {
                android.content.Context app = getContext().getApplicationContext();
                stockDownloads.execute(() -> {
                    HttpsURLConnection connection = null;
                    try {
                        connection = (HttpsURLConnection) new URL(stockUrl).openConnection();
                        connection.setConnectTimeout(8000);
                        connection.setReadTimeout(8000);
                        connection.setInstanceFollowRedirects(false);
                        if (connection.getResponseCode() != 200 || connection.getContentLengthLong() > 2_000_000) return;
                        java.io.ByteArrayOutputStream bytes = new java.io.ByteArrayOutputStream();
                        try (java.io.InputStream input = connection.getInputStream()) {
                            byte[] buffer = new byte[8192];
                            int size;
                            while ((size = input.read(buffer)) != -1) {
                                if (bytes.size() + size > 2_000_000) return;
                                bytes.write(buffer, 0, size);
                            }
                        }
                        byte[] image = bytes.toByteArray();
                        if (android.graphics.BitmapFactory.decodeByteArray(image, 0, image.length) == null) return;
                        File target = stockFile(app, id);
                        target.getParentFile().mkdirs();
                        try (java.io.FileOutputStream output = new java.io.FileOutputStream(target)) {
                            output.write(image);
                        }
                    } catch (Exception ignored) {
                    } finally {
                        if (connection != null) connection.disconnect();
                        pendingStock.remove(id);
                    }
                });
            }
            return Bundle.EMPTY;
        }
        if ("art".equals(method)) {
            Bundle result = new Bundle();
            File image = file(getContext(), id);
            if (image.isFile()) {
                result.putString("uri", URI.buildUpon().appendPath(id).build().toString());
                result.putLong("revision", image.lastModified());
            }
            return result;
        }
        if ("stock".equals(method)) {
            byte[] image = extras.getByteArray("image");
            if (image == null || image.length == 0 || image.length > 2_000_000) return Bundle.EMPTY;
            File target = stockFile(getContext(), id);
            if (!target.isFile()) {
                target.getParentFile().mkdirs();
                try (java.io.FileOutputStream output = new java.io.FileOutputStream(target)) {
                    output.write(image);
                } catch (java.io.IOException ignored) { return Bundle.EMPTY; }
            }
            return Bundle.EMPTY;
        }
        return Bundle.EMPTY;
    }

    @Override public ParcelFileDescriptor openFile(Uri uri, String mode) throws FileNotFoundException {
        checkCaller();
        if (!"r".equals(mode) || uri.getPathSegments().size() != 1) throw new FileNotFoundException();
        String id = uri.getLastPathSegment();
        if (!valid(id)) throw new FileNotFoundException();
        return ParcelFileDescriptor.open(file(getContext(), id), ParcelFileDescriptor.MODE_READ_ONLY);
    }

    @Override public String getType(Uri uri) { return "image/png"; }
    @Override public Cursor query(Uri uri, String[] projection, String selection, String[] selectionArgs, String sortOrder) { return null; }
    @Override public Uri insert(Uri uri, ContentValues values) { return null; }
    @Override public int delete(Uri uri, String selection, String[] selectionArgs) { return 0; }
    @Override public int update(Uri uri, ContentValues values, String selection, String[] selectionArgs) { return 0; }
}
