package io.github.hxfuxyy.zetacard.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.BugReport
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.MoreVert
import androidx.compose.material.icons.outlined.Wallet
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.material3.pulltorefresh.PullToRefreshDefaults
import androidx.compose.material3.pulltorefresh.rememberPullToRefreshState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import io.github.hxfuxyy.zetacard.R
import io.github.hxfuxyy.zetacard.core.CardLabels
import io.github.hxfuxyy.zetacard.core.CropGeometry
import io.github.hxfuxyy.zetacard.data.CardItem
import io.github.hxfuxyy.zetacard.ui.LegacyNotice
import io.github.hxfuxyy.zetacard.ui.UiState
import io.github.hxfuxyy.zetacard.ui.components.Artwork
import io.github.hxfuxyy.zetacard.ui.components.CardEditor
import io.github.hxfuxyy.zetacard.ui.components.LogsDialog
import io.github.hxfuxyy.zetacard.ui.components.ZetaCardFace
import java.io.File

data class CardsActions(
    val onRefresh: () -> Unit,
    val onOpenWallet: () -> Unit,
    val onOpenGitHub: () -> Unit,
    val onShowLogs: () -> Unit,
    val onExportLogs: () -> Unit,
    val onCloseLogs: () -> Unit,
    val onChoose: (String) -> Unit,
    val onRename: (String, String) -> Unit,
    val onRestore: (String) -> Unit,
    val onRemoveLegacy: () -> Unit,
)

private data class Preview(val title: String, val file: File, val revision: Long)

@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun CardsScreen(state: UiState, versionLabel: String, actions: CardsActions) {
    var renaming by remember { mutableStateOf<CardItem?>(null) }
    var restoring by remember { mutableStateOf<CardItem?>(null) }
    var preview by remember { mutableStateOf<Preview?>(null) }
    val pullState = rememberPullToRefreshState()

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            Column {
                TopAppBar(
                    title = { Text("ZetaCard", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold) },
                    actions = {
                        IconButton(onClick = actions.onOpenWallet) {
                            Icon(Icons.Outlined.Wallet, contentDescription = "Open Google Wallet")
                        }
                        OverflowMenu(versionLabel, actions)
                    },
                )
                Box(Modifier.fillMaxWidth().height(4.dp)) {
                    if (state.busy) LinearProgressIndicator(Modifier.fillMaxWidth())
                }
            }
        },
    ) { inner ->
        PullToRefreshBox(
            isRefreshing = state.refreshing,
            onRefresh = actions.onRefresh,
            state = pullState,
            modifier = Modifier.fillMaxSize().padding(inner),
            indicator = {
                PullToRefreshDefaults.LoadingIndicator(
                    state = pullState,
                    isRefreshing = state.refreshing,
                    modifier = Modifier.align(Alignment.TopCenter),
                    containerColor = MaterialTheme.colorScheme.primaryContainer,
                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                )
            },
        ) {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 8.dp, bottom = 32.dp),
                verticalArrangement = Arrangement.spacedBy(18.dp),
            ) {
                item(key = "header") { Header(state) }
                state.legacy?.let { legacy -> item(key = "legacy") { LegacyNoticeCard(legacy, actions.onRemoveLegacy, actions.onRefresh) } }
                if (state.loaded && state.cards.isEmpty()) item(key = "empty") { EmptyState(actions.onOpenWallet) }
                items(state.cards, key = { it.id }) { card ->
                    CardEditor(
                        card = card,
                        enabled = !state.busy,
                        onChoose = { actions.onChoose(card.id) },
                        onRename = { renaming = card },
                        onRestore = { restoring = card },
                        onPreview = { title, file, revision -> preview = Preview(title, file, revision) },
                    )
                }
            }
        }
    }

    renaming?.let { card -> RenameDialog(card, onDismiss = { renaming = null }, onSave = { actions.onRename(card.id, it) }) }
    restoring?.let { card ->
        AlertDialog(
            onDismissRequest = { restoring = null },
            title = { Text("Restore original artwork?") },
            text = { Text("Your design for ${card.displayName.title} will be removed and Google Wallet will restart.") },
            confirmButton = {
                TextButton(onClick = {
                    restoring = null
                    actions.onRestore(card.id)
                }) { Text("Restore") }
            },
            dismissButton = { TextButton(onClick = { restoring = null }) { Text("Cancel") } },
        )
    }
    preview?.let { current -> PreviewDialog(current, onDismiss = { preview = null }) }
    state.logs?.let { LogsDialog(it, onExport = actions.onExportLogs, onClose = actions.onCloseLogs) }
}

@Composable
private fun OverflowMenu(versionLabel: String, actions: CardsActions) {
    var open by remember { mutableStateOf(false) }
    var about by remember { mutableStateOf(false) }
    Box {
        IconButton(onClick = { open = true }) { Icon(Icons.Outlined.MoreVert, contentDescription = "More options") }
        DropdownMenu(
            expanded = open,
            onDismissRequest = { open = false },
            shape = RoundedCornerShape(20.dp),
            containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
        ) {
            DropdownMenuItem(
                text = { Text("View logs") },
                leadingIcon = { Icon(Icons.Outlined.BugReport, contentDescription = null) },
                onClick = {
                    open = false
                    actions.onShowLogs()
                },
            )
            DropdownMenuItem(
                text = { Text("About ZetaCard") },
                leadingIcon = { Icon(Icons.Outlined.Info, contentDescription = null) },
                onClick = {
                    open = false
                    about = true
                },
            )
        }
    }
    if (about) AboutDialog(versionLabel, onOpenGitHub = actions.onOpenGitHub, onDismiss = { about = false })
}

@Composable
private fun AboutDialog(versionLabel: String, onOpenGitHub: () -> Unit, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        icon = { ZetaCardFace(Modifier.width(96.dp)) },
        title = { Text("ZetaCard") },
        text = {
            Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(versionLabel, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
                Text(
                    "Custom artwork for your Google Wallet cards, applied through LSPosed.",
                    style = MaterialTheme.typography.bodyMedium,
                    textAlign = TextAlign.Center,
                )
                Text(
                    "Made by hxfuxyy. Released under the ZetaCard Source License 2.0.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                )
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Close") } },
        dismissButton = {
            TextButton(onClick = {
                onDismiss()
                onOpenGitHub()
            }) {
                Icon(painterResource(R.drawable.ic_github), contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(8.dp))
                Text("Source code")
            }
        },
    )
}

@Composable
private fun Header(state: UiState) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text("Your cards", style = MaterialTheme.typography.headlineLarge, fontWeight = FontWeight.Bold)
        val count = state.cards.size
        if (count > 0) {
            Text(
                if (count == 1) "1 card from Google Wallet" else "$count cards from Google Wallet",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun LegacyNoticeCard(notice: LegacyNotice, onRemove: () -> Unit, onRetry: () -> Unit) {
    val colors = MaterialTheme.colorScheme
    Card(
        colors = CardDefaults.cardColors(
            containerColor = if (notice.complete) colors.secondaryContainer else colors.errorContainer,
            contentColor = if (notice.complete) colors.onSecondaryContainer else colors.onErrorContainer,
        ),
        shape = RoundedCornerShape(24.dp),
    ) {
        Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            if (notice.complete) {
                Text("Remove the old ZetaCard", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                Text(
                    "Your designs were copied to this version. Uninstall the old app (dev.zxcwsurx.zetacard) so only one " +
                        "module changes Google Wallet.",
                    style = MaterialTheme.typography.bodyMedium,
                )
                Button(onClick = onRemove) { Text("Open old app settings") }
            } else {
                Text("Keep the old ZetaCard for now", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                val progress = if (notice.expected > 0) " (${notice.present} of ${notice.expected} copied)" else ""
                Text(
                    "Designs from the old app have not been fully copied yet$progress. Grant root to ZetaCard and retry. " +
                        "Do not uninstall the old app until this finishes, or its designs will be lost.",
                    style = MaterialTheme.typography.bodyMedium,
                )
                Button(onClick = onRetry) { Text("Retry") }
            }
        }
    }
}

@Composable
private fun EmptyState(onOpenWallet: () -> Unit) {
    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
        shape = RoundedCornerShape(24.dp),
    ) {
        Column(Modifier.padding(22.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Icon(
                Icons.Outlined.Wallet,
                contentDescription = null,
                modifier = Modifier.size(34.dp),
                tint = MaterialTheme.colorScheme.primary,
            )
            Text("No cards found yet", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
            Text(
                "Enable ZetaCard for Google Wallet in LSPosed, open Wallet once and view your cards, then pull down here to refresh. " +
                    "In KernelSU, SukiSU, APatch or Magisk, ensure 'Umount modules' or DenyList is disabled for Google Wallet.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Button(onClick = onOpenWallet) { Text("Open Google Wallet") }
        }
    }
}

@Composable
private fun RenameDialog(card: CardItem, onDismiss: () -> Unit, onSave: (String) -> Unit) {
    var name by remember(card.id) { mutableStateOf(card.name) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Card name") },
        text = {
            OutlinedTextField(
                value = name,
                onValueChange = { name = it.take(CardLabels.MAX_USER_LENGTH) },
                label = { Text("Name") },
                singleLine = true,
            )
        },
        confirmButton = {
            TextButton(onClick = {
                if (name.isNotBlank()) onSave(name.trim())
                onDismiss()
            }) { Text("Save") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

@Composable
private fun PreviewDialog(preview: Preview, onDismiss: () -> Unit) {
    Dialog(onDismissRequest = onDismiss) {
        Surface(shape = RoundedCornerShape(24.dp), color = MaterialTheme.colorScheme.surface) {
            Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                Text(preview.title, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
                Artwork(
                    file = preview.file,
                    revision = preview.revision,
                    placeholder = "Preview unavailable",
                    description = preview.title,
                    modifier = Modifier.fillMaxWidth().aspectRatio(CropGeometry.CARD_RATIO),
                )
                TextButton(onClick = onDismiss, modifier = Modifier.align(Alignment.End)) { Text("Close") }
            }
        }
    }
}
