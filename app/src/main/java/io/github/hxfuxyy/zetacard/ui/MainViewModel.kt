package io.github.hxfuxyy.zetacard.ui

import android.app.Application
import android.graphics.Bitmap
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import io.github.hxfuxyy.zetacard.core.CropGeometry
import io.github.hxfuxyy.zetacard.core.ImageLimits
import io.github.hxfuxyy.zetacard.core.LogSanitizer
import io.github.hxfuxyy.zetacard.core.Pan
import io.github.hxfuxyy.zetacard.core.PixelSize
import io.github.hxfuxyy.zetacard.data.ArtRepository
import io.github.hxfuxyy.zetacard.data.CardItem
import io.github.hxfuxyy.zetacard.image.ImageProcessor
import io.github.hxfuxyy.zetacard.image.ImageTooLargeException
import io.github.hxfuxyy.zetacard.root.LegacyMigration
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
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

data class AccessState(val root: Boolean? = null, val lsposed: Boolean? = null, val checking: Boolean = false) {
    val ready: Boolean get() = root == true && lsposed == true
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
)

data class LegacyNotice(val complete: Boolean, val expected: Int, val present: Int)

private fun LegacyMigration.Outcome.toNotice(): LegacyNotice? = when (state) {
    LegacyMigration.State.NOT_NEEDED, null -> null
    LegacyMigration.State.COMPLETE -> LegacyNotice(complete = true, expected = expected, present = present)
    else -> LegacyNotice(complete = false, expected = expected, present = present)
}

private const val PENDING_SYNC = "Saved. It applies after the LSPosed module connects and Google Wallet is reopened"

class MainViewModel(application: Application) : AndroidViewModel(application) {
    private val repository = ArtRepository(application)
    private val mutableState = MutableStateFlow(UiState(showWelcome = !repository.isWelcomed))
    private val messageChannel = Channel<String>(Channel.BUFFERED)
    private var importJob: Job? = null

    val state: StateFlow<UiState> = mutableState.asStateFlow()
    val messages: Flow<String> = messageChannel.receiveAsFlow()

    fun onFirstLaunch() {
        repository.clearSessionLogs()
        repository.log("App opened")
        if (repository.isLsposedConnected()) repository.log("LSPosed data service connected")
    }

    fun onResume() {
        if (!mutableState.value.showWelcome) importFromWallet(userInitiated = false)
    }

    fun refresh() = importFromWallet(userInitiated = true)

    private fun importFromWallet(userInitiated: Boolean) {
        if (userInitiated) mutableState.update { it.copy(refreshing = true) }
        if (importJob?.isActive == true) return
        importJob = viewModelScope.launch {
            try {
                val migration = repository.migrateLegacy()
                if (migration.copiedNow > 0) notify("Copied ${migration.copiedNow} design(s) from the previous ZetaCard")
                reloadCards()
                mutableState.update { it.copy(legacy = migration.toNotice()) }
                val added = repository.importFromWallet(retryPreviews = userInitiated)
                reloadCards()
                if (added > 0) notify("Found $added card(s)")
            } catch (error: Exception) {
                if (error is CancellationException) throw error
                repository.warn("Card import failed: ${LogSanitizer.describe(error)}")
            } finally {
                mutableState.update { it.copy(refreshing = false) }
            }
        }
    }

    private suspend fun reloadCards() {
        val cards = repository.cards()
        mutableState.update { it.copy(cards = cards, loaded = true) }
    }

    fun checkAccess() {
        if (mutableState.value.access.checking) return
        mutableState.update { it.copy(access = AccessState(root = null, lsposed = repository.isLsposedConnected(), checking = true)) }
        viewModelScope.launch {
            val root = repository.checkRoot()
            mutableState.update { it.copy(access = AccessState(root, repository.isLsposedConnected(), checking = false)) }
        }
    }

    fun finishWelcome() {
        repository.markWelcomed()
        mutableState.update { it.copy(showWelcome = false) }
        importFromWallet(userInitiated = false)
    }

    fun rename(id: String, name: String) {
        viewModelScope.launch {
            repository.rename(id, name)
            reloadCards()
        }
    }

    fun onPhotoPicked(id: String, uri: Uri) {
        mutableState.update { it.copy(busy = true) }
        viewModelScope.launch {
            try {
                val image = repository.decodePicked(uri)
                mutableState.update { it.copy(crop = CropRequest(id, image)) }
            } catch (error: ImageTooLargeException) {
                notify(error.message ?: "This image is too large")
            } catch (error: Exception) {
                if (error is CancellationException) throw error
                repository.warn("Image open failed: ${LogSanitizer.describe(error)}")
                notify("Could not open this image")
            } finally {
                mutableState.update { it.copy(busy = false) }
            }
        }
    }

    fun cancelCrop() = mutableState.update { it.copy(crop = null) }

    fun applyCrop(fit: PhotoFit, zoom: Float, pan: Pan, viewport: PixelSize) {
        val request = mutableState.value.crop ?: return
        mutableState.update { it.copy(crop = null, busy = true) }
        viewModelScope.launch {
            try {
                val result = withContext(Dispatchers.Default) {
                    if (fit == PhotoFit.CROP) {
                        val output = PixelSize(ImageLimits.OUTPUT_WIDTH, ImageLimits.OUTPUT_HEIGHT)
                        ImageProcessor.renderCrop(request.image, CropGeometry.cropTransform(request.size, viewport, zoom, pan, output))
                    } else {
                        ImageProcessor.renderStretch(request.image)
                    }
                }
                val applied = repository.saveArtwork(request.id, result)
                reloadCards()
                notify(if (applied) "Artwork saved and applied" else PENDING_SYNC)
            } catch (error: Exception) {
                if (error is CancellationException) throw error
                repository.warn("Artwork save failed: ${LogSanitizer.describe(error)}")
                notify("Could not save artwork")
            } finally {
                mutableState.update { it.copy(busy = false) }
            }
        }
    }

    fun restore(id: String) {
        mutableState.update { it.copy(busy = true) }
        viewModelScope.launch {
            try {
                val applied = repository.restore(id)
                reloadCards()
                notify(if (applied) "Original artwork restored" else PENDING_SYNC)
            } catch (error: Exception) {
                if (error is CancellationException) throw error
                repository.warn("Restore failed: ${LogSanitizer.describe(error)}")
                notify("Could not restore the original artwork")
            } finally {
                mutableState.update { it.copy(busy = false) }
            }
        }
    }

    fun showLogs() {
        viewModelScope.launch {
            val text = repository.logs()
            mutableState.update { it.copy(logs = text) }
        }
    }

    fun closeLogs() = mutableState.update { it.copy(logs = null) }

    fun exportLogs(uri: Uri) {
        viewModelScope.launch {
            try {
                repository.exportLogs(uri)
                notify("Logs exported")
            } catch (error: Exception) {
                if (error is CancellationException) throw error
                repository.warn("Log export failed: ${LogSanitizer.describe(error)}")
                notify("Could not export logs")
            }
        }
    }

    fun reportOpenFailure(target: String) = notify("$target is not available on this device")

    private fun notify(message: String) {
        messageChannel.trySend(message)
    }
}
