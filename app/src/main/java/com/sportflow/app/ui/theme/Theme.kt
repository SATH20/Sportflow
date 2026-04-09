package com.sportflow.app.ui.theme

import android.app.Activity
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat

private val SportFlowLightColorScheme = lightColorScheme(
    primary = PlayoGreen,
    onPrimary = Color.White,
    primaryContainer = PlayoGreenLight,
    onPrimaryContainer = PlayoGreenDark,
    secondary = InfoBlue,
    onSecondary = Color.White,
    secondaryContainer = InfoBlueLight,
    onSecondaryContainer = InfoBlueDark,
    tertiary = WarningAmber,
    background = PureWhite,
    onBackground = TextPrimary,
    surface = PureWhite,
    onSurface = TextPrimary,
    surfaceVariant = OffWhite,
    onSurfaceVariant = TextSecondary,
    outline = CardBorder,
    error = ErrorRed,
    onError = Color.White
)

@Composable
fun SportFlowTheme(
    content: @Composable () -> Unit
) {
    val colorScheme = SportFlowLightColorScheme
    val view = LocalView.current

    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as Activity).window
            window.statusBarColor = PureWhite.toArgb()
            WindowCompat.getInsetsController(window, view).isAppearanceLightStatusBars = true
        }
    }

    val sportFlowColors = SportFlowColors()
    val sportFlowTypography = SportFlowTypography()

    CompositionLocalProvider(
        LocalSportFlowColors provides sportFlowColors,
        LocalSportFlowTypography provides sportFlowTypography
    ) {
        MaterialTheme(
            colorScheme = colorScheme,
            content = content
        )
    }
}

object SportFlowTheme {
    val colors: SportFlowColors
        @Composable
        get() = LocalSportFlowColors.current

    val typography: SportFlowTypography
        @Composable
        get() = LocalSportFlowTypography.current
}
