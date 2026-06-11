package com.auto.odo.presentation.theme

import android.app.Activity
import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat
import com.auto.odo.core.AppThemeMode

private val DarkColorScheme = darkColorScheme(
    primary = PrimaryColor,
    secondary = SecondaryColor,
    tertiary = TertiaryColor,
    background = DarkBackground,
    surface = DarkSurface,
    onBackground = DarkOnBackground,
    onSurface = DarkOnSurface,
    surfaceVariant = DarkSurfaceVariant
)

private val LightColorScheme = lightColorScheme(
    primary = LightPrimary,
    secondary = SecondaryColor,
    tertiary = TertiaryColor,
    background = LightBackground,
    surface = LightSurface,
    onBackground = LightOnBackground,
    onSurface = LightOnSurface,
    surfaceVariant = LightSurfaceVariant
)

@Composable
fun OdoTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    themeMode: AppThemeMode = AppThemeMode.STANDARD,
    content: @Composable () -> Unit
) {
    val context = LocalContext.current
    val dynamicColor = (themeMode == AppThemeMode.MONET || themeMode == AppThemeMode.MONET_AMOLED) &&
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.S

    val baseColorScheme = when {
        dynamicColor && darkTheme -> dynamicDarkColorScheme(context)
        dynamicColor && !darkTheme -> dynamicLightColorScheme(context)
        darkTheme -> DarkColorScheme
        else -> LightColorScheme
    }

    // Apply AMOLED background override if dark theme and AMOLED is selected
    val colorScheme = if (darkTheme && (themeMode == AppThemeMode.AMOLED || themeMode == AppThemeMode.MONET_AMOLED)) {
        baseColorScheme.copy(
            background = Color.Black,
            surface = Color(0xFF0D0D0D),         // Extremely dark gray for cards
            surfaceVariant = Color(0xFF151515),  // Slightly lighter gray for borders/divs
            onBackground = Color(0xFFF8FAFC),
            onSurface = Color(0xFFF1F5F9)
        )
    } else {
        baseColorScheme
    }

    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as Activity).window
            // Use fully transparent system bars for a seamless edge-to-edge layout
            window.statusBarColor = Color.Transparent.toArgb()
            window.navigationBarColor = Color.Transparent.toArgb()

            val controller = WindowCompat.getInsetsController(window, view)
            // If it is light theme, show dark icons on status bar and navigation bar.
            // If it is dark theme, show light icons.
            controller.isAppearanceLightStatusBars = !darkTheme
            controller.isAppearanceLightNavigationBars = !darkTheme
        }
    }

    MaterialTheme(
        colorScheme = colorScheme,
        content = content
    )
}
