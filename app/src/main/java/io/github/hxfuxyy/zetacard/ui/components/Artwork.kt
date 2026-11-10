package io.github.hxfuxyy.zetacard.ui.components

import android.graphics.Bitmap
import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Image
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import io.github.hxfuxyy.zetacard.image.ImageProcessor
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

private const val DEFAULT_PREVIEW_WIDTH = 720

@Composable
fun Artwork(
    file: File,
    revision: Long,
    placeholder: String,
    modifier: Modifier = Modifier,
    description: String = placeholder,
    caption: String? = null,
    shape: Shape = RoundedCornerShape(14.dp),
    borderColor: Color = MaterialTheme.colorScheme.outlineVariant,
) {
    BoxWithConstraints(
        modifier
            .clip(shape)
            .background(MaterialTheme.colorScheme.surfaceContainer)
            .border(1.dp, borderColor, shape),
        contentAlignment = Alignment.Center,
    ) {
        val targetWidth = if (constraints.hasBoundedWidth) constraints.maxWidth else DEFAULT_PREVIEW_WIDTH
        val bitmap by produceState<Bitmap?>(initialValue = null, file.path, revision, targetWidth, caption) {
            value = withContext(Dispatchers.IO) {
                runCatching { ImageProcessor.decodePreview(file, targetWidth, caption) }.getOrNull()
            }
        }
        Crossfade(targetState = bitmap, animationSpec = tween(320), label = "artwork") { image ->
            if (image != null) {
                Image(
                    image.asImageBitmap(),
                    contentDescription = description,
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Crop,
                )
            } else {
                Column(
                    Modifier.fillMaxSize().padding(8.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(4.dp, Alignment.CenterVertically),
                ) {
                    Icon(Icons.Outlined.Image, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text(
                        placeholder,
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center,
                    )
                }
            }
        }
    }
}
