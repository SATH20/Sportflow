package com.sportflow.app.ui.theme

import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color

// ── "Ice & Action" Design System — GNITS Orange Accent ──────────────────────
// Clean white base with GNITS Orange (#F09819) as the primary accent

// Backgrounds
val PureWhite = Color(0xFFFFFFFF)
val OffWhite = Color(0xFFF8FAFC)
val ScreenBg = Color(0xFFF1F5F9)

// Card & Surface
val CardSurface = Color(0xFFF8FAFC)
val CardBorder = Color(0xFFE2E8F0)
val CardShadow = Color(0x0D000000)        // 5% black
val DividerColor = Color(0xFFE2E8F0)

// Primary Accent — "GNITS Orange"
val GnitsOrange = Color(0xFFF09819)
val GnitsOrangeLight = Color(0xFFFEF3E2)
val GnitsOrangeDark = Color(0xFFE08C15)

// Secondary Accent — "Info Blue"
val InfoBlue = Color(0xFF635BFF)
val InfoBlueLight = Color(0xFFEEF2FF)
val InfoBlueDark = Color(0xFF4338CA)

// Text
val TextPrimary = Color(0xFF0F172A)
val TextSecondary = Color(0xFF64748B)
val TextTertiary = Color(0xFF94A3B8)
val TextOnOrange = Color(0xFFFFFFFF)
val TextLink = Color(0xFF635BFF)

// Semantic
val LiveRed = Color(0xFFEF4444)
val LiveRedBg = Color(0xFFFEF2F2)
val SuccessGreen = Color(0xFF22C55E)
val SuccessGreenLight = Color(0xFFDCFCE7)
val WarningAmber = Color(0xFFF59E0B)
val WarningAmberBg = Color(0xFFFFFBEB)
val ErrorRed = Color(0xFFEF4444)

// ── Glow & Space ────────────────────────────────────────────────────────────
// Login / Glass UI
val DeepSpace = Color(0xFF1A0E00)
val SoftWhite = Color(0xFFF8FAFC)
val SoftWhiteDim = Color(0xFF94A3B8)
val GnitsOrangeGlow = Color(0xFFF09819)
val GnitsOrangeGlowLight = Color(0xFFFBD38D)
val WarmPulse = Color(0xFFFF6B35)
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
val NavActive = Color(0xFFF09819)
val NavInactive = Color(0xFF94A3B8)

// Bracket
val BracketLine = Color(0xFFCBD5E1)       // Stadium Mist
val BracketWinnerPath = Color(0xFFF09819) // GNITS Orange winner path

@Immutable
data class SportFlowColors(
    val background: Color = PureWhite,
    val screenBg: Color = ScreenBg,
    val cardSurface: Color = CardSurface,
    val cardBorder: Color = CardBorder,
    val primary: Color = GnitsOrange,
    val primaryLight: Color = GnitsOrangeLight,
    val primaryDark: Color = GnitsOrangeDark,
    val secondary: Color = InfoBlue,
    val secondaryLight: Color = InfoBlueLight,
    val textPrimary: Color = TextPrimary,
    val textSecondary: Color = TextSecondary,
    val textTertiary: Color = TextTertiary,
    val liveRed: Color = LiveRed,
    val divider: Color = DividerColor
)

val LocalSportFlowColors = staticCompositionLocalOf { SportFlowColors() }
