package com.chessoffline.app.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val DarkChessColorScheme = darkColorScheme(
    primary = Color(0xFF81B64C),
    onPrimary = Color(0xFF0E1706),
    secondary = Color(0xFF60A5FA),
    onSecondary = Color(0xFF081426),
    background = Color(0xFF14161A),
    onBackground = Color(0xFFF1F5F9),
    surface = Color(0xFF1E2127),
    onSurface = Color(0xFFF1F5F9),
    surfaceVariant = Color(0xFF282C34),
    onSurfaceVariant = Color(0xFF94A3B8),
    outline = Color(0xFF363C47),
    error = Color(0xFFEF4444),
    onError = Color.White
)

@Composable
fun ChessOfflineTheme(
    content: @Composable () -> Unit
) {
    MaterialTheme(
        colorScheme = DarkChessColorScheme,
        content = content
    )
}
