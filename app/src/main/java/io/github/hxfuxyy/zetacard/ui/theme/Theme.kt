package io.github.hxfuxyy.zetacard.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext

private val DarkColors = darkColorScheme(
    primary = Color(0xFFB9C2FF),
    onPrimary = Color(0xFF142064),
    secondary = Color(0xFFC3C7E4),
    tertiary = Color(0xFFE0B6FF),
    background = Color(0xFF0E111C),
    surface = Color(0xFF151927),
    surfaceContainerLow = Color(0xFF1B2030),
    surfaceContainer = Color(0xFF23293A),
)

private val LightColors = lightColorScheme(
    primary = Color(0xFF4546BD),
    secondary = Color(0xFF5D637C),
    tertiary = Color(0xFF8747B4),
    background = Color(0xFFF8F9FD),
    surface = Color(0xFFFFFFFF),
    surfaceContainerLow = Color(0xFFF0F2FA),
)

@Composable
fun ZetaTheme(content: @Composable () -> Unit) {
    val dark = isSystemInDarkTheme()
    val context = LocalContext.current
    val colors = when {
        Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && dark -> dynamicDarkColorScheme(context)
        Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> dynamicLightColorScheme(context)
        dark -> DarkColors
        else -> LightColors
    }
    MaterialTheme(colorScheme = colors, content = content)
}
