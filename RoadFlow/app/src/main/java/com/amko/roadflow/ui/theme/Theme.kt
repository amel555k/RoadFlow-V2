package com.amko.roadflow.ui.theme

import android.app.Activity
import android.os.Build
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
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

private val DarkColorScheme = darkColorScheme(
    primary = Color(0xFF2C5F8A),
    secondary = Color(0xFF1E3A56),
    tertiary = Pink80,
    background = Color(0xFF0E1A2B),
    surface = Color(0xFF16273D),
    onPrimary = Color.White,
    onBackground = Color(0xFFE4ECF5),
    onSurface = Color(0xFFE4ECF5)
)

private val LightColorScheme = lightColorScheme(
    primary = Color(0xFF2C5F8A),
    secondary = Color(0xFF1E3A56),
    tertiary = Pink40,
    background = LightGreyBackground,
    surface = Color.White,
    onPrimary = Color.White,
    onBackground = Color.Black,
    onSurface = Color.Black
)

@Composable
fun RoadFlowTheme(
    appTheme: AppTheme = AppTheme.SYSTEM,
    dynamicColor: Boolean = false,
    content: @Composable () -> Unit
) {
    val darkTheme = when (appTheme) {
        AppTheme.LIGHT -> false
        AppTheme.DARK -> true
        AppTheme.SYSTEM -> isSystemInDarkTheme()
    }

    val colorScheme = when {
        dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
            val context = LocalContext.current
            if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        }
        darkTheme -> DarkColorScheme
        else -> LightColorScheme
    }

    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as Activity).window
            val statusBarColor = if (darkTheme) Color(0xFF0E1A2B) else Color.White
            window.statusBarColor = statusBarColor.toArgb()
            WindowCompat.getInsetsController(window, view).isAppearanceLightStatusBars = !darkTheme
        }
    }

    val animatedColorScheme = animateColorScheme(colorScheme)

    MaterialTheme(
        colorScheme = animatedColorScheme,
        typography = Typography,
        content = content
    )
}

@Composable
private fun animateColorScheme(target: androidx.compose.material3.ColorScheme): androidx.compose.material3.ColorScheme {
    val animSpec = tween<Color>(durationMillis = 500)

    return target.copy(
        primary = animateColorAsState(target.primary, animSpec, label = "primary").value,
        onPrimary = animateColorAsState(target.onPrimary, animSpec, label = "onPrimary").value,
        primaryContainer = animateColorAsState(target.primaryContainer, animSpec, label = "primaryContainer").value,
        onPrimaryContainer = animateColorAsState(target.onPrimaryContainer, animSpec, label = "onPrimaryContainer").value,
        secondary = animateColorAsState(target.secondary, animSpec, label = "secondary").value,
        onSecondary = animateColorAsState(target.onSecondary, animSpec, label = "onSecondary").value,
        secondaryContainer = animateColorAsState(target.secondaryContainer, animSpec, label = "secondaryContainer").value,
        onSecondaryContainer = animateColorAsState(target.onSecondaryContainer, animSpec, label = "onSecondaryContainer").value,
        tertiary = animateColorAsState(target.tertiary, animSpec, label = "tertiary").value,
        onTertiary = animateColorAsState(target.onTertiary, animSpec, label = "onTertiary").value,
        tertiaryContainer = animateColorAsState(target.tertiaryContainer, animSpec, label = "tertiaryContainer").value,
        onTertiaryContainer = animateColorAsState(target.onTertiaryContainer, animSpec, label = "onTertiaryContainer").value,
        background = animateColorAsState(target.background, animSpec, label = "background").value,
        onBackground = animateColorAsState(target.onBackground, animSpec, label = "onBackground").value,
        surface = animateColorAsState(target.surface, animSpec, label = "surface").value,
        onSurface = animateColorAsState(target.onSurface, animSpec, label = "onSurface").value,
        surfaceVariant = animateColorAsState(target.surfaceVariant, animSpec, label = "surfaceVariant").value,
        onSurfaceVariant = animateColorAsState(target.onSurfaceVariant, animSpec, label = "onSurfaceVariant").value,
        error = animateColorAsState(target.error, animSpec, label = "error").value,
        onError = animateColorAsState(target.onError, animSpec, label = "onError").value,
        errorContainer = animateColorAsState(target.errorContainer, animSpec, label = "errorContainer").value,
        onErrorContainer = animateColorAsState(target.onErrorContainer, animSpec, label = "onErrorContainer").value,
        outline = animateColorAsState(target.outline, animSpec, label = "outline").value,
        outlineVariant = animateColorAsState(target.outlineVariant, animSpec, label = "outlineVariant").value,
        inverseSurface = animateColorAsState(target.inverseSurface, animSpec, label = "inverseSurface").value,
        inverseOnSurface = animateColorAsState(target.inverseOnSurface, animSpec, label = "inverseOnSurface").value,
        inversePrimary = animateColorAsState(target.inversePrimary, animSpec, label = "inversePrimary").value,
        scrim = animateColorAsState(target.scrim, animSpec, label = "scrim").value
    )
}