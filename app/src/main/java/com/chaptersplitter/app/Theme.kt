package com.chaptersplitter.app

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

/*
 * One Material 3 palette for the whole app. The values are the same teal colours used by the
 * Caption tools web page, so native screens and the web screens look like one app.
 */

private val LightColors = lightColorScheme(
    primary = Color(0xFF1F6F5C),
    onPrimary = Color.White,
    primaryContainer = Color(0xFFD8E8DF),
    onPrimaryContainer = Color(0xFF0F2B22),
    secondary = Color(0xFF4A635A),
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFD8E8DF),
    onSecondaryContainer = Color(0xFF0F2B22),
    tertiary = Color(0xFF3F6374),
    onTertiary = Color.White,
    background = Color(0xFFF4F7F4),
    onBackground = Color(0xFF171D1A),
    surface = Color.White,
    onSurface = Color(0xFF171D1A),
    surfaceVariant = Color(0xFFEDF1ED),
    onSurfaceVariant = Color(0xFF4A524E),
    outline = Color(0xFF79827D),
    outlineVariant = Color(0xFFD3DAD5),
    surfaceContainerLowest = Color.White,
    surfaceContainerLow = Color.White,
    surfaceContainer = Color.White,
    surfaceContainerHigh = Color(0xFFEDF1ED),
    surfaceContainerHighest = Color(0xFFE3E9E4),
    error = Color(0xFFBA1A1A),
    onError = Color.White,
    errorContainer = Color(0xFFFFDAD6),
    onErrorContainer = Color(0xFF410002)
)

private val DarkColors = darkColorScheme(
    primary = Color(0xFF8FD5BC),
    onPrimary = Color(0xFF00382B),
    primaryContainer = Color(0xFF2A3A33),
    onPrimaryContainer = Color(0xFFD0E8DD),
    secondary = Color(0xFFB2CCC1),
    onSecondary = Color(0xFF1D352C),
    secondaryContainer = Color(0xFF2A3A33),
    onSecondaryContainer = Color(0xFFD0E8DD),
    tertiary = Color(0xFFA6CCDF),
    onTertiary = Color(0xFF083546),
    background = Color(0xFF0F1512),
    onBackground = Color(0xFFE0E4DF),
    surface = Color(0xFF18201C),
    onSurface = Color(0xFFE0E4DF),
    surfaceVariant = Color(0xFF212B26),
    onSurfaceVariant = Color(0xFFBFC9C3),
    outline = Color(0xFF89938D),
    outlineVariant = Color(0xFF3F4944),
    surfaceContainerLowest = Color(0xFF0A100D),
    surfaceContainerLow = Color(0xFF18201C),
    surfaceContainer = Color(0xFF18201C),
    surfaceContainerHigh = Color(0xFF212B26),
    surfaceContainerHighest = Color(0xFF2A3530),
    error = Color(0xFFFFB4AB),
    onError = Color(0xFF690005),
    errorContainer = Color(0xFF93000A),
    onErrorContainer = Color(0xFFFFDAD6)
)

@Composable
fun AppTheme(dark: Boolean, content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = if (dark) DarkColors else LightColors,
        content = content
    )
}
