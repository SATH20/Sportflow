package com.sportflow.app.ui.theme

import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color

// ── "Ice & Action" Design System ────────────────────────────────────────────
// Inspired by Playo's clean, consumer-grade sports aesthetic

// Backgrounds
val PureWhite = Color(0xFFFFFFFF)
val OffWhite = Color(0xFFF8FAFC)
val ScreenBg = Color(0xFFF1F5F9)

// Card & Surface
val CardSurface = Color(0xFFF8FAFC)
val CardBorder = Color(0xFFE2E8F0)
val CardShadow = Color(0x0D000000)        // 5% black
val DividerColor = Color(0xFFE2E8F0)

// Primary Accent — "Playo Green"
val PlayoGreen = Color(0xFF22C55E)
val PlayoGreenLight = Color(0xFFDCFCE7)
val PlayoGreenDark = Color(0xFF16A34A)

// Secondary Accent — "Info Blue"
val InfoBlue = Color(0xFF635BFF)
val InfoBlueLight = Color(0xFFEEF2FF)
val InfoBlueDark = Color(0xFF4338CA)

// Text
val TextPrimary = Color(0xFF0F172A)
val TextSecondary = Color(0xFF64748B)
val TextTertiary = Color(0xFF94A3B8)
val TextOnGreen = Color(0xFFFFFFFF)
val TextLink = Color(0xFF635BFF)

// Semantic
val LiveRed = Color(0xFFEF4444)
val LiveRedBg = Color(0xFFFEF2F2)
val SuccessGreen = Color(0xFF22C55E)
val WarningAmber = Color(0xFFF59E0B)
val WarningAmberBg = Color(0xFFFFFBEB)
val ErrorRed = Color(0xFFEF4444)

// ── Glow & Space ────────────────────────────────────────────────────────────
// New Colors for Login/Glass UI
val DeepSpace = Color(0xFF0F172A)
val SoftWhite = Color(0xFFF8FAFC)
val SoftWhiteDim = Color(0xFF94A3B8)
val AntigravityBlue = Color(0xFF635BFF)
val AntigravityBlueLight = Color(0xFF818CF8)
val CyanPulse = Color(0xFF22D3EE)
val GlassBorder = Color(0x33FFFFFF)

// Glassmorphism
val GlassWhite = Color(0xB3FFFFFF)        // 70% white
val GlassBorderLight = Color(0x33FFFFFF)  // 20% white
val GlassOverlay = Color(0x80000000)      // 50% black for image overlays

// Sport Tags
val TagBeginner = Color(0xFF22C55E)
val TagIntermediate = Color(0xFFF59E0B)
val TagAdvanced = Color(0xFFEF4444)
val TagPro = Color(0xFF635BFF)

// Bottom Nav
val NavActive = Color(0xFF22C55E)
val NavInactive = Color(0xFF94A3B8)

// Bracket
val BracketLine = Color(0xFFCBD5E1)       // Stadium Mist
val BracketWinnerPath = Color(0xFF22C55E) // Playo Green winner path

@Immutable
data class SportFlowColors(
    val background: Color = PureWhite,
    val screenBg: Color = ScreenBg,
    val cardSurface: Color = CardSurface,
    val cardBorder: Color = CardBorder,
    val primary: Color = PlayoGreen,
    val primaryLight: Color = PlayoGreenLight,
    val primaryDark: Color = PlayoGreenDark,
    val secondary: Color = InfoBlue,
    val secondaryLight: Color = InfoBlueLight,
    val textPrimary: Color = TextPrimary,
    val textSecondary: Color = TextSecondary,
    val textTertiary: Color = TextTertiary,
    val liveRed: Color = LiveRed,
    val divider: Color = DividerColor
)

val LocalSportFlowColors = staticCompositionLocalOf { SportFlowColors() }
