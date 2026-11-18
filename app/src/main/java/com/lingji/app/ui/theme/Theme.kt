package com.lingji.app.ui.theme

import android.app.Activity
import android.os.Build
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
    tertiary = Teal600,
    onTertiary = Color.White,
    tertiaryContainer = Teal50,
    onTertiaryContainer = Teal700,
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
    tertiary = Teal100,
    onTertiary = Teal700,
    tertiaryContainer = Teal700,
    onTertiaryContainer = Teal50,
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
            // 信息岛：底部导航栏保持透明，让手势小白条悬浮在页面内容之上，
            // 而不是用背景色独占底部一块区域。只调整图标明暗以适配浅色内容。
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                // 关闭系统为透明导航栏自动叠加的对比度遮罩，否则仍会显示成一条底栏。
                window.isNavigationBarContrastEnforced = false
            }
            WindowCompat.getInsetsController(window, view).apply {
                isAppearanceLightStatusBars = !darkTheme
                isAppearanceLightNavigationBars = !darkTheme
            }
        }
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = LingjiTypography,
        shapes = LingjiShapes,
        content = content
    )
}
