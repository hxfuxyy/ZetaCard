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
    private final Set<String> loggedCardIds = new HashSet<>();
    private final Set<String> loggedCustomIds = new HashSet<>();
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
        List<Class<?>> classes = new ArrayList<>(names.size());
        int count = 0;
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
        installComposeCards(context, classes);
        return count;
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

    private void installComposeCards(Context context, List<Class<?>> classes) {
        try {
            ComposeLayout layout = findComposeLayout(classes);
            if (layout == null) {
                Log.w(TAG, "Compose card structure not found in this Wallet version");
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
                                context.getContentResolver().call(ArtProvider.URI, "discover", null, request);
                            }
                        }
                        return chain.proceed();
                    });
            hook(layout.renderer).setExceptionMode(XposedInterface.ExceptionMode.PROTECTIVE)
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
                                            synchronized (loggedCustomIds) {
                                                if (loggedCustomIds.add(id)) Log.i(TAG, "Compose custom artwork found: " + id.substring(0, 8));
                                            }
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
            Log.i(TAG, "Compose card hooks installed: " + cardConstructor.getDeclaringClass().getName()
                    + " / " + layout.renderer.getDeclaringClass().getName());
        } catch (Throwable error) { Log.w(TAG, "Compose card hooks unavailable", error); }
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
