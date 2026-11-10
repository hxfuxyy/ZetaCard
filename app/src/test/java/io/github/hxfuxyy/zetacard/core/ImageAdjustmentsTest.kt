package io.github.hxfuxyy.zetacard.core

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ImageAdjustmentsTest {
    @Test
    fun neutralLeavesColorsUnchanged() {
        val neutral = ImageAdjustments.Neutral
        assertTrue(neutral.isNeutral)
        assertEquals(Triple(12, 128, 250), neutral.apply(12, 128, 250))
        assertArrayEquals(
            floatArrayOf(1f, 0f, 0f, 0f, 0f, 0f, 1f, 0f, 0f, 0f, 0f, 0f, 1f, 0f, 0f, 0f, 0f, 0f, 1f, 0f),
            neutral.colorMatrix(),
            0f,
        )
    }

    @Test
    fun brightnessShiftsAndClamps() {
        val brighter = ImageAdjustments(brightness = 0.2f)
        assertFalse(brighter.isNeutral)
        assertEquals(Triple(51, 179, 255), brighter.apply(0, 128, 250))
        assertEquals(Triple(0, 0, 0), ImageAdjustments(brightness = -0.3f).apply(10, 20, 30))
    }

    @Test
    fun shadeDarkensProportionally() {
        assertEquals(Triple(100, 50, 0), ImageAdjustments(shade = 0.5f).apply(200, 100, 0))
    }

    @Test
    fun valuesOutsideTheRangeAreClamped() {
        val extreme = ImageAdjustments(brightness = 5f, shade = 5f).clamped()
        assertEquals(ImageAdjustments.MAX_BRIGHTNESS, extreme.brightness, 0f)
        assertEquals(ImageAdjustments.MAX_SHADE, extreme.shade, 0f)
        val matrix = ImageAdjustments(shade = 5f).colorMatrix()
        assertEquals(1f - ImageAdjustments.MAX_SHADE, matrix[0], 1e-6f)
        assertEquals(1f, matrix[18], 0f)
    }
}
