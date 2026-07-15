package io.github.varunkumar.spaceclean.ui.theme

import androidx.compose.ui.graphics.Color

// ── Themeable chrome colours ────────────────────────────────────────────────
// These are live getters backed by the active theme (see Palette.kt / ThemeState),
// so switching themes re-colours the whole app without touching call sites.
val ElectricCyan     : Color get() = ThemeState.palette.accent          // primary accent
val NeonViolet       : Color get() = ThemeState.palette.accent2         // secondary accent
val VelvetBlack      : Color get() = ThemeState.palette.background      // root background
val AuroraBase       : Color get() = ThemeState.palette.background      // aurora canvas
val CyberCard        : Color get() = ThemeState.palette.surface         // card background
val CyberCardHover   : Color get() = ThemeState.palette.surfaceHover    // pressed / hover card
val SurfaceVariant   : Color get() = ThemeState.palette.surfaceVariant  // chips / thumbnails
val OutlineColor     : Color get() = ThemeState.palette.outline         // hairline borders

val TextPrimary      : Color get() = ThemeState.palette.textPrimary
val TextSecondary    : Color get() = ThemeState.palette.textSecondary
val TextMuted        : Color get() = ThemeState.palette.textMuted

// Glow halos derived from the active accents
val CyanGlow         : Color get() = ThemeState.palette.accent.copy(alpha = 0.33f)
val VioletGlow       : Color get() = ThemeState.palette.accent2.copy(alpha = 0.33f)

// Glassmorphism surface system (themed)
val GlassFill        : Color get() = ThemeState.palette.glassFill
val GlassFillStrong  : Color get() = ThemeState.palette.glassFillStrong
val GlassBorder      : Color get() = ThemeState.palette.glassBorder
val GlassBorderSoft  : Color get() = ThemeState.palette.glassBorderSoft
val GlassHighlight   : Color get() = ThemeState.palette.glassHighlight

// Aurora backdrop blooms (themed)
val AuroraCyan       : Color get() = ThemeState.palette.auroraBloom1
val AuroraViolet     : Color get() = ThemeState.palette.auroraBloom2
val AuroraBlue       : Color get() = ThemeState.palette.auroraBloom3

// ── Legacy Brand (kept fixed — used by MediaDetailsScreen) ─────────────────
val NeonBlue         = Color(0xFF00CFFF)
val NeonBlueDim      = Color(0xFF0099BB)
val NeonBlueAlpha13  = Color(0x2200CFFF)
val NeonBlueAlpha27  = Color(0x4400CFFF)
val NeonBlueAlpha80  = Color(0xCC00CFFF)

// ── Fixed backgrounds (media viewers use pure black regardless of theme) ───
val PitchBlack       = Color(0xFF000000)
val SurfaceDark      = Color(0xFF0A0A0F)
val CardSurface      = Color(0xFF0F1117)
val CardElevated     = Color(0xFF161B25)

// ── Status colours (intentionally constant across all themes) ──────────────
val SuccessGreen     = Color(0xFF00E5A0)
val WarningAmber     = Color(0xFFFFB740)
val ErrorRed         = Color(0xFFFF4560)
