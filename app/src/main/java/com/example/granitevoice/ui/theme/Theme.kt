package com.example.granitevoice.ui.theme

import android.app.Activity
import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat

private val GraniteDarkColorScheme = darkColorScheme(
    primary = GraniteBlue,
    onPrimary = Color.White,
    secondary = GraniteAccent,
    onSecondary = Color.White,
    tertiary = GraniteBlueLight,
    onTertiary = Color.Black,
    background = GraniteBackgroundDark,
    onBackground = GraniteTextPrimaryDark,
    surface = GraniteSurfaceDark,
    onSurface = GraniteTextPrimaryDark,
    surfaceVariant = GraniteGreyDark,
    onSurfaceVariant = GraniteTextSecondaryDark,
    error = GraniteError,
    onError = Color.White,
    outline = GraniteTextSecondaryDark,
    outlineVariant = GraniteGreyLight
)

private val GraniteLightColorScheme = lightColorScheme(
    primary = GraniteBlue,
    onPrimary = Color.White,
    secondary = GraniteAccent,
    onSecondary = Color.White,
    tertiary = GraniteBlueDark,
    onTertiary = Color.White,
    background = GraniteBackgroundLight,
    onBackground = GraniteTextPrimary,
    surface = GraniteSurfaceLight,
    onSurface = GraniteTextPrimary,
    surfaceVariant = Color(0xFFe8e8e8),
    onSurfaceVariant = GraniteTextSecondary,
    error = GraniteError,
    onError = Color.White,
    outline = GraniteTextSecondary,
    outlineVariant = GraniteGreyLight
)

@Composable
fun GraniteVoiceTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    // Dynamic color is available on Android 12+
    dynamicColor: Boolean = false,
    content: @Composable () -> Unit
) {
    val colorScheme = when {
        dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
            val context = LocalContext.current
            if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        }
        darkTheme -> GraniteDarkColorScheme
        else -> GraniteLightColorScheme
    }

    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as Activity).window
            window.statusBarColor = colorScheme.primary.toArgb()
            WindowCompat.getInsetsController(window, view).isAppearanceLightStatusBars = !darkTheme
        }
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography,
        content = content
    )
}