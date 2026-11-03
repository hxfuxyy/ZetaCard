package io.github.hxfuxyy.zetacard.core;

public final class ImageLimits {
    public static final int OUTPUT_WIDTH = 1400;
    public static final int OUTPUT_HEIGHT = 880;
    public static final int MAX_EDIT_SIDE = 3000;
    public static final long MAX_SOURCE_PIXELS = 100_000_000L;
    public static final int MAX_STOCK_SIDE = 4096;

    private ImageLimits() { }

    public static boolean isDecodable(int width, int height) {
        return width > 0 && height > 0 && (long) width * height <= MAX_SOURCE_PIXELS;
    }

    public static int sampleSize(int width, int height, int maxSide) {
        if (width <= 0 || height <= 0 || maxSide <= 0) return 1;
        int largest = Math.max(width, height);
        int sample = 1;
        while (largest / sample > maxSide && sample < (1 << 12)) sample <<= 1;
        return sample;
    }
}
