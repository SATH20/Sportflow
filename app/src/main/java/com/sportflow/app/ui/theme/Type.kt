package com.sportflow.app.ui.theme

import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

// Using system default (close to Inter/Roboto). Replace with bundled Inter if desired.
val SportFlowFontFamily = FontFamily.Default

@Immutable
data class SportFlowTypography(
    // Display — Hero headlines
    val displayLarge: TextStyle = TextStyle(
        fontFamily = SportFlowFontFamily,
        fontWeight = FontWeight.ExtraBold,
        fontSize = 32.sp,
        lineHeight = 40.sp,
        letterSpacing = (-0.5).sp,
        color = TextPrimary
    ),
    // Section headers
    val displayMedium: TextStyle = TextStyle(
        fontFamily = SportFlowFontFamily,
        fontWeight = FontWeight.Bold,
        fontSize = 24.sp,
        lineHeight = 32.sp,
        letterSpacing = (-0.3).sp,
        color = TextPrimary
    ),
    // Card titles
    val headlineLarge: TextStyle = TextStyle(
        fontFamily = SportFlowFontFamily,
        fontWeight = FontWeight.Bold,
        fontSize = 20.sp,
        lineHeight = 28.sp,
        color = TextPrimary
    ),
    val headlineMedium: TextStyle = TextStyle(
        fontFamily = SportFlowFontFamily,
        fontWeight = FontWeight.SemiBold,
        fontSize = 18.sp,
        lineHeight = 26.sp,
        color = TextPrimary
    ),
    val headlineSmall: TextStyle = TextStyle(
        fontFamily = SportFlowFontFamily,
        fontWeight = FontWeight.SemiBold,
        fontSize = 16.sp,
        lineHeight = 24.sp,
        color = TextPrimary
    ),
    // Body
    val bodyLarge: TextStyle = TextStyle(
        fontFamily = SportFlowFontFamily,
        fontWeight = FontWeight.Normal,
        fontSize = 16.sp,
        lineHeight = 24.sp,
        color = TextSecondary
    ),
    val bodyMedium: TextStyle = TextStyle(
        fontFamily = SportFlowFontFamily,
        fontWeight = FontWeight.Normal,
        fontSize = 14.sp,
        lineHeight = 20.sp,
        color = TextSecondary
    ),
    val bodySmall: TextStyle = TextStyle(
        fontFamily = SportFlowFontFamily,
        fontWeight = FontWeight.Normal,
        fontSize = 12.sp,
        lineHeight = 16.sp,
        color = TextTertiary
    ),
    // Labels / Chips
    val labelLarge: TextStyle = TextStyle(
        fontFamily = SportFlowFontFamily,
        fontWeight = FontWeight.SemiBold,
        fontSize = 14.sp,
        lineHeight = 20.sp,
        color = TextPrimary
    ),
    val labelMedium: TextStyle = TextStyle(
        fontFamily = SportFlowFontFamily,
        fontWeight = FontWeight.Medium,
        fontSize = 12.sp,
        lineHeight = 16.sp,
        color = TextSecondary
    ),
    val labelSmall: TextStyle = TextStyle(
        fontFamily = SportFlowFontFamily,
        fontWeight = FontWeight.Medium,
        fontSize = 10.sp,
        lineHeight = 14.sp,
        letterSpacing = 0.5.sp,
        color = TextTertiary
    ),
    // Score display
    val scoreDisplay: TextStyle = TextStyle(
        fontFamily = SportFlowFontFamily,
        fontWeight = FontWeight.ExtraBold,
        fontSize = 48.sp,
        lineHeight = 56.sp,
        color = TextPrimary
    ),
    // Timer
    val timerDisplay: TextStyle = TextStyle(
        fontFamily = SportFlowFontFamily,
        fontWeight = FontWeight.Bold,
        fontSize = 14.sp,
        lineHeight = 20.sp,
        letterSpacing = 1.sp,
        color = PlayoGreen
    )
)

val LocalSportFlowTypography = staticCompositionLocalOf { SportFlowTypography() }
