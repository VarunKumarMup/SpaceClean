package io.github.varunkumar.spaceclean.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable

/**
 * SpaceClean always runs in dark mode — the pitch-black / neon-blue palette is the brand.
 * Dynamic colour (Android 12+) is intentionally disabled so the look is consistent
 * regardless of the user's wallpaper.
 */
private val SpaceCleanColorScheme = darkColorScheme(
    primary            = NeonBlue,
    onPrimary          = PitchBlack,
    primaryContainer   = NeonBlueAlpha27,
    onPrimaryContainer = NeonBlue,
    secondary          = NeonBlueDim,
    onSecondary        = PitchBlack,
    secondaryContainer = CardElevated,
    onSecondaryContainer = TextSecondary,
    background         = PitchBlack,
    onBackground       = TextPrimary,
    surface            = CardSurface,
    onSurface          = TextPrimary,
    surfaceVariant     = CardElevated,
    onSurfaceVariant   = TextSecondary,
    outline            = NeonBlueAlpha27,
    outlineVariant     = TextMuted,
    error              = ErrorRed,
    onError            = PitchBlack,
)

@Composable
fun SpaceCleanTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = SpaceCleanColorScheme,
        typography  = Typography,
        content     = content,
    )
}
