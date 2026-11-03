package io.github.hxfuxyy.zetacard.wallet;

final class FakeWallet {
    private FakeWallet() { }

    static final class Pixels {
        int width;
    }

    abstract static class ImageModel {
    }

    static final class BitmapModel extends ImageModel {
        final Pixels bitmap;

        BitmapModel(Pixels bitmap) {
            this.bitmap = bitmap;
        }
    }

    static final class UrlModel extends ImageModel {
        final String url;
        final String description;
        final int width;

        UrlModel(String url, String description, int width) {
            this.url = url;
            this.description = description;
            this.width = width;
        }
    }

    static final class CardState {
        final ImageModel art;
        final boolean selected;
        final boolean expired;
        final boolean locked;
        final String title;
        final String subtitle;
        final int color;
        final long stamp;
        final Object extra;

        CardState(ImageModel art, boolean selected, boolean expired, boolean locked, String title, String subtitle) {
            this.art = art;
            this.selected = selected;
            this.expired = expired;
            this.locked = locked;
            this.title = title;
            this.subtitle = subtitle;
            this.color = 0;
            this.stamp = 0;
            this.extra = null;
        }
    }

    static final class KeyedState {
        final CardState state;
        final Object key;

        KeyedState(CardState state, Object key) {
            this.state = state;
            this.key = key;
        }
    }

    static final class Renderer {
        static void render(ImageModel model, String description, Object modifier, Object alignment, Object scale,
                float alpha, Object filter, Object composer, Object quality, int changed, int defaults) {
        }
    }

    static final class OtherRenderer {
        static void render(ImageModel model, String description, Object modifier, Object alignment, Object scale,
                float alpha, Object filter, Object composer, Object quality, int changed, int defaults) {
        }
    }

    static final class KeyedStateCopy {
        final CardState state;
        final Object key;

        KeyedStateCopy(CardState state, Object key) {
            this.state = state;
            this.key = key;
        }
    }

    static final class OverloadedRenderer {
        static void render(ImageModel model, String description, Object modifier, float alpha, Object alignment,
                Object scale, Object filter, Object composer, Object quality, int changed, int defaults) {
        }

        static void render(ImageModel model, String description, Object modifier, Object alignment, Object scale,
                float alpha, Object filter, Object composer, Object quality, int changed, int defaults) {
        }
    }

    static final class WeakRenderer {
        static void draw(ImageModel model, String description, Object modifier, float alpha, int changed, int defaults) {
        }
    }

    static final class Unrelated {
        final String name;
        final int count;

        Unrelated(String name, int count) {
            this.name = name;
            this.count = count;
        }

        static void notARenderer(int value, String text, float alpha, int a, int b, int c) {
        }
    }
}
