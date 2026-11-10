package io.github.hxfuxyy.zetacard.core

data class ImageAdjustments(val brightness: Float = 0f, val shade: Float = 0f) {
    val isNeutral: Boolean get() = brightness == 0f && shade == 0f

    fun clamped(): ImageAdjustments = ImageAdjustments(
        brightness.coerceIn(MIN_BRIGHTNESS, MAX_BRIGHTNESS),
        shade.coerceIn(0f, MAX_SHADE),
    )

    fun colorMatrix(): FloatArray {
        val value = clamped()
        val scale = 1f - value.shade
        val offset = 255f * value.brightness * scale
        return floatArrayOf(
            scale, 0f, 0f, 0f, offset,
            0f, scale, 0f, 0f, offset,
            0f, 0f, scale, 0f, offset,
            0f, 0f, 0f, 1f, 0f,
        )
    }

    fun apply(red: Int, green: Int, blue: Int): Triple<Int, Int, Int> {
        val m = colorMatrix()
        fun channel(row: Int, c: Int) = (m[row * 5 + row] * c + m[row * 5 + 4]).toInt().coerceIn(0, 255)
        return Triple(channel(0, red), channel(1, green), channel(2, blue))
    }

    companion object {
        const val MIN_BRIGHTNESS = -0.3f
        const val MAX_BRIGHTNESS = 0.3f
        const val MAX_SHADE = 0.6f
        val Neutral = ImageAdjustments()
    }
}
