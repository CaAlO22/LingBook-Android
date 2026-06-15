package com.lingji.app.ui.theme

import android.app.Activity
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat

private val LightColors = lightColorScheme(
    primary = Primary,
    onPrimary = OnPrimary,
    primaryContainer = PrimaryContainer,
    onPrimaryContainer = Stone800,
    secondary = Stone700,
    onSecondary = Color.White,
    secondaryContainer = Stone100,
    onSecondaryContainer = Stone800,
    tertiary = Purple600,
    onTertiary = Color.White,
    tertiaryContainer = Purple50,
    onTertiaryContainer = Purple700,
    background = Paper,
    onBackground = Ink,
    surface = Color.White,
    onSurface = Ink,
    surfaceVariant = Stone100,
    onSurfaceVariant = Stone600,
    surfaceTint = Primary,
    outline = Stone200,
    outlineVariant = Stone100,
    error = ErrorDark,
    onError = Color.White,
    errorContainer = ErrorLight,
    onErrorContainer = ErrorDark
)

private val DarkColors = darkColorScheme(
    primary = Stone100,
    onPrimary = Stone800,
    primaryContainer = Stone700,
    onPrimaryContainer = Stone100,
    secondary = Stone300,
    onSecondary = Stone800,
    secondaryContainer = Stone700,
    onSecondaryContainer = Stone100,
    tertiary = Purple100,
    onTertiary = Purple700,
    tertiaryContainer = Purple700,
    onTertiaryContainer = Purple50,
    background = Stone800,
    onBackground = Paper,
    surface = Stone700,
    onSurface = Paper,
    surfaceVariant = Stone700,
    onSurfaceVariant = Stone300,
    surfaceTint = Stone100,
    outline = Stone500,
    outlineVariant = Stone600,
    error = ErrorLight,
    onError = ErrorDark,
    errorContainer = ErrorDark,
    onErrorContainer = ErrorLight
)

@Composable
fun LingjiTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit
) {
    val colorScheme = if (darkTheme) DarkColors else LightColors
    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as Activity).window
            window.statusBarColor = colorScheme.background.toArgb()
            window.navigationBarColor = colorScheme.background.toArgb()
            WindowCompat.getInsetsController(window, view).isAppearanceLightStatusBars = !darkTheme
        }
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = LingjiTypography,
        shapes = LingjiShapes,
        content = content
    )
}
