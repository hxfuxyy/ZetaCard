package io.github.hxfuxyy.zetacard.wallet;

import android.annotation.SuppressLint;
import android.app.Application;
import android.content.Context;
import android.content.SharedPreferences;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Rect;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.Drawable;
import android.net.Uri;
import android.os.ParcelFileDescriptor;
import android.os.Process;
import android.util.Log;
import android.util.LruCache;
import android.widget.ImageView;
import dalvik.system.DexFile;
import io.github.hxfuxyy.zetacard.core.CardIds;
import io.github.hxfuxyy.zetacard.core.CardLabels;
import io.github.hxfuxyy.zetacard.core.CardRegistry;
import io.github.hxfuxyy.zetacard.core.LogSanitizer;
import io.github.hxfuxyy.zetacard.data.ArtSync;
import io.github.hxfuxyy.zetacard.image.CaptionRenderer;
import io.github.libxposed.api.XposedInterface;
import io.github.libxposed.api.XposedModule;
import io.github.libxposed.api.XposedModuleInterface;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Enumeration;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.WeakHashMap;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public final class WalletHook extends XposedModule {
    private static final String TAG = "ZetaCard";
    private static final String WALLET = "com.google.android.apps.walletnfcrel";
    private static final String HOOK_CACHE = "zetacard_hook_cache";
    private static final String REGISTRY_FILE = "zetacard_cards.tsv";
    private static final String HEARTBEAT_FILE = "zetacard_heartbeat";
    private static final long HEARTBEAT_INTERVAL_MS = 1500L;
    private static final long ART_RETRY_MS = 15_000L;
    private static final String DISK_CACHE_MARKER = "image_manager_disk_cache";
    private static final long SCAN_BUDGET_MS = 45_000L;
    private static final int STOCK_WIDTH = 700;
    private static final int STOCK_HEIGHT = 440;

    private final Set<Method> installed = ConcurrentHashMap.newKeySet();
    private final Set<Class<?>> drawInstalled = ConcurrentHashMap.newKeySet();
    private final Map<Drawable, BitmapDrawable> replacements = Collections.synchronizedMap(new WeakHashMap<>());
    private final Map<Drawable, String> cardIds = Collections.synchronizedMap(new WeakHashMap<>());
    private final Set<String> capturedStock = ConcurrentHashMap.newKeySet();
    private final Set<String> composeCardUrls = ConcurrentHashMap.newKeySet();
    private final Set<String> loggedOnce = ConcurrentHashMap.newKeySet();
    private final Map<String, Long> failedArt = new ConcurrentHashMap<>();
    private final Map<String, CardRegistry.Entry> discovered = new LinkedHashMap<>();
    private final LruCache<String, Bitmap> bitmaps = new LruCache<String, Bitmap>(16 * 1024) {
        @Override protected int sizeOf(String key, Bitmap value) { return value.getByteCount() / 1024; }
    };
    private final ComposeLayoutFinder finder = new ComposeLayoutFinder(Bitmap.class);
    private final ExecutorService storage = Executors.newSingleThreadExecutor(runnable -> {
        Thread thread = new Thread(runnable, "ZetaCard Wallet storage");
        thread.setDaemon(true);
        return thread;
    });
    private String pendingRegistry;
    private volatile long lastHeartbeat;
    private volatile WalletEventLog eventLog;
    private volatile boolean composeInstalled;
    private boolean searched;

    @SuppressLint("DiscouragedPrivateApi")
    @Override public void onModuleLoaded(XposedModuleInterface.ModuleLoadedParam param) {
        if (!WALLET.equals(param.getProcessName())) return;
        try {
            Method attach = Application.class.getDeclaredMethod("attach", Context.class);
            attach.setAccessible(true);
            hook(attach).setExceptionMode(XposedInterface.ExceptionMode.PROTECTIVE)
                    .setId("zetacard-application-attach")
                    .intercept(chain -> {
                        Object result = chain.proceed();
                        Context context = (Context) chain.getArgs().get(0);
                        start(context, context.getClassLoader());
                        return result;
                    });
        } catch (Throwable error) {
            Log.e(TAG, "Could not prepare Wallet hooks: " + LogSanitizer.describe(error));
        }
    }

    @Override public void onPackageReady(XposedModuleInterface.PackageReadyParam param) {
        if (!WALLET.equals(param.getPackageName()) || !WALLET.equals(Application.getProcessName())) return;
        Context context = currentApplication();
        if (context != null) start(context, param.getClassLoader());
    }

    private synchronized void start(Context context, ClassLoader loader) {
        if (searched) return;
        searched = true;
        try {
            eventLog = new WalletEventLog(context.getFilesDir());
            long version = walletVersion(context);
            event("Module loaded in Wallet " + version);
            seedFromRegistry(context);
            preloadArtwork();
            installDiskCacheHook(context);
            if (restoreFromCache(context, loader, version)) return;
            SharedPreferences cache = context.getSharedPreferences(HOOK_CACHE, Context.MODE_PRIVATE);
            if (isKnownUnsupported(cache, version)) {
                warn("unsupported", "Wallet " + version + " was scanned before without a supported card layout; " +
                        "update ZetaCard or reinstall it to rescan", null);
                return;
            }
            Thread scanner = new Thread(() -> scan(context, loader, version), "ZetaCard Wallet scan");
            scanner.setDaemon(true);
            scanner.start();
        } catch (Throwable error) {
            warn("start", "Hook setup failed", error);
        }
    }

    private void seedFromRegistry(Context context) {
        File file = new File(context.getFilesDir(), REGISTRY_FILE);
        if (!file.isFile() || file.length() > 1024 * 1024) return;
        try {
            String text = new String(java.nio.file.Files.readAllBytes(file.toPath()), StandardCharsets.UTF_8);
            synchronized (discovered) {
                for (CardRegistry.Entry entry : CardRegistry.parse(text)) {
                    discovered.put(entry.id, entry);
                    if (!entry.stockUrl.isEmpty() && CardIds.fromSource(entry.stockUrl).equals(entry.id)) {
                        composeCardUrls.add(entry.stockUrl);
                    }
                }
            }
        } catch (Throwable error) {
            warn("registry-read", "Saved card list unreadable", error);
        }
    }

    private void preloadArtwork() {
        List<String> ids;
        synchronized (discovered) {
            ids = new ArrayList<>(discovered.keySet());
        }
        if (ids.isEmpty()) return;
        storage.execute(() -> {
            for (String id : ids) loadArt(id);
        });
    }

    private boolean restoreFromCache(Context context, ClassLoader loader, long version) {
        SharedPreferences cache = context.getSharedPreferences(HOOK_CACHE, Context.MODE_PRIVATE);
        String savedCompose = cache.getString("compose", null);
        if (version == -1 || cache.getLong("version", -1) != version ||
                cache.getLong("module", -1) != moduleStamp() || savedCompose == null) return false;
        try {
            List<ComposeLayoutFinder.Layout> layouts = "NONE".equals(savedCompose)
                    ? Collections.emptyList() : finder.restoreAll(loader, savedCompose);
            List<Method> legacy = restoreLegacyMethods(loader, cache.getString("legacy", ""));
            for (Method method : legacy) installLegacyHook(context, method);
            if (!layouts.isEmpty()) installComposeCards(context, layouts);
            event("Using saved hook map for Wallet " + version + " (" + legacy.size() + " legacy hook(s))");
            return true;
        } catch (Throwable error) {
            warn("cache", "Saved hook map invalid; rescanning Wallet", error);
            cache.edit().clear().apply();
            return false;
        }
    }

    private boolean isKnownUnsupported(SharedPreferences cache, long version) {
        return version != -1 && cache.getLong("unsupported_version", -1) == version &&
                cache.getLong("unsupported_module", -1) == moduleStamp();
    }

    private void scan(Context context, ClassLoader loader, long version) {
        try {
            Process.setThreadPriority(Process.THREAD_PRIORITY_BACKGROUND);
        } catch (Throwable ignored) {
        }
        long started = System.nanoTime();
        try {
            event("Scanning Wallet " + version + " for artwork hooks in the background");
            List<String> names = walletClassNames(context);
            List<Class<?>> classes = new ArrayList<>(names.size());
            List<String> legacyNames = new ArrayList<>();
            int legacy = 0;
            long deadline = started + SCAN_BUDGET_MS * 1_000_000L;
            boolean outOfTime = false;
            for (int index = 0; index < names.size(); index++) {
                if ((index & 255) == 0 && System.nanoTime() > deadline) {
                    outOfTime = true;
                    break;
                }
                String name = names.get(index);
                try {
                    Class<?> type = Class.forName(name, false, loader);
                    classes.add(type);
                    for (Method method : type.getDeclaredMethods()) {
                        if (!isLegacyRenderer(method)) continue;
                        Class<?>[] p = method.getParameterTypes();
                        installLegacyHook(context, method);
                        legacy++;
                        legacyNames.add(type.getName() + "|" + method.getName() + "|" + p[0].getName() + "|" + p[2].getName());
                    }
                } catch (Throwable ignored) {
                }
            }
            long remaining = (deadline - System.nanoTime()) / 1_000_000L;
            ComposeLayoutFinder.Result result = outOfTime || remaining <= 0
                    ? ComposeLayoutFinder.timedOutBeforeMatching() : finder.find(classes, remaining);
            int inspected = classes.size();
            classes.clear();
            event("Wallet classes inspected: " + inspected + " of " + names.size() + "; " + result.summary());
            if (!result.layouts.isEmpty()) installComposeCards(context, result.layouts);
            else warn("compose-missing", "Compose card structure not resolved (" + result.status + ")", null);
            saveScan(context, version, result, legacy, legacyNames);
            long elapsed = (System.nanoTime() - started) / 1_000_000L;
            event("Artwork hook scan finished in " + elapsed + " ms; reopen Wallet if cards were already shown");
        } catch (Throwable error) {
            warn("scan", "Wallet scan failed", error);
        }
    }

    private void saveScan(Context context, long version, ComposeLayoutFinder.Result result, int legacy,
            List<String> legacyNames) {
        if (version == -1) return;
        boolean retryable = result.status == ComposeLayoutFinder.Status.TIMED_OUT ||
                result.status == ComposeLayoutFinder.Status.TOO_MANY;
        if (result.layout == null && retryable) {
            warn("scan-retry", "Wallet scan incomplete (" + result.status + "); it will retry on the next launch", null);
            return;
        }
        SharedPreferences.Editor editor = context.getSharedPreferences(HOOK_CACHE, Context.MODE_PRIVATE).edit().clear();
        if (result.layout != null || legacy > 0) {
            editor.putLong("version", version)
                    .putString("compose", result.layouts.isEmpty() ? "NONE" : result.encode())
                    .putString("legacy", String.join(";", legacyNames))
                    .putLong("module", moduleStamp());
            event("Hook map saved for Wallet " + version);
        } else {
            editor.putLong("unsupported_version", version).putLong("unsupported_module", moduleStamp());
            warn("scan-empty", "No artwork hooks resolved for Wallet " + version, null);
        }
        editor.apply();
    }

    private List<String> walletClassNames(Context context) {
        List<String> names = new ArrayList<>();
        DexFile dex = null;
        try {
            dex = new DexFile(context.getApplicationInfo().sourceDir);
            Enumeration<String> entries = dex.entries();
            while (entries.hasMoreElements()) {
                String name = entries.nextElement();
                if (name.indexOf('.') < 0) names.add(name);
            }
        } catch (Throwable error) {
            warn("enumerate", "Cannot enumerate Wallet classes", error);
        } finally {
            if (dex != null) {
                try { dex.close(); } catch (Throwable ignored) { }
            }
        }
        return names;
    }

    private boolean isLegacyRenderer(Method method) {
        Class<?>[] p = method.getParameterTypes();
        return p.length == 3 && p[1] == ImageView.class && Drawable.class.isAssignableFrom(p[0]) &&
                method.getReturnType() == Void.TYPE && hasUriField(p[2]);
    }

    private void installDiskCacheHook(Context context) {
        try {
            Method decode = BitmapFactory.class.getDeclaredMethod("decodeFile", String.class, BitmapFactory.Options.class);
            hook(decode).setExceptionMode(XposedInterface.ExceptionMode.PROTECTIVE)
                    .setId("zetacard-disk-cache")
                    .intercept(chain -> {
                        Object result = chain.proceed();
                        try {
                            Object path = chain.getArgs().get(0);
                            if (!(result instanceof Bitmap) || !(path instanceof String) ||
                                    !((String) path).contains(DISK_CACHE_MARKER)) return result;
                            String name = new File((String) path).getName();
                            int dot = name.indexOf('.');
                            String id = dot > 0 ? name.substring(0, dot) : name;
                            if (!CardIds.isValid(id)) return result;
                            Bitmap custom = loadArt(id);
                            if (custom == null) return result;
                            Bitmap stock = (Bitmap) result;
                            Bitmap art = Bitmap.createScaledBitmap(custom, stock.getWidth(), stock.getHeight(), true);
                            BitmapFactory.Options options = (BitmapFactory.Options) chain.getArgs().get(1);
                            boolean mutable = options != null && options.inMutable;
                            if (art == custom || (mutable && !art.isMutable())) {
                                art = art.copy(art.getConfig() == null ? Bitmap.Config.ARGB_8888 : art.getConfig(), mutable);
                            }
                            if (loggedOnce.add("disk:" + id)) event("Cached artwork replaced: " + CardIds.shortId(id));
                            return art;
                        } catch (Throwable error) {
                            warn("disk-art", "Cached artwork skipped", error);
                        }
                        return result;
                    });
        } catch (Throwable error) {
            warn("disk-hook", "Cached artwork hook unavailable", error);
        }
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
                    catch (Throwable error) { warn("legacy-art", "Legacy artwork skipped", error); }
                    return result;
                });
        installDrawHook(context, method.getParameterTypes()[0]);
    }

    private List<Method> restoreLegacyMethods(ClassLoader loader, String encoded) throws Exception {
        List<Method> methods = new ArrayList<>();
        if (encoded == null || encoded.isEmpty()) return methods;
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

    private synchronized void installComposeCards(Context context, List<ComposeLayoutFinder.Layout> layouts) {
        if (composeInstalled || layouts.isEmpty()) return;
        Map<java.lang.reflect.Constructor<?>, List<ComposeLayoutFinder.Layout>> byConstructor = new LinkedHashMap<>();
        Map<Method, List<ComposeLayoutFinder.Layout>> byRenderer = new LinkedHashMap<>();
        for (ComposeLayoutFinder.Layout layout : layouts) {
            byConstructor.computeIfAbsent(layout.cardConstructor, key -> new ArrayList<>()).add(layout);
            byRenderer.computeIfAbsent(layout.renderer, key -> new ArrayList<>()).add(layout);
        }
        int installedHooks = 0;
        for (Map.Entry<java.lang.reflect.Constructor<?>, List<ComposeLayoutFinder.Layout>> entry : byConstructor.entrySet()) {
            if (installDiscoveryHook(context, entry.getKey(), entry.getValue())) installedHooks++;
        }
        for (Map.Entry<Method, List<ComposeLayoutFinder.Layout>> entry : byRenderer.entrySet()) {
            if (installRendererHook(entry.getKey(), entry.getValue())) installedHooks++;
        }
        composeInstalled = installedHooks > 0;
        event("Compose card hooks installed: " + byConstructor.size() + " card state(s), " + byRenderer.size() +
                " renderer(s) from " + layouts.size() + " equal layout(s)");
    }

    private boolean installDiscoveryHook(Context context, java.lang.reflect.Constructor<?> constructor,
            List<ComposeLayoutFinder.Layout> layouts) {
        try {
            hook(constructor).setExceptionMode(XposedInterface.ExceptionMode.PROTECTIVE)
                    .setId("zetacard-compose-discover-" + constructor.getDeclaringClass().getName())
                    .intercept(chain -> {
                        try {
                            Object state = chain.getArgs().get(0);
                            for (ComposeLayoutFinder.Layout layout : layouts) {
                                if (!layout.cardArt.getDeclaringClass().isInstance(state)) continue;
                                Object art = layout.cardArt.get(state);
                                if (loggedOnce.add("compose-state")) {
                                    event("Card state artwork model: " + (art == null ? "none" : art.getClass().getName()));
                                }
                                if (layout.urlModel.isInstance(art)) {
                                    discoverComposeCard(context, (String) layout.url.get(art));
                                    break;
                                }
                            }
                        } catch (Throwable error) {
                            warn("compose-discover", "Compose card discovery skipped", error);
                        }
                        return chain.proceed();
                    });
            return true;
        } catch (Throwable error) {
            warn("compose-install-discover", "Compose card discovery hook unavailable", error);
            return false;
        }
    }

    private boolean installRendererHook(Method renderer, List<ComposeLayoutFinder.Layout> layouts) {
        try {
            hook(renderer).setExceptionMode(XposedInterface.ExceptionMode.PROTECTIVE)
                    .setId("zetacard-compose-art-" + renderer.toGenericString().hashCode())
                    .intercept(chain -> {
                        try {
                            Object art = chain.getArgs().get(0);
                            if (loggedOnce.add("compose-render")) {
                                event("Artwork renderer model: " + (art == null ? "none" : art.getClass().getName()));
                            }
                            for (ComposeLayoutFinder.Layout layout : layouts) {
                                if (!layout.urlModel.isInstance(art)) continue;
                                String source = (String) layout.url.get(art);
                                if (source == null || !composeCardUrls.contains(source)) break;
                                String id = CardIds.fromSource(source);
                                Bitmap bitmap = loadArt(id);
                                if (bitmap == null) break;
                                if (loggedOnce.add("custom:" + id)) event("Custom artwork active: " + CardIds.shortId(id));
                                Object[] args = chain.getArgs().toArray();
                                args[0] = layout.bitmapConstructor.newInstance(bitmap);
                                return chain.proceed(args);
                            }
                        } catch (Throwable error) {
                            warn("compose-art", "Compose artwork skipped", error);
                        }
                        return chain.proceed();
                    });
            return true;
        } catch (Throwable error) {
            warn("compose-install-art", "Compose artwork hook unavailable", error);
            return false;
        }
    }

    private void heartbeat(Context context) {
        long now = System.currentTimeMillis();
        if (now - lastHeartbeat < HEARTBEAT_INTERVAL_MS) return;
        lastHeartbeat = now;
        storage.execute(() -> {
            try (FileOutputStream stream = new FileOutputStream(new File(context.getFilesDir(), HEARTBEAT_FILE))) {
                stream.write(Long.toString(now).getBytes(StandardCharsets.UTF_8));
            } catch (Throwable error) {
                warn("heartbeat", "Card heartbeat skipped", error);
            }
        });
    }

    private void discoverComposeCard(Context context, String source) {
        heartbeat(context);
        if (source == null || !"https".equals(Uri.parse(source).getScheme())) return;
        composeCardUrls.add(source);
        String id = CardIds.fromSource(source);
        if (loggedOnce.add("card:" + id)) Log.i(TAG, "Compose card discovered: " + CardIds.shortId(id));
        recordCard(context, id, CardLabels.DEFAULT, source);
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
                        BitmapDrawable art = replacements.get(card);
                        String id = cardIds.get(card);
                        Drawable current = (Drawable) artField.get(card);
                        if (id != null && current != null && current != art && flagField.getBoolean(card) &&
                                capturedStock.add(id)) {
                            captureStock(context, id, current);
                        }
                        if (art != null) {
                            artField.set(card, art);
                            flagField.setBoolean(card, true);
                        }
                        return chain.proceed();
                    });
        } catch (Throwable error) {
            warn("draw-hook", "Card background hook unavailable", error);
        }
    }

    private void captureStock(Context context, String id, Drawable current) {
        try {
            Bitmap stock = Bitmap.createBitmap(STOCK_WIDTH, STOCK_HEIGHT, Bitmap.Config.ARGB_8888);
            Canvas canvas = new Canvas(stock);
            Rect old = new Rect(current.getBounds());
            current.setBounds(0, 0, STOCK_WIDTH, STOCK_HEIGHT);
            current.draw(canvas);
            current.setBounds(old);
            storage.execute(() -> {
                try {
                    ByteArrayOutputStream bytes = new ByteArrayOutputStream();
                    stock.compress(Bitmap.CompressFormat.PNG, 100, bytes);
                    writeStock(context, id, bytes.toByteArray());
                } catch (Throwable error) {
                    capturedStock.remove(id);
                    warn("stock", "Stock preview skipped", error);
                } finally {
                    stock.recycle();
                }
            });
        } catch (Throwable error) {
            capturedStock.remove(id);
            warn("stock", "Stock preview skipped", error);
        }
    }

    private boolean hasUriField(Class<?> type) {
        for (Field field : type.getDeclaredFields()) if (field.getType() == Uri.class) return true;
        return false;
    }

    private Bitmap loadArt(String id) {
        String key = null;
        try {
            SharedPreferences preferences = getRemotePreferences("art");
            long revision = preferences.getLong(id, -1);
            if (revision < 0) return null;
            String caption = preferences.getString(ArtSync.CAPTION_PREFIX + id, null);
            key = id + ":remote:" + revision + ":" + (caption == null ? "" : caption.hashCode());
            Bitmap cached = bitmaps.get(key);
            if (cached != null) return cached;
            Long failedAt = failedArt.get(key);
            if (failedAt != null && System.currentTimeMillis() - failedAt < ART_RETRY_MS) return null;
            try (ParcelFileDescriptor file = openRemoteFile("art_" + id)) {
                Bitmap image = BitmapFactory.decodeFileDescriptor(file.getFileDescriptor());
                if (image != null && caption != null) image = CaptionRenderer.render(image, caption);
                if (image != null) {
                    bitmaps.put(key, image);
                    failedArt.remove(key);
                } else {
                    failedArt.put(key, System.currentTimeMillis());
                }
                return image;
            }
        } catch (Throwable error) {
            if (key != null) failedArt.put(key, System.currentTimeMillis());
            warn("remote-art", "LSPosed artwork unavailable", error);
        }
        return null;
    }

    private void recordCard(Context context, String id, String label, String stockUrl) {
        CardRegistry.Entry entry = new CardRegistry.Entry(id, CardLabels.stripControl(label), CardLabels.stripControl(stockUrl));
        synchronized (discovered) {
            CardRegistry.Entry previous = discovered.get(id);
            if (previous != null && previous.label.equals(entry.label) && previous.stockUrl.equals(entry.stockUrl)) return;
            discovered.put(id, entry);
            boolean scheduled = pendingRegistry != null;
            pendingRegistry = CardRegistry.format(discovered.values());
            if (scheduled) return;
        }
        storage.execute(() -> {
            String content;
            synchronized (discovered) {
                content = pendingRegistry;
                pendingRegistry = null;
            }
            if (content != null) writeRegistry(context, content);
        });
    }

    private void writeRegistry(Context context, String content) {
        try {
            File target = new File(context.getFilesDir(), REGISTRY_FILE);
            File temp = new File(context.getFilesDir(), "zetacard_cards.tmp");
            byte[] bytes = content.getBytes(StandardCharsets.UTF_8);
            try (FileOutputStream stream = new FileOutputStream(temp)) {
                stream.write(bytes);
                stream.getFD().sync();
            }
            if (!temp.renameTo(target)) {
                try (FileOutputStream stream = new FileOutputStream(target)) { stream.write(bytes); }
                temp.delete();
            }
        } catch (Throwable error) {
            warn("registry", "Card registry write failed", error);
        }
    }

    private void writeStock(Context context, String id, byte[] png) {
        try {
            File target = new File(context.getFilesDir(), "zetacard_stock_" + id + ".png");
            if (target.isFile()) return;
            try (FileOutputStream stream = new FileOutputStream(target)) { stream.write(png); }
        } catch (Throwable error) {
            warn("stock-write", "Stock preview write skipped", error);
        }
    }

    private void paint(Context context, Object[] args) throws Exception {
        heartbeat(context);
        if (args.length != 3 || !(args[0] instanceof Drawable) || !(args[1] instanceof ImageView) || args[2] == null) return;
        Uri original = null;
        String label = CardLabels.DEFAULT;
        for (Field field : args[2].getClass().getDeclaredFields()) {
            if (Modifier.isStatic(field.getModifiers())) continue;
            field.setAccessible(true);
            Object value = field.get(args[2]);
            if (value instanceof Uri && "https".equals(((Uri) value).getScheme())) original = (Uri) value;
            if (value instanceof String && CardLabels.isDisplayCandidate((String) value)) label = ((String) value).trim();
        }
        if (original == null) return;
        String id = CardIds.fromSource(original.toString());
        if (loggedOnce.add("legacy:" + id)) Log.i(TAG, "Legacy card discovered: " + CardIds.shortId(id));
        Drawable card = (Drawable) args[0];
        cardIds.put(card, id);
        recordCard(context, id, label, null);
        Bitmap bitmap = loadArt(id);
        if (bitmap == null) {
            replacements.remove(card);
            return;
        }
        replacements.put(card, new BitmapDrawable(context.getResources(), bitmap));
        card.invalidateSelf();
    }

    @SuppressLint("PrivateApi")
    private Context currentApplication() {
        try {
            Class<?> thread = Class.forName("android.app.ActivityThread");
            return (Context) thread.getMethod("currentApplication").invoke(null);
        } catch (Throwable ignored) {
            return null;
        }
    }

    private long moduleStamp() {
        try { return new File(getModuleApplicationInfo().sourceDir).lastModified(); }
        catch (Throwable ignored) { return -1; }
    }

    private long walletVersion(Context context) {
        try { return context.getPackageManager().getPackageInfo(WALLET, 0).getLongVersionCode(); }
        catch (Exception ignored) { return -1; }
    }

    private void event(String message) {
        String clean = LogSanitizer.clean(message);
        Log.i(TAG, clean);
        try { log(Log.INFO, TAG, clean); } catch (Throwable ignored) { }
        WalletEventLog file = eventLog;
        if (file != null) file.write("INFO", clean);
    }

    private void warn(String key, String message, Throwable error) {
        if (!loggedOnce.add("warn:" + key)) return;
        String detail = LogSanitizer.clean(message + (error == null ? "" : ": " + LogSanitizer.describe(error)));
        Log.w(TAG, detail);
        try { log(Log.WARN, TAG, detail); } catch (Throwable ignored) { }
        WalletEventLog file = eventLog;
        if (file != null) file.write("WARN", detail);
    }
}
