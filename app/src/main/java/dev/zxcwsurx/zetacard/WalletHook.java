package dev.zxcwsurx.zetacard;

import android.app.Application;
import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.Drawable;
import android.net.Uri;
import android.os.Bundle;
import android.util.Log;
import android.util.LruCache;
import android.widget.ImageView;
import java.lang.reflect.Field;
import java.lang.reflect.Constructor;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.io.ByteArrayOutputStream;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.Map;
import java.util.WeakHashMap;
import dalvik.system.DexFile;
import io.github.libxposed.api.XposedInterface;
import io.github.libxposed.api.XposedModule;
import io.github.libxposed.api.XposedModuleInterface;

public final class WalletHook extends XposedModule {
    private static final String TAG = "ZetaCard";
    private static final String WALLET = "com.google.android.apps.walletnfcrel";
    private final Set<Method> installed = new HashSet<>();
    private final Set<Class<?>> drawInstalled = new HashSet<>();
    private final Map<Drawable, BitmapDrawable> replacements = new WeakHashMap<>();
    private final Map<Drawable, String> cardIds = new WeakHashMap<>();
    private final Set<String> capturedStock = new HashSet<>();
    private final LruCache<String, Bitmap> bitmaps = new LruCache<String, Bitmap>(16 * 1024) {
        @Override protected int sizeOf(String key, Bitmap value) { return value.getByteCount() / 1024; }
    };
    private final Set<String> composeCardUrls = new HashSet<>();
    private boolean searched;

    @Override public void onModuleLoaded(XposedModuleInterface.ModuleLoadedParam param) {
        Log.i(TAG, "Loaded in " + param.getProcessName());
        if (!WALLET.equals(param.getProcessName())) return;
        try {
            Method attach = Application.class.getDeclaredMethod("attach", Context.class);
            attach.setAccessible(true);
            hook(attach).setExceptionMode(XposedInterface.ExceptionMode.PROTECTIVE)
                    .setId("zetacard-application-attach")
                    .intercept(chain -> {
                        Object result = chain.proceed();
                        Context context = (Context) chain.getArgs().get(0);
                        searchOnce(context, context.getClassLoader());
                        return result;
                    });
        } catch (Throwable error) { Log.e(TAG, "Could not inspect Wallet", error); }
    }

    @Override public void onPackageReady(XposedModuleInterface.PackageReadyParam param) {
        if (!WALLET.equals(param.getPackageName())) return;
        Context context = Application.getProcessName().startsWith(WALLET) ? currentApplication() : null;
        if (context != null) searchOnce(context, param.getClassLoader());
    }

    private synchronized void searchOnce(Context context, ClassLoader loader) {
        if (searched) return;
        searched = true;
        Log.i(TAG, "Artwork render hooks: " + install(context, loader));
    }

    private Context currentApplication() {
        try {
            Class<?> thread = Class.forName("android.app.ActivityThread");
            return (Context) thread.getMethod("currentApplication").invoke(null);
        } catch (Throwable ignored) { return null; }
    }

    private int install(Context context, ClassLoader loader) {
        List<String> names = new ArrayList<>();
        try {
            DexFile dex = new DexFile(context.getApplicationInfo().sourceDir);
            Enumeration<String> entries = dex.entries();
            while (entries.hasMoreElements()) {
                String name = entries.nextElement();
                if (name.indexOf('.') < 0) names.add(name);
            }
            dex.close();
        } catch (Throwable error) { Log.w(TAG, "Cannot enumerate Wallet classes", error); }
        Log.i(TAG, "Wallet classes inspected: " + names.size());
        int count = 0;
        for (String name : names) {
            try {
                Class<?> type = Class.forName(name, false, loader);
                for (Method method : type.getDeclaredMethods()) {
                    Class<?>[] p = method.getParameterTypes();
                    if (p.length != 3 || p[1] != ImageView.class ||
                            !Drawable.class.isAssignableFrom(p[0]) ||
                            method.getReturnType() != Void.TYPE ||
                            !hasUriField(p[2])) continue;
                    if (!installed.add(method)) continue;
                    method.setAccessible(true);
                    hook(method).setPriority(XposedInterface.PRIORITY_DEFAULT)
                            .setExceptionMode(XposedInterface.ExceptionMode.PROTECTIVE)
                            .setId("zetacard-" + method.toGenericString().hashCode())
                            .intercept(chain -> {
                                Object result = chain.proceed();
                                try { paint(context, chain.getArgs().toArray()); }
                                catch (Throwable error) { Log.w(TAG, "Artwork skipped", error); }
                                return result;
                            });
                    count++;
                    Log.i(TAG, "Renderer candidate: " + method.toGenericString());
                    installDrawHook(context, p[0]);
                }
            } catch (Throwable ignored) { }
        }
        installComposeCards(context, loader);
        return count;
    }

    private void installComposeCards(Context context, ClassLoader loader) {
        try {
            Class<?> cardState = Class.forName("badc", false, loader);
            Class<?> keyedState = Class.forName("sdb", false, loader);
            Class<?> urlModel = Class.forName("awmf", false, loader);
            Class<?> imageModel = Class.forName("awmg", false, loader);
            Class<?> bitmapModel = Class.forName("awlt", false, loader);
            Field cardArt = cardState.getDeclaredField("a");
            Field url = urlModel.getDeclaredField("a");
            cardArt.setAccessible(true);
            url.setAccessible(true);
            Constructor<?> bitmapConstructor = bitmapModel.getDeclaredConstructor(Bitmap.class);
            bitmapConstructor.setAccessible(true);
            Constructor<?> cardConstructor = keyedState.getDeclaredConstructor(cardState, Object.class);
            cardConstructor.setAccessible(true);
            hook(cardConstructor).setExceptionMode(XposedInterface.ExceptionMode.PROTECTIVE)
                    .setId("zetacard-compose-discover")
                    .intercept(chain -> {
                        Object art = cardArt.get(chain.getArgs().get(0));
                        if (urlModel.isInstance(art)) {
                            String source = (String) url.get(art);
                            Uri uri = Uri.parse(source);
                            if ("https".equals(uri.getScheme())) {
                                synchronized (composeCardUrls) { composeCardUrls.add(source); }
                                String id = sha256(source);
                                Bundle request = new Bundle();
                                request.putString("id", id);
                                request.putString("label", "Payment card");
                                request.putString("stockUrl", source);
                                context.getContentResolver().call(ArtProvider.URI, "discover", null, request);
                            }
                        }
                        return chain.proceed();
                    });
            Class<?> image = Class.forName("azmh", false, loader);
            for (Method method : image.getDeclaredMethods()) {
                Class<?>[] p = method.getParameterTypes();
                if (!Modifier.isStatic(method.getModifiers()) || !method.getName().equals("b") ||
                        p.length != 11 || p[0] != imageModel) continue;
                method.setAccessible(true);
                hook(method).setExceptionMode(XposedInterface.ExceptionMode.PROTECTIVE)
                        .setId("zetacard-compose-art")
                        .intercept(chain -> {
                            try {
                                Object art = chain.getArgs().get(0);
                                if (urlModel.isInstance(art)) {
                                    String source = (String) url.get(art);
                                    boolean card;
                                    synchronized (composeCardUrls) { card = composeCardUrls.contains(source); }
                                    if (card) {
                                        String id = sha256(source);
                                        Bundle request = new Bundle();
                                        request.putString("id", id);
                                        Bundle answer = context.getContentResolver().call(ArtProvider.URI, "art", null, request);
                                        String path = answer == null ? null : answer.getString("uri");
                                        if (path != null) {
                                            String cacheKey = id + ":" + answer.getLong("revision");
                                            Bitmap bitmap = bitmaps.get(cacheKey);
                                            if (bitmap == null) {
                                                try (java.io.InputStream stream = context.getContentResolver().openInputStream(Uri.parse(path))) {
                                                    bitmap = BitmapFactory.decodeStream(stream);
                                                }
                                                if (bitmap != null) bitmaps.put(cacheKey, bitmap);
                                            }
                                            if (bitmap != null) {
                                                Object[] args = chain.getArgs().toArray();
                                                args[0] = bitmapConstructor.newInstance(bitmap);
                                                return chain.proceed(args);
                                            }
                                        }
                                    }
                                }
                            } catch (Throwable error) { Log.w(TAG, "Compose artwork skipped", error); }
                            return chain.proceed();
                        });
                break;
            }
            Log.i(TAG, "Compose card hooks installed");
        } catch (Throwable error) { Log.w(TAG, "Compose card hooks unavailable", error); }
    }

    private void installDrawHook(Context context, Class<?> type) {
        if (!drawInstalled.add(type)) return;
        try {
            Field background = null;
            Field customFlag = null;
            for (Field field : type.getDeclaredFields()) {
                if (field.getType() == Drawable.class) background = field;
                if (field.getType() == Boolean.TYPE && !Modifier.isFinal(field.getModifiers())) customFlag = field;
            }
            if (background == null || customFlag == null) return;
            background.setAccessible(true);
            customFlag.setAccessible(true);
            Field artField = background;
            Field flagField = customFlag;
            Method draw = type.getDeclaredMethod("draw", Canvas.class);
            draw.setAccessible(true);
            hook(draw).setExceptionMode(XposedInterface.ExceptionMode.PROTECTIVE)
                    .setId("zetacard-draw-" + type.getName())
                    .intercept(chain -> {
                        Drawable card = (Drawable) chain.getThisObject();
                        BitmapDrawable art;
                        String id;
                        synchronized (replacements) { art = replacements.get(card); }
                        synchronized (cardIds) { id = cardIds.get(card); }
                        Drawable current = (Drawable) artField.get(card);
                        if (id != null && current != null && current != art &&
                                flagField.getBoolean(card) && !capturedStock.contains(id)) {
                            try {
                                Bitmap stock = Bitmap.createBitmap(700, 440, Bitmap.Config.ARGB_8888);
                                Canvas canvas = new Canvas(stock);
                                android.graphics.Rect old = current.getBounds();
                                current.setBounds(0, 0, 700, 440);
                                current.draw(canvas);
                                current.setBounds(old);
                                ByteArrayOutputStream bytes = new ByteArrayOutputStream();
                                stock.compress(Bitmap.CompressFormat.PNG, 100, bytes);
                                stock.recycle();
                                Bundle data = new Bundle();
                                data.putString("id", id);
                                data.putByteArray("image", bytes.toByteArray());
                                context.getContentResolver().call(ArtProvider.URI, "stock", null, data);
                                capturedStock.add(id);
                            } catch (Throwable error) { Log.w(TAG, "Stock preview skipped", error); }
                        }
                        if (art != null) {
                            artField.set(card, art);
                            flagField.setBoolean(card, true);
                        }
                        return chain.proceed();
                    });
        } catch (Throwable error) { Log.w(TAG, "Card background hook unavailable", error); }
    }

    private boolean hasUriField(Class<?> type) {
        for (Field f : type.getDeclaredFields()) if (f.getType() == Uri.class) return true;
        return false;
    }

    private void paint(Context context, Object[] args) throws Exception {
        if (args.length != 3 || !(args[1] instanceof ImageView) || args[2] == null) return;
        Uri original = null;
        String label = "Payment card";
        for (Field f : args[2].getClass().getDeclaredFields()) {
            if (Modifier.isStatic(f.getModifiers())) continue;
            f.setAccessible(true);
            Object value = f.get(args[2]);
            if (value instanceof Uri && "https".equals(((Uri) value).getScheme())) original = (Uri) value;
            if (value instanceof String) {
                String s = ((String) value).trim();
                if (s.length() >= 3 && s.length() <= 48 && !s.contains("/") &&
                        !s.matches(".*[0-9]{8,}.*") && !s.matches("[0-9a-fA-F]{32,}")) label = s;
            }
        }
        if (original == null) return;
        String id = sha256(original.toString());
        synchronized (cardIds) { cardIds.put((Drawable) args[0], id); }
        Bundle request = new Bundle();
        request.putString("id", id);
        request.putString("label", label);
        context.getContentResolver().call(ArtProvider.URI, "discover", null, request);
        Bundle answer = context.getContentResolver().call(ArtProvider.URI, "art", null, request);
        String path = answer == null ? null : answer.getString("uri");
        Drawable card = (Drawable) args[0];
        if (path == null) {
            synchronized (replacements) { replacements.remove(card); }
            return;
        }
        String cacheKey = id + ":" + answer.getLong("revision");
        Bitmap bitmap = bitmaps.get(cacheKey);
        if (bitmap == null) {
            try (java.io.InputStream stream = context.getContentResolver().openInputStream(Uri.parse(path))) {
                bitmap = BitmapFactory.decodeStream(stream);
            }
            if (bitmap != null) bitmaps.put(cacheKey, bitmap);
        }
        if (bitmap == null) return;
        synchronized (replacements) {
            replacements.put(card, new BitmapDrawable(context.getResources(), bitmap));
        }
        card.invalidateSelf();
    }

    private static String sha256(String value) throws Exception {
        byte[] digest = MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8));
        StringBuilder hex = new StringBuilder(64);
        for (byte b : digest) hex.append(String.format("%02x", b & 0xff));
        return hex.toString();
    }
}
