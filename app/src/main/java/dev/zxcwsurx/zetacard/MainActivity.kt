package dev.zxcwsurx.zetacard

import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas as AndroidCanvas
import android.graphics.Paint
import android.net.Uri
import android.os.Bundle
import android.os.Build
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTransformGestures
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.AddPhotoAlternate
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.Crop
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.Image
import androidx.compose.material.icons.outlined.Wallet
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.material3.pulltorefresh.PullToRefreshDefaults
import androidx.compose.material3.pulltorefresh.rememberPullToRefreshState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.concurrent.thread
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

private const val CARD_RATIO = 700f / 440f
private val MASKED_CARD_SUFFIX = Regex("""^(.+?)\s+([•●·*]{1,4}\s*\d{4})$""")
private data class CardItem(val id: String, val name: String, val stock: File, val custom: File)
private data class CropRequest(val id: String, val image: Bitmap)
private enum class PhotoFit { CROP, STRETCH }

class MainActivity : ComponentActivity() {
    private var refresh by mutableIntStateOf(0)
    private var diagnostics by mutableStateOf<String?>(null)
    private var cropRequest by mutableStateOf<CropRequest?>(null)
    private var pickingId: String? = null

    private val logExport = registerForActivityResult(ActivityResultContracts.CreateDocument("text/plain")) { uri ->
        if (uri == null) return@registerForActivityResult
        try {
            contentResolver.openOutputStream(uri, "wt")?.bufferedWriter()?.use { it.write(exportText()) }
                ?: error("Could not open the selected file")
            DiagnosticsLog.info(this, "Logs exported")
            toast("Logs exported")
        } catch (error: Exception) {
            DiagnosticsLog.warning(this, "Log export failed: ${error.javaClass.simpleName}")
            toast(error.message ?: "Could not export logs")
        }
    }

    private val photoPicker = registerForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        val id = pickingId
        pickingId = null
        if (uri == null || id == null) return@registerForActivityResult
        thread(name = "ZetaCard image reader") {
            try {
                val image = decodePicked(uri)
                runOnUiThread { cropRequest = CropRequest(id, image) }
            } catch (error: Exception) {
                DiagnosticsLog.warning(this, "Image open failed: ${error.javaClass.simpleName}: ${error.message}")
                runOnUiThread { toast(error.message ?: "Could not open image") }
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (savedInstanceState == null) DiagnosticsLog.clear(this)
        DiagnosticsLog.info(this, "App opened")
        if (ZetaApplication.isConnected()) DiagnosticsLog.info(this, "LSPosed data service connected")
        grantWalletAccess()
        setContent {
            ZetaTheme {
                AppScreen(
                    refresh = refresh,
                    onRefresh = { refresh++ },
                    onOpenWallet = ::openWallet,
                    onOpenGitHub = ::openGitHub,
                    onDetectHooks = ::detectHooks,
                    onShowLogs = ::showLogs,
                    onExportLogs = ::exportLogs,
                    logs = diagnostics,
                    onCloseLogs = { diagnostics = null },
                    onChoose = { id ->
                        pickingId = id
                        photoPicker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
                    },
                    onRename = ::rename,
                    onRestore = ::restore,
                )
                cropRequest?.let { request ->
                    CropEditor(request,
                        onCancel = { cropRequest = null },
                        onSave = { fit, zoom, pan, viewport ->
                            cropRequest = null
                            thread(name = "ZetaCard crop writer") {
                                try {
                                    val result = if (fit == PhotoFit.CROP) cropBitmap(request.image, zoom, pan, viewport)
                                        else stretchBitmap(request.image)
                                    saveImage(request.id, result)
                                    ZetaApplication.syncArt(application as ZetaApplication, request.id)
                                    DiagnosticsLog.info(this, "Artwork saved: ${request.id.take(8)}")
                                    result.recycle()
                                    request.image.recycle()
                                    runOnUiThread { refresh++; toast("Artwork saved") }
                                } catch (error: Exception) {
                                    DiagnosticsLog.warning(this, "Artwork save failed: ${error.javaClass.simpleName}: ${error.message}")
                                    runOnUiThread { toast(error.message ?: "Could not save artwork") }
                                }
                            }
                        },
                    )
                }
            }
        }
    }

    override fun onResume() { super.onResume(); grantWalletAccess(); refresh++ }

    private fun toast(message: String) = Toast.makeText(this, message, Toast.LENGTH_SHORT).show()

    private fun grantWalletAccess() {
        try {
            grantUriPermission("com.google.android.apps.walletnfcrel", ArtProvider.URI,
                Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_PREFIX_URI_PERMISSION)
        } catch (_: IllegalArgumentException) { }
    }

    private fun openWallet() {
        val launch = packageManager.getLaunchIntentForPackage("com.google.android.apps.walletnfcrel")
        if (launch != null) startActivity(launch) else toast("Google Wallet is not installed")
    }

    private fun openGitHub() {
        startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://github.com/hxfuxyy/ZetaCard")))
    }

    private fun detectHooks() {
        thread(name = "ZetaCard hook reset") {
            val result = (application as ZetaApplication).requestHookScan()
            runOnUiThread {
                toast(result)
                if (result.startsWith("Hook scan requested")) openWallet()
            }
        }
    }

    private fun showLogs() {
        diagnostics = "Logs remain while ZetaCard is open or in the background. Closing and reopening it starts a new log. For Wallet hook events, export LSPosed logs.\n\n${DiagnosticsLog.read(this)}"
    }

    private fun exportLogs() {
        val timestamp = SimpleDateFormat("yyyyMMdd-HHmmss", Locale.US).format(Date())
        logExport.launch("ZetaCard-logs-$timestamp.txt")
    }

    private fun exportText(): String {
        val timestamp = SimpleDateFormat("yyyy-MM-dd HH:mm:ss Z", Locale.US).format(Date())
        fun version(packageName: String): String = try {
            packageManager.getPackageInfo(packageName, 0).versionName ?: "Unknown"
        } catch (_: Exception) { "Not installed or unavailable" }
        return """ZetaCard diagnostics
Exported: $timestamp
Device: ${Build.MANUFACTURER} ${Build.MODEL}
Android: ${Build.VERSION.RELEASE} (API ${Build.VERSION.SDK_INT})
Firmware: ${Build.DISPLAY}
Build ID: ${Build.ID}
ZetaCard: ${version(packageName)}
Google Wallet: ${version("com.google.android.apps.walletnfcrel")}

App events
${DiagnosticsLog.read(this)}

Wallet hook events are available in LSPosed logs.
"""
    }

    private fun rename(id: String, name: String) {
        ArtProvider.prefs(this).edit().putString("name.$id", name).apply()
        refresh++
    }

    private fun restore(id: String) {
        ArtProvider.file(this, id).delete()
        DiagnosticsLog.info(this, "Original artwork restored: ${id.take(8)}")
        thread(name = "ZetaCard artwork remove") { ZetaApplication.syncArt(application as ZetaApplication, id) }
        refresh++
        toast("Original artwork restored")
    }

    private fun decodePicked(uri: Uri): Bitmap {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        contentResolver.openInputStream(uri).use { BitmapFactory.decodeStream(it, null, bounds) }
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0 ||
            bounds.outWidth.toLong() * bounds.outHeight > 60_000_000L) error("Image is too large")
        val sample = max(1, max(bounds.outWidth, bounds.outHeight) / 3000)
        val options = BitmapFactory.Options().apply { inSampleSize = sample }
        return contentResolver.openInputStream(uri).use { BitmapFactory.decodeStream(it, null, options) }
            ?: error("Could not read image")
    }

    private fun saveImage(id: String, image: Bitmap) {
        val target = ArtProvider.file(this, id)
        target.parentFile?.mkdirs()
        val temp = File(target.parentFile, "$id.tmp")
        FileOutputStream(temp).use { if (!image.compress(Bitmap.CompressFormat.PNG, 95, it)) error("Could not save image") }
        if (!temp.renameTo(target)) error("Could not save image")
    }
}

@Composable
private fun ZetaTheme(content: @Composable () -> Unit) {
    val colors = if (androidx.compose.foundation.isSystemInDarkTheme()) darkColorScheme(
        primary = Color(0xFFB9C2FF), onPrimary = Color(0xFF142064),
        secondary = Color(0xFFC3C7E4), tertiary = Color(0xFFE0B6FF),
        background = Color(0xFF0E111C), surface = Color(0xFF151927),
        surfaceContainerLow = Color(0xFF1B2030), surfaceContainer = Color(0xFF23293A),
    ) else lightColorScheme(
        primary = Color(0xFF4546BD), secondary = Color(0xFF5D637C),
        tertiary = Color(0xFF8747B4), background = Color(0xFFF8F9FD),
        surface = Color(0xFFFFFFFF), surfaceContainerLow = Color(0xFFF0F2FA),
    )
    MaterialTheme(colorScheme = colors, content = content)
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun AppScreen(
    refresh: Int,
    onRefresh: () -> Unit,
    onOpenWallet: () -> Unit,
    onOpenGitHub: () -> Unit,
    onDetectHooks: () -> Unit,
    onShowLogs: () -> Unit,
    onExportLogs: () -> Unit,
    logs: String?,
    onCloseLogs: () -> Unit,
    onChoose: (String) -> Unit,
    onRename: (String, String) -> Unit,
    onRestore: (String) -> Unit,
) {
    val context = LocalContext.current
    val cards = remember(refresh) {
        ArtProvider.ids(context).map { id ->
            CardItem(id, ArtProvider.prefs(context).getString("name.$id", "Payment card") ?: "Payment card",
                ArtProvider.stockFile(context, id), ArtProvider.file(context, id))
        }
    }
    var renaming by remember { mutableStateOf<CardItem?>(null) }
    var fullPreview by remember { mutableStateOf<Pair<String, File>?>(null) }
    val listState = rememberLazyListState()
    var isRefreshing by remember { mutableStateOf(false) }
    val pullState = rememberPullToRefreshState()
    val refreshScope = rememberCoroutineScope()

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            TopAppBar(
                title = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(painterResource(R.drawable.zeta_toolbar_mark),
                            contentDescription = null, modifier = Modifier.size(40.dp),
                            tint = MaterialTheme.colorScheme.onSurface)
                        Spacer(Modifier.width(11.dp))
                        Text("ZetaCard", style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold)
                    }
                },
                actions = {
                    IconButton(onClick = onOpenWallet) { Icon(Icons.Outlined.Wallet, contentDescription = "Open Wallet") }
                    IconButton(onClick = onOpenGitHub) {
                        Icon(painterResource(R.drawable.ic_github), contentDescription = "Open GitHub")
                    }
                },
            )
        },
    ) { inner ->
        PullToRefreshBox(
            isRefreshing = isRefreshing,
            onRefresh = {
                if (!isRefreshing) {
                    isRefreshing = true
                    onRefresh()
                    refreshScope.launch {
                        delay(900)
                        isRefreshing = false
                    }
                }
            },
            state = pullState,
            modifier = Modifier.fillMaxSize().padding(inner),
            indicator = {
                PullToRefreshDefaults.LoadingIndicator(
                    state = pullState,
                    isRefreshing = isRefreshing,
                    modifier = Modifier.align(Alignment.TopCenter),
                    containerColor = MaterialTheme.colorScheme.primaryContainer,
                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                )
            },
        ) {
          LazyColumn(
            state = listState,
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 16.dp, bottom = 36.dp),
            verticalArrangement = Arrangement.spacedBy(18.dp),
        ) {
            item {
                Column(verticalArrangement = Arrangement.spacedBy(7.dp)) {
                    Text("Your cards", style = MaterialTheme.typography.headlineLarge, fontWeight = FontWeight.Bold)
                    Text("Give every card a look that feels like yours.",
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            item {
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    FilledTonalButton(onClick = onDetectHooks) { Text("Detect hooks") }
                    OutlinedButton(onClick = onShowLogs) { Text("View logs") }
                }
            }
            if (cards.isEmpty()) item { EmptyState(onOpenWallet) }
            items(cards, key = { it.id }) { card ->
                CardEditor(card, refresh, onChoose = { onChoose(card.id) },
                    onRename = { renaming = card },
                    onRestore = { onRestore(card.id) }, onPreview = { title, file -> fullPreview = title to file })
            }
            item {
                Column(modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text("ZetaCard · v${context.packageManager.getPackageInfo(context.packageName, 0).versionName}",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text("Artwork stays on this device. Your payment details remain in Wallet.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
          }
        }
    }

    renaming?.let { card ->
        var name by remember(card.id) { mutableStateOf(card.name) }
        AlertDialog(
            onDismissRequest = { renaming = null },
            title = { Text("Card name") },
            text = { OutlinedTextField(name, { name = it.take(48) }, label = { Text("Name") }, singleLine = true) },
            confirmButton = { TextButton(onClick = {
                if (name.isNotBlank()) onRename(card.id, name.trim())
                renaming = null
            }) { Text("Save") } },
            dismissButton = { TextButton(onClick = { renaming = null }) { Text("Cancel") } },
        )
    }
    fullPreview?.let { (title, file) ->
        Dialog(onDismissRequest = { fullPreview = null }) {
            Surface(shape = RoundedCornerShape(24.dp), color = MaterialTheme.colorScheme.surface) {
                Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                    Text(title, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
                    Artwork(file, refresh, modifier = Modifier.fillMaxWidth().aspectRatio(CARD_RATIO), placeholder = "Preview unavailable")
                    TextButton(onClick = { fullPreview = null }, modifier = Modifier.align(Alignment.End)) { Text("Close") }
                }
            }
        }
    }
    if (logs != null) {
        AlertDialog(
            onDismissRequest = onCloseLogs,
            title = { Text("Diagnostics") },
            text = { Text(logs, modifier = Modifier.height(380.dp).verticalScroll(rememberScrollState()),
                style = MaterialTheme.typography.bodySmall) },
            confirmButton = {
                Row {
                    TextButton(onClick = onExportLogs) { Text("Export") }
                    TextButton(onClick = {
                        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                        clipboard.setPrimaryClip(ClipData.newPlainText("ZetaCard logs", logs))
                        onCloseLogs()
                    }) { Text("Copy") }
                }
            },
            dismissButton = { TextButton(onClick = onCloseLogs) { Text("Close") } },
        )
    }
}

@Composable
private fun EmptyState(onOpenWallet: () -> Unit) {
    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
        shape = RoundedCornerShape(24.dp)) {
        Column(Modifier.padding(22.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Icon(Icons.Outlined.Wallet, contentDescription = null, modifier = Modifier.size(34.dp),
                tint = MaterialTheme.colorScheme.primary)
            Text("No cards found yet", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
            Text("Enable ZetaCard for Google Wallet in LSPosed, then open Wallet once.",
                style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Button(onClick = onOpenWallet) { Text("Open Google Wallet") }
        }
    }
}

@Composable
private fun CardEditor(
    card: CardItem,
    refresh: Int,
    onChoose: () -> Unit,
    onRename: () -> Unit,
    onRestore: () -> Unit,
    onPreview: (String, File) -> Unit,
) {
    val custom = card.custom.isFile
    val label = remember(card.name) { MASKED_CARD_SUFFIX.matchEntire(card.name) }
    val title = label?.groupValues?.get(1) ?: card.name
    val suffix = label?.groupValues?.get(2)
    Card(shape = RoundedCornerShape(26.dp),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            Row(verticalAlignment = Alignment.Top) {
                Column(Modifier.weight(1f)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(title, modifier = Modifier.weight(1f),
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        if (suffix != null) {
                            Spacer(Modifier.width(8.dp))
                            Text(suffix, style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.SemiBold)
                        }
                    }
                    Row(verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        if (custom) Icon(Icons.Outlined.CheckCircle,
                            contentDescription = "Custom artwork active",
                            modifier = Modifier.size(16.dp), tint = MaterialTheme.colorScheme.tertiary)
                        Text(if (custom) "Custom artwork active" else "Original artwork",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
                IconButton(onClick = onRename) { Icon(Icons.Outlined.Edit, contentDescription = "Rename card") }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                ArtworkTile("Original", card.stock, refresh, Modifier.weight(1f),
                    onClick = { onPreview("Original artwork", card.stock) })
                ArtworkTile("Your design", card.custom, refresh, Modifier.weight(1f),
                    onClick = { onPreview("Your design", card.custom) })
            }
            Button(onClick = onChoose, modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(16.dp)) {
                Icon(Icons.Outlined.AddPhotoAlternate, contentDescription = null, modifier = Modifier.size(19.dp))
                Spacer(Modifier.width(8.dp))
                Text(if (custom) "Change photo" else "Choose photo")
            }
            if (custom) OutlinedButton(onClick = onRestore, modifier = Modifier.fillMaxWidth()) {
                Text("Restore original")
            }
        }
    }
}

@Composable
private fun ArtworkTile(label: String, file: File, refresh: Int, modifier: Modifier, onClick: () -> Unit) {
    Column(modifier, verticalArrangement = Arrangement.spacedBy(7.dp)) {
        Artwork(file, refresh, Modifier.fillMaxWidth().aspectRatio(CARD_RATIO).clickable(onClick = onClick),
            placeholder = if (label == "Original") "Open Wallet to load" else "Add your artwork")
        Text(label, style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun Artwork(file: File, refresh: Int, modifier: Modifier, placeholder: String) {
    val bitmap by produceState<Bitmap?>(initialValue = null, file.path, file.lastModified(), refresh) {
        value = if (file.isFile) withContext(Dispatchers.IO) { BitmapFactory.decodeFile(file.path) } else null
    }
    Box(modifier.clip(RoundedCornerShape(14.dp))
        .background(MaterialTheme.colorScheme.surfaceContainer)
        .border(1.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(14.dp)),
        contentAlignment = Alignment.Center) {
        if (bitmap != null) {
            Image(bitmap!!.asImageBitmap(), contentDescription = placeholder,
                modifier = Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
        } else {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Icon(Icons.Outlined.Image, contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant)
                Spacer(Modifier.height(4.dp))
                Text(placeholder, style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

@Composable
private fun CropEditor(request: CropRequest, onCancel: () -> Unit,
    onSave: (PhotoFit, Float, Offset, IntSize) -> Unit) {
    var fit by remember(request.id) { mutableStateOf(PhotoFit.CROP) }
    var zoom by remember(request.id) { mutableFloatStateOf(1f) }
    var pan by remember(request.id) { mutableStateOf(Offset.Zero) }
    var viewport by remember(request.id) { mutableStateOf(IntSize.Zero) }
    val fillZoom = coverZoom(request.image, viewport)
    val zoomLimit = max(8f, fillZoom * 2f)
    Dialog(onDismissRequest = onCancel, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
            Column(Modifier.fillMaxSize().padding(20.dp), verticalArrangement = Arrangement.spacedBy(18.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconButton(onClick = onCancel) { Icon(Icons.Outlined.Close, contentDescription = "Cancel") }
                    Text("Fit your photo", modifier = Modifier.weight(1f),
                        style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
                }
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    if (fit == PhotoFit.CROP) FilledTonalButton(onClick = {}, modifier = Modifier.weight(1f)) {
                        Text("Crop")
                    } else OutlinedButton(onClick = { fit = PhotoFit.CROP }, modifier = Modifier.weight(1f)) {
                        Text("Crop")
                    }
                    if (fit == PhotoFit.STRETCH) FilledTonalButton(onClick = {}, modifier = Modifier.weight(1f)) {
                        Text("Stretch")
                    } else OutlinedButton(onClick = { fit = PhotoFit.STRETCH }, modifier = Modifier.weight(1f)) {
                        Text("Stretch")
                    }
                }
                Text(if (fit == PhotoFit.CROP)
                    "Move and zoom your photo inside the card frame."
                    else "Use the whole photo. It will stretch to the card shape.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
                Spacer(Modifier.weight(1f))
                Box((if (fit == PhotoFit.CROP) Modifier.fillMaxWidth().height(320.dp)
                    else Modifier.fillMaxWidth().aspectRatio(CARD_RATIO))
                    .clip(RoundedCornerShape(18.dp))
                    .background(MaterialTheme.colorScheme.surfaceContainer)
                    .onSizeChanged {
                        viewport = it
                        pan = constrainedPan(pan, request.image, it, zoom)
                    }
                    .then(if (fit == PhotoFit.CROP) Modifier.pointerInput(request, viewport) {
                        detectTransformGestures { _, drag, gestureZoom, _ ->
                            val nextZoom = (zoom * gestureZoom).coerceIn(1f, zoomLimit)
                            zoom = nextZoom
                            pan = constrainedPan(pan + drag, request.image, viewport, nextZoom)
                        }
                    } else Modifier)) {
                    if (fit == PhotoFit.CROP) {
                        Image(request.image.asImageBitmap(), contentDescription = "Full photo under the card crop frame",
                            modifier = Modifier.fillMaxSize().graphicsLayer {
                                scaleX = zoom
                                scaleY = zoom
                                translationX = pan.x
                                translationY = pan.y
                            }, contentScale = ContentScale.Fit)
                        val frameColor = MaterialTheme.colorScheme.primary
                        Canvas(Modifier.fillMaxSize()) {
                            val frame = cropFrame(IntSize(size.width.roundToInt(), size.height.roundToInt()))
                            val left = (size.width - frame.width) / 2f
                            val top = (size.height - frame.height) / 2f
                            val right = left + frame.width
                            val bottom = top + frame.height
                            val shade = Color.Black.copy(alpha = 0.58f)
                            drawRect(shade, size = Size(size.width, top))
                            drawRect(shade, topLeft = Offset(0f, bottom), size = Size(size.width, size.height - bottom))
                            drawRect(shade, topLeft = Offset(0f, top), size = Size(left, frame.height.toFloat()))
                            drawRect(shade, topLeft = Offset(right, top), size = Size(size.width - right, frame.height.toFloat()))
                            drawRoundRect(frameColor, topLeft = Offset(left, top),
                                size = Size(frame.width.toFloat(), frame.height.toFloat()),
                                cornerRadius = CornerRadius(16.dp.toPx()), style = Stroke(2.dp.toPx()))
                        }
                    } else {
                        Image(request.image.asImageBitmap(), contentDescription = "Full photo stretched to card shape",
                            modifier = Modifier.fillMaxSize(), contentScale = ContentScale.FillBounds)
                    }
                }
                Spacer(Modifier.weight(1f))
                if (fit == PhotoFit.CROP) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Outlined.Crop, contentDescription = null)
                        Slider(value = zoom, onValueChange = {
                            zoom = it
                            pan = constrainedPan(pan, request.image, viewport, zoom)
                        }, valueRange = 1f..zoomLimit, modifier = Modifier.weight(1f).padding(horizontal = 10.dp))
                        Text(String.format(java.util.Locale.US, "%.1fx", zoom), style = MaterialTheme.typography.labelLarge)
                    }
                    FilledTonalButton(onClick = {
                        zoom = fillZoom
                        pan = Offset.Zero
                    }, modifier = Modifier.fillMaxWidth()) { Text("Fill card frame") }
                }
                Button(onClick = { onSave(fit, zoom, pan, viewport) },
                    enabled = fit == PhotoFit.STRETCH || viewport.width > 0 && zoom + 0.001f >= fillZoom,
                    modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(16.dp)) {
                    Text(if (fit == PhotoFit.CROP) "Apply crop" else "Use stretched photo")
                }
            }
        }
    }
}

private fun cropFrame(viewport: IntSize): IntSize {
    if (viewport.width <= 0 || viewport.height <= 0) return IntSize.Zero
    val width = min(viewport.width * 0.90f, viewport.height * 0.75f * CARD_RATIO)
    return IntSize(width.roundToInt(), (width / CARD_RATIO).roundToInt())
}

private fun coverZoom(bitmap: Bitmap, viewport: IntSize): Float {
    if (viewport.width <= 0 || viewport.height <= 0) return 1f
    val frame = cropFrame(viewport)
    val fit = min(viewport.width.toFloat() / bitmap.width, viewport.height.toFloat() / bitmap.height)
    return max(1f, max(frame.width / (bitmap.width * fit), frame.height / (bitmap.height * fit)))
}

private fun constrainedPan(value: Offset, bitmap: Bitmap, viewport: IntSize, zoom: Float): Offset {
    if (viewport.width <= 0 || viewport.height <= 0) return Offset.Zero
    val frame = cropFrame(viewport)
    val scale = min(viewport.width.toFloat() / bitmap.width, viewport.height.toFloat() / bitmap.height) * zoom
    val maxX = max(0f, (bitmap.width * scale - frame.width) / 2f)
    val maxY = max(0f, (bitmap.height * scale - frame.height) / 2f)
    return Offset(value.x.coerceIn(-maxX, maxX), value.y.coerceIn(-maxY, maxY))
}

private fun cropBitmap(source: Bitmap, zoom: Float, pan: Offset, viewport: IntSize): Bitmap {
    require(viewport.width > 0 && viewport.height > 0)
    require(zoom + 0.001f >= coverZoom(source, viewport))
    val frame = cropFrame(viewport)
    val scale = min(viewport.width.toFloat() / source.width, viewport.height.toFloat() / source.height) * zoom
    val sourceWidth = frame.width / scale
    val sourceHeight = frame.height / scale
    val centerX = source.width / 2f - pan.x / scale
    val centerY = source.height / 2f - pan.y / scale
    val result = Bitmap.createBitmap(1400, 880, Bitmap.Config.ARGB_8888)
    val outputScaleX = 1400f / sourceWidth
    val outputScaleY = 880f / sourceHeight
    AndroidCanvas(result).apply {
        translate(700f - centerX * outputScaleX, 440f - centerY * outputScaleY)
        scale(outputScaleX, outputScaleY)
        drawBitmap(source, 0f, 0f, Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG))
    }
    return result
}

private fun stretchBitmap(source: Bitmap): Bitmap {
    val result = Bitmap.createBitmap(1400, 880, Bitmap.Config.ARGB_8888)
    AndroidCanvas(result).drawBitmap(source, null, android.graphics.RectF(0f, 0f, 1400f, 880f),
        Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG))
    return result
}
