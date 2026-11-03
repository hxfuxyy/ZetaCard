package io.github.hxfuxyy.zetacard.core

import kotlin.math.abs
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CropGeometryTest {
    private val viewport = PixelSize(1000, 2000)
    private val output = PixelSize(1400, 880)

    @Test
    fun frameKeepsCardRatioAndFitsViewport() {
        val frame = CropGeometry.frame(viewport)
        assertEquals(900, frame.width)
        assertEquals(566, frame.height)
        val wide = CropGeometry.frame(PixelSize(3000, 600))
        assertTrue(wide.width <= 3000 * 0.9f)
        assertTrue(wide.height <= 600 * 0.75f + 1)
        assertEquals(PixelSize.Zero, CropGeometry.frame(PixelSize.Zero))
    }

    @Test
    fun coverZoomMakesThePhotoFillTheFrame() {
        val image = PixelSize(1000, 1000)
        val zoom = CropGeometry.coverZoom(image, viewport)
        val scale = CropGeometry.fitScale(image, viewport) * zoom
        val frame = CropGeometry.frame(viewport)
        assertTrue(image.width * scale + 0.01f >= frame.width)
        assertTrue(image.height * scale + 0.01f >= frame.height)
        assertTrue(CropGeometry.canApply(image, viewport, zoom))
        assertFalse(CropGeometry.canApply(image, viewport, zoom - 0.1f))
    }

    @Test
    fun coverZoomIsAtLeastOne() {
        assertEquals(1f, CropGeometry.coverZoom(PixelSize(100, 100), PixelSize.Zero))
        assertTrue(CropGeometry.coverZoom(PixelSize(4000, 100), viewport) >= 1f)
    }

    @Test
    fun panIsClampedSoTheFrameStaysCovered() {
        val image = PixelSize(2000, 1000)
        val zoom = CropGeometry.coverZoom(image, viewport) * 2f
        val clamped = CropGeometry.constrainPan(Pan(1e6f, -1e6f), image, viewport, zoom)
        val scale = CropGeometry.fitScale(image, viewport) * zoom
        val frame = CropGeometry.frame(viewport)
        assertEquals((image.width * scale - frame.width) / 2f, clamped.x, 0.01f)
        assertEquals(-(image.height * scale - frame.height) / 2f, clamped.y, 0.01f)
        assertEquals(Pan.Zero, CropGeometry.constrainPan(Pan(5f, 5f), image, PixelSize.Zero, zoom))
    }

    @Test
    fun cropTransformMapsTheFrameOntoTheOutput() {
        val image = PixelSize(3000, 2000)
        val zoom = CropGeometry.coverZoom(image, viewport) * 1.5f
        val pan = CropGeometry.constrainPan(Pan(120f, -80f), image, viewport, zoom)
        val transform = CropGeometry.cropTransform(image, viewport, zoom, pan, output)
        val frame = CropGeometry.frame(viewport)
        val scale = CropGeometry.fitScale(image, viewport) * zoom
        val centerX = image.width / 2f - pan.x / scale
        val centerY = image.height / 2f - pan.y / scale
        val left = centerX - frame.width / scale / 2f
        val top = centerY - frame.height / scale / 2f
        val right = centerX + frame.width / scale / 2f
        val bottom = centerY + frame.height / scale / 2f
        assertNear(0f, transform.translateX + transform.scaleX * left)
        assertNear(0f, transform.translateY + transform.scaleY * top)
        assertNear(output.width.toFloat(), transform.translateX + transform.scaleX * right)
        assertNear(output.height.toFloat(), transform.translateY + transform.scaleY * bottom)
    }

    @Test(expected = IllegalArgumentException::class)
    fun cropTransformRejectsUncoveredFrames() {
        CropGeometry.cropTransform(PixelSize(1000, 1000), viewport, 0.5f, Pan.Zero, output)
    }

    private fun assertNear(expected: Float, actual: Float) {
        assertTrue("expected $expected but was $actual", abs(expected - actual) < 0.5f)
    }
}
