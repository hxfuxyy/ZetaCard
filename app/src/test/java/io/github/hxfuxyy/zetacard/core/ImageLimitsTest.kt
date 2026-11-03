package io.github.hxfuxyy.zetacard.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ImageLimitsTest {
    @Test
    fun sampleSizeIsSmallestPowerOfTwoThatFits() {
        assertEquals(1, ImageLimits.sampleSize(3000, 2000, 3000))
        assertEquals(2, ImageLimits.sampleSize(3001, 2000, 3000))
        assertEquals(2, ImageLimits.sampleSize(5999, 4000, 3000))
        assertEquals(4, ImageLimits.sampleSize(4000, 12001, 3000))
        assertEquals(8, ImageLimits.sampleSize(24000, 100, 3000))
    }

    @Test
    fun sampledImagesNeverExceedTheLimit() {
        for (side in listOf(1, 2999, 3000, 3001, 6000, 6001, 12345, 48000)) {
            val sample = ImageLimits.sampleSize(side, side / 2 + 1, 3000)
            assertTrue("side $side sample $sample", side / sample <= 3000)
            assertEquals(0, sample and (sample - 1))
        }
    }

    @Test
    fun invalidInputsUseNoSampling() {
        assertEquals(1, ImageLimits.sampleSize(0, 100, 3000))
        assertEquals(1, ImageLimits.sampleSize(100, -1, 3000))
        assertEquals(1, ImageLimits.sampleSize(100, 100, 0))
    }

    @Test
    fun rejectsEmptyOrHugeImages() {
        assertTrue(ImageLimits.isDecodable(4000, 3000))
        assertFalse(ImageLimits.isDecodable(0, 3000))
        assertFalse(ImageLimits.isDecodable(-1, -1))
        assertFalse(ImageLimits.isDecodable(20_000, 20_000))
    }
}
