package com.securemessenger.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val DarkColors = darkColorScheme(
    primary      = Color(0xFF4A9EFF),
    background   = Color(0xFF0D0D0D),
    surface      = Color(0xFF1A1A1A),
    onPrimary    = Color.White,
    onBackground = Color(0xFFE0E0E0),
)

@Composable
fun SecureMessengerTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = DarkColors, content = content)
}
