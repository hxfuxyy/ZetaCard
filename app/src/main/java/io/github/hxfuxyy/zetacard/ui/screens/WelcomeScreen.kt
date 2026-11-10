package io.github.hxfuxyy.zetacard.ui.screens

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.togetherWith
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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import io.github.hxfuxyy.zetacard.R
import io.github.hxfuxyy.zetacard.ui.AccessState
import io.github.hxfuxyy.zetacard.ui.components.Appear
import io.github.hxfuxyy.zetacard.ui.components.ZetaHero
import io.github.hxfuxyy.zetacard.ui.components.rememberHaptics
import kotlinx.coroutines.delay

private data class Requirement(val title: Int, val detail: Int, val ok: Boolean?, val okLabel: Int, val missingLabel: Int)

@Composable
fun WelcomeScreen(access: AccessState, onCheck: () -> Unit, onDone: () -> Unit) {
    LaunchedEffect(Unit) { onCheck() }
    val haptics = rememberHaptics()
    var wasReady by remember { mutableStateOf(access.ready) }
    LaunchedEffect(access.ready) {
        if (access.ready && !wasReady) haptics.confirm()
        wasReady = access.ready
    }
    val requirements = listOf(
        Requirement(
            R.string.welcome_root_title,
            R.string.welcome_root_detail,
            access.root,
            R.string.status_granted,
            R.string.status_not_granted,
        ),
        Requirement(
            R.string.welcome_lsposed_title,
            R.string.welcome_lsposed_detail,
            if (access.checking && access.lsposed != true) null else access.lsposed,
            R.string.status_module_active,
            R.string.status_module_inactive,
        ),
        Requirement(
            R.string.welcome_scope_title,
            R.string.welcome_scope_detail,
            when {
                access.checking && access.scope != true -> null
                access.lsposed == false -> false
                else -> access.scope
            },
            R.string.status_scope_added,
            R.string.status_scope_missing,
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
                Appear(delayMillis = 450) {
                    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Text(
                            stringResource(R.string.welcome_title),
                            modifier = Modifier.fillMaxWidth(),
                            style = MaterialTheme.typography.headlineMedium,
                            fontWeight = FontWeight.Bold,
                            textAlign = TextAlign.Center,
                        )
                        Text(
                            stringResource(R.string.welcome_subtitle),
                            modifier = Modifier.fillMaxWidth(),
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            textAlign = TextAlign.Center,
                        )
                    }
                }
                Spacer(Modifier.height(8.dp))
                Appear(delayMillis = 650) {
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
            }
            Spacer(Modifier.height(16.dp))
            Appear(delayMillis = 800) {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    OutlinedButton(
                        onClick = onCheck,
                        enabled = !access.checking,
                        modifier = Modifier.fillMaxWidth().height(52.dp),
                        shape = RoundedCornerShape(18.dp),
                    ) {
                        Text(stringResource(if (access.checking) R.string.status_checking else R.string.action_check_again))
                    }
                    AnimatedVisibility(
                        visible = !access.ready && !access.checking,
                        enter = fadeIn() + expandVertically(),
                        exit = fadeOut() + shrinkVertically(),
                    ) {
                        Text(
                            stringResource(R.string.welcome_required),
                            modifier = Modifier.fillMaxWidth(),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            textAlign = TextAlign.Center,
                        )
                    }
                    Button(
                        onClick = onDone,
                        enabled = access.ready,
                        modifier = Modifier.fillMaxWidth().height(52.dp),
                        shape = RoundedCornerShape(18.dp),
                    ) {
                        Text(stringResource(R.string.action_continue))
                    }
                }
            }
        }
    }
}

@Composable
private fun RequirementRow(requirement: Requirement) {
    val colors = MaterialTheme.colorScheme
    val accent by animateColorAsState(
        when (requirement.ok) {
            true -> colors.primary
            false -> colors.error
            null -> colors.onSurfaceVariant
        },
        label = "requirementAccent",
    )
    val badge by animateColorAsState(
        when (requirement.ok) {
            true -> colors.primaryContainer
            false -> colors.errorContainer
            null -> colors.surfaceContainerHighest
        },
        label = "requirementBadge",
    )
    Row(
        Modifier.fillMaxWidth().padding(16.dp),
        verticalAlignment = Alignment.Top,
        horizontalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Box(Modifier.size(40.dp).clip(CircleShape).background(badge), contentAlignment = Alignment.Center) {
            AnimatedContent(
                targetState = requirement.ok,
                transitionSpec = { (fadeIn(tween(220)) + scaleIn(tween(260), initialScale = 0.6f)) togetherWith fadeOut(tween(120)) },
                label = "requirementIcon",
            ) { ok ->
                when (ok) {
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
        }
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(stringResource(requirement.title), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            Text(
                stringResource(
                    when (requirement.ok) {
                        true -> requirement.okLabel
                        false -> requirement.missingLabel
                        null -> R.string.status_checking
                    },
                ),
                style = MaterialTheme.typography.labelLarge,
                color = accent,
            )
            Spacer(Modifier.height(4.dp))
            Text(stringResource(requirement.detail), style = MaterialTheme.typography.bodyMedium, color = colors.onSurfaceVariant)
        }
    }
}
