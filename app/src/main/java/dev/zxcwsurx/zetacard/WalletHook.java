package dev.zxcwsurx.zetacard;

import android.app.Application;
import android.content.Context;
import android.content.SharedPreferences;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.Drawable;
import android.net.Uri;
import android.os.Bundle;
import android.os.ParcelFileDescriptor;
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
    private final Set<String> loggedCardIds = new HashSet<>();
    private final Set<String> loggedCustomIds = new HashSet<>();
    private final Set<String> loggedFailures = new HashSet<>();
    private volatile long providerRetryAfter;
    private boolean loggedComposeState;
    private boolean loggedComposeRender;
    private boolean searched;

    @Override public boolean onHotReloading(XposedModuleInterface.HotReloadingParam param) {
        return true;
    }

    @Override public void onHotReloaded(XposedModuleInterface.HotReloadedParam param) {
        for (XposedInterface.HookHandle handle : param.getOldHookHandles()) handle.unhook();
        Context context = currentApplication();
        if (context != null) searchOnce(context, context.getClassLoader());
        else failure("reload-context", "Wallet context unavailable after hook reload", null);
    }

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
        try { event("Artwork render hooks: " + install(context, loader)); }
        catch (Throwable error) { failure("scan", "Hook setup failed", error); }
    }

    private void event(String message) {
        Log.i(TAG, message);
        try { log(Log.INFO, TAG, message); } catch (Throwable ignored) { }
    }

    private void failure(String key, String message, Throwable error) {
        synchronized (loggedFailures) { if (!loggedFailures.add(key)) return; }
        String detail = error == null ? "" : ": " + error.getClass().getSimpleName() +
                (error.getMessage() == null ? "" : " - " + error.getMessage());
        Log.w(TAG, message + detail);
        try { log(Log.WARN, TAG, message + detail); } catch (Throwable ignored) { }
    }

    private Context currentApplication() {
        try {
            Class<?> thread = Class.forName("android.app.ActivityThread");
            return (Context) thread.getMethod("currentApplication").invoke(null);
        } catch (Throwable ignored) { return null; }
    }

    private int install(Context context, ClassLoader loader) {
        SharedPreferences cache = context.getSharedPreferences("zetacard_hook_cache", Context.MODE_PRIVATE);
        long version = walletVersion(context);
        long requestedScan = 0;
        try { requestedScan = getRemotePreferences("control").getLong("scan", 0); }
        catch (Throwable error) { failure("control", "LSPosed scan control unavailable", error); }
        String savedCompose = cache.getString("compose", null);
        if (version != -1 && cache.getLong("version", -1) == version &&
                cache.getLong("scan", 0) == requestedScan && savedCompose != null) {
            try {
                ComposeLayout layout = restoreComposeLayout(loader, savedCompose);
                List<Method> legacy = restoreLegacyMethods(loader, cache.getString("legacy", ""));
                for (Method method : legacy) installLegacyHook(context, method);
                installComposeCards(context, layout);
                event("Using saved hook map for Wallet " + version);
                return legacy.size();
            } catch (Throwable error) { failure("cache", "Saved hook map invalid; scanning Wallet", error); }
        }
        event("Scanning Wallet " + version + " for artwork hooks");
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
        List<Class<?>> classes = new ArrayList<>(names.size());
        int count = 0;
        List<String> legacyNames = new ArrayList<>();
        for (String name : names) {
            try {
                Class<?> type = Class.forName(name, false, loader);
                classes.add(type);
                for (Method method : type.getDeclaredMethods()) {
                    Class<?>[] p = method.getParameterTypes();
                    if (p.length != 3 || p[1] != ImageView.class ||
                            !Drawable.class.isAssignableFrom(p[0]) ||
                            method.getReturnType() != Void.TYPE ||
                            !hasUriField(p[2])) continue;
                    installLegacyHook(context, method);
                    count++;
                    legacyNames.add(type.getName() + "|" + method.getName() + "|" + p[0].getName() + "|" + p[2].getName());
                    Log.i(TAG, "Renderer candidate: " + method.toGenericString());
                }
            } catch (Throwable ignored) { }
        }
        ComposeLayout layout = findComposeLayout(classes);
        installComposeCards(context, layout);
        if (version != -1) {
            cache.edit().putLong("version", version).putString("compose", layout == null ? "NONE" : saveComposeLayout(layout))
                    .putString("legacy", String.join(";", legacyNames)).putLong("scan", requestedScan).commit();
            event("Hook map saved for Wallet " + version);
        }
        return count;
    }

    private long walletVersion(Context context) {
        try { return context.getPackageManager().getPackageInfo(WALLET, 0).getLongVersionCode(); }
        catch (Exception ignored) { return -1; }
    }

    private void installLegacyHook(Context context, Method method) {
        if (!installed.add(method)) return;
        method.setAccessible(true);
        hook(method).setPriority(XposedInterface.PRIORITY_DEFAULT)
                .setExceptionMode(XposedInterface.ExceptionMode.PROTECTIVE)
                .setId("zetacard-" + method.toGenericString().hashCode())
                .intercept(chain -> {
                    Object result = chain.proceed();
                    try { paint(context, chain.getArgs().toArray()); }
                    catch (Throwable error) { failure("legacy-art", "Legacy artwork skipped", error); }
                    return result;
                });
        installDrawHook(context, method.getParameterTypes()[0]);
    }

    private List<Method> restoreLegacyMethods(ClassLoader loader, String encoded) throws Exception {
        List<Method> methods = new ArrayList<>();
        if (encoded.isEmpty()) return methods;
        for (String part : encoded.split(";")) {
            String[] fields = part.split("\\|", -1);
            if (fields.length != 4) throw new IllegalArgumentException("Invalid legacy hook cache");
            Class<?> type = Class.forName(fields[0], false, loader);
            Class<?> drawable = Class.forName(fields[2], false, loader);
            Class<?> data = Class.forName(fields[3], false, loader);
            methods.add(type.getDeclaredMethod(fields[1], drawable, ImageView.class, data));
        }
        return methods;
    }

    private static final class ComposeLayout {
        final Class<?> urlModel;
        final Field cardArt;
        final Field url;
        final Constructor<?> bitmapConstructor;
        final Constructor<?> cardConstructor;
        final Method renderer;

        ComposeLayout(Class<?> urlModel, Field cardArt, Field url,
                Constructor<?> bitmapConstructor, Constructor<?> cardConstructor, Method renderer) {
            this.urlModel = urlModel;
            this.cardArt = cardArt;
            this.url = url;
            this.bitmapConstructor = bitmapConstructor;
            this.cardConstructor = cardConstructor;
            this.renderer = renderer;
        }
    }

    private String saveComposeLayout(ComposeLayout layout) {
        return String.join("|", layout.cardConstructor.getParameterTypes()[0].getName(),
                layout.cardConstructor.getDeclaringClass().getName(), layout.urlModel.getName(),
                layout.bitmapConstructor.getDeclaringClass().getName(), layout.cardArt.getName(),
                layout.url.getName(), layout.renderer.getDeclaringClass().getName(), layout.renderer.getName());
    }

    private ComposeLayout restoreComposeLayout(ClassLoader loader, String encoded) throws Exception {
        if ("NONE".equals(encoded)) return null;
        String[] fields = encoded.split("\\|", -1);
        if (fields.length != 8) throw new IllegalArgumentException("Invalid Compose hook cache");
        Class<?> cardState = Class.forName(fields[0], false, loader);
        Class<?> keyedState = Class.forName(fields[1], false, loader);
        Class<?> urlModel = Class.forName(fields[2], false, loader);
        Class<?> bitmapModel = Class.forName(fields[3], false, loader);
        Class<?> imageModel = bitmapModel.getSuperclass();
        Field cardArt = cardState.getDeclaredField(fields[4]);
        Field url = urlModel.getDeclaredField(fields[5]);
        Constructor<?> bitmapConstructor = bitmapModel.getDeclaredConstructor(Bitmap.class);
        Constructor<?> cardConstructor = keyedState.getDeclaredConstructor(cardState, Object.class);
        Class<?> rendererType = Class.forName(fields[6], false, loader);
        Method renderer = null;
        for (Method method : rendererType.getDeclaredMethods()) {
            Class<?>[] p = method.getParameterTypes();
            if (method.getName().equals(fields[7]) && Modifier.isStatic(method.getModifiers()) &&
                    method.getReturnType() == Void.TYPE && p.length == 11 && p[0] == imageModel &&
                    p[1] == String.class && p[5] == Float.TYPE && p[9] == Integer.TYPE &&
                    p[10] == Integer.TYPE) { renderer = method; break; }
        }
        if (renderer == null) throw new NoSuchMethodException("Saved Compose renderer");
        cardArt.setAccessible(true);
        url.setAccessible(true);
        bitmapConstructor.setAccessible(true);
        cardConstructor.setAccessible(true);
        renderer.setAccessible(true);
        return new ComposeLayout(urlModel, cardArt, url, bitmapConstructor, cardConstructor, renderer);
    }

    private void installComposeCards(Context context, ComposeLayout layout) {
        try {
            if (layout == null) {
                failure("compose-missing", "Compose card structure not found", null);
                return;
            }
            Field cardArt = layout.cardArt;
            Field url = layout.url;
            Class<?> urlModel = layout.urlModel;
            Constructor<?> bitmapConstructor = layout.bitmapConstructor;
            Constructor<?> cardConstructor = layout.cardConstructor;
            hook(cardConstructor).setExceptionMode(XposedInterface.ExceptionMode.PROTECTIVE)
                    .setId("zetacard-compose-discover")
                    .intercept(chain -> {
                        Object art = cardArt.get(chain.getArgs().get(0));
                        if (!loggedComposeState) {
                            loggedComposeState = true;
                            event("Card state artwork model: " + (art == null ? "none" : art.getClass().getName()));
                        }
                        if (urlModel.isInstance(art)) {
                            String source = (String) url.get(art);
                            Uri uri = Uri.parse(source);
                            if ("https".equals(uri.getScheme())) {
                                synchronized (composeCardUrls) { composeCardUrls.add(source); }
                                String id = sha256(source);
                                synchronized (loggedCardIds) {
                                    if (loggedCardIds.add(id)) Log.i(TAG, "Compose card discovered: " + id.substring(0, 8));
                                }
                                Bundle request = new Bundle();
                                request.putString("id", id);
                                request.putString("label", "Payment card");
                                request.putString("stockUrl", source);
                                try { context.getContentResolver().call(ArtProvider.URI, "discover", null, request); }
                                catch (RuntimeException error) { failure("provider-discover", "Card list unavailable", error); }
                            }
                        }
                        return chain.proceed();
                    });
            hook(layout.renderer).setExceptionMode(XposedInterface.ExceptionMode.PROTECTIVE)
                        .setId("zetacard-compose-art")
                        .intercept(chain -> {
                            try {
                                Object art = chain.getArgs().get(0);
                                if (!loggedComposeRender) {
                                    loggedComposeRender = true;
                                    event("Artwork renderer model: " + (art == null ? "none" : art.getClass().getName()));
                                }
                                if (urlModel.isInstance(art)) {
                                    String source = (String) url.get(art);
                                    boolean card;
                                    synchronized (composeCardUrls) { card = composeCardUrls.contains(source); }
                                    if (card) {
                                        String id = sha256(source);
                                        Bitmap bitmap = loadArt(context, id);
                                        if (bitmap != null) {
                                            synchronized (loggedCustomIds) {
                                                if (loggedCustomIds.add(id)) event("Custom artwork active: " + id.substring(0, 8));
                                            }
                                            Object[] args = chain.getArgs().toArray();
                                            args[0] = bitmapConstructor.newInstance(bitmap);
                                            return chain.proceed(args);
                                        }
                                    }
                                }
                            } catch (Throwable error) { failure("compose-art", "Compose artwork skipped", error); }
                            return chain.proceed();
                        });
            event("Compose card hooks installed: " + cardConstructor.getDeclaringClass().getName()
                    + " / " + layout.renderer.getDeclaringClass().getName());
        } catch (Throwable error) { failure("compose-install", "Compose card hooks unavailable", error); }
    }

    private ComposeLayout findComposeLayout(List<Class<?>> classes) {
        List<Class<?>> bitmapModels = new ArrayList<>();
        List<Class<?>> urlModels = new ArrayList<>();
        List<Class<?>> cardStates = new ArrayList<>();
        List<Class<?>> keyedStates = new ArrayList<>();
        List<Method> renderers = new ArrayList<>();
        for (Class<?> type : classes) {
            try {
                int instanceFields = 0;
                int bitmapFields = 0;
                int stringFields = 0;
                int booleanFields = 0;
                for (Field field : type.getDeclaredFields()) {
                    if (Modifier.isStatic(field.getModifiers())) continue;
                    instanceFields++;
                    if (field.getType() == Bitmap.class) bitmapFields++;
                    if (field.getType() == String.class) stringFields++;
                    if (field.getType() == Boolean.TYPE) booleanFields++;
                }
                Class<?> parent = type.getSuperclass();
                if (parent != null && parent != Object.class) {
                    if (instanceFields == 1 && bitmapFields == 1 && hasConstructor(type, Bitmap.class))
                        bitmapModels.add(type);
                    if (instanceFields >= 3 && instanceFields <= 4 && stringFields == 2 &&
                            hasUrlConstructor(type)) urlModels.add(type);
                }
                if (instanceFields >= 8 && instanceFields <= 10 && booleanFields == 3)
                    cardStates.add(type);
                if (instanceFields == 2) keyedStates.add(type);
                for (Method method : type.getDeclaredMethods()) {
                    Class<?>[] p = method.getParameterTypes();
                    if (Modifier.isStatic(method.getModifiers()) && method.getReturnType() == Void.TYPE &&
                            p.length == 11 && p[1] == String.class && p[5] == Float.TYPE &&
                            p[9] == Integer.TYPE && p[10] == Integer.TYPE) renderers.add(method);
                }
            } catch (Throwable ignored) { }
        }
        Log.i(TAG, "Compose structure candidates: " + bitmapModels.size() + " bitmap, " +
                urlModels.size() + " URL, " + cardStates.size() + " state, " + renderers.size() + " renderer");
        List<ComposeLayout> matches = new ArrayList<>();
        for (Class<?> bitmapModel : bitmapModels) {
            Class<?> imageModel = bitmapModel.getSuperclass();
            for (Class<?> urlModel : urlModels) {
                if (urlModel.getSuperclass() != imageModel) continue;
                Field url = null;
                for (Field field : urlModel.getDeclaredFields()) {
                    if (!Modifier.isStatic(field.getModifiers()) && field.getType() == String.class) {
                        url = field;
                        break;
                    }
                }
                if (url == null) continue;
                for (Class<?> cardState : cardStates) {
                    Field cardArt = null;
                    for (Field field : cardState.getDeclaredFields()) {
                        if (!Modifier.isStatic(field.getModifiers()) && field.getType() == imageModel) {
                            cardArt = field;
                            break;
                        }
                    }
                    if (cardArt == null || !hasCardConstructor(cardState, imageModel)) continue;
                    for (Class<?> keyedState : keyedStates) {
                        Constructor<?> cardConstructor;
                        try { cardConstructor = keyedState.getDeclaredConstructor(cardState, Object.class); }
                        catch (NoSuchMethodException ignored) { continue; }
                        for (Method renderer : renderers) {
                            if (renderer.getParameterTypes()[0] != imageModel) continue;
                            try {
                                Constructor<?> bitmapConstructor = bitmapModel.getDeclaredConstructor(Bitmap.class);
                                matches.add(new ComposeLayout(urlModel, cardArt, url,
                                        bitmapConstructor, cardConstructor, renderer));
                            } catch (NoSuchMethodException ignored) { }
                        }
                    }
                }
            }
        }
        if (matches.size() != 1) {
            Log.w(TAG, "Compose structure matches: " + matches.size());
            return null;
        }
        ComposeLayout result = matches.get(0);
        result.cardArt.setAccessible(true);
        result.url.setAccessible(true);
        result.bitmapConstructor.setAccessible(true);
        result.cardConstructor.setAccessible(true);
        result.renderer.setAccessible(true);
        return result;
    }

    private boolean hasConstructor(Class<?> type, Class<?> parameter) {
        try { type.getDeclaredConstructor(parameter); return true; }
        catch (NoSuchMethodException ignored) { return false; }
    }

    private boolean hasUrlConstructor(Class<?> type) {
        for (Constructor<?> constructor : type.getDeclaredConstructors()) {
            Class<?>[] p = constructor.getParameterTypes();
            if (p.length >= 3 && p[0] == String.class && p[1] == String.class) return true;
        }
        return false;
    }

    private boolean hasCardConstructor(Class<?> type, Class<?> imageModel) {
        for (Constructor<?> constructor : type.getDeclaredConstructors()) {
            Class<?>[] p = constructor.getParameterTypes();
            if (p.length >= 8 && p.length <= 9 && p[0] == imageModel) return true;
        }
        return false;
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

    private Bitmap loadArt(Context context, String id) {
        if (android.os.SystemClock.elapsedRealtime() >= providerRetryAfter) try {
            Bundle request = new Bundle();
            request.putString("id", id);
            Bundle answer = context.getContentResolver().call(ArtProvider.URI, "art", null, request);
            String path = answer == null ? null : answer.getString("uri");
            if (path != null) {
                String key = id + ":provider:" + answer.getLong("revision");
                Bitmap cached = bitmaps.get(key);
                if (cached != null) return cached;
                try (java.io.InputStream stream = context.getContentResolver().openInputStream(Uri.parse(path))) {
                    Bitmap image = BitmapFactory.decodeStream(stream);
                    if (image != null) bitmaps.put(key, image);
                    return image;
                }
            }
        } catch (Throwable error) {
            providerRetryAfter = android.os.SystemClock.elapsedRealtime() + 5000;
            failure("provider-art", "Direct artwork access unavailable; using LSPosed data", error);
        }
        try {
            long revision = getRemotePreferences("art").getLong(id, -1);
            if (revision < 0) return null;
            String key = id + ":remote:" + revision;
            Bitmap cached = bitmaps.get(key);
            if (cached != null) return cached;
            try (ParcelFileDescriptor file = openRemoteFile("art_" + id)) {
                Bitmap image = BitmapFactory.decodeFileDescriptor(file.getFileDescriptor());
                if (image != null) bitmaps.put(key, image);
                return image;
            }
        } catch (Throwable error) { failure("remote-art", "LSPosed artwork unavailable", error); }
        return null;
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
        synchronized (loggedCardIds) {
            if (loggedCardIds.add(id)) Log.i(TAG, "Legacy card discovered: " + id.substring(0, 8));
        }
        synchronized (cardIds) { cardIds.put((Drawable) args[0], id); }
        Bundle request = new Bundle();
        request.putString("id", id);
        request.putString("label", label);
        try { context.getContentResolver().call(ArtProvider.URI, "discover", null, request); }
        catch (RuntimeException error) { failure("provider-discover", "Card list unavailable", error); }
        Drawable card = (Drawable) args[0];
        Bitmap bitmap = loadArt(context, id);
        if (bitmap == null) {
            synchronized (replacements) { replacements.remove(card); }
            return;
        }
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
