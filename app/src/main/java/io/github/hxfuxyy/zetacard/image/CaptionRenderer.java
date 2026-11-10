package io.github.hxfuxyy.zetacard.image;

import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.Shader;
import android.graphics.Typeface;
import android.text.TextPaint;
import android.text.TextUtils;

public final class CaptionRenderer {
    private static final float TEXT_SIZE = 0.085f;
    private static final float MARGIN = 0.065f;
    private static final float BASELINE = 0.165f;
    private static final float MAX_WIDTH = 0.55f;
    private static final float SCRIM_END = 0.42f;

    private CaptionRenderer() { }

    public static Bitmap render(Bitmap source, String caption) {
        if (source == null || caption == null || caption.isEmpty()) return source;
        Bitmap out = source.copy(Bitmap.Config.ARGB_8888, true);
        if (out == null) return source;
        float width = out.getWidth();
        float height = out.getHeight();
        Canvas canvas = new Canvas(out);
        Paint scrim = new Paint(Paint.ANTI_ALIAS_FLAG);
        scrim.setShader(new LinearGradient(0, 0, 0, height * SCRIM_END,
                Color.argb(110, 0, 0, 0), Color.TRANSPARENT, Shader.TileMode.CLAMP));
        canvas.drawRect(0, 0, width, height * SCRIM_END, scrim);
        TextPaint paint = new TextPaint(Paint.ANTI_ALIAS_FLAG);
        paint.setColor(Color.WHITE);
        paint.setTypeface(Typeface.create(Typeface.DEFAULT, Typeface.BOLD));
        paint.setTextSize(height * TEXT_SIZE);
        paint.setTextAlign(Paint.Align.RIGHT);
        paint.setShadowLayer(height * 0.02f, 0, height * 0.006f, Color.argb(150, 0, 0, 0));
        CharSequence text = TextUtils.ellipsize(caption, paint, width * MAX_WIDTH, TextUtils.TruncateAt.END);
        canvas.drawText(text, 0, text.length(), width * (1f - MARGIN), height * BASELINE, paint);
        return out;
    }
}
