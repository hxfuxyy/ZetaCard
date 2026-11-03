package io.github.hxfuxyy.zetacard.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import io.github.hxfuxyy.zetacard.ui.AccessState
import io.github.hxfuxyy.zetacard.ui.components.ZetaHero

private data class Requirement(val title: String, val detail: String, val ok: Boolean?, val okLabel: String, val missingLabel: String)

@Composable
fun WelcomeScreen(access: AccessState, onCheck: () -> Unit, onDone: () -> Unit) {
    LaunchedEffect(Unit) { onCheck() }
    val requirements = listOf(
        Requirement(
            title = "Root access",
            detail = "Allow ZetaCard in KernelSU, Magisk or your root manager. It reads your card list from Google Wallet.",
            ok = access.root,
            okLabel = "Granted",
            missingLabel = "Not granted",
        ),
        Requirement(
            title = "LSPosed module",
            detail = "Enable ZetaCard in LSPosed or Vector with Google Wallet in its scope. This shows your artwork inside Wallet.",
            ok = access.lsposed,
            okLabel = "Active",
            missingLabel = "Not active",
        ),
    )
    Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        Column(Modifier.fillMaxSize().safeDrawingPadding().padding(horizontal = 24.dp, vertical = 16.dp)) {
            Column(
                Modifier.weight(1f).verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Spacer(Modifier.height(8.dp))
                ZetaHero()
                Text(
                    "Welcome to ZetaCard",
                    modifier = Modifier.fillMaxWidth(),
                    style = MaterialTheme.typography.headlineMedium,
                    fontWeight = FontWeight.Bold,
                    textAlign = TextAlign.Center,
                )
                Text(
                    "Custom artwork for your Google Wallet cards. Set up these two things first.",
                    modifier = Modifier.fillMaxWidth(),
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                )
                Spacer(Modifier.height(8.dp))
                Surface(shape = RoundedCornerShape(24.dp), color = MaterialTheme.colorScheme.surfaceContainerLow) {
                    Column {
                        requirements.forEachIndexed { index, requirement ->
                            if (index >
                                0
                            ) {
                                HorizontalDivider(Modifier.padding(start = 72.dp), color = MaterialTheme.colorScheme.outlineVariant)
                            }
                            RequirementRow(requirement)
                        }
                    }
                }
            }
            Spacer(Modifier.height(16.dp))
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedButton(
                    onClick = onCheck,
                    enabled = !access.checking,
                    modifier = Modifier.fillMaxWidth().height(52.dp),
                    shape = RoundedCornerShape(16.dp),
                ) {
                    Text(if (access.checking) "Checking…" else "Check again")
                }
                Button(onClick = onDone, modifier = Modifier.fillMaxWidth().height(52.dp), shape = RoundedCornerShape(16.dp)) {
                    Text(if (access.ready) "Continue" else "Continue without setup")
                }
            }
        }
    }
}

@Composable
private fun RequirementRow(requirement: Requirement) {
    val colors = MaterialTheme.colorScheme
    val accent = when (requirement.ok) {
        true -> colors.primary
        false -> colors.error
        null -> colors.onSurfaceVariant
    }
    Row(
        Modifier.fillMaxWidth().padding(16.dp),
        verticalAlignment = Alignment.Top,
        horizontalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Box(
            Modifier.size(40.dp).clip(CircleShape).background(
                when (requirement.ok) {
                    true -> colors.primaryContainer
                    false -> colors.errorContainer
                    null -> colors.surfaceContainerHighest
                },
            ),
            contentAlignment = Alignment.Center,
        ) {
            when (requirement.ok) {
                true -> Icon(
                    Icons.Outlined.Check,
                    contentDescription = null,
                    tint = colors.onPrimaryContainer,
                    modifier = Modifier.size(22.dp),
                )
                false -> Icon(
                    Icons.Outlined.Close,
                    contentDescription = null,
                    tint = colors.onErrorContainer,
                    modifier = Modifier.size(22.dp),
                )
                null -> CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp)
            }
        }
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(requirement.title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            Text(
                when (requirement.ok) {
                    true -> requirement.okLabel
                    false -> requirement.missingLabel
                    null -> "Checking…"
                },
                style = MaterialTheme.typography.labelLarge,
                color = accent,
            )
            Spacer(Modifier.height(4.dp))
            Text(requirement.detail, style = MaterialTheme.typography.bodyMedium, color = colors.onSurfaceVariant)
        }
    }
}
