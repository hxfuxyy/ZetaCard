package io.github.hxfuxyy.zetacard

import android.content.ActivityNotFoundException
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
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.ViewModelProvider
import io.github.hxfuxyy.zetacard.root.LegacyMigration
import io.github.hxfuxyy.zetacard.root.RootBridge
import io.github.hxfuxyy.zetacard.ui.MainViewModel
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
    private var pickingId: String? = null

    private val logExport = registerForActivityResult(ActivityResultContracts.CreateDocument("text/plain")) { uri ->
        if (uri != null) viewModel.exportLogs(uri)
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
            onOpenGitHub = ::openGitHub,
            onShowLogs = viewModel::showLogs,
            onExportLogs = ::exportLogs,
            onCloseLogs = viewModel::closeLogs,
            onChoose = ::choosePhoto,
            onRename = viewModel::rename,
            onRestore = viewModel::restore,
            onRemoveLegacy = ::openLegacySettings,
        )
        val versionLabel = "Version ${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE})"
        setContent {
            ZetaTheme { AppContent(viewModel, actions, versionLabel) }
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

    private fun exportLogs() {
        val timestamp = SimpleDateFormat("yyyyMMdd-HHmmss", Locale.US).format(Date())
        logExport.launch("ZetaCard-logs-$timestamp.txt")
    }

    private fun openWallet() {
        val launch = packageManager.getLaunchIntentForPackage(RootBridge.WALLET_PACKAGE)
        if (launch == null) viewModel.reportOpenFailure("Google Wallet") else startSafely(launch, "Google Wallet")
    }

    private fun openGitHub() {
        startSafely(Intent(Intent.ACTION_VIEW, Uri.parse(REPOSITORY_URL)), "A web browser")
    }

    private fun openLegacySettings() {
        val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.fromParts("package", LegacyMigration.LEGACY_PACKAGE, null))
        startSafely(intent, "App settings")
    }

    private fun startSafely(intent: Intent, target: String) {
        try {
            startActivity(intent)
        } catch (_: ActivityNotFoundException) {
            viewModel.reportOpenFailure(target)
        }
    }

    private companion object {
        const val STATE_PICKING_ID = "picking_id"
        const val REPOSITORY_URL = "https://github.com/hxfuxyy/ZetaCard"
    }
}

@Composable
private fun AppContent(viewModel: MainViewModel, actions: CardsActions, versionLabel: String) {
    val state by viewModel.state.collectAsState()
    val context = LocalContext.current
    LaunchedEffect(viewModel) {
        viewModel.messages.collect { Toast.makeText(context, it, Toast.LENGTH_SHORT).show() }
    }
    if (state.showWelcome) {
        WelcomeScreen(access = state.access, onCheck = viewModel::checkAccess, onDone = viewModel::finishWelcome)
        return
    }
    CardsScreen(state = state, versionLabel = versionLabel, actions = actions)
    state.crop?.let { request ->
        CropEditor(request = request, onCancel = viewModel::cancelCrop, onSave = viewModel::applyCrop)
    }
}
