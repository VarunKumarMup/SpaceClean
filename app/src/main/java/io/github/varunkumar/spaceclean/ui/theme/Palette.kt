package io.github.varunkumar.spaceclean.ui.theme

import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.Color

/**
 * Runtime theme system.
 *
 * Every "chrome" colour in [Color.kt] is a getter that reads [ThemeState.palette], so changing
 * the palette re-colours the whole app with no per-screen changes. Brand/status colours
 * (success/warning/error, per-category tile colours) intentionally stay fixed across themes.
 */
data class AppPalette(
    val background:      Color,
    val surface:         Color,
    val surfaceVariant:  Color,
    val surfaceHover:    Color,
    val outline:         Color,
    val accent:          Color,
    val accent2:         Color,
    val textPrimary:     Color,
    val textSecondary:   Color,
    val textMuted:       Color,
    val glassFill:        Color,
    val glassFillStrong:  Color,
    val glassBorder:      Color,
    val glassBorderSoft:  Color,
    val glassHighlight:   Color,
    val auroraBloom1:    Color,
    val auroraBloom2:    Color,
    val auroraBloom3:    Color,
    val isLight:         Boolean = false,
)

enum class AppThemeId(val displayName: String, val palette: AppPalette) {
    CYBER("Cyber", AppPalette(
        background = Color(0xFF08080C), surface = Color(0xFF141420), surfaceVariant = Color(0xFF1C1C2C),
        surfaceHover = Color(0xFF1E1E2E), outline = Color(0xFF2A2A3A),
        accent = Color(0xFF00F0FF), accent2 = Color(0xFF9D00FF),
        textPrimary = Color(0xFFFFFFFF), textSecondary = Color(0xFF8A9BB5), textMuted = Color(0xFF3E4A5C),
        glassFill = Color(0x14FFFFFF), glassFillStrong = Color(0x1FFFFFFF), glassBorder = Color(0x40FFFFFF),
        glassBorderSoft = Color(0x14FFFFFF), glassHighlight = Color(0x2EFFFFFF),
        auroraBloom1 = Color(0x3300F0FF), auroraBloom2 = Color(0x339D00FF), auroraBloom3 = Color(0x262563EB),
    )),
    MIDNIGHT("Midnight", AppPalette(
        background = Color(0xFF000000), surface = Color(0xFF0B0B10), surfaceVariant = Color(0xFF15151C),
        surfaceHover = Color(0xFF17171F), outline = Color(0xFF24242E),
        accent = Color(0xFF00E5FF), accent2 = Color(0xFF7C4DFF),
        textPrimary = Color(0xFFFFFFFF), textSecondary = Color(0xFF8A93A6), textMuted = Color(0xFF3A4150),
        glassFill = Color(0x0FFFFFFF), glassFillStrong = Color(0x1AFFFFFF), glassBorder = Color(0x33FFFFFF),
        glassBorderSoft = Color(0x10FFFFFF), glassHighlight = Color(0x24FFFFFF),
        auroraBloom1 = Color(0x2600E5FF), auroraBloom2 = Color(0x267C4DFF), auroraBloom3 = Color(0x1A1E40AF),
    )),
    OCEAN("Ocean", AppPalette(
        background = Color(0xFF06101A), surface = Color(0xFF0E1B28), surfaceVariant = Color(0xFF15273A),
        surfaceHover = Color(0xFF193146), outline = Color(0xFF1F3A52),
        accent = Color(0xFF2FA8FF), accent2 = Color(0xFF22D3EE),
        textPrimary = Color(0xFFF2F7FF), textSecondary = Color(0xFF8FA8C4), textMuted = Color(0xFF40566E),
        glassFill = Color(0x14FFFFFF), glassFillStrong = Color(0x1FFFFFFF), glassBorder = Color(0x402FA8FF),
        glassBorderSoft = Color(0x142FA8FF), glassHighlight = Color(0x2EFFFFFF),
        auroraBloom1 = Color(0x332FA8FF), auroraBloom2 = Color(0x3322D3EE), auroraBloom3 = Color(0x264338CA),
    )),
    EMERALD("Emerald", AppPalette(
        background = Color(0xFF05120D), surface = Color(0xFF0E1C16), surfaceVariant = Color(0xFF152921),
        surfaceHover = Color(0xFF18322A), outline = Color(0xFF1E3A2E),
        accent = Color(0xFF22E59A), accent2 = Color(0xFF34D399),
        textPrimary = Color(0xFFF1FBF6), textSecondary = Color(0xFF8BB5A4), textMuted = Color(0xFF3E5C4F),
        glassFill = Color(0x14FFFFFF), glassFillStrong = Color(0x1FFFFFFF), glassBorder = Color(0x4022E59A),
        glassBorderSoft = Color(0x1422E59A), glassHighlight = Color(0x2EFFFFFF),
        auroraBloom1 = Color(0x3322E59A), auroraBloom2 = Color(0x3310B981), auroraBloom3 = Color(0x260D9488),
    )),
    LIGHT("Light", AppPalette(
        background = Color(0xFFF3F5FA), surface = Color(0xFFFFFFFF), surfaceVariant = Color(0xFFEAEEF5),
        surfaceHover = Color(0xFFE3E8F1), outline = Color(0xFFD7DEEA),
        accent = Color(0xFF0091B5), accent2 = Color(0xFF7C3AED),
        textPrimary = Color(0xFF0F1722), textSecondary = Color(0xFF53607A), textMuted = Color(0xFF93A0B5),
        glassFill = Color(0x99FFFFFF), glassFillStrong = Color(0xCCFFFFFF), glassBorder = Color(0x1F0F1722),
        glassBorderSoft = Color(0x0D0F1722), glassHighlight = Color(0x66FFFFFF),
        auroraBloom1 = Color(0x2200B5D8), auroraBloom2 = Color(0x227C3AED), auroraBloom3 = Color(0x1A3B82F6),
        isLight = true,
    )),
}

object ThemeState {
    var current by mutableStateOf(AppThemeId.CYBER)
        private set
    val palette: AppPalette get() = current.palette

    fun set(id: AppThemeId) { current = id }
}

private const val PREFS = "spaceclean_prefs"
private const val KEY_THEME = "theme_id"

fun loadSavedTheme(context: Context) {
    val name = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString(KEY_THEME, null)
    val id = AppThemeId.entries.firstOrNull { it.name == name } ?: AppThemeId.CYBER
    ThemeState.set(id)
}

fun applyAndSaveTheme(context: Context, id: AppThemeId) {
    ThemeState.set(id)
    context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putString(KEY_THEME, id.name).apply()
}
