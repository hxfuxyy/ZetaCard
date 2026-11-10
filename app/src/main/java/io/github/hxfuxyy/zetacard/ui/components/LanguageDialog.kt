package io.github.hxfuxyy.zetacard.ui.components

import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import io.github.hxfuxyy.zetacard.R
import io.github.hxfuxyy.zetacard.data.AppLanguage
import io.github.hxfuxyy.zetacard.data.AppLanguages

@Composable
fun LanguageDialog(selected: String, onSelect: (String) -> Unit, onDismiss: () -> Unit) {
    val options = listOf(AppLanguage("", stringResource(R.string.language_system))) + AppLanguages.all
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.language_title)) },
        text = {
            LazyColumn(Modifier.heightIn(max = 420.dp)) {
                items(options, key = { it.tag.ifEmpty { "system" } }) { language ->
                    val checked = if (language.tag.isEmpty()) selected.isEmpty() else AppLanguages.matches(selected, language.tag)
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(14.dp))
                            .bouncyClickable(pressedScale = 0.98f) { onSelect(language.tag) }
                            .padding(vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        RadioButton(selected = checked, onClick = { onSelect(language.tag) })
                        Text(language.label, style = MaterialTheme.typography.bodyLarge)
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_close)) } },
    )
}
