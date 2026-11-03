package io.github.hxfuxyy.zetacard.data

import android.app.Application
import android.graphics.Bitmap
import android.net.Uri
import android.os.Build
import io.github.hxfuxyy.zetacard.BuildConfig
import io.github.hxfuxyy.zetacard.ZetaApplication
import io.github.hxfuxyy.zetacard.core.CardIds
import io.github.hxfuxyy.zetacard.image.ImageProcessor
import io.github.hxfuxyy.zetacard.logging.DiagnosticsLog
import io.github.hxfuxyy.zetacard.root.LegacyMigration
import io.github.hxfuxyy.zetacard.root.RootBridge
import java.io.IOException
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class ArtRepository(private val app: Application) {
    val isWelcomed: Boolean get() = ArtStore.isWelcomed(app)

    fun markWelcomed() = ArtStore.setWelcomed(app)

    fun isLsposedConnected(): Boolean = ZetaApplication.isConnected()

    suspend fun cards(): List<CardItem> = withContext(Dispatchers.IO) {
        ArtStore.ids(app).map { id ->
            val stock = ArtStore.stockFile(app, id)
            val custom = ArtStore.artFile(app, id)
            CardItem(
                id = id,
                name = ArtStore.name(app, id),
                stock = stock,
                custom = custom,
                hasCustom = custom.isFile,
                stockRevision = stock.lastModified(),
                customRevision = custom.lastModified(),
                syncedRevision = ArtStore.syncedRevision(app, id),
            )
        }
    }

    suspend fun importFromWallet(retryPreviews: Boolean): Int = withContext(Dispatchers.IO) {
        if (retryPreviews) RootBridge.retryMissingStock()
        RootBridge.importCards(app)
    }

    suspend fun migrateLegacy(): LegacyMigration.Outcome = withContext(Dispatchers.IO) {
        val outcome = LegacyMigration.run(app)
        if (outcome.copiedNow > 0) ArtStore.ids(app).forEach { ZetaApplication.syncArt(app, it) }
        outcome
    }

    suspend fun checkRoot(): Boolean = withContext(Dispatchers.IO) { RootBridge.checkRoot() }

    suspend fun rename(id: String, name: String) = withContext(Dispatchers.IO) { ArtStore.setName(app, id, name) }

    suspend fun decodePicked(uri: Uri): Bitmap = withContext(Dispatchers.IO) {
        ImageProcessor.decodeForEditing(app.contentResolver, uri)
    }

    suspend fun saveArtwork(id: String, image: Bitmap): Boolean = withContext(Dispatchers.IO) {
        ImageProcessor.writePng(ArtStore.artFile(app, id), image)
        DiagnosticsLog.info(app, "Artwork saved: ${CardIds.shortId(id)}")
        applyToWallet(id)
    }

    suspend fun restore(id: String): Boolean = withContext(Dispatchers.IO) {
        val file = ArtStore.artFile(app, id)
        if (file.exists() && !file.delete()) throw IOException("Could not remove the custom artwork")
        DiagnosticsLog.info(app, "Original artwork restored: ${CardIds.shortId(id)}")
        applyToWallet(id)
    }

    private fun applyToWallet(id: String): Boolean {
        val synced = ZetaApplication.syncArt(app, id)
        if (!synced) {
            DiagnosticsLog.warning(app, "LSPosed service unavailable; artwork will sync when it connects")
            return false
        }
        if (RootBridge.restartWallet(app)) DiagnosticsLog.info(app, "Google Wallet restarted to apply artwork")
        return true
    }

    suspend fun logs(): String = withContext(Dispatchers.IO) {
        "App events\n${DiagnosticsLog.read(app)}\n\nWallet module events\n${walletEvents()}"
    }

    suspend fun exportLogs(uri: Uri) = withContext(Dispatchers.IO) {
        val output = app.contentResolver.openOutputStream(uri, "wt") ?: throw IOException("Could not open the selected file")
        output.bufferedWriter().use { it.write(exportText()) }
        DiagnosticsLog.info(app, "Logs exported")
    }

    fun clearSessionLogs() {
        DiagnosticsLog.clear(app)
        RootBridge.resetLogState()
    }

    fun log(message: String) = DiagnosticsLog.info(app, message)

    fun warn(message: String) = DiagnosticsLog.warning(app, message)

    private fun walletEvents(): String {
        val events = RootBridge.walletEvents(app)
        if (events != null) return events
        return "No Wallet module events yet. Open Google Wallet once with root granted to ZetaCard. " +
            "If the module does not load, ensure 'Umount modules' or DenyList is turned off for Google Wallet in your root manager."
    }

    private fun exportText(): String {
        val timestamp = SimpleDateFormat("yyyy-MM-dd HH:mm:ss Z", Locale.US).format(Date())
        return buildString {
            appendLine("ZetaCard logs")
            appendLine("Exported: $timestamp")
            appendLine("Device: ${Build.MANUFACTURER} ${Build.MODEL}")
            appendLine("Android: ${Build.VERSION.RELEASE} (API ${Build.VERSION.SDK_INT})")
            appendLine("Firmware: ${Build.DISPLAY}")
            appendLine("Build ID: ${Build.ID}")
            appendLine("ZetaCard: ${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE})")
            appendLine("Google Wallet: ${packageVersion(RootBridge.WALLET_PACKAGE)}")
            appendLine("LSPosed service: ${if (isLsposedConnected()) "connected" else "not connected"}")
            appendLine()
            appendLine("App events")
            appendLine(DiagnosticsLog.read(app))
            appendLine()
            appendLine("Wallet module events")
            appendLine(walletEvents())
        }
    }

    private fun packageVersion(packageName: String): String = try {
        val info = app.packageManager.getPackageInfo(packageName, 0)
        "${info.versionName ?: "unknown"} (${info.longVersionCode})"
    } catch (_: Exception) {
        "not installed or unavailable"
    }
}
