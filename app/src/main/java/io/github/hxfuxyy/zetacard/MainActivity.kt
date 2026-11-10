package io.github.hxfuxyy.zetacard

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalResources
import androidx.lifecycle.ViewModelProvider
import io.github.hxfuxyy.zetacard.data.AppLanguages
import io.github.hxfuxyy.zetacard.root.LegacyMigration
import io.github.hxfuxyy.zetacard.root.RootBridge
import io.github.hxfuxyy.zetacard.ui.MainViewModel
import io.github.hxfuxyy.zetacard.ui.MessageTone
import io.github.hxfuxyy.zetacard.ui.components.rememberHaptics
import io.github.hxfuxyy.zetacard.ui.screens.CardsActions
import io.github.hxfuxyy.zetacard.ui.screens.CardsScreen
import io.github.hxfuxyy.zetacard.ui.screens.CropEditor
import io.github.hxfuxyy.zetacard.ui.screens.WelcomeScreen
import io.github.hxfuxyy.zetacard.ui.theme.ZetaTheme
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class MainActivity : ComponentActivity() {
    private lateinit var viewModel: MainViewModel

    override fun attachBaseContext(newBase: Context) {
        super.attachBaseContext(AppLanguages.wrap(newBase))
    }
    private var pickingId: String? = null

    private val logExport = registerForActivityResult(ActivityResultContracts.CreateDocument("text/plain")) { uri ->
        if (uri != null) viewModel.exportLogs(uri)
    }

    private val backupExport = registerForActivityResult(ActivityResultContracts.CreateDocument("application/zip")) { uri ->
        if (uri != null) viewModel.exportBackup(uri)
    }

    private val backupImport = registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) viewModel.importBackup(uri)
    }

    private val photoPicker = registerForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        val id = pickingId
        pickingId = null
        if (uri != null && id != null) viewModel.onPhotoPicked(id, uri)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        pickingId = savedInstanceState?.getString(STATE_PICKING_ID)
        viewModel = ViewModelProvider(this)[MainViewModel::class.java]
        if (savedInstanceState == null) viewModel.onFirstLaunch()
        val actions = CardsActions(
            onRefresh = viewModel::refresh,
            onOpenWallet = ::openWallet,
            onShowLogs = viewModel::showLogs,
            onExportLogs = ::exportLogs,
            onCloseLogs = viewModel::closeLogs,
            onChoosePhoto = ::choosePhoto,
            onPreset = viewModel::applyPreset,
            onCaption = viewModel::setCaption,
            onRename = viewModel::rename,
            onRestore = viewModel::restore,
            onRemoveLegacy = ::openLegacySettings,
            onExportBackup = ::exportBackup,
            onImportBackup = { backupImport.launch(BACKUP_TYPES) },
            onLanguage = ::selectLanguage,
            onOpenUrl = { url -> startSafely(Intent(Intent.ACTION_VIEW, Uri.parse(url)), R.string.message_browser_unavailable) },
            loadThumbnail = viewModel::presetThumbnail,
        )
        setContent {
            ZetaTheme { AppContent(viewModel, actions, AppLanguages.current(this)) }
        }
    }

    override fun onResume() {
        super.onResume()
        viewModel.onResume()
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        pickingId?.let { outState.putString(STATE_PICKING_ID, it) }
    }

    private fun choosePhoto(id: String) {
        pickingId = id
        photoPicker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
    }

    private fun selectLanguage(tag: String) {
        viewModel.onLanguageChanged(tag)
        AppLanguages.select(this, tag)
    }

    private fun exportBackup() {
        val timestamp = SimpleDateFormat("yyyyMMdd-HHmmss", Locale.US).format(Date())
        backupExport.launch("ZetaCard-backup-$timestamp.zip")
    }

    private fun exportLogs() {
        val timestamp = SimpleDateFormat("yyyyMMdd-HHmmss", Locale.US).format(Date())
        logExport.launch("ZetaCard-logs-$timestamp.txt")
    }

    private fun openWallet() {
        val launch = packageManager.getLaunchIntentForPackage(RootBridge.WALLET_PACKAGE)
        if (launch ==
            null
        ) {
            viewModel.reportOpenFailure(R.string.message_wallet_unavailable)
        } else {
            startSafely(launch, R.string.message_wallet_unavailable)
        }
    }

    private fun openLegacySettings() {
        val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.fromParts("package", LegacyMigration.LEGACY_PACKAGE, null))
        startSafely(intent, R.string.message_settings_unavailable)
    }

    private fun startSafely(intent: Intent, failure: Int) {
        try {
            startActivity(intent)
        } catch (_: ActivityNotFoundException) {
            viewModel.reportOpenFailure(failure)
        }
    }

    private companion object {
        const val STATE_PICKING_ID = "picking_id"
        val BACKUP_TYPES = arrayOf("application/zip", "application/x-zip-compressed", "application/octet-stream")
    }
}

@Composable
private fun AppContent(viewModel: MainViewModel, actions: CardsActions, languageTag: String) {
    val state by viewModel.state.collectAsState()
    val context = LocalContext.current
    val resources by rememberUpdatedState(LocalResources.current)
    val haptics = rememberHaptics()
    LaunchedEffect(viewModel) {
        var toast: Toast? = null
        viewModel.messages.collect { message ->
            when (message.tone) {
                MessageTone.SUCCESS -> haptics.confirm()
                MessageTone.ERROR -> haptics.reject()
                MessageTone.INFO -> Unit
            }
            toast?.cancel()
            toast = Toast.makeText(context, message.text.resolve(resources), Toast.LENGTH_SHORT).also { it.show() }
        }
    }
    AnimatedContent(
        targetState = state.showWelcome,
        transitionSpec = { fadeIn(tween(420)) togetherWith fadeOut(tween(260)) },
        label = "root",
    ) { welcome ->
        if (welcome) {
            WelcomeScreen(access = state.access, onCheck = viewModel::checkAccess, onDone = viewModel::finishWelcome)
        } else {
            CardsScreen(
                state = state,
                versionName = BuildConfig.VERSION_NAME,
                versionCode = BuildConfig.VERSION_CODE,
                languageTag = languageTag,
                actions = actions,
            )
        }
    }
    state.crop?.let { request ->
        CropEditor(request = request, onCancel = viewModel::cancelCrop, onSave = viewModel::applyCrop)
    }
}
