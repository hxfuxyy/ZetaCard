package io.github.hxfuxyy.zetacard.ui.components

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandHorizontally
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.shrinkHorizontally
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.Palette
import androidx.compose.material.icons.outlined.Restore
import androidx.compose.material.icons.outlined.Schedule
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import io.github.hxfuxyy.zetacard.R
import io.github.hxfuxyy.zetacard.core.CardLabels
import io.github.hxfuxyy.zetacard.core.CropGeometry
import io.github.hxfuxyy.zetacard.data.CardItem
import io.github.hxfuxyy.zetacard.data.SyncState
import java.io.File

private val EditorShape = RoundedCornerShape(28.dp)
private val PreviewShape = RoundedCornerShape(20.dp)

@Composable
fun cardTitle(card: CardItem): String =
    if (card.name == CardLabels.DEFAULT) stringResource(R.string.card_default_name) else card.displayName.title

@Composable
fun CardEditor(
    card: CardItem,
    enabled: Boolean,
    onChangeDesign: () -> Unit,
    onRename: () -> Unit,
    onRestore: () -> Unit,
    onCaptionChange: (Boolean) -> Unit,
    onPreview: (String, File, Long, String?) -> Unit,
    modifier: Modifier = Modifier,
) {
    val name = card.displayName
    val haptics = rememberHaptics()
    val setCaption: (Boolean) -> Unit = {
        haptics.toggle(it)
        onCaptionChange(it)
    }
    var showDesign by remember(card.id, card.hasCustom, card.customRevision) { mutableStateOf(card.hasCustom) }
    val originalLabel = stringResource(R.string.label_original)
    val designLabel = stringResource(R.string.label_your_design)
    Card(
        modifier = modifier,
        shape = EditorShape,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            Row(verticalAlignment = Alignment.Top) {
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            cardTitle(card),
                            modifier = Modifier.weight(1f, fill = false),
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.SemiBold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                        if (name.suffix != null) {
                            Spacer(Modifier.width(8.dp))
                            Text(name.suffix, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                        }
                    }
                    StatusPill(card.syncState)
                }
                IconButton(onClick = onRename) {
                    Icon(Icons.Outlined.Edit, contentDescription = stringResource(R.string.action_rename_card))
                }
            }
            AnimatedContent(
                targetState = showDesign && card.hasCustom,
                transitionSpec = {
                    (fadeIn(tween(260)) + scaleIn(tween(320), initialScale = 0.96f)) togetherWith fadeOut(tween(180))
                },
                label = "preview",
            ) { design ->
                val file = if (design) card.custom else card.stock
                val revision = if (design) card.customRevision else card.stockRevision
                val title = if (design) designLabel else originalLabel
                val caption = if (design) card.caption else null
                val onPreviewClick: () -> Unit = {
                    if (design || !card.hasCustom) {
                        onPreview(title, file, revision, caption)
                    } else {
                        showDesign = true
                    }
                }
                Artwork(
                    file = file,
                    revision = revision,
                    placeholder = stringResource(if (design) R.string.placeholder_add_design else R.string.placeholder_open_wallet),
                    description = title,
                    caption = caption,
                    shape = PreviewShape,
                    borderColor = if (design) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant,
                    modifier = Modifier
                        .fillMaxWidth()
                        .aspectRatio(CropGeometry.CARD_RATIO)
                        .clip(PreviewShape)
                        .bouncyClickable(onClick = onPreviewClick),
                )
            }
            AnimatedVisibility(visible = card.hasCustom, enter = fadeIn() + expandVertically(), exit = fadeOut() + shrinkVertically()) {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    PreviewToggle(showDesign, originalLabel, designLabel) { showDesign = it }
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(16.dp))
                            .clickable(enabled = enabled) { setCaption(!card.captionEnabled) }
                            .padding(horizontal = 4.dp, vertical = 2.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Column(Modifier.weight(1f)) {
                            Text(stringResource(R.string.caption_title), style = MaterialTheme.typography.titleSmall)
                            Text(
                                stringResource(R.string.caption_summary),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        Switch(checked = card.captionEnabled, onCheckedChange = setCaption, enabled = enabled)
                    }
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Button(
                    onClick = onChangeDesign,
                    enabled = enabled,
                    modifier = Modifier.weight(1f).height(52.dp),
                    shape = RoundedCornerShape(18.dp),
                ) {
                    Icon(Icons.Outlined.Palette, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                    Text(stringResource(if (card.hasCustom) R.string.action_change_design else R.string.action_choose_design), maxLines = 1)
                }
                AnimatedVisibility(
                    visible = card.hasCustom,
                    enter = fadeIn() + expandHorizontally(),
                    exit = fadeOut() + shrinkHorizontally(),
                ) {
                    OutlinedButton(
                        onClick = onRestore,
                        enabled = enabled,
                        modifier = Modifier.height(52.dp),
                        shape = RoundedCornerShape(18.dp),
                    ) {
                        Icon(Icons.Outlined.Restore, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(8.dp))
                        Text(stringResource(R.string.action_restore), maxLines = 1)
                    }
                }
            }
        }
    }
}

@Composable
private fun PreviewToggle(showDesign: Boolean, originalLabel: String, designLabel: String, onChange: (Boolean) -> Unit) {
    val colors = MaterialTheme.colorScheme
    val haptics = rememberHaptics()
    BoxWithConstraints(
        Modifier
            .fillMaxWidth()
            .height(42.dp)
            .clip(RoundedCornerShape(21.dp))
            .background(colors.surfaceContainerHigh),
    ) {
        val half = maxWidth / 2
        val offset by animateDpAsState(
            targetValue = if (showDesign) half else 0.dp,
            animationSpec = spring(dampingRatio = 0.78f, stiffness = Spring.StiffnessMediumLow),
            label = "toggle",
        )
        Box(
            Modifier
                .offset(x = offset)
                .width(half)
                .fillMaxHeight()
                .padding(4.dp)
                .clip(RoundedCornerShape(17.dp))
                .background(colors.secondaryContainer),
        )
        Row(Modifier.fillMaxSize()) {
            listOf(false to originalLabel, true to designLabel).forEach { (design, label) ->
                val selected = design == showDesign
                val color by animateColorAsState(
                    if (selected) colors.onSecondaryContainer else colors.onSurfaceVariant.copy(alpha = 0.6f),
                    label = "toggleText",
                )
                Box(
                    Modifier.weight(1f).fillMaxHeight().clip(RoundedCornerShape(21.dp)).clickable {
                        if (design != showDesign) haptics.tick()
                        onChange(design)
                    },
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        label,
                        style = MaterialTheme.typography.labelLarge,
                        color = color,
                        fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
                        maxLines = 1,
                    )
                }
            }
        }
    }
}

@Composable
private fun StatusPill(state: SyncState) {
    val colors = MaterialTheme.colorScheme
    val container by animateColorAsState(
        when (state) {
            SyncState.APPLIED -> colors.tertiaryContainer
            SyncState.PENDING_APPLY, SyncState.PENDING_RESTORE -> colors.secondaryContainer
            SyncState.ORIGINAL -> colors.surfaceContainerHigh
        },
        animationSpec = tween(400),
        label = "pillContainer",
    )
    val content by animateColorAsState(
        when (state) {
            SyncState.APPLIED -> colors.onTertiaryContainer
            SyncState.PENDING_APPLY, SyncState.PENDING_RESTORE -> colors.onSecondaryContainer
            SyncState.ORIGINAL -> colors.onSurfaceVariant
        },
        animationSpec = tween(400),
        label = "pillContent",
    )
    Surface(shape = RoundedCornerShape(50), color = container, contentColor = content) {
        AnimatedContent(targetState = state, transitionSpec = {
            fadeIn(tween(220)) togetherWith fadeOut(tween(160))
        }, label = "pill") { current ->
            Row(
                Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                when (current) {
                    SyncState.APPLIED -> Icon(Icons.Outlined.CheckCircle, contentDescription = null, modifier = Modifier.size(14.dp))
                    SyncState.PENDING_APPLY, SyncState.PENDING_RESTORE ->
                        Icon(Icons.Outlined.Schedule, contentDescription = null, modifier = Modifier.size(14.dp))
                    SyncState.ORIGINAL -> Unit
                }
                Text(
                    stringResource(
                        when (current) {
                            SyncState.APPLIED -> R.string.status_active
                            SyncState.PENDING_APPLY -> R.string.status_pending_apply
                            SyncState.PENDING_RESTORE -> R.string.status_pending_restore
                            SyncState.ORIGINAL -> R.string.status_original
                        },
                    ),
                    style = MaterialTheme.typography.labelMedium,
                )
            }
        }
    }
}
