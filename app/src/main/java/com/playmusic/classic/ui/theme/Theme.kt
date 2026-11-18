package com.playmusic.classic.ui.theme

import android.app.Activity
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat

val PlayMusicOrange = Color(0xFFFF5722)
val PlayMusicOrangeDark = Color(0xFFE64A19)
val PlayMusicBackground = Color(0xFFFAFAFA)
val PlayMusicSurface = Color(0xFFFFFFFF)
val PlayMusicTextPrimary = Color(0xFF212121)
val PlayMusicTextSecondary = Color(0xFF757575)
val PlayMusicDrawerHeaderBg = Color(0xFF263238)

private val ClassicPlayMusicColorScheme = lightColorScheme(
    primary = PlayMusicOrange,
    onPrimary = Color.White,
    primaryContainer = PlayMusicOrangeDark,
    onPrimaryContainer = Color.White,
    secondary = PlayMusicOrange,
    onSecondary = Color.White,
    background = PlayMusicBackground,
    onBackground = PlayMusicTextPrimary,
    surface = PlayMusicSurface,
    onSurface = PlayMusicTextPrimary,
    surfaceVariant = Color(0xFFEEEEEE),
    onSurfaceVariant = PlayMusicTextSecondary
)

@Composable
fun PlayMusicClassicTheme(content: @Composable () -> Unit) {
    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as Activity).window
            window.statusBarColor = PlayMusicOrangeDark.toArgb()
            WindowCompat.getInsetsController(window, view).isAppearanceLightStatusBars = false
        }
    }
    MaterialTheme(
        colorScheme = ClassicPlayMusicColorScheme,
        content = content
    )
}
