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
import io.github.hxfuxyy.zetacard.root.RootShell
import java.io.IOException
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

private const val WALLET_SCAN_TIMEOUT_MS = 7000L
private const val AUTO_SCAN_INTERVAL_MS = 6L * 60 * 60 * 1000

class ArtRepository(private val app: Application) {
    val isWelcomed: Boolean get() = ArtStore.isWelcomed(app)

    fun markWelcomed() = ArtStore.setWelcomed(app)

    fun isLsposedConnected(): Boolean = ZetaApplication.isConnected()

    fun isWalletInScope(): Boolean? = ZetaApplication.isWalletInScope()

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
                captionEnabled = ArtStore.isCaptionEnabled(app, id),
                caption = ArtStore.captionText(app, id),
                syncedCaption = ArtStore.syncedCaption(app, id),
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

    suspend fun scanWallet(): Boolean = withContext(Dispatchers.IO) {
        val started = System.currentTimeMillis()
        val seen = RootBridge.scanWallet(app, WALLET_SCAN_TIMEOUT_MS)
        DiagnosticsLog.info(app, "Wallet scan ${if (seen) "saw cards" else "timed out"} after ${System.currentTimeMillis() - started} ms")
        seen
    }

    suspend fun canScanWallet(): Boolean = withContext(Dispatchers.IO) { RootShell.isAvailable() }

    fun autoScanAllowed(): Boolean = System.currentTimeMillis() - ArtStore.lastAutoScan(app) > AUTO_SCAN_INTERVAL_MS

    fun markAutoScan() = ArtStore.setLastAutoScan(app, System.currentTimeMillis())

    suspend fun checkRoot(): Boolean = withContext(Dispatchers.IO) { RootBridge.checkRoot() }

    suspend fun rename(id: String, name: String): Boolean = withContext(Dispatchers.IO) {
        ArtStore.setName(app, id, name)
        if (ArtStore.isCaptionEnabled(app, id) && ArtStore.artFile(app, id).isFile) applyToWallet(id) else true
    }

    suspend fun setCaption(id: String, enabled: Boolean): Boolean = withContext(Dispatchers.IO) {
        ArtStore.setCaptionEnabled(app, id, enabled)
        if (ArtStore.artFile(app, id).isFile) applyToWallet(id) else true
    }

    suspend fun presetThumbnail(preset: Preset, width: Int): Bitmap = withContext(Dispatchers.IO) {
        ImageProcessor.decodeAsset(app.assets, preset.asset, width)
    }

    suspend fun applyPreset(id: String, preset: Preset): Boolean = withContext(Dispatchers.IO) {
        val image = ImageProcessor.decodeAsset(app.assets, preset.asset)
        ImageProcessor.writePng(ArtStore.artFile(app, id), image)
        DiagnosticsLog.info(app, "Preset ${preset.id} applied: ${CardIds.shortId(id)}")
        applyToWallet(id)
    }

    suspend fun exportBackup(uri: Uri): BackupResult = withContext(Dispatchers.IO) {
        DesignBackup.export(app, uri).also { DiagnosticsLog.info(app, "Backup exported: ${it.designs} design(s)") }
    }

    suspend fun importBackup(uri: Uri): BackupResult = withContext(Dispatchers.IO) {
        val result = DesignBackup.restore(app, uri)
        DiagnosticsLog.info(app, "Backup imported: ${result.cards} card(s), ${result.designs} design(s)")
        var synced = true
        for (id in ArtStore.ids(app)) synced = ZetaApplication.syncArt(app, id) && synced
        if (!synced) DiagnosticsLog.warning(app, "Backup restored locally; LSPosed sync pending")
        if (synced && result.designs > 0) RootBridge.restartWallet(app)
        result.copy(applied = synced)
    }

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

    suspend fun logs(): String = withContext(Dispatchers.IO) { diagnosticsReport() }

    suspend fun exportLogs(uri: Uri) = withContext(Dispatchers.IO) {
        val output = app.contentResolver.openOutputStream(uri, "wt") ?: throw IOException("Could not open the selected file")
        output.bufferedWriter().use { it.write(diagnosticsReport()) }
        DiagnosticsLog.info(app, "Logs exported")
    }

    fun startSession() {
        RootBridge.resetLogState()
    }

    fun log(message: String) = DiagnosticsLog.info(app, message)

    fun warn(message: String) = DiagnosticsLog.warning(app, message)

    fun error(message: String, error: Throwable) = DiagnosticsLog.error(app, message, error)

    private fun walletEvents(): String {
        val events = RootBridge.walletEvents(app)
        if (events != null) return events
        return "No Wallet module events yet. Open Google Wallet once with root granted to ZetaCard. " +
            "If the module does not load, ensure 'Umount modules' or DenyList is turned off for Google Wallet in your root manager."
    }

    private fun diagnosticsReport(): String {
        val timestamp = SimpleDateFormat("yyyy-MM-dd HH:mm:ss Z", Locale.US).format(Date())
        val ids = ArtStore.ids(app)
        return buildString {
            appendLine("ZetaCard diagnostics")
            appendLine("Generated: $timestamp")
            appendLine()
            appendLine("[App]")
            appendLine("Version: ${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE}, ${BuildConfig.BUILD_TYPE})")
            appendLine("Package: ${app.packageName}")
            appendLine(
                "App language: ${AppLanguages.current(app).ifEmpty {
                    "system"
                }}; system locale: ${Locale.getDefault().toLanguageTag()}",
            )
            appendLine()
            appendLine("[Device]")
            appendLine("Model: ${Build.MANUFACTURER} ${Build.MODEL} (${Build.DEVICE})")
            appendLine("Android: ${Build.VERSION.RELEASE} (API ${Build.VERSION.SDK_INT}), security patch ${Build.VERSION.SECURITY_PATCH}")
            appendLine("Firmware: ${Build.DISPLAY}")
            appendLine("Fingerprint: ${Build.FINGERPRINT}")
            appendLine("ABI: ${Build.SUPPORTED_ABIS.firstOrNull() ?: "unknown"}")
            appendLine()
            appendLine("[Framework]")
            appendLine("LSPosed: ${ZetaApplication.frameworkSummary()}")
            appendLine("Google Wallet in module scope: ${ZetaApplication.walletScopeSummary()}")
            appendLine("Root: ${RootBridge.rootVersion() ?: "not available"}")
            appendLine("Google Wallet: ${packageVersion(RootBridge.WALLET_PACKAGE)}")
            appendLine(
                "Old ZetaCard (${LegacyMigration.LEGACY_PACKAGE}): ${if (LegacyMigration.isLegacyInstalled(
                        app,
                    )
                ) {
                    "installed"
                } else {
                    "not installed"
                }}, migration done: ${ArtStore.isLegacyMigrated(app)}",
            )
            appendLine()
            appendLine("[Cards]")
            appendLine("Cards: ${ids.size}, designs: ${ids.count { ArtStore.artFile(app, it).isFile }}")
            for (id in ids) {
                val art = ArtStore.artFile(app, id)
                val state = when {
                    art.isFile && ArtStore.syncedRevision(app, id) == art.lastModified() -> "applied"
                    art.isFile -> "waiting for LSPosed"
                    ArtStore.syncedRevision(app, id) >= 0 -> "restore waiting for LSPosed"
                    else -> "original"
                }
                val preview = if (ArtStore.stockFile(app, id).isFile) "preview ok" else "no preview"
                appendLine("- ${CardIds.shortId(id)}: $state, caption ${if (ArtStore.isCaptionEnabled(app, id)) "on" else "off"}, $preview")
            }
            appendLine()
            appendLine("[Wallet hook cache]")
            appendLine(hookCacheSummary())
            appendLine()
            appendLine("[App events]")
            appendLine(DiagnosticsLog.read(app))
            appendLine()
            appendLine("[Wallet module events]")
            appendLine(walletEvents())
        }
    }

    private fun hookCacheSummary(): String {
        val xml = RootBridge.walletHookCache(app) ?: return "No hook cache yet (Wallet has not been scanned, or root is unavailable)."
        val entries = Regex("""<(long|string) name="([^"]+)"(?: value="([^"]*)")?\s*/?>([^<]*)""").findAll(xml).map { match ->
            val name = match.groupValues[2]
            val value = match.groupValues[3].ifEmpty { match.groupValues[4] }
            if (name == "compose" ||
                name == "legacy"
            ) {
                "$name: ${if (value.isBlank() || value == "NONE") "none" else "${value.split(';').size} entry(ies)"}"
            } else {
                "$name: $value"
            }
        }.toList()
        return if (entries.isEmpty()) "Hook cache present but empty." else entries.joinToString("\n")
    }

    private fun packageVersion(packageName: String): String = try {
        val info = app.packageManager.getPackageInfo(packageName, 0)
        "${info.versionName ?: "unknown"} (${info.longVersionCode})"
    } catch (_: Exception) {
        "not installed or unavailable"
    }
}
