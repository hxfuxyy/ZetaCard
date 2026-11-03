package io.github.hxfuxyy.zetacard.ui.components

import android.graphics.Bitmap
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
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import io.github.hxfuxyy.zetacard.image.ImageProcessor
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

private val ArtworkShape = RoundedCornerShape(14.dp)
private const val DEFAULT_PREVIEW_WIDTH = 720

@Composable
fun Artwork(
    file: File,
    revision: Long,
    placeholder: String,
    modifier: Modifier = Modifier,
    description: String = placeholder,
    borderColor: Color = MaterialTheme.colorScheme.outlineVariant,
) {
    BoxWithConstraints(
        modifier
            .clip(ArtworkShape)
            .background(MaterialTheme.colorScheme.surfaceContainer)
            .border(1.dp, borderColor, ArtworkShape),
        contentAlignment = Alignment.Center,
    ) {
        val targetWidth = if (constraints.hasBoundedWidth) constraints.maxWidth else DEFAULT_PREVIEW_WIDTH
        val bitmap by produceState<Bitmap?>(initialValue = null, file.path, revision, targetWidth) {
            value = withContext(Dispatchers.IO) {
                runCatching { ImageProcessor.decodePreview(file, targetWidth) }.getOrNull()
            }
        }
        val image = bitmap
        if (image != null) {
            Image(
                image.asImageBitmap(),
                contentDescription = description,
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Crop,
            )
        } else {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(4.dp),
                modifier = Modifier.padding(8.dp),
            ) {
                Icon(Icons.Outlined.Image, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
                Text(
                    placeholder,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                )
            }
        }
    }
}
