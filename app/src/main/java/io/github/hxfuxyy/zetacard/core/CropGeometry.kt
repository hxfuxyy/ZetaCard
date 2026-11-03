package io.github.hxfuxyy.zetacard.core

import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

data class PixelSize(val width: Int, val height: Int) {
    val isEmpty: Boolean get() = width <= 0 || height <= 0

    companion object {
        val Zero = PixelSize(0, 0)
    }
}

data class Pan(val x: Float, val y: Float) {
    operator fun plus(other: Pan) = Pan(x + other.x, y + other.y)

    companion object {
        val Zero = Pan(0f, 0f)
    }
}

data class CropTransform(val translateX: Float, val translateY: Float, val scaleX: Float, val scaleY: Float)

object CropGeometry {
    const val CARD_RATIO = 700f / 440f
    private const val FRAME_WIDTH_FRACTION = 0.90f
    private const val FRAME_HEIGHT_FRACTION = 0.75f
    private const val ZOOM_TOLERANCE = 0.001f

    fun frame(viewport: PixelSize): PixelSize {
        if (viewport.isEmpty) return PixelSize.Zero
        val width = min(viewport.width * FRAME_WIDTH_FRACTION, viewport.height * FRAME_HEIGHT_FRACTION * CARD_RATIO)
        return PixelSize(width.roundToInt(), (width / CARD_RATIO).roundToInt())
    }

    fun fitScale(image: PixelSize, viewport: PixelSize): Float =
        min(viewport.width.toFloat() / image.width, viewport.height.toFloat() / image.height)

    fun coverZoom(image: PixelSize, viewport: PixelSize): Float {
        if (viewport.isEmpty || image.isEmpty) return 1f
        val frame = frame(viewport)
        val fit = fitScale(image, viewport)
        return max(1f, max(frame.width / (image.width * fit), frame.height / (image.height * fit)))
    }

    fun maxZoom(image: PixelSize, viewport: PixelSize): Float = max(8f, coverZoom(image, viewport) * 2f)

    fun constrainPan(value: Pan, image: PixelSize, viewport: PixelSize, zoom: Float): Pan {
        if (viewport.isEmpty || image.isEmpty) return Pan.Zero
        val frame = frame(viewport)
        val scale = fitScale(image, viewport) * zoom
        val maxX = max(0f, (image.width * scale - frame.width) / 2f)
        val maxY = max(0f, (image.height * scale - frame.height) / 2f)
        return Pan(value.x.coerceIn(-maxX, maxX), value.y.coerceIn(-maxY, maxY))
    }

    fun canApply(image: PixelSize, viewport: PixelSize, zoom: Float): Boolean =
        !viewport.isEmpty && !image.isEmpty && zoom + ZOOM_TOLERANCE >= coverZoom(image, viewport)

    fun cropTransform(image: PixelSize, viewport: PixelSize, zoom: Float, pan: Pan, output: PixelSize): CropTransform {
        require(canApply(image, viewport, zoom)) { "The photo does not cover the card frame" }
        require(!output.isEmpty) { "Output size must be positive" }
        val frame = frame(viewport)
        val scale = fitScale(image, viewport) * zoom
        val sourceWidth = frame.width / scale
        val sourceHeight = frame.height / scale
        val centerX = image.width / 2f - pan.x / scale
        val centerY = image.height / 2f - pan.y / scale
        val scaleX = output.width / sourceWidth
        val scaleY = output.height / sourceHeight
        return CropTransform(
            translateX = output.width / 2f - centerX * scaleX,
            translateY = output.height / 2f - centerY * scaleY,
            scaleX = scaleX,
            scaleY = scaleY,
        )
    }
}
