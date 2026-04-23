package com.sportflow.app.ui.theme

import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color

// ── Playo-Inspired "Card & Canvas" Design System — GNITS SportFlow ─────────────
// Light grey canvas (#F7F7F7) with GNITS Orange (#F09819) as the hero accent.
// Cards pop on the canvas with pure white backgrounds and 4dp shadows.

// ── Canvas (Background) ─────────────────────────────────────────────────────────
val PlayoCanvas     = Color(0xFFF7F7F7)   // Playo exact background
val PureWhite       = Color(0xFFFFFFFF)
val OffWhite        = Color(0xFFF8FAFC)
val ScreenBg        = Color(0xFFF7F7F7)   // Alias → PlayoCanvas

// ── Card & Surface ──────────────────────────────────────────────────────────────
val CardSurface     = Color(0xFFFFFFFF)   // Cards are pure white to pop on canvas
val CardBorder      = Color(0xFFEEEEEE)   // Subtle 1dp border
val CardShadow      = Color(0x14000000)   // ~8% black for 4dp shadow
val DividerColor    = Color(0xFFEEEEEE)
val SoftWhite       = Color(0xFFF8FAFC)
val SoftWhiteDim    = Color(0xFF94A3B8)

// ── GNITS Orange — Primary Hero Accent ─────────────────────────────────────────
val GnitsOrange      = Color(0xFFF09819)
val GnitsOrangeDark  = Color(0xFFD4850F)   // Gradient end
val GnitsOrangeLight = Color(0xFFFFF3E0)   // Tint for chips/badges
val GnitsOrangeGlow  = Color(0xFFF09819)
val GnitsOrangeGlowLight = Color(0xFFFBD38D)

// ── Mirage Blue — Bracket Dark Theme ───────────────────────────────────────────
val MirageBlue      = Color(0xFF1A2340)   // Dark bracket background
val MirageBlueMid   = Color(0xFF243055)   // Card on bracket
val MirageBlueCard  = Color(0xFF2D3A66)   // Selected/winner card
val BracketGlow     = Color(0xFFF09819)   // Glowing orange winner lines
val BracketLine     = Color(0xFF3A4A7A)   // Regular connector lines
val BracketWinnerPath = Color(0xFFF09819)

// ── Secondary Accent — Info / Action Blue ──────────────────────────────────────
val InfoBlue        = Color(0xFF2D7DD2)   // More energetic blue
val InfoBlueLight   = Color(0xFFE8F4FD)
val InfoBlueDark    = Color(0xFF1A5FAF)

// ── Text ────────────────────────────────────────────────────────────────────────
val TextPrimary     = Color(0xFF111827)   // Near-black
val TextSecondary   = Color(0xFF6B7280)
val TextTertiary    = Color(0xFF9CA3AF)
val TextOnOrange    = Color(0xFFFFFFFF)
val TextLink        = Color(0xFF2D7DD2)
val TextOnDark      = Color(0xFFFFFFFF)
val TextOnDarkSub   = Color(0xFFB0BADA)

// ── Semantic ────────────────────────────────────────────────────────────────────
val LiveRed         = Color(0xFFEF4444)
val LiveRedBg       = Color(0xFFFEF2F2)
val SuccessGreen    = Color(0xFF16A34A)
val SuccessGreenLight = Color(0xFFDCFCE7)
val WarningAmber    = Color(0xFFF59E0B)
val WarningAmberBg  = Color(0xFFFFFBEB)
val ErrorRed        = Color(0xFFEF4444)
val SquadFullGrey   = Color(0xFFB0B8C1)   // Pill-button "Full" state

// ── Sport Category Tag Colors ───────────────────────────────────────────────────
val CricketColor    = Color(0xFF2E7D32)   // Deep green
val BadmintonColor  = Color(0xFF1565C0)   // Royal blue
val BasketballColor = Color(0xFFE65100)   // Deep orange
val FootballColor   = Color(0xFF283593)   // Indigo
val VolleyballColor = Color(0xFFC62828)   // Crimson
val KabaddiColor    = Color(0xFF6A1B9A)   // Purple
val TableTennisColor = Color(0xFF00695C)  // Teal
val ChessColor      = Color(0xFF37474F)   // Blue-grey

// ── Shimmer ─────────────────────────────────────────────────────────────────────
val ShimmerBase     = Color(0xFFE8E8E8)
val ShimmerHighlight = Color(0xFFF5F5F5)

// ── Glassmorphism / Login ────────────────────────────────────────────────────────
val DeepSpace       = Color(0xFF1A0E00)
val WarmPulse       = Color(0xFFFF6B35)
val GlassBorder     = Color(0x33FFFFFF)
val GlassWhite      = Color(0xB3FFFFFF)
val GlassBorderLight = Color(0x33FFFFFF)
val GlassOverlay    = Color(0x80000000)

// ── Nav ─────────────────────────────────────────────────────────────────────────
val NavActive       = Color(0xFFF09819)
val NavInactive     = Color(0xFF9CA3AF)

// ── Tags ────────────────────────────────────────────────────────────────────────
val TagBeginner     = Color(0xFF16A34A)
val TagIntermediate = Color(0xFFF59E0B)
val TagAdvanced     = Color(0xFFEF4444)
val TagPro          = Color(0xFF2D7DD2)

@Immutable
data class SportFlowColors(
    val background: Color = PlayoCanvas,
    val screenBg: Color   = PlayoCanvas,
    val cardSurface: Color = CardSurface,
    val cardBorder: Color  = CardBorder,
    val primary: Color     = GnitsOrange,
    val primaryLight: Color = GnitsOrangeLight,
    val primaryDark: Color  = GnitsOrangeDark,
    val secondary: Color    = InfoBlue,
    val secondaryLight: Color = InfoBlueLight,
    val textPrimary: Color  = TextPrimary,
    val textSecondary: Color = TextSecondary,
    val textTertiary: Color  = TextTertiary,
    val liveRed: Color       = LiveRed,
    val divider: Color       = DividerColor,
    val mirageBlue: Color    = MirageBlue,
    val bracketGlow: Color   = BracketGlow
)

val LocalSportFlowColors = staticCompositionLocalOf { SportFlowColors() }
