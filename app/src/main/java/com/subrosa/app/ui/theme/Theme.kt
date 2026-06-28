package com.subrosa.app.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val DossierColors = lightColorScheme(
    primary = Oxblood,
    onPrimary = Color(0xFFF6EEDF),
    primaryContainer = OxbloodDeep,
    onPrimaryContainer = Color(0xFFF6EEDF),
    secondary = Brass,
    onSecondary = Color(0xFF201913),
    background = Paper,
    onBackground = Ink,
    surface = PaperHigh,
    onSurface = Ink,
    surfaceVariant = PaperEdge,
    onSurfaceVariant = InkSoft,
    outline = Hairline,
    outlineVariant = Hairline,
    error = MissingRed,
    onError = Color(0xFFF6EEDF),
)

@Composable
fun SubRosaTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = DossierColors,
        typography = Typography,
        content = content,
    )
}
