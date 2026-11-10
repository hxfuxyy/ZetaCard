package io.github.hxfuxyy.zetacard.ui

import android.app.Application
import android.graphics.Bitmap
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import io.github.hxfuxyy.zetacard.R
import io.github.hxfuxyy.zetacard.core.CropGeometry
import io.github.hxfuxyy.zetacard.core.ImageAdjustments
import io.github.hxfuxyy.zetacard.core.ImageLimits
import io.github.hxfuxyy.zetacard.core.InvalidBackupException
import io.github.hxfuxyy.zetacard.core.LogSanitizer
import io.github.hxfuxyy.zetacard.core.Pan
import io.github.hxfuxyy.zetacard.core.PixelSize
import io.github.hxfuxyy.zetacard.data.ArtRepository
import io.github.hxfuxyy.zetacard.data.CardItem
import io.github.hxfuxyy.zetacard.data.LsposedConnection
import io.github.hxfuxyy.zetacard.data.Preset
import io.github.hxfuxyy.zetacard.image.ImageProcessor
import io.github.hxfuxyy.zetacard.image.ImageTooLargeException
import io.github.hxfuxyy.zetacard.root.LegacyMigration
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

enum class PhotoFit { CROP, STRETCH }

data class CropRequest(val id: String, val image: Bitmap) {
    val size: PixelSize get() = PixelSize(image.width, image.height)
}

data class AccessState(
    val root: Boolean? = null,
    val lsposed: Boolean? = null,
    val scope: Boolean? = null,
    val checking: Boolean = false,
) {
    val ready: Boolean get() = root == true && lsposed == true && scope == true
}

data class UiState(
    val showWelcome: Boolean = false,
    val cards: List<CardItem> = emptyList(),
    val loaded: Boolean = false,
    val refreshing: Boolean = false,
    val busy: Boolean = false,
    val crop: CropRequest? = null,
    val logs: String? = null,
    val access: AccessState = AccessState(),
    val legacy: LegacyNotice? = null,
    val scanning: Boolean = false,
)

data class LegacyNotice(val complete: Boolean, val expected: Int, val present: Int)

private fun LegacyMigration.Outcome.toNotice(): LegacyNotice? = when (state) {
    LegacyMigration.State.NOT_NEEDED, null -> null
    LegacyMigration.State.COMPLETE -> LegacyNotice(complete = true, expected = expected, present = present)
    else -> LegacyNotice(complete = false, expected = expected, present = present)
}

private const val PRESET_THUMBNAIL_WIDTH = 720
private const val MIN_REFRESH_MILLIS = 700L

class MainViewModel(application: Application) : AndroidViewModel(application) {
    private val repository = ArtRepository(application)
    private val mutableState = MutableStateFlow(UiState(showWelcome = !repository.isWelcomed))
    private val messageChannel = Channel<UiMessage>(Channel.BUFFERED)
    private val thumbnails = HashMap<String, Bitmap>()
    private var importJob: Job? = null
    private var autoScanned = false
    private var refreshQueued = false

    val state: StateFlow<UiState> = mutableState.asStateFlow()
    val messages: Flow<UiMessage> = messageChannel.receiveAsFlow()

    init {
        viewModelScope.launch {
            LsposedConnection.connected.collect { connected ->
                val scope = if (connected) repository.isWalletInScope() else false
                mutableState.update { it.copy(access = it.access.copy(lsposed = connected, scope = scope)) }
            }
        }
        viewModelScope.launch {
            LsposedConnection.syncRevision.collect { revision -> if (revision > 0) reloadCards() }
        }
    }

    fun onFirstLaunch() {
        repository.startSession()
        repository.log("App opened, LSPosed ${if (repository.isLsposedConnected()) "connected" else "not connected yet"}")
    }

    fun onResume() {
        if (mutableState.value.showWelcome) checkAccess() else importFromWallet(userInitiated = false)
    }

    fun refresh() = importFromWallet(userInitiated = true)

    private fun importFromWallet(userInitiated: Boolean) {
        if (userInitiated) mutableState.update { it.copy(refreshing = true) }
        if (importJob?.isActive == true) {
            if (userInitiated) refreshQueued = true
            return
        }
        importJob = viewModelScope.launch {
            val started = System.currentTimeMillis()
            try {
                val migration = repository.migrateLegacy()
                if (migration.copiedNow > 0) notifyPlural(R.plurals.message_designs_migrated, migration.copiedNow)
                reloadCards()
                mutableState.update { it.copy(legacy = migration.toNotice()) }
                var added = repository.importFromWallet(retryPreviews = userInitiated)
                reloadCards()
                val autoScan = !userInitiated && !autoScanned && mutableState.value.cards.isEmpty() && repository.autoScanAllowed()
                if ((autoScan || (userInitiated && added == 0)) && repository.canScanWallet()) {
                    autoScanned = true
                    if (autoScan) repository.markAutoScan()
                    mutableState.update { it.copy(scanning = true) }
                    try {
                        repository.scanWallet()
                        added += repository.importFromWallet(retryPreviews = true)
                        reloadCards()
                    } finally {
                        mutableState.update { it.copy(scanning = false) }
                    }
                }
                if (userInitiated) repository.log("Manual refresh: $added new card(s), ${mutableState.value.cards.size} total")
                when {
                    added > 0 -> notifyPlural(R.plurals.message_cards_found, added)
                    userInitiated -> messageChannel.trySend(UiMessage(UiText.Res(R.string.message_no_new_cards)))
                }
            } catch (error: Exception) {
                if (error is CancellationException) throw error
                repository.error("Card import failed", error)
            } finally {
                val elapsed = System.currentTimeMillis() - started
                if (userInitiated && elapsed < MIN_REFRESH_MILLIS) delay(MIN_REFRESH_MILLIS - elapsed)
                if (!refreshQueued) mutableState.update { it.copy(refreshing = false) }
            }
        }
        importJob?.invokeOnCompletion {
            if (refreshQueued) {
                refreshQueued = false
                viewModelScope.launch { importFromWallet(userInitiated = true) }
            }
        }
    }

    private suspend fun reloadCards() {
        val cards = repository.cards()
        mutableState.update { it.copy(cards = cards, loaded = true) }
    }

    fun checkAccess() {
        if (mutableState.value.access.checking) return
        mutableState.update { it.copy(access = it.access.copy(checking = true)) }
        viewModelScope.launch {
            val root = repository.checkRoot()
            val lsposed = repository.isLsposedConnected()
            val scope = if (lsposed) repository.isWalletInScope() else false
            val scopeText = when (scope) {
                true -> "yes"
                false -> "no"
                null -> "unknown"
            }
            repository.log(
                "Setup check: root ${if (root) "granted" else "missing"}, LSPosed ${if (lsposed) "active" else "missing"}, Wallet scope $scopeText",
            )
            mutableState.update { it.copy(access = AccessState(root, lsposed, scope, checking = false)) }
        }
    }

    fun finishWelcome() {
        if (!mutableState.value.access.ready) return
        repository.markWelcomed()
        repository.log("Setup completed")
        mutableState.update { it.copy(showWelcome = false) }
        importFromWallet(userInitiated = false)
    }

    fun rename(id: String, name: String) = runBusy("Rename", R.string.message_save_failed) {
        if (!repository.rename(id, name)) notify(R.string.message_pending_sync)
    }

    fun setCaption(id: String, enabled: Boolean) = runBusy("Caption ${if (enabled) "on" else "off"}", R.string.message_save_failed) {
        if (!repository.setCaption(id, enabled)) notify(R.string.message_pending_sync)
    }

    fun applyPreset(id: String, preset: Preset) = runBusy("Preset ${preset.id}", R.string.message_save_failed) {
        notify(if (repository.applyPreset(id, preset)) R.string.message_artwork_applied else R.string.message_pending_sync)
    }

    fun exportBackup(uri: Uri) = if (mutableState.value.busy) {
        Unit
    } else {
        runBusy("Backup export", R.string.message_backup_export_failed) {
            val result = repository.exportBackup(uri)
            notifyPlural(R.plurals.message_backup_exported, result.designs)
        }
    }

    fun importBackup(uri: Uri) = if (mutableState.value.busy) {
        Unit
    } else {
        runBusy("Backup import", R.string.message_backup_import_failed) {
            try {
                val result = repository.importBackup(uri)
                if (result.applied || result.designs == 0) {
                    notifyPlural(R.plurals.message_backup_imported, result.designs)
                } else {
                    notify(R.string.message_pending_sync)
                }
            } catch (error: InvalidBackupException) {
                repository.warn("Backup rejected: ${LogSanitizer.describe(error)}")
                notify(R.string.message_backup_invalid)
            }
        }
    }

    suspend fun presetThumbnail(preset: Preset): Bitmap? {
        thumbnails[preset.id]?.let { return it }
        return runCatching { repository.presetThumbnail(preset, PRESET_THUMBNAIL_WIDTH) }.getOrNull()?.also { thumbnails[preset.id] = it }
    }

    fun onPhotoPicked(id: String, uri: Uri) {
        mutableState.update { it.copy(busy = true) }
        viewModelScope.launch {
            try {
                val image = repository.decodePicked(uri)
                repository.log("Photo opened for editing: ${image.width}x${image.height}")
                mutableState.update { it.copy(crop = CropRequest(id, image)) }
            } catch (error: ImageTooLargeException) {
                repository.warn("Photo rejected: too large")
                notify(R.string.message_image_too_large)
            } catch (error: Exception) {
                if (error is CancellationException) throw error
                repository.error("Image open failed", error)
                notify(R.string.message_image_open_failed)
            } finally {
                mutableState.update { it.copy(busy = false) }
            }
        }
    }

    fun cancelCrop() = mutableState.update { it.copy(crop = null) }

    fun applyCrop(fit: PhotoFit, zoom: Float, pan: Pan, viewport: PixelSize, adjustments: ImageAdjustments) {
        val request = mutableState.value.crop ?: return
        mutableState.update { it.copy(crop = null) }
        runBusy(
            "Photo ${fit.name.lowercase()} zoom=${"%.2f".format(
                java.util.Locale.US,
                zoom,
            )} adjust=${adjustments.brightness}/${adjustments.shade}",
            R.string.message_save_failed,
        ) {
            val result = withContext(Dispatchers.Default) {
                if (fit == PhotoFit.CROP) {
                    val output = PixelSize(ImageLimits.OUTPUT_WIDTH, ImageLimits.OUTPUT_HEIGHT)
                    ImageProcessor.renderCrop(
                        request.image,
                        CropGeometry.cropTransform(request.size, viewport, zoom, pan, output),
                        adjustments,
                    )
                } else {
                    ImageProcessor.renderStretch(request.image, adjustments)
                }
            }
            val applied = repository.saveArtwork(request.id, result)
            notify(if (applied) R.string.message_artwork_applied else R.string.message_pending_sync)
        }
    }

    fun restore(id: String) = runBusy("Restore original", R.string.message_restore_failed) {
        notify(if (repository.restore(id)) R.string.message_restored else R.string.message_pending_sync)
    }

    fun showLogs() {
        viewModelScope.launch {
            val text = repository.logs()
            mutableState.update { it.copy(logs = text) }
        }
    }

    fun closeLogs() = mutableState.update { it.copy(logs = null) }

    fun exportLogs(uri: Uri) = runBusy("Log export", R.string.message_logs_export_failed) {
        repository.exportLogs(uri)
        notify(R.string.message_logs_exported)
    }

    fun onLanguageChanged(tag: String) = repository.log("Language changed to ${tag.ifEmpty { "system" }}")

    fun reportOpenFailure(message: Int) = notify(message)

    private fun runBusy(action: String, failure: Int, block: suspend () -> Unit) {
        mutableState.update { it.copy(busy = true) }
        viewModelScope.launch {
            try {
                block()
                repository.log("$action done")
                reloadCards()
            } catch (error: Exception) {
                if (error is CancellationException) throw error
                repository.error("$action failed", error)
                notify(failure)
            } finally {
                mutableState.update { it.copy(busy = false) }
            }
        }
    }

    private fun notify(message: Int) {
        messageChannel.trySend(UiMessage(UiText.Res(message), tone = toneOf(message)))
    }

    private fun notifyPlural(message: Int, count: Int) {
        messageChannel.trySend(UiMessage(UiText.Plural(message, count), tone = MessageTone.SUCCESS))
    }

    private fun toneOf(message: Int): MessageTone = when (message) {
        R.string.message_artwork_applied, R.string.message_restored, R.string.message_logs_exported -> MessageTone.SUCCESS
        R.string.message_pending_sync -> MessageTone.INFO
        else -> MessageTone.ERROR
    }
}
