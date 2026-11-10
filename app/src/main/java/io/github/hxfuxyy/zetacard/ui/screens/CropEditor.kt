package io.github.hxfuxyy.zetacard.ui.screens

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.Tune
import androidx.compose.material3.Button
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.ColorMatrix
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import io.github.hxfuxyy.zetacard.R
import io.github.hxfuxyy.zetacard.core.CropGeometry
import io.github.hxfuxyy.zetacard.core.ImageAdjustments
import io.github.hxfuxyy.zetacard.core.Pan
import io.github.hxfuxyy.zetacard.core.PixelSize
import io.github.hxfuxyy.zetacard.ui.CropRequest
import io.github.hxfuxyy.zetacard.ui.PhotoFit
import kotlin.math.roundToInt

@Composable
fun CropEditor(
    request: CropRequest,
    onCancel: () -> Unit,
    onSave: (PhotoFit, Float, Pan, PixelSize, ImageAdjustments) -> Unit,
) {
    val imageSize = request.size
    val image: ImageBitmap = remember(request) { request.image.asImageBitmap() }
    var fit by remember(request) { mutableStateOf(PhotoFit.CROP) }
    var zoom by remember(request) { mutableFloatStateOf(1f) }
    var pan by remember(request) { mutableStateOf(Pan.Zero) }
    var viewport by remember(request) { mutableStateOf(PixelSize.Zero) }
    var brightness by remember(request) { mutableFloatStateOf(0f) }
    var shade by remember(request) { mutableFloatStateOf(0f) }
    var adjusting by remember(request) { mutableStateOf(false) }
    val adjustments = ImageAdjustments(brightness, shade)
    val filter = if (adjustments.isNeutral) null else ColorFilter.colorMatrix(ColorMatrix(adjustments.colorMatrix()))
    val fillZoom = CropGeometry.coverZoom(imageSize, viewport)
    val zoomLimit = CropGeometry.maxZoom(imageSize, viewport)
    val cropping = fit == PhotoFit.CROP

    Dialog(onDismissRequest = onCancel, properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false)) {
        Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
            Column(
                Modifier.fillMaxSize().safeDrawingPadding().padding(horizontal = 20.dp, vertical = 12.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconButton(onClick = onCancel) {
                        Icon(Icons.Outlined.Close, contentDescription = stringResource(R.string.action_cancel))
                    }
                    Text(
                        stringResource(R.string.crop_title),
                        modifier = Modifier.weight(1f),
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.SemiBold,
                    )
                    FilterChip(
                        selected = adjusting,
                        onClick = { adjusting = !adjusting },
                        label = { Text(stringResource(R.string.crop_adjust)) },
                        leadingIcon = { Icon(Icons.Outlined.Tune, contentDescription = null) },
                    )
                }
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    FitButton(stringResource(R.string.crop_mode_crop), selected = cropping, modifier = Modifier.weight(1f)) {
                        fit =
                            PhotoFit.CROP
                    }
                    FitButton(stringResource(R.string.crop_mode_stretch), selected = !cropping, modifier = Modifier.weight(1f)) {
                        fit = PhotoFit.STRETCH
                    }
                }
                AnimatedContent(
                    targetState = cropping,
                    transitionSpec = { fadeIn(tween(220)) togetherWith fadeOut(tween(160)) },
                    label = "cropHint",
                ) { crop ->
                    Text(
                        stringResource(if (crop) R.string.crop_hint else R.string.stretch_hint),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                val frameModifier = if (cropping) {
                    Modifier.fillMaxWidth().weight(1f)
                } else {
                    Modifier.fillMaxWidth().aspectRatio(CropGeometry.CARD_RATIO)
                }
                Box(
                    frameModifier
                        .clip(RoundedCornerShape(20.dp))
                        .background(MaterialTheme.colorScheme.surfaceContainer)
                        .onSizeChanged {
                            viewport = PixelSize(it.width, it.height)
                            if (cropping) zoom = zoom.coerceAtLeast(CropGeometry.coverZoom(imageSize, viewport))
                            pan = CropGeometry.constrainPan(pan, imageSize, viewport, zoom)
                        }
                        .then(
                            if (cropping) {
                                Modifier.pointerInput(request, viewport, fillZoom, zoomLimit) {
                                    detectTransformGestures { _, drag, gestureZoom, _ ->
                                        val nextZoom = (zoom * gestureZoom).coerceIn(fillZoom, zoomLimit)
                                        zoom = nextZoom
                                        pan = CropGeometry.constrainPan(pan + Pan(drag.x, drag.y), imageSize, viewport, nextZoom)
                                    }
                                }
                            } else {
                                Modifier
                            },
                        ),
                ) {
                    if (cropping) {
                        Image(
                            image,
                            contentDescription = stringResource(R.string.crop_photo_description),
                            modifier = Modifier.fillMaxSize().graphicsLayer {
                                scaleX = zoom
                                scaleY = zoom
                                translationX = pan.x
                                translationY = pan.y
                            },
                            contentScale = ContentScale.Fit,
                            colorFilter = filter,
                        )
                        CropFrameOverlay(MaterialTheme.colorScheme.primary)
                    } else {
                        Image(
                            image,
                            contentDescription = stringResource(R.string.stretch_photo_description),
                            modifier = Modifier.fillMaxSize(),
                            contentScale = ContentScale.FillBounds,
                            colorFilter = filter,
                        )
                    }
                }
                if (!cropping) Spacer(Modifier.weight(1f))
                AnimatedVisibility(visible = adjusting, enter = fadeIn() + expandVertically(), exit = fadeOut() + shrinkVertically()) {
                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        LabeledSlider(
                            stringResource(R.string.adjust_brightness),
                            brightness,
                            ImageAdjustments.MIN_BRIGHTNESS..ImageAdjustments.MAX_BRIGHTNESS,
                        ) { brightness = it }
                        LabeledSlider(stringResource(R.string.adjust_shade), shade, 0f..ImageAdjustments.MAX_SHADE) { shade = it }
                    }
                }
                AnimatedVisibility(visible = cropping, enter = fadeIn() + expandVertically(), exit = fadeOut() + shrinkVertically()) {
                    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                stringResource(R.string.crop_zoom),
                                style = MaterialTheme.typography.labelLarge,
                                modifier = Modifier.width(88.dp),
                            )
                            Slider(
                                value = zoom.coerceIn(fillZoom, zoomLimit),
                                onValueChange = {
                                    zoom = it
                                    pan = CropGeometry.constrainPan(pan, imageSize, viewport, zoom)
                                },
                                valueRange = fillZoom..zoomLimit,
                                modifier = Modifier.weight(1f).padding(horizontal = 8.dp),
                            )
                            Text(
                                String.format(LocalConfiguration.current.locales[0], "%.1f×", zoom),
                                style = MaterialTheme.typography.labelLarge,
                            )
                        }
                        FilledTonalButton(
                            onClick = {
                                zoom = fillZoom
                                pan = Pan.Zero
                            },
                            modifier = Modifier.fillMaxWidth(),
                        ) { Text(stringResource(R.string.crop_reset)) }
                    }
                }
                Button(
                    onClick = { onSave(fit, zoom, pan, viewport, adjustments) },
                    enabled = !cropping || CropGeometry.canApply(imageSize, viewport, zoom),
                    modifier = Modifier.fillMaxWidth().height(54.dp),
                    shape = RoundedCornerShape(18.dp),
                ) {
                    Text(stringResource(if (cropping) R.string.crop_apply else R.string.stretch_apply))
                }
            }
        }
    }
}

@Composable
private fun LabeledSlider(label: String, value: Float, range: ClosedFloatingPointRange<Float>, onChange: (Float) -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(label, style = MaterialTheme.typography.labelLarge, modifier = Modifier.width(88.dp))
        Slider(value = value, onValueChange = onChange, valueRange = range, modifier = Modifier.weight(1f).padding(horizontal = 8.dp))
        Text(
            String.format(LocalConfiguration.current.locales[0], "%+d", (value * 100).roundToInt()),
            style = MaterialTheme.typography.labelLarge,
            modifier = Modifier.width(44.dp),
        )
    }
}

@Composable
private fun FitButton(label: String, selected: Boolean, modifier: Modifier, onClick: () -> Unit) {
    if (selected) {
        FilledTonalButton(onClick = {}, modifier = modifier) { Text(label) }
    } else {
        OutlinedButton(onClick = onClick, modifier = modifier) { Text(label) }
    }
}

@Composable
private fun CropFrameOverlay(frameColor: Color) {
    Canvas(Modifier.fillMaxSize()) {
        val frame = CropGeometry.frame(PixelSize(size.width.roundToInt(), size.height.roundToInt()))
        val left = (size.width - frame.width) / 2f
        val top = (size.height - frame.height) / 2f
        val right = left + frame.width
        val bottom = top + frame.height
        val shade = Color.Black.copy(alpha = 0.58f)
        drawRect(shade, size = Size(size.width, top))
        drawRect(shade, topLeft = Offset(0f, bottom), size = Size(size.width, size.height - bottom))
        drawRect(shade, topLeft = Offset(0f, top), size = Size(left, frame.height.toFloat()))
        drawRect(shade, topLeft = Offset(right, top), size = Size(size.width - right, frame.height.toFloat()))
        drawRoundRect(
            frameColor,
            topLeft = Offset(left, top),
            size = Size(frame.width.toFloat(), frame.height.toFloat()),
            cornerRadius = CornerRadius(16.dp.toPx()),
            style = Stroke(2.dp.toPx()),
        )
    }
}
