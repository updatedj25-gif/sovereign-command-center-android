package com.sovereign.commandcenter.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable

private val SovereignColorScheme = lightColorScheme(
    primary = SovereignAmber,
    onPrimary = SovereignCream,
    primaryContainer = SovereignAmberSurface,
    onPrimaryContainer = SovereignAmberDark,
    background = SovereignCream,
    onBackground = SovereignStone950,
    surface = SovereignCreamDarker,
    onSurface = SovereignStone900,
    surfaceVariant = SovereignStone200,
    outline = SovereignAmber.copy(alpha = 0.35f)
)

@Composable
fun SovereignTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = SovereignColorScheme,
        typography = Typography,
        content = content
    )
}
