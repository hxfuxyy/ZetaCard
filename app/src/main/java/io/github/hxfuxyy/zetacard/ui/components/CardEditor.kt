package io.github.hxfuxyy.zetacard.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.AddPhotoAlternate
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.Edit
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
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import io.github.hxfuxyy.zetacard.core.CropGeometry
import io.github.hxfuxyy.zetacard.data.CardItem
import io.github.hxfuxyy.zetacard.data.SyncState
import java.io.File

@Composable
fun CardEditor(
    card: CardItem,
    enabled: Boolean,
    onChoose: () -> Unit,
    onRename: () -> Unit,
    onRestore: () -> Unit,
    onPreview: (String, File, Long) -> Unit,
) {
    val name = card.displayName
    Card(
        shape = RoundedCornerShape(26.dp),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            Row(verticalAlignment = Alignment.Top) {
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            name.title,
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
                IconButton(onClick = onRename) { Icon(Icons.Outlined.Edit, contentDescription = "Rename card") }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                ArtworkTile(
                    label = "Original",
                    file = card.stock,
                    revision = card.stockRevision,
                    placeholder = "Open Wallet to load",
                    highlighted = false,
                    modifier = Modifier.weight(1f),
                    onClick = { onPreview("Original artwork", card.stock, card.stockRevision) },
                )
                ArtworkTile(
                    label = "Your design",
                    file = card.custom,
                    revision = card.customRevision,
                    placeholder = "Add your artwork",
                    highlighted = card.hasCustom,
                    modifier = Modifier.weight(1f),
                    onClick = {
                        if (card.hasCustom) {
                            onPreview("Your design", card.custom, card.customRevision)
                        } else if (enabled) {
                            onChoose()
                        }
                    },
                )
            }
            if (card.hasCustom) {
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    Button(onClick = onChoose, enabled = enabled, modifier = Modifier.weight(1f), shape = RoundedCornerShape(16.dp)) {
                        Icon(Icons.Outlined.AddPhotoAlternate, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(8.dp))
                        Text("Change", maxLines = 1)
                    }
                    OutlinedButton(
                        onClick = onRestore,
                        enabled = enabled,
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(16.dp),
                    ) {
                        Icon(Icons.Outlined.Restore, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(8.dp))
                        Text("Restore", maxLines = 1)
                    }
                }
            } else {
                Button(onClick = onChoose, enabled = enabled, modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(16.dp)) {
                    Icon(Icons.Outlined.AddPhotoAlternate, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                    Text("Choose photo")
                }
            }
        }
    }
}

@Composable
private fun StatusPill(state: SyncState) {
    val colors = MaterialTheme.colorScheme
    val (container, content) = when (state) {
        SyncState.APPLIED -> colors.tertiaryContainer to colors.onTertiaryContainer
        SyncState.PENDING_APPLY, SyncState.PENDING_RESTORE -> colors.secondaryContainer to colors.onSecondaryContainer
        SyncState.ORIGINAL -> colors.surfaceContainerHigh to colors.onSurfaceVariant
    }
    val label = when (state) {
        SyncState.APPLIED -> "Custom artwork active"
        SyncState.PENDING_APPLY -> "Saved, waiting for LSPosed"
        SyncState.PENDING_RESTORE -> "Restore waiting for LSPosed"
        SyncState.ORIGINAL -> "Original artwork"
    }
    Surface(shape = RoundedCornerShape(50), color = container, contentColor = content) {
        Row(
            Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            when (state) {
                SyncState.APPLIED -> Icon(Icons.Outlined.CheckCircle, contentDescription = null, modifier = Modifier.size(14.dp))
                SyncState.PENDING_APPLY, SyncState.PENDING_RESTORE ->
                    Icon(Icons.Outlined.Schedule, contentDescription = null, modifier = Modifier.size(14.dp))
                SyncState.ORIGINAL -> Unit
            }
            Text(label, style = MaterialTheme.typography.labelMedium)
        }
    }
}

@Composable
private fun ArtworkTile(
    label: String,
    file: File,
    revision: Long,
    placeholder: String,
    highlighted: Boolean,
    modifier: Modifier,
    onClick: () -> Unit,
) {
    Column(modifier, verticalArrangement = Arrangement.spacedBy(7.dp)) {
        Artwork(
            file = file,
            revision = revision,
            placeholder = placeholder,
            description = label,
            modifier = Modifier.fillMaxWidth().aspectRatio(CropGeometry.CARD_RATIO).clickable(onClick = onClick),
            borderColor = if (highlighted) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant,
        )
        Text(
            label,
            style = MaterialTheme.typography.labelMedium,
            color = if (highlighted) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}
