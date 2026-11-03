package io.github.hxfuxyy.zetacard.ui.components

import android.provider.Settings
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import io.github.hxfuxyy.zetacard.R
import io.github.hxfuxyy.zetacard.core.CropGeometry

private const val DESIGN_WIDTH = 208f
private const val WORDMARK_RATIO = 500f / 96f

private fun cardShape(unit: Dp) = RoundedCornerShape(unit * 18)

@Composable
fun ZetaCardFace(modifier: Modifier = Modifier) {
    val colors = MaterialTheme.colorScheme
    val dark = isSystemInDarkTheme()
    val deep = if (dark) colors.inversePrimary else colors.primary
    val bright = if (dark) colors.primary else colors.tertiaryContainer
    val brush = Brush.linearGradient(
        colors = listOf(deep, colors.tertiary, bright),
        start = Offset(0f, Float.POSITIVE_INFINITY),
        end = Offset(Float.POSITIVE_INFINITY, 0f),
    )
    BoxWithConstraints(modifier.aspectRatio(CropGeometry.CARD_RATIO)) {
        val unit = maxWidth / DESIGN_WIDTH
        Box(Modifier.matchParentSize().clip(cardShape(unit)).background(brush)) {
            Box(
                Modifier
                    .padding(start = unit * 16, top = unit * 18)
                    .size(unit * 32, unit * 24)
                    .clip(RoundedCornerShape(unit * 5))
                    .background(Color.White.copy(alpha = 0.38f)),
            )
            Image(
                painterResource(R.drawable.zeta_wordmark),
                contentDescription = null,
                colorFilter = ColorFilter.tint(Color.White),
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .padding(start = unit * 16, bottom = unit * 16)
                    .width(unit * 92)
                    .aspectRatio(WORDMARK_RATIO),
            )
        }
    }
}

@Composable
private fun OriginalCardFace(modifier: Modifier = Modifier) {
    val colors = MaterialTheme.colorScheme
    BoxWithConstraints(modifier.aspectRatio(CropGeometry.CARD_RATIO)) {
        val unit = maxWidth / DESIGN_WIDTH
        Box(Modifier.matchParentSize().clip(cardShape(unit)).background(colors.surfaceContainerHighest)) {
            Column(
                Modifier.padding(start = unit * 16, top = unit * 30),
                verticalArrangement = Arrangement.spacedBy(unit * 8),
            ) {
                Box(Modifier.size(unit * 114, unit * 6).clip(RoundedCornerShape(unit * 3)).background(colors.onSurface.copy(alpha = 0.14f)))
                Box(Modifier.size(unit * 74, unit * 6).clip(RoundedCornerShape(unit * 3)).background(colors.onSurface.copy(alpha = 0.14f)))
            }
        }
    }
}

@Composable
fun ZetaHero(modifier: Modifier = Modifier, cardWidth: Dp = 200.dp) {
    val context = LocalContext.current
    val animate = remember {
        Settings.Global.getFloat(context.contentResolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f) > 0f
    }
    val progress = remember { Animatable(if (animate) 0f else 1f) }
    LaunchedEffect(Unit) {
        if (animate) progress.animateTo(1f, spring(dampingRatio = 0.72f, stiffness = Spring.StiffnessLow))
    }
    val unit = cardWidth / DESIGN_WIDTH
    Box(modifier.fillMaxWidth().height(cardWidth * 0.95f), contentAlignment = Alignment.Center) {
        OriginalCardFace(
            Modifier.width(cardWidth).graphicsLayer {
                translationX = (unit * -24).toPx()
                translationY = (unit * -12).toPx()
                rotationZ = 10f
                shadowElevation = (unit * 6).toPx()
                shape = cardShape(unit)
            },
        )
        ZetaCardFace(
            Modifier.width(cardWidth).graphicsLayer {
                val t = progress.value
                translationX = (unit * (-24 + 42 * t)).toPx()
                translationY = (unit * (-12 + 24 * t)).toPx()
                rotationZ = 10f - 16f * t
                alpha = (t / 0.35f).coerceIn(0f, 1f)
                shadowElevation = (unit * 14).toPx()
                shape = cardShape(unit)
            },
        )
    }
}
