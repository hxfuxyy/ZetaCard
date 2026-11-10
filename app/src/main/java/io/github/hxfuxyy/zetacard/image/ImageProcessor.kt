package io.github.hxfuxyy.zetacard.image

import android.content.ContentResolver
import android.content.res.AssetManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.ColorMatrix
import android.graphics.ColorMatrixColorFilter
import android.graphics.ImageDecoder
import android.graphics.Paint
import android.graphics.RectF
import android.net.Uri
import io.github.hxfuxyy.zetacard.core.CropTransform
import io.github.hxfuxyy.zetacard.core.ImageAdjustments
import io.github.hxfuxyy.zetacard.core.ImageLimits
import java.io.File
import java.io.FileOutputStream
import java.io.IOException

class ImageTooLargeException : IOException("This image is too large")

object ImageProcessor {
    private val renderPaint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)

    fun decodeForEditing(resolver: ContentResolver, uri: Uri): Bitmap {
        val source = ImageDecoder.createSource(resolver, uri)
        return ImageDecoder.decodeBitmap(source) { decoder, info, _ ->
            val width = info.size.width
            val height = info.size.height
            if (!ImageLimits.isDecodable(width, height)) throw ImageTooLargeException()
            decoder.setTargetSampleSize(ImageLimits.sampleSize(width, height, ImageLimits.MAX_EDIT_SIDE))
            decoder.allocator = ImageDecoder.ALLOCATOR_SOFTWARE
            decoder.isMutableRequired = false
        }
    }

    fun decodePreview(file: File, targetWidth: Int, caption: String? = null): Bitmap? {
        if (!file.isFile) return null
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(file.path, bounds)
        if (!ImageLimits.isDecodable(bounds.outWidth, bounds.outHeight)) return null
        val sample = ImageLimits.sampleSize(bounds.outWidth, bounds.outHeight, targetWidth.coerceAtLeast(1) * 2)
        val bitmap = BitmapFactory.decodeFile(file.path, BitmapFactory.Options().apply { inSampleSize = sample })
        return if (caption != null) CaptionRenderer.render(bitmap, caption) else bitmap
    }

    fun renderCrop(source: Bitmap, transform: CropTransform, adjustments: ImageAdjustments = ImageAdjustments.Neutral): Bitmap {
        val result = Bitmap.createBitmap(ImageLimits.OUTPUT_WIDTH, ImageLimits.OUTPUT_HEIGHT, Bitmap.Config.ARGB_8888)
        Canvas(result).apply {
            translate(transform.translateX, transform.translateY)
            scale(transform.scaleX, transform.scaleY)
            drawBitmap(source, 0f, 0f, paintFor(adjustments))
        }
        return result
    }

    fun renderStretch(source: Bitmap, adjustments: ImageAdjustments = ImageAdjustments.Neutral): Bitmap {
        val result = Bitmap.createBitmap(ImageLimits.OUTPUT_WIDTH, ImageLimits.OUTPUT_HEIGHT, Bitmap.Config.ARGB_8888)
        val bounds = RectF(0f, 0f, ImageLimits.OUTPUT_WIDTH.toFloat(), ImageLimits.OUTPUT_HEIGHT.toFloat())
        Canvas(result).drawBitmap(source, null, bounds, paintFor(adjustments))
        return result
    }

    fun decodeAsset(assets: AssetManager, path: String, targetWidth: Int = 0): Bitmap {
        val options = BitmapFactory.Options()
        if (targetWidth > 0) {
            val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            assets.open(path).use { BitmapFactory.decodeStream(it, null, bounds) }
            options.inSampleSize = ImageLimits.sampleSize(bounds.outWidth, bounds.outHeight, targetWidth)
        }
        return assets.open(path).use { BitmapFactory.decodeStream(it, null, options) }
            ?: throw IOException("Could not decode $path")
    }

    private fun paintFor(adjustments: ImageAdjustments): Paint {
        if (adjustments.isNeutral) return renderPaint
        return Paint(renderPaint).apply { colorFilter = ColorMatrixColorFilter(ColorMatrix(adjustments.colorMatrix())) }
    }

    fun writePng(target: File, image: Bitmap) {
        val parent = target.parentFile ?: throw IOException("Invalid artwork location")
        if (!parent.isDirectory && !parent.mkdirs()) throw IOException("Could not create the artwork folder")
        val temp = File(parent, "${target.nameWithoutExtension}.tmp")
        try {
            FileOutputStream(temp).use { output ->
                if (!image.compress(Bitmap.CompressFormat.PNG, 100, output)) throw IOException("Could not encode the image")
                output.fd.sync()
            }
            if (!temp.renameTo(target)) throw IOException("Could not save the image")
        } finally {
            if (temp.exists()) temp.delete()
        }
    }
}
