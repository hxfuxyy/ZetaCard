package io.github.hxfuxyy.zetacard.wallet;

import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public final class ComposeLayoutFinder {
    public static final int MAX_MATCHES = 100_000;
    public static final int MAX_EQUAL_LAYOUTS = 32;
    static final String CACHE_VERSION = "v2";
    private static final String LIST_SEPARATOR = ";";

    public static final class Layout {
        public final Class<?> urlModel;
        public final Field cardArt;
        public final Field url;
        public final Constructor<?> bitmapConstructor;
        public final Constructor<?> cardConstructor;
        public final Method renderer;

        public Layout(Class<?> urlModel, Field cardArt, Field url,
                Constructor<?> bitmapConstructor, Constructor<?> cardConstructor, Method renderer) {
            this.urlModel = urlModel;
            this.cardArt = cardArt;
            this.url = url;
            this.bitmapConstructor = bitmapConstructor;
            this.cardConstructor = cardConstructor;
            this.renderer = renderer;
        }

        public Class<?> imageModel() {
            return bitmapConstructor.getDeclaringClass().getSuperclass();
        }

        public void makeAccessible() {
            cardArt.setAccessible(true);
            url.setAccessible(true);
            bitmapConstructor.setAccessible(true);
            cardConstructor.setAccessible(true);
            renderer.setAccessible(true);
        }

        public String encode() {
            StringBuilder parameters = new StringBuilder();
            for (Class<?> type : renderer.getParameterTypes()) {
                if (parameters.length() > 0) parameters.append(',');
                parameters.append(type.getName());
            }
            return String.join("|", CACHE_VERSION, cardConstructor.getParameterTypes()[0].getName(),
                    cardConstructor.getDeclaringClass().getName(), urlModel.getName(),
                    bitmapConstructor.getDeclaringClass().getName(), cardArt.getName(),
                    url.getName(), renderer.getDeclaringClass().getName(), renderer.getName(), parameters.toString());
        }

        boolean sameAs(Layout other) {
            return cardArt.equals(other.cardArt) && url.equals(other.url) && renderer.equals(other.renderer) &&
                    bitmapConstructor.equals(other.bitmapConstructor) && cardConstructor.equals(other.cardConstructor);
        }
    }

    public enum Status { FOUND, NOT_FOUND, AMBIGUOUS, TOO_MANY, TIMED_OUT }

    public static final class Result {
        public final Status status;
        public final Layout layout;
        public final List<Layout> layouts;
        public final int bitmapModels;
        public final int urlModels;
        public final int cardStates;
        public final int renderers;
        public final int matches;
        public final int bestScore;

        Result(Status status, List<Layout> layouts, int bitmapModels, int urlModels, int cardStates,
                int renderers, int matches, int bestScore) {
            this.status = status;
            this.layouts = layouts == null ? Collections.emptyList() : Collections.unmodifiableList(layouts);
            this.layout = this.layouts.isEmpty() ? null : this.layouts.get(0);
            this.bitmapModels = bitmapModels;
            this.urlModels = urlModels;
            this.cardStates = cardStates;
            this.renderers = renderers;
            this.matches = matches;
            this.bestScore = bestScore;
        }

        public String summary() {
            return "Compose candidates: " + bitmapModels + " bitmap, " + urlModels + " URL, " + cardStates +
                    " state, " + renderers + " renderer; " + matches + " match(es), " + layouts.size() +
                    " equal best layout(s), " + status;
        }

        public String encode() {
            List<String> parts = new ArrayList<>();
            for (Layout item : layouts) parts.add(item.encode());
            return String.join(LIST_SEPARATOR, parts);
        }
    }

    private static final class Deadline {
        private final long end;

        Deadline(long budgetMillis) {
            end = budgetMillis <= 0 ? Long.MAX_VALUE : System.nanoTime() + budgetMillis * 1_000_000L;
        }

        boolean passed() {
            return end != Long.MAX_VALUE && System.nanoTime() > end;
        }
    }

    private static final class CardState {
        final Class<?> type;
        final Field art;

        CardState(Class<?> type, Field art) {
            this.type = type;
            this.art = art;
        }
    }

    private final Class<?> bitmapType;

    public ComposeLayoutFinder(Class<?> bitmapType) {
        this.bitmapType = bitmapType;
    }

    public Result find(List<Class<?>> classes, long budgetMillis) {
        Deadline deadline = new Deadline(budgetMillis);
        List<Class<?>> bitmapModels = new ArrayList<>();
        List<Class<?>> urlModels = new ArrayList<>();
        List<Class<?>> stateCandidates = new ArrayList<>();
        Map<Class<?>, List<Constructor<?>>> keyedConstructors = new HashMap<>();
        Map<Class<?>, List<Method>> renderersByImage = new LinkedHashMap<>();
        int rendererCount = 0;
        for (Class<?> type : classes) {
            if (deadline.passed()) return timedOut(bitmapModels, urlModels, stateCandidates, rendererCount);
            try {
                int instanceFields = 0;
                int bitmapFields = 0;
                int stringFields = 0;
                for (Field field : type.getDeclaredFields()) {
                    if (Modifier.isStatic(field.getModifiers())) continue;
                    instanceFields++;
                    if (field.getType() == bitmapType) bitmapFields++;
                    if (field.getType() == String.class) stringFields++;
                }
                Class<?> parent = type.getSuperclass();
                if (parent != null && parent != Object.class) {
                    if (instanceFields == 1 && bitmapFields == 1 && hasConstructor(type, bitmapType)) bitmapModels.add(type);
                    if (stringFields >= 1 && hasUrlConstructor(type)) urlModels.add(type);
                }
                if (instanceFields >= 5) stateCandidates.add(type);
                if (instanceFields >= 2 && instanceFields <= 4) indexKeyedConstructors(type, keyedConstructors);
                for (Method method : type.getDeclaredMethods()) {
                    if (!isRendererShape(method)) continue;
                    renderersByImage.computeIfAbsent(method.getParameterTypes()[0], key -> new ArrayList<>()).add(method);
                    rendererCount++;
                }
            } catch (Throwable ignored) {
            }
        }

        Scoring scoring = new Scoring(new HashMap<>());
        int examined = 0;
        for (Class<?> bitmapModel : bitmapModels) {
            Class<?> imageModel = bitmapModel.getSuperclass();
            List<Method> renderers = renderersByImage.getOrDefault(imageModel, Collections.emptyList());
            if (renderers.isEmpty()) continue;
            Constructor<?> bitmapConstructor;
            try { bitmapConstructor = bitmapModel.getDeclaredConstructor(bitmapType); }
            catch (NoSuchMethodException ignored) { continue; }
            List<CardState> states = cardStatesFor(imageModel, stateCandidates, deadline);
            if (states == null) return timedOut(bitmapModels, urlModels, stateCandidates, rendererCount);
            if (states.isEmpty()) continue;
            for (Class<?> urlModel : urlModels) {
                if (urlModel.getSuperclass() != imageModel) continue;
                Field url = firstInstanceField(urlModel, String.class);
                if (url == null) continue;
                for (CardState state : states) {
                    List<Constructor<?>> constructors = keyedConstructors.getOrDefault(state.type, Collections.emptyList());
                    Field cardArt = state.art;
                    for (Constructor<?> cardConstructor : constructors) {
                        for (Method renderer : renderers) {
                            if (deadline.passed()) return timedOut(bitmapModels, urlModels, stateCandidates, rendererCount);
                            if (++examined > MAX_MATCHES) {
                                return new Result(Status.TOO_MANY, null, bitmapModels.size(), urlModels.size(),
                                        stateCandidates.size(), rendererCount, examined, scoring.bestScore);
                            }
                            scoring.offer(new Layout(urlModel, cardArt, url, bitmapConstructor, cardConstructor, renderer));
                        }
                    }
                }
            }
        }
        Status status;
        if (examined == 0) status = Status.NOT_FOUND;
        else if (scoring.tie || scoring.best().isEmpty() || scoring.best().size() > MAX_EQUAL_LAYOUTS) status = Status.AMBIGUOUS;
        else status = Status.FOUND;
        List<Layout> layouts = status == Status.FOUND ? scoring.best() : null;
        if (layouts != null) for (Layout item : layouts) item.makeAccessible();
        return new Result(status, layouts, bitmapModels.size(), urlModels.size(), stateCandidates.size(),
                rendererCount, examined, scoring.bestScore);
    }

    public List<Layout> restoreAll(ClassLoader loader, String encoded) throws ReflectiveOperationException {
        if (encoded == null || encoded.isEmpty()) throw new IllegalArgumentException("Empty Compose hook cache");
        List<Layout> layouts = new ArrayList<>();
        for (String part : encoded.split(LIST_SEPARATOR)) layouts.add(restore(loader, part));
        if (layouts.size() > MAX_EQUAL_LAYOUTS) throw new IllegalArgumentException("Too many cached Compose layouts");
        return layouts;
    }

    public Layout restore(ClassLoader loader, String encoded) throws ReflectiveOperationException {
        String[] fields = encoded.split("\\|", -1);
        if (fields.length != 10 || !CACHE_VERSION.equals(fields[0])) throw new IllegalArgumentException("Invalid Compose hook cache");
        Class<?> cardState = Class.forName(fields[1], false, loader);
        Class<?> keyedState = Class.forName(fields[2], false, loader);
        Class<?> urlModel = Class.forName(fields[3], false, loader);
        Class<?> bitmapModel = Class.forName(fields[4], false, loader);
        Class<?> imageModel = bitmapModel.getSuperclass();
        Field cardArt = cardState.getDeclaredField(fields[5]);
        Field url = urlModel.getDeclaredField(fields[6]);
        if (cardArt.getType() != imageModel || url.getType() != String.class || urlModel.getSuperclass() != imageModel)
            throw new NoSuchFieldException("Saved Compose fields changed");
        Constructor<?> bitmapConstructor = bitmapModel.getDeclaredConstructor(bitmapType);
        Constructor<?> cardConstructor = keyedState.getDeclaredConstructor(cardState, Object.class);
        Class<?> rendererType = Class.forName(fields[7], false, loader);
        String[] names = fields[9].isEmpty() ? new String[0] : fields[9].split(",");
        Class<?>[] parameters = new Class<?>[names.length];
        for (int i = 0; i < names.length; i++) parameters[i] = typeFor(names[i], loader);
        Method renderer = rendererType.getDeclaredMethod(fields[8], parameters);
        if (parameters.length == 0 || parameters[0] != imageModel || !isRendererShape(renderer))
            throw new NoSuchMethodException("Saved Compose renderer changed");
        Layout layout = new Layout(urlModel, cardArt, url, bitmapConstructor, cardConstructor, renderer);
        layout.makeAccessible();
        return layout;
    }

    private static Class<?> typeFor(String name, ClassLoader loader) throws ClassNotFoundException {
        switch (name) {
            case "boolean": return Boolean.TYPE;
            case "byte": return Byte.TYPE;
            case "char": return Character.TYPE;
            case "short": return Short.TYPE;
            case "int": return Integer.TYPE;
            case "long": return Long.TYPE;
            case "float": return Float.TYPE;
            case "double": return Double.TYPE;
            default: return Class.forName(name, false, loader);
        }
    }

    public static boolean isRendererShape(Method method) {
        if (!Modifier.isStatic(method.getModifiers()) || method.getReturnType() != Void.TYPE) return false;
        Class<?>[] p = method.getParameterTypes();
        if (p.length < 6 || p.length > 14 || p[0].isPrimitive()) return false;
        int strings = 0;
        int floats = 0;
        int ints = 0;
        for (Class<?> type : p) {
            if (type == String.class) strings++;
            else if (type == Float.TYPE) floats++;
            else if (type == Integer.TYPE) ints++;
        }
        return strings >= 1 && floats >= 1 && ints >= 2;
    }

    static int score(Layout layout) {
        return score(layout, new HashMap<>());
    }

    private static int score(Layout layout, Map<Class<?>, int[]> shapes) {
        int score = 0;
        Class<?>[] p = layout.renderer.getParameterTypes();
        if (p.length == 11 && p[1] == String.class && p[5] == Float.TYPE && p[9] == Integer.TYPE && p[10] == Integer.TYPE) {
            score += 5;
        } else if (p.length >= 9 && p.length <= 13) {
            score += 1;
        }
        int[] url = shape(layout.urlModel, shapes);
        if (url[STRINGS] == 2) score += 2;
        if (url[FIELDS] >= 3 && url[FIELDS] <= 4) score += 1;
        int[] state = shape(layout.cardConstructor.getParameterTypes()[0], shapes);
        if (state[BOOLEANS] == 3) score += 2;
        if (state[FIELDS] >= 8 && state[FIELDS] <= 10) score += 1;
        if (shape(layout.cardConstructor.getDeclaringClass(), shapes)[FIELDS] == 2) score += 1;
        return score;
    }

    private static final int FIELDS = 0;
    private static final int STRINGS = 1;
    private static final int BOOLEANS = 2;

    private static int[] shape(Class<?> type, Map<Class<?>, int[]> shapes) {
        int[] known = shapes.get(type);
        if (known != null) return known;
        int[] counts = new int[3];
        for (Field field : type.getDeclaredFields()) {
            if (Modifier.isStatic(field.getModifiers())) continue;
            counts[FIELDS]++;
            if (field.getType() == String.class) counts[STRINGS]++;
            if (field.getType() == Boolean.TYPE) counts[BOOLEANS]++;
        }
        shapes.put(type, counts);
        return counts;
    }

    private static final class Scoring {
        private final Map<Class<?>, int[]> shapes;
        private final List<Layout> best = new ArrayList<>();
        int bestScore = Integer.MIN_VALUE;
        boolean tie;

        Scoring(Map<Class<?>, int[]> shapes) {
            this.shapes = shapes;
        }

        List<Layout> best() {
            return best;
        }

        void offer(Layout candidate) {
            int value = score(candidate, shapes);
            if (value > bestScore) {
                bestScore = value;
                best.clear();
                best.add(candidate);
                tie = false;
            } else if (value == bestScore && !best.isEmpty()) {
                if (candidate.renderer.getDeclaringClass() != best.get(0).renderer.getDeclaringClass()) tie = true;
                for (Layout known : best) if (known.sameAs(candidate)) return;
                if (best.size() <= MAX_EQUAL_LAYOUTS) best.add(candidate);
                else tie = true;
            }
        }
    }

    public static Result timedOutBeforeMatching() {
        return new Result(Status.TIMED_OUT, null, 0, 0, 0, 0, 0, Integer.MIN_VALUE);
    }

    private Result timedOut(List<Class<?>> bitmapModels, List<Class<?>> urlModels, List<Class<?>> states, int renderers) {
        return new Result(Status.TIMED_OUT, null, bitmapModels.size(), urlModels.size(), states.size(), renderers, 0,
                Integer.MIN_VALUE);
    }

    private static List<CardState> cardStatesFor(Class<?> imageModel, List<Class<?>> candidates, Deadline deadline) {
        List<CardState> result = new ArrayList<>();
        for (Class<?> type : candidates) {
            if (deadline.passed()) return null;
            try {
                Field art = firstInstanceField(type, imageModel);
                if (art == null || !hasCardConstructor(type, imageModel)) continue;
                result.add(new CardState(type, art));
            } catch (Throwable ignored) {
            }
        }
        return result;
    }

    private static void indexKeyedConstructors(Class<?> type, Map<Class<?>, List<Constructor<?>>> index) {
        for (Constructor<?> constructor : type.getDeclaredConstructors()) {
            Class<?>[] p = constructor.getParameterTypes();
            if (p.length == 2 && p[1] == Object.class && !p[0].isPrimitive()) {
                index.computeIfAbsent(p[0], key -> new ArrayList<>()).add(constructor);
            }
        }
    }

    private static Field firstInstanceField(Class<?> type, Class<?> fieldType) {
        for (Field field : type.getDeclaredFields()) {
            if (!Modifier.isStatic(field.getModifiers()) && field.getType() == fieldType) return field;
        }
        return null;
    }

    private static boolean hasConstructor(Class<?> type, Class<?> parameter) {
        try {
            type.getDeclaredConstructor(parameter);
            return true;
        } catch (NoSuchMethodException ignored) {
            return false;
        }
    }

    private static boolean hasUrlConstructor(Class<?> type) {
        for (Constructor<?> constructor : type.getDeclaredConstructors()) {
            Class<?>[] p = constructor.getParameterTypes();
            if (p.length >= 2 && p[0] == String.class) return true;
        }
        return false;
    }

    private static boolean hasCardConstructor(Class<?> type, Class<?> imageModel) {
        for (Constructor<?> constructor : type.getDeclaredConstructors()) {
            Class<?>[] p = constructor.getParameterTypes();
            if (p.length >= 5 && p.length <= 12 && p[0] == imageModel) return true;
        }
        return false;
    }
}
