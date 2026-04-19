package com.securemessenger.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val DarkColors = darkColorScheme(
    primary             = Color(0xFFCC2222),
    onPrimary           = Color(0xFFFFFFFF),
    primaryContainer    = Color(0xFF7A0000),
    onPrimaryContainer  = Color(0xFFFFDAD4),
    secondary           = Color(0xFF8B1A1A),
    onSecondary         = Color(0xFFFFFFFF),
    secondaryContainer  = Color(0xFF5C0000),
    onSecondaryContainer = Color(0xFFFFB4A8),
    background          = Color(0xFF0D0D0D),
    onBackground        = Color(0xFFE0E0E0),
    surface             = Color(0xFF1A1A1A),
    onSurface           = Color(0xFFE0E0E0),
    surfaceVariant      = Color(0xFF2A1A1A),
    onSurfaceVariant    = Color(0xFFBBADAD),
    error               = Color(0xFFFF4444),
    onError             = Color(0xFF690000),
    errorContainer      = Color(0xFF93000A),
    onErrorContainer    = Color(0xFFFFDAD6),
    outline             = Color(0xFF5C4040),
)

@Composable
fun SecureMessengerTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = DarkColors, content = content)
}
