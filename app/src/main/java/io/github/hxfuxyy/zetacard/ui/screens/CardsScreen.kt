package io.github.hxfuxyy.zetacard.ui.screens

import android.graphics.Bitmap
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
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
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Backup
import androidx.compose.material.icons.outlined.BugReport
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.Language
import androidx.compose.material.icons.outlined.MoreVert
import androidx.compose.material.icons.outlined.Restore
import androidx.compose.material.icons.outlined.Wallet
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.HorizontalDivider
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
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import io.github.hxfuxyy.zetacard.R
import io.github.hxfuxyy.zetacard.core.CardLabels
import io.github.hxfuxyy.zetacard.core.CropGeometry
import io.github.hxfuxyy.zetacard.data.CardItem
import io.github.hxfuxyy.zetacard.data.Preset
import io.github.hxfuxyy.zetacard.ui.LegacyNotice
import io.github.hxfuxyy.zetacard.ui.UiState
import io.github.hxfuxyy.zetacard.ui.components.AboutSheet
import io.github.hxfuxyy.zetacard.ui.components.Appear
import io.github.hxfuxyy.zetacard.ui.components.Artwork
import io.github.hxfuxyy.zetacard.ui.components.CardEditor
import io.github.hxfuxyy.zetacard.ui.components.DesignPickerSheet
import io.github.hxfuxyy.zetacard.ui.components.LanguageDialog
import io.github.hxfuxyy.zetacard.ui.components.LogsDialog
import io.github.hxfuxyy.zetacard.ui.components.VersionBadge
import io.github.hxfuxyy.zetacard.ui.components.ZetaCardFace
import io.github.hxfuxyy.zetacard.ui.components.ZetaHero
import io.github.hxfuxyy.zetacard.ui.components.cardTitle
import io.github.hxfuxyy.zetacard.ui.components.rememberHaptics
import java.io.File

data class CardsActions(
    val onRefresh: () -> Unit,
    val onOpenWallet: () -> Unit,
    val onShowLogs: () -> Unit,
    val onExportLogs: () -> Unit,
    val onCloseLogs: () -> Unit,
    val onChoosePhoto: (String) -> Unit,
    val onPreset: (String, Preset) -> Unit,
    val onCaption: (String, Boolean) -> Unit,
    val onRename: (String, String) -> Unit,
    val onRestore: (String) -> Unit,
    val onRemoveLegacy: () -> Unit,
    val onExportBackup: () -> Unit,
    val onImportBackup: () -> Unit,
    val onLanguage: (String) -> Unit,
    val onOpenUrl: (String) -> Unit,
    val loadThumbnail: suspend (Preset) -> Bitmap?,
)

private data class Preview(val title: String, val file: File, val revision: Long, val caption: String?)

@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun CardsScreen(
    state: UiState,
    versionName: String,
    versionCode: Int,
    languageTag: String,
    actions: CardsActions,
) {
    var renaming by remember { mutableStateOf<CardItem?>(null) }
    var restoring by remember { mutableStateOf<CardItem?>(null) }
    var picking by remember { mutableStateOf<CardItem?>(null) }
    var preview by remember { mutableStateOf<Preview?>(null) }
    val pullState = rememberPullToRefreshState()
    val haptics = rememberHaptics()

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            Column {
                TopAppBar(
                    title = {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Text(stringResource(R.string.app_name), fontWeight = FontWeight.Bold)
                            VersionBadge(versionName)
                        }
                    },
                    actions = {
                        IconButton(onClick = actions.onOpenWallet) {
                            Icon(Icons.Outlined.Wallet, contentDescription = stringResource(R.string.action_open_wallet))
                        }
                        OverflowMenu(versionName, versionCode, languageTag, busy = state.busy, actions = actions)
                    },
                )
                Box(Modifier.fillMaxWidth().height(4.dp)) {
                    androidx.compose.animation.AnimatedVisibility(
                        visible = state.busy,
                        enter = fadeIn(),
                        exit = fadeOut(),
                    ) {
                        LinearProgressIndicator(Modifier.fillMaxWidth())
                    }
                }
            }
        },
    ) { inner ->
        PullToRefreshBox(
            isRefreshing = state.refreshing,
            onRefresh = {
                haptics.threshold()
                actions.onRefresh()
            },
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
                contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 4.dp, bottom = 32.dp),
                verticalArrangement = Arrangement.spacedBy(18.dp),
            ) {
                item(key = "header") { Appear(Modifier.animateItem(), once = true) { Header(state) } }
                state.legacy?.let { legacy ->
                    item(key = "legacy") {
                        LegacyNoticeCard(legacy, actions.onRemoveLegacy, actions.onRefresh, Modifier.animateItem())
                    }
                }
                if (state.loaded && state.cards.isEmpty()) {
                    item(key = "empty") {
                        Appear(Modifier.animateItem(), once = true) { EmptyState(scanning = state.scanning, onScan = actions.onRefresh) }
                    }
                }
                itemsIndexed(state.cards, key = { _, card -> card.id }) { index, card ->
                    Appear(Modifier.animateItem(), delayMillis = 80L * index.coerceAtMost(5), once = true) {
                        CardEditor(
                            card = card,
                            enabled = !state.busy,
                            onChangeDesign = { picking = card },
                            onRename = { renaming = card },
                            onRestore = { restoring = card },
                            onCaptionChange = { actions.onCaption(card.id, it) },
                            onPreview = { title, file, revision, caption -> preview = Preview(title, file, revision, caption) },
                        )
                    }
                }
            }
        }
    }

    picking?.let { card ->
        DesignPickerSheet(
            cardName = cardTitle(card),
            onDismiss = { picking = null },
            onPickPhoto = {
                picking = null
                actions.onChoosePhoto(card.id)
            },
            onPreset = { preset ->
                picking = null
                actions.onPreset(card.id, preset)
            },
            loadThumbnail = actions.loadThumbnail,
        )
    }
    renaming?.let { card -> RenameDialog(card, onDismiss = { renaming = null }, onSave = { actions.onRename(card.id, it) }) }
    restoring?.let { card ->
        AlertDialog(
            onDismissRequest = { restoring = null },
            title = { Text(stringResource(R.string.restore_title)) },
            text = { Text(stringResource(R.string.restore_message, cardTitle(card))) },
            confirmButton = {
                TextButton(onClick = {
                    restoring = null
                    actions.onRestore(card.id)
                }) { Text(stringResource(R.string.action_restore)) }
            },
            dismissButton = { TextButton(onClick = { restoring = null }) { Text(stringResource(R.string.action_cancel)) } },
        )
    }
    preview?.let { current -> PreviewDialog(current, onDismiss = { preview = null }) }
    state.logs?.let { LogsDialog(it, onExport = actions.onExportLogs, onClose = actions.onCloseLogs) }
}

@Composable
private fun OverflowMenu(versionName: String, versionCode: Int, languageTag: String, busy: Boolean, actions: CardsActions) {
    var open by remember { mutableStateOf(false) }
    var about by remember { mutableStateOf(false) }
    var language by remember { mutableStateOf(false) }
    Box {
        IconButton(onClick = { open = true }) { Icon(Icons.Outlined.MoreVert, contentDescription = stringResource(R.string.action_more)) }
        DropdownMenu(
            expanded = open,
            onDismissRequest = { open = false },
            shape = RoundedCornerShape(20.dp),
            containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
        ) {
            MenuItem(R.string.menu_backup, Icons.Outlined.Backup, enabled = !busy) {
                open = false
                actions.onExportBackup()
            }
            MenuItem(R.string.menu_restore_backup, Icons.Outlined.Restore, enabled = !busy) {
                open = false
                actions.onImportBackup()
            }
            HorizontalDivider(Modifier.padding(vertical = 4.dp))
            MenuItem(R.string.menu_language, Icons.Outlined.Language) {
                open = false
                language = true
            }
            MenuItem(R.string.menu_logs, Icons.Outlined.BugReport) {
                open = false
                actions.onShowLogs()
            }
            MenuItem(R.string.menu_about, Icons.Outlined.Info) {
                open = false
                about = true
            }
        }
    }
    if (about) AboutSheet(versionName, versionCode, onOpenUrl = actions.onOpenUrl, onDismiss = { about = false })
    if (language) {
        LanguageDialog(
            selected = languageTag,
            onSelect = {
                language = false
                actions.onLanguage(it)
            },
            onDismiss = { language = false },
        )
    }
}

@Composable
private fun MenuItem(label: Int, icon: ImageVector, enabled: Boolean = true, onClick: () -> Unit) {
    DropdownMenuItem(
        text = { Text(stringResource(label)) },
        leadingIcon = { Icon(icon, contentDescription = null) },
        enabled = enabled,
        onClick = onClick,
    )
}

@Composable
private fun Header(state: UiState, modifier: Modifier = Modifier) {
    val count = state.cards.size
    Column(modifier.padding(top = 8.dp, bottom = 2.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(stringResource(R.string.cards_title), style = MaterialTheme.typography.headlineLarge, fontWeight = FontWeight.Bold)
        val resources = LocalResources.current
        AnimatedContent(
            targetState = count,
            transitionSpec = { (fadeIn() + slideInVertically { it / 2 }) togetherWith (fadeOut() + slideOutVertically { -it / 2 }) },
            label = "count",
        ) { value ->
            Text(
                if (value > 0) resources.getQuantityString(R.plurals.cards_count, value, value) else " ",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun LegacyNoticeCard(notice: LegacyNotice, onRemove: () -> Unit, onRetry: () -> Unit, modifier: Modifier = Modifier) {
    val colors = MaterialTheme.colorScheme
    Card(
        modifier = modifier,
        colors = CardDefaults.cardColors(
            containerColor = if (notice.complete) colors.secondaryContainer else colors.errorContainer,
            contentColor = if (notice.complete) colors.onSecondaryContainer else colors.onErrorContainer,
        ),
        shape = RoundedCornerShape(24.dp),
    ) {
        Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            if (notice.complete) {
                Text(
                    stringResource(R.string.legacy_done_title),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                )
                Text(stringResource(R.string.legacy_done_message), style = MaterialTheme.typography.bodyMedium)
                Button(onClick = onRemove) { Text(stringResource(R.string.legacy_open_settings)) }
            } else {
                Text(
                    stringResource(R.string.legacy_pending_title),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                )
                val message = if (notice.expected > 0) {
                    stringResource(R.string.legacy_pending_progress, notice.present, notice.expected)
                } else {
                    stringResource(R.string.legacy_pending_message)
                }
                Text(message, style = MaterialTheme.typography.bodyMedium)
                Button(onClick = onRetry) { Text(stringResource(R.string.action_retry)) }
            }
        }
    }
}

@Composable
private fun EmptyState(scanning: Boolean, onScan: () -> Unit, modifier: Modifier = Modifier) {
    Card(
        modifier = modifier,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
        shape = RoundedCornerShape(28.dp),
    ) {
        Column(Modifier.padding(22.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            ZetaHero(cardWidth = 150.dp)
            AnimatedContent(
                targetState = scanning,
                transitionSpec = { fadeIn() togetherWith fadeOut() },
                label = "emptyState",
            ) { active ->
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(
                        stringResource(if (active) R.string.scan_title else R.string.empty_title),
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.SemiBold,
                    )
                    Text(
                        stringResource(if (active) R.string.scan_message else R.string.empty_message),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    if (active) {
                        LinearProgressIndicator(Modifier.fillMaxWidth())
                    } else {
                        Text(
                            stringResource(R.string.empty_root_hint),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Button(onClick = onScan, shape = RoundedCornerShape(18.dp)) { Text(stringResource(R.string.action_scan_wallet)) }
                    }
                }
            }
        }
    }
}

@Composable
private fun RenameDialog(card: CardItem, onDismiss: () -> Unit, onSave: (String) -> Unit) {
    var name by remember(card.id) { mutableStateOf(card.name) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.rename_title)) },
        text = {
            OutlinedTextField(
                value = name,
                onValueChange = { name = it.take(CardLabels.MAX_USER_LENGTH) },
                label = { Text(stringResource(R.string.rename_label)) },
                singleLine = true,
            )
        },
        confirmButton = {
            TextButton(onClick = {
                if (name.isNotBlank()) onSave(name.trim())
                onDismiss()
            }) { Text(stringResource(R.string.action_save)) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) } },
    )
}

@Composable
private fun PreviewDialog(preview: Preview, onDismiss: () -> Unit) {
    Dialog(onDismissRequest = onDismiss) {
        Surface(shape = RoundedCornerShape(28.dp), color = MaterialTheme.colorScheme.surfaceContainerLow) {
            Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                Text(preview.title, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
                Artwork(
                    file = preview.file,
                    revision = preview.revision,
                    placeholder = stringResource(R.string.placeholder_unavailable),
                    description = preview.title,
                    caption = preview.caption,
                    shape = RoundedCornerShape(20.dp),
                    modifier = Modifier.fillMaxWidth().aspectRatio(CropGeometry.CARD_RATIO),
                )
                TextButton(onClick = onDismiss, modifier = Modifier.align(Alignment.End)) { Text(stringResource(R.string.action_close)) }
            }
        }
    }
}
