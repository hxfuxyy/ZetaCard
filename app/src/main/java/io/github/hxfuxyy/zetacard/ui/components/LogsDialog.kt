package io.github.hxfuxyy.zetacard.ui.components

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import io.github.hxfuxyy.zetacard.R

@Composable
fun LogsDialog(logs: String, onExport: () -> Unit, onClose: () -> Unit) {
    val context = LocalContext.current
    val clipLabel = stringResource(R.string.logs_title)
    AlertDialog(
        onDismissRequest = onClose,
        title = { Text(stringResource(R.string.logs_title)) },
        text = {
            SelectionContainer {
                Text(
                    logs,
                    modifier = Modifier.heightIn(max = 420.dp).verticalScroll(rememberScrollState()),
                    style = MaterialTheme.typography.bodySmall,
                    fontFamily = FontFamily.Monospace,
                )
            }
        },
        confirmButton = {
            Row {
                TextButton(onClick = onExport) { Text(stringResource(R.string.action_export)) }
                TextButton(onClick = {
                    val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
                    clipboard?.setPrimaryClip(ClipData.newPlainText(clipLabel, logs))
                    onClose()
                }) { Text(stringResource(R.string.action_copy)) }
            }
        },
        dismissButton = { TextButton(onClick = onClose) { Text(stringResource(R.string.action_close)) } },
    )
}
